package paige.navic.domain.repositories

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import paige.navic.data.remote.NetworkClientFactory
import paige.navic.data.remote.aurral.AURRAL_JSON
import paige.navic.data.remote.aurral.AurralArtistSearchResponseDto
import paige.navic.data.remote.aurral.AurralAuthMeDto
import paige.navic.data.remote.aurral.AurralHealthDto
import paige.navic.data.remote.aurral.AurralRequestDto
import paige.navic.data.remote.aurral.AurralWeeklyFlowStatusDto
import paige.navic.data.remote.aurral.KtorAurralApiClient
import paige.navic.data.remote.aurral.aurralArtistSearchResult
import paige.navic.data.remote.aurral.aurralCurrentFlowUrl
import paige.navic.data.remote.aurral.aurralServiceStatus
import paige.navic.ui.screens.activity.aurralAcquisitionQueueItemControls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AurralV3CompatibilityTest {
	private val mbid = "11111111-1111-1111-1111-111111111111"
	private val baseUrl = "https://aurral.example.com"
	private val json = headersOf(HttpHeaders.ContentType, "application/json")
	private val v3Bootstrap = """{"status":"ok","appVersion":"3.0.1","api":{"version":2,"features":["playlists"]}}"""

	@Test
	fun apiVersionComesFromBootstrapAndIsRememberedOnlyWhenDefinitive() = runTest {
		for ((status, body, expected, cached) in listOf(
			Quad(HttpStatusCode.OK, v3Bootstrap, 2, true),
			Quad(HttpStatusCode.OK, """{"status":"ok","appVersion":"2.13.0"}""", 1, true),
			Quad(HttpStatusCode.NotFound, "Not found", 1, true),
			Quad(HttpStatusCode.ServiceUnavailable, "down", 1, false)
		)) {
			var calls = 0
			val engine = MockEngine { request ->
				assertEquals("/api/health/bootstrap", request.url.encodedPath)
				calls++
				respond(body, status, json)
			}
			try {
				val api = KtorAurralApiClient(NetworkClientFactory { engine })
				assertEquals(expected, api.fetchApiVersion(baseUrl, emptyMap()), body)
				assertEquals(expected, api.fetchApiVersion(baseUrl, emptyMap()), body)
				assertEquals(if (cached) 1 else 2, calls, body)
			} finally { engine.close() }
		}
	}

	@Test
	fun v3MonitoringSendsTheMonitoringBodyForUpdateAndAdd() = runTest {
		for (existing in listOf(true, false)) {
			val writes = mutableListOf<Pair<HttpMethod, JsonObject>>()
			val engine = MockEngine { request ->
				when {
					request.url.encodedPath.endsWith("/api/health/bootstrap") -> respond(v3Bootstrap, headers = json)
					request.method == HttpMethod.Get ->
						respond("{}", if (existing) HttpStatusCode.OK else HttpStatusCode.NotFound, json)
					else -> {
						writes += request.method to Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
						respond("""{"foreignArtistId":"$mbid","monitored":false}""", headers = json)
					}
				}
			}
			try {
				val api = KtorAurralApiClient(NetworkClientFactory { engine })
				api.monitorArtist(
					baseUrl, emptyMap(), mbid,
					AurralArtistMonitorPayload(foreignArtistId = mbid, artistName = "Koji Kondo", monitorOption = "all", monitored = true)
				)
				val (method, body) = writes.single()
				val monitoring = Json.parseToJsonElement("""{"monitored":true}""")
				if (existing) {
					assertEquals(HttpMethod.Put, method)
					assertEquals(mapOf("monitoring" to monitoring), body.toMap())
				} else {
					assertEquals(HttpMethod.Post, method)
					assertEquals(setOf("foreignArtistId", "artistName", "monitoring"), body.keys)
					assertEquals(monitoring, body["monitoring"])
				}
			} finally { engine.close() }
		}
	}

	@Test
	fun v3RequestsCannotBeCancelledAndNoDeleteIsSent() = runTest {
		val methods = mutableListOf<HttpMethod>()
		val engine = MockEngine { request ->
			methods += request.method
			respond(v3Bootstrap, headers = json)
		}
		try {
			val api = KtorAurralApiClient(NetworkClientFactory { engine })
			assertFailsWith<IllegalStateException> {
				api.cancelAcquisitionRequest(baseUrl, emptyMap(), AurralAcquisitionDeleteTarget.Album("42"))
			}
			assertEquals(listOf(HttpMethod.Get), methods)
		} finally { engine.close() }
	}

	@Test
	fun v3StatusMapsPlaylistPermissionStaticPlaylistsAndNonCancellableRequests() {
		val authMe = AURRAL_JSON.decodeFromString<AurralAuthMeDto>(
			"""{"user":{"username":"darka","permissions":{"accessPlaylists":true}}}"""
		)
		val flows = AURRAL_JSON.decodeFromString<AurralWeeklyFlowStatusDto>(
			"""{"sharedPlaylists":[{"id":"a"}],"staticPlaylists":[{"id":"b"},{"id":"c"}]}"""
		)
		val requests = listOf(AurralRequestDto(id = "r1", albumId = "42", albumName = "Ocarina", artistMbid = mbid))

		val v3 = aurralServiceStatus(AurralHealthDto(), authMe, flows, requests, apiVersion = 2)
		assertEquals(2, v3.apiVersion)
		assertTrue(v3.accessFlow)
		assertEquals(2, v3.sharedPlaylistsCount)
		assertFalse(v3.acquisitionQueue.single().cancellable)
		assertFalse(aurralAcquisitionQueueItemControls(v3.acquisitionQueue.single()).canCancel)

		val legacyFlows = AURRAL_JSON.decodeFromString<AurralWeeklyFlowStatusDto>("""{"sharedPlaylists":[{"id":"a"}]}""")
		val v2 = aurralServiceStatus(AurralHealthDto(), authMe = null, weeklyFlow = legacyFlows, requests = requests)
		assertEquals(1, v2.sharedPlaylistsCount)
		assertTrue(aurralAcquisitionQueueItemControls(v2.acquisitionQueue.single()).canCancel)
	}

	@Test
	fun artistSearchReadsUnifiedItemsAndTheLegacyArtistsField() {
		for (body in listOf(
			"""{"items":[{"id":"$mbid","name":"Koji Kondo"}],"count":1,"offset":0}""",
			"""{"artists":[{"id":"$mbid","name":"Koji Kondo"}],"count":1,"offset":0}"""
		)) {
			val result = aurralArtistSearchResult(baseUrl, "koji", AURRAL_JSON.decodeFromString<AurralArtistSearchResponseDto>(body))
			assertEquals(listOf("Koji Kondo"), result.artists.map { it.name }, body)
		}
	}

	@Test
	fun persistedWeeklyFlowUrlsMoveToPlaylistsAndOldPathsAreGone() {
		assertEquals(
			"https://aurral.example.com/aurral/api/playlists/stream/job-1?token=t",
			aurralCurrentFlowUrl("https://aurral.example.com/aurral/api/weekly-flow/stream/job-1?token=t")
		)
		assertEquals(
			"https://aurral.example.com/api/playlists/artwork/p1/preview",
			aurralCurrentFlowUrl("https://aurral.example.com/api/weekly-flow/artwork/p1/preview")
		)
		val client = java.io.File("src/commonMain/kotlin/paige/navic/data/remote/aurral/AurralApiClient.kt").readText()
		assertFalse("api/weekly-flow" in client)
		assertFalse("api/search/artists" in client)
		val media = java.io.File("src/androidMain/kotlin/paige/navic/shared/AndroidMediaItemFactory.android.kt").readText()
		assertTrue("aurralCurrentFlowUrl(filePath)" in media)
	}

	private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
