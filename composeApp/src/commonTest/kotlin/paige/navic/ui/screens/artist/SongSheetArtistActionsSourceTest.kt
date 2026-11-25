package paige.navic.ui.screens.artist

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** #76: every song menu offers "View artist", one entry per credited artist when the arrays name several. */
class SongSheetArtistActionsSourceTest {
	@Test
	fun everySongSheetCallerWiresBothArtistActions() {
		val callers = listOf(
			"ui/components/common/SongRow.kt",
			"ui/screens/collection/components/SongRowDropdown.kt",
			"ui/screens/library/components/QuickPickSongCard.kt",
			"ui/screens/nowPlaying/components/controls/MoreButton.kt",
			"ui/screens/search/SearchScreen.kt",
			"ui/screens/song/components/Item.kt"
		)
		callers.forEach { path ->
			val call = songSheetArguments(sourceFile(path).readText())
			assertTrue("onViewArtist = " in call, "$path: the song menu must offer View artist.")
			assertTrue(
				"onViewCreditedArtist = " in call,
				"$path: the song menu must list each credited artist."
			)
		}
	}

	/** The argument list of the first `SongSheet(` call, matched by parentheses. */
	private fun songSheetArguments(source: String): String {
		val start = source.indexOf("SongSheet(") + "SongSheet(".length
		assertTrue(start >= "SongSheet(".length, "SongSheet call missing")
		var depth = 1
		var index = start
		while (depth > 0) {
			when (source[index]) {
				'(' -> depth++
				')' -> depth--
			}
			index++
		}
		return source.substring(start, index - 1)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("src/commonMain/kotlin/paige/navic/$path"),
			File("composeApp/src/commonMain/kotlin/paige/navic/$path"),
			File("../composeApp/src/commonMain/kotlin/paige/navic/$path")
		).first(File::exists)
}
