package paige.navic.shared

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NavidromeDeferredTranscodeSeekTest {
	private val stream = "https://music.example/rest/stream?u=me&t=tok&s=salt&id=long&maxBitRate=192&format=opus"
	private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()

	@After
	fun release() = exo.release()

	/** A player whose current song is a live transcode (unseekable) until told otherwise. */
	private inner class LivePlayer(uri: String) : ForwardingPlayer(exo) {
		var seekable = false
		val seeks = mutableListOf<Pair<Int, Long>>()
		private val item = MediaItem.Builder().setMediaId("long").setUri(uri).build()

		init {
			exo.setMediaItem(item)
		}

		override fun isCurrentMediaItemSeekable() = seekable
		override fun getPlaybackState() = Player.STATE_READY
		override fun getCurrentTimeline(): Timeline =
			SinglePeriodTimeline(C.TIME_UNSET, seekable, false, false, null, exo.currentMediaItem!!)
		override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
			seeks += mediaItemIndex to positionMs
		}
	}

	private class QueuedExecutor : Executor {
		val tasks = ArrayDeque<Runnable>()
		override fun execute(command: Runnable) {
			tasks += command
		}
		fun runAll() {
			while (tasks.isNotEmpty()) tasks.removeFirst().run()
		}
	}

	private class Harness(val player: LivePlayer, lengths: List<Long?>) {
		val executor = QueuedExecutor()
		val probes = ArrayDeque(lengths)
		val replaced = mutableListOf<MediaItem>()
		val recorded = mutableListOf<Long>()
		val events = mutableListOf<String>()
		val seek = NavidromeDeferredTranscodeSeek(
			player = player,
			completedLength = { probes.removeFirstOrNull() },
			onCompletedLength = { _, length -> recorded += length },
			replaceItem = { _, item -> replaced += item },
			executor = executor,
			handler = Handler(Looper.getMainLooper()),
			policy = NavidromeDeferredTranscodeSeek.Policy(sleep = {}),
			log = { event, _, detail -> events += "$event $detail" },
		)

		fun settle() {
			executor.runAll()
			shadowOf(Looper.getMainLooper()).idle()
		}
	}

	@Test
	fun seekInLiveTranscodeWaitsForTheFinishedFileThenReloadsAtTheTarget() {
		val h = Harness(LivePlayer(stream), listOf(null, null, 14_000_291L))

		assertTrue(h.seek.interceptSeek(0, 300_000L), "the seek must not reach the player (it would restart at 0:00)")
		assertTrue(h.player.seeks.isEmpty())
		assertTrue(h.seek.interceptSeek(0, 420_000L), "a later seek replaces the waiting target")
		h.settle()

		assertEquals(listOf(14_000_291L), h.recorded)
		val reloaded = h.replaced.single()
		assertEquals("long", reloaded.mediaId)
		val reloadUri = reloaded.localConfiguration!!.uri
		assertEquals("1", reloadUri.getQueryParameter(NavidromeDeferredTranscodeSeek.RELOAD_PARAM))
		assertEquals(NavidromeStreamCacheKey.of(Uri.parse(stream)), NavidromeStreamCacheKey.of(reloadUri), "same cached bytes")
		assertEquals(listOf(0 to 420_000L), h.player.seeks)
		assertTrue(h.events.any { it.startsWith("seek-resumed") })
	}

	@Test
	fun seekableOriginalAndStartOfSongSeeksAreLeftToThePlayer() {
		val live = Harness(LivePlayer(stream), emptyList())
		assertFalse(live.seek.interceptSeek(0, 0L), "restarting the song is a normal seek")

		val seekable = Harness(LivePlayer(stream).apply { seekable = true }, emptyList())
		assertFalse(seekable.seek.interceptSeek(0, 60_000L))

		val original = Harness(LivePlayer("https://music.example/rest/stream?u=me&id=long&format=raw"), emptyList())
		assertFalse(original.seek.interceptSeek(0, 60_000L))

		val radio = Harness(LivePlayer("https://radio.example/live.mp3"), emptyList())
		assertFalse(radio.seek.interceptSeek(0, 60_000L))
	}

	@Test
	fun changingSongCancelsTheWaitingSeek() {
		val h = Harness(LivePlayer(stream), listOf(14_000_291L))
		assertTrue(h.seek.interceptSeek(0, 300_000L))
		h.seek.cancel()
		h.settle()
		assertTrue(h.replaced.isEmpty())
		assertTrue(h.player.seeks.isEmpty())
	}

	@Test
	fun recordContentLengthOnlyFillsAnUnknownLength() {
		val dir: File = Files.createTempDirectory("navic-deferred-seek").toFile()
		val cache = SimpleCache(dir, NoOpCacheEvictor(), StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext()))
		try {
			assertEquals(C.LENGTH_UNSET.toLong(), ContentMetadata.getContentLength(cache.getContentMetadata("k")))
			cache.recordContentLength("k", 100L)
			cache.recordContentLength("k", 50L)
			assertEquals(100L, ContentMetadata.getContentLength(cache.getContentMetadata("k")))
		} finally {
			cache.release()
			dir.deleteRecursively()
		}
	}

	@Test
	fun sessionPlayerRoutesSeeksThroughTheInterceptor() {
		val intercepted = mutableListOf<Long>()
		val player = stablePlaybackSessionPlayer(exo, interceptSeek = { _, positionMs -> intercepted += positionMs; true })
		exo.setMediaItem(MediaItem.fromUri(stream))
		player.seekTo(5_000L)
		player.seekTo(0, 6_000L)
		assertEquals(listOf(5_000L, 6_000L), intercepted)
		assertEquals(0L, exo.currentPosition)
	}
}
