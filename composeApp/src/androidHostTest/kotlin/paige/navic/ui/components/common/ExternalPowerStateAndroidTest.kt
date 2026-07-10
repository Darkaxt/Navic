package paige.navic.ui.components.common

import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalPowerStateAndroidTest {
	@Test
	fun nullPluggedValueIsUnknown() {
		assertEquals(null, externalPowerConnectedFromPluggedValue(null))
	}

	@Test
	fun negativePluggedValueIsUnknown() {
		assertEquals(null, externalPowerConnectedFromPluggedValue(-1))
	}

	@Test
	fun zeroPluggedValueIsNotExternallyPowered() {
		assertEquals(false, externalPowerConnectedFromPluggedValue(0))
	}

	@Test
	fun positivePluggedMasksAreExternallyPowered() {
		listOf(1, 2, 4, 8).forEach { plugged ->
			assertEquals(true, externalPowerConnectedFromPluggedValue(plugged), "plugged=$plugged")
		}
	}
}
