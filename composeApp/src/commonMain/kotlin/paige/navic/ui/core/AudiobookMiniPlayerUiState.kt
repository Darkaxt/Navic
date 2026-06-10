package paige.navic.ui.core

import paige.navic.reader.ReadaloudPlaybackMetadataLabels

data class AudiobookMiniPlayerUiState(
	val isAvailable: Boolean = false,
	val isPlaying: Boolean = false,
	val bookId: String? = null,
	val bookTitle: String? = null,
	val versionRowId: String? = null,
	val coverUrl: String? = null,
	val coverCacheKey: String? = null,
	val imageRequestHeaders: Map<String, String> = emptyMap(),
	val chapterLabel: String? = null,
	val sectionLabel: String? = null,
	val narratorLabel: String? = null,
	val trackIndex: Int = 0,
	val mediaId: String? = null,
	val positionMs: Long = 0L,
	val durationMs: Long? = null,
	val playbackSpeed: Float = 1f,
	val activeAudioMetadata: ReadaloudPlaybackMetadataLabels? = null
) {
	val progress: Float
		get() = durationMs
			?.takeIf { it > 0L }
			?.let { duration -> (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) }
			?: 0f
}
