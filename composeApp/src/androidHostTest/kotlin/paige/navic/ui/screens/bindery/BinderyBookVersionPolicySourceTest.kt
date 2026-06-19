package paige.navic.ui.screens.bindery

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class BinderyBookVersionPolicySourceTest {
	@Test
	fun genericFormatHelpersLiveOutsideBookVersionPolicy() {
		val policy = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicy.kt")
		val helpers =
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/versionpolicy/BinderyBookVersionFormatHelpers.kt")
		val helperText = helpers.readText()

		assertTrue(
			policy.readLines().size < 1_200,
			"BinderyBookVersionPolicy should not own generic media-format and label helpers."
		)
		assertContains(helperText, "internal fun String.fileExtension(): String?")
		assertContains(helperText, "internal fun String.fileNameStem(): String?")
		assertContains(helperText, "internal fun Map<String, String>.firstNonBlankValue(")
		assertContains(helperText, "internal fun String?.audioFormatQualityRank(): Int")
		assertContains(helperText, "internal fun String.displayToken(): String")
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull { it.isFile }
			?: error("Could not locate $path")
}
