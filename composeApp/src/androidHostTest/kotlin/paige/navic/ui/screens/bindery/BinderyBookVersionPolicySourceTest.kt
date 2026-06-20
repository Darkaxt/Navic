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

	@Test
	fun whispersyncMatchesSheetLaunchesReaderInsteadOfDeadSidecarAction() {
		val screen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookScreen.kt")
			.readText()
		val policy = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookVersionPolicy.kt")
			.readText()

		assertContains(
			policy,
			"fun binderyWhispersyncReaderDestinationForRowMatch(",
			message = "Whispersync match launch must use a row-aware policy that works from ebook and audiobook rows."
		)
		assertContains(
			screen,
			"binderyWhispersyncReaderDestinationForRowMatch(",
			message = "The visible Whispersync matches sheet must route to the same paired reader contract as the direct audiobook launch action."
		)
		assertTrue(
			!screen.contains("onOpenSidecar") && !screen.contains("timing layer is not wired yet"),
			"BinderyBookScreen must not keep an inert sidecar button now that paired reader launch is wired."
		)
		assertTrue(
			!screen.contains("Text(\"Sidecar\")"),
			"The match sheet action should be a reader launch action, not a raw sidecar placeholder."
		)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull { it.isFile }
			?: error("Could not locate $path")
}
