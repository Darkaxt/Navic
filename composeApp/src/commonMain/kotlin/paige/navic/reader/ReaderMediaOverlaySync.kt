package paige.navic.reader

data class ReaderMediaOverlaySyncState(
	val syncEnabled: Boolean = true,
	val activeClipKey: String? = null
)

data class ReaderMediaOverlaySyncStep(
	val state: ReaderMediaOverlaySyncState,
	val readerCommand: ReaderBridgeCommand? = null
)

data class ReadaloudAudioSeekTarget(
	val trackIndex: Int,
	val audioResource: String,
	val positionMs: Long,
	val clip: MediaOverlayClip
)

fun ReaderMediaOverlaySyncState.onReadaloudPlaybackPosition(
	plan: ReadaloudPlaybackPlan,
	timeline: MediaOverlayTimeline?,
	position: ReadaloudPlaybackPosition
): ReaderMediaOverlaySyncStep {
	if (!syncEnabled || timeline == null || plan.mediaItems.isEmpty()) {
		return ReaderMediaOverlaySyncStep(this)
	}
	val audioResource = plan.audioResourceFor(position) ?: return clearOverlayIfNeeded()
	val clip = timeline.activeClip(audioResource, position.positionMs) ?: return clearOverlayIfNeeded()
	val key = clip.readerOverlaySyncKey()
	return if (key == activeClipKey) {
		ReaderMediaOverlaySyncStep(this)
	} else {
		ReaderMediaOverlaySyncStep(
			state = copy(activeClipKey = key),
			readerCommand = ReaderBridgeCommand.ApplyOverlayFragment(clip.toReaderOverlayFragment())
		)
	}
}

fun ReaderMediaOverlaySyncState.audioSeekTargetForReaderEvent(
	plan: ReadaloudPlaybackPlan,
	timeline: MediaOverlayTimeline?,
	event: ReaderBridgeEvent
): ReadaloudAudioSeekTarget? {
	if (!syncEnabled || timeline == null) return null
	val href = event.syncedHref() ?: return null
	val target = timeline.seekTargetForText(href) ?: return null
	if (target.clip.readerOverlaySyncKey() == activeClipKey) return null
	val trackIndex = plan.trackIndexForAudioResource(target.audioResource) ?: return null
	return ReadaloudAudioSeekTarget(
		trackIndex = trackIndex,
		audioResource = target.audioResource,
		positionMs = target.positionMs,
		clip = target.clip
	)
}

private fun ReaderMediaOverlaySyncState.clearOverlayIfNeeded(): ReaderMediaOverlaySyncStep =
	if (activeClipKey == null) {
		ReaderMediaOverlaySyncStep(this)
	} else {
		ReaderMediaOverlaySyncStep(
			state = copy(activeClipKey = null),
			readerCommand = ReaderBridgeCommand.ClearOverlay
		)
	}

private fun ReadaloudPlaybackPlan.audioResourceFor(position: ReadaloudPlaybackPosition): String? =
	mediaItems.getOrNull(position.trackIndex)?.uri
		?: position.mediaId?.let { mediaId -> mediaItems.firstOrNull { item -> item.mediaId == mediaId }?.uri }

private fun ReadaloudPlaybackPlan.trackIndexForAudioResource(audioResource: String): Int? {
	val normalized = normalizedMediaOverlayResource(audioResource)
	return mediaItems.indexOfFirst { item ->
		normalizedMediaOverlayResource(item.uri) == normalized || item.mediaId == audioResource
	}.takeIf { index -> index >= 0 }
}

private fun ReaderBridgeEvent.syncedHref(): String? =
	when (this) {
		is ReaderBridgeEvent.LocationChanged -> locator.href
		is ReaderBridgeEvent.SelectionChanged -> href
		is ReaderBridgeEvent.TocItemChanged -> href
		else -> null
	}?.takeIf { it.isNotBlank() }

internal fun MediaOverlayClip.readerOverlaySyncKey(): String =
	listOf(
		normalizedMediaOverlayResource(audioResource),
		normalizedMediaOverlayResource(textResource),
		fragmentId.orEmpty(),
		startSeconds.toString(),
		endSeconds.toString()
	).joinToString("|")
