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

	private fun prefetcher(fake: FakeServer, unmetered: Boolean, downloaded: Set<String> = emptySet(), events: MutableList<String> = mutableListOf()) =
		NavidromeStreamPrefetcher(
			cache = cache,
			upstreamFactory = fake,
			isUnmetered = { unmetered },
			originalUriForSong = { id -> original(id, salt = "fresh") },
			isDownloaded = { it in downloaded },
			onTransfer = { event, key, outcome -> events += "$event:${key.songId}:${key.quality}:$outcome" },
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
		assertTrue(player.contains("prefetcher.upgradeCachedTranscodes()"))
		assertTrue(player.contains("streamPrefetcher?.close()"))
	}
}
