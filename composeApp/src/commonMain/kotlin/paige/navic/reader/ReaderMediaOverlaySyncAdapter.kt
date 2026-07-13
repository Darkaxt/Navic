package paige.navic.reader

typealias ReaderReadaloudSyncState = ReaderOverlaySyncState

data class MediaOverlayPlaybackInput(
	val position: ReadaloudPlaybackPosition
)

data class ReadaloudAudioSeekTarget(
	val trackIndex: Int,
	val audioResource: String,
	val positionMs: Long,
	val clip: MediaOverlayClip
)

data class ReaderReadaloudReaderEventStep(
	val state: ReaderReadaloudSyncState,
	val audioSeekTarget: ReadaloudAudioSeekTarget? = null
)

class MediaOverlaySyncAdapter(
	private val plan: ReadaloudPlaybackPlan,
	private val timeline: MediaOverlayTimeline
) : ReaderOverlayTimelineAdapter<MediaOverlayPlaybackInput, ReaderBridgeEvent, ReadaloudAudioSeekTarget> {
	override fun playbackCue(input: MediaOverlayPlaybackInput): ReaderOverlayCue? {
		val audioResource = plan.audioResourceForOverlay(input.position) ?: return null
		return timeline.activeClip(audioResource, input.position.positionMs)?.toOverlayCue()
	}

	override fun readerTarget(
		input: ReaderBridgeEvent
	): ReaderOverlayReaderTarget<ReadaloudAudioSeekTarget>? {
		val href = input.syncedOverlayHref() ?: return null
		val target = timeline.seekTargetForText(href) ?: return null
		val trackIndex = plan.trackIndexForOverlayAudio(target.audioResource) ?: return null
		return ReaderOverlayReaderTarget(
			cue = target.clip.toOverlayCue(),
			seekTarget = ReadaloudAudioSeekTarget(
				trackIndex = trackIndex,
				audioResource = target.audioResource,
				positionMs = target.positionMs,
				clip = target.clip
			)
		)
	}
}

fun ReaderReadaloudSyncState.onPlaybackPosition(
	plan: ReadaloudPlaybackPlan,
	timeline: MediaOverlayTimeline?,
	position: ReadaloudPlaybackPosition
): ReaderReadaloudSyncState {
	if (timeline == null || plan.mediaItems.isEmpty()) return this
	val adapter = MediaOverlaySyncAdapter(plan, timeline)
	return followPlaybackCue(adapter.playbackCue(MediaOverlayPlaybackInput(position)))
}

fun ReaderReadaloudSyncState.onReaderEvent(
	plan: ReadaloudPlaybackPlan,
	timeline: MediaOverlayTimeline?,
	event: ReaderBridgeEvent
): ReaderReadaloudReaderEventStep {
	if (timeline == null) return ReaderReadaloudReaderEventStep(this)
	val adapter = MediaOverlaySyncAdapter(plan, timeline)
	val step = followReaderTarget(adapter.readerTarget(event))
	return ReaderReadaloudReaderEventStep(
		state = step.state,
		audioSeekTarget = step.seekTarget
	)
}

private fun MediaOverlayClip.toOverlayCue(): ReaderOverlayCue =
	ReaderOverlayCue(
		key = listOf(
			normalizedMediaOverlayResource(audioResource),
			normalizedMediaOverlayResource(textResource),
			fragmentId.orEmpty(),
			startSeconds.toString(),
			endSeconds.toString()
		).joinToString("|"),
		fragment = toReaderOverlayFragment()
	)

private fun ReadaloudPlaybackPlan.audioResourceForOverlay(
	position: ReadaloudPlaybackPosition
): String? =
	mediaItems.getOrNull(position.trackIndex)?.overlayResourceHref
		?: mediaItems.getOrNull(position.trackIndex)?.uri
		?: position.mediaId?.let { mediaId ->
			mediaItems.firstOrNull { item -> item.mediaId == mediaId }
				?.let { item -> item.overlayResourceHref ?: item.uri }
		}

private fun ReadaloudPlaybackPlan.trackIndexForOverlayAudio(audioResource: String): Int? {
	val normalized = normalizedMediaOverlayResource(audioResource)
	return mediaItems.indexOfFirst { item ->
		normalizedMediaOverlayResource(item.overlayResourceHref ?: item.uri) == normalized ||
			item.mediaId == audioResource
	}.takeIf { index -> index >= 0 }
}

private fun ReaderBridgeEvent.syncedOverlayHref(): String? =
	when (this) {
		is ReaderBridgeEvent.LocationChanged -> locator.href
		is ReaderBridgeEvent.SelectionChanged -> href
		is ReaderBridgeEvent.TocItemChanged -> href
		else -> null
	}?.takeIf { it.isNotBlank() }
