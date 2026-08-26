package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.reader.ReaderPageAdjacentChapterDirection
import paige.navic.reader.ReaderPageRasterPriority

class ReaderPageAdjacentChapterPrefetchTest {
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
		assertEquals(1, submissions.size)

		coordinator.onPassiveAvailable()

		assertEquals(2, submissions.size)
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
