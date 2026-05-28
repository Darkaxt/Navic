package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingArtworkSize

fun nowPlayingArtworkPaddingDp(
	size: NowPlayingArtworkSize,
	isPausedOrInactive: Boolean
): Int = size.activePaddingDp + if (isPausedOrInactive) 32 else 0
