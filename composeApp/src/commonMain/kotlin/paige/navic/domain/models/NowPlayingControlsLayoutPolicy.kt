package paige.navic.domain.models

enum class NowPlayingControlsLayoutBlock {
	Timeline,
	PlaybackButtons
}

fun nowPlayingControlsLayoutBlocks(
	swapControlsAndTimeline: Boolean
): List<NowPlayingControlsLayoutBlock> =
	if (swapControlsAndTimeline) {
		listOf(
			NowPlayingControlsLayoutBlock.PlaybackButtons,
			NowPlayingControlsLayoutBlock.Timeline
		)
	} else {
		listOf(
			NowPlayingControlsLayoutBlock.Timeline,
			NowPlayingControlsLayoutBlock.PlaybackButtons
		)
	}
