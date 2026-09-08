package paige.navic.domain.repositories

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LyricsHttpFailureTest {
	@Test
	fun serviceAndAuthenticationFailuresAreNotAbsence() = runBlocking {
		for (status in listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.TooManyRequests, HttpStatusCode.Unauthorized)) {
			val client = HttpClient(MockEngine { respond("failure", status) })
			try {
				val failure = assertFailsWith<IllegalStateException> {
					client.get("https://lyrics.example.test/lyrics").lyricsContentOrNull()
				}
				assertEquals("Lyrics provider returned HTTP ${status.value}", failure.message)
			} finally {
				client.close()
			}
		}
	}

	@Test
	fun successfulEmptyAndNotFoundResponsesAreAbsence() = runBlocking {
		val client = HttpClient(MockEngine { request ->
			if (request.url.encodedPath == "/missing") respond("not found", HttpStatusCode.NotFound)
			else respond("", HttpStatusCode.NoContent)
		})
		try {
			assertNull(client.get("https://lyrics.example.test/missing").lyricsContentOrNull())
			assertEquals("", client.get("https://lyrics.example.test/empty").lyricsContentOrNull())
		} finally {
			client.close()
		}
	}

	@Test
	fun successfulFallbackEndpointRecoversFromServiceFailure() = runBlocking {
		var calls = 0
		val client = HttpClient(MockEngine {
			calls++
			if (calls == 1) respond("unavailable", HttpStatusCode.ServiceUnavailable)
			else respond("[00:01.00]Recovered lyrics", HttpStatusCode.OK)
		})
		try {
			val lyrics = fetchFirstAvailableLyrics(listOf("primary", "fallback")) {
				client.get("https://lyrics.example.test/$it").lyricsContentOrNull()
			}
			assertEquals("[00:01.00]Recovered lyrics", lyrics)
			assertEquals(2, calls)
		} finally {
			client.close()
		}
	}
}
