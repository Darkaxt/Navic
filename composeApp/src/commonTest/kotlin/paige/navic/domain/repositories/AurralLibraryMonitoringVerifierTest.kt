package paige.navic.domain.repositories

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AurralLibraryMonitoringVerifierTest {
	private val cellos = "c8e94c78-0e17-4d9a-b1f6-96e1b22ebe97"
	private val other = "11111111-2222-3333-4444-555555555555"

	@Test
	fun unmonitoredListRowsAreConfirmedPerArtistAndTrustedRowsAreNot(): Unit = runBlocking {
		val lookups = mutableListOf<String>()
		val verifier = AurralLibraryMonitoringVerifier(nowMillis = { 0L })
		val artists = listOf(
			AurralDiscoverArtist(id = cellos, name = "2CELLOS", monitored = false),
			AurralDiscoverArtist(id = other, name = "Known", monitored = true),
			AurralDiscoverArtist(id = "not-an-mbid", name = "Local", monitored = false)
		)

		val verified = verifier.verify(artists) { mbid -> lookups += mbid; true }

		assertEquals(listOf(true, true, false), verified.map { it.monitored })
		assertEquals(listOf(cellos), lookups)
	}

	@Test
	fun failedLookupsKeepTheListValueAndAnswersAreRememberedUntilTheTtl(): Unit = runBlocking {
		var now = 0L
		var calls = 0
		val verifier = AurralLibraryMonitoringVerifier(nowMillis = { now })
		val artists = listOf(
			AurralDiscoverArtist(id = cellos, name = "2CELLOS", monitored = false),
			AurralDiscoverArtist(id = other, name = "Broken", monitored = false)
		)
		val lookup: suspend (String) -> Boolean? = { mbid ->
			calls++
			if (mbid == other) error("HTTP 500") else true
		}

		assertEquals(listOf(true, false), verifier.verify(artists, lookup).map { it.monitored })
		assertEquals(2, calls)
		now += AURRAL_VERIFIED_MONITORING_TTL.inWholeMilliseconds - 1
		verifier.verify(artists, lookup)
		assertEquals(3, calls, "only the failed artist is asked again")
		now += 2
		verifier.verify(artists, lookup)
		assertEquals(5, calls)
	}
}
