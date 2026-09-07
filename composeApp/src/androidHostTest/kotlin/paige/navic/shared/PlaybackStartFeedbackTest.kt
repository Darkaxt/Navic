package paige.navic.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackStartFeedbackTest {
	@Test
	fun publishesBeforeDispatchAndRejectsRepeatedPlay() = runTest {
		val feedback = PlaybackStartFeedback()
		val prepared = CompletableDeferred<Unit>()
		var queueSubmissions = 0
		feedback.launch(this) {
			prepared.await()
			queueSubmissions++
			feedback.starting()
			feedback.awaitSettled()
		}
		assertEquals(PlaybackStartPhase.Preparing, feedback.state.value?.phase)
		feedback.launch(this) { queueSubmissions++ }
		runCurrent()
		assertEquals(0, queueSubmissions)
		feedback.settle() // The previous song playing must not finish preparation.
		prepared.complete(Unit)
		runCurrent()
		assertEquals(1, queueSubmissions)
		assertEquals(PlaybackStartPhase.Starting, feedback.state.value?.phase)
		assertTrue(feedback.isPending)
		feedback.settle()
		runCurrent()
		assertNull(feedback.state.value)
		assertFalse(feedback.isPending)
	}

	@Test
	fun hidingDoesNotCancelAndRepeatedPlayRevealsProgress() = runTest {
		val feedback = PlaybackStartFeedback()
		feedback.launch(this) { feedback.starting(); feedback.awaitSettled() }
		runCurrent()
		feedback.hide()
		assertFalse(feedback.state.value!!.visible)
		assertTrue(feedback.isPending)
		feedback.launch(this) { error("Duplicate request executed") }
		assertTrue(feedback.state.value!!.visible)
		feedback.settle()
		runCurrent()
		assertNull(feedback.state.value)
	}

	@Test
	fun cancellationPreventsLateQueueReplacementAndOldCleanupCannotClearNewRequest() = runTest {
		val feedback = PlaybackStartFeedback()
		val prepared = CompletableDeferred<Unit>()
		var submitted = false
		feedback.launch(this) { prepared.await(); submitted = true }
		runCurrent()
		feedback.cancel()
		feedback.launch(this) { feedback.starting(); feedback.awaitSettled() }
		prepared.complete(Unit)
		runCurrent()
		assertFalse(submitted)
		assertEquals(PlaybackStartPhase.Starting, feedback.state.value?.phase)
		feedback.cancel()
		runCurrent()
		assertNull(feedback.state.value)
		assertFalse(feedback.isPending)
	}

	@Test
	fun completedOperationAllowsNextRequest() = runTest {
		val feedback = PlaybackStartFeedback()
		feedback.launch(this) { }
		runCurrent()
		assertFalse(feedback.isPending)
		feedback.launch(this) { feedback.starting(); feedback.awaitSettled() }
		runCurrent()
		assertTrue(feedback.isPending)
		feedback.cancel()
	}

	@Test
	fun failureReleasesAdmissionAndIsNotSwallowed() = runTest {
		val feedback = PlaybackStartFeedback()
		val failure = IllegalStateException("Preparation failed")
		var reported: Throwable? = null
		val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) +
			CoroutineExceptionHandler { _, error -> reported = error })
		feedback.launch(scope) { throw failure }
		runCurrent()
		assertEquals(failure, reported)
		assertNull(feedback.state.value)
		assertFalse(feedback.isPending)
		scope.cancel()
	}

	@Test
	fun cancelledOwnerBeforeDispatchCannotLeaveProgressStuck() = runTest {
		val feedback = PlaybackStartFeedback()
		val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
		feedback.launch(scope) { error("Cancelled operation executed") }
		scope.cancel()
		runCurrent()
		assertNull(feedback.state.value)
		assertFalse(feedback.isPending)
		feedback.launch(scope) { error("Cancelled scope executed") }
		runCurrent()
		assertNull(feedback.state.value)
	}
}
