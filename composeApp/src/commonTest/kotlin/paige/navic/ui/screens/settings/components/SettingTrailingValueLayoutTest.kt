package paige.navic.ui.screens.settings.components

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingTrailingValueLayoutTest {
	@Test
	fun shortValuesReserveTrailingSlot() {
		assertEquals(96, SettingTrailingValueLayout.reservedWidthDp(intrinsicLabelWidthDp = 24))
	}

	@Test
	fun mediumValuesUseIntrinsicWidth() {
		assertEquals(120, SettingTrailingValueLayout.reservedWidthDp(intrinsicLabelWidthDp = 120))
	}

	@Test
	fun longValuesKeepExistingMaximumWidth() {
		assertEquals(160, SettingTrailingValueLayout.reservedWidthDp(intrinsicLabelWidthDp = 320))
	}
}
