package paige.navic.reader

import paige.navic.util.core.Logger

const val WhispersyncSyncLogTag = "WhispersyncSync"

data class ReaderWhispersyncPendingAudioSeek(
	val overlayRequestId: Long,
	val target: WhispersyncAudioSeekTarget
)

data class ReaderWhispersyncSessionState(
	val sidecar: WhispersyncSidecar? = null,
	val sync: ReaderWhispersyncSyncState = ReaderWhispersyncSyncState(),
	val visibleTextRange: ReaderWhispersyncVisibleTextRange? = null,
	val pendingAudioSeek: ReaderWhispersyncPendingAudioSeek? = null,
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

enum class ReaderWhispersyncStatusMessage {
	Ready,
	Paused,
	SeekingAudio,
	Playing,
	NoActiveCue,
	VisiblePageEnded,
	Mismatch,
	Unavailable,
	AudioUnavailable
}

data class ReaderWhispersyncStatus(
	val kind: ReaderWhispersyncStatusKind = ReaderWhispersyncStatusKind.Unavailable,
	val message: ReaderWhispersyncStatusMessage? = null,
	val detail: String? = null,
	val syncedSegmentCount: Int? = null,
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
				message = ReaderWhispersyncStatusMessage.Paused
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
					"Whispersync playback state=no-active-cue " +
						"active=true matched=false reason=timeline-gap"
				)
			}
			return ReaderWhispersyncPlaybackPositionStep(
				state = clearOverlayIfNeeded(),
				status = ReaderWhispersyncStatus(
					kind = ReaderWhispersyncStatusKind.NoActiveCue,
					message = ReaderWhispersyncStatusMessage.NoActiveCue,
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
			"Whispersync playback state=active matched=true active=true " +
				"command=$commandLabel"
		)
	}
	return ReaderWhispersyncPlaybackPositionStep(
		state = nextState,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.Playing,
			message = ReaderWhispersyncStatusMessage.Playing,
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
			message = ReaderWhispersyncStatusMessage.Paused,
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
			"Whispersync visible range state=ready matched=false active=${activeCueKey != null} " +
				"reason=no-timeline-match"
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
				message = ReaderWhispersyncStatusMessage.Paused
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
		"Whispersync visible range state=seeking matched=true active=true command=seek"
	)
	return ReaderWhispersyncVisibleRangeStep(
		state = step.state,
		audioSeekTarget = target,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.SeekingAudio,
			message = ReaderWhispersyncStatusMessage.SeekingAudio,
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
			"Whispersync text point state=ready matched=false active=${activeCueKey != null} " +
				"reason=no-timeline-match"
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
				message = ReaderWhispersyncStatusMessage.Paused
			)
		)
	}
	val target = readerTarget.seekTarget
		Logger.i(
			WhispersyncSyncLogTag,
			"Whispersync text point state=seeking matched=true " +
				"active=${readerTarget.cue.key == activeCueKey} command=seek"
		)
	val step = followReaderTarget(readerTarget)
	return ReaderWhispersyncVisibleRangeStep(
		state = step.state,
		audioSeekTarget = step.seekTarget,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.SeekingAudio,
			message = ReaderWhispersyncStatusMessage.SeekingAudio,
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
			message = ReaderWhispersyncStatusMessage.Ready,
			syncedSegmentCount = count
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
