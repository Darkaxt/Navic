package paige.navic.data.remote

import dev.zt64.subsonic.api.model.Album
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertSame

class SubsonicItemDateSanitizerTest {
	private val json = Json { ignoreUnknownKeys = true }

	private fun album(releaseDate: String, originalReleaseDate: String) =
		"""{"id":"al-1","name":"Undated","artist":"Someone","artistId":"ar-1","coverArt":"al-1",""" +
			""""songCount":3,"duration":600,"created":"2026-01-02T03:04:05Z","year":2001,""" +
			""""releaseDate":$releaseDate,"originalReleaseDate":$originalReleaseDate}"""

	@Test
	fun libraryRejectsEmptyItemDateUntilSanitized() {
		val raw = album("{}", "{}")
		assertFails { json.decodeFromString(Album.serializer(), raw) }

		val decoded = json.decodeFromString(Album.serializer(), sanitizeSubsonicItemDates(raw))
		assertEquals("Undated", decoded.name)
		assertNull(decoded.releaseDate)
		assertNull(decoded.originalReleaseDate)
	}

	@Test
	fun keepsCompleteDatesUnchanged() {
		val raw = album("""{"year":2001,"month":4,"day":2}""", """{"year":1999,"month":12,"day":31}""")
		assertSame(raw, sanitizeSubsonicItemDates(raw))
		val decoded = json.decodeFromString(Album.serializer(), raw)
		assertEquals(2001, decoded.releaseDate?.year)
		assertEquals(1999, decoded.originalReleaseDate?.year)
	}

	@Test
	fun yearOnlyDatesAreDroppedSoTheAlbumStillDecodes() {
		val raw = album("""{"year":2001}""", """{"year":1999,"month":4,"day":2}""")
		assertFails { json.decodeFromString(Album.serializer(), raw) }
		val decoded = json.decodeFromString(Album.serializer(), sanitizeSubsonicItemDates(raw))
		assertNull(decoded.releaseDate)
		assertEquals(1999, decoded.originalReleaseDate?.year)
		assertEquals(2001, decoded.year)
	}

	@Test
	fun removesOnlyPartialDatesInsideNestedResponses() {
		val raw = """{"subsonic-response":{"status":"ok","albumList2":{"album":[""" +
			album("{}", """{"year":1999,"month":1,"day":2}""") + "," + album("""{"month":5}""", "{ }") + "]}}}"
		val sanitized = sanitizeSubsonicItemDates(raw)
		val albums = json.parseToJsonElement(sanitized).toString()
		assertEquals(1, Regex("\"originalReleaseDate\"").findAll(albums).count())
		assertEquals(0, Regex("\"releaseDate\"").findAll(albums).count())
	}

	@Test
	fun clientPluginSanitizesJsonBodies() = runBlocking {
		val client = HttpClient(MockEngine {
			respond(
				content = album("{}", "{}"),
				headers = headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8")
			)
		}) { install(SubsonicItemDateSanitizer) }
		val body = client.get("https://navidrome.example/rest/getAlbum.view").bodyAsText()
		assertEquals("Undated", json.decodeFromString(Album.serializer(), body).name)
		client.close()
	}
}
