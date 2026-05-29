package paige.navic.ui.components.toolbars

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.compose.ui.unit.dp

class SheetToolbarTest {
	@Test
	fun bottomNowPlayingToolbarGetsExtraBottomBreathingRoom() {
		assertEquals(
			SheetToolbarPadding(horizontal = 16.dp, top = 12.dp, bottom = 36.dp),
			sheetToolbarPadding(isLandscape = false, isBottomToolbar = true)
		)
	}

	@Test
	fun topSheetToolbarKeepsExistingPortraitPadding() {
		assertEquals(
			SheetToolbarPadding(horizontal = 16.dp, top = 24.dp, bottom = 24.dp),
			sheetToolbarPadding(isLandscape = false, isBottomToolbar = false)
		)
	}
}
