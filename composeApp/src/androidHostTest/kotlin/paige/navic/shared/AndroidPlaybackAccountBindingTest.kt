package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.domain.manager.PlaybackAccountBoundary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidPlaybackAccountBindingTest {
	@Test
	fun accountSwitchAndLogoutClearActualLocalMediaBeforeReturningButCredentialsDoNot() = runTest {
		for (nextOwner in listOf("B", null)) {
			val boundary = PlaybackAccountBoundary("A", dispatcher = StandardTestDispatcher(testScheduler))
			val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
			var invalidations = 0
			val unregister = bindPlaybackAccount(boundary, player) { invalidations++ }
			try {
				val outgoing = MediaItem.Builder().setMediaId("same-song-id").setUri("file:///A/song.mp3").build()
				player.setMediaItem(outgoing)
				player.playWhenReady = true
				boundary.transitionTo("A")
				assertEquals(outgoing, player.currentMediaItem)
				assertTrue(player.playWhenReady)
				assertEquals(0, invalidations)
				boundary.transitionTo(nextOwner)
				assertEquals(nextOwner, boundary.capture().ownerId)
				assertEquals(0, player.mediaItemCount)
				assertEquals(null, player.currentMediaItem)
				assertFalse(player.playWhenReady)
				assertEquals(Player.STATE_IDLE, player.playbackState)
				assertEquals(1, invalidations)
				unregister()
				boundary.transitionTo("C")
				assertEquals(1, invalidations)
			} finally {
				unregister()
				player.release()
			}
		}
	}
}
