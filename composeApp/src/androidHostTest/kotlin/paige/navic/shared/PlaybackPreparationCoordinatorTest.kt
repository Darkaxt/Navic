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
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackPreparationCoordinatorTest {
	@Test
	fun foregroundCleanupRunsWhenCancelledBeforeDispatch() = runTest {
		val coordinator = PlaybackPreparationCoordinator(this, PlaybackStartFeedback())
		var finished = 0
		coordinator.launch(onFinished = { finished++ }) { error("Cancelled request ran") }
		coordinator.cancel()
		runCurrent()
		assertEquals(1, finished)
	}

	@Test
	fun foregroundCleanupDoesNotWaitForNonCooperativeWorkOrReleaseItsReplacement() = runTest {
		val coordinator = PlaybackPreparationCoordinator(this, PlaybackStartFeedback())
		val pending = CompletableDeferred<Unit>()
		var oldFinished = 0
		var newFinished = 0
		coordinator.launch(bulk = true, onFinished = { oldFinished++ }) {
			withContext(NonCancellable) { pending.await() }
		}
		runCurrent()
		coordinator.cancel()
		assertEquals(1, oldFinished)
		coordinator.launch(bulk = true, onFinished = { newFinished++ }) { CompletableDeferred<Unit>().await() }
		pending.complete(Unit)
		runCurrent()
		assertEquals(1, oldFinished)
		assertEquals(0, newFinished)
		coordinator.cancel()
		runCurrent()
		assertEquals(1, newFinished)
	}

	@Test
	fun backCancellationPreservesPreviousPlaybackAndRejectsLatePreparation() = runTest {
		val feedback = PlaybackStartFeedback()
		val coordinator = PlaybackPreparationCoordinator(this, feedback)
		val prepared = CompletableDeferred<Unit>()
		var playing = "previous"
		var pauses = 0
		coordinator.launch(bulk = true) {
			withContext(NonCancellable) { prepared.await() }
			commit { playing = "replacement" }
		}
		runCurrent()
		coordinator.cancelFromUser { pauses++; playing = "paused" }
		assertNull(feedback.state.value)
		assertFalse(feedback.isPending)
		prepared.complete(Unit)
		runCurrent()
		assertEquals("previous", playing)
		assertEquals(0, pauses)
	}

	@Test
	fun backAfterQueueCommitPausesOnlyThePendingStartAndAllowsAnotherRequest() = runTest {
		val feedback = PlaybackStartFeedback()
		val coordinator = PlaybackPreparationCoordinator(this, feedback)
		var queue = listOf("previous")
		var playing = false
		coordinator.launch(bulk = true) {
			commit { queue = listOf("new", "next"); playing = true; feedback.starting() }
			feedback.awaitSettled()
		}
		runCurrent()
		coordinator.cancelFromUser { playing = false }
		assertFalse(playing)
		assertEquals(listOf("new", "next"), queue)
		assertNull(feedback.state.value)
		coordinator.cancelFromUser { error("No request must not pause unrelated playback") }
		coordinator.launch(bulk = true) { feedback.starting(); feedback.awaitSettled() }
		runCurrent()
		assertTrue(feedback.isPending)
		assertEquals(PlaybackStartPhase.Starting, feedback.state.value?.phase)
		coordinator.cancel()
	}

	@Test
	fun cancelledBulkCleanupCannotDismissItsReplacement() = runTest {
		val feedback = PlaybackStartFeedback()
		val coordinator = PlaybackPreparationCoordinator(this, feedback)
		val prepared = CompletableDeferred<Unit>()
		coordinator.launch(bulk = true) {
			withContext(NonCancellable) { prepared.await() }
			commit { error("Cancelled request committed") }
		}
		runCurrent()
		coordinator.cancelFromUser { error("Preparation must not pause") }
		coordinator.launch(bulk = true) { feedback.starting(); feedback.awaitSettled() }
		prepared.complete(Unit)
		runCurrent()
		assertEquals(PlaybackStartPhase.Starting, feedback.state.value?.phase)
		assertTrue(feedback.isPending)
		coordinator.cancel()
	}

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
