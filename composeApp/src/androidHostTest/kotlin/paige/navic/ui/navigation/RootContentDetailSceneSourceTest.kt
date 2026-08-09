package paige.navic.ui.navigation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class RootContentDetailSceneSourceTest {
	@Test
	fun rootContentDetailsUseTheFullScene() {
		val source = projectFile("composeApp/src/commonMain/kotlin/paige/navic/App.kt").readText()

		assertFalse(
			source.contains("detailPane(\"root\")"),
			"Root content details must replace the previous screen instead of sharing a scene."
		)

		listOf(
			"AurralHub",
			"AurralDiscoverList",
			"AurralDiscoverCollection",
			"AurralDiscoverTag",
			"AurralArtist",
			"AurralMissingAlbum",
			"CollectionDetail",
			"SongDetail"
		).forEach { destination ->
			val entryDeclaration = source.lineSequence()
				.firstOrNull { "entry<Screen.$destination>" in it }
			assertNotNull(entryDeclaration, "Missing navigation entry for $destination")
			assertFalse(
				"metadata =" in entryDeclaration,
				"$destination must use Navigation3's fullscreen scene."
			)
		}

		assertContains(
			source,
			"entry<Screen.Settings.Root>(metadata = listPane(\"settings\"))"
		)
		assertContains(
			source,
			"entry<Screen.Settings.Appearance>(metadata = detailPane(\"settings\"))"
		)
	}

	private fun projectFile(relativePath: String): File = listOf(
		File(relativePath),
		File("../$relativePath")
	).firstOrNull(File::isFile) ?: error("Unable to locate $relativePath")
}
