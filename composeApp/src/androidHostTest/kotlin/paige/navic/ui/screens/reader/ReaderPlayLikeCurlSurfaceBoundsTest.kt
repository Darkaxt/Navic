package paige.navic.ui.screens.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPlayLikeCurlSurfaceBoundsTest {
	@Test
	fun rendererSurfaceKeepsTheWholeFoliateCompositionVisible() {
		val source = sourceFile(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/" +
				"ReaderPlayLikeCurlFoliateController.android.kt"
		).readText()
		val update = source
			.substringAfter("private fun updateSurfaceBounds()")
			.substringBefore("private fun PreparedPages.page(")

		assertContains(update, "params.width = ViewGroup.LayoutParams.MATCH_PARENT")
		assertContains(update, "params.height = ViewGroup.LayoutParams.MATCH_PARENT")
		assertFalse(update.contains("page.bitmap.width"))
		assertFalse(update.contains("page.bitmap.height"))
		assertFalse(source.contains("readerPlayLikeCurlPortraitSurfaceWidth("))
		assertContains(source, "setZOrderOnTop(true)")
		assertFalse(source.contains("setZOrderMediaOverlay(true)"))
		assertTrue(
			update.indexOf("params.width = ViewGroup.LayoutParams.MATCH_PARENT") <
				update.indexOf("surfaceView.requestLayout()")
		)
	}

	private fun sourceFile(relativePath: String): File {
		var directory = File(System.getProperty("user.dir")).absoluteFile
		repeat(8) {
			File(directory, relativePath).takeIf(File::isFile)?.let { return it }
			directory = directory.parentFile ?: return@repeat
		}
		error("Could not locate $relativePath from ${System.getProperty("user.dir")}")
	}
}
