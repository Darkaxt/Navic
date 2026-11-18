package paige.navic.ui.screens.artist

import paige.navic.ui.navigation.Screen
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ArtistGenreNavigationTest {
	@Test
	fun genreLabelOpensGenreDetailRoute() {
		assertEquals(Screen.GenreDetail("Shoegaze"), artistGenreDestination("Shoegaze"))
		assertEquals(Screen.GenreDetail("Dream Pop"), artistGenreDestination("  Dream Pop "))
	}

	@Test
	fun blankGenreLabelHasNoDestination() {
		assertNull(artistGenreDestination("   "))
	}

	@Test
	fun artistHeaderGenreChipsAreClickable() {
		val source = sourceFile("ui/screens/artist/ArtistDetailScreen.kt").readText()
		val metadata = source.substringAfter("private fun AurralArtistProfileMetadata(")
			.substringBefore("private fun ArtistHeaderMetadataChip(")

		assertContains(source, "artistGenreDestination(genre)")
		assertContains(metadata, "onClick = dropUnlessResumed { onGenreClick(genre) }")
		assertFalse(
			"label = genre,\n\t\t\t\t\t\tonClick = null" in metadata,
			"Artist genre labels must not be static chips."
		)
	}

	private fun sourceFile(relativePath: String): File = listOf(
		File("src/commonMain/kotlin/paige/navic/$relativePath"),
		File("composeApp/src/commonMain/kotlin/paige/navic/$relativePath")
	).firstOrNull { it.isFile }
		?: error("Unable to locate $relativePath")
}
