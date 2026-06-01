package paige.navic.domain.models

fun shouldFailHostedDownload(error: Throwable): Boolean =
	throwableChain(error).any { throwable ->
		val className = throwable::class.simpleName.orEmpty().lowercase()
		val message = throwable.message.orEmpty().lowercase()
		hasServiceDownHttpStatus(message) ||
			serviceDownClassTokens.any { token -> token in className } ||
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

private val serviceDownClassTokens = listOf(
	"connectexception",
	"connecttimeoutexception",
	"httprequesttimeoutexception",
	"noroutetohostexception",
	"sockettimeoutexception",
	"unknownhostexception",
	"unresolvedaddressexception"
)

private val serviceDownMessageTokens = listOf(
	"connection refused",
	"connection reset",
	"failed to connect",
	"name does not resolve",
	"network is unreachable",
	"no route to host",
	"service unavailable",
	"server unavailable",
	"timed out",
	"timeout",
	"unable to resolve host"
)
