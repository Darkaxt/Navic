package paige.navic.reader

import paige.navic.domain.manager.PreferenceManager

const val MinReaderWhispersyncPlaybackSpeed = 0.5f
const val MaxReaderWhispersyncPlaybackSpeed = 3f
const val DefaultReaderWhispersyncPlaybackSpeed = 1f
const val DefaultReaderWhispersyncHighlightColorArgb = 0x66F6C343

enum class ReaderWhispersyncHighlightLoading(val value: String) {
	CurrentCue("current-cue"),
	PersistentPlayedText("persistent-played-text")
}

enum class ReaderWhispersyncHighlightStyle(val value: String) {
	Selection("selection"),
	Marker("marker")
}

enum class ReaderWhispersyncPageBoundaryBehavior(val value: String) {
	PauseAtVisiblePageEnd("pause-at-visible-page-end")
}

enum class ReaderWhispersyncLongPressBehavior(val value: String) {
	SeekAudioToText("seek-audio-to-text")
}

data class ReaderListeningSettings(
	val listeningEnabled: Boolean,
	val playbackSpeed: Float,
	val highlightLeadMs: Int,
	val highlightColorArgb: Int,
	val highlightLoading: ReaderWhispersyncHighlightLoading,
	val highlightStyle: ReaderWhispersyncHighlightStyle,
	val pageBoundaryBehavior: ReaderWhispersyncPageBoundaryBehavior,
	val longPressBehavior: ReaderWhispersyncLongPressBehavior
)

fun defaultReaderListeningSettings(): ReaderListeningSettings =
	ReaderListeningSettings(
		listeningEnabled = true,
		playbackSpeed = DefaultReaderWhispersyncPlaybackSpeed,
		highlightLeadMs = DefaultReaderWhispersyncHighlightLeadMs,
		highlightColorArgb = DefaultReaderWhispersyncHighlightColorArgb,
		highlightLoading = ReaderWhispersyncHighlightLoading.CurrentCue,
		highlightStyle = ReaderWhispersyncHighlightStyle.Selection,
		pageBoundaryBehavior = ReaderWhispersyncPageBoundaryBehavior.PauseAtVisiblePageEnd,
		longPressBehavior = ReaderWhispersyncLongPressBehavior.SeekAudioToText
	)

fun ReaderListeningSettings.normalizedReaderListeningSettings(): ReaderListeningSettings =
	copy(
		playbackSpeed = normalizedReaderWhispersyncPlaybackSpeed(playbackSpeed),
		highlightLeadMs = normalizedReaderWhispersyncHighlightLeadMs(highlightLeadMs)
	)

fun PreferenceManager.readerListeningSettings(): ReaderListeningSettings =
	ReaderListeningSettings(
		listeningEnabled = readerWhispersyncListeningEnabled,
		playbackSpeed = normalizedReaderWhispersyncPlaybackSpeed(readerWhispersyncPlaybackSpeed),
		highlightLeadMs = normalizedReaderWhispersyncHighlightLeadMs(readerWhispersyncHighlightLeadMs),
		highlightColorArgb = readerWhispersyncHighlightColorArgb,
		highlightLoading = normalizedReaderWhispersyncHighlightLoading(readerWhispersyncHighlightLoading),
		highlightStyle = normalizedReaderWhispersyncHighlightStyle(readerWhispersyncHighlightStyle),
		pageBoundaryBehavior = normalizedReaderWhispersyncPageBoundaryBehavior(readerWhispersyncPageBoundaryBehavior),
		longPressBehavior = normalizedReaderWhispersyncLongPressBehavior(readerWhispersyncLongPressBehavior)
	)

fun PreferenceManager.setReaderListeningSettings(settings: ReaderListeningSettings) {
	val normalized = settings.normalizedReaderListeningSettings()
	readerWhispersyncListeningEnabled = normalized.listeningEnabled
	readerWhispersyncPlaybackSpeed = normalized.playbackSpeed
	readerWhispersyncHighlightLeadMs = normalized.highlightLeadMs
	readerWhispersyncHighlightColorArgb = normalized.highlightColorArgb
	readerWhispersyncHighlightLoading = normalized.highlightLoading.value
	readerWhispersyncHighlightStyle = normalized.highlightStyle.value
	readerWhispersyncPageBoundaryBehavior = normalized.pageBoundaryBehavior.value
	readerWhispersyncLongPressBehavior = normalized.longPressBehavior.value
}

fun ReaderSettings.withReaderListeningSettings(settings: ReaderListeningSettings): ReaderSettings {
	val normalized = settings.normalizedReaderListeningSettings()
	return copy(
		readaloudSyncEnabled = normalized.listeningEnabled,
		whispersyncHighlightLeadMs = normalized.highlightLeadMs,
		whispersyncHighlightColorArgb = normalized.highlightColorArgb,
		whispersyncHighlightLoading = normalized.highlightLoading.value,
		whispersyncHighlightStyle = normalized.highlightStyle.value
	)
}

fun normalizedReaderWhispersyncPlaybackSpeed(value: Float): Float =
	normalizedReadaloudPlaybackSpeed(value).coerceIn(
		MinReaderWhispersyncPlaybackSpeed,
		MaxReaderWhispersyncPlaybackSpeed
	)

fun normalizedReaderWhispersyncHighlightLoading(value: String?): ReaderWhispersyncHighlightLoading =
	ReaderWhispersyncHighlightLoading.entries.firstOrNull { loading -> loading.value == value }
		?: ReaderWhispersyncHighlightLoading.CurrentCue

fun normalizedReaderWhispersyncHighlightStyle(value: String?): ReaderWhispersyncHighlightStyle =
	ReaderWhispersyncHighlightStyle.entries.firstOrNull { style -> style.value == value }
		?: ReaderWhispersyncHighlightStyle.Selection

fun normalizedReaderWhispersyncPageBoundaryBehavior(value: String?): ReaderWhispersyncPageBoundaryBehavior =
	ReaderWhispersyncPageBoundaryBehavior.entries.firstOrNull { behavior -> behavior.value == value }
		?: ReaderWhispersyncPageBoundaryBehavior.PauseAtVisiblePageEnd

fun normalizedReaderWhispersyncLongPressBehavior(value: String?): ReaderWhispersyncLongPressBehavior =
	ReaderWhispersyncLongPressBehavior.entries.firstOrNull { behavior -> behavior.value == value }
		?: ReaderWhispersyncLongPressBehavior.SeekAudioToText
