package paige.navic.domain.repositories

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import paige.navic.data.remote.NetworkClientFactory
import paige.navic.data.remote.bindery.BinderyApiException
import paige.navic.data.remote.bindery.KtorBinderyApiClient
import paige.navic.data.remote.bindery.binderyWordSyncChapterRoute
import paige.navic.data.remote.bindery.binderyWordSyncIndexRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BinderyWordSyncRoutePolicyTest {
	private val identity = BinderyWhispersyncIdentity(
		bookId = 7,
		ebookBookFileId = 11,
		audiobookBookFileId = 13,
		artifactId = 17
	)

	@Test
	fun opdsAndApiAliasesShareCanonicalGenerationCachePaths() {
		val opds = binderyWordSyncIndexRoute(
			baseUrl = "https://bindery.example.com/opds",
			identity = identity,
			advertisedHref = "/opds/books/7/sync/17/wordsync/index"
		)
		val api = binderyWordSyncIndexRoute(
			baseUrl = "https://bindery.example.com/opds",
			identity = identity,
			advertisedHref = "https://bindery.example.com/api/v1/sync/artifacts/17/wordsync/index"
		)
		val chapter = binderyWordSyncChapterRoute(
			baseUrl = "https://bindery.example.com/opds",
			identity = identity,
			chapterKey = "spine-002-chapter",
			advertisedHref = "/api/v1/sync/artifacts/17/wordsync/spine-002-chapter"
		)

		assertEquals("https://bindery.example.com/opds/books/7/sync/17/wordsync/index", opds.requestUrl)
		assertEquals("https://bindery.example.com/api/v1/sync/artifacts/17/wordsync/index", api.requestUrl)
		assertEquals(opds.cachePath, api.cachePath)
		assertEquals(
			"book:7|ebook:11|audiobook:13|artifact:17|index",
			opds.cachePath
		)
		assertEquals(
			"book:7|ebook:11|audiobook:13|artifact:17|chapter:spine-002-chapter",
			chapter.cachePath
		)
	}

	@Test
	fun prefixedBinderyBaseAcceptsOnlyItsCanonicalOpdsAndApiRoutes() {
		val opds = binderyWordSyncIndexRoute(
			baseUrl = "https://bindery.example.com/service/opds",
			identity = identity,
			advertisedHref = "/service/opds/books/7/sync/17/wordsync/index"
		)
		val api = binderyWordSyncIndexRoute(
			baseUrl = "https://bindery.example.com/service/opds",
			identity = identity,
			advertisedHref = "https://bindery.example.com/service/api/v1/sync/artifacts/17/wordsync/index"
		)

		assertEquals(
			"https://bindery.example.com/service/opds/books/7/sync/17/wordsync/index",
			opds.requestUrl
		)
		assertEquals(
			"https://bindery.example.com/service/api/v1/sync/artifacts/17/wordsync/index",
			api.requestUrl
		)
		assertFailsWith<IllegalArgumentException> {
			binderyWordSyncIndexRoute(
				baseUrl = "https://bindery.example.com/service/opds",
				identity = identity,
				advertisedHref = "/opds/books/7/sync/17/wordsync/index"
			)
		}
	}

	@Test
	fun absoluteRoutesUseCanonicalOriginAndExactPathSemantics() {
		val caseNormalized = binderyWordSyncIndexRoute(
			baseUrl = "https://BINDERY.example.com/opds",
			identity = identity,
			advertisedHref = "https://bindery.example.com/opds/books/7/sync/17/wordsync/index"
		)
		val defaultPortNormalized = binderyWordSyncIndexRoute(
			baseUrl = "https://bindery.example.com:443/opds",
			identity = identity,
			advertisedHref = "https://bindery.example.com/opds/books/7/sync/17/wordsync/index"
		)

		assertEquals(
			"https://bindery.example.com/opds/books/7/sync/17/wordsync/index",
			caseNormalized.requestUrl
		)
		assertEquals(
			"https://bindery.example.com/opds/books/7/sync/17/wordsync/index",
			defaultPortNormalized.requestUrl
		)
	}

	@Test
	fun rejectsUntrustedOrNonCanonicalAdvertisedRoutes() {
		val invalidIndexHrefs = listOf(
			"https://evil.example/opds/books/7/sync/17/wordsync/index",
			"https://user@bindery.example.com/opds/books/7/sync/17/wordsync/index",
			"/opds/books/7/sync/17/wordsync/index?download=1",
			"/opds/books/7/sync/17/wordsync/index#fragment",
			"/opds/books/7/sync/18/wordsync/index",
			"/opds/books/8/sync/17/wordsync/index",
			"/opds/books/7/sync/17/wordsync/%2e%2e/index",
			"/opds/books/7/sync/17/wordsync%2findex",
			"/opds/books/7/sync/17/wordsync\\index"
		)

		invalidIndexHrefs.forEach { href ->
			assertFailsWith<IllegalArgumentException>(href) {
				binderyWordSyncIndexRoute(
					baseUrl = "https://bindery.example.com/opds",
					identity = identity,
					advertisedHref = href
				)
			}
		}
		listOf(".", "..", "index").forEach { chapterKey ->
			assertFailsWith<IllegalArgumentException>(chapterKey) {
				binderyWordSyncChapterRoute(
					baseUrl = "https://bindery.example.com/opds",
					identity = identity,
					chapterKey = chapterKey,
					advertisedHref = "/opds/books/7/sync/17/wordsync/$chapterKey"
				)
			}
		}
		assertFailsWith<IllegalArgumentException> {
			binderyWordSyncChapterRoute(
				baseUrl = "https://bindery.example.com/opds",
				identity = identity,
				chapterKey = "spine-002-chapter",
				advertisedHref = "/opds/books/7/sync/17/wordsync/other-chapter"
			)
		}
	}

	@Test
	fun authenticatedWordSyncRequestRejectsRedirectWithoutFollowing() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = KtorBinderyApiClient(
			NetworkClientFactory {
				MockEngine { request ->
					requests += request
					respond(
						content = "",
						status = HttpStatusCode.Found,
						headers = headersOf(HttpHeaders.Location, "https://evil.example/index")
					)
				}
			}
		)

		assertFailsWith<BinderyApiException> {
			client.fetchWordSyncIndexJson(
				baseUrl = "https://bindery.example.com/opds",
				requestHeaders = mapOf("X-Api-Key" to "secret"),
				identity = identity,
				advertisedHref = "/opds/books/7/sync/17/wordsync/index"
			)
		}

		assertEquals(1, requests.size)
		assertEquals("secret", requests.single().headers["X-Api-Key"])
	}

	@Test
	fun invalidWordSyncRouteIsRejectedBeforeNetworkRequest() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = KtorBinderyApiClient(
			NetworkClientFactory {
				MockEngine { request ->
					requests += request
					respond("{}", HttpStatusCode.OK)
				}
			}
		)

		assertFailsWith<IllegalArgumentException> {
			client.fetchWordSyncIndexJson(
				baseUrl = "https://bindery.example.com/opds",
				requestHeaders = mapOf("X-Api-Key" to "secret"),
				identity = identity,
				advertisedHref = "https://evil.example/opds/books/7/sync/17/wordsync/index"
			)
		}
		assertEquals(0, requests.size)
	}
}
