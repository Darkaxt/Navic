package paige.navic.reader

data class ReaderWhispersyncPlaybackControlState(
	val visible: Boolean = false,
	val loading: Boolean = false,
	val crossed: Boolean = true,
	val enabled: Boolean = false,
	val contentDescription: String = "Whispersync audiobook",
	val command: ReaderReadaloudPlaybackCommand? = null
)

fun readerWhispersyncPlaybackControlState(
	status: ReaderWhispersyncStatus,
	playbackState: ReaderReadaloudPlaybackUiState?
): ReaderWhispersyncPlaybackControlState {
	if (!status.visible ||
		status.kind == ReaderWhispersyncStatusKind.Ready ||
		status.kind == ReaderWhispersyncStatusKind.NoActiveCue
	) {
		return ReaderWhispersyncPlaybackControlState()
	}
	val availablePlayback = playbackState?.takeIf { it.isAvailable }
		?: return ReaderWhispersyncPlaybackControlState(
			visible = true,
			loading = status.kind != ReaderWhispersyncStatusKind.LoadFailed,
			crossed = true,
			enabled = false,
			contentDescription = "Whispersync audiobook loading"
		)
	val command = availablePlayback.toggleCommand()
	val crossed = !availablePlayback.isPlaying || !availablePlayback.syncEnabled
	return ReaderWhispersyncPlaybackControlState(
		visible = true,
		loading = false,
		crossed = crossed,
		enabled = command != null,
		contentDescription = if (availablePlayback.isPlaying) {
			"Pause Whispersync audiobook"
		} else {
			"Play Whispersync audiobook"
		},
		command = command
	)
}

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
	seekTarget.segment.audioTrackIndex
		?.takeIf { trackIndex -> trackIndex in mediaItems.indices }
		?.let { return it }
	return 0.takeIf { mediaItems.size == 1 }
}
