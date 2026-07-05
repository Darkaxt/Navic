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
}
