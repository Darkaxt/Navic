package paige.navic.domain.repositories

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import paige.navic.data.remote.NetworkClientFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BinderyApiClientSecurityTest {
	@Test
	fun offOriginAbsoluteCatalogPathDoesNotReceiveApiKey() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = KtorBinderyApiClient(
			NetworkClientFactory {
				MockEngine { request ->
					requests += request
					respond("", HttpStatusCode.Unauthorized)
				}
			}
		)

		assertFailsWith<BinderyApiException> {
			client.fetchCatalog(
				baseUrl = "https://bindery.example.com/opds",
				requestHeaders = mapOf("X-Api-Key" to "secret"),
				path = "https://cdn.example.net/catalog"
			)
		}

		assertEquals(1, requests.size)
		assertNull(requests.single().headers["X-Api-Key"])
	}

	@Test
	fun authenticatedRedirectIsNotFollowed() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val client = KtorBinderyApiClient(
			NetworkClientFactory {
				MockEngine { request ->
					requests += request
					respond(
						content = "",
						status = HttpStatusCode.Found,
						headers = headersOf(HttpHeaders.Location, "https://evil.example/catalog")
					)
				}
			}
		)

		assertFailsWith<BinderyApiException> {
			client.fetchCatalog(
				baseUrl = "https://bindery.example.com/opds",
				requestHeaders = mapOf("X-Api-Key" to "secret"),
				path = "/"
			)
		}

		assertEquals(1, requests.size)
		assertEquals("secret", requests.single().headers["X-Api-Key"])
	}
}
