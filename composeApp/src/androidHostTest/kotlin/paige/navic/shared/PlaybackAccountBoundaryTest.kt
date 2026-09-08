package paige.navic.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import paige.navic.domain.manager.PlaybackAccountBoundary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackAccountBoundaryTest {
	@Test
	fun transitionRevokesOutgoingMediaBeforePublishingNewOwnerAndRejectsLateRestore() = runTest {
		val boundary = PlaybackAccountBoundary("A", dispatcher = StandardTestDispatcher(testScheduler))
		val outgoing = boundary.capture()
		val loaded = CompletableDeferred<Unit>()
		var localUri: String? = "file:///A/song.mp3"
		var playing = true
		val events = mutableListOf<String>()
		boundary.register { next ->
			assertEquals(null, boundary.capture().ownerId)
			playing = false
			localUri = null
			events += "revoked-for-$next"
		}
		launch {
			loaded.await()
			if (boundary.isCurrent(outgoing)) localUri = "file:///A/song.mp3"
		}
		boundary.transitionTo("B")
		events += "B-usable"
		loaded.complete(Unit)
		runCurrent()
		assertEquals(listOf("revoked-for-B", "B-usable"), events)
		assertFalse(playing)
		assertEquals(null, localUri)
		assertFalse(boundary.isCurrent(outgoing))
	}

	@Test
	fun sameAccountCredentialsPreservePlaybackButLogoutInvalidatesEvenAfterReturningToSameAccount() = runTest {
		val boundary = PlaybackAccountBoundary("A", dispatcher = StandardTestDispatcher(testScheduler))
		val outgoing = boundary.capture()
		var revoked = 0
		boundary.register { revoked++ }
		boundary.transitionTo("A")
		assertTrue(boundary.isCurrent(outgoing))
		assertEquals(0, revoked)
		boundary.transitionTo(null)
		boundary.transitionTo("A")
		assertFalse(boundary.isCurrent(outgoing))
		assertEquals(2, revoked)
	}
}
