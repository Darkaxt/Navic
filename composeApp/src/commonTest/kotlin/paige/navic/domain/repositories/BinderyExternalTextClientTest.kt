package paige.navic.domain.repositories

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BinderyExternalTextClientTest {
	@Test
	fun approvedSourceReturnsTransportBody() = runBlocking {
		val transport = RecordingExternalTextTransport(
			ExternalTextTransportResponse(HttpStatusCode.OK, "<html>approved</html>")
		)
		val client = SecureExternalTextClient(transport)

		val body = client.fetch(
			"https://audiobookbay.lu/abss/the-hobbit/",
			ExternalTextPurpose.AudioBookBayProviderCover
		)

		assertEquals("<html>approved</html>", body)
		assertEquals(listOf("https://audiobookbay.lu/abss/the-hobbit/"), transport.urls)
	}

	@Test
	fun redirectResponseIsRejectedWithoutFollowingLocation() = runBlocking {
		val transport = RecordingExternalTextTransport(
			ExternalTextTransportResponse(HttpStatusCode.Found, "")
		)
		val client = SecureExternalTextClient(transport)

		val failure = assertFailsWith<BinderyApiException> {
			client.fetch(
				"https://audiobookbay.lu/abss/the-hobbit/",
				ExternalTextPurpose.AudioBookBayProviderCover
			)
		}

		assertEquals(HttpStatusCode.Found, failure.status)
		assertEquals(listOf("https://audiobookbay.lu/abss/the-hobbit/"), transport.urls)
	}

	@Test
	fun rejectedSourceNeverReachesTransport() = runBlocking {
		val transport = RecordingExternalTextTransport(
			ExternalTextTransportResponse(HttpStatusCode.OK, "unexpected")
		)
		val client = SecureExternalTextClient(transport)

		assertFailsWith<IllegalStateException> {
			client.fetch(
				"https://192.168.1.1/private",
				ExternalTextPurpose.AudioBookBayProviderCover
			)
		}

		assertEquals(emptyList(), transport.urls)
	}
}

private class RecordingExternalTextTransport(
	private val response: ExternalTextTransportResponse
) : ExternalTextTransport {
	val urls = mutableListOf<String>()

	override suspend fun get(request: ApprovedExternalTextRequest): ExternalTextTransportResponse {
		urls += request.url
		return response
	}
}
