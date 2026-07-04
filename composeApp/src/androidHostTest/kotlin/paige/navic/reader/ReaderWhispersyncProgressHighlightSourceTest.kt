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

		val paintBlock = runtime
			.substringAfter("paintActiveMediaOverlayFragment(fragment) {")
			.substringBefore("\n  startMediaOverlayProgressAnimation")
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
		assertContains(highlighter, "const highlightDraw = this.readerMediaOverlayHighlightDraw()")
		assertContains(highlighter, "overlayer.add(overlayKey, range, highlightDraw")
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
	fun progressiveWhispersyncHighlightUsesAudioProgressFractionForResolvedRangePacing() {
		val protocol = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt").readText()
		val model = sourceFile("composeApp/src/commonMain/kotlin/paige/navic/reader/WhispersyncModels.kt").readText()
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		assertContains(protocol, "textProgressFraction")
		assertContains(model, "textProgressFraction")

		val painter = runtime
			.substringAfter("mediaOverlayPaintEndForResolvedRange(")
			.substringBefore("\n  postMediaOverlayRangeDiagnostic")
		val progress = runtime
			.substringAfter("mediaOverlayProgressFraction(")
			.substringBefore("\n  paintActiveMediaOverlayFragment")

		assertContains(painter, "fragment")
		assertContains(painter, "this.mediaOverlayProgressFraction")
		assertContains(progress, "textProgressFraction")
		assertContains(painter, "resolvedNormalizedTextEnd - resolvedNormalizedTextStart")
		assertFalse(
			painter.contains("(paintEnd - textStart) / characterCount"),
			"Resolved EPUB highlight fill must be paced by audio progress, not by stale sidecar offset length."
		)
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
	fun progressiveWhispersyncHighlightAnchorsOffsetFallbackBeforeNextResolvedCue() {
		val helpers = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader-helpers.js").readText()

		val clamp = helpers
			.substringAfter("export const readerMediaOverlayClampRangeBeforeNextCue =")
			.substringBefore("\n}\n\nexport const readerMediaOverlayTextOffsetForRange")

		assertContains(clamp, "range?.locator === 'offset'")
		assertContains(clamp, "nextStart <= currentStart")
		assertContains(clamp, "next-anchor-gap")
		assertContains(clamp, "readerMediaOverlayRawOffsetForNormalizedOffset(map, anchoredStart, 'start')")
		assertContains(clamp, "readerMediaOverlayRawOffsetForNormalizedOffset(map, nextStart, 'start')")
	}

	@Test
	fun progressiveWhispersyncHighlightInterpolatesBetweenNativePositionTicks() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		assertContains(runtime, "requestAnimationFrame")
		assertContains(runtime, "cancelAnimationFrame")
		assertContains(runtime, "startMediaOverlayProgressAnimation")
		assertContains(runtime, "stopMediaOverlayProgressAnimation")
		assertContains(runtime, "mediaOverlayProgressAnimationFrame")
		assertContains(runtime, "mediaOverlayProgressDisplayKey")
		assertContains(runtime, "smallBackwardJitter")
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
	fun progressiveWhispersyncHighlightConsumesListeningModeOverlaySettings() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		assertContains(runtime, "readerMediaOverlayHighlightColor(settings = this.readerSettings)")
		assertContains(runtime, "readerMediaOverlayHighlightDraw(settings = this.readerSettings)")
		assertContains(runtime, "readerMediaOverlayPersistentPlayed(settings = this.readerSettings)")
		assertContains(runtime, "readerDrawMediaOverlayMarker")

		val activePainter = runtime
			.substringAfter("paintActiveMediaOverlayFragment(fragment) {")
			.substringBefore("\n  startMediaOverlayProgressAnimation")
		val highlighter = runtime
			.substringAfter("highlightMediaOverlayTextRange(fragment) {")
			.substringBefore("\n  clearOverlay()")

		assertContains(activePainter, "const preservePlayed = this.readerMediaOverlayPersistentPlayed()")
		assertContains(activePainter, "this.clearOverlay({ preservePlayed, preserveAnimation: true })")
		assertContains(activePainter, "if (preservePlayed) this.paintPlayedMediaOverlayFragments()")
		assertContains(highlighter, "const highlightColor = this.readerMediaOverlayHighlightColor()")
		assertContains(highlighter, "const highlightDraw = this.readerMediaOverlayHighlightDraw()")
		assertContains(highlighter, "overlayer.add(overlayKey, range, highlightDraw")
		assertFalse(
			highlighter.contains("color: 'var(--reader-accent)'"),
			"Whispersync highlight color must come from Listening mode settings, not the reader accent token."
		)
	}

	@Test
	fun whispersyncOverlayActivationDoesNotNavigateReaderAcrossPages() {
		val runtime = sourceFile("composeApp/src/androidMain/assets/reader/navic-reader.js").readText()

		val applyOverlay = runtime
			.substringAfter("async applyOverlayFragment(fragment) {")
			.substringBefore("\n  updateOverlayFragmentProgress")

		assertFalse(
			applyOverlay.contains("this.goTo(targetHref, 'media-overlay-follow')"),
			"Playback-driven Whispersync overlay updates must not relocate the reader; page-end handling is owned by native."
		)
		assertContains(applyOverlay, "media-overlay-follow:outside-visible-page")
		assertContains(applyOverlay, "overlayFragmentInactive")
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
