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

internal data class ReaderPagePreparedActiveDeck(
	val rasterProfileEpoch: Long,
	val rasterEpoch: Long,
	val sourceCenterPageIndex: Int,
	val generationId: Long
)

internal data class ReaderPageAdjacentChapterPrefetchSubmission(
	val sessionId: Long,
	val key: ReaderPageAdjacentChapterPrefetchKey,
	val activeDeckGenerationId: Long,
	val chapter: ReaderPageAdjacentChapterPrefetchChapter,
	val targets: List<ReaderPageRasterBatchTarget>
)

/**
 * Owns eligibility, ordering, and exact callback identity for adjacent-chapter raster work.
 * The host remains responsible for running each submitted batch off the interaction path.
 */
internal class ReaderPageAdjacentChapterPrefetchCoordinator(
	private val onSubmit: (ReaderPageAdjacentChapterPrefetchSubmission) -> Unit,
	private val onCancel: (ReaderPageAdjacentChapterPrefetchSubmission) -> Unit
) {
	private var plan: ReaderPageAdjacentChapterPrefetchPlan? = null
	private var preparedActiveDeck: ReaderPagePreparedActiveDeck? = null
	private var activeSubmission: ReaderPageAdjacentChapterPrefetchSubmission? = null
	private val durablePageIndices = linkedSetOf<Int>()
	private val attemptedChapterIdentities = linkedSetOf<ReaderPageAdjacentChapterIdentity>()
	private var nextSessionId = 0L
	private var suspended = false
	private var hostAvailable = true
	private var interactionActive = false

	fun replaceDurablePlan(value: ReaderPageAdjacentChapterPrefetchPlan) {
		require(value.key.currentChapterIndex >= 0)
		require(value.key.currentChapterPageStartIndex >= 0)
		require(value.key.currentChapterPageCount > 0)
		require(value.key.rasterProfileEpoch > 0L)
		require(value.key.rasterEpoch > 0L)
		require(
			value.chapters.map { chapter -> chapter.identity.direction }.distinct().size ==
				value.chapters.size
		)
		value.chapters.forEach { chapter ->
			requireValidChapter(value.key, chapter)
		}
		if (plan == value) {
			trySubmit()
			return
		}
		cancelActive()
		plan = value
		durablePageIndices.clear()
		attemptedChapterIdentities.clear()
		suspended = false
		trySubmit()
	}

	fun beginBlockingSession() {
		cancelActive()
		plan = null
		durablePageIndices.clear()
		attemptedChapterIdentities.clear()
		suspended = false
	}

	fun clear() {
		cancelActive()
		plan = null
		preparedActiveDeck = null
		durablePageIndices.clear()
		attemptedChapterIdentities.clear()
		suspended = false
	}

	fun onHostAvailabilityChanged(available: Boolean) {
		if (hostAvailable == available) return
		hostAvailable = available
		attemptedChapterIdentities.clear()
		if (available) {
			trySubmit()
		} else {
			cancelActive()
		}
	}

	fun onInteractionActiveChanged(active: Boolean) {
		if (interactionActive == active) return
		interactionActive = active
		attemptedChapterIdentities.clear()
		if (active) {
			cancelActive()
		} else {
			trySubmit()
		}
	}

	fun suspendForForegroundWork() {
		suspended = true
		attemptedChapterIdentities.clear()
		cancelActive()
	}

	fun resumeAfterForegroundWork() {
		suspended = false
		attemptedChapterIdentities.clear()
		trySubmit()
	}

	fun onPreparedActiveDeckChanged(deck: ReaderPagePreparedActiveDeck?) {
		if (preparedActiveDeck == deck) return
		preparedActiveDeck = deck
		attemptedChapterIdentities.clear()
		cancelActive()
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

	fun onBatchFinished(
		submission: ReaderPageAdjacentChapterPrefetchSubmission
	): Boolean {
		if (activeSubmission != submission) return false
		activeSubmission = null
		attemptedChapterIdentities += submission.chapter.identity
		trySubmit()
		return true
	}

	fun isActive(submission: ReaderPageAdjacentChapterPrefetchSubmission): Boolean =
		activeSubmission == submission && isEligible(submission)

	fun durableChapterIdentities(): Set<ReaderPageAdjacentChapterIdentity> =
		plan?.chapters.orEmpty().mapNotNullTo(linkedSetOf()) { chapter ->
			chapter.identity.takeIf {
				chapter.targets.all { target -> target.pageIndex in durablePageIndices }
			}
		}

	private fun trySubmit() {
		val currentPlan = plan ?: return
		val deck = preparedActiveDeck ?: return
		if (
			suspended ||
			!hostAvailable ||
			interactionActive ||
			!currentPlan.key.matches(deck) ||
			activeSubmission != null
		) return
		val chapter = currentPlan.chapters.firstOrNull { candidate ->
			candidate.targets.any { target -> target.pageIndex !in durablePageIndices }
		} ?: return
		if (chapter.identity in attemptedChapterIdentities) return
		val missingTargets = chapter.targets.filter { target ->
			target.pageIndex !in durablePageIndices
		}
		val submission = ReaderPageAdjacentChapterPrefetchSubmission(
			sessionId = Math.incrementExact(nextSessionId).also { nextSessionId = it },
			key = currentPlan.key,
			activeDeckGenerationId = deck.generationId,
			chapter = chapter,
			targets = missingTargets
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
			currentPlan.key.matches(deck) &&
			deck.generationId == submission.activeDeckGenerationId
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
			chapter.identity.chapterIndex == when (chapter.identity.direction) {
				ReaderPageAdjacentChapterDirection.Current -> key.currentChapterIndex
				ReaderPageAdjacentChapterDirection.Next -> key.currentChapterIndex + 1
				ReaderPageAdjacentChapterDirection.Previous ->
					(key.currentChapterIndex - 1).coerceAtLeast(0)
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
