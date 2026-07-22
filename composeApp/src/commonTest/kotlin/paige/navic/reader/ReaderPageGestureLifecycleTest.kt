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
