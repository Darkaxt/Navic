package paige.navic.shared

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.domain.models.restoredShuffleOrder
import paige.navic.ui.core.PlayerUiState
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AndroidPlaybackQueueTraversalTest {
	@Test
	fun sameIdentitySourceReplacementPreservesTheActualSessionShuffleOrder() {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			val items = (0..3).map { MediaItem.Builder().setMediaId("song-$it").setUri("https://server/song-$it.mp3").build() }
			player.setMediaItems(items, 0, 51_000L)
			player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(3, 0, 2, 1), 0))
			player.shuffleModeEnabled = true
			val sessionPlayer = stablePlaybackSessionPlayer(player)
			sessionPlayer.replaceMediaItem(2, items[2].buildUpon().setUri("file:///music/song-2.mp3").build())
			assertEquals(listOf(3, 0, 2, 1), player.persistableShuffleOrder())
			assertEquals(0, player.currentMediaItemIndex)
			assertEquals(51_000L, player.currentPosition)
		} finally {
			player.release()
		}
	}

	@Test
	fun selectionAfterIdleErrorPreparesWithoutRebuildingQueueOrResumingPausedIntent() {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			player.setMediaItems((0..2).map { MediaItem.Builder().setMediaId("song-$it").setUri("file:///song-$it.mp3").build() })
			player.pause()
			assertEquals(Player.STATE_IDLE, player.playbackState)
			player.seekTo(1, 0L)
			player.prepareIdlePlaybackAfterSeek()
			assertEquals(Player.STATE_BUFFERING, player.playbackState)
			assertEquals(1, player.currentMediaItemIndex)
			assertEquals(3, player.mediaItemCount)
			assertEquals(false, player.playWhenReady)
		} finally {
			player.release()
		}
	}

	@Test
	fun repeatOneProjectionDoesNotAlterPersistedTimelineTraversalAcrossRestart() {
		val original = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		val restored = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			val items = (0..3).map { MediaItem.Builder().setMediaId("song-$it").setUri("file:///song-$it.mp3").build() }
			original.setMediaItems(items, 0, 0)
			original.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(intArrayOf(3, 0, 2, 1), 0))
			original.shuffleModeEnabled = true
			for (repeatMode in listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE)) {
				original.repeatMode = repeatMode
				val snapshot = PlayerUiState(currentIndex = original.currentMediaItemIndex,
					upcomingIndexes = original.upcomingMediaItemIndexes(), shuffleOrder = original.persistableShuffleOrder(),
					isShuffleEnabled = true, repeatMode = repeatMode)
				if (repeatMode == Player.REPEAT_MODE_ONE) assertEquals(listOf(0), snapshot.upcomingIndexes)
				val saved = Json.decodeFromString<PlayerUiState>(Json.encodeToString(snapshot))
				val order = restoredShuffleOrder(items.size, saved.currentIndex, saved.upcomingIndexes, saved.shuffleOrder)!!
				assertEquals(listOf(3, 0, 2, 1), order)
				restored.setMediaItems(items, saved.currentIndex, 0)
				restored.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), 0))
				restored.shuffleModeEnabled = true
				restored.repeatMode = Player.REPEAT_MODE_OFF
				assertEquals(listOf(2, 1), restored.upcomingMediaItemIndexes())
				assertEquals(order, restored.persistableShuffleOrder())
			}
		} finally {
			original.release()
			restored.release()
		}
	}
}
