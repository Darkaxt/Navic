package paige.navic.domain.models

enum class NavidromeFailureDisposition {
	ServiceUnavailable,
	Terminal
}

fun classifyNavidromeFailure(error: Throwable): NavidromeFailureDisposition {
	val failures = navidromeThrowableChain(error).toList()
	val messages = failures.map { it.message.orEmpty().lowercase() }
	val classNames = failures.mapNotNull { it::class.simpleName?.lowercase() }
	val statuses = messages.flatMap { message ->
		HTTP_STATUS_PATTERN.findAll(message).mapNotNull { match ->
			match.groupValues.getOrNull(1)?.toIntOrNull()
		}.toList()
	}

	if (statuses.any { it in 400..499 }) return NavidromeFailureDisposition.Terminal
	if (messages.any { message -> TERMINAL_MESSAGE_TOKENS.any(message::contains) }) {
		return NavidromeFailureDisposition.Terminal
	}
	if (statuses.any(SERVICE_UNAVAILABLE_HTTP_STATUSES::contains)) {
		return NavidromeFailureDisposition.ServiceUnavailable
	}
	if (
		messages.any { message -> SERVICE_UNAVAILABLE_MESSAGE_TOKENS.any(message::contains) } ||
		classNames.any { className -> SERVICE_UNAVAILABLE_CLASS_TOKENS.any(className::contains) }
	) {
		return NavidromeFailureDisposition.ServiceUnavailable
	}
	return NavidromeFailureDisposition.Terminal
}

private fun navidromeThrowableChain(error: Throwable): Sequence<Throwable> = sequence {
	val seen = mutableSetOf<Throwable>()
	var current: Throwable? = error
	while (current != null && seen.add(current)) {
		yield(current)
		current = current.cause
	}
}

private val HTTP_STATUS_PATTERN = Regex(
	pattern = """(?:http(?:\s+status)?|status)\s*[:=]?\s*(\d{3})""",
	option = RegexOption.IGNORE_CASE
)

private val SERVICE_UNAVAILABLE_HTTP_STATUSES = setOf(500, 502, 503, 504, 521, 522, 523, 524)

private val TERMINAL_MESSAGE_TOKENS = listOf(
	"non-audio content",
	"decoder",
	"malformed media",
	"malformed container",
	"unsupported format"
)

private val SERVICE_UNAVAILABLE_MESSAGE_TOKENS = listOf(
	"service unavailable",
	"server unavailable",
	"failed to connect",
	"connection refused",
	"connection reset",
	"unable to resolve host",
	"unknown host",
	"no route to host",
	"network is unreachable",
	"software caused connection abort",
	"broken pipe",
	"timed out",
	"timeout"
)

private val SERVICE_UNAVAILABLE_CLASS_TOKENS = listOf(
	"unknownhost",
	"connectexception",
	"socketexception",
	"noroutetohost",
	"timeout"
)
