package paige.navic.reader

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderWhispersyncProgressHighlightSourceTest {
	@Test
	fun readerRuntimeSupportsProgressiveWhispersyncHighlightUpdates() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		assertContains(runtime, "case 'updateOverlayFragmentProgress':")
		assertContains(runtime, "updateOverlayFragmentProgress(fragment)")
		assertContains(runtime, "textProgressEnd")
		assertContains(runtime, "paintEnd")
		assertContains(runtime, "clampedMediaOverlayProgressEnd")

		val applyOverlay = runtime
			.substringAfter("async applyOverlayFragment(fragment) {")
			.substringBefore("\n  highlightMediaOverlayTextRange")
		val paintBlock = applyOverlay.substringAfter("this.clearOverlay()")
		val textRangePath = paintBlock.indexOf("this.highlightMediaOverlayTextRange(fragment)")
		val fragmentIdPath = paintBlock.indexOf("fragment.fragmentId")
		assertTrue(
			textRangePath >= 0 && fragmentIdPath >= 0 && textRangePath < fragmentIdPath,
			"Whispersync text offsets must be attempted before fragment-id fallback so ebookStart/ebookEnd can drive progressive highlighting."
		)
	}

	@Test
	fun progressiveWhispersyncHighlightDoesNotMutateEpubDom() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		val highlighter = runtime
			.substringAfter("highlightMediaOverlayTextRange(fragment) {")
			.substringBefore("\n  clearOverlay()")
		assertContains(highlighter, "content.overlayer")
		assertContains(highlighter, "Overlayer.highlight")
		assertFalse(
			highlighter.contains("surroundContents") ||
				highlighter.contains("extractContents") ||
				highlighter.contains("insertNode"),
			"Whispersync highlighting must paint via the SVG overlayer; DOM range mutation splits EPUB block markup."
		)
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
