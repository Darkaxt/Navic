package paige.navic.reader

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

data class ReaderWhispersyncSyncState(
	val syncEnabled: Boolean = true,
	val activeSegmentKey: String? = null,
	val engineCommand: ReaderEngineCommand? = null,
	val engineCommandKey: Long = 0L
)

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

fun ReaderWhispersyncSyncState.setSyncEnabled(enabled: Boolean): ReaderWhispersyncSyncState {
	val nextState = copy(
		syncEnabled = enabled,
		activeSegmentKey = if (enabled) activeSegmentKey else null
	)
	val clearCommand = if (!enabled && activeSegmentKey != null) {
		ReaderEngineCommand.ClearMediaOverlay
	} else {
		null
	}
	return nextState.withEngineCommand(clearCommand)
}

fun ReaderWhispersyncSyncState.onAudiobookPlaybackPosition(
	timeline: WhispersyncTimeline?,
	audioResource: String,
	positionMs: Long,
	audioTrackIndex: Int? = null
): ReaderWhispersyncSyncState =
	onAudiobookPlaybackPositionStep(
		timeline = timeline,
		audioResource = audioResource,
		positionMs = positionMs,
		audioTrackIndex = audioTrackIndex
	).state

fun ReaderWhispersyncSyncState.onAudiobookPlaybackPositionStep(
	timeline: WhispersyncTimeline?,
	audioResource: String,
	positionMs: Long,
	audioTrackIndex: Int? = null
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
	val segment = timeline.activeSegment(
		audioResource = audioResource,
		positionMs = positionMs,
		audioTrackIndex = audioTrackIndex
	)
		?: return ReaderWhispersyncPlaybackPositionStep(
			state = clearOverlayIfNeeded(),
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.Mismatch,
				label = "Whispersync mismatch",
				detail = audioResource,
				audioResource = audioResource,
				positionMs = positionMs
			)
		)
	val key = segment.readerOverlaySyncKey()
	val nextState = if (key == activeSegmentKey) {
		this
	} else {
		copy(activeSegmentKey = key)
			.withEngineCommand(ReaderEngineCommand.ApplyMediaOverlay(segment.toReaderOverlayFragment()))
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

fun ReaderWhispersyncSyncState.onVisibleTextRange(
	timeline: WhispersyncTimeline?,
	textHref: String,
	visibleStart: Int,
	visibleEnd: Int
): ReaderWhispersyncVisibleRangeStep {
	if (timeline == null) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	val target = timeline.seekTargetForVisibleTextRange(
		textHref = textHref,
		visibleStart = visibleStart,
		visibleEnd = visibleEnd
	) ?: return ReaderWhispersyncVisibleRangeStep(
		state = clearOverlayIfNeeded(),
		status = readerWhispersyncReadyStatus(timeline)
	)
	if (!syncEnabled) {
		return ReaderWhispersyncVisibleRangeStep(
			state = this,
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SyncDisabled,
				label = "Whispersync paused"
			)
		)
	}
	val key = target.segment.readerOverlaySyncKey()
	if (key == activeSegmentKey) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	val nextState = copy(activeSegmentKey = key)
		.withEngineCommand(ReaderEngineCommand.ApplyMediaOverlay(target.segment.toReaderOverlayFragment()))
	return ReaderWhispersyncVisibleRangeStep(
		state = nextState,
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

private fun ReaderWhispersyncSyncState.clearOverlayIfNeeded(): ReaderWhispersyncSyncState =
	if (activeSegmentKey == null) {
		this
	} else {
		copy(activeSegmentKey = null)
			.withEngineCommand(ReaderEngineCommand.ClearMediaOverlay)
	}

private fun ReaderWhispersyncSyncState.withEngineCommand(
	command: ReaderEngineCommand?
): ReaderWhispersyncSyncState =
	if (command == null) {
		this
	} else {
		copy(
			engineCommand = command,
			engineCommandKey = engineCommandKey + 1L
		)
	}

private fun WhispersyncSegment.readerOverlaySyncKey(): String =
	listOf(
		audioResourceId.orEmpty(),
		audioTrackIndex?.toString().orEmpty(),
		normalizedMediaOverlayResource(audioResource),
		normalizedMediaOverlayResource(textHref),
		fragmentId.orEmpty(),
		rangeCfi.orEmpty(),
		startMs.toString(),
		endMs.toString()
	).joinToString("|")
