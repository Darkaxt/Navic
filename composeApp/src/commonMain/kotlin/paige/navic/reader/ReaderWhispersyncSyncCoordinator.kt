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
	val activeProgressKey: Int? = null,
	val engineCommand: ReaderEngineCommand? = null,
	val engineCommandKey: Long = 0L,
	val pausedAtBoundary: Boolean = false
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
	PausedAtPageBoundary,
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
	val status: ReaderWhispersyncStatus? = null,
	val playbackCommand: ReaderReadaloudPlaybackCommand? = null
)

data class ReaderWhispersyncPlaybackPositionStep(
	val state: ReaderWhispersyncSyncState,
	val status: ReaderWhispersyncStatus? = null,
	val activeSegment: ReaderWhispersyncActiveSegmentDiagnostic? = null,
	val playbackCommand: ReaderReadaloudPlaybackCommand? = null
)

data class ReaderWhispersyncActiveSegmentDiagnostic(
	val audioResource: String,
	val audioTrackIndex: Int? = null,
	val positionMs: Long,
	val segmentId: String? = null,
	val label: String? = null,
	val textHref: String,
	val textStart: Int? = null,
	val textEnd: Int? = null,
	val fragmentId: String? = null,
	val progress: Double? = null,
	val applyMediaOverlay: Boolean = false
)

fun ReaderWhispersyncSyncState.setSyncEnabled(enabled: Boolean): ReaderWhispersyncSyncState {
	val nextState = copy(
		syncEnabled = enabled,
		activeSegmentKey = if (enabled) activeSegmentKey else null,
		activeProgressKey = if (enabled) activeProgressKey else null
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
	audioTrackIndex: Int? = null,
	visibleTextRange: ReaderWhispersyncVisibleTextRange? = null
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
	// Page-bounded playback: the ebook leads, so audio must not advance past the
	// visible page. Check the boundary BEFORE the active-segment lookup so audio
	// pauses exactly at the end of the last on-page segment (not after spilling
	// into the next segment). A deliberate user pause is distinguished by status
	// (this sets PausedAtPageBoundary, not SyncDisabled) so a page-turn can resume.
	val pageSegments = visibleTextRange?.let { range ->
		timeline.segmentsForVisibleTextRange(range.textHref, range.visibleStart, range.visibleEnd)
	}
	val pageBoundaryMs = pageSegments?.maxOfOrNull { it.endMs }
	if (pageBoundaryMs != null && positionMs >= pageBoundaryMs && !pausedAtBoundary) {
		val lastOnPage = pageSegments.lastOrNull { it.endMs == pageBoundaryMs }
		return ReaderWhispersyncPlaybackPositionStep(
			state = copy(pausedAtBoundary = true),
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.PausedAtPageBoundary,
				label = "Reached end of page",
				detail = lastOnPage?.label,
				audioResource = lastOnPage?.audioResource ?: audioResource,
				positionMs = positionMs
			),
			activeSegment = lastOnPage?.let { segment ->
				ReaderWhispersyncActiveSegmentDiagnostic(
					audioResource = segment.audioResource,
					audioTrackIndex = segment.audioTrackIndex,
					positionMs = positionMs,
					segmentId = segment.id,
					label = segment.label,
					textHref = segment.textHref,
					textStart = segment.textStart,
					textEnd = segment.textEnd,
					fragmentId = segment.fragmentId,
					progress = segment.progressAt(positionMs),
					applyMediaOverlay = false
				)
			},
			playbackCommand = ReaderReadaloudPlaybackCommand.Pause
		)
	}
	val segment = timeline.activeSegment(
		audioResource = audioResource,
		positionMs = positionMs,
		audioTrackIndex = audioTrackIndex
	)
		?: return ReaderWhispersyncPlaybackPositionStep(
			state = clearOverlayIfNeeded(),
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.NoActiveCue,
				label = "No synced text here",
				detail = audioResource,
				audioResource = audioResource,
				positionMs = positionMs
			)
		)
	val key = segment.readerOverlaySyncKey()
	val progressKey = segment.readerOverlayProgressKey(positionMs)
	val command = if (key == activeSegmentKey && progressKey == activeProgressKey) {
		null
	} else {
		ReaderEngineCommand.ApplyMediaOverlay(segment.toReaderOverlayFragment(positionMs))
	}
	val nextState = if (command == null) {
		if (pausedAtBoundary) copy(pausedAtBoundary = false) else this
	} else {
		copy(
			activeSegmentKey = key,
			activeProgressKey = progressKey,
			pausedAtBoundary = false
		).withEngineCommand(command)
	}
	return ReaderWhispersyncPlaybackPositionStep(
		state = nextState,
		status = ReaderWhispersyncStatus(
			kind = ReaderWhispersyncStatusKind.Playing,
			label = "Whispersync playing",
			detail = segment.label,
			audioResource = segment.audioResource,
			positionMs = positionMs
		),
		activeSegment = ReaderWhispersyncActiveSegmentDiagnostic(
			audioResource = segment.audioResource,
			audioTrackIndex = segment.audioTrackIndex,
			positionMs = positionMs,
			segmentId = segment.id,
			label = segment.label,
			textHref = segment.textHref,
			textStart = segment.textStart,
			textEnd = segment.textEnd,
			fragmentId = segment.fragmentId,
			progress = segment.progressAt(positionMs),
			applyMediaOverlay = command is ReaderEngineCommand.ApplyMediaOverlay
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
	val resumePlayback = pausedAtBoundary && syncEnabled
	if (resumePlayback) {
		// Seamless resume: continue from the paused position WITHOUT seeking, so a
		// sentence split across two pages is not restarted from its beginning. The
		// audio→text path highlights the new page's cues as audio reaches them.
		return ReaderWhispersyncVisibleRangeStep(
			state = copy(pausedAtBoundary = false),
			playbackCommand = ReaderReadaloudPlaybackCommand.Play,
			status = ReaderWhispersyncStatus(
				kind = ReaderWhispersyncStatusKind.SeekingAudio,
				label = "Resuming audiobook",
				detail = target.segment.label,
				audioResource = target.audioResource,
				positionMs = target.positionMs
			)
		)
	}
	val key = target.segment.readerOverlaySyncKey()
	if (key == activeSegmentKey) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	val nextState = copy(
		activeSegmentKey = key,
		activeProgressKey = null
	)
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

fun ReaderWhispersyncSyncState.onTextOffset(
	timeline: WhispersyncTimeline?,
	textHref: String,
	textOffset: Int
): ReaderWhispersyncVisibleRangeStep {
	if (timeline == null) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
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
	val target = timeline.seekTargetForTextOffset(
		textHref = textHref,
		textOffset = textOffset
	) ?: return ReaderWhispersyncVisibleRangeStep(
		state = this,
		status = readerWhispersyncReadyStatus(timeline)
	)
	val resumePlayback = pausedAtBoundary && syncEnabled
	val key = target.segment.readerOverlaySyncKey()
	val nextState = copy(
		activeSegmentKey = key,
		activeProgressKey = null,
		pausedAtBoundary = false
	)
		.withEngineCommand(ReaderEngineCommand.ApplyMediaOverlay(target.segment.toReaderOverlayFragment()))
	return ReaderWhispersyncVisibleRangeStep(
		state = nextState,
		audioSeekTarget = target,
		playbackCommand = if (resumePlayback) ReaderReadaloudPlaybackCommand.Play else null,
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
		copy(
			activeSegmentKey = null,
			activeProgressKey = null
		)
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

private fun WhispersyncSegment.readerOverlayProgressKey(positionMs: Long): Int =
	(progressAt(positionMs) * 1000.0).toInt().coerceIn(0, 1000)
