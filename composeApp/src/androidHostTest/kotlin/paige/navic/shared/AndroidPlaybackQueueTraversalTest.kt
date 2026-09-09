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
