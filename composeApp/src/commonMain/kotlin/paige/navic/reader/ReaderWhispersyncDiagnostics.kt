package paige.navic.reader

import paige.navic.util.core.Logger

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

internal fun ReaderOverlayCoordinateMode.whispersyncCoordinateModeLogValue(): String =
	when (this) {
		ReaderOverlayCoordinateMode.CueV1DomUtf16 -> "cue-progressive"
		ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8 -> "word-exact"
	}

internal fun String?.whispersyncSourceLogValue(): String =
	when (this) {
		"media-overlay-follow" -> "audio-follow"
		null -> "unspecified"
		else -> "reader"
	}

internal fun ReaderWhispersyncSessionState.whispersyncSegmentCountLogValue(): Int =
	timeline?.segments?.size ?: 0

/** WordSync overlay acceptance logging; keeps raw Foliate bridge events out of the shell. */
internal fun ReaderEngineHostEvent.logWordSyncOverlayState() {
	val bridgeEvent = (this as? ReaderEngineHostEvent.FoliateBridge)?.event
	when (bridgeEvent) {
		is ReaderBridgeEvent.OverlayFragmentActive -> if (
			bridgeEvent.fragment.coordinateMode == ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8
		) {
			Logger.i(
				WhispersyncSyncLogTag,
				"WordSync overlay state=active " +
					"anchor=${if (bridgeEvent.anchorReceipt != null) "present" else "absent"}"
			)
		}
		is ReaderBridgeEvent.OverlayFragmentInactive -> if (
			bridgeEvent.coordinateMode == ReaderOverlayCoordinateMode.WordSyncV1ExtractedUtf8
		) {
			Logger.w(
				WhispersyncSyncLogTag,
				"WordSync overlay state=inactive anchor=absent " +
					"reason=${wordSyncOverlayInactiveReasonLogValue(bridgeEvent.reason)}"
			)
		}
		else -> Unit
	}
}

internal fun ReaderEngineViewState.overlayCommandKeyOrNull(): Long? =
	(this as? ReaderEngineViewState.WebViewPublication)?.commandKey

private fun wordSyncOverlayInactiveReasonLogValue(reason: String?): String = when (reason) {
	"animation-outside-visible-page",
	"animation-paint-rejected",
	"user-relocation-active",
	"outside-visible-page",
	"paint-rejected",
	"invalid-coordinate-mode",
	"stale-progress-request",
	"progress-outside-visible-page",
	"progress-paint-rejected",
	"document-loaded",
	"anchor-rejected" -> reason
	null -> "absent"
	else -> "other"
}
