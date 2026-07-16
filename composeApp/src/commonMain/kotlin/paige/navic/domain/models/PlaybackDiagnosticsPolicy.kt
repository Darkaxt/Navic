package paige.navic.domain.models

const val PlaybackDiagnosticsLogTag = "PlaybackDiagnostics"

private val PlaybackDiagnosticWhitespaceRegex = Regex("\\s+")

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
	replace(PlaybackDiagnosticWhitespaceRegex, " ").trim()
