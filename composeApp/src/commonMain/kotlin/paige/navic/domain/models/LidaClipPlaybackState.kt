package paige.navic.domain.models

data class LidaClipPlaybackState(
	val retryKey: Int = 0,
	val errorMessage: String? = null
) {
	fun onReady(): LidaClipPlaybackState =
		copy(errorMessage = null)

	fun onError(message: String): LidaClipPlaybackState =
		copy(errorMessage = message)

	fun onRetry(): LidaClipPlaybackState =
		copy(
			retryKey = retryKey + 1,
			errorMessage = null
		)
}

fun lidaClipPlaybackErrorMessage(
	errorCodeName: String?,
	message: String?
): String {
	val detail = errorCodeName?.trim()?.takeIf { it.isNotEmpty() }
		?: message?.trim()?.takeIf { it.isNotEmpty() }
	return if (detail == null) {
		"Video playback failed"
	} else {
		"Video playback failed: $detail"
	}
}

fun shouldRecoverLidaClipFromPlaybackError(message: String): Boolean {
	val normalized = message.trim().lowercase()
	return "error_code_io_file_not_found" in normalized ||
		"file not found" in normalized
}
