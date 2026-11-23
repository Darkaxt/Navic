package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderPageAdjacentChapterDirection
import paige.navic.reader.adjacentChapterDirection

internal data class ReaderPageAdjacentChapterIdentity(
	val direction: ReaderPageAdjacentChapterDirection,
	val chapterIndex: Int,
	val pageStartIndex: Int,
	val pageCount: Int
) {
	val endPageIndexExclusive: Int = pageStartIndex + pageCount

	fun contains(pageIndex: Int): Boolean =
		pageCount > 0 && pageIndex in pageStartIndex until endPageIndexExclusive
}

internal data class ReaderPageAdjacentChapterPrefetchKey(
	val currentChapterIndex: Int,
	val currentChapterPageStartIndex: Int,
	val currentChapterPageCount: Int,
	val rasterProfileEpoch: Long,
	val rasterEpoch: Long
) {
	private val currentChapterEndPageIndexExclusive =
		currentChapterPageStartIndex + currentChapterPageCount

	fun matches(deck: ReaderPagePreparedActiveDeck): Boolean =
		deck.rasterProfileEpoch == rasterProfileEpoch &&
			deck.rasterEpoch == rasterEpoch &&
			currentChapterPageCount > 0 &&
			deck.sourceCenterPageIndex in
				currentChapterPageStartIndex until currentChapterEndPageIndexExclusive
}

internal data class ReaderPageAdjacentChapterPrefetchChapter(
	val identity: ReaderPageAdjacentChapterIdentity,
	val targets: List<ReaderPageRasterBatchTarget>
)

internal data class ReaderPageAdjacentChapterPrefetchPlan(
	val key: ReaderPageAdjacentChapterPrefetchKey,
	val chapters: List<ReaderPageAdjacentChapterPrefetchChapter>
)

internal data class ReaderPageAdjacentChapterPrefetchProgress(
	val durablePages: Int,
	val plannedPages: Int,
	val durableChapters: Int,
	val plannedChapters: Int
) {
	val complete: Boolean
		get() = plannedChapters > 0 && durableChapters == plannedChapters
}

internal data class ReaderPagePreparedActiveDeck(
	val rasterProfileEpoch: Long,
	val rasterEpoch: Long,
	val sourceCenterPageIndex: Int,
	val generationId: Long,
	val preparationGeneration: Long = 0L
)

internal data class ReaderPageAdjacentChapterPrefetchSubmission(
	val sessionId: Long,
	val key: ReaderPageAdjacentChapterPrefetchKey,
	val activeDeckGenerationId: Long,
	val chapter: ReaderPageAdjacentChapterPrefetchChapter,
	val targets: List<ReaderPageRasterBatchTarget>,
	/** Center page of the prepared deck this batch was submitted for: the live reference page. */
	val activeDeckCenterPageIndex: Int = -1
)

/**
 * Owns eligibility, ordering, and exact callback identity for adjacent-chapter raster work.
 * The host remains responsible for running each submitted batch off the interaction path.
 */
internal class ReaderPageAdjacentChapterPrefetchCoordinator(
	private val onSubmit: (ReaderPageAdjacentChapterPrefetchSubmission) -> Unit,
	private val onCancel: (ReaderPageAdjacentChapterPrefetchSubmission) -> Unit,
	/** Every chapter of the current plan is durable or was attempted, with nothing in flight. */
	private val onDrained: () -> Unit = {},
	/** The drained state may no longer hold: a new plan, deck, interaction or foreground work. */
	private val onDisturbed: () -> Unit = {}
) {
	private var plan: ReaderPageAdjacentChapterPrefetchPlan? = null
	private var preparedActiveDeck: ReaderPagePreparedActiveDeck? = null
	private var activeSubmission: ReaderPageAdjacentChapterPrefetchSubmission? = null
	private val durablePageIndices = linkedSetOf<Int>()
	private val attemptedChapterIdentities = linkedSetOf<ReaderPageAdjacentChapterIdentity>()
	private var drainedReported = false
	private var awaitingSettledView = false
	private val transientDeferrals = mutableMapOf<ReaderPageAdjacentChapterIdentity, Int>()
	private var nextSessionId = 0L
	private var suspended = false
	private var hostAvailable = true
	private var interactionActive = false
	private var durableEpochs: Pair<Long, Long>? = null

	/**
	 * "Prepare pages" (#71): the reader stays usable, so a page turn into another chapter
	 * re-plans; pages already durable under the same raster epochs stay counted.
	 */
	var retainDurablePagesAcrossPlans = false

	fun replaceDurablePlan(value: ReaderPageAdjacentChapterPrefetchPlan) {
		require(value.key.currentChapterIndex >= 0)
		require(value.key.currentChapterPageStartIndex >= 0)
		require(value.key.currentChapterPageCount > 0)
		require(value.key.rasterProfileEpoch > 0L)
		require(value.key.rasterEpoch > 0L)
		require(
			value.chapters
				.map { chapter -> chapter.identity.direction }
				.filterNot { direction -> direction == ReaderPageAdjacentChapterDirection.Later }
				.let { directions -> directions.distinct().size == directions.size }
		)
		require(
			value.chapters
				.filter { chapter -> chapter.identity.direction == ReaderPageAdjacentChapterDirection.Later }
				.let { later -> later.map { chapter -> chapter.identity.chapterIndex }.distinct().size == later.size }
		)
		value.chapters.forEach { chapter ->
			requireValidChapter(value.key, chapter)
		}
		if (plan == value) {
			trySubmit()
			return
		}
		cancelActive()
		onDisturbed()
		plan = value
		val epochs = value.key.rasterProfileEpoch to value.key.rasterEpoch
		if (!retainDurablePagesAcrossPlans || durableEpochs != epochs) durablePageIndices.clear()
		durableEpochs = epochs
		attemptedChapterIdentities.clear()
		awaitingSettledView = false
		transientDeferrals.clear()
		suspended = false
		trySubmit()
	}

	fun beginBlockingSession() {
		cancelActive()
		onDisturbed()
		plan = null
		if (!retainDurablePagesAcrossPlans) {
			durablePageIndices.clear()
			durableEpochs = null
		}
		attemptedChapterIdentities.clear()
		awaitingSettledView = false
		transientDeferrals.clear()
		suspended = false
	}

	fun clear() {
		cancelActive()
		onDisturbed()
		plan = null
		preparedActiveDeck = null
		durablePageIndices.clear()
		durableEpochs = null
		attemptedChapterIdentities.clear()
		awaitingSettledView = false
		transientDeferrals.clear()
		suspended = false
	}

	fun onHostAvailabilityChanged(available: Boolean) {
		if (hostAvailable == available) return
		hostAvailable = available
		attemptedChapterIdentities.clear()
		awaitingSettledView = false
		if (available) {
			trySubmit()
		} else {
			cancelActive()
			onDisturbed()
		}
	}

	fun onInteractionActiveChanged(active: Boolean) {
		if (interactionActive == active) return
		interactionActive = active
		attemptedChapterIdentities.clear()
		awaitingSettledView = false
		if (active) {
			cancelActive()
			onDisturbed()
		} else {
			trySubmit()
		}
	}

	fun onPassiveAvailable() {
		attemptedChapterIdentities.clear()
		awaitingSettledView = false
		trySubmit()
	}

	fun suspendForForegroundWork() {
		suspended = true
		attemptedChapterIdentities.clear()
		cancelActive()
		onDisturbed()
	}

	fun resumeAfterForegroundWork() {
		suspended = false
		attemptedChapterIdentities.clear()
		awaitingSettledView = false
		trySubmit()
	}

	fun onPreparedActiveDeckChanged(deck: ReaderPagePreparedActiveDeck?) {
		if (preparedActiveDeck == deck) return
		preparedActiveDeck = deck
		attemptedChapterIdentities.clear()
		awaitingSettledView = false
		cancelActive()
		onDisturbed()
		trySubmit()
	}

	fun onTargetDurable(
		submission: ReaderPageAdjacentChapterPrefetchSubmission,
		pageIndex: Int
	): Boolean {
		val active = activeSubmission
		if (active != submission || active.targets.none { target -> target.pageIndex == pageIndex }) {
			return false
		}
		durablePageIndices += pageIndex
		return true
	}

	/**
	 * [haltRun] stops the rest of the plan too (the page cache is full), until the next deck,
	 * availability change or retry; any other unfinished batch only sets its own chapter aside.
	 */
	fun onBatchFinished(
		submission: ReaderPageAdjacentChapterPrefetchSubmission,
		haltRun: Boolean = false
	): Boolean {
		if (activeSubmission != submission) return false
		activeSubmission = null
		attemptedChapterIdentities += submission.chapter.identity
		if (haltRun) plan?.chapters?.mapTo(attemptedChapterIdentities) { chapter -> chapter.identity }
		trySubmit()
		return true
	}

	/**
	 * The batch could not start because a page turn was relocating the live view (#71 tablet):
	 * every chapter would defer the same way, so the run waits for the next settled view and
	 * retries this chapter instead of setting it (and the rest of the book) aside. After
	 * [MaxTransientDeferrals] deferrals the chapter is set aside like a failed one, so the run
	 * always ends.
	 */
	fun onBatchDeferred(submission: ReaderPageAdjacentChapterPrefetchSubmission): Boolean {
		if (activeSubmission != submission) return false
		activeSubmission = null
		val identity = submission.chapter.identity
		val deferrals = (transientDeferrals[identity] ?: 0) + 1
		transientDeferrals[identity] = deferrals
		if (deferrals >= MaxTransientDeferrals) {
			attemptedChapterIdentities += identity
			trySubmit()
		} else {
			awaitingSettledView = true
		}
		return true
	}

	/** The live view settled after a page turn: a deferred run resumes. */
	fun onViewSettled() {
		if (!awaitingSettledView) return
		awaitingSettledView = false
		trySubmit()
	}

	fun isActive(submission: ReaderPageAdjacentChapterPrefetchSubmission): Boolean =
		activeSubmission == submission && isEligible(submission)

	fun durableChapterIdentities(): Set<ReaderPageAdjacentChapterIdentity> =
		plan?.chapters.orEmpty().mapNotNullTo(linkedSetOf()) { chapter ->
			chapter.identity.takeIf {
				chapter.targets.all { target -> target.pageIndex in durablePageIndices }
			}
		}

	/** Durable versus planned pages and chapters of the current plan (#66 progress). */
	fun planProgress(): ReaderPageAdjacentChapterPrefetchProgress? {
		val chapters = plan?.chapters ?: return null
		return ReaderPageAdjacentChapterPrefetchProgress(
			durablePages = chapters.sumOf { chapter ->
				chapter.targets.count { target -> target.pageIndex in durablePageIndices }
			},
			plannedPages = chapters.sumOf { chapter -> chapter.targets.size },
			durableChapters = durableChapterIdentities().size,
			plannedChapters = chapters.size
		)
	}

	private fun trySubmit() {
		val currentPlan = plan ?: return
		val deck = preparedActiveDeck ?: return
		if (
			suspended ||
			awaitingSettledView ||
			!hostAvailable ||
			interactionActive ||
			!planServesDeck(currentPlan.key, deck) ||
			activeSubmission != null
		) return
		// A chapter whose batch failed is tried again on the next deck or availability change; it
		// must not stop the chapters after it. A transient failure while a page turn relocates the
		// live view otherwise drained a "Prepare pages" run half way (#71 tablet). Run-wide
		// failures (a full or failing page cache) halt the plan through onBatchFinished(haltRun).
		val chapter = currentPlan.chapters.firstOrNull { candidate ->
			candidate.identity !in attemptedChapterIdentities &&
				candidate.targets.any { target -> target.pageIndex !in durablePageIndices }
		}
		if (chapter == null) {
			drainedReported = true
			onDrained()
			return
		}
		// Work found after a drain (a retry, or a turn that cleared the attempts) un-settles the
		// layout again, so "Some pages could not be prepared" does not outlive the resumed run.
		if (drainedReported) {
			drainedReported = false
			onDisturbed()
		}
		val missingTargets = chapter.targets.filter { target ->
			target.pageIndex !in durablePageIndices
		}
		val submission = ReaderPageAdjacentChapterPrefetchSubmission(
			sessionId = Math.incrementExact(nextSessionId).also { nextSessionId = it },
			key = currentPlan.key,
			activeDeckGenerationId = deck.generationId,
			chapter = chapter,
			targets = missingTargets,
			activeDeckCenterPageIndex = deck.sourceCenterPageIndex
		)
		activeSubmission = submission
		onSubmit(submission)
	}

	private fun isEligible(
		submission: ReaderPageAdjacentChapterPrefetchSubmission
	): Boolean {
		val currentPlan = plan ?: return false
		val deck = preparedActiveDeck ?: return false
		return !suspended &&
			hostAvailable &&
			!interactionActive &&
			currentPlan.key == submission.key &&
			planServesDeck(currentPlan.key, deck) &&
			deck.generationId == submission.activeDeckGenerationId
	}

	/**
	 * An adjacent-chapter plan is relative to the chapter being read. A "Prepare pages" plan
	 * (#71) names every page of the book by its absolute index, so a page turn into another
	 * chapter does not make it stale: only new raster epochs do. Otherwise the run stalls when
	 * the reader turns into an already prepared chapter, which builds no new plan.
	 */
	private fun planServesDeck(
		key: ReaderPageAdjacentChapterPrefetchKey,
		deck: ReaderPagePreparedActiveDeck
	): Boolean = if (retainDurablePagesAcrossPlans) {
		deck.rasterProfileEpoch == key.rasterProfileEpoch && deck.rasterEpoch == key.rasterEpoch
	} else {
		key.matches(deck)
	}

	private fun cancelActive() {
		val submission = activeSubmission ?: return
		activeSubmission = null
		onCancel(submission)
	}

	private fun requireValidChapter(
		key: ReaderPageAdjacentChapterPrefetchKey,
		chapter: ReaderPageAdjacentChapterPrefetchChapter
	) {
		require(chapter.identity.chapterIndex >= 0)
		require(
			when (chapter.identity.direction) {
				ReaderPageAdjacentChapterDirection.Current ->
					chapter.identity.chapterIndex == key.currentChapterIndex
				ReaderPageAdjacentChapterDirection.Next ->
					chapter.identity.chapterIndex == key.currentChapterIndex + 1
				ReaderPageAdjacentChapterDirection.Previous ->
					chapter.identity.chapterIndex == (key.currentChapterIndex - 1).coerceAtLeast(0)
				// Whole-book preparation (#66) also plans the chapters before the previous one.
				ReaderPageAdjacentChapterDirection.Later ->
					chapter.identity.chapterIndex >= key.currentChapterIndex + 2 ||
						chapter.identity.chapterIndex <= key.currentChapterIndex - 2
			}
		)
		require(chapter.identity.pageStartIndex >= 0)
		require(chapter.identity.pageCount > 0)
		require(chapter.targets.isNotEmpty())
		require(chapter.targets.map { target -> target.pageIndex }.distinct().size == chapter.targets.size)
		require(chapter.targets.all { target ->
			chapter.identity.contains(target.pageIndex) &&
				target.priority.adjacentChapterDirection == chapter.identity.direction
		})
	}
}

private const val MaxTransientDeferrals = 3
