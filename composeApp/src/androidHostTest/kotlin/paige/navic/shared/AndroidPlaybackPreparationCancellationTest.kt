package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidPlaybackPreparationCancellationTest {
	@Test
	fun backBeforeCommitKeepsTheOldEngineAndIndependentDownloadsRunning() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val feedback = PlaybackStartFeedback()
		val preparation = PlaybackPreparationCoordinator(this, feedback)
		val pending = CompletableDeferred<Unit>()
		val download = CompletableDeferred<Unit>()
		var downloaded = false
		launch { download.await(); downloaded = true }
		try {
			player.setMediaItem(item("previous"), 60_000L)
			player.play()
			preparation.launch(bulk = true) {
				withContext(NonCancellable) { pending.await() }
				commit { player.setMediaItem(item("replacement")); player.play() }
			}
			runCurrent()
			preparation.cancelFromUser(player::pause)
			pending.complete(Unit)
			download.complete(Unit)
			runCurrent()
			assertTrue(player.playWhenReady)
			assertEquals("previous", player.currentMediaItem?.mediaId)
			assertEquals(60_000L, player.currentPosition)
			assertTrue(downloaded)
			assertFalse(feedback.isPending)
		} finally { preparation.cancel(); player.release() }
	}

	@Test
	fun backDuringStartingRevokesEnginePlayIntentWithoutRemovingTheQueue() = runTest {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val feedback = PlaybackStartFeedback()
		val preparation = PlaybackPreparationCoordinator(this, feedback)
		try {
			preparation.launch(bulk = true) {
				commit {
					player.setMediaItems(listOf(item("new"), item("next")))
					player.play()
					feedback.starting()
				}
				feedback.awaitSettled()
			}
			runCurrent()
			assertTrue(player.playWhenReady)
			preparation.cancelFromUser(player::pause)
			runCurrent()
			assertFalse(player.playWhenReady)
			assertEquals(2, player.mediaItemCount)
			assertEquals("new", player.currentMediaItem?.mediaId)
			player.play()
			preparation.cancelFromUser(player::pause)
			assertTrue(player.playWhenReady, "A stale Back must not pause later playback")
		} finally { preparation.cancel(); player.release() }
	}

	private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("file:///music/$id.flac").build()
}
