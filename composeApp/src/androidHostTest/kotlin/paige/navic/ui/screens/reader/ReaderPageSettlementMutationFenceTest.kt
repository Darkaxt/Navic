package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReaderPageSettlementMutationFenceTest {
	@Test
	fun deferredRefreshRemainsBlockedUntilMatchingSettlementIsReconciled() {
		val fence = ReaderPageSettlementMutationFence()

		fence.onSettlementStarted(gestureId = 46L, sourceGenerationId = 34L)

		assertTrue(fence.deferRefreshIfBlocked(activeGestureId = 46L))
		assertTrue(fence.blocksExternalDeckMutation(activeGestureId = 46L))
		assertTrue(
			fence.blocksExternalDeckMutation(activeGestureId = null),
			"Publishing the terminal must not reopen deck mutation inside the callback."
		)
		assertFalse(fence.takeDeferredRefreshIfUnblocked(activeGestureId = null))
		assertFailsWith<IllegalStateException> {
			fence.onSettlementReconciled(
				gestureId = 46L,
				sourceGenerationId = 36L,
				activeGestureId = null
			)
		}

		assertTrue(
			fence.onSettlementReconciled(
				gestureId = 46L,
				sourceGenerationId = 34L,
				activeGestureId = null
			)
		)
		assertFalse(fence.blocksExternalDeckMutation(activeGestureId = null))
		assertFalse(fence.takeDeferredRefreshIfUnblocked(activeGestureId = null))
	}

	@Test
	fun gestureTerminalReleasesOneDeferredRefreshWithoutSettlement() {
		val fence = ReaderPageSettlementMutationFence()

		assertTrue(fence.deferRefreshIfBlocked(activeGestureId = 7L))
		assertFalse(fence.takeDeferredRefreshIfUnblocked(activeGestureId = 7L))
		assertTrue(fence.takeDeferredRefreshIfUnblocked(activeGestureId = null))
		assertFalse(fence.takeDeferredRefreshIfUnblocked(activeGestureId = null))
	}
}
