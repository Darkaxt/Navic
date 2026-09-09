package paige.navic.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackPreparationCoordinatorTest {
	@Test
	fun supersededPreparationCannotCommitEvenWhenItsDependencyIgnoresCancellation() = runTest {
		for (command in listOf("pause", "clear", "selection", "ownership-loss", "session", "controller")) {
			val coordinator = PlaybackPreparationCoordinator(this, PlaybackStartFeedback())
			val prepared = CompletableDeferred<Unit>()
			val commits = mutableListOf<String>()
			coordinator.launch {
				withContext(NonCancellable) { prepared.await() }
				commit { commits += "old" }
			}
			runCurrent()
			coordinator.cancel()
			if (command == "selection") coordinator.launch { commit { commits += "new" } }
			prepared.complete(Unit)
			runCurrent()
			assertEquals(if (command == "selection") listOf("new") else emptyList(), commits, command)
		}
	}

	@Test
	fun bulkAndSelectionSupersedeEachOtherButDuplicateBulkKeepsOriginalAdmission() = runTest {
		val feedback = PlaybackStartFeedback()
		val coordinator = PlaybackPreparationCoordinator(this, feedback)
		val prepared = CompletableDeferred<Unit>()
		val commits = mutableListOf<String>()
		coordinator.launch { prepared.await(); commit { commits += "selection" } }
		runCurrent()
		coordinator.launch(bulk = true) {
			prepared.await()
			commit { commits += "bulk" }
			feedback.starting()
			feedback.awaitSettled()
		}
		assertEquals(PlaybackStartPhase.Preparing, feedback.state.value?.phase)
		feedback.hide()
		coordinator.launch(bulk = true) { error("Duplicate bulk request admitted") }
		assertEquals(true, feedback.state.value?.visible)
		prepared.complete(Unit)
		runCurrent()
		assertEquals(listOf("bulk"), commits)
		coordinator.launch { commit { commits += "new-selection" } }
		runCurrent()
		assertEquals(listOf("bulk", "new-selection"), commits)
		assertNull(feedback.state.value)
		assertFalse(feedback.isPending)
	}

	@Test
	fun cancellationBeforeDispatchAndStaleOwnerRejectCommit() = runTest {
		val coordinator = PlaybackPreparationCoordinator(this, PlaybackStartFeedback())
		coordinator.launch { error("Cancelled preparation ran") }
		coordinator.cancel()
		runCurrent()
		var owner = "original"
		val prepared = CompletableDeferred<Unit>()
		coordinator.launch(isOwnerCurrent = { owner == "original" }) {
			prepared.await()
			commit { error("Replaced session or controller committed") }
		}
		runCurrent()
		owner = "replacement"
		prepared.complete(Unit)
		runCurrent()
	}
}
