package paige.navic.ui.screens.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DeveloperLogsNavigationTest {
	@Test
	fun developerOptionsShowsOneLogsDestination() {
		val developerScreen = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/settings/DeveloperScreen.kt"
		).readText()

		assertEquals(
			expected = 1,
			actual = Regex("backStack\\.add\\(Screen\\.Settings\\.Logs\\)")
				.findAll(developerScreen)
				.count(),
			message = "Developer Options must expose one Logs row."
		)
	}
}
