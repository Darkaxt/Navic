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
		val paintBlock = applyOverlay.substringAfter("this.clearOverlay({ preservePlayed: true })")
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

	@Test
	fun contentEntriesPreservesFoliateOverlayerAndHrefForWhispersyncPainting() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		val contentEntries = runtime
			.substringAfter("contentEntries(detail = {}) {")
			.substringBefore("\n  contentDocuments()")

		assertContains(contentEntries, "const add = (source, doc, index)")
		assertContains(contentEntries, "...(source || {})")
		assertContains(contentEntries, "add(matchingContent || detail, detail.doc")
		assertContains(contentEntries, "add(content, content.doc, content.index)")
		assertFalse(
			contentEntries.contains("entries.push({ doc, index })"),
			"contentEntries must not strip Foliate getContents() metadata; Whispersync SVG painting needs content.overlayer."
		)
	}

	@Test
	fun progressiveWhispersyncHighlightPaintsFirstCharacterOnSegmentStart() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		val highlighter = runtime
			.substringAfter("highlightMediaOverlayTextRange(fragment) {")
			.substringBefore("\n  clearOverlay()")

		assertContains(highlighter, "const rawPaintEnd = this.clampedMediaOverlayProgressEnd(textStart, textEnd, fragment)")
		assertContains(highlighter, "const paintEnd = Math.min(textEnd, Math.max(textStart + 1, rawPaintEnd))")
	}

	@Test
	fun progressiveWhispersyncHighlightResolvesEbookTextNearOffsetAnchorBeforePainting() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()
		val helpers = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader-helpers.js").readText()

		assertContains(helpers, "export const readerMediaOverlayResolvedTextRange")
		assertContains(helpers, "export const readerMediaOverlayNormalizedTextMap")
		assertContains(helpers, "export const readerMediaOverlayRawOffsetForNormalizedOffset")
		assertContains(helpers, "ebookText")
		assertContains(helpers, "ReaderMediaOverlayTextSearchPaddingMinimum")
		assertContains(helpers, "readerMediaOverlayClosestTextMatch")
		assertContains(helpers, "normalizedTextStart")
		assertContains(helpers, "normalizedTextEnd")
		assertContains(helpers, "locator")
		assertContains(helpers, "ebook-text")

		val highlighter = runtime
			.substringAfter("highlightMediaOverlayTextRange(fragment) {")
			.substringBefore("\n  clearOverlay()")

		assertContains(highlighter, "readerMediaOverlayNormalizedTextMap(entries)")
		assertContains(highlighter, "readerMediaOverlayResolvedTextRange")
		assertContains(highlighter, "fragment.ebookText")
		assertFalse(
			highlighter.contains("readerMediaOverlayResolvedTextRange(normalizedMap, textStart, textEnd, fragment.spokenText)"),
			"Whispersync EPUB highlighting must not use ASR text as the location authority; Bindery ebookText owns the EPUB-side span."
		)
		assertContains(highlighter, "resolvedRange.normalizedTextStart")
		assertContains(highlighter, "resolvedRange.normalizedTextEnd")
		assertContains(highlighter, "resolvedTextStart")
		assertContains(highlighter, "resolvedTextEnd")
		assertContains(highlighter, "mediaOverlayPaintEndForResolvedRange")
		assertContains(highlighter, "readerMediaOverlayRawOffsetForNormalizedOffset")
	}

	@Test
	fun progressiveWhispersyncHighlightUsesVisibleEbookTextSuffixAndNextCueClamp() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()
		val helpers = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader-helpers.js").readText()

		assertContains(helpers, "readerMediaOverlayEbookTextCandidates")
		assertContains(helpers, "locator: 'ebook-text-suffix'")
		assertContains(helpers, "preferredCenter - searchStart")
		assertContains(helpers, "readerMediaOverlayClampRangeBeforeNextCue")

		val highlighter = runtime
			.substringAfter("highlightMediaOverlayTextRange(fragment) {")
			.substringBefore("\n  clearOverlay()")

		assertContains(highlighter, "fragment.nextEbookText")
		assertContains(highlighter, "fragment.nextTextStart")
		assertContains(highlighter, "fragment.nextTextEnd")
		assertContains(highlighter, "readerMediaOverlayClampRangeBeforeNextCue")
	}

	@Test
	fun progressiveWhispersyncHighlightKeepsPlayedOverlaySeparateFromActiveOverlay() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		assertContains(runtime, "ReaderMediaOverlayActiveRangeKey")
		assertContains(runtime, "ReaderMediaOverlayPlayedRangeKeyPrefix")
		assertContains(runtime, "rememberPlayedMediaOverlayFragment")
		assertContains(runtime, "prunePlayedMediaOverlayFragments")

		val highlighter = runtime
			.substringAfter("highlightMediaOverlayTextRange(fragment) {")
			.substringBefore("\n  clearOverlay()")

		assertContains(highlighter, "overlayKey")
		assertContains(highlighter, "ReaderMediaOverlayActiveRangeKey")
		assertFalse(
			highlighter.contains("overlayer.add(ReaderMediaOverlayRangeKey"),
			"Whispersync active highlighting must not reuse the played-highlight key; completed cues need separate persistent overlays."
		)
	}

	@Test
	fun progressiveWhispersyncHighlightLogsResolvedRangeDiagnostics() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		val diagnostic = runtime
			.substringAfter("postMediaOverlayRangeDiagnostic(fragment, sidecarRange, resolvedRange, paintNormalized, paintRaw) {")
			.substringBefore("\n  highlightMediaOverlayTextRange")
		val highlighter = runtime
			.substringAfter("highlightMediaOverlayTextRange(fragment) {")
			.substringBefore("\n  clearOverlay()")

		assertContains(diagnostic, "media-overlay-range:resolved")
		assertContains(diagnostic, "spokenLength")
		assertContains(diagnostic, "ebookLength")
		assertContains(diagnostic, "locator")
		assertContains(diagnostic, "matched")
		assertContains(diagnostic, "sidecarRange")
		assertContains(diagnostic, "resolvedRange")
		assertContains(diagnostic, "normalizedRange")
		assertContains(diagnostic, "JSON.stringify(diagnostic)")
		assertContains(highlighter, "this.postMediaOverlayRangeDiagnostic")
	}

	private fun sourceFile(path: String): File =
		listOf(
			File("../$path"),
			File(path)
		).firstOrNull(File::isFile)
			?: error("Unable to locate $path")
}
