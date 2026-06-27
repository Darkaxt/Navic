package paige.navic.domain.manager

internal fun normalizeSubsonicInstanceUrl(instanceUrl: String): String {
	var normalized = instanceUrl.trim()
	if (normalized.isEmpty()) return normalized

	if (!normalized.startsWith("https://", ignoreCase = true) &&
		!normalized.startsWith("http://", ignoreCase = true)
	) {
		normalized = "https://$normalized"
	}

	normalized = normalized
		.substringBefore('#')
		.substringBefore('?')
		.trimEnd('/')

	return if (normalized.endsWith("/app", ignoreCase = true)) {
		normalized.dropLast("/app".length).trimEnd('/')
	} else {
		normalized
	}
}
