package paige.navic.ui.screens.nowPlaying

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingWideLandscapeSourceTest {
	@Test
	fun wideLandscapeControlsMeasureTheActualRightPane() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/NowPlayingScreen.kt"
		).readText()

		assertFalse(
			"val contentPaneWidthDp = maxWidth.value.toInt() / 2" in source,
			"Wide Now Playing controls must not derive their center width from the outer player window; " +
				"the real right pane is smaller after padding and Row measurement."
		)
		assertTrue(
			"BoxWithConstraints(\n\t\t\t\t\t\t\tmodifier = Modifier.weight(1f).fillMaxHeight()," in source &&
				"contentPaneWidthDp = maxWidth.value.toInt()" in source,
			"Wide Now Playing controls must measure from the weighted right pane's own constraints " +
				"so title, buttons, progress, and Up Next share the same center."
		)
	}

	@Test
	fun wideLandscapeVinylPresentationControlsTheArtworkShape() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/Artwork.kt"
		).readText()

		assertTrue(
			"nowPlayingArtworkShapeForPlayback(\n\t\tconfiguredShape = preferenceManager.coverArtShape,\n\t\tisRotating = isVinylPresentation\n\t)" in source,
			"Wide Now Playing generated artwork must use the vinyl/disc shape whenever the media slot is " +
				"in vinyl presentation mode, even when the artwork animation is not currently rotating."
		)
	}

	@Test
	fun wideLandscapeVerticalUpNextUsesStableKeys() {
		val source = File(
			"src/commonMain/kotlin/paige/navic/ui/screens/nowPlaying/components/rows/UpNextRow.kt"
		).readText()

		assertTrue(
			"key(song.id)" in source,
			"Wide Now Playing vertical Up Next rows must keep stable song keys so artwork/video state " +
				"does not migrate between rows during queue updates."
		)
	}
}
