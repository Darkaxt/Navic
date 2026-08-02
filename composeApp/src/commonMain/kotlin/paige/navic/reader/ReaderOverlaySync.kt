package paige.navic.reader

data class ReaderOverlaySyncState(
	val syncEnabled: Boolean = true,
	val activeCueKey: String? = null,
	val activeProgressTextEnd: Int? = null,
	val activeOverlayRequestId: Long? = null,
	val confirmedOverlayRequestId: Long? = null,
	val engineCommand: ReaderEngineCommand? = null,
	val engineCommandKey: Long = 0L
)

data class ReaderOverlayCue(
	val key: String,
	val fragment: ReaderOverlayFragment,
	val progressTextEnd: Int? = null
)

data class ReaderOverlayReaderTarget<T>(
	val cue: ReaderOverlayCue,
	val seekTarget: T,
	val repeatSeek: Boolean = false,
	val updateRepeatedCue: Boolean = true
)

data class ReaderOverlayReaderStep<T>(
	val state: ReaderOverlaySyncState,
	val seekTarget: T? = null
)

interface ReaderOverlayTimelineAdapter<PlaybackInput, ReaderInput, SeekTarget> {
	fun playbackCue(input: PlaybackInput): ReaderOverlayCue?

	fun readerTarget(input: ReaderInput): ReaderOverlayReaderTarget<SeekTarget>?
}

fun ReaderOverlaySyncState.setSyncEnabled(enabled: Boolean): ReaderOverlaySyncState {
	if (enabled == syncEnabled) return this
	val nextState = copy(syncEnabled = enabled)
	return if (!enabled) nextState.clearOverlayIfNeeded() else nextState
}

fun ReaderOverlaySyncState.followPlaybackCue(cue: ReaderOverlayCue?): ReaderOverlaySyncState {
	if (!syncEnabled) return this
	return if (cue == null) clearOverlayIfNeeded() else followCue(cue)
}

fun <T> ReaderOverlaySyncState.followReaderTarget(
	target: ReaderOverlayReaderTarget<T>?
): ReaderOverlayReaderStep<T> {
	if (!syncEnabled || target == null) return ReaderOverlayReaderStep(this)
	val repeatedCue = target.cue.key == activeCueKey
	val nextState =
		if (repeatedCue && !target.updateRepeatedCue) this
		else followCue(target.cue)
	val commandChanged = nextState.engineCommandKey != engineCommandKey
	return ReaderOverlayReaderStep(
		state = nextState,
		seekTarget = target.seekTarget.takeIf {
			!repeatedCue || target.repeatSeek || commandChanged
		}
	)
}

private fun ReaderOverlaySyncState.followCue(cue: ReaderOverlayCue): ReaderOverlaySyncState =
	if (cue.key != activeCueKey) {
		val requestId = engineCommandKey + 1L
		copy(
			activeCueKey = cue.key,
			activeProgressTextEnd = cue.progressTextEnd,
			activeOverlayRequestId = requestId,
			confirmedOverlayRequestId = null,
			engineCommand = ReaderEngineCommand.ApplyMediaOverlay(
				cue.fragment.copy(overlayRequestId = requestId)
			),
			engineCommandKey = requestId
		)
	} else if (
		confirmedOverlayRequestId == activeOverlayRequestId &&
		cue.progressTextEnd != null &&
		cue.progressTextEnd != activeProgressTextEnd
	) {
		copy(activeProgressTextEnd = cue.progressTextEnd)
			.withEngineCommand(
				ReaderEngineCommand.UpdateMediaOverlayProgress(
					cue.fragment.copy(overlayRequestId = activeOverlayRequestId)
				)
			)
	} else {
		this
	}

internal fun ReaderOverlaySyncState.clearOverlayIfNeeded(): ReaderOverlaySyncState =
	if (activeCueKey == null) {
		this
	} else {
		copy(
			activeCueKey = null,
			activeProgressTextEnd = null,
			activeOverlayRequestId = null,
			confirmedOverlayRequestId = null
		).withEngineCommand(ReaderEngineCommand.ClearMediaOverlay)
	}

internal fun ReaderOverlaySyncState.confirmOverlay(requestId: Long?): ReaderOverlaySyncState =
	if (requestId != null && requestId == activeOverlayRequestId) {
		copy(confirmedOverlayRequestId = requestId)
	} else {
		this
	}

internal fun ReaderOverlaySyncState.rejectOverlay(requestId: Long?): ReaderOverlaySyncState =
	if (requestId != null && requestId != activeOverlayRequestId) {
		this
	} else {
		copy(
			activeCueKey = null,
			activeProgressTextEnd = null,
			activeOverlayRequestId = null,
			confirmedOverlayRequestId = null,
			engineCommand = null
		)
	}

internal fun ReaderOverlaySyncState.hasConfirmedOverlay(requestId: Long?): Boolean =
	requestId != null &&
		requestId == activeOverlayRequestId &&
		requestId == confirmedOverlayRequestId

private fun ReaderOverlaySyncState.withEngineCommand(
	command: ReaderEngineCommand?
): ReaderOverlaySyncState =
	if (command == null) {
		this
	} else {
		copy(
			engineCommand = command,
			engineCommandKey = engineCommandKey + 1L
		)
	}
