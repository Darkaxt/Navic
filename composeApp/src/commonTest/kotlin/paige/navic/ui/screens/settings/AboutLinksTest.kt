package paige.navic.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class AboutLinksTest {
	@Test
	fun sourceUrlPointsToForkRepository() {
		assertEquals(
			"https://github.com/Darkaxt/Navic",
			ABOUT_SOURCE_URL
		)
	}
}
