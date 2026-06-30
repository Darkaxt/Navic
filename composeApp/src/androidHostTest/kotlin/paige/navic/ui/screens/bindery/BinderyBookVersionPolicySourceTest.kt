package paige.navic.ui.screens.bindery

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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

	@Test
	fun binderyBookScreenPassesLayoutAspectToFullscreenCoverRoutes() {
		val screen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyBookScreen.kt")
			.readText()

		assertContains(
			screen,
			"BoxWithConstraints(",
			message = "Bindery reader launches must derive the target cover aspect from the actual screen surface."
		)
		assertContains(
			screen,
			"readerLaunchTargetAspectRatio",
			message = "BinderyBookScreen must keep one layout-derived aspect value for all reader launch paths."
		)
		assertEquals(
			4,
			Regex("fullscreenCoverTargetAspectRatio\\s*=\\s*readerLaunchTargetAspectRatio")
				.findAll(screen)
				.count(),
			"Every BookScreen reader launch path must pass the layout aspect into fullscreen cover variant selection."
		)
	}

	@Test
	fun binderyHubContinueReadingPassesLayoutAspectToFullscreenCoverRoutes() {
		val screen = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/bindery/BinderyHubScreen.kt")
			.readText()

		assertContains(
			screen,
			"BoxWithConstraints(",
			message = "Continue-reading launches must derive fullscreen cover variants from the hub's visible reader surface."
		)
		assertContains(
			screen,
			"readerLaunchTargetAspectRatio",
			message = "BinderyHubScreen must keep one layout-derived aspect value for continue-reading reader launches."
		)
		assertContains(
			screen,
			"binderyContinueReadingLaunchDecision(",
			message = "The continue-reading click path must rebuild the reader destination with the current surface aspect."
		)
		assertContains(
			screen,
			"binderyContinueReadingWhispersyncDestination(",
			message = "The continue-reading Whispersync sheet must use the same aspect-aware route policy as ebook-only launches."
		)
		assertEquals(
			3,
			Regex("fullscreenCoverTargetAspectRatio\\s*=\\s*readerLaunchTargetAspectRatio")
				.findAll(screen)
				.count(),
			"Every Hub continue-reading launch path must pass the layout aspect into fullscreen cover variant selection."
		)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull { it.isFile }
			?: error("Could not locate $path")
}
