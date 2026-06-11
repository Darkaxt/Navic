package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeImageLinkTest {
	@Test
	fun androidReaderDoesNotForceCoverPagesToViewportInsidePaginatedIframe() {
		val bridgeText = readerBridgeText()

		assertFalse(
			bridgeText.contains("data-navic-cover-page"),
			"Cover-page viewport expansion inside Foliate's paginated iframe can trigger ResizeObserver loops and blank the first page."
		)
		assertFalse(
			bridgeText.contains("width: 100vw !important") && bridgeText.contains("height: 100vh !important"),
			"Reader content CSS must not force EPUB cover documents to viewport size inside the paginated iframe."
		)
	}

	@Test
	fun androidReaderUsesShellCoverInsteadOfNumberingTheEpubCoverPage() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "ReaderShellCoverLayerSelector")
		assertContains(bridgeText, "ensureReaderShellCoverLayer")
		assertContains(bridgeText, "loadShellCover")
		assertContains(bridgeText, "book.getCover?.()")
		assertContains(bridgeText, "firstReadableContentTarget")
		assertContains(bridgeText, "showShellCover")
		assertContains(bridgeText, "hideShellCover")
		assertContains(bridgeText, "this.shellCoverVisible")
		assertContains(
			bridgeText,
			"if (this.shellCoverVisible) {",
			message = "The organic page number layer must be suppressed while the shell cover is visible."
		)
		assertContains(
			bridgeText,
			"if (this.shellCoverVisible && direction === 'next')",
			message = "Next from the shell cover should reveal the first real content page instead of advancing twice."
		)
		assertContains(
			bridgeText,
			"if (direction === 'previous' && this.canReturnToShellCover())",
			message = "Previous from the first real content page should return to the shell cover surface."
		)
		assertFalse(
			bridgeText.contains("classifyReaderCoverDocument"),
			"Shell-cover support must not reintroduce EPUB iframe cover classification."
		)
	}

	@Test
	fun androidReaderShowsShellCoverBeforeSavedResumeLocation() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "startLocatorTargetsShellCover")
		assertContains(bridgeText, "const startLocatorIsShellCover = this.startLocatorTargetsShellCover(startLocator)")
		assertContains(
			bridgeText,
			"const shellCoverUrl = await this.loadShellCover()",
			message = "Every EPUB open should try to show the program-level cover before revealing reader content."
		)
		assertContains(
			bridgeText,
			"} else if (locator) {",
			message = "Non-cover resume locators must still load behind the shell cover."
		)
		assertContains(
			bridgeText,
			"await this.view.goTo(locator)",
			message = "The saved location should be preserved behind the initial cover overlay."
		)
		assertFalse(
			bridgeText.contains("const shouldStartAtShellCover ="),
			"Shell cover loading should not be gated by the absence of a saved locator."
		)
		assertContains(bridgeText, "detailTargetsCover")
		assertContains(bridgeText, "location-changed:cover-skipped")
	}

	@Test
	fun androidReaderShellCoverUsesFullscreenBlackSurface() {
		val bridgeText = readerBridgeText()
		val shellCoverLayer = bridgeText
			.substringAfter("const updateReaderShellCoverLayer = (layer, coverUrl, settings, title = '') => {")
			.substringBefore("\nconst updateReaderSurfaceTextureLayer")

		assertContains(shellCoverLayer, "background: '#000000'")
		assertContains(shellCoverLayer, "'background-color': '#000000'")
		assertContains(shellCoverLayer, "padding: '0px'")
		assertContains(shellCoverLayer, "width: '100%'")
		assertContains(shellCoverLayer, "height: '100%'")
		assertContains(shellCoverLayer, "'object-fit': 'contain'")
		assertContains(shellCoverLayer, "'max-height': '100%'")
		assertContains(shellCoverLayer, "'box-shadow': 'none'")
	}

	@Test
	fun androidReaderShellCoverTapsAndPreviousDoNotFallThroughToEpubCover() {
		val bridgeText = readerBridgeText()
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val canReturnToShellCover = bridgeText
			.substringAfter("canReturnToShellCover() {")
			.substringBefore("\n  applyReaderViewportLayout")

		assertContains(readerScreenText, "ReaderNativeTapOverlay(")
		assertContains(readerScreenText, "onPageTurn = { command ->")
		assertContains(readerScreenText, "dispatchReaderCommand(command)")
		assertFalse(
			runtimeText.contains("readerTargetInsideShellCover") || runtimeText.contains("isInteractiveReaderTarget"),
			"Cover image taps must be owned above the WebView, not special-cased inside content hit testing."
		)
		assertContains(
			canReturnToShellCover,
			"const firstContent = Number(this.firstReadableContentTarget())",
			message = "Previous from the first readable section should return to shell cover before Foliate can enter its EPUB cover."
		)
		assertContains(
			canReturnToShellCover,
			"Math.floor(sectionIndex) <= firstContent",
			message = "The shell-cover boundary must be section-based, not only page-index based."
		)
	}

	@Test
	fun androidReaderStylesEbookHyperlinksAsInlineFastForwardAffordances() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "a:any-link")
		assertContains(bridgeText, "classifyReaderLinks")
		assertContains(bridgeText, "data-navic-link-kind")
		assertContains(bridgeText, "color: inherit !important")
		assertContains(bridgeText, "text-decoration: none !important")
		assertFalse(
			bridgeText.contains("content: ' >>'"),
			"Hyperlinks must not expose the literal ASCII fast-forward marker."
		)
		assertContains(bridgeText, "a:any-link[data-navic-link-kind=\"text\"]::after")
		assertContains(bridgeText, "a:any-link[data-navic-link-kind=\"media\"]::after")
		assertContains(bridgeText, "content: ' »'")
		assertContains(bridgeText, "vertical-align: sub")
		assertContains(bridgeText, "font-size: 0.72em")
		assertContains(bridgeText, "closestElement")
		assertContains(bridgeText, "parentElement?.closest")
		assertContains(bridgeText, "attachLinkNavigation")
		assertContains(bridgeText, "attachContentDocumentBehaviors")
		assertContains(bridgeText, "if (detail.doc)")
		assertContains(bridgeText, "closestElement(event.target, 'a[href]')")
		assertContains(bridgeText, "readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(bridgeText, "event.stopPropagation()")
		assertContains(bridgeText, "await this.goTo(href)")
		assertContains(bridgeText, "link:navigate")
	}

	@Test
	fun androidReaderSettingsDoNotQueryPaginationBeforeEpubFrameExists() {
		val bridgeText = readerBridgeText()
		val applySettings = bridgeText
			.substringAfter("applySettings(settings) {")
			.substringBefore("\n  applyThemeToLoadedContent")

		assertFalse(
			applySettings.contains("readerPagePosition("),
			"Settings are applied during EPUB open before Foliate always has an active iframe; querying pagination there can abort publication loading."
		)
		assertContains(bridgeText, "scheduleReaderPageNumberRefresh")
		assertContains(bridgeText, "tryUpdateReaderPageNumberLayer")
	}

	@Test
	fun androidReaderInjectsThemeColorsIntoPublicationDocuments() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "const palette = readerThemePalette(settings.theme)")
		assertContains(bridgeText, "this.readerSettings = settings")
		assertContains(bridgeText, "this.applyDocumentTheme(content.doc, settings, content.index)")
		assertContains(bridgeText, "applyThemeToLoadedContent")
		assertContains(bridgeText, "applyDocumentTheme(doc, settings = this.readerSettings, index = undefined)")
		assertContains(bridgeText, "background-color: var(--reader-background) !important")
		assertContains(bridgeText, "background: var(--reader-background) !important")
		assertContains(bridgeText, "[style*=\"background\"]")
		assertContains(bridgeText, "canvas, svg")
		assertContains(bridgeText, "--theme-bg-color: \${palette.background}")
		assertContains(bridgeText, "shadowRoot.querySelectorAll('#background, #background > *')")
		assertContains(bridgeText, "--reader-background: \${palette.background}")
		assertContains(bridgeText, "--reader-foreground: \${palette.foreground}")
		assertContains(bridgeText, "--reader-accent: \${palette.accent}")
		assertContains(bridgeText, "html, body")
		assertContains(bridgeText, "background-color: var(--reader-background) !important")
		assertContains(bridgeText, "color: var(--reader-foreground) !important")
	}

	@Test
	fun androidReaderAppliesToggleableSepiaOverlayToWhiteBackedImages() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "ReaderThemeSepia")
		assertContains(bridgeText, "readerThemeKey(settings?.theme)")
		assertContains(bridgeText, "attachSepiaImageOverlayToggle")
		assertContains(bridgeText, "readerImageFromMediaTarget")
		assertContains(bridgeText, "data-navic-sepia-overlay")
		assertContains(bridgeText, "img:not([data-navic-sepia-overlay=\"off\"])")
		assertContains(bridgeText, "mix-blend-mode: multiply")
		assertContains(bridgeText, "mix-blend-mode: normal !important")
		assertContains(bridgeText, "event.stopImmediatePropagation()")
		assertContains(bridgeText, "image:sepia-overlay")
		val sepiaImageRule = bridgeText.substringAfter("img:not([data-navic-sepia-overlay=\"off\"]) {")
			.substringBefore("}")
		assertFalse(
			sepiaImageRule.contains("background-color"),
			"Sepia image overlay must not fill transparent image pixels with a second sepia background."
		)
	}

	@Test
	fun androidReaderDoesNotNavigateImageAnchorsWhenTogglingSepiaOverlay() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")

		assertContains(bridgeText, "const isReaderMediaAnchor =")
		assertContains(bridgeText, "const isReaderMediaTapTarget =")
		assertContains(linkNavigation, "const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(linkNavigation, "if (mediaTapTarget) {")
		assertContains(linkNavigation, "event.preventDefault()")
		assertContains(linkNavigation, "event.stopImmediatePropagation()")
		assertFalse(
			linkNavigation
				.substringAfter("if (mediaTapTarget) {")
				.substringBefore("return")
				.contains("await this.goTo(href)"),
			"Reader media anchor taps must never fall through to href navigation while toggling image tint."
		)
		assertTrue(
			linkNavigation.indexOf("const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)") <
				linkNavigation.indexOf("const rawHref"),
			"Reader link navigation must ignore media/image anchors before resolving hrefs."
		)
		assertContains(bridgeText, "toggleSepiaImageOverlayFromEvent(doc, event)")
		assertContains(bridgeText, "const image = readerImageFromMediaTarget(mediaTapTarget)")
	}

	@Test
	fun androidReaderLetsNativePageTurnOverlayWinOverImageTintToggles() {
		val bridgeText = readerBridgeText()
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val nativeOverlay = readerScreenText
			.substringAfter("private fun ReaderNativeTapOverlay(")
			.substringBefore("\n@Composable\nprivate fun ReaderNativeTapZoneDebugOverlay")
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")

		assertContains(readerScreenText, "ReaderNativeTapOverlay(")
		assertContains(nativeOverlay, "down.consume()")
		assertContains(nativeOverlay, "onPageTurn(command)")
		assertContains(linkNavigation, "const toggled = this.toggleSepiaImageOverlayFromEvent(doc, event)")
		assertContains(sepiaToggle, "this.toggleSepiaImageOverlayFromEvent(doc, tapEvent, state.mediaTapTarget)")
		assertFalse(
			runtimeText.contains("readerTapZoneWouldTurnPage") || runtimeText.contains("handleReaderTapZone"),
			"Image/link JavaScript must not arbitrate page-turn zones after native overlay ownership."
		)
	}

	@Test
	fun androidReaderConsumesTouchImageTogglesBeforeSyntheticLinkClicks() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")

		assertContains(bridgeText, "toggleSepiaImageOverlayFromEvent(doc, event)")
		assertContains(sepiaToggle, "touchstart")
		assertContains(sepiaToggle, "touchend")
		assertContains(bridgeText, "markReaderMediaTapHandled(doc, event, image || mediaTapTarget)")
		assertContains(bridgeText, "readerShouldSuppressMediaSyntheticClick(doc, event, anchor)")
		assertContains(bridgeText, "__navicSuppressNextMediaClickUntil")
		assertContains(bridgeText, "performance.now() <= suppressUntil")
		assertContains(sepiaToggle, "const anchor = closestElement(event.target, 'a[href]')")
		assertContains(sepiaToggle, "mediaTapTarget: readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(sepiaToggle, "state.mediaTapTarget")
		assertContains(sepiaToggle, "__navicLastMediaTapHandledAt")
		assertContains(bridgeText, "readerLastMediaTapRectContainsPoint")
		assertContains(bridgeText, "__navicLastMediaTapRect")
		assertContains(bridgeText, "markReaderMediaTapHandled(doc, event, image || mediaTapTarget)")
		assertTrue(
			linkNavigation.indexOf("readerShouldSuppressMediaSyntheticClick(doc, event, anchor)") <
				linkNavigation.indexOf("const rawHref"),
			"Reader link navigation must suppress synthetic clicks from image tint toggles before resolving hrefs."
		)
	}

	@Test
	fun androidReaderGivesMediaHitTestingPriorityOverAdjacentLinks() {
		val bridgeText = readerBridgeText()
		val mediaHitTesting = bridgeText
			.substringAfter("const readerMediaTapTargetForEvent = (doc, event, anchor) =>")
			.substringBefore("\n\n// Ported from Komikku")
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  readerTapZone")

		assertContains(mediaHitTesting, "const { clientX, clientY } = readerEventClientPoint(event)")
		assertContains(mediaHitTesting, "doc?.elementsFromPoint?.(clientX, clientY)")
		assertContains(mediaHitTesting, "getBoundingClientRect")
		assertContains(mediaHitTesting, "readerPointInsideRect")
		assertContains(mediaHitTesting, "readerMediaElementFromCandidate")
		assertContains(linkNavigation, "const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(linkNavigation, "if (mediaTapTarget) {")
		assertTrue(
			linkNavigation.indexOf("const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)") <
				linkNavigation.indexOf("const rawHref"),
			"Reader link navigation must let media/image hit testing consume the tap before href resolution."
		)
		assertContains(bridgeText, "toggleSepiaImageOverlayFromEvent(doc, event)")
		assertContains(bridgeText, "const image = readerImageFromMediaTarget(mediaTapTarget)")
	}

	@Test
	fun androidReaderDoesNotLetTextAnchorsConsumeAdjacentImageTaps() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")

		assertContains(bridgeText, "const readerPointInsideAnchorText = (anchor, event) =>")
		assertContains(bridgeText, "doc.createTreeWalker(anchor, NodeFilter.SHOW_TEXT")
		assertContains(bridgeText, "range.getClientRects()")
		assertContains(linkNavigation, "if (!readerPointInsideAnchorText(anchor, event)) {")
		assertContains(linkNavigation, "link:text-hit-miss")
		assertTrue(
			linkNavigation.indexOf("if (!readerPointInsideAnchorText(anchor, event)) {") <
				linkNavigation.indexOf("const rawHref"),
			"Text-link navigation must reject taps outside real text before resolving hrefs."
		)
		assertTrue(
			linkNavigation.indexOf("const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)") <
				linkNavigation.indexOf("if (!readerPointInsideAnchorText(anchor, event)) {"),
			"Media hit testing must still run before text-anchor hit testing."
		)
	}

	@Test
	fun androidReaderSuppressesSyntheticAnchorClicksAfterImageTintToggle() {
		val bridgeText = readerBridgeText()
		val suppression = bridgeText
			.substringAfter("const readerShouldSuppressMediaSyntheticClick = (doc, event, anchor) =>")
			.substringBefore("\n\n// Ported from Komikku")
		val immediateSuppressBlock = suppression
			.substringAfter("if (suppressUntil && performance.now() <= suppressUntil) {")
			.substringBefore("\n  }")

		assertContains(suppression, "const suppressUntil = Number(win.__navicSuppressNextMediaClickUntil || 0)")
		assertContains(suppression, "if (suppressUntil && performance.now() <= suppressUntil) {")
		assertContains(immediateSuppressBlock, "win.__navicSuppressNextMediaClickUntil = 0")
		assertContains(immediateSuppressBlock, "return true")
		assertTrue(
			immediateSuppressBlock.indexOf("win.__navicSuppressNextMediaClickUntil = 0") <
				immediateSuppressBlock.indexOf("return true"),
			"Any immediate anchor click after an image tint toggle must be consumed before adjacent-link hit testing."
		)
		assertFalse(
			immediateSuppressBlock.contains("readerLastMediaTapRectContainsPoint"),
			"Immediate synthetic-click suppression must not depend on where the adjacent link reports its hit box."
		)
	}

}
