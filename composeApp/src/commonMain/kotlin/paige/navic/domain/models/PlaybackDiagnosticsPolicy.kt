package paige.navic.domain.models

const val PlaybackDiagnosticsLogTag = "PlaybackDiagnostics"

private val PlaybackDiagnosticWhitespaceRegex = Regex("\\s+")
private val PlaybackDiagnosticUrlRegex = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

fun playbackErrorCauseDetails(error: Throwable): String {
	val seen = mutableSetOf<Throwable>()
	val details = mutableListOf<String>()
	var cause = error.cause
	while (cause != null && seen.add(cause)) {
		details += "${cause::class.simpleName}: ${cause.message.orEmpty()}"
		cause = cause.cause
	}
	return details.joinToString(" <- ").sanitizedPlaybackDiagnosticValue()
}

fun shouldPersistAppLogEvent(issueLoggingEnabled: Boolean, tag: String): Boolean =
	issueLoggingEnabled || tag == PlaybackDiagnosticsLogTag

fun playbackDiagnosticMessage(
	event: String,
	vararg fields: Pair<String, Any?>
): String = buildString {
	append(event.sanitizedPlaybackDiagnosticValue())
	fields.forEach { (key, rawValue) ->
		val value = rawValue
			?.toString()
			?.sanitizedPlaybackDiagnosticValue()
			?.takeIf { it.isNotBlank() }
			?: return@forEach
		append(' ')
		append(key.sanitizedPlaybackDiagnosticValue())
		append('=')
		append(value)
	}
}

private fun String.sanitizedPlaybackDiagnosticValue(): String =
	replace(PlaybackDiagnosticUrlRegex, "[redacted-url]")
		.replace(PlaybackDiagnosticWhitespaceRegex, " ").trim()
