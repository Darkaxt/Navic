package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageAdjacentChapterDirection
import paige.navic.reader.ReaderPageRasterPriority

class ReaderPageAdjacentChapterPrefetchTest {
	@Test
	fun reportsDrainedOnlyOnceEveryChapterFinishedAndDisturbedOnInteraction() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		var drained = 0
		var disturbed = 0
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {},
			onDrained = { drained++ },
			onDisturbed = { disturbed++ }
		)
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		repeat(2) {
			assertEquals(0, drained, "work is still pending")
			val submission = submissions.last()
			submission.targets.forEach { target -> coordinator.onTargetDurable(submission, target.pageIndex) }
			assertTrue(coordinator.onBatchFinished(submission))
		}
		assertEquals(1, drained)
		val beforeInteraction = disturbed
		coordinator.onInteractionActiveChanged(true)
		assertEquals(beforeInteraction + 1, disturbed)
		coordinator.onInteractionActiveChanged(false)
		assertEquals(2, drained, "idle again after the interaction")
	}

	@Test
	fun aPlanWithoutAdjacentChaptersDrainsOnceItsDeckIsPrepared() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		var drained = 0
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {},
			onDrained = { drained++ }
		)
		coordinator.replaceDurablePlan(adjacentPlan().copy(chapters = emptyList()))
		assertEquals(0, drained, "the deck is not prepared yet")
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		assertTrue(submissions.isEmpty())
		assertEquals(1, drained, "nothing adjacent to prepare, so the other layout may start (#57)")
	}

	@Test
	fun waitsForMatchingPreparedActiveDeckThenSubmitsSeparateChapters() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {}
		)
		val plan = adjacentPlan()

		coordinator.replaceDurablePlan(plan)
		assertTrue(submissions.isEmpty())

		coordinator.onPreparedActiveDeckChanged(
			preparedDeck(profileEpoch = 8L)
		)
		assertTrue(submissions.isEmpty())

		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		assertEquals(1, submissions.size)
		assertEquals(
			ReaderPageAdjacentChapterDirection.Previous,
			submissions.single().chapter.identity.direction
		)

		val previous = submissions.single()
		previous.targets.forEach { target ->
			assertTrue(coordinator.onTargetDurable(previous, target.pageIndex))
		}
		assertTrue(coordinator.onBatchFinished(previous))
		assertEquals(2, submissions.size)
		assertEquals(
			ReaderPageAdjacentChapterDirection.Next,
			submissions.last().chapter.identity.direction
		)

		val next = submissions.last()
		next.targets.forEach { target ->
			assertTrue(coordinator.onTargetDurable(next, target.pageIndex))
		}
		assertTrue(coordinator.onBatchFinished(next))
		assertEquals(
			setOf(
				plan.chapters[0].identity,
				plan.chapters[1].identity
			),
			coordinator.durableChapterIdentities()
		)
	}

	@Test
	fun blockingSessionRetainsAnAlreadyPublishedDestinationDeck() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {}
		)
		coordinator.onPreparedActiveDeckChanged(
			preparedDeck(sourceCenterPageIndex = 8)
		)

		coordinator.beginBlockingSession()
		coordinator.replaceDurablePlan(chapterBoundaryPlan())

		assertEquals(1, submissions.size)
		assertEquals(
			ReaderPageAdjacentChapterDirection.Previous,
			submissions.single().chapter.identity.direction
		)
	}

	@Test
	fun webViewDetachmentCancelsThenResubmitsMissingWorkOnReattach() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val cancellations = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = cancellations::add
		)
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		val detached = submissions.single()
		coordinator.onTargetDurable(detached, detached.targets.first().pageIndex)

		coordinator.onHostAvailabilityChanged(false)
		assertEquals(listOf(detached), cancellations)
		assertFalse(coordinator.onBatchFinished(detached))

		coordinator.onHostAvailabilityChanged(true)
		assertEquals(2, submissions.size)
		assertEquals(
			detached.targets.drop(1).map { target -> target.pageIndex },
			submissions.last().targets.map { target -> target.pageIndex }
		)
	}

	@Test
	fun pointerInteractionCancelsThenResubmitsOnlyMissingWork() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val cancellations = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = cancellations::add
		)
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		val interrupted = submissions.single()
		coordinator.onTargetDurable(interrupted, interrupted.targets.first().pageIndex)

		coordinator.onInteractionActiveChanged(true)
		assertEquals(listOf(interrupted), cancellations)
		assertFalse(coordinator.onBatchFinished(interrupted))

		coordinator.onInteractionActiveChanged(false)
		assertEquals(2, submissions.size)
		assertEquals(
			interrupted.targets.drop(1).map { target -> target.pageIndex },
			submissions.last().targets.map { target -> target.pageIndex }
		)
	}

	@Test
	fun passiveAvailabilityRetriesAnUndurableRetiredChapter() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {}
		)
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		val retired = submissions.single()

		assertTrue(coordinator.onBatchFinished(retired))
		assertEquals(2, submissions.size, "the next chapter still runs")
		val next = submissions.last()
		next.targets.forEach { target -> coordinator.onTargetDurable(next, target.pageIndex) }
		assertTrue(coordinator.onBatchFinished(next))
		assertEquals(2, submissions.size, "the retired chapter waits for a retry")

		coordinator.onPassiveAvailable()

		assertEquals(3, submissions.size)
		assertEquals(
			retired.targets.map { target -> target.pageIndex },
			submissions.last().targets.map { target -> target.pageIndex }
		)
	}

	@Test
	fun replacementPlanAndStaleCallbacksCannotMutateCurrentOwnership() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val cancellations = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = cancellations::add
		)
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		val stale = submissions.single()

		coordinator.replaceDurablePlan(
			adjacentPlan(profileEpoch = 9L, rasterEpoch = 12L)
		)
		assertEquals(listOf(stale), cancellations)
		assertFalse(coordinator.onTargetDurable(stale, stale.targets.first().pageIndex))
		assertFalse(coordinator.onBatchFinished(stale))
		assertTrue(coordinator.durableChapterIdentities().isEmpty())
	}

	@Test
	fun bookPreparationKeepsDurablePagesAcrossRePlansUntilTheEpochsChange() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {}
		)
		coordinator.retainDurablePagesAcrossPlans = true
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		val submission = submissions.last()
		submission.targets.forEach { target -> coordinator.onTargetDurable(submission, target.pageIndex) }
		val prepared = coordinator.planProgress()!!.durablePages
		assertTrue(prepared > 0)

		// #71: a page turn into another chapter re-plans; the prepared pages stay counted.
		coordinator.beginBlockingSession()
		coordinator.replaceDurablePlan(adjacentPlan())
		assertEquals(prepared, coordinator.planProgress()!!.durablePages)

		coordinator.replaceDurablePlan(adjacentPlan(profileEpoch = 9L, rasterEpoch = 12L))
		assertEquals(0, coordinator.planProgress()!!.durablePages, "new raster epochs invalidate them")
	}

	@Test
	fun bookPreparationContinuesAfterATurnIntoAnotherPreparedChapter() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {}
		)
		coordinator.retainDurablePagesAcrossPlans = true
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		val before = submissions.size
		assertTrue(before > 0)

		// #71: the reader turned out of the planned current chapter (pages 4..7) and the
		// chapter it reached was already prepared, so no new plan arrives.
		coordinator.onInteractionActiveChanged(true)
		coordinator.onPreparedActiveDeckChanged(preparedDeck(sourceCenterPageIndex = 12, generationId = 42L))
		coordinator.onInteractionActiveChanged(false)
		assertTrue(submissions.size > before, "the book run resumes under the same plan")
		assertEquals(42L, submissions.last().activeDeckGenerationId)
		assertEquals(12, submissions.last().activeDeckCenterPageIndex, "it references the live deck")

		coordinator.onPreparedActiveDeckChanged(
			preparedDeck(rasterEpoch = 12L, sourceCenterPageIndex = 12, generationId = 43L)
		)
		assertEquals(42L, submissions.last().activeDeckGenerationId, "other raster epochs still wait")
	}

	@Test
	fun aFailedChapterDoesNotDrainTheRunBeforeTheChaptersAfterIt() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		var drained = 0
		var disturbed = 0
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {},
			onDrained = { drained++ },
			onDisturbed = { disturbed++ }
		)
		coordinator.retainDurablePagesAcrossPlans = true
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		// The first chapter's batch fails (a page turn relocated the live view under it).
		val failed = submissions.last()
		assertTrue(coordinator.onBatchFinished(failed))
		assertEquals(0, drained, "the chapters after the failed one still run")
		val next = submissions.last()
		assertTrue(next.chapter.identity != failed.chapter.identity)
		next.targets.forEach { target -> coordinator.onTargetDurable(next, target.pageIndex) }
		assertTrue(coordinator.onBatchFinished(next))
		assertEquals(1, drained, "only the failed chapter is missing now")
		assertEquals(false, coordinator.planProgress()?.complete)

		// A retry finds the failed chapter again: the drained run is disturbed, so the book card
		// shows it preparing rather than "Some pages could not be prepared".
		val disturbedBeforeRetry = disturbed
		coordinator.onPassiveAvailable()
		assertEquals(disturbedBeforeRetry + 1, disturbed)
		val retry = submissions.last()
		assertEquals(failed.chapter.identity, retry.chapter.identity)
		retry.targets.forEach { target -> coordinator.onTargetDurable(retry, target.pageIndex) }
		assertTrue(coordinator.onBatchFinished(retry))
		assertEquals(2, drained)
		assertEquals(true, coordinator.planProgress()?.complete)
	}

	@Test
	fun aDeferredChapterWaitsForTheSettledViewInsteadOfDrainingTheRun() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		var drained = 0
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {},
			onDrained = { drained++ }
		)
		coordinator.retainDurablePagesAcrossPlans = true
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck())
		// A page turn relocates the live view: the batch defers and nothing else is tried.
		val deferred = submissions.last()
		val submitted = submissions.size
		assertTrue(coordinator.onBatchDeferred(deferred))
		assertEquals(submitted, submissions.size, "the rest of the book is not burnt through")
		assertEquals(0, drained)
		coordinator.onViewSettled()
		assertEquals(deferred.chapter.identity, submissions.last().chapter.identity, "same chapter again")
		// A chapter that keeps deferring is eventually set aside, so the run always ends.
		assertTrue(coordinator.onBatchDeferred(submissions.last()))
		coordinator.onViewSettled()
		assertTrue(coordinator.onBatchDeferred(submissions.last()))
		val next = submissions.last()
		assertTrue(next.chapter.identity != deferred.chapter.identity, "third deferral sets it aside")
		next.targets.forEach { target -> coordinator.onTargetDurable(next, target.pageIndex) }
		assertTrue(coordinator.onBatchFinished(next))
		assertEquals(1, drained)
		assertEquals(false, coordinator.planProgress()?.complete)
	}

	@Test
	fun adjacentPrefetchStillWaitsForAPlanOfTheChapterBeingRead() {
		val submissions = mutableListOf<ReaderPageAdjacentChapterPrefetchSubmission>()
		val coordinator = ReaderPageAdjacentChapterPrefetchCoordinator(
			onSubmit = submissions::add,
			onCancel = {}
		)
		coordinator.replaceDurablePlan(adjacentPlan())
		coordinator.onPreparedActiveDeckChanged(preparedDeck(sourceCenterPageIndex = 12))
		assertTrue(submissions.isEmpty())
	}
}

private fun adjacentPlan(
	profileEpoch: Long = 7L,
	rasterEpoch: Long = 11L
): ReaderPageAdjacentChapterPrefetchPlan = ReaderPageAdjacentChapterPrefetchPlan(
	key = ReaderPageAdjacentChapterPrefetchKey(
		currentChapterIndex = 3,
		currentChapterPageStartIndex = 4,
		currentChapterPageCount = 4,
		rasterProfileEpoch = profileEpoch,
		rasterEpoch = rasterEpoch
	),
	chapters = listOf(
		ReaderPageAdjacentChapterPrefetchChapter(
			identity = ReaderPageAdjacentChapterIdentity(
				direction = ReaderPageAdjacentChapterDirection.Previous,
				chapterIndex = 2,
				pageStartIndex = 1,
				pageCount = 3
			),
			targets = listOf(
				ReaderPageRasterBatchTarget(3, ReaderPageRasterPriority.PreviousChapter),
				ReaderPageRasterBatchTarget(2, ReaderPageRasterPriority.PreviousChapterRemainder)
			)
		),
		ReaderPageAdjacentChapterPrefetchChapter(
			identity = ReaderPageAdjacentChapterIdentity(
				direction = ReaderPageAdjacentChapterDirection.Next,
				chapterIndex = 4,
				pageStartIndex = 8,
				pageCount = 3
			),
			targets = listOf(
				ReaderPageRasterBatchTarget(8, ReaderPageRasterPriority.NextChapter),
				ReaderPageRasterBatchTarget(9, ReaderPageRasterPriority.NextChapterRemainder)
			)
		)
	)
)

private fun chapterBoundaryPlan(): ReaderPageAdjacentChapterPrefetchPlan =
	ReaderPageAdjacentChapterPrefetchPlan(
		key = ReaderPageAdjacentChapterPrefetchKey(
			currentChapterIndex = 4,
			currentChapterPageStartIndex = 8,
			currentChapterPageCount = 3,
			rasterProfileEpoch = 7L,
			rasterEpoch = 11L
		),
		chapters = listOf(
			ReaderPageAdjacentChapterPrefetchChapter(
				identity = ReaderPageAdjacentChapterIdentity(
					direction = ReaderPageAdjacentChapterDirection.Previous,
					chapterIndex = 3,
					pageStartIndex = 4,
					pageCount = 4
				),
				targets = listOf(
					ReaderPageRasterBatchTarget(7, ReaderPageRasterPriority.PreviousChapter)
				)
			),
			ReaderPageAdjacentChapterPrefetchChapter(
				identity = ReaderPageAdjacentChapterIdentity(
					direction = ReaderPageAdjacentChapterDirection.Next,
					chapterIndex = 5,
					pageStartIndex = 11,
					pageCount = 2
				),
				targets = listOf(
					ReaderPageRasterBatchTarget(11, ReaderPageRasterPriority.NextChapter)
				)
			)
		)
	)

private fun preparedDeck(
	profileEpoch: Long = 7L,
	rasterEpoch: Long = 11L,
	sourceCenterPageIndex: Int = 6,
	generationId: Long = 41L
): ReaderPagePreparedActiveDeck = ReaderPagePreparedActiveDeck(
	rasterProfileEpoch = profileEpoch,
	rasterEpoch = rasterEpoch,
	sourceCenterPageIndex = sourceCenterPageIndex,
	generationId = generationId
)
