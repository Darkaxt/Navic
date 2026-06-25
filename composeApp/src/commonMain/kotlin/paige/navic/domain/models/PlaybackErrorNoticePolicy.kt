package paige.navic.domain.models

enum class PlaybackErrorNotice {
	SongNotFound,
	FailedDownload,
	FailedToPlaySong
}

fun playbackErrorNotice(
	errorCodeName: String?,
	message: String?,
	details: List<String> = emptyList()
): PlaybackErrorNotice {
	val text = (listOfNotNull(errorCodeName, message) + details)
		.joinToString(" ")
		.lowercase()

	return when {
		isSongNotFoundPlaybackError(text) -> PlaybackErrorNotice.SongNotFound
		isFailedDownloadPlaybackError(text) -> PlaybackErrorNotice.FailedDownload
		else -> PlaybackErrorNotice.FailedToPlaySong
	}
}

private fun isSongNotFoundPlaybackError(text: String): Boolean =
	"error_code_io_file_not_found" in text ||
		"404" in text ||
		"not found" in text ||
		"file does not exist" in text

private fun isFailedDownloadPlaybackError(text: String): Boolean =
	"error_code_parsing_container_unsupported" in text ||
		"non-audio content" in text ||
		"available extractors" in text ||
		"could read the stream" in text ||
		"malformed" in text ||
		"invalid downloaded" in text ||
		"cached file" in text
