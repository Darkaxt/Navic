package paige.navic.reader

enum class ReaderWhispersyncPlaybackControlDescription {
	Audiobook,
	Loading,
	Pause,
	Play,
	NoAudioCueOnPage,
	WaitingForPage
}

data class ReaderWhispersyncPlaybackControlState(
	val visible: Boolean = false,
	val loading: Boolean = false,
	val crossed: Boolean = true,
	val enabled: Boolean = false,
	val noAudioCueOnPage: Boolean = false,
	val contentDescription: ReaderWhispersyncPlaybackControlDescription =
		ReaderWhispersyncPlaybackControlDescription.Audiobook,
	val command: ReaderReadaloudPlaybackCommand? = null
)

fun readerWhispersyncPlaybackControlState(
	status: ReaderWhispersyncStatus,
	playbackState: ReaderReadaloudPlaybackUiState?,
	hasPreparedVisibleTarget: Boolean,
	pageHeld: Boolean = false
): ReaderWhispersyncPlaybackControlState {
	if (playbackState?.isPlaying == true) {
		return ReaderWhispersyncPlaybackControlState(
			visible = true,
			loading = false,
			crossed = false,
			enabled = true,
			noAudioCueOnPage = false,
			contentDescription = ReaderWhispersyncPlaybackControlDescription.Pause,
			command = ReaderReadaloudPlaybackCommand.Pause
		)
	}
	if (!status.visible) {
		return ReaderWhispersyncPlaybackControlState()
	}
	// The page is not READY (page preparation, opening question): audio waits for it.
	if (pageHeld) {
		return ReaderWhispersyncPlaybackControlState(
			visible = true,
			loading = true,
			crossed = true,
			enabled = false,
			contentDescription = ReaderWhispersyncPlaybackControlDescription.WaitingForPage
		)
	}
	if (
		status.kind == ReaderWhispersyncStatusKind.SeekingAudio &&
		playbackState?.isPlaying != true
	) {
		return ReaderWhispersyncPlaybackControlState(
			visible = true,
			loading = true,
			crossed = true,
			enabled = false,
			contentDescription = ReaderWhispersyncPlaybackControlDescription.Loading
		)
	}
	val availablePlayback = playbackState?.takeIf { it.isAvailable }
		?: return ReaderWhispersyncPlaybackControlState(
			visible = true,
			loading = status.kind != ReaderWhispersyncStatusKind.LoadFailed,
			crossed = true,
			enabled = false,
			contentDescription = ReaderWhispersyncPlaybackControlDescription.Loading
		)
	// A finished spread (page end) replays from its first visible word on Play.
	val command = availablePlayback.whispersyncHeadsetCommand(
		hasPreparedVisibleTarget = hasPreparedVisibleTarget
	)
	val crossed = !availablePlayback.isPlaying || !availablePlayback.syncEnabled
	val noAudioCueOnPage = !availablePlayback.isPlaying && !hasPreparedVisibleTarget
	return ReaderWhispersyncPlaybackControlState(
		visible = true,
		loading = false,
		crossed = crossed,
		enabled = command != null,
		noAudioCueOnPage = noAudioCueOnPage,
		contentDescription = when {
			availablePlayback.isPlaying -> ReaderWhispersyncPlaybackControlDescription.Pause
			noAudioCueOnPage -> ReaderWhispersyncPlaybackControlDescription.NoAudioCueOnPage
			else -> ReaderWhispersyncPlaybackControlDescription.Play
		},
		command = command
	)
}

fun readerWhispersyncPlaybackControlPresentation(
	control: ReaderWhispersyncPlaybackControlState,
	shellCoverVisible: Boolean,
	mediaOverlayAvailable: Boolean
): ReaderWhispersyncPlaybackControlState =
	if (control.command == ReaderReadaloudPlaybackCommand.Pause) {
		control
	} else if (shellCoverVisible || !mediaOverlayAvailable) {
		control.copy(visible = false)
	} else {
		control
	}

fun readerWhispersyncTransportEnabled(
	playbackState: ReaderReadaloudPlaybackUiState,
	hasPreparedVisibleTarget: Boolean
): Boolean =
	playbackState.isAvailable && (playbackState.isPlaying || hasPreparedVisibleTarget)

private fun ReaderReadaloudPlaybackUiState.whispersyncHeadsetCommand(
	hasPreparedVisibleTarget: Boolean
): ReaderReadaloudPlaybackCommand? =
	if (!readerWhispersyncTransportEnabled(this, hasPreparedVisibleTarget)) {
		null
	} else if (isPlaying) {
		ReaderReadaloudPlaybackCommand.Pause
	} else {
		ReaderReadaloudPlaybackCommand.Play
	}

/** The page transport row: back/forward step pages, play/pause keeps the position, stop resets. */
data class ReaderWhispersyncTransportControls(
	val playPause: ReaderReadaloudPlaybackCommand?,
	val playing: Boolean,
	val stopEnabled: Boolean
)

fun readerWhispersyncTransportControls(
	control: ReaderWhispersyncPlaybackControlState,
	playbackState: ReaderReadaloudPlaybackUiState?,
	userPaused: Boolean,
	transportPhase: ReaderWhispersyncTransportPhase?
): ReaderWhispersyncTransportControls = ReaderWhispersyncTransportControls(
	playPause = control.command?.takeIf { control.enabled },
	playing = playbackState?.isPlaying == true,
	stopEnabled = playbackState?.isPlaying == true || userPaused ||
		transportPhase == ReaderWhispersyncTransportPhase.BoundaryPaused
)

fun readerWhispersyncPlaybackCommandForSeekTarget(
	playbackPlan: ReadaloudPlaybackPlan?,
	seekTarget: WhispersyncAudioSeekTarget?
): ReaderReadaloudPlaybackCommand? {
	if (playbackPlan == null || seekTarget == null || playbackPlan.mediaItems.isEmpty()) return null
	val trackIndex = playbackPlan.trackIndexForWhispersyncAudioResource(seekTarget) ?: return null
	return ReaderReadaloudPlaybackCommand.SeekToTrack(
		trackIndex = trackIndex,
		positionMs = seekTarget.positionMs.coerceAtLeast(0L)
	)
}

private fun ReadaloudPlaybackPlan.trackIndexForWhispersyncAudioResource(
	seekTarget: WhispersyncAudioSeekTarget
): Int? {
	seekTarget.audioTrackIndex?.let { trackIndex ->
		val mediaItem = mediaItems.getOrNull(trackIndex) ?: return null
		if (mediaItem.resourceKey == seekTarget.audioResource || mediaItems.size == 1) return trackIndex
		// Bindery resource keys need not equal sidecar audio hrefs; agreement with the sidecar's own
		// track resolution is the same identity. Disagreement fails closed.
		return trackIndex.takeIf { sidecarTrackIndexForWhispersyncAudioResource(seekTarget) == it }
	}
	return sidecarTrackIndexForWhispersyncAudioResource(seekTarget)
}

private fun ReadaloudPlaybackPlan.sidecarTrackIndexForWhispersyncAudioResource(
	seekTarget: WhispersyncAudioSeekTarget
): Int? {
	seekTarget.segment.audioTrackIndex
		?.takeIf { trackIndex -> trackIndex in mediaItems.indices }
		?.let { return it }
	val targetCandidates = (listOf(seekTarget.audioResource) + seekTarget.segment.audioResourceCandidates())
		.flatMap(String::normalizedWhispersyncResourceCandidates)
		.toSet()
	if (targetCandidates.isEmpty()) return null
	val exactIndex = mediaItems.indexOfFirst { item ->
		val itemCandidates = listOfNotNull(item.uri, item.mediaId, item.resourceKey)
			.flatMap(String::normalizedWhispersyncResourceCandidates)
			.toSet()
		targetCandidates.any { target ->
			target in itemCandidates
		}
	}.takeIf { index -> index >= 0 }
	if (exactIndex != null) return exactIndex
	return 0.takeIf { mediaItems.size == 1 }
}
