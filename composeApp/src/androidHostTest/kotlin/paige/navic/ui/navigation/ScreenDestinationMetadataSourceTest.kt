package paige.navic.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ScreenDestinationMetadataSourceTest {
	@Test
	fun destinationPoliciesConsumeOneExhaustiveMetadataTable() {
		val metadata = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/ScreenDestinationMetadata.kt"
		).readText()
		val backPolicy = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/NavBackPolicy.kt"
		).readText()
		val profilePolicy = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/BottomBarProfilePolicy.kt"
		).readText()

		assertContains(metadata, "fun Screen.destinationMetadata(): ScreenDestinationMetadata =")
		assertContains(metadata, "when (this)")
		assertFalse(metadata.contains("else ->"), "Screen metadata must remain compiler-exhaustive")
		assertContains(backPolicy, "screen.destinationMetadata()")
		assertFalse(backPolicy.contains("when (screen)"))
		assertContains(profilePolicy, "screen?.destinationMetadata()")
		assertFalse(profilePolicy.contains("when (screen)"))
	}

	private fun sourceFile(path: String): File =
		listOf(File(path), File("../$path")).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
