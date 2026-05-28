package paige.navic.domain.models

import paige.navic.domain.models.settings.NowPlayingArtworkSize
import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingArtworkSizePolicyTest {
	@Test
	fun biggestMatchesCurrentArtworkPadding() {
		assertEquals(
			16,
			nowPlayingArtworkPaddingDp(
				size = NowPlayingArtworkSize.Biggest,
				isPausedOrInactive = false,
				shrinkWhenPausedOrInactive = true
			)
		)
		assertEquals(
			48,
			nowPlayingArtworkPaddingDp(
				size = NowPlayingArtworkSize.Biggest,
				isPausedOrInactive = true,
				shrinkWhenPausedOrInactive = true
			)
		)
	}

	@Test
	fun expandedUsesLessPaddingThanBiggest() {
		assertEquals(
			0,
			nowPlayingArtworkPaddingDp(
				size = NowPlayingArtworkSize.Expanded,
				isPausedOrInactive = false,
				shrinkWhenPausedOrInactive = true
			)
		)
		assertEquals(
			32,
			nowPlayingArtworkPaddingDp(
				size = NowPlayingArtworkSize.Expanded,
				isPausedOrInactive = true,
				shrinkWhenPausedOrInactive = true
			)
		)
	}

	@Test
	fun smallerArtworkSizesIncreasePadding() {
		assertEquals(32, nowPlayingArtworkPaddingDp(NowPlayingArtworkSize.Big, false))
		assertEquals(48, nowPlayingArtworkPaddingDp(NowPlayingArtworkSize.Medium, false))
		assertEquals(72, nowPlayingArtworkPaddingDp(NowPlayingArtworkSize.Small, false))
	}

	@Test
	fun shrinkCanBeDisabledForPausedOrInactiveArtwork() {
		assertEquals(
			16,
			nowPlayingArtworkPaddingDp(
				size = NowPlayingArtworkSize.Biggest,
				isPausedOrInactive = true,
				shrinkWhenPausedOrInactive = false
			)
		)
	}
}
