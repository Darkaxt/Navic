package paige.navic.domain.models

fun shouldFailHostedDownload(error: Throwable): Boolean =
	throwableChain(error).any { throwable ->
		val message = throwable.message.orEmpty().lowercase()
		hasServiceDownHttpStatus(message) ||
			"non-audio content" in message ||
			serviceDownMessageTokens.any { token -> token in message }
	}

private fun hasServiceDownHttpStatus(message: String): Boolean =
	serviceDownHttpStatuses.any { status ->
		"http $status" in message ||
			"http$status" in message ||
			"http status $status" in message ||
			"status $status" in message
	}

private fun throwableChain(error: Throwable): Sequence<Throwable> = sequence {
	val seen = mutableSetOf<Throwable>()
	var current: Throwable? = error
	while (current != null && seen.add(current)) {
		yield(current)
		current = current.cause
	}
}

private val serviceDownHttpStatuses = setOf(
	500,
	502,
	503,
	504,
	521,
	522,
	523,
	524
)

private val serviceDownMessageTokens = listOf(
	"service unavailable",
	"server unavailable"
)
