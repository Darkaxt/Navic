package paige.navic.domain.models

enum class HostedDownloadFailureAction {
	WaitForService,
	Fail
}

fun hostedDownloadFailureAction(error: Throwable): HostedDownloadFailureAction =
	when (classifyNavidromeFailure(error)) {
		NavidromeFailureDisposition.ServiceUnavailable -> HostedDownloadFailureAction.WaitForService
		NavidromeFailureDisposition.Terminal -> HostedDownloadFailureAction.Fail
	}

fun shouldFailHostedDownload(error: Throwable): Boolean =
	hostedThrowableChain(error).any { throwable ->
		val message = throwable.message.orEmpty().lowercase()
		"non-audio content" in message ||
			"service unavailable" in message ||
			"server unavailable" in message ||
			LEGACY_SERVICE_DOWN_HTTP_STATUSES.any { status ->
				"http $status" in message || "status $status" in message
			}
	}

private fun hostedThrowableChain(error: Throwable): Sequence<Throwable> = sequence {
	val seen = mutableSetOf<Throwable>()
	var current: Throwable? = error
	while (current != null && seen.add(current)) {
		yield(current)
		current = current.cause
	}
}

private val LEGACY_SERVICE_DOWN_HTTP_STATUSES = setOf(500, 502, 503, 504, 521, 522, 523, 524)
