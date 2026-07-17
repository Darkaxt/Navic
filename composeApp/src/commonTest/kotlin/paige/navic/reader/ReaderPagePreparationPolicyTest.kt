package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPagePreparationPolicyTest {
	@Test
	fun progressUsesCompletedOverRequired() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 12,
			completedCount = 3,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 1,
			hasPreparedBefore = false
		)

		assertEquals(0.25f, state.progress)
		assertEquals(ReaderPagePreparationPresentation.Cover, state.presentation)
	}

	@Test
	fun currentNextAndPreviousMakeReaderInteractive() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 12,
			completedCount = 3,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 3,
			hasPreparedBefore = false
		)

		assertTrue(state.interactiveReady)
		assertEquals(ReaderPagePreparationPresentation.Hidden, state.presentation)
	}

	@Test
	fun availableBoundaryNeighborsAreEnoughForInteractiveReadiness() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Ready,
			requiredCount = 2,
			completedCount = 2,
			interactiveRequiredCount = 2,
			interactiveCompletedCount = 2,
			hasPreparedBefore = false
		)

		assertTrue(state.interactiveReady)
		assertEquals(ReaderPagePreparationPresentation.Hidden, state.presentation)
	}

	@Test
	fun subsequentCacheOutrunKeepsStablePageWithoutForegroundProgress() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Preparing,
			requiredCount = 6,
			completedCount = 0,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 0,
			hasPreparedBefore = true,
			activePageNumber = 8
		)

		assertFalse(state.interactiveReady)
		assertEquals(ReaderPagePreparationPresentation.Hidden, state.presentation)
		assertEquals("Page 8", state.activePageLabel)
		assertEquals(ReaderPagePreparationGestureDisposition.ConsumeWhilePreparing, state.gestureDisposition)
	}

	@Test
	fun subsequentFailureRemainsVisibleAndRetryable() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			requiredCount = 3,
			completedCount = 1,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 1,
			hasPreparedBefore = true,
			error = "Page preparation failed",
			retryable = true
		)

		assertEquals(ReaderPagePreparationPresentation.Compact, state.presentation)
		assertTrue(state.retryable)
	}

	@Test
	fun initialFailureStaysVisibleAndRetryable() {
		val state = readerPagePreparationState(
			phase = ReaderPagePreparationPhase.Failed,
			requiredCount = 3,
			completedCount = 1,
			interactiveRequiredCount = 3,
			interactiveCompletedCount = 1,
			hasPreparedBefore = false,
			error = "Page preparation failed",
			retryable = true
		)

		assertEquals(ReaderPagePreparationPresentation.Cover, state.presentation)
		assertTrue(state.retryable)
		assertEquals("Page preparation failed", state.error)
	}
}
