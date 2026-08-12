package paige.navic.reader

internal fun ReaderReadaloudPlaybackCommand.whispersyncPlaybackCommandLogValue(): String =
	when (this) {
		ReaderReadaloudPlaybackCommand.Play -> "play"
		ReaderReadaloudPlaybackCommand.Pause -> "pause"
		ReaderReadaloudPlaybackCommand.StopAndReset -> "stop-reset"
		is ReaderReadaloudPlaybackCommand.SeekTo -> "seek"
		is ReaderReadaloudPlaybackCommand.SeekToTrack -> "seek-track"
		is ReaderReadaloudPlaybackCommand.SetSpeed -> "set-speed"
		is ReaderReadaloudPlaybackCommand.SetSyncEnabled -> "set-sync"
	}

internal fun ReaderEngineCommand?.whispersyncCommandLogValue(): String =
	when (this) {
		is ReaderEngineCommand.ApplyMediaOverlay -> "apply-overlay"
		is ReaderEngineCommand.UpdateMediaOverlayProgress -> "update-overlay"
		ReaderEngineCommand.ClearMediaOverlay -> "clear-overlay"
		else -> "none"
	}

internal fun String?.whispersyncSourceLogValue(): String =
	when (this) {
		"media-overlay-follow" -> "audio-follow"
		null -> "unspecified"
		else -> "reader"
	}

internal fun ReaderWhispersyncSessionState.whispersyncSegmentCountLogValue(): Int =
	timeline?.segments?.size ?: 0
