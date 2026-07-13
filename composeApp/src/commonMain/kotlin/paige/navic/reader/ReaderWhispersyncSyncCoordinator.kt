package paige.navic.reader

import paige.navic.util.core.Logger

const val WhispersyncSyncLogTag = "WhispersyncSync"

data class ReaderWhispersyncSessionState(
	val sidecar: WhispersyncSidecar? = null,
	val sync: ReaderWhispersyncSyncState = ReaderWhispersyncSyncState(),
	val visibleTextRange: ReaderWhispersyncVisibleTextRange? = null,
	val audioSeekTarget: WhispersyncAudioSeekTarget? = null,
	val status: ReaderWhispersyncStatus = ReaderWhispersyncStatus()
) {
	val timeline: WhispersyncTimeline?
		get() = sidecar?.timeline

	val available: Boolean
		get() = timeline?.segments?.isNotEmpty() == true
}

typealias ReaderWhispersyncSyncState = ReaderOverlaySyncState

data class ReaderWhispersyncVisibleTextRange(
	val textHref: String,
	val visibleStart: Int,
	val visibleEnd: Int,
	val rangeCfi: String? = null,
	val source: String? = null
)

enum class ReaderWhispersyncStatusKind {
	Unavailable,
	Ready,
	SyncDisabled,
	SeekingAudio,
	Playing,
	NoActiveCue,
	Mismatch,
	LoadFailed
}

data class ReaderWhispersyncStatus(
	val kind: ReaderWhispersyncStatusKind = ReaderWhispersyncStatusKind.Unavailable,
	val label: String? = null,
	val detail: String? = null,
	val audioResource: String? = null,
	val positionMs: Long? = null
) {
	val visible: Boolean
		get() = kind != ReaderWhispersyncStatusKind.Unavailable

	val requiresAttention: Boolean
		get() = kind == ReaderWhispersyncStatusKind.Mismatch ||
			kind == ReaderWhispersyncStatusKind.LoadFailed

	val repairable: Boolean
		get() = kind == ReaderWhispersyncStatusKind.Mismatch
}

data class ReaderWhispersyncVisibleRangeStep(
	val state: ReaderWhispersyncSyncState,
	val audioSeekTarget: WhispersyncAudioSeekTarget? = null,
	val status: ReaderWhispersyncStatus? = null
)

data class ReaderWhispersyncPlaybackPositionStep(
	val state: ReaderWhispersyncSyncState,
	val status: ReaderWhispersyncStatus? = null
)

fun ReaderWhispersyncSyncState.onAudiobookPlaybackPosition(
	timeline: WhispersyncTimeline?,
	audioResource: String,
	positionMs: Long,
	audioTrackIndex: Int? = null,
	playbackSpeed: Float = 1f,
	highlightLeadMs: Int = 0
): ReaderWhispersyncSyncState =
	onAudiobookPlaybackPositionStep(
		timeline = timeline,
		audioResource = audioResource,
		positionMs = positionMs,
		audioTrackIndex = audioTrackIndex,
		playbackSpeed = playbackSpeed,
		highlightLeadMs = highlightLeadMs
	).state

fun ReaderWhispersyncSyncState.onAudiobookPlaybackPositionStep(
	timeline: WhispersyncTimeline?,
	audioResource: String,
	positionMs: Long,
	audioTrackIndex: Int? = null,
	playbackSpeed: Float = 1f,
	highlightLeadMs: Int = 0
): ReaderWhispersyncPlaybackPositionStep {
	if (!syncEnabled) {
		return ReaderWhispersyncPlaybackPositionStep(
			state = this,
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				label = "Whispersync paused"
			)
		)
	}
	if (timeline == null) {
		return ReaderWhispersyncPlaybackPositionStep(state = this)
	}
	val adapter = WhispersyncOverlaySyncAdapter(timeline)
	val resolution = adapter.playbackResolution(
		WhispersyncPlaybackSyncInput(
			audioResource = audioResource,
			positionMs = positionMs,
			audioTrackIndex = audioTrackIndex,
			playbackSpeed = playbackSpeed,
			highlightLeadMs = highlightLeadMs
		)
	) ?: run {
			if (activeCueKey != null) {
				Logger.w(
					WhispersyncSyncLogTag,
					"Whispersync playback position lost cue audio=${audioResource.whispersyncLogValue()} " +
						"track=${audioTrackIndex ?: "n/a"} positionMs=$positionMs"
				)
			}
			return ReaderWhispersyncPlaybackPositionStep(
				state = clearOverlayIfNeeded(),
				status = ReaderWhispersyncStatus(
					kind = ReaderWhispersyncStatusKind.NoActiveCue,
					label = "No synced text here",
					detail = audioResource,
					audioResource = audioResource,
					positionMs = positionMs
				)
			)
	}
	val segment = resolution.segment
	val nextState = followPlaybackCue(resolution.cue)
	val command = nextState.engineCommand
		?.takeIf { nextState.engineCommandKey != engineCommandKey }
	val commandLabel = when (command) {
		is ReaderEngineCommand.ApplyMediaOverlay -> "applyOverlay"
		is ReaderEngineCommand.UpdateMediaOverlayProgress -> "updateOverlayProgress"
		else -> null
	}
	if (commandLabel != null) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync playback position audio=${audioResource.whispersyncLogValue()} " +
				"track=${audioTrackIndex ?: "n/a"} positionMs=$positionMs " +
				"cue=${segment.id.orEmpty().ifBlank { "n/a" }} " +
				"text=${segment.textHref.whispersyncLogValue()} " +
				"textRange=${segment.textStart ?: "n/a"}-${segment.textEnd ?: "n/a"} " +
				"progressTextEnd=${resolution.cue.progressTextEnd ?: "n/a"} " +
				"command=$commandLabel"
		)
	}
	return ReaderWhispersyncPlaybackPositionStep(
		state = nextState,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.Playing,
			label = "Whispersync playing",
			detail = segment.label,
			audioResource = segment.audioResource,
			positionMs = positionMs
		)
	)
}

fun ReaderWhispersyncSyncState.onAudiobookPlaybackPausedStep(
	audioResource: String? = null,
	positionMs: Long? = null,
	clearPlaybackOverlay: Boolean = false
): ReaderWhispersyncPlaybackPositionStep {
	val nextState = if (clearPlaybackOverlay) {
		clearOverlayIfNeeded()
	} else {
		this
	}
	return ReaderWhispersyncPlaybackPositionStep(
		state = nextState,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.SyncDisabled,
			label = "Whispersync paused",
			audioResource = audioResource?.trim()?.takeIf { it.isNotEmpty() },
			positionMs = positionMs
		)
	)
}

fun ReaderWhispersyncSyncState.onVisibleTextRange(
	timeline: WhispersyncTimeline?,
	textHref: String,
	visibleStart: Int,
	visibleEnd: Int
): ReaderWhispersyncVisibleRangeStep {
	if (timeline == null) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	val adapter = WhispersyncOverlaySyncAdapter(timeline)
	val readerTarget = adapter.readerTarget(
		WhispersyncReaderSyncInput.VisibleRange(
			textHref = textHref,
			visibleStart = visibleStart,
			visibleEnd = visibleEnd
		)
	) ?: run {
		Logger.w(
			WhispersyncSyncLogTag,
			"Whispersync visible range not matched href=${textHref.whispersyncLogValue()} " +
				"textRange=$visibleStart-$visibleEnd"
		)
		return ReaderWhispersyncVisibleRangeStep(
			state = clearOverlayIfNeeded(),
			status = readerWhispersyncReadyStatus(timeline)
		)
	}
	if (!syncEnabled) {
		return ReaderWhispersyncVisibleRangeStep(
			state = this,
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				label = "Whispersync paused"
			)
		)
	}
	val step = followReaderTarget(readerTarget)
	val target = step.seekTarget
	if (target == null) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	Logger.i(
		WhispersyncSyncLogTag,
		"Whispersync visible range selected audio=${target.audioResource.whispersyncLogValue()} " +
			"positionMs=${target.positionMs} href=${textHref.whispersyncLogValue()} " +
			"textRange=$visibleStart-$visibleEnd " +
			"cue=${target.segment.id.orEmpty().ifBlank { "n/a" }} " +
			"cueTextRange=${target.segment.textStart ?: "n/a"}-${target.segment.textEnd ?: "n/a"}"
	)
	return ReaderWhispersyncVisibleRangeStep(
		state = step.state,
		audioSeekTarget = target,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.SeekingAudio,
			label = "Syncing audiobook",
			detail = target.segment.label,
			audioResource = target.audioResource,
			positionMs = target.positionMs
		)
	)
}

fun ReaderWhispersyncSyncState.onTextPoint(
	timeline: WhispersyncTimeline?,
	textHref: String,
	textOffset: Int
): ReaderWhispersyncVisibleRangeStep {
	if (timeline == null) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	val adapter = WhispersyncOverlaySyncAdapter(timeline)
	val readerTarget = adapter.readerTarget(
		WhispersyncReaderSyncInput.TextPoint(
			textHref = textHref,
			textOffset = textOffset
		)
	) ?: run {
		Logger.w(
			WhispersyncSyncLogTag,
			"Whispersync text point not matched href=${textHref.whispersyncLogValue()} " +
				"textOffset=$textOffset"
		)
		return ReaderWhispersyncVisibleRangeStep(
			state = this,
			status = readerWhispersyncReadyStatus(timeline)
		)
	}
	if (!syncEnabled) {
		return ReaderWhispersyncVisibleRangeStep(
			state = this,
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				label = "Whispersync paused"
			)
		)
	}
	val target = readerTarget.seekTarget
	if (readerTarget.cue.key != activeCueKey) {
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync text point selected audio=${target.audioResource.whispersyncLogValue()} " +
				"positionMs=${target.positionMs} href=${textHref.whispersyncLogValue()} " +
				"textOffset=$textOffset cue=${target.segment.id.orEmpty().ifBlank { "n/a" }} " +
				"textRange=${target.segment.textStart ?: "n/a"}-${target.segment.textEnd ?: "n/a"}"
		)
	}
	val step = followReaderTarget(readerTarget)
	return ReaderWhispersyncVisibleRangeStep(
		state = step.state,
		audioSeekTarget = step.seekTarget,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.SeekingAudio,
			label = "Syncing audiobook",
			detail = target.segment.label,
			audioResource = target.audioResource,
			positionMs = target.positionMs
		)
	)
}

fun readerWhispersyncReadyStatus(timeline: WhispersyncTimeline?): ReaderWhispersyncStatus {
	val count = timeline?.segments?.size ?: 0
	return if (count > 0) {
		ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.Ready,
			label = "Whispersync ready",
			detail = "$count synced ${if (count == 1) "segment" else "segments"}"
		)
	} else {
		ReaderWhispersyncStatus()
	}
}

internal fun String?.whispersyncLogValue(maxLength: Int = 96): String =
	this
		?.replace('\\', '/')
		?.replace(Regex("\\s+"), " ")
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.let { value ->
			if (value.length <= maxLength) value else value.take(maxLength - 1) + "..."
		}
		?: "n/a"
