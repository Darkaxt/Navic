package paige.navic.domain.models

import kotlin.math.abs
import kotlin.math.roundToInt

enum class NowPlayingControlsLayoutBlock {
	Timeline,
	TechnicalInfo,
	PlaybackButtons
}

enum class NowPlayingPlaybackButtonsArrangement {
	Compact,
	EvenlySpaced
}

fun nowPlayingControlsLayoutBlocks(
	swapControlsAndTimeline: Boolean,
	showTechnicalInfo: Boolean = false
): List<NowPlayingControlsLayoutBlock> {
	val primaryBlocks = if (swapControlsAndTimeline) {
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
	if (!showTechnicalInfo) return primaryBlocks
	return listOf(
		primaryBlocks.first(),
		NowPlayingControlsLayoutBlock.TechnicalInfo,
		primaryBlocks.last()
	)
}

fun nowPlayingPlaybackButtonsArrangement(
	spaceControlsEvenly: Boolean
): NowPlayingPlaybackButtonsArrangement =
	if (spaceControlsEvenly) {
		NowPlayingPlaybackButtonsArrangement.EvenlySpaced
	} else {
		NowPlayingPlaybackButtonsArrangement.Compact
	}

fun nowPlayingPlayButtonSpeedLabel(playbackSpeed: Float): String? {
	if (!playbackSpeed.isFinite()) return null

	val tenths = (playbackSpeed * 10).roundToInt()
	if (tenths == 10) return null

	val sign = if (tenths < 0) "-" else ""
	val absoluteTenths = abs(tenths)
	return "$sign${absoluteTenths / 10}.${absoluteTenths % 10}x"
}

fun shouldOpenQueueFromNowPlayingControlsTap(
	enabled: Boolean,
	hasCurrentSong: Boolean
): Boolean =
	enabled && hasCurrentSong

fun shouldOpenPlaybackSpeedFromNowPlayingPlayButton(
	hasCurrentSong: Boolean
): Boolean =
	hasCurrentSong
