package paige.navic.shared

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NavidromePlaybackCacheTest {
	private val server = "https://music.example/navidrome/rest/stream"
	private fun transcoded(id: String, salt: String = "s1") = Uri.parse("$server?u=me&t=tok$salt&s=$salt&id=$id&maxBitRate=192&format=opus")
	private fun original(id: String, salt: String = "s1") = Uri.parse("$server?u=me&t=tok$salt&s=$salt&id=$id&format=raw")

	private val dir: File = Files.createTempDirectory("navic-playback-cache").toFile()
	private val cache = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(64L * 1024 * 1024), StandaloneDatabaseProvider(RuntimeEnvironment.getApplication()))

	@After
	fun release() {
		cache.release()
		dir.deleteRecursively()
	}

	/** Navidrome stand-in: transcodes are 4 KiB of 'T', originals 16 KiB of 'O'. */
	private class FakeServer : DataSource.Factory {
		val opened = mutableListOf<String>()
		override fun createDataSource(): DataSource = object : DataSource {
			private var data = ByteArray(0)
			private var pos = 0
			private var uri: Uri? = null
			override fun open(dataSpec: DataSpec): Long {
				uri = dataSpec.uri
				val isOriginal = dataSpec.uri.getQueryParameter("format") == "raw"
				opened += "${dataSpec.uri.getQueryParameter("id")}:${if (isOriginal) "original" else "transcode"}@${dataSpec.position}"
				data = ByteArray(if (isOriginal) 16 * 1024 else 4 * 1024) { (if (isOriginal) 'O' else 'T').code.toByte() }
				pos = dataSpec.position.toInt()
				return data.size - dataSpec.position
			}
			override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
				if (pos >= data.size) return C.RESULT_END_OF_INPUT
				val n = minOf(length, data.size - pos)
				System.arraycopy(data, pos, buffer, offset, n)
				pos += n
				return n
			}
			override fun getUri(): Uri? = uri
			override fun close() = Unit
			override fun addTransferListener(transferListener: TransferListener) = Unit
		}
	}

	private fun readAll(source: DataSource, uri: Uri): ByteArray {
		source.open(DataSpec(uri))
		val out = java.io.ByteArrayOutputStream()
		val buffer = ByteArray(1024)
		while (true) {
			val n = source.read(buffer, 0, buffer.size)
			if (n == C.RESULT_END_OF_INPUT) break
			out.write(buffer, 0, n)
		}
		source.close()
		return out.toByteArray()
	}

	private fun prefetcher(
		fake: FakeServer,
		unmetered: Boolean,
		downloaded: Set<String> = emptySet(),
		events: MutableList<String> = mutableListOf(),
		isInUse: (String) -> Boolean = { false },
	) =
		NavidromeStreamPrefetcher(
			cache = cache,
			upstreamFactory = fake,
			isUnmetered = { unmetered },
			originalUriForSong = { id -> original(id, salt = "fresh") },
			isDownloaded = { it in downloaded },
			onTransfer = { event, key, outcome -> events += "$event:${key.songId}:${key.quality}:$outcome" },
			isInUse = isInUse,
			executor = Executor(Runnable::run),
		)

	@Test
	fun cacheKeysIgnoreAuthAndDistinguishQuality() {
		val a = NavidromeStreamCacheKey.of(transcoded("x", salt = "a"))
		val b = NavidromeStreamCacheKey.of(transcoded("x", salt = "b"))
		assertEquals(a, b)
		assertEquals("opus-192", a?.quality)
		assertEquals("original", NavidromeStreamCacheKey.of(original("x"))?.quality)
		assertEquals("original", NavidromeStreamCacheKey.of(Uri.parse("$server?u=me&id=x"))?.quality)
		assertEquals(a, NavidromeStreamCacheKey.parse(a!!.value))
		assertNull(NavidromeStreamCacheKey.of(Uri.parse("$server?u=me&id=x&format=opus&timeOffset=30")))
		assertNull(NavidromeStreamCacheKey.of(Uri.parse("https://radio.example/live.mp3")))
		assertFalse(a.value.contains("tok"))

		val upgraded = NavidromeStreamCacheKey.originalUri(transcoded("x"))
		assertEquals("raw", upgraded.getQueryParameter("format"))
		assertNull(upgraded.getQueryParameter("maxBitRate"))
		assertEquals("toks1", upgraded.getQueryParameter("t"))
	}

	@Test
	fun streamedSongReplaysFromTheCacheWithoutTheNetwork() {
		val fake = FakeServer()
		val factory = NavidromeStreamCacheDataSource.Factory(cache, fake, fake)

		val first = readAll(factory.createDataSource(), transcoded("song", salt = "a"))
		val second = readAll(factory.createDataSource(), transcoded("song", salt = "b"))

		assertContentEquals(first, second)
		assertEquals(listOf("song:transcode@0"), fake.opened)
	}

	@Test
	fun completeOriginalIsPlayedInsteadOfTheTranscode() {
		val fake = FakeServer()
		prefetcher(fake, unmetered = true).prefetch(transcoded("song"))
		val opens = mutableListOf<String>()
		val factory = NavidromeStreamCacheDataSource.Factory(cache, fake, fake) { requested, served, full ->
			opens += "${requested.quality}->${served.quality}:$full"
		}

		val bytes = readAll(factory.createDataSource(), transcoded("song", salt = "later"))

		assertEquals(16 * 1024, bytes.size)
		assertTrue(bytes.all { it == 'O'.code.toByte() })
		assertEquals(listOf("song:original@0"), fake.opened, "only the prefetch touched the network")
		assertEquals(listOf("opus-192->original:true"), opens)
	}

	/** Serves 4 KiB transcodes in 512-byte reads, calling [onRead] before each read; can be set to fail. */
	private class SlowServer(var failing: Boolean = false, val onRead: (readIndex: Int) -> Unit = {}) : DataSource.Factory {
		override fun createDataSource(): DataSource = object : DataSource {
			private var pos = 0
			private var reads = 0
			private var uri: Uri? = null
			override fun open(dataSpec: DataSpec): Long {
				if (failing) throw java.io.IOException("timeout")
				uri = dataSpec.uri
				pos = dataSpec.position.toInt()
				return 4096L - pos
			}
			override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
				onRead(reads++)
				if (pos >= 4096) return C.RESULT_END_OF_INPUT
				val n = minOf(length, 512, 4096 - pos)
				pos += n
				return n
			}
			override fun getUri(): Uri? = uri
			override fun close() = Unit
			override fun addTransferListener(transferListener: TransferListener) = Unit
		}
	}

	@Test
	fun aStreamDownloadingSlowerThanItPlaysIsUnreadyBeforeItStarts() {
		var now = 0L
		val results = mutableMapOf<String, Boolean>()
		lateinit var prefetcher: NavidromeStreamPrefetcher
		val uri = transcoded("slow")
		val server = SlowServer { readIndex ->
			if (readIndex == 1) {
				now = 10_000L
				results["tooEarlyToJudge"] = prefetcher.isUnready(uri, 60_000L)
			}
			if (readIndex == 2) {
				// 1 KiB of 4 KiB arrived in 20 s.
				now = 20_000L
				results["shortSong"] = prefetcher.isUnready(uri, 60_000L)
				results["longSong"] = prefetcher.isUnready(uri, 100_000L)
				results["unknownLength"] = prefetcher.isUnready(uri, C.TIME_UNSET)
			}
		}
		prefetcher = NavidromeStreamPrefetcher(cache, server, { false }, { null }, { false },
			executor = Executor(Runnable::run), clock = { now })

		prefetcher.prefetch(uri)

		assertEquals(false, results["tooEarlyToJudge"])
		assertEquals(true, results["shortSong"], "1 KiB + 20 s rate x 60 s cannot reach 4 KiB in time")
		assertEquals(false, results["longSong"], "over 100 s the rest arrives before playback reaches it")
		assertEquals(false, results["unknownLength"])
		assertFalse(prefetcher.isUnready(uri, 60_000L), "fully cached afterwards")
		assertFalse(prefetcher.isUnready(transcoded("never-prefetched"), 60_000L), "no evidence, no skip")
	}

	@Test
	fun aFailedPrefetchIsUnreadyUntilARetryCachesIt() {
		val server = SlowServer(failing = true)
		val prefetcher = NavidromeStreamPrefetcher(cache, server, { false }, { null }, { false },
			executor = Executor(Runnable::run))
		val uri = transcoded("flaky")

		prefetcher.defer(uri)
		assertTrue(prefetcher.isUnready(uri, 60_000L))

		server.failing = false
		prefetcher.retry()
		assertFalse(prefetcher.isUnready(uri, 60_000L))
		assertTrue(cache.isFullyCached(NavidromeStreamCacheKey.of(uri)!!.value))
	}

	@Test
	fun prefetchUsesStreamingQualityOnMeteredNetworksAndSkipsDownloadsAndCachedSongs() {
		val fake = FakeServer()
		val events = mutableListOf<String>()
		val metered = prefetcher(fake, unmetered = false, downloaded = setOf("local"), events = events)

		metered.prefetch(transcoded("next"))
		metered.prefetch(transcoded("next", salt = "again"))
		metered.prefetch(transcoded("local"))

		assertEquals(listOf("next:transcode@0"), fake.opened)
		assertEquals(listOf("prefetch:next:opus-192:complete"), events)
	}

	@Test
	fun unmeteredUpgradeReplacesCompletedTranscodesWithOriginals() {
		val fake = FakeServer()
		val factory = NavidromeStreamCacheDataSource.Factory(cache, fake, fake)
		readAll(factory.createDataSource(), transcoded("heard"))
		val events = mutableListOf<String>()

		prefetcher(fake, unmetered = false, events = events).upgradeCachedTranscodes()
		assertTrue(events.isEmpty(), "no upgrade on metered networks")

		prefetcher(fake, unmetered = true, events = events).upgradeCachedTranscodes()

		assertEquals(listOf("upgrade:heard:original:complete"), events)
		assertEquals(setOf(NavidromeStreamCacheKey.of(original("heard"))!!.value), cache.keys)
		assertTrue(cache.isFullyCached(NavidromeStreamCacheKey.of(original("heard"))!!.value))
	}

	@Test
	fun openSongKeepsItsVariantUntilThePlayerReleasesIt() {
		val fake = FakeServer()
		val opens = mutableListOf<String>()
		val factory = NavidromeStreamCacheDataSource.Factory(cache, fake, fake) { requested, served, _ ->
			opens += "${requested.quality}->${served.quality}"
		}
		val transcodeBytes = readAll(factory.createDataSource(), transcoded("song"))

		// The original finishes downloading while the transcode is still playing.
		prefetcher(fake, unmetered = true, isInUse = factory::isInUse).upgradeCachedTranscodes()
		assertTrue(cache.isFullyCached(NavidromeStreamCacheKey.of(original("song"))!!.value))
		assertTrue(cache.isFullyCached(NavidromeStreamCacheKey.of(transcoded("song"))!!.value), "in-use transcode kept")

		// A seek or load retry reopens the same request: it must get the same bytes the decoder expects.
		assertContentEquals(transcodeBytes, readAll(factory.createDataSource(), transcoded("song", salt = "seek")))

		// Once the song leaves the current/next window, a replay uses the original.
		factory.retainPinsFor(setOf("other"))
		val replay = readAll(factory.createDataSource(), transcoded("song", salt = "replay"))
		assertTrue(replay.all { it == 'O'.code.toByte() })
		assertEquals(listOf("opus-192->opus-192", "opus-192->opus-192", "opus-192->original"), opens)
		assertEquals(listOf("song:transcode@0", "song:original@0"), fake.opened)
	}

	@Test
	fun nonNavidromeStreamsBypassTheCache() {
		val fake = FakeServer()
		val factory = NavidromeStreamCacheDataSource.Factory(cache, fake, fake)
		readAll(factory.createDataSource(), Uri.parse("https://radio.example/live.mp3?id=r"))
		assertTrue(cache.keys.isEmpty())
	}

	@Test
	fun playbackServiceReadsStreamsThroughTheCacheAndPrefetchesTheNextSong() {
		val player = File("src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt").readText()
		assertTrue(player.contains("DefaultDataSource.Factory(this, cachedStreamDataSourceFactory)"))
		assertTrue(player.contains("prefetcher.prefetchNext(player)"))
		assertTrue(player.contains("skipUnreadyStreams(player, prefetcher)"))
		assertTrue(player.contains("prefetcher.retry()"))
		assertTrue(player.contains("prefetcher.upgradeCachedTranscodes()"))
		assertTrue(player.contains("isInUse = cachedStreamDataSourceFactory::isInUse"))
		assertEquals(2, Regex("""\n\s*releaseStreamPins\(\)""").findAll(player).count(), "pins released on transitions and queue changes")
		assertTrue(player.contains("streamPrefetcher?.close()"))
	}
}
