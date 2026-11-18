package paige.navic.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class SubsonicScrobbleTimeFixTest {
	@Test
	fun scrobbleTimeIsSentAsEpochMillis(): Unit = runBlocking {
		val urls = mutableListOf<Url>()
		val client = HttpClient(MockEngine { request -> urls += request.url; respond("{}") }) {
			install(SubsonicScrobbleTimeFix)
		}
		val playedAt = Instant.fromEpochMilliseconds(1_763_500_000_123L)

		client.get("https://music.example/rest/scrobble") {
			parameter("id", "song-1")
			parameter("time", playedAt)
			parameter("submission", true)
		}
		client.get("https://music.example/rest/getAlbum") { parameter("time", playedAt) }

		assertEquals("1763500000123", urls[0].parameters["time"])
		assertEquals("song-1", urls[0].parameters["id"])
		assertEquals(playedAt.toString(), urls[1].parameters["time"], "only scrobble is rewritten")
	}

	@Test
	fun helpersKeepValidValuesAndMatchViewSuffix() {
		assertEquals("42", subsonicEpochMillisTime("42"))
		assertEquals("garbage", subsonicEpochMillisTime("garbage"))
		assertTrue(isSubsonicScrobblePath("/rest/scrobble.view"))
		assertFalse(isSubsonicScrobblePath("/rest/scrobbles"))
	}

	@Test
	fun theSubsonicClientInstallsTheFixAndQueuedScrobblesKeepTheirPlayTime() {
		val factory = java.io.File("src/commonMain/kotlin/paige/navic/data/remote/SubsonicClientFactory.kt").readText()
		val sync = java.io.File("src/commonMain/kotlin/paige/navic/domain/manager/SyncManager.kt").readText()
		assertTrue("install(SubsonicScrobbleTimeFix)" in factory)
		assertTrue("action.createdAtEpochMs.takeIf { it > 0 }" in sync)
	}
}
