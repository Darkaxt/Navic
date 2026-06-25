package paige.navic.domain.models

fun shouldRejectAudioDownloadContentType(contentType: String?): Boolean {
	val mediaType = contentType
		?.substringBefore(';')
		?.trim()
		?.lowercase()
		?: return false

	return mediaType.startsWith("text/") ||
		mediaType == "application/json" ||
		mediaType == "application/xml" ||
		mediaType == "application/xhtml+xml"
}

fun shouldUseDownloadedAudioFile(fileSizeBytes: Long): Boolean =
	fileSizeBytes >= MinimumPlausibleAudioDownloadBytes

private const val MinimumPlausibleAudioDownloadBytes = 16 * 1024L
