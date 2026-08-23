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

sealed interface ReaderReadaloudReaderInteraction {
	data class UserNavigation(
		val textHref: String,
		val causalSequence: Long
	) : ReaderReadaloudReaderInteraction

	data class ExplicitSelection(
		val textHref: String
	) : ReaderReadaloudReaderInteraction
}

data class ReaderReadaloudReaderEventStep(
	val state: ReaderReadaloudSyncState,
	val audioSeekTarget: ReadaloudAudioSeekTarget? = null,
	val consumedUserNavigationCausalSequence: Long? = null
)

class MediaOverlaySyncAdapter(
	private val plan: ReadaloudPlaybackPlan,
	private val timeline: MediaOverlayTimeline
) : ReaderOverlayTimelineAdapter<MediaOverlayPlaybackInput, ReaderReadaloudReaderInteraction, ReadaloudAudioSeekTarget> {
	override fun playbackCue(input: MediaOverlayPlaybackInput): ReaderOverlayCue? {
		val audioResource = plan.audioResourceForOverlay(input.position) ?: return null
		return timeline.activeClip(audioResource, input.position.positionMs)?.toOverlayCue()
	}

	override fun readerTarget(
		input: ReaderReadaloudReaderInteraction
	): ReaderOverlayReaderTarget<ReadaloudAudioSeekTarget>? {
		val href = when (input) {
			is ReaderReadaloudReaderInteraction.UserNavigation -> input.textHref
			is ReaderReadaloudReaderInteraction.ExplicitSelection -> input.textHref
		}.takeIf { it.isNotBlank() } ?: return null
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

fun ReaderReadaloudSyncState.onReaderInteraction(
	plan: ReadaloudPlaybackPlan,
	timeline: MediaOverlayTimeline?,
	interaction: ReaderReadaloudReaderInteraction
): ReaderReadaloudReaderEventStep {
	if (timeline == null) return ReaderReadaloudReaderEventStep(this)
	val adapter = MediaOverlaySyncAdapter(plan, timeline)
	val step = followReaderTarget(adapter.readerTarget(interaction))
	return ReaderReadaloudReaderEventStep(
		state = step.state,
		audioSeekTarget = step.seekTarget,
		consumedUserNavigationCausalSequence =
			(interaction as? ReaderReadaloudReaderInteraction.UserNavigation)
				?.causalSequence
				?.takeIf { step.seekTarget != null }
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
