package paige.navic.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NavigationSceneContractSourceTest {
	@Test
	fun sceneMetadataUsesTypedNavigationKeys() {
		val nowPlaying = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/NowPlayingScene.kt"
		).readText()
		val bottomSheet = sourceFile(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/navigation/BottomSheetScene.kt"
		).readText()

		assertContains(nowPlaying, "object MetadataKey : NavMetadataKey<NowPlayingBottomSheetMetadata>")
		assertContains(nowPlaying, ") = metadata {")
		assertContains(nowPlaying, "MetadataKey,")
		assertFalse(nowPlaying.contains("PROPERTIES_KEY"))
		assertFalse(nowPlaying.contains("MAX_WIDTH_KEY"))
		assertFalse(nowPlaying.contains("IS_TRANSPARENT_KEY"))
		assertContains(bottomSheet, "object MetadataKey : NavMetadataKey<ModalBottomSheetProperties>")
	}

	@Test
	fun uncheckedSceneKeyCastIsIsolatedBehindOnePinnedAdapter() {
		val navigationSources = sourceDirectory(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/navigation"
		).walkTopDown().filter { it.extension == "kt" }.toList()
		val castOwners = navigationSources.filter { it.readText().contains("contentKey as T") }

		assertEquals(listOf("NavEntrySceneKeyAdapter.kt"), castOwners.map(File::getName))
		assertContains(castOwners.single().readText(), "androidx-navigation3 = 1.1.0-beta01")
	}

	private fun sourceFile(path: String): File =
		listOf(File(path), File("../$path")).firstOrNull(File::isFile)
			?: error("Unable to locate $path")

	private fun sourceDirectory(path: String): File =
		listOf(File(path), File("../$path")).firstOrNull(File::isDirectory)
			?: error("Unable to locate $path")
}
