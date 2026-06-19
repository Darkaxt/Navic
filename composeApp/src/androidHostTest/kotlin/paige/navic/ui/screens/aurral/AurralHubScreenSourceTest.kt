package paige.navic.ui.screens.aurral

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class AurralHubScreenSourceTest {
	@Test
	fun reusableHubComponentsLiveOutsideScreenCoordinator() {
		val screen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralHubScreen.kt")
		val components = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/aurral/AurralHubComponents.kt")
		val componentsText = components.readText()

		assertTrue(
			screen.readLines().size < 1_200,
			"AurralHubScreen should coordinate state and sections instead of owning reusable row/dialog components."
		)
		assertContains(componentsText, "fun AurralCreateFlowDialog(")
		assertContains(componentsText, "internal fun AurralHubSummaryRow(")
		assertContains(componentsText, "internal fun AurralHubSectionTitle(")
		assertContains(componentsText, "internal fun AurralHubQueueRow(")
		assertContains(componentsText, "internal fun aurralFlowActionMessage(")
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull { it.isFile }
			?: error("Could not locate $path")
}
