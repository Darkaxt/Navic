package paige.navic.reader

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
	val targetCandidates = listOf(seekTarget.audioResource) + seekTarget.segment.audioResourceCandidates()
	if (targetCandidates.isEmpty()) return null
	val exactIndex = mediaItems.indexOfFirst { item ->
		val itemCandidates = listOfNotNull(item.uri, item.mediaId, item.resourceKey)
			.flatMap(String::normalizedWhispersyncResourceCandidates)
			.toSet()
		targetCandidates.any { target ->
			itemCandidates.any { candidate ->
				candidate == target ||
					candidate.endsWith("/$target") ||
					target.endsWith("/$candidate")
			}
		}
	}.takeIf { index -> index >= 0 }
	if (exactIndex != null) return exactIndex
	seekTarget.segment.audioTrackIndex
		?.takeIf { trackIndex -> trackIndex in mediaItems.indices }
		?.let { return it }
	return 0.takeIf { mediaItems.size == 1 }
}
