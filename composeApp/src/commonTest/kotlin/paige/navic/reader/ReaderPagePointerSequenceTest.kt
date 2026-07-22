package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPagePointerSequenceTest {
	@Test
	fun horizontalDominanceClaimsCurlOnlyAfterTouchSlop() {
		val sequence = ReaderPagePointerSequence(gestureId = 7L, downX = 100f, downY = 200f)

		assertEquals(ReaderPagePointerOwnership.Pending, sequence.moveTo(106f, 203f, 8f))
		assertEquals(ReaderPagePointerOwnership.Curl, sequence.moveTo(120f, 205f, 8f))
	}

	@Test
	fun verticalMotionLeavesOwnershipWithContent() {
		val sequence = ReaderPagePointerSequence(gestureId = 8L, downX = 100f, downY = 200f)

		assertEquals(ReaderPagePointerOwnership.Content, sequence.moveTo(104f, 220f, 8f))
	}

	@Test
	fun pointerUpRetainsOriginalIdUntilDelayedTapCompletes() {
		val sequence = ReaderPagePointerSequence(gestureId = 9L, downX = 10f, downY = 10f)

		sequence.pointerUp()

		assertEquals(9L, sequence.pendingTapGestureId)
		assertTrue(sequence.complete(ReaderPageGestureTerminalOutcome.CompletedTapAction))
		assertFalse(sequence.complete(ReaderPageGestureTerminalOutcome.CancelledByUser))
	}
}
