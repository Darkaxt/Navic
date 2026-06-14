package paige.navic.reader

data class ReaderReadaloudSyncState(
	val overlayState: ReaderMediaOverlaySyncState = ReaderMediaOverlaySyncState(),
	val engineCommand: ReaderEngineCommand? = null,
	val engineCommandKey: Long = 0L
)

data class ReaderReadaloudReaderEventStep(
	val state: ReaderReadaloudSyncState,
	val audioSeekTarget: ReadaloudAudioSeekTarget? = null
)

fun ReaderReadaloudSyncState.setSyncEnabled(enabled: Boolean): ReaderReadaloudSyncState {
	val step = overlayState.setSyncEnabled(enabled)
	return copy(overlayState = step.state).withEngineCommand(step.engineCommand)
}

fun ReaderReadaloudSyncState.onPlaybackPosition(
	plan: ReadaloudPlaybackPlan,
	timeline: MediaOverlayTimeline?,
	position: ReadaloudPlaybackPosition
): ReaderReadaloudSyncState {
	val step = overlayState.onReadaloudPlaybackPosition(
		plan = plan,
		timeline = timeline,
		position = position
	)
	return copy(overlayState = step.state).withEngineCommand(step.engineCommand)
}

fun ReaderReadaloudSyncState.onReaderEvent(
	plan: ReadaloudPlaybackPlan,
	timeline: MediaOverlayTimeline?,
	event: ReaderBridgeEvent
): ReaderReadaloudReaderEventStep {
	val seekTarget = overlayState.audioSeekTargetForReaderEvent(
		plan = plan,
		timeline = timeline,
		event = event
	) ?: return ReaderReadaloudReaderEventStep(state = this)
	val nextOverlayState = overlayState.copy(
		activeClipKey = seekTarget.clip.readerOverlaySyncKey()
	)
	val nextState = copy(overlayState = nextOverlayState)
		.withEngineCommand(
			ReaderEngineCommand.ApplyMediaOverlay(seekTarget.clip.toReaderOverlayFragment())
		)
	return ReaderReadaloudReaderEventStep(
		state = nextState,
		audioSeekTarget = seekTarget
	)
}

private fun ReaderReadaloudSyncState.withEngineCommand(
	command: ReaderEngineCommand?
): ReaderReadaloudSyncState =
	if (command == null) {
		this
	} else {
		copy(
			engineCommand = command,
			engineCommandKey = engineCommandKey + 1L
		)
	}
