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

data class NowPlayingWideLandscapeContentLayout(
	val progressWidthDp: Int,
	val upNextWidthDp: Int
)

fun shouldUseWideNowPlayingLandscapeLayout(
	widthDp: Int,
	heightDp: Int
): Boolean = widthDp > heightDp && widthDp >= NowPlayingWideLandscapeMinimumWidthDp

fun nowPlayingWideLandscapeContentLayout(
	contentPaneWidthDp: Int
): NowPlayingWideLandscapeContentLayout {
	val progressWidth = contentPaneWidthDp
		.coerceAtMost(760)
		.coerceAtLeast(0)
	val upNextWidth = (contentPaneWidthDp - 140)
		.coerceAtMost(440)
		.coerceAtLeast(0)

	return NowPlayingWideLandscapeContentLayout(
		progressWidthDp = progressWidth,
		upNextWidthDp = upNextWidth
	)
}

private const val NowPlayingWideLandscapeMinimumWidthDp = 900

fun nowPlayingControlsLayoutBlocks(
	swapControlsAndTimeline: Boolean,
	showTechnicalInfo: Boolean = false
): List<NowPlayingControlsLayoutBlock> {
	return if (swapControlsAndTimeline) {
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
}

fun shouldOverlayTechnicalInfoBetween(
	first: NowPlayingControlsLayoutBlock,
	second: NowPlayingControlsLayoutBlock
): Boolean =
	(first == NowPlayingControlsLayoutBlock.PlaybackButtons &&
		second == NowPlayingControlsLayoutBlock.Timeline) ||
		(first == NowPlayingControlsLayoutBlock.Timeline &&
			second == NowPlayingControlsLayoutBlock.PlaybackButtons)

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
