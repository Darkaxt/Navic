package paige.navic.reader

fun readerWhispersyncPlaybackCommandForSeekTarget(
	playbackPlan: ReadaloudPlaybackPlan?,
	seekTarget: WhispersyncAudioSeekTarget?
): ReaderReadaloudPlaybackCommand? {
	if (playbackPlan == null || seekTarget == null || playbackPlan.mediaItems.isEmpty()) return null
	val trackIndex = playbackPlan.trackIndexForWhispersyncAudioResource(seekTarget.audioResource) ?: return null
	return ReaderReadaloudPlaybackCommand.SeekToTrack(
		trackIndex = trackIndex,
		positionMs = seekTarget.positionMs.coerceAtLeast(0L)
	)
}

private fun ReadaloudPlaybackPlan.trackIndexForWhispersyncAudioResource(audioResource: String): Int? {
	val targetCandidates = audioResource.whispersyncAudioResourceCandidates()
	if (targetCandidates.isEmpty()) return null
	return mediaItems.indexOfFirst { item ->
		val itemCandidates = listOfNotNull(item.uri, item.mediaId, item.resourceKey)
			.flatMap(String::whispersyncAudioResourceCandidates)
			.toSet()
		targetCandidates.any { target ->
			itemCandidates.any { candidate ->
				candidate == target ||
					candidate.endsWith("/$target") ||
					target.endsWith("/$candidate")
			}
		}
	}.takeIf { index -> index >= 0 }
}

private fun String.whispersyncAudioResourceCandidates(): List<String> {
	val cleaned = trim()
		.substringBefore('#')
		.substringBefore('?')
		.replace('\\', '/')
		.trimStart('/')
		.takeIf { it.isNotBlank() }
		?: return emptyList()
	val withoutScheme = cleaned.substringAfter("://", missingDelimiterValue = cleaned)
	val urlPath = withoutScheme.substringAfter('/', missingDelimiterValue = withoutScheme)
	return listOf(
		cleaned,
		withoutScheme,
		urlPath
	)
		.map(::normalizedMediaOverlayResource)
		.filter { it.isNotBlank() }
		.distinct()
}
