package paige.navic.reader

data class ReaderWhispersyncSyncState(
	val syncEnabled: Boolean = true,
	val activeSegmentKey: String? = null,
	val engineCommand: ReaderEngineCommand? = null,
	val engineCommandKey: Long = 0L
)

data class ReaderWhispersyncVisibleRangeStep(
	val state: ReaderWhispersyncSyncState,
	val audioSeekTarget: WhispersyncAudioSeekTarget? = null
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
	positionMs: Long
): ReaderWhispersyncSyncState {
	if (!syncEnabled || timeline == null) return this
	val segment = timeline.activeSegment(audioResource = audioResource, positionMs = positionMs)
		?: return clearOverlayIfNeeded()
	val key = segment.readerOverlaySyncKey()
	return if (key == activeSegmentKey) {
		this
	} else {
		copy(activeSegmentKey = key)
			.withEngineCommand(ReaderEngineCommand.ApplyMediaOverlay(segment.toReaderOverlayFragment()))
	}
}

fun ReaderWhispersyncSyncState.onVisibleTextRange(
	timeline: WhispersyncTimeline?,
	textHref: String,
	visibleStart: Int,
	visibleEnd: Int
): ReaderWhispersyncVisibleRangeStep {
	if (!syncEnabled || timeline == null) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	val target = timeline.seekTargetForVisibleTextRange(
		textHref = textHref,
		visibleStart = visibleStart,
		visibleEnd = visibleEnd
	) ?: return ReaderWhispersyncVisibleRangeStep(state = this)
	val key = target.segment.readerOverlaySyncKey()
	if (key == activeSegmentKey) {
		return ReaderWhispersyncVisibleRangeStep(state = this)
	}
	val nextState = copy(activeSegmentKey = key)
		.withEngineCommand(ReaderEngineCommand.ApplyMediaOverlay(target.segment.toReaderOverlayFragment()))
	return ReaderWhispersyncVisibleRangeStep(
		state = nextState,
		audioSeekTarget = target
	)
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
		normalizedMediaOverlayResource(audioResource),
		normalizedMediaOverlayResource(textHref),
		fragmentId.orEmpty(),
		rangeCfi.orEmpty(),
		startMs.toString(),
		endMs.toString()
	).joinToString("|")
