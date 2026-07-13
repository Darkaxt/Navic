package paige.navic.domain.repositories

import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

internal data class ExternalTextTransportResponse(
	val status: HttpStatusCode,
	val body: String
)

internal interface ExternalTextTransport {
	suspend fun get(request: ApprovedExternalTextRequest): ExternalTextTransportResponse
}

internal class SecureExternalTextClient(
	private val transport: ExternalTextTransport
) {
	suspend fun fetch(url: String, purpose: ExternalTextPurpose): String {
		val request = approvedExternalTextRequest(url, purpose)
		val response = transport.get(request)
		if (!response.status.isSuccess()) {
			throw BinderyApiException(
				response.status,
				binderyHttpErrorMessage("Provider source page", response.status)
			)
		}
		return response.body
	}
}

internal expect fun platformExternalTextTransport(): ExternalTextTransport
