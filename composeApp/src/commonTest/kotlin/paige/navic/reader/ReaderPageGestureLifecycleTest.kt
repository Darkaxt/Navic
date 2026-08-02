package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPageGestureLifecycleTest {
	@Test
	fun gestureIdsIncreaseMonotonically() {
		val lifecycle = ReaderPageGestureLifecycle()

		assertEquals(1L, lifecycle.beginGesture())
		assertEquals(2L, lifecycle.beginGesture())
		assertEquals(3L, lifecycle.beginGesture())
	}

	@Test
	fun gestureAcceptsExactlyOneTerminalOutcome() {
		val lifecycle = ReaderPageGestureLifecycle()
		val gestureId = lifecycle.beginGesture()

		assertTrue(
			lifecycle.completeGesture(
				gestureId,
				ReaderPageGestureTerminalOutcome.CommittedForward
			)
		)
		assertFalse(
			lifecycle.completeGesture(
				gestureId,
				ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
			)
		)
		assertEquals(
			ReaderPageGestureTerminalOutcome.CommittedForward,
			lifecycle.terminalOutcome(gestureId)
		)
	}

	@Test
	fun taskFourTerminalOutcomesAreTyped() {
		val outcomes = ReaderPageGestureTerminalOutcome.values().toSet()

		assertTrue(ReaderPageGestureTerminalOutcome.CompletedTapAction in outcomes)
		assertTrue(ReaderPageGestureTerminalOutcome.CancelledLifecycle in outcomes)
		assertTrue(ReaderPageGestureTerminalOutcome.FailedRecovery in outcomes)
	}

	@Test
	fun busyFeedbackIsLimitedToRendererWorkRejections() {
		val busyOutcomes = setOf(
			ReaderPageGestureTerminalOutcome.RejectedPreparing,
			ReaderPageGestureTerminalOutcome.RejectedSettling,
			ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable
		)

		ReaderPageGestureTerminalOutcome.values().forEach { outcome ->
			assertEquals(
				outcome in busyOutcomes,
				readerPageGestureShouldShowBusyFeedback(outcome),
				"Unexpected busy-feedback classification for $outcome"
			)
		}
	}

	@Test
	fun busyFeedbackMinimumTimerWaitsForMatchingFullyVisiblePresentation() {
		assertFalse(
			readerRendererBusyFeedbackCanStartMinimumTimer(
				activeRejectionToken = 1L,
				fullyVisibleRejectionToken = 0L
			)
		)
		assertFalse(
			readerRendererBusyFeedbackCanStartMinimumTimer(
				activeRejectionToken = 2L,
				fullyVisibleRejectionToken = 1L
			)
		)
		assertTrue(
			readerRendererBusyFeedbackCanStartMinimumTimer(
				activeRejectionToken = 2L,
				fullyVisibleRejectionToken = 2L
			)
		)
		assertFalse(
			readerRendererBusyFeedbackCanStartMinimumTimer(
				activeRejectionToken = 0L,
				fullyVisibleRejectionToken = 0L
			)
		)
	}

	@Test
	fun readyFeedbackDelayGuaranteesMinimumVisibility() {
		assertEquals(2_000L, ReaderRendererBusyFeedbackMinimumMillis)
		assertEquals(4_000L, ReaderRendererBusyFeedbackMaximumMillis)
		assertEquals(2_000L, readerRendererBusyFeedbackReadyDelayMillis(-1L))
		assertEquals(2_000L, readerRendererBusyFeedbackReadyDelayMillis(0L))
		assertEquals(1L, readerRendererBusyFeedbackReadyDelayMillis(1_999L))
		assertEquals(0L, readerRendererBusyFeedbackReadyDelayMillis(2_000L))
		assertEquals(0L, readerRendererBusyFeedbackReadyDelayMillis(4_000L))
	}

	@Test
	fun unknownGestureCannotReceiveTerminalOutcome() {
		val lifecycle = ReaderPageGestureLifecycle()

		assertFalse(
			lifecycle.completeGesture(
				99L,
				ReaderPageGestureTerminalOutcome.RejectedPreparing
			)
		)
	}

	@Test
	fun outcomeHistoryIsBoundedWithoutForgettingActiveGestures() {
		val lifecycle = ReaderPageGestureLifecycle(historyLimit = 2)
		val first = lifecycle.beginGesture()
		val second = lifecycle.beginGesture()
		val third = lifecycle.beginGesture()

		assertTrue(lifecycle.completeGesture(first, ReaderPageGestureTerminalOutcome.CancelledByUser))
		assertTrue(lifecycle.completeGesture(second, ReaderPageGestureTerminalOutcome.CommittedForward))
		assertTrue(lifecycle.completeGesture(third, ReaderPageGestureTerminalOutcome.CommittedBackward))

		assertEquals(null, lifecycle.terminalOutcome(first))
		assertEquals(ReaderPageGestureTerminalOutcome.CommittedForward, lifecycle.terminalOutcome(second))
		assertEquals(ReaderPageGestureTerminalOutcome.CommittedBackward, lifecycle.terminalOutcome(third))
	}
}
