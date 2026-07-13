package paige.navic.domain.repositories

internal actual fun platformExternalTextTransport(): ExternalTextTransport =
	object : ExternalTextTransport {
		override suspend fun get(request: ApprovedExternalTextRequest): ExternalTextTransportResponse {
			throw IllegalStateException("External provider page fetching is unavailable on this platform.")
		}
	}
