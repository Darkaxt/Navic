package paige.navic.domain.repositories

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import paige.navic.data.remote.NetworkClientFactory
import paige.navic.data.remote.aurral.AurralArtistMonitoringOutcome
import paige.navic.data.remote.aurral.KtorAurralApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AurralMonitoringApiTest {
	private val mbid = "11111111-1111-1111-1111-111111111111"
	private val baseUrl = "https://aurral.example.com"
	private fun payload(monitored: Boolean) = AurralArtistMonitorPayload(
		foreignArtistId = mbid, artistName = "Koji Kondo",
		monitorOption = if (monitored) "all" else "none", monitored = monitored
	)

	@Test
	fun explicitMatchingPutConfirmsBothDirectionsWithoutVerificationGet() = runTest {
		for (monitored in listOf(true, false)) {
			val methods = mutableListOf<HttpMethod>()
			val engine = MockEngine { request ->
				methods += request.method
				respond(
					"""{"foreignArtistId":"$mbid","monitored":$monitored}""",
					headers = headersOf(HttpHeaders.ContentType, "application/json")
				)
			}
			try {
				val api = KtorAurralApiClient(NetworkClientFactory { engine })
				assertEquals(AurralArtistMonitoringOutcome.Confirmed(monitored), api.monitorArtist(baseUrl, emptyMap(), mbid, payload(monitored)))
				assertEquals(listOf(HttpMethod.Get, HttpMethod.Put), methods)
			} finally { engine.close() }
		}
	}

	@Test
	fun ambiguousPutNeverConfirmsIncludingDefaultFalseAndConflictingIdentity() = runTest {
		for (body in listOf(
			"", "not json", "{}", """{"monitored":false}""",
			"""{"mbid":"$mbid"}""",
			"""{"mbid":"different","monitored":false}""",
			"""{"mbid":"$mbid","foreignArtistId":"different","monitored":false}""",
			"""{"mbid":"$mbid","monitored":true}"""
		)) {
			val engine = MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json")) }
			try {
				val api = KtorAurralApiClient(NetworkClientFactory { engine })
				assertEquals(AurralArtistMonitoringOutcome.AwaitingConfirmation, api.monitorArtist(baseUrl, emptyMap(), mbid, payload(false)), body)
			} finally { engine.close() }
		}
	}

	@Test
	fun acceptedPostOrPutRemainsPendingEvenWithMatchingArtistFields() = runTest {
		for (adding in listOf(true, false)) {
			val methods = mutableListOf<HttpMethod>()
			val engine = MockEngine { request ->
				methods += request.method
				respond(
					"""{"queued":true,"foreignArtistId":"$mbid","monitored":true}""",
					status = if (request.method == HttpMethod.Get) {
						if (adding) HttpStatusCode.NotFound else HttpStatusCode.OK
					} else HttpStatusCode.Accepted,
					headers = headersOf(HttpHeaders.ContentType, "application/json")
				)
			}
			try {
				val api = KtorAurralApiClient(NetworkClientFactory { engine })
				assertEquals(AurralArtistMonitoringOutcome.AwaitingConfirmation, api.monitorArtist(baseUrl, emptyMap(), mbid, payload(true)))
				assertEquals(listOf(HttpMethod.Get, if (adding) HttpMethod.Post else HttpMethod.Put), methods)
			} finally { engine.close() }
		}
	}

	@Test
	fun postExistingArtistRaceCompletesOriginalMutationWithOnePut() = runTest {
		val methods = mutableListOf<HttpMethod>()
		val engine = MockEngine { request ->
			methods += request.method
			when (request.method) {
				HttpMethod.Get -> respond("{}", HttpStatusCode.NotFound)
				HttpMethod.Post -> respond("""{"queued":false,"foreignArtistId":"$mbid","artist":{"monitored":false}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
				else -> respond("""{"mbid":"$mbid","monitored":true}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
			}
		}
		try {
			val api = KtorAurralApiClient(NetworkClientFactory { engine })
			assertEquals(AurralArtistMonitoringOutcome.Confirmed(true), api.monitorArtist(baseUrl, emptyMap(), mbid, payload(true)))
			assertEquals(listOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Put), methods)
		} finally { engine.close() }
	}

	@Test
	fun confirmationReadRevalidatesAndDoesNotInferFalseFromMissingStateOr404() = runTest {
		for ((status, body, expected) in listOf(
			Triple(HttpStatusCode.NotFound, "{}", null),
			Triple(HttpStatusCode.Accepted, """{"mbid":"$mbid","monitored":true}""", null),
			Triple(HttpStatusCode.OK, """{"mbid":"$mbid"}""", null),
			Triple(HttpStatusCode.OK, """{"mbid":"other","monitored":false}""", null),
			Triple(HttpStatusCode.OK, """{"mbid":"$mbid","monitored":false}""", false),
			Triple(HttpStatusCode.OK, """{"mbid":"$mbid","monitored":true}""", true)
		)) {
			val engine = MockEngine { request ->
				assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
				respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
			}
			try {
				val api = KtorAurralApiClient(NetworkClientFactory { engine })
				assertEquals(expected, api.fetchArtistMonitoringConfirmation(baseUrl, emptyMap(), mbid))
			} finally { engine.close() }
		}
	}

	@Test
	fun configurationGuardPreventsEveryFollowupWriteAfterInvalidation() = runTest {
		for (invalidateAt in listOf("existing-get", "missing-get", "existing-post")) {
			var current = true
			val methods = mutableListOf<HttpMethod>()
			val engine = MockEngine { request ->
				methods += request.method
				if (request.method == HttpMethod.Get) {
					if (invalidateAt != "existing-post") current = false
					respond("{}", if (invalidateAt == "existing-get") HttpStatusCode.OK else HttpStatusCode.NotFound)
				} else {
					current = false
					respond("""{"queued":false,"foreignArtistId":"$mbid"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
				}
			}
			try {
				val api = KtorAurralApiClient(NetworkClientFactory { engine })
				assertFailsWith<CancellationException> {
					api.monitorArtist(baseUrl, emptyMap(), mbid, payload(true)) {
						if (!current) throw CancellationException("configuration changed")
					}
				}
				assertEquals(if (invalidateAt == "existing-post") listOf(HttpMethod.Get, HttpMethod.Post) else listOf(HttpMethod.Get), methods)
			} finally { engine.close() }
		}
	}

	@Test
	fun cancelledMutationDoesNotReturnAnAcceptedOutcome() = runTest {
		val engine = MockEngine { request ->
			if (request.method == HttpMethod.Put) throw CancellationException("cancelled PUT")
			respond("{}")
		}
		try {
			val api = KtorAurralApiClient(NetworkClientFactory { engine })
			assertFailsWith<CancellationException> { api.monitorArtist(baseUrl, emptyMap(), mbid, payload(true)) }
		} finally { engine.close() }
	}
}
