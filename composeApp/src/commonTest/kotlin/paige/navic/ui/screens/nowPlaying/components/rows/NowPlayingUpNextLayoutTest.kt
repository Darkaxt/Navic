package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class NowPlayingUpNextLayoutTest {
	@Test
	fun horizontalItemsUseCompactFixedWidthWhenArtworkIsShown() {
		assertEquals(188.dp, nowPlayingUpNextItemWidth(showArtwork = true))
	}

	@Test
	fun horizontalItemsUseWiderFixedWidthWhenArtworkIsHidden() {
		assertEquals(220.dp, nowPlayingUpNextItemWidth(showArtwork = false))
	}

	@Test
	fun doesNotReserveSpaceBelowUpNextWhenTechnicalInfoIsShownAsOverlay() {
		assertEquals(0.dp, nowPlayingUpNextBottomPadding(showTechnicalInfo = true))
	}

	@Test
	fun doesNotAddExtraUpNextBottomSpaceWhenTechnicalInfoIsHidden() {
		assertEquals(0.dp, nowPlayingUpNextBottomPadding(showTechnicalInfo = false))
	}

	@Test
	fun upNextItemsUseDynamicTonalContainerColor() {
		assertEquals(NowPlayingUpNextContainerTone.SecondaryContainer, nowPlayingUpNextContainerTone())
	}

	@Test
	fun upNextItemsKeepBackgroundReadableOverDynamicArtwork() {
		assertEquals(0.86f, nowPlayingUpNextItemContainerAlpha())
	}
}
