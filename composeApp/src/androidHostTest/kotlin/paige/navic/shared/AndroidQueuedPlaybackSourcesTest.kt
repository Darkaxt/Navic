package paige.navic.shared

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class AndroidQueuedPlaybackSourcesTest {
	@Test
	fun internalBatchPreservesCurrentSourceShufflePositionMetadataAndPausedIntent() {
		val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			val items = (0..5).map {
				MediaItem.Builder().setMediaId("song-$it").setUri("https://server/song-$it")
					.setMediaMetadata(MediaMetadata.Builder().setTitle("Title $it").build()).build()
			}
			exo.setMediaItems(items, 2, 51_000L)
			val order = listOf(5, 2, 4, 0, 3, 1)
			exo.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), 0))
			exo.shuffleModeEnabled = true
			exo.pause()
			val player = stablePlaybackSessionPlayer(exo)
			val updates = items.mapIndexed { index, item ->
				QueuedPlaybackSourceUpdate(index, item.mediaId, item.localConfiguration!!.uri, "file:///music/$index.flac".toUri())
			}
			val args = queuedPlaybackSourceArgs("account", 7, updates)
			assertTrue(applyQueuedPlaybackSources(player, args, "account", 7))
			assertEquals(order, player.persistableShuffleOrder())
			assertEquals(items[2], player.currentMediaItem)
			assertEquals(51_000L, player.currentPosition)
			assertFalse(player.playWhenReady)
			for (index in listOf(0, 1, 3, 4, 5)) {
				assertEquals("file", player.getMediaItemAt(index).localConfiguration!!.uri.scheme)
				assertEquals(items[index].mediaMetadata, player.getMediaItemAt(index).mediaMetadata)
			}
			var timelineChanges = 0
			player.addListener(object : Player.Listener {
				override fun onTimelineChanged(timeline: Timeline, reason: Int) { timelineChanges++ }
			})
			assertTrue(applyQueuedPlaybackSources(player, args, "account", 7))
			assertEquals(0, timelineChanges, "Duplicate/stale URI updates must be no-ops")
		} finally { exo.release() }
	}

	@Test
	fun retiredAccountReplacedQueueAndChangedUriRejectStaleUpdates() {
		val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
		try {
			val items = (0..2).map { MediaItem.Builder().setMediaId("song-$it").setUri("https://server/$it").build() }
			player.setMediaItems(items)
			val args = queuedPlaybackSourceArgs("account", 7, listOf(
				QueuedPlaybackSourceUpdate(1, "song-1", items[1].localConfiguration!!.uri, "file:///music/1.flac".toUri())))
			assertFalse(applyQueuedPlaybackSources(player, args, "other-account", 7))
			assertFalse(applyQueuedPlaybackSources(player, args, "account", 8))
			player.replaceMediaItem(1, items[1].buildUpon().setUri("https://server/new-quality").build())
			assertTrue(applyQueuedPlaybackSources(player, args, "account", 7))
			assertEquals("https://server/new-quality", player.getMediaItemAt(1).localConfiguration!!.uri.toString())
			val replaced = items[1].buildUpon().setMediaId("different-song").build()
			player.replaceMediaItem(1, replaced)
			assertTrue(applyQueuedPlaybackSources(player, args, "account", 7))
			assertEquals(replaced, player.getMediaItemAt(1))
		} finally { player.release() }
	}
}
