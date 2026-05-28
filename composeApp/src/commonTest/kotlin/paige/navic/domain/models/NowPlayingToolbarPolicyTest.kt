package paige.navic.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import paige.navic.domain.models.settings.ToolbarPosition

class NowPlayingToolbarPolicyTest {
	@Test
	fun hiddenToolbarPositionDoesNotRenderToolbarSlots() {
		assertTrue(shouldShowSheetToolbarTop(ToolbarPosition.Top))
		assertFalse(shouldShowSheetToolbarBottom(ToolbarPosition.Top))

		assertFalse(shouldShowSheetToolbarTop(ToolbarPosition.Bottom))
		assertTrue(shouldShowSheetToolbarBottom(ToolbarPosition.Bottom))

		assertFalse(shouldShowSheetToolbarTop(ToolbarPosition.Hidden))
		assertFalse(shouldShowSheetToolbarBottom(ToolbarPosition.Hidden))
	}

	@Test
	fun hiddenToolbarPositionDoesNotReservePortraitGap() {
		assertTrue(shouldReserveNowPlayingToolbarGap(ToolbarPosition.Top, isLandscape = false))
		assertTrue(shouldReserveNowPlayingToolbarGap(ToolbarPosition.Bottom, isLandscape = false))
		assertFalse(shouldReserveNowPlayingToolbarGap(ToolbarPosition.Hidden, isLandscape = false))
		assertFalse(shouldReserveNowPlayingToolbarGap(ToolbarPosition.Top, isLandscape = true))
	}
}
