package paige.navic.reader

data class ReaderReadaloudSyncState(
	val overlayState: ReaderMediaOverlaySyncState = ReaderMediaOverlaySyncState(),
	val readerCommand: ReaderBridgeCommand? = null,
	val readerCommandKey: Long = 0L
)

data class ReaderReadaloudReaderEventStep(
	val state: ReaderReadaloudSyncState,
	val audioSeekTarget: ReadaloudAudioSeekTarget? = null
)

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
	return copy(overlayState = step.state).withReaderCommand(step.readerCommand)
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
		.withReaderCommand(
			ReaderBridgeCommand.ApplyOverlayFragment(
				seekTarget.clip.toReaderOverlayFragment()
			)
		)
	return ReaderReadaloudReaderEventStep(
		state = nextState,
		audioSeekTarget = seekTarget
	)
}

private fun ReaderReadaloudSyncState.withReaderCommand(
	command: ReaderBridgeCommand?
): ReaderReadaloudSyncState =
	if (command == null) {
		this
	} else {
		copy(
			readerCommand = command,
			readerCommandKey = readerCommandKey + 1L
		)
	}
