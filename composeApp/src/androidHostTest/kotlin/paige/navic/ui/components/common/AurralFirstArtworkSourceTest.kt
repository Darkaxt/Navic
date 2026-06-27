package paige.navic.ui.components.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AurralFirstArtworkSourceTest {
	@Test
	fun songArtworkSurfacesUseAurralFirstPlaybackSongCoverArt() {
		val sourceFiles = listOf(
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/components/common/SongRow.kt") to "PlaybackSongCoverArt(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/song/components/Item.kt") to "PlaybackSongCoverArt(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/queue/components/Item.kt") to "PlaybackSongCoverArt(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/collection/components/SongRow.kt") to "PlaybackSongCoverArt(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/rows/UpNextRow.kt") to "PlaybackSongCoverArt(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/components/sheets/SongSheet.kt") to "PlaybackSongCoverArt(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/search/SearchScreen.kt") to "PlaybackSongCoverArt(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/library/components/QuickPickSongCard.kt") to "rememberPlaybackArtworkUiState("
		)

		sourceFiles.forEach { (file, expectedResolver) ->
			val text = file.readText()
			assertTrue(
				text.contains(expectedResolver),
				"${file.name} should route song artwork through the shared Aurral-first artwork resolver."
			)
			assertFalse(
				text.contains("coverArtId = song.coverArtId"),
				"${file.name} must not bypass Aurral-first artwork resolution by rendering raw Navidrome song cover art."
			)
		}
	}

	@Test
	fun artistArtworkSurfacesUseAurralFirstArtistArtworkResolver() {
		val sourceFiles = listOf(
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistListScreen.kt") to "rememberArtistArtworkUiState(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/components/sheets/ArtistSheet.kt") to "AurralFirstArtistCoverArt(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/starred/components/Content.kt") to "rememberArtistArtworkUiState(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/genre/GenreDetailScreen.kt") to "rememberArtistArtworkUiState(",
			sourceFile("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/artist/ArtistDetailScreen.kt") to "rememberAurralFirstArtistArtworkUiState("
		)

		sourceFiles.forEach { (file, expectedResolver) ->
			val text = file.readText()
			assertTrue(
				text.contains(expectedResolver),
				"${file.name} should route artist artwork through the shared Aurral-first artwork resolver."
			)
			assertFalse(
				text.contains("coverArtId = artist.coverArtId"),
				"${file.name} must not bypass Aurral-first artwork resolution by rendering raw Navidrome artist cover art."
			)
			assertFalse(
				text.contains("imageUrl = artist.artistImageUrl"),
				"${file.name} must not pass stale Navidrome artist image URLs around the Aurral-first resolver."
			)
		}
	}
}

private fun sourceFile(path: String): File =
	listOf(
		File(path),
		File("../$path"),
		File(path.removePrefix("composeApp/"))
	).firstOrNull { it.isFile }
		?: error("Could not locate source file $path")
