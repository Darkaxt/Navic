package paige.navic.data.remote

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class NetworkClientFactoryTest {
	@Test
	fun createsIsolatedClientsWithSharedUserAgentAndJsonPolicy() = runBlocking {
		val requests = mutableListOf<HttpRequestData>()
		val factory = NetworkClientFactory {
			MockEngine { request ->
				requests += request
				respond(
					content = "{\"value\":\"ok\",\"unknown\":true}",
					headers = headersOf(HttpHeaders.ContentType, "application/json")
				)
			}
		}
		val authenticated = factory.create(
			json = NetworkJson.tolerant,
			configure = {
				defaultRequest { header(HttpHeaders.Authorization, "Bearer secret") }
			}
		)
		val anonymous = factory.create(json = NetworkJson.tolerant)

		assertNotSame(authenticated, anonymous)
		assertEquals(
			"ok",
			authenticated.get("https://service-one.example/value").body<NetworkFixture>().value
		)
		assertEquals(
			"ok",
			anonymous.get("https://service-two.example/value").body<NetworkFixture>().value
		)
		assertEquals("Navic", requests[0].headers[HttpHeaders.UserAgent])
		assertEquals("Bearer secret", requests[0].headers[HttpHeaders.Authorization])
		assertEquals("Navic", requests[1].headers[HttpHeaders.UserAgent])
		assertNull(requests[1].headers[HttpHeaders.Authorization])

		authenticated.close()
		anonymous.close()
	}
}

@Serializable
private data class NetworkFixture(val value: String)
