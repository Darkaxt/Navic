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
}
