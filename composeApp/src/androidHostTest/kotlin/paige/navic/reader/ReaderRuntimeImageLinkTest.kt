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
	fun androidReaderShowsShellCoverBeforeSavedResumeLocationWithoutLoadingEpubCoverBehindIt() {
		val bridgeText = readerBridgeText()
		val openPublication = bridgeText
			.substringAfter("async openPublication({ url, mediaOverlayEnabled = false, externalShellCover = false, startLocator = null, settings = null }) {")
			.substringBefore("\n  close()")

		assertContains(bridgeText, "startLocatorTargetsShellCover")
		assertContains(
			bridgeText,
			"const shellCoverUrl = this.externalShellCover ? null : await this.loadShellCover()",
			message = "Every EPUB open should try to show the program-level cover before revealing reader content."
		)
		assertContains(
			openPublication,
			"const hasShellCoverSurface = this.externalShellCover || Boolean(shellCoverUrl)"
		)
		assertContains(
			openPublication,
			"const shouldStartAtShellCover = hasShellCoverSurface && this.startLocatorTargetsShellCover(startLocator)",
			message = "A start locator that points at the EPUB cover must be treated as the shell cover, so Foliate never renders the EPUB cover behind the native cover surface."
		)
		assertContains(
			openPublication,
			"if (shouldStartAtShellCover) {"
		)
		assertTrue(
			openPublication.indexOf("const hasShellCoverSurface = this.externalShellCover || Boolean(shellCoverUrl)") <
				openPublication.indexOf("const shouldStartAtShellCover = hasShellCoverSurface && this.startLocatorTargetsShellCover(startLocator)"),
			"The cover-start decision must know whether a native or JS shell cover exists before suppressing EPUB cover navigation."
		)
		assertTrue(
			openPublication.indexOf("if (shouldStartAtShellCover)") <
				openPublication.indexOf("} else if (locator) {"),
			"Cover-targeting resume locators must be bypassed before generic locator navigation."
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
		assertContains(
			openPublication,
			"readerStartLocatorHasPosition(startLocator) && !shouldStartAtShellCover",
			message = "Initial resume snapshots must not be posted for EPUB cover locators that were converted into the native shell-cover page."
		)
		assertContains(bridgeText, "detailTargetsCover")
		assertContains(bridgeText, "location-changed:cover-skipped")
	}

	@Test
	fun androidReaderSkipsWebShellCoverWhenNativeCoverSurfaceIsAvailable() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "externalShellCover = false")
		assertContains(bridgeText, "this.externalShellCover = Boolean(externalShellCover)")
		assertContains(
			bridgeText,
			"this.close()\n      this.externalShellCover = Boolean(externalShellCover)",
			message = "The external shell-cover flag must be applied after close() resets runtime state."
		)
		assertContains(
			bridgeText,
			"const shellCoverUrl = this.externalShellCover ? null : await this.loadShellCover()",
			message = "Native cover mode must not create a second WebView shell-cover layer underneath the Compose cover surface."
		)
		assertContains(bridgeText, "const hasShellCoverSurface = this.externalShellCover || Boolean(shellCoverUrl)")
		assertContains(bridgeText, "} else if (hasShellCoverSurface) {")
		assertContains(
			bridgeText,
			"if (shellCoverUrl) this.showShellCover()",
			message = "Only the JS fallback cover layer should be shown by the WebView runtime."
		)
	}

	@Test
	fun commonReaderUsesNativeShellCoverSurfaceWhenResolverProvidesCoverUrl() {
		val readerScreenText = readerScreenFile().readText()
		val runtimeHostText = readerAndroidFile("ReaderPublicationRuntimeHost.android.kt").readText()
		val controllerText = readerCommonFile("ReaderController.kt").readText()
		val engineText = readerCommonFile("ReaderEngine.kt").readText()
		val foliateAdapterText = readerCommonFile("FoliateEpubEngineAdapter.kt").readText()
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()

		assertContains(
			runtimeHostText,
			"resolved.shellCoverUrl",
			message = "The Android resolver's EPUB cover URL must be surfaced to common reader UI."
		)
		assertContains(readerScreenText, "shellCoverUrl: String?")
		assertContains(readerScreenText, "val hasShellCover = !shellCoverUrl.isNullOrBlank()")
		assertContains(readerScreenText, "nativeShellCoverUrl = shellCoverUrl")
		assertContains(readerScreenText, "canReturnToShellCover = hasShellCover")
		assertContains(readerScreenText, "KomikkuReaderNativeFrameHost(")
		assertContains(readerScreenText, "shellCoverVisible = controllerState.shellCoverVisible")
		assertContains(readerScreenText, "shellCoverUrl = shellCoverUrl")
		assertContains(engineText, "nativeShellCoverUrl: String?")
		assertContains(engineText, "canReturnToShellCover: Boolean")
		assertContains(foliateAdapterText, "nativeShellCoverUrl = request.nativeShellCoverUrl")
		assertContains(foliateAdapterText, "canReturnToShellCover = request.canReturnToShellCover")
		assertContains(controllerText, "nativeShellCoverUrl = normalizedRequest.nativeShellCoverUrl")
		assertContains(controllerText, "shellCoverVisible = !normalizedRequest.nativeShellCoverUrl.isNullOrBlank()")
		assertContains(nativeFrameHostText, "KomikkuReaderNativeShellCoverView")
		assertContains(nativeFrameHostText, "setShellCover(shellCoverVisible, shellCoverUrl, shellCoverTitle)")
		assertContains(webViewHostText, "externalShellCover: Boolean")
		assertFalse(
			webViewHostText.contains("nativeShellCoverUrl: String?") ||
				webViewHostText.contains("ReaderShellCoverView") ||
				webViewHostText.contains("shellCoverView"),
			"The engine WebView host is renderer-only; native shell-cover state belongs to the controller/native frame."
		)
	}

	@Test
	fun commonReaderKeepsNativeShellCoverOutOfSecondaryRendererPreparation() {
		val readerScreenText = readerScreenFile().readText()
		val controllerText = readerCommonFile("ReaderController.kt").readText()
		val coordinatorText = readerCommonFile("ReaderCoordinator.kt").readText()

		assertContains(
			readerScreenText,
			"onPublicationReady = { publicationUrl, shellCoverUrl, savedProgress ->",
			message = "Publication preparation should pass the resolved shell-cover URL into the controller open request once."
		)
		assertContains(readerScreenText, "toReaderEngineOpenRequest(")
		assertContains(coordinatorText, "fun open(request: ReaderEngineOpenRequest): ReaderCoordinatorStep")
		assertContains(controllerText, "state = state.copy(")
		assertContains(controllerText, "nativeShellCoverUrl = normalizedRequest.nativeShellCoverUrl")
		assertContains(controllerText, "canReturnToShellCover = normalizedRequest.canReturnToShellCover")
		assertFalse(
			readerScreenText.contains("handlePublicationPrepared(publicationUrl, null)"),
			"Secondary runtime preparation must not clear the controller-owned native cover by passing a literal null cover."
		)
	}

	@Test
	fun androidPublicationRuntimeLogsNativeShellCoverResolution() {
		val runtimeHostText = readerAndroidFile("ReaderPublicationRuntimeHost.android.kt").readText()

		assertContains(
			runtimeHostText,
			"""shellCover=${'$'}{if (resolved.shellCoverUrl.isNullOrBlank()) "missing" else "present"}""",
			message = "ADB logs must expose whether EPUB native shell-cover extraction succeeded before the WebView opens."
		)
		assertContains(
			runtimeHostText,
			"shellCover=unavailable",
			message = "Direct/local publication opens must log that no resolver cover extraction ran."
		)
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
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val controllerText = readerCommonFile("ReaderController.kt").readText()
		val chromeStateText = readerCommonFile("ReaderChromeState.kt").readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()

		assertContains(readerScreenText, "nativeShellCoverUrl = shellCoverUrl")
		assertContains(nativeFrameHostText, "KomikkuReaderNativeShellCoverView")
		assertContains(nativeFrameHostText, "canvas.drawColor(Color.BLACK)")
		assertContains(nativeFrameHostText, "canvas.drawBitmap")
		assertFalse(
			webViewHostText.contains("ReaderSurfaceHost") ||
				webViewHostText.contains("shellCoverWebView") ||
				webViewHostText.contains("ReaderShellCoverView"),
			"Cover image taps must be owned by the native Komikku frame, not by the renderer WebView host."
		)
		assertFalse(
			webViewHostText.contains("readerShellCoverHtml"),
			"Shell cover should not be an HTML/CSS wrapper now that cover is a reader-owned surface."
		)
		assertContains(nativeFrameHostText, "KomikkuReaderNativeViewerContainer")
		assertContains(nativeFrameHostText, "onAction(KomikkuNavigationRegion.NEXT)")
		assertContains(nativeFrameHostText, "onAction(KomikkuNavigationRegion.PREV)")
		assertContains(controllerText, "private fun onShellCoverViewerAction(action: ReaderViewerAction)")
		assertContains(controllerText, "shellCoverVisible = false")
		assertContains(controllerText, "readerShouldReturnToNativeShellCover(")
		assertFalse(
			readerScreenText.contains("ReaderNativeTapOverlay("),
			"Reader taps must be owned by the Android reader surface host, not a Compose overlay that can sit behind the WebView surface."
		)
		assertFalse(
			runtimeText.contains("readerTargetInsideShellCover") || runtimeText.contains("isInteractiveReaderTarget"),
			"Cover image taps must be owned above the WebView, not special-cased inside content hit testing."
		)
		assertContains(
			chromeStateText,
			"fun readerShouldReturnToNativeShellCover(",
			message = "Previous from the first readable page should be decided by common controller state before Foliate can enter its EPUB cover."
		)
		assertContains(
			chromeStateText,
			"(locator?.pageIndex ?: -1) <= 0",
			message = "The shell-cover boundary must remain controller-owned and based on committed reader location."
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
	fun androidReaderKeepsMediaInteractionBelowNativeShortTapOwner() {
		val bridgeText = readerBridgeText()
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val viewerContainerBody = nativeFrameHostText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuReaderNativeNavigationOverlayView")
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  readerContentActionInDocumentAtPoint")
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")

		assertFalse(
			readerScreenText.contains("ReaderNativeTapOverlay("),
			"Readable media taps must not depend on a Compose overlay consuming the WebView gesture stream."
		)
		assertContains(nativeFrameHostText, "KomikkuReaderNativeViewerContainer")
		assertContains(viewerContainerBody, "override fun onInterceptTouchEvent(event: MotionEvent): Boolean")
		assertContains(viewerContainerBody, "nativeShortTapIntercepted = nativeTapCandidate && !nativeTapLongConfirmed")
		assertContains(viewerContainerBody, "override fun onLongTapConfirmed(event: MotionEvent)")
		assertContains(viewerContainerBody, "nativeTapLongConfirmed = true")
		assertContains(viewerContainerBody, "onAction(action)")
		assertFalse(
			webViewHostText.contains("ReaderSurfaceHost") ||
				webViewHostText.contains("markContentTapHandled()") ||
				webViewHostText.contains("scheduleReaderCenterTap("),
			"The engine WebView host must not keep the legacy delayed center-tap suppression layer after Komikku native input ownership."
		)
		assertContains(claimInteractiveTouch, "if (this.nativeTapZones === true) return false")
		assertTrue(
			claimInteractiveTouch.indexOf("if (this.nativeTapZones === true) return false") <
				claimInteractiveTouch.indexOf("post(this.readerContentActionClaimPayload"),
			"Short link/image touches must not post ownership claims while the native Komikku surface owns tap zones."
		)
		assertContains(linkNavigation, "const toggled = this.toggleSepiaImageOverlayFromEvent(doc, event)")
		assertContains(sepiaToggle, "this.toggleSepiaImageOverlayFromEvent(doc, tapEvent, state.mediaTapTarget)")
	}

	@Test
	fun readerNormalLinksReportContentActionClaimsBeforeNavigation() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val normalLinkNavigation = linkNavigation
			.substringAfter("const rawHref = anchor.getAttribute('href')")
			.substringBefore("await this.goTo(href)")

		assertContains(normalLinkNavigation, "this.rememberReaderContentActionTouch(doc, event, {")
		assertContains(normalLinkNavigation, "post(this.readerContentActionClaimPayload(doc, event, {")
		assertContains(normalLinkNavigation, "source: 'link'")
		assertTrue(
			normalLinkNavigation.indexOf("post(this.readerContentActionClaimPayload(doc, event, {") <
				normalLinkNavigation.indexOf("event.preventDefault()"),
			"Normal EPUB links must report content action metadata before navigation mutates the document."
		)
	}

	@Test
	fun androidEngineWebViewHostDoesNotOwnReaderWideTapArbitration() {
		val webViewHostText = readerEngineWebViewHostFile().readText()

		assertFalse(
			webViewHostText.contains("ReaderSurfaceHost") ||
				webViewHostText.contains("ReaderCenterTapDelayMs") ||
				webViewHostText.contains("pendingCenterTap") ||
				webViewHostText.contains("scheduleReaderCenterTap(") ||
				webViewHostText.contains("markContentTapHandled()") ||
				webViewHostText.contains("dispatchReaderWideTap"),
			"The renderer WebView host must not keep the old delayed center-tap arbitration layer after native Komikku input ownership."
		)
	}

	@Test
	fun androidReaderRoutesContentClaimsAsEngineMetadataNotNativeTapCancellation() {
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val foliateAdapterText = readerCommonFile("FoliateEpubEngineAdapter.kt").readText()
		val controllerText = readerCommonFile("ReaderController.kt").readText()

		assertContains(webViewHostText, "ReaderEngineHostEvent.FoliateBridge(event)")
		assertContains(webViewHostText, "webView?.post { handleReaderBridgeEvent(event) }")
		assertContains(
			foliateAdapterText,
			"is ReaderBridgeEvent.ContentTapHandled -> ReaderEngineEvent.ContentActionClaimed(event.claim)"
		)
		assertContains(controllerText, "is ReaderEngineEvent.ContentActionClaimed ->")
		assertContains(controllerText, "lastContentActionClaim = event.claim")
		assertFalse(
			webViewHostText.contains("ContentTapHandled") &&
				webViewHostText.contains("markContentTapHandled"),
			"Content claims are controller metadata now; the WebView host must not use them to cancel native tap dispatch."
		)
	}

	@Test
	fun androidReaderIgnoresInteractiveTouchClaimsWhenNativeTapZonesOwnShortTaps() {
		val bridgeText = readerBridgeText()
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  readerContentActionInDocumentAtPoint")
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")

		assertContains(bridgeText, "claimReaderInteractiveContentTouch(doc, event)")
		assertContains(claimInteractiveTouch, "if (this.nativeTapZones === true) return false")
		assertContains(linkNavigation, "doc.addEventListener('touchstart', event => {")
		assertContains(linkNavigation, "this.claimReaderInteractiveContentTouch(doc, event)")
		assertContains(linkNavigation, "doc.addEventListener('touchend', event => {")
		assertContains(claimInteractiveTouch, "source: 'media-touch'")
		assertContains(claimInteractiveTouch, "source: 'link-touch'")
		assertTrue(
			claimInteractiveTouch.indexOf("if (this.nativeTapZones === true) return false") <
				claimInteractiveTouch.indexOf("post(this.readerContentActionClaimPayload"),
			"Native Komikku tap zones must win before JS posts touch-phase content claims."
		)
	}

	@Test
	fun androidReaderSuppressesOrdinaryContentClicksWhenNativeTapZonesOwnShortTaps() {
		val bridgeText = readerBridgeText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")
		val linkClickHandler = linkNavigation
			.substringAfter("doc.addEventListener('click', async event => {")
			.substringBefore("}, { capture: true })")
		val imageClickHandler = sepiaToggle
			.substringAfter("doc.addEventListener('click', event => {")
			.substringBefore("}, { capture: true, passive: false })")
		val imageTouchEndHandler = sepiaToggle
			.substringAfter("doc.addEventListener('touchend', event => {")
			.substringBefore("}, { capture: true, passive: false })")

		assertContains(
			bridgeText,
			"suppressReaderNativeTapZoneContentActivation",
			message = "The runtime needs a single guard for leaked ordinary WebView clicks while Android owns short taps."
		)
		assertContains(linkClickHandler, "this.suppressReaderNativeTapZoneContentActivation(doc, event, 'link-click')")
		assertTrue(
			linkClickHandler.indexOf("this.suppressReaderNativeTapZoneContentActivation(doc, event, 'link-click')") <
				linkClickHandler.indexOf("const anchor = closestElement(event.target, 'a[href]')"),
			"Native short-tap ownership must suppress link activation before resolving or navigating anchors."
		)
		assertContains(imageClickHandler, "this.suppressReaderNativeTapZoneContentActivation(doc, event, 'image-click')")
		assertContains(imageClickHandler, "const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)")
		assertContains(imageClickHandler, "mediaTapTarget && this.suppressReaderNativeTapZoneContentActivation(doc, event, 'image-click')")
		assertTrue(
			imageClickHandler.indexOf("const mediaTapTarget = readerMediaTapTargetForEvent(doc, event, anchor)") <
				imageClickHandler.indexOf("this.suppressReaderNativeTapZoneContentActivation(doc, event, 'image-click')") &&
				imageClickHandler.indexOf("mediaTapTarget && this.suppressReaderNativeTapZoneContentActivation(doc, event, 'image-click')") <
				imageClickHandler.indexOf("this.toggleSepiaImageOverlayFromEvent(doc, event, mediaTapTarget)"),
			"Native short-tap ownership must suppress image click activation before toggling the sepia overlay without swallowing plain text links."
		)
		assertContains(imageTouchEndHandler, "this.suppressReaderNativeTapZoneContentActivation(doc, tapEvent, 'image-touchend')")
		assertContains(imageTouchEndHandler, "state.mediaTapTarget && this.suppressReaderNativeTapZoneContentActivation(doc, tapEvent, 'image-touchend')")
		assertTrue(
			imageTouchEndHandler.indexOf("state.mediaTapTarget && this.suppressReaderNativeTapZoneContentActivation(doc, tapEvent, 'image-touchend')") <
				imageTouchEndHandler.indexOf("this.toggleSepiaImageOverlayFromEvent(doc, tapEvent, state.mediaTapTarget)"),
			"Native short-tap ownership must suppress image touchend activation before toggling the sepia overlay without swallowing text-link touch metadata."
		)
	}

	@Test
	fun androidReaderClaimsAnchorTouchBeforeTextRectNavigationHitTesting() {
		val bridgeText = readerBridgeText()
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  readerContentActionInDocumentAtPoint")

		assertContains(claimInteractiveTouch, "if (this.nativeTapZones === true) return false")
		assertContains(
			claimInteractiveTouch,
			"if (anchor) {",
			message = "Fallback/non-native touch ownership still records actual anchor targets."
		)
		assertFalse(
			claimInteractiveTouch.contains("if (anchor && readerPointInsideAnchorText(anchor, event))"),
			"Text-rect hit testing can gate link navigation, but must not gate content metadata collection."
		)
		assertContains(
			claimInteractiveTouch,
			"textHit: readerPointInsideAnchorText(anchor, event)",
			message = "Anchor diagnostics should still record whether the touch was inside rendered link text."
		)
	}

	@Test
	fun androidReaderKeepsPointerAndMouseContentClaimsBehindNativeGuard() {
		val bridgeText = readerBridgeText()
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  readerContentActionInDocumentAtPoint")
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")

		assertContains(claimInteractiveTouch, "if (this.nativeTapZones === true) return false")
		assertContains(
			linkNavigation,
			"doc.addEventListener('pointerdown', event => {",
			message = "Pointer events remain instrumented for metadata/non-native fallback paths."
		)
		assertContains(
			linkNavigation,
			"doc.addEventListener('mousedown', event => {",
			message = "Mouse-style WebView paths remain instrumented for metadata/non-native fallback paths."
		)
		assertTrue(
			linkNavigation.indexOf("doc.addEventListener('pointerdown'") <
				linkNavigation.indexOf("doc.addEventListener('click'"),
			"Pointer ownership must run before final click navigation/toggle work."
		)
		assertTrue(
			linkNavigation.indexOf("doc.addEventListener('mousedown'") <
				linkNavigation.indexOf("doc.addEventListener('click'"),
			"Mouse ownership must run before final click navigation/toggle work."
		)
	}

	@Test
	fun androidReaderKeepsSepiaImageTouchMetadataBehindNativeGuard() {
		val bridgeText = readerBridgeText()
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  readerContentActionInDocumentAtPoint")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  effectiveReaderDirection")
		val sepiaTouchStart = sepiaToggle
			.substringAfter("doc.addEventListener('touchstart', event => {")
			.substringBefore("}, { capture: true, passive: true })")

		assertContains(claimInteractiveTouch, "if (this.nativeTapZones === true) return false")
		assertContains(
			sepiaTouchStart,
			"this.claimReaderInteractiveContentTouch(doc, event)",
			message = "Image gestures should still record fallback content metadata before sepia toggle bookkeeping."
		)
		assertTrue(
			sepiaTouchStart.indexOf("this.claimReaderInteractiveContentTouch(doc, event)") <
				sepiaTouchStart.indexOf("touchState = {"),
			"The image touch metadata hook should happen before gesture bookkeeping so non-native fallback paths remain deterministic."
		)
	}

	@Test
	fun readerHarnessCssSmokeRequiresContentActionBridgeOwnership() {
		val harnessText = listOf(
			File("tools/reader-harness/src/run-reader-harness.mjs"),
			File("../tools/reader-harness/src/run-reader-harness.mjs")
		).first { it.exists() }.readText()
		val assertionsText = listOf(
			File("tools/reader-harness/src/reader-trace-assertions.mjs"),
			File("../tools/reader-harness/src/reader-trace-assertions.mjs")
		).first { it.exists() }.readText()

		assertContains(harnessText, "__navicReaderPostedMessages")
		assertContains(
			harnessText,
			"nativeTapZones: true",
			message = "CSS smoke must explicitly exercise the Android-native tap ownership mode, not only the old WebView-owned fallback."
		)
		assertContains(
			harnessText,
			"imageContentTapHandledCount",
			message = "CSS smoke must record native bridge ownership for image tint toggles, otherwise Android chrome races are invisible locally."
		)
		assertContains(
			harnessText,
			"textLinkContentTapHandledCount",
			message = "CSS smoke must record native bridge ownership for text link navigation, otherwise Android chrome races are invisible locally."
		)
		assertContains(
			harnessText,
			"imageTouchContentTapHandledCount",
			message = "CSS smoke must record touch-phase bridge ownership for image taps, because Android chrome races the touch sequence."
		)
		assertContains(
			harnessText,
			"textLinkTouchContentTapHandledCount",
			message = "CSS smoke must record touch-phase bridge ownership for link taps, because Android chrome races the touch sequence."
		)
		assertContains(
			harnessText,
			"nativeTapZonesSuppressedImageClickCount",
			message = "CSS smoke must prove ordinary image clicks are suppressed when the native reader surface owns short taps."
		)
		assertContains(
			harnessText,
			"nativeTapZonesSuppressedTextLinkClickCount",
			message = "CSS smoke must prove ordinary text-link clicks are suppressed by the link handler when the native reader surface owns short taps."
		)
		assertContains(
			harnessText,
			"nativeTapZonesImageOverlayTraceCount",
			message = "CSS smoke must fail if an ordinary native-mode image click still toggles the sepia overlay."
		)
		assertContains(
			harnessText,
			"nativeTapZonesTextLinkNavigationTraceCount",
			message = "CSS smoke must fail if an ordinary native-mode text link click still navigates inside the WebView."
		)
		assertContains(
			assertionsText,
			"Expected image tint toggle to send readerContentTapHandled",
			message = "The renderer assertion must fail when image actions do not notify native content ownership."
		)
		assertContains(
			assertionsText,
			"Expected styled text link click to send readerContentTapHandled",
			message = "The renderer assertion must fail when text links do not notify native content ownership."
		)
		assertContains(
			assertionsText,
			"Expected image touch to send readerContentTapHandled",
			message = "The renderer assertion must fail when image touch events do not notify native content ownership before Android chrome."
		)
		assertContains(
			assertionsText,
			"Expected styled text link touch to send readerContentTapHandled",
			message = "The renderer assertion must fail when link touch events do not notify native content ownership before Android chrome."
		)
		assertContains(
			assertionsText,
			"Expected native tap zones to suppress ordinary image clicks",
			message = "The renderer assertion must fail when native-mode image clicks still reach image behavior."
		)
		assertContains(
			assertionsText,
			"Expected native tap zones to suppress ordinary text-link clicks",
			message = "The renderer assertion must fail when native-mode text links still reach WebView navigation."
		)
		assertContains(
			harnessText,
			"imageNativeCenterContentHit",
			message = "CSS smoke must simulate Android's delayed center-menu hit test for image taps, not only count bridge posts."
		)
		assertContains(
			harnessText,
			"imageNativeScaledContentHit",
			message = "CSS smoke must simulate Android MotionEvent view-pixel coordinates, not only browser CSS coordinates."
		)
		assertContains(
			harnessText,
			"textLinkNativeCenterContentHit",
			message = "CSS smoke must simulate Android's delayed center-menu hit test for link taps, not only count bridge posts."
		)
		assertContains(
			harnessText,
			"textLinkNativeScaledContentHit",
			message = "CSS smoke must prove link hit-testing survives Android view-pixel to CSS-pixel normalization."
		)
		assertContains(
			assertionsText,
			"Expected native center hit-test to suppress image chrome",
			message = "The renderer assertion must fail when Android-style center chrome would still open over an image interaction."
		)
		assertContains(
			assertionsText,
			"Expected scaled native center hit-test to suppress image chrome",
			message = "The renderer assertion must fail when native Android view-pixel coordinates miss image content."
		)
		assertContains(
			assertionsText,
			"Expected native center hit-test to suppress link chrome",
			message = "The renderer assertion must fail when Android-style center chrome would still open over a link interaction."
		)
		assertContains(
			assertionsText,
			"Expected scaled native center hit-test to suppress link chrome",
			message = "The renderer assertion must fail when native Android view-pixel coordinates miss link content."
		)
	}

	@Test
	fun androidReaderKeepsRuntimeContentHitTestOutOfNativeTapDispatch() {
		val bridgeText = readerBridgeText()
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val exportedBridge = bridgeText
			.substringAfter("window.NavicReaderBridge = {")
			.substringBefore("\n}")

		assertContains(
			bridgeText,
			"readerContentActionAtRootPoint",
			message = "The runtime keeps coordinate content hit testing for diagnostics and non-native fallback paths."
		)
		assertContains(
			exportedBridge,
			"readerContentActionAtPoint",
			message = "The bridge can expose content hit testing without making the Android WebView host the tap owner."
		)
		assertFalse(
			webViewHostText.contains("readerContentActionAtPoint") ||
				webViewHostText.contains("centerTapSequence") ||
				webViewHostText.contains("Reader surface delayed center tap ignored for runtime content hit"),
			"Android native tap dispatch must not depend on async WebView runtime hit testing."
		)
	}

	@Test
	fun androidReaderDoesNotQueryRuntimeContentHitBeforeNativeViewerAction() {
		val webViewHostText = readerEngineWebViewHostFile().readText()

		assertFalse(
			webViewHostText.contains("private fun queryReaderContentActionAtPoint(") ||
				webViewHostText.contains("readerContentActionAtPoint("),
			"Komikku native viewer actions should not be gated by renderer-side coordinate queries."
		)
	}

	@Test
	fun androidReaderRemembersRecentContentTouchOnlyInsideRuntimeMetadataLayer() {
		val bridgeText = readerBridgeText()
		val harnessText = listOf(
			File("tools/reader-harness/src/run-reader-harness.mjs"),
			File("../tools/reader-harness/src/run-reader-harness.mjs")
		).first { it.exists() }.readText()
		val assertionsText = listOf(
			File("tools/reader-harness/src/reader-trace-assertions.mjs"),
			File("../tools/reader-harness/src/reader-trace-assertions.mjs")
		).first { it.exists() }.readText()
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  readerContentActionInDocumentAtPoint")
		val contentActionAtRootPoint = bridgeText
			.substringAfter("readerContentActionAtRootPoint(rootX, rootY, viewWidth = null, viewHeight = null) {")
			.substringBefore("\n  handleReaderTapZoneTap")

		assertContains(
			bridgeText,
			"rememberReaderContentActionTouch",
			message = "Touch-phase image/link metadata should be remembered locally for renderer diagnostics and fallback content hit tests."
		)
		assertContains(
			bridgeText,
			"recentReaderContentActionAtRootPoint",
			message = "Runtime coordinate hit testing should check recent content-owned touch points before falling back to the current DOM."
		)
		assertTrue(
			claimInteractiveTouch.indexOf("rememberReaderContentActionTouch") <
				claimInteractiveTouch.indexOf("post(this.readerContentActionClaimPayload"),
			"Content touch metadata should be remembered before posting to Android so the local fallback survives document mutation."
		)
		assertTrue(
			contentActionAtRootPoint.indexOf("recentReaderContentActionAtRootPoint(rootPoint)") <
				contentActionAtRootPoint.indexOf("for (const entry of this.contentEntries())"),
			"Recent content-owned touch points must be checked before querying the mutated current document tree."
		)
		assertContains(
			harnessText,
			"imageRecentTouchContentHitAfterRemoval",
			message = "CSS smoke must model an image touch whose DOM node disappears before a later runtime hit test."
		)
		assertContains(
			harnessText,
			"textLinkRecentTouchContentHitAfterRemoval",
			message = "CSS smoke must model a link touch whose DOM node disappears before a later runtime hit test."
		)
		assertContains(
			assertionsText,
			"Expected recent image touch ownership",
			message = "Renderer assertions must fail when recent image touch ownership does not suppress native chrome."
		)
		assertContains(
			assertionsText,
			"Expected recent text link touch ownership",
			message = "Renderer assertions must fail when recent link touch ownership does not suppress native chrome."
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
