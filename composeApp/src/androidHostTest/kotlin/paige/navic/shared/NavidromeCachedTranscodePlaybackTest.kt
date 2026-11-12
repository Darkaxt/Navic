package paige.navic.shared

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InterruptedIOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class NavidromeCachedTranscodePlaybackTest {
	private val streamUri = Uri.parse("https://music.example/rest/stream?id=song42&u=user&t=token&s=salt&maxBitRate=192&format=opus")

	/** Simulates Navidrome: live (no length, no ranges) until the cache completes after [liveHeadProbes] HEADs. */
	private class FakeNavidrome(var liveHeadProbes: Int, val cachedLength: Long = 5_735_821L) {
		var headProbes = 0
		var gets = 0
		val log = mutableListOf<String>()
		var cacheComplete = false

		fun source(): HttpDataSource = object : HttpDataSource {
			private var headers: Map<String, List<String>> = emptyMap()
			private var openUri: Uri? = null
			override fun open(dataSpec: DataSpec): Long {
				openUri = dataSpec.uri
				if (dataSpec.httpMethod == DataSpec.HTTP_METHOD_HEAD) {
					headProbes++
					if (headProbes > liveHeadProbes) cacheComplete = true
					log += "HEAD cached=$cacheComplete"
				} else {
					gets++
					log += "GET pos=${dataSpec.position} cached=$cacheComplete"
				}
				return if (cacheComplete) {
					headers = mapOf("Accept-Ranges" to listOf("bytes"))
					cachedLength - dataSpec.position
				} else {
					headers = mapOf("Accept-Ranges" to listOf("none"))
					C.LENGTH_UNSET.toLong()
				}
			}
			override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
			override fun getUri(): Uri? = openUri
			override fun getResponseHeaders(): Map<String, List<String>> = headers
			override fun close() { log += "close" }
			override fun addTransferListener(transferListener: TransferListener) = Unit
			override fun setRequestProperty(name: String, value: String) = Unit
			override fun clearRequestProperty(name: String) = Unit
			override fun clearAllRequestProperties() = Unit
			override fun getResponseCode(): Int = 200
		}
	}

	private class FakeClock {
		var now = 0L
		val sleep: (Long) -> Unit = { now += it }
	}

	private fun policy(clock: FakeClock, fallbacks: MutableList<Pair<String?, Long>> = mutableListOf()) =
		NavidromeCachedTranscodeDataSource.Policy(
			readyTimeoutMs = 2_000, pollIntervalMs = 250, maxConsecutiveFallbacks = 2,
			clock = { clock.now }, sleep = clock.sleep,
			onUncachedFallback = { id, waited -> fallbacks += id to waited }
		)

	private fun dataSource(server: FakeNavidrome, policy: NavidromeCachedTranscodeDataSource.Policy) =
		NavidromeCachedTranscodeDataSource(server.source(), server::source, policy)

	@Test
	fun coldTranscodeSwitchesToTheCompletedCacheWithExactLength() {
		val server = FakeNavidrome(liveHeadProbes = 2)
		val clock = FakeClock()
		val source = dataSource(server, policy(clock))

		val length = source.open(DataSpec(streamUri))

		assertEquals(5_735_821L, length)
		assertEquals(3, server.headProbes)
		assertEquals(2, server.gets)
		assertEquals(listOf("GET pos=0 cached=false", "HEAD cached=false", "close", "HEAD cached=false", "close",
			"HEAD cached=true", "close", "close", "GET pos=0 cached=true"), server.log)
		assertTrue(source.responseHeaders.acceptsByteRanges())
	}

	@Test
	fun alreadyCachedOrRawStreamsAreNotProbed() {
		val server = FakeNavidrome(liveHeadProbes = 0).apply { cacheComplete = true }
		val source = dataSource(server, policy(FakeClock()))

		assertEquals(5_735_821L, source.open(DataSpec(streamUri)))
		assertEquals(0, server.headProbes)
		assertEquals(1, server.gets)
	}

	@Test
	fun rangeRequestsAndOtherUrlsPassThrough() {
		val server = FakeNavidrome(liveHeadProbes = 100)
		val source = dataSource(server, policy(FakeClock()))

		source.open(DataSpec.Builder().setUri(streamUri).setPosition(1_000).build())
		source.close()
		source.open(DataSpec(Uri.parse("https://music.example/rest/getCoverArt?id=1")))

		assertEquals(0, server.headProbes)
	}

	@Test
	fun timeoutKeepsTheOpenLiveStreamAndReportsFallbackWithoutCredentials() {
		val server = FakeNavidrome(liveHeadProbes = 100)
		val clock = FakeClock()
		val fallbacks = mutableListOf<Pair<String?, Long>>()
		val source = dataSource(server, policy(clock, fallbacks))

		val length = source.open(DataSpec(streamUri))

		assertEquals(C.LENGTH_UNSET.toLong(), length)
		assertEquals(1, server.gets, "live stream must not be reopened after fallback")
		assertEquals(8, server.headProbes)
		assertEquals(listOf<Pair<String?, Long>>("song42" to 2_000L), fallbacks)
	}

	@Test
	fun repeatedFallbacksPauseProbingUntilTheCooldownExpires() {
		val server = FakeNavidrome(liveHeadProbes = 1_000)
		val clock = FakeClock()
		val policy = policy(clock)

		repeat(2) { dataSource(server, policy).open(DataSpec(streamUri)) }
		val probesAfterFallbacks = server.headProbes
		val start = clock.now
		dataSource(server, policy).open(DataSpec(streamUri))

		assertEquals(probesAfterFallbacks, server.headProbes)
		assertEquals(start, clock.now)

		clock.now += policy.fallbackCooldownMs
		val resumedLength = dataSource(FakeNavidrome(liveHeadProbes = 0), policy).open(DataSpec(streamUri))
		assertEquals(5_735_821L, resumedLength, "probing resumes after the cooldown")
		assertEquals(0, policy.consecutiveFallbacks.get())
	}

	@Test
	fun successfulCacheSwitchResetsFallbackCounter() {
		val clock = FakeClock()
		val policy = policy(clock)
		dataSource(FakeNavidrome(liveHeadProbes = 1_000), policy).open(DataSpec(streamUri))
		dataSource(FakeNavidrome(liveHeadProbes = 0), policy).open(DataSpec(streamUri))

		assertEquals(0, policy.consecutiveFallbacks.get())
	}

	@Test
	fun interruptionDuringWaitBecomesInterruptedIOException() {
		val server = FakeNavidrome(liveHeadProbes = 100)
		val policy = NavidromeCachedTranscodeDataSource.Policy(sleep = { throw InterruptedException() })
		val source = dataSource(server, policy)

		assertFailsWith<InterruptedIOException> { source.open(DataSpec(streamUri)) }
		assertTrue(Thread.interrupted(), "interrupt flag must be restored")
	}

	@Test
	fun streamUrlNoLongerRequestsEstimatedContentLength() {
		val source = File("src/androidMain/kotlin/paige/navic/shared/AndroidPlaybackStreamUri.android.kt").readText()
		assertFalse(source.contains("appendQueryParameter(\"estimateContentLength\""))
		val player = File("src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt").readText()
		assertTrue(player.contains("NavidromeCachedTranscodeDataSource.Factory("))
	}

	@Test
	fun warmerHeadsEachUpcomingTranscodeOnceIgnoringAuthSaltAndRawOrLocalItems() {
		val server = FakeNavidrome(liveHeadProbes = 100)
		val warmer = NavidromeTranscodeWarmer(object : HttpDataSource.Factory {
			override fun createDataSource(): HttpDataSource = server.source()
			override fun setDefaultRequestProperties(defaultRequestProperties: Map<String, String>) = this
		}, Runnable::run)
		val next = Uri.parse("https://music.example/rest/stream?id=next&u=user&t=a&s=1&maxBitRate=192&format=opus")
		val sameNextNewSalt = Uri.parse("https://music.example/rest/stream?id=next&u=user&t=b&s=2&maxBitRate=192&format=opus")
		val raw = Uri.parse("https://music.example/rest/stream?id=raw&u=user&t=a&s=1")
		val local = Uri.parse("file:///data/user/0/paige.navic/files/song.flac")

		warmer.warm(listOf(next, raw, local))
		warmer.warm(listOf(sameNextNewSalt))

		assertEquals(1, server.headProbes)
		assertEquals(0, server.gets)
		assertEquals(listOf("HEAD cached=false", "close"), server.log)
	}

	@Test
	fun serviceWarmsUpcomingItemsOnTransitionsAndQueueChanges() {
		val player = File("src/androidMain/kotlin/paige/navic/shared/MediaPlayer.android.kt").readText()
		assertTrue(player.contains("NavidromeTranscodeWarmer(httpDataSourceFactory"))
		assertEquals(2, player.split("transcodeWarmer.warmUpcoming(player)").size - 1)
	}
}
