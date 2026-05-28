package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingArtworkSize

fun nowPlayingArtworkPaddingDp(
	size: NowPlayingArtworkSize,
	isPausedOrInactive: Boolean,
	shrinkWhenPausedOrInactive: Boolean = true
): Int = size.activePaddingDp + if (isPausedOrInactive && shrinkWhenPausedOrInactive) 32 else 0
