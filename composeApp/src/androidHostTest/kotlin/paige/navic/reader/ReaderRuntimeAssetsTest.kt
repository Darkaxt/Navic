package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeAssetsTest {
	@Test
	fun androidReaderAssetsPackageFoliateRuntimeAndNavicBridge() {
		val root = readerAssetRoot()
		val runtimeManifest = root.resolve("runtime.json")
		val index = root.resolve("index.html")
		val bridge = root.resolve("navic-reader.js")
		val foliatePackage = root.resolve("vendor/foliate-js/package.json")
		val foliateView = root.resolve("vendor/foliate-js/view.js")
		val foliateFixedLayout = root.resolve("vendor/foliate-js/fixed-layout.js")
		val foliatePdfAdapter = root.resolve("vendor/foliate-js/pdf.js")
		val pdfJs = root.resolve("vendor/foliate-js/vendor/pdfjs/pdf.js")
		val pdfJsWorker = root.resolve("vendor/foliate-js/vendor/pdfjs/pdf.worker.js")

		assertTrue(runtimeManifest.isFile, "reader runtime manifest must be packaged")
		assertTrue(index.isFile, "reader index.html must be packaged")
		assertTrue(bridge.isFile, "Navic reader bridge must be packaged")
		assertTrue(foliatePackage.isFile, "foliate-js package metadata must be packaged")
		assertTrue(foliateView.isFile, "foliate-js view runtime must be packaged")
		assertTrue(foliateFixedLayout.isFile, "foliate fixed-layout runtime must be packaged")
		assertTrue(foliatePdfAdapter.isFile, "foliate PDF adapter must be packaged")
		assertTrue(pdfJs.isFile, "PDF.js runtime must be packaged")
		assertTrue(pdfJsWorker.isFile, "PDF.js worker must be packaged")

		val manifestText = runtimeManifest.readText()
		val foliateViewText = foliateView.readText()
		val foliateFixedLayoutText = foliateFixedLayout.readText()
		val foliatePdfAdapterText = foliatePdfAdapter.readText()
		assertContains(manifestText, "\"engine\": \"foliate-js\"")
		assertContains(manifestText, "\"version\": \"1.0.1\"")
		assertContains(manifestText, "\"entrypoint\": \"index.html\"")

		assertContains(index.readText(), "navic-reader.js")
		assertContains(index.readText(), "style-src 'self' blob: 'unsafe-inline'")
		assertContains(index.readText(), "frame-src blob: data: about:")
		assertContains(bridge.readText(), "window.NavicReaderBridge")
		assertContains(bridge.readText(), "selectionChanged")
		assertContains(bridge.readText(), "applyOverlayFragment")
		assertContains(bridge.readText(), "applyHighlights")
		assertContains(bridge.readText(), "publicationReady")
		assertContains(bridge.readText(), "overlayFragmentActive")
		assertContains(bridge.readText(), "normalizeSearchResult")
		assertContains(bridge.readText(), "sectionTitle")
		assertContains(bridge.readText(), "postToc")
		assertContains(bridge.readText(), "flattenTocItems")
		assertContains(bridge.readText(), "type: 'toc'")
		assertContains(bridge.readText(), "margin-inline")
		assertContains(bridge.readText(), "[NavicReader]")
		assertContains(bridge.readText(), "openPublication:start")
		assertContains(bridge.readText(), "reportError")
		assertContains(foliateViewText, "customElements.define('foliate-view'")
		assertContains(foliateViewText, "await isPDF(file)")
		assertContains(foliateViewText, "await import('./pdf.js')")
		assertContains(foliatePdfAdapterText, "export const makePDF")
		assertContains(foliatePdfAdapterText, "ensurePDFJS")
		assertContains(foliatePdfAdapterText, "pdf.worker.js")
		assertContains(pdfJs.readText(), "root.pdfjsLib = factory()")
		assertContains(pdfJsWorker.readText(), "pdfjsWorker")
		assertContains(foliateFixedLayoutText, "applyShadowStyles")
		assertFalse(
			foliateFixedLayoutText.contains("construct-style-sheets-polyfill"),
			"fixed-layout must not import unpackaged bare modules"
		)
		assertContains(foliatePackage.readText(), "\"name\": \"foliate-js\"")
		assertContains(foliatePackage.readText(), "\"version\": \"1.0.1\"")
	}

	@Test
	fun androidRuntimeConstantsPointAtPackagedReaderEntrypoint() {
		assertEquals("reader/index.html", ReaderWebRuntime.AssetEntrypointPath)
		assertEquals("https://appassets.androidplatform.net/assets/reader/index.html", ReaderWebRuntime.entrypointUrl)
		assertEquals("NavicAndroidBridge", ReaderWebRuntime.AndroidBridgeName)
		assertFalse(ReaderWebRuntime.LocalPublicationFileAccessEnabled)
		assertFalse(ReaderWebRuntime.WebContentsDebuggingDefaultEnabled)
	}

	@Test
	fun androidWebViewRuntimeHonorsReaderViewportMeta() {
		val runtimeText = readerWebRuntimeFile().readText()

		assertContains(
			runtimeText,
			"useWideViewPort = true",
			message = "Android WebView must honor the reader viewport meta tag instead of using a tall wide layout viewport"
		)
		assertContains(runtimeText, "loadWithOverviewMode = false")
		assertContains(runtimeText, "textZoom = 100")
	}

	@Test
	fun androidReaderWebViewRuntimeBypassesCachedBundledAssets() {
		val runtimeText = readerWebRuntimeFile().readText()

		assertContains(
			runtimeText,
			"cacheMode = WebSettings.LOAD_NO_CACHE",
			message = "Reader WebView must not keep serving stale appassets reader JS after APK updates."
		)
		assertContains(
			runtimeText,
			"webView.clearCache(true)",
			message = "Reader WebView should clear its HTTP cache before loading the bundled runtime."
		)
	}

	@Test
	fun androidReaderWebViewDebuggingIsControlledByEbookSetting() {
		val hostText = readerWebViewHostFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(hostText, "enableDebugging = settings.webContentsDebuggingEnabled == true")
		assertContains(ebooksSettingsText, "readerWebContentsDebuggingEnabled")
		assertContains(ebooksSettingsText, "option_ebook_reader_web_debugging")
		assertContains(searchSettingsText, "ebooks.web-debugging")
		assertContains(searchSettingsText, "readerWebContentsDebuggingEnabled")
	}

	@Test
	fun androidReaderKeepScreenOnIsControlledByEbookSetting() {
		val hostText = readerWebViewHostFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(hostText, "view.keepScreenOn = settings.keepScreenOn == true")
		assertContains(ebooksSettingsText, "readerKeepScreenOn")
		assertContains(ebooksSettingsText, "option_ebook_reader_keep_screen_on")
		assertContains(searchSettingsText, "ebooks.keep-screen-on")
		assertContains(searchSettingsText, "readerKeepScreenOn")
	}

	@Test
	fun androidPdfRuntimePublishesStableViewportForFixedLayout() {
		val root = readerAssetRoot()
		val foliateFixedLayoutText = root.resolve("vendor/foliate-js/fixed-layout.js").readText()
		val foliatePdfAdapterText = root.resolve("vendor/foliate-js/pdf.js").readText()

		assertContains(
			foliatePdfAdapterText,
			"return { type: 'image', src, width: pageWidth, height: pageHeight }",
			message = "PDF pages must expose direct image dimensions before fixed-layout paint"
		)
		assertContains(foliatePdfAdapterText, "[FoliatePDF] renderPage")
		assertContains(foliatePdfAdapterText, "spread: 'none'")
		assertContains(foliatePdfAdapterText, "type: 'image'")
		assertContains(foliatePdfAdapterText, "URL.createObjectURL")
		assertContains(foliateFixedLayoutText, "inlineImage")
		assertContains(foliateFixedLayoutText, "[FoliateFXL] inline-image-loaded")
		assertContains(foliateFixedLayoutText, "await getViewport(doc, this.defaultViewport)")
		assertContains(foliateFixedLayoutText, "normalizeFrameSize")
		assertContains(foliateFixedLayoutText, "Number.isFinite")
		assertContains(foliateFixedLayoutText, "[FoliateFXL] frame-loaded")
	}

	@Test
	fun androidFixedLayoutKeepsPdfPagesVisibleWhenWebViewReportsWideViewport() {
		val root = readerAssetRoot()
		val foliateFixedLayoutText = root.resolve("vendor/foliate-js/fixed-layout.js").readText()
		val foliatePdfAdapterText = root.resolve("vendor/foliate-js/pdf.js").readText()

		assertContains(
			foliateFixedLayoutText,
			"align-items: flex-start;",
			message = "PDF image pages must not be vertically centered inside Android WebView's wide layout viewport"
		)
		assertContains(foliateFixedLayoutText, "[FoliateFXL] layout")
		assertContains(foliateFixedLayoutText, "visualViewport")
		assertContains(foliatePdfAdapterText, "[FoliatePDF] bitmap")
		assertContains(foliatePdfAdapterText, "nonWhite")
	}

	@Test
	fun androidPaginatorKeepsEpubIframesInsideVisibleViewport() {
		val root = readerAssetRoot()
		val bridgeText = root.resolve("navic-reader.js").readText()
		val paginatorText = root.resolve("vendor/foliate-js/paginator.js").readText()

		assertContains(
			paginatorText,
			"applyVisibleViewport",
			message = "EPUB paginator must constrain layout to Android WebView's visible viewport"
		)
		assertContains(paginatorText, "[FoliatePaginator] layout")
		assertContains(paginatorText, "visualViewport")
		assertContains(paginatorText, "iframe-srcdoc-loaded")
		assertContains(paginatorText, "firstText")
		assertContains(bridgeText, "content-layout")
		assertContains(bridgeText, "frameElement")
	}

	@Test
	fun androidReaderShellOwnsAnxStyleViewportSurface() {
		val root = readerAssetRoot()
		val indexText = root.resolve("index.html").readText()
		val bridgeText = root.resolve("navic-reader.js").readText()

		assertContains(
			indexText,
			"height: 100vh",
			message = "Reader shell must use an Anx-style viewport height instead of inheriting a collapsed percentage height"
		)
		assertContains(indexText, "position: fixed;")
		assertContains(indexText, "inset: 0;")
		assertContains(indexText, "body > foliate-view")
		assertContains(bridgeText, "readerRoot = document.body")
		assertContains(bridgeText, "applyReaderViewportLayout")
		assertContains(bridgeText, "window.visualViewport")
		assertContains(bridgeText, "renderer?.render?.()")
	}

	@Test
	fun androidReaderBridgeExposesAnxStylePageTurnCommands() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()

		assertContains(bridgeText, "case 'nextPage'")
		assertContains(bridgeText, "case 'previousPage'")
		assertContains(bridgeText, "async nextPage()")
		assertContains(bridgeText, "async previousPage()")
		assertContains(bridgeText, "this.view?.next?.()")
		assertContains(bridgeText, "this.view?.prev?.()")
		assertContains(bridgeText, "page-turn:start")
		assertContains(bridgeText, "page-turn:done")
	}

	@Test
	fun androidReaderBridgeExposesProgressSeekCommand() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()

		assertContains(bridgeText, "case 'goToProgress'")
		assertContains(bridgeText, "async goToProgress(progress)")
		assertContains(bridgeText, "this.view?.goToFraction")
		assertContains(bridgeText, "progress-seek")
		assertContains(readerScreenText, "ReaderBridgeCommand.GoToProgress")
		assertContains(readerScreenText, "Slider(")
		assertContains(readerScreenText, "onProgressSeek: (Float) -> Unit")
	}

	@Test
	fun androidReaderBridgePortsAnxStyleScrolledEdgePageTurns() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()

		assertContains(bridgeText, "ScrollEdgeTurnSwipeThreshold")
		assertContains(bridgeText, "attachScrolledEdgeTurnGestures")
		assertContains(bridgeText, "doc.addEventListener('touchstart'")
		assertContains(bridgeText, "doc.addEventListener('touchmove'")
		assertContains(bridgeText, "doc.addEventListener('touchend'")
		assertContains(bridgeText, "renderer.scrolled")
		assertContains(bridgeText, "renderer.viewSize - renderer.end")
		assertContains(bridgeText, "renderer.start <= ScrollEdgeTurnSlop")
		assertContains(bridgeText, "page-turn:edge-swipe")
	}

	@Test
	fun commonReaderChromeExposesPageTurnControls() {
		val readerScreenText = readerScreenFile().readText()

		assertContains(readerScreenText, "onPreviousPage: () -> Unit")
		assertContains(readerScreenText, "onNextPage: () -> Unit")
		assertContains(readerScreenText, "ReaderBridgeCommand.PreviousPage")
		assertContains(readerScreenText, "ReaderBridgeCommand.NextPage")
		assertContains(readerScreenText, "Icons.Filled.SkipPrevious")
		assertContains(readerScreenText, "Icons.Filled.SkipNext")
	}

	@Test
	fun readerChromeIsImmersiveAndDrivenByCenterTapBridgeEvents() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()

		assertContains(bridgeText, "readerCenterTap")
		assertContains(bridgeText, "attachCenterTapGesture")
		assertContains(bridgeText, "doc.addEventListener('click'")
		assertContains(bridgeText, "doc.addEventListener('touchstart'")
		assertContains(bridgeText, "doc.addEventListener('touchend'")
		assertContains(bridgeText, "handleReaderTapZone")
		assertContains(bridgeText, "__navicLastTapHandledAt")
		assertContains(bridgeText, "CenterTapMovementSlop")
		assertContains(bridgeText, "readerTapZone")
		assertContains(bridgeText, "await this.previousPage()")
		assertContains(bridgeText, "await this.nextPage()")
		assertContains(readerScreenText, "ReaderBridgeEvent.CenterTap")
		assertContains(readerScreenText, "chromeVisible")
		assertContains(readerScreenText, "if (chromeVisible)")
		assertFalse(
			readerScreenText.contains("RootTopBar("),
			"ReaderScreen must not show the global search/settings/account top bar in the reading area."
		)
	}

	@Test
	fun androidReaderSupportsPdfSurfaceNavigationAndScrolling() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val surfaceGesture = bridgeText
			.substringAfter("attachSurfaceTapGesture(element) {")
			.substringBefore("\n  attachLinkNavigation")

		assertContains(bridgeText, "attachSurfaceTapGesture")
		assertContains(bridgeText, "this.attachSurfaceTapGesture(this.view)")
		assertContains(bridgeText, "this.view?.isFixedLayout === true")
		assertContains(bridgeText, "overflow: fixedLayout ? 'auto' : 'hidden'")
		assertContains(bridgeText, "handleReaderTapZone(event, document, 'surface')")
		assertContains(bridgeText, "FixedLayoutSurfaceSwipeThreshold")
		assertContains(bridgeText, "turnFixedLayoutSwipePage(deltaX)")
		assertContains(surfaceGesture, "element.addEventListener('touchstart'")
		assertContains(surfaceGesture, "element.addEventListener('touchmove'")
		assertContains(surfaceGesture, "element.addEventListener('touchend'")
		assertContains(surfaceGesture, "element.addEventListener('touchcancel'")
		assertContains(surfaceGesture, "Math.abs(deltaX) >= FixedLayoutSurfaceSwipeThreshold")
		assertContains(surfaceGesture, "await this.turnFixedLayoutSwipePage(deltaX)")
		assertContains(surfaceGesture, "__navicLastSurfaceTapHandledAt")
		assertContains(bridgeText, "startLocator?.progress")
		assertContains(bridgeText, "await this.goToProgress(progress)")
	}

	@Test
	fun androidReaderPreservesProgressOnlyResumeLocatorsForFixedLayoutPublications() {
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerWebViewHostFile().readText()

		assertContains(readerScreenText, "startProgress = resumeStartLocator?.progress")
		assertContains(webViewHostText, "startProgress: Double?")
		assertContains(webViewHostText, "startProgress?.toString().orEmpty()")
		assertContains(webViewHostText, "progress = startProgress")
		assertContains(webViewHostText, "it.cfi != null || it.href != null || it.progress != null")
	}

	@Test
	fun androidReaderPublishesPostReadyLocationSnapshotAfterResumeSeek() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val openPublication = bridgeText
			.substringAfter("async openPublication({ url, mediaOverlayEnabled = false, startLocator = null, settings = null }) {")
			.substringBefore("\n  close()")
		val onRelocate = bridgeText
			.substringAfter("onRelocate(detail) {")
			.substringBefore("\n  attachContentDocumentBehaviors")

		assertContains(bridgeText, "lastRelocateDetail = null")
		assertContains(bridgeText, "postLocationChanged(detail")
		assertContains(bridgeText, "postCurrentLocationSnapshot('initial-resume')")
		assertContains(onRelocate, "this.lastRelocateDetail = detail")
		assertContains(onRelocate, "this.postLocationChanged(detail")
		assertTrue(
			openPublication.indexOf("post({ type: 'publicationReady' })") <
				openPublication.indexOf("this.postCurrentLocationSnapshot('initial-resume')"),
			"PublicationReady must be sent before the synthetic location snapshot so native progress saving is armed."
		)
	}

	@Test
	fun androidReaderReportsFixedLayoutPagePositionToChrome() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()
		val chromeStateText = readerCommonFile("ReaderChromeState.kt").readText()

		assertContains(bridgeText, "fixedLayoutPagePosition(detail)")
		assertContains(bridgeText, "this.view?.isFixedLayout === true")
		assertContains(bridgeText, "this.view?.book?.sections?.length")
		assertContains(bridgeText, "pageIndex: pagePosition?.pageIndex")
		assertContains(bridgeText, "pageCount: pagePosition?.pageCount")
		assertContains(bridgeProtocolText, "val pageIndex: Int? = null")
		assertContains(bridgeProtocolText, "val pageCount: Int? = null")
		assertContains(chromeStateText, "readerPageProgressLabel")
		assertContains(chromeStateText, "\"Page ${'$'}{pageIndex + 1} of ${'$'}pageCount\"")
	}

	@Test
	fun androidReaderStylesEbookHyperlinksAsInlineFastForwardAffordances() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()

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
	fun androidReaderInjectsThemeColorsIntoPublicationDocuments() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()

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
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()

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
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val interactiveTarget = bridgeText
			.substringAfter("const isInteractiveReaderTarget = target =>")
			.substringBefore("\n\nconst stableHash")
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  readerTapZone")

		assertContains(bridgeText, "const isReaderMediaAnchor =")
		assertContains(bridgeText, "const isReaderMediaTapTarget =")
		assertContains(interactiveTarget, "readerMediaSelector")
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
	fun androidReaderConsumesTouchImageTogglesBeforeSyntheticLinkClicks() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val linkNavigation = bridgeText
			.substringAfter("attachLinkNavigation(doc, index) {")
			.substringBefore("\n  classifyReaderLinks")
		val sepiaToggle = bridgeText
			.substringAfter("attachSepiaImageOverlayToggle(doc) {")
			.substringBefore("\n  readerTapZone")

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
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
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
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
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
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
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

	@Test
	fun androidReaderExposesKomikkuStyleTapZonePresets() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(bridgeText, "readerTapZoneMode")
		assertContains(bridgeText, "KomikkuNavigationRegionMenu")
		assertContains(bridgeText, "KomikkuNavigationRegionPrevious")
		assertContains(bridgeText, "KomikkuNavigationRegionNext")
		assertContains(bridgeText, "KomikkuNavigationRegionLeft")
		assertContains(bridgeText, "KomikkuNavigationRegionRight")
		assertContains(bridgeText, "komikkuConstantMenuRegion")
		assertContains(bridgeText, "komikkuRegionSize")
		assertContains(bridgeText, "komikkuNavigationRegions")
		assertContains(bridgeText, "komikkuTapAction")
		assertContains(bridgeText, "case ReaderTapZoneEdge")
		assertContains(bridgeText, "case ReaderTapZoneKindle")
		assertContains(bridgeText, "case ReaderTapZoneLShaped")
		assertContains(bridgeText, "case ReaderTapZoneRightLeft")
		assertContains(bridgeText, "case ReaderTapZoneDisabled")
		assertFalse(
			bridgeText.contains("CenterTapStartFraction") || bridgeText.contains("CenterTapEndFraction"),
			"Tap-zone dispatch must use the Komikku normalized-region model, not Navic's old center box thresholds."
		)
		assertContains(ebooksSettingsText, "readerTapZone")
		assertContains(ebooksSettingsText, "option_ebook_reader_tap_zone")
		assertContains(searchSettingsText, "ebooks.tap-zone")
	}

	@Test
	fun androidReaderPrioritizesCenterMenuTapZoneOverPageTurns() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val tapAction = bridgeText
			.substringAfter("const komikkuTapAction = (")
			.substringBefore("\n\nconst readerAssetUrl")

		assertContains(bridgeText, "ReaderCenterMenuRegionSize")
		assertContains(bridgeText, "readerCenterMenuRegion")
		assertContains(bridgeText, "smallerTapZone ? 0.2 : 0.25")
		assertFalse(
			bridgeText.contains("smallerTapZone ? 0.25 : 0.33"),
			"Komikku's one-third tap bands are too large once mapped onto Navic's WebView reader surface."
		)
		assertTrue(
			tapAction.indexOf("readerCenterMenuRegion") <
				tapAction.indexOf("regions.find"),
			"Center/menu taps must win before previous/next regions consume the visual page center."
		)
		assertTrue(
			tapAction.indexOf("komikkuConstantMenuRegion") <
				tapAction.indexOf("regions.find"),
			"The always-visible menu strip must still be checked before page-turn regions."
		)
	}

	@Test
	fun androidReaderNormalizesTapZonesAgainstRootReaderSurfaceLikeKomikku() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerTapZone = bridgeText
			.substringAfter("readerTapZone(event, doc) {")
			.substringBefore("\n  effectiveReaderDirection")

		assertContains(bridgeText, "readerRootTapPoint")
		assertContains(bridgeText, "const frameElement = win?.frameElement")
		assertContains(bridgeText, "const frameRect = frameElement?.getBoundingClientRect?.()")
		assertContains(bridgeText, "const surfaceRect = this.readerTapSurfaceRect()")
		assertContains(readerTapZone, "const point = readerRootTapPoint(event, doc)")
		assertContains(readerTapZone, "const surfaceRect = this.readerTapSurfaceRect()")
		assertContains(readerTapZone, "(point.x - surfaceRect.left) / surfaceRect.width")
		assertContains(readerTapZone, "(point.y - surfaceRect.top) / surfaceRect.height")
		assertContains(readerTapZone, "tap-zone")
		assertFalse(
			readerTapZone.contains("win.innerWidth") || readerTapZone.contains("doc?.documentElement?.clientWidth"),
			"Tap-zone dispatch must normalize against the root reader surface, not a Foliate iframe document viewport."
		)
		assertFalse(
			readerTapZone.contains("const x = event.clientX") || readerTapZone.contains("const y = event.clientY"),
			"Iframe taps must be translated through their frame rect before navigation regions are evaluated."
		)
	}

	@Test
	fun androidReaderExposesKomikkuSmallerTapZoneControl() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertContains(bridgeText, "settings.smallerTapZone === true")
		assertContains(bridgeText, "this.smallerTapZone = settings.smallerTapZone === true")
		assertContains(readerScreenText, "Smaller tap zones")
		assertContains(readerScreenText, "toggleSmallerTapZone()")
		assertContains(ebooksSettingsText, "readerSmallerTapZone")
		assertContains(ebooksSettingsText, "option_ebook_reader_smaller_tap_zones")
		assertContains(searchSettingsText, "ebooks.smaller-tap-zones")
		assertContains(searchSettingsText, "readerSmallerTapZone")
		assertContains(preferenceText, "smallerTapZone = readerSmallerTapZone")
		assertContains(bridgeProtocolText, "val smallerTapZone: Boolean? = null")
		assertContains(bridgeProtocolText, "smallerTapZone?.let { put(\"smallerTapZone\", it) }")
	}

	@Test
	fun androidReaderPortsKomikkuFullscreenSystemBars() {
		val systemBarsEffect = listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderSystemBarsEffect.android.kt"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderSystemBarsEffect.android.kt")
		).firstOrNull { it.isFile }
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()
		val bridgeProtocolText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertTrue(systemBarsEffect?.isFile == true, "Reader must own a native system-bars effect like Komikku ReaderActivity.")
		val systemBarsEffectText = systemBarsEffect.readText()
		assertContains(systemBarsEffectText, "WindowCompat.getInsetsController")
		assertContains(systemBarsEffectText, "WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE")
		assertContains(systemBarsEffectText, "WindowInsetsCompat.Type.systemBars()")
		assertContains(systemBarsEffectText, "controller.hide(WindowInsetsCompat.Type.systemBars())")
		assertContains(systemBarsEffectText, "controller.show(WindowInsetsCompat.Type.systemBars())")
		assertContains(readerScreenText, "ReaderSystemBarsEffect(")
		assertContains(readerScreenText, "chromeVisible || optionsVisible")
		assertContains(readerScreenText, "Fullscreen")
		assertContains(readerScreenText, "toggleFullscreen()")
		assertContains(ebooksSettingsText, "readerFullscreen")
		assertContains(ebooksSettingsText, "option_ebook_reader_fullscreen")
		assertContains(searchSettingsText, "ebooks.fullscreen")
		assertContains(searchSettingsText, "readerFullscreen")
		assertContains(preferenceText, "fullscreen = readerFullscreen")
		assertContains(bridgeProtocolText, "val fullscreen: Boolean? = null")
		assertContains(bridgeProtocolText, "fullscreen?.let { put(\"fullscreen\", it) }")
	}

	@Test
	fun androidReaderExposesExpandedThemePalettes() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(bridgeText, "ReaderThemePalettes")
		assertContains(bridgeText, "sepia: {")
		assertContains(bridgeText, "dusk: {")
		assertContains(bridgeText, "black: {")
		assertContains(bridgeText, "--reader-accent")
		assertContains(ebooksSettingsText, "option_ebook_reader_theme_sepia")
		assertContains(ebooksSettingsText, "option_ebook_reader_theme_black")
		assertContains(searchSettingsText, "ReaderSupportedThemes")
	}

	@Test
	fun androidReaderExposesParagraphSpacingAndPublisherStyleControls() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(bridgeText, "readerParagraphSpacingEm")
		assertContains(bridgeText, "--reader-paragraph-spacing")
		assertContains(bridgeText, "'--reader-paragraph-spacing': readerParagraphSpacingEm(settings)")
		assertContains(bridgeText, "margin-block-end: var(--reader-paragraph-spacing, \${readerParagraphSpacingEm(settings)})")
		assertContains(bridgeText, "margin-bottom: var(--reader-paragraph-spacing, \${readerParagraphSpacingEm(settings)})")
		assertContains(bridgeText, "settings.publisherStyles === true")
		assertContains(bridgeText, "paragraphSpacingPercent")
		assertContains(bridgeText, "paragraphSpacing=\${")
		assertContains(ebooksSettingsText, "readerParagraphSpacingPercent")
		assertContains(ebooksSettingsText, "readerPublisherStylesEnabled")
		assertContains(ebooksSettingsText, "option_ebook_reader_paragraph_spacing")
		assertContains(ebooksSettingsText, "option_ebook_reader_publisher_styles")
		assertContains(searchSettingsText, "ebooks.paragraph-spacing")
		assertContains(searchSettingsText, "ebooks.publisher-styles")
	}

	@Test
	fun androidReaderAppliesParagraphSpacingOutsidePublisherStyleOverride() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val contentCss = bridgeText
			.substringAfter("const readerContentCss = settings =>")
			.substringBefore("const normalizeSearchResult")
		val typographyCss = bridgeText
			.substringAfter("const readerTypographyCss = settings =>")
			.substringBefore("const readerParagraphSpacingCss = settings =>")

		assertContains(bridgeText, "const readerParagraphSpacingCss = settings =>")
		assertContains(contentCss, "\${readerParagraphSpacingCss(settings)}")
		assertFalse(
			typographyCss.contains("margin-block-end: var(--reader-paragraph-spacing"),
			"Paragraph spacing must remain active even when publisher typography styles are enabled."
		)
	}

	@Test
	fun androidReaderReinjectsCompleteContentCssIntoLoadedPublicationDocuments() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\n  applyReaderDirection")

		assertContains(applyDocumentTheme, "themeStyle.textContent = readerContentCss(settings)")
		assertFalse(
			applyDocumentTheme.contains("themeStyle.textContent = readerDocumentThemeCss(settings)"),
			"Loaded publication documents need the full reader stylesheet, not only theme colors."
		)
		assertFalse(
			applyDocumentTheme.contains("ensurePaperTextureLayer(doc)"),
			"Paper texture must be a single reader-window layer, not injected into each publication document."
		)
		assertFalse(
			applyDocumentTheme.contains("updatePaperTextureLayer"),
			"Paper texture must not be applied to rendered EPUB elements."
		)
	}

	@Test
	fun androidReaderPackagesDeterministicPaperTextureVariants() {
		val root = readerAssetRoot()
		val bridgeText = root.resolve("navic-reader.js").readText()
		val texture1 = root.resolve("paper-textures/paper-texture-1.png")
		val texture2 = root.resolve("paper-textures/paper-texture-2.png")
		val texture3 = root.resolve("paper-textures/paper-texture-3.png")

		assertTrue(texture1.isFile, "Reader paper texture 1 must be packaged")
		assertTrue(texture2.isFile, "Reader paper texture 2 must be packaged")
		assertTrue(texture3.isFile, "Reader paper texture 3 must be packaged")
		assertTrue(texture1.length() > 1_000, "Reader paper texture 1 should be a real image")
		assertTrue(texture2.length() > 1_000, "Reader paper texture 2 should be a real image")
		assertTrue(texture3.length() > 1_000, "Reader paper texture 3 should be a real image")
		assertTrue(texture1.hasPngAlphaChannel(), "Reader paper texture 1 must be transparent")
		assertTrue(texture2.hasPngAlphaChannel(), "Reader paper texture 2 must be transparent")
		assertTrue(texture3.hasPngAlphaChannel(), "Reader paper texture 3 must be transparent")
		assertTrue(texture1.averagePngAlpha() >= 2.0, "Reader paper texture 1 must be visible at runtime")
		assertTrue(texture2.averagePngAlpha() >= 2.0, "Reader paper texture 2 must be visible at runtime")
		assertTrue(texture3.averagePngAlpha() >= 2.0, "Reader paper texture 3 must be visible at runtime")
		assertContains(bridgeText, "ReaderPaperTextureAssets")
		assertContains(bridgeText, "paper-textures/paper-texture-1.png")
		assertContains(bridgeText, "paper-textures/paper-texture-2.png")
		assertContains(bridgeText, "paper-textures/paper-texture-3.png")
		assertContains(bridgeText, "ReaderPaperTextureVariantCount = ReaderPaperTextureAssets.length * 2 * 2")
		assertContains(bridgeText, "readerPaperTextureVariantKey")
		assertContains(bridgeText, "readerPaperTextureVariantForPage")
		assertContains(bridgeText, "textureIndex")
		assertContains(bridgeText, "rotate180")
		assertContains(bridgeText, "mirrored")
		assertContains(bridgeText, "scaleX(-1)")
		assertContains(bridgeText, "rotate(180deg)")
		assertContains(bridgeText, "'pointer-events': 'none'")
		assertContains(bridgeText, "ReaderSurfacePaperTextureLayerSelector")
		assertContains(bridgeText, "readerSurfacePaperTextureOpacity")
		assertContains(bridgeText, "surfaceTextureOpacity=\${")
		assertContains(bridgeText, "surfaceTextureImage=\${")
		assertFalse(
			bridgeText.contains("ReaderPaperTextureLayerSelector = '[data-navic-paper-texture-layer=\"true\"]'"),
			"Document-scoped texture layers cause opacity stacking across rendered elements."
		)
	}

	@Test
	fun androidReaderAppliesParagraphSpacingAsElementStyles() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val paragraphSpacing = bridgeText
			.substringAfter("const applyReaderParagraphSpacing = (doc, settings) =>")
			.substringBefore("\n\nconst ensureReaderSurfaceTextureLayer")
		val paragraphSpacingCss = bridgeText
			.substringAfter("const readerParagraphSpacingCss = settings =>")
			.substringBefore("\n\nconst isThemeBackgroundMediaElement")
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\n  applyReaderDirection")

		assertContains(paragraphSpacing, "const spacing = readerParagraphSpacingEm(settings)")
		assertContains(paragraphSpacing, "doc?.querySelectorAll?.('p,[data-navic-paragraph-block=\"true\"]')")
		assertContains(paragraphSpacing, "const blocks = Array.from")
		assertContains(paragraphSpacing, "'display': 'block'")
		assertContains(paragraphSpacing, "'margin-block-end': spacing")
		assertContains(paragraphSpacing, "'margin-block-start': '0'")
		assertContains(paragraphSpacing, "'padding-block-end': '0'")
		assertContains(paragraphSpacing, "'margin-bottom': spacing")
		assertContains(paragraphSpacingCss, "margin-block-end: var(--reader-paragraph-spacing")
		assertContains(paragraphSpacingCss, "margin-bottom: var(--reader-paragraph-spacing")
		assertFalse(
			paragraphSpacingCss.contains("html body p::after"),
			"Paragraph spacing must use real element margins because paginated EPUB layout can ignore pseudo-element spacing."
		)
		assertContains(applyDocumentTheme, "applyReaderParagraphSpacing(doc, settings)")
	}

	@Test
	fun androidReaderMirrorsPaperTextureOnTopLevelSurface() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val surfaceTextureUpdater = bridgeText
			.substringAfter("updateSurfacePaperTexture(detail = {}) {")
			.substringBefore("\n  applyReaderDirection")
		val surfaceLayerUpdater = bridgeText
			.substringAfter("const updateReaderSurfaceTextureLayer = (layer, textureVariant, settings) =>")
			.substringBefore("\n\nconst isParagraphCandidate")
		val applySettings = bridgeText
			.substringAfter("applySettings(settings) {")
			.substringBefore("\n  applyThemeToLoadedContent")
		val onLoad = bridgeText
			.substringAfter("onLoad(detail = {}) {")
			.substringBefore("\n  logContentLayout")
		val onRelocate = bridgeText
			.substringAfter("onRelocate(detail) {")
			.substringBefore("\n  attachContentDocumentBehaviors")

		assertContains(bridgeText, "ReaderSurfacePaperTextureLayerSelector")
		assertContains(bridgeText, "ensureReaderSurfaceTextureLayer")
		assertContains(bridgeText, "updateReaderSurfaceTextureLayer")
		assertContains(bridgeText, "readerRoot.append(layer)")
		assertContains(bridgeText, "data-navic-surface-paper-texture-layer")
		assertContains(bridgeText, "readerSurfacePaperTextureOpacity")
		assertContains(bridgeText, "this.updateSurfacePaperTexture")
		assertFalse(
			surfaceTextureUpdater.contains("if (this.view?.isFixedLayout !== true)"),
			"The paper texture must cover the reader window for EPUB and fixed-layout content."
		)
		assertContains(surfaceTextureUpdater, "readerRoot.dataset.navicSurfacePaperTextureAsset")
		assertFalse(
			surfaceLayerUpdater.contains("readerPaperTextureBackgroundImage(textureVariant, settings)"),
			"The top-level surface texture must stay subtle and must not reuse the stacked document texture overlay."
		)
		assertContains(applySettings, "this.updateSurfacePaperTexture()")
		assertContains(onLoad, "this.updateSurfacePaperTexture(detail)")
		assertContains(onRelocate, "this.updateSurfacePaperTexture(detail)")
		assertContains(bridgeText, "position: 'fixed'")
		assertContains(bridgeText, "'pointer-events': 'none'")
	}

	@Test
	fun androidReaderKeepsPaperTextureAtReaderWindowSurfaceOnly() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val documentThemeCss = bridgeText
			.substringAfter("const readerDocumentThemeCss = settings =>")
			.substringBefore("const readerContentCss = settings =>")
		val applyDocumentTheme = bridgeText
			.substringAfter("applyDocumentTheme(doc, settings = this.readerSettings, index = undefined) {")
			.substringBefore("\n  applyReaderDirection")
		val surfaceTextureUpdater = bridgeText
			.substringAfter("updateSurfacePaperTexture(detail = {}) {")
			.substringBefore("\n  applyReaderDirection")
		val surfaceLayerUpdater = bridgeText
			.substringAfter("const updateReaderSurfaceTextureLayer = (layer, textureVariant, settings) =>")
			.substringBefore("\n\nconst isThemeBackgroundMediaElement")

		assertFalse(documentThemeCss.contains("html::before"), "Document pseudo-elements must not carry paper texture.")
		assertFalse(documentThemeCss.contains("body::before"), "Document pseudo-elements must not carry paper texture.")
		assertFalse(
			documentThemeCss.contains("[data-navic-paper-texture-layer=\"true\"]"),
			"Texture must not be injected into individual EPUB documents."
		)
		assertFalse(
			documentThemeCss.contains("background-image: var(--reader-paper-texture-image)"),
			"Document backgrounds should stay solid theme colors; texture belongs to the reader window."
		)
		assertFalse(
			bridgeText.contains("readerPaperTextureLayerCount(settings)"),
			"Stacking the same texture many times creates the sepia opacity mess."
		)
		assertFalse(
			bridgeText.contains("Array.from({ length: readerPaperTextureLayerCount(settings) }"),
			"Texture must be one window layer, not a repeated background stack."
		)
		assertFalse(applyDocumentTheme.contains("ensurePaperTextureLayer(doc)"))
		assertFalse(applyDocumentTheme.contains("updatePaperTextureLayer"))
		assertFalse(applyDocumentTheme.contains("'background-image': readerPaperTextureBackgroundImage"))
		assertContains(surfaceTextureUpdater, "ensureReaderSurfaceTextureLayer()")
		assertContains(surfaceLayerUpdater, "position: 'fixed'")
		assertContains(surfaceLayerUpdater, "width: '100vw'")
		assertContains(surfaceLayerUpdater, "height: '100vh'")
		assertContains(surfaceLayerUpdater, "'background-image': textureUrl")
		assertContains(surfaceLayerUpdater, "opacity: readerSurfacePaperTextureOpacity(settings)")
		assertContains(surfaceLayerUpdater, "'pointer-events': 'none'")
	}

	@Test
	fun androidReaderContentLayoutLogsComputedParagraphAndTextureState() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val contentLayoutLogger = bridgeText
			.substringAfter("logContentLayout(label) {")
			.substringBefore("\n  contentEntries")

		assertContains(contentLayoutLogger, "paragraphBlockCount")
		assertContains(contentLayoutLogger, "firstParagraphMarginEnd")
		assertContains(contentLayoutLogger, "surfaceTextureLayer")
		assertContains(contentLayoutLogger, "surfaceTextureAsset")
	}

	@Test
	fun commonReaderParagraphSpacingControlsUseReadableDefaultFallback() {
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(readerScreenText, "state.settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent")
		assertContains(ebooksSettingsText, "settings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent")
		assertContains(searchSettingsText, "readerSettings.paragraphSpacingPercent ?: DefaultReaderParagraphSpacingPercent")
		assertFalse(readerScreenText.contains("state.settings.paragraphSpacingPercent ?: 0"))
		assertFalse(ebooksSettingsText.contains("settings.paragraphSpacingPercent ?: 0"))
		assertFalse(searchSettingsText.contains("readerSettings.paragraphSpacingPercent ?: 0"))
	}

	@Test
	fun androidReaderPublicationRuntimeLogsCacheHitMissState() {
		val runtimeHostText = readerAndroidFile("ReaderPublicationRuntimeHost.android.kt").readText()

		assertContains(runtimeHostText, "val resolved = BinderyReaderPublicationResolver")
		assertContains(runtimeHostText, "cache=${'$'}{if (resolved.fromCache) \"hit\" else \"miss\"}")
		assertContains(runtimeHostText, "cacheKey=${'$'}{resolved.cacheKey}")
		assertContains(runtimeHostText, "fileBytes=${'$'}{resolved.publicationFile.length()}")
	}

	@Test
	fun androidReaderUsesSectionIndexFallbackForProgressOnlyFixedLayoutResume() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val goToProgress = bridgeText
			.substringAfter("async goToProgress(progress) {")
			.substringBefore("\n  async nextPage()")

		assertContains(bridgeText, "progressTargetForSections")
		assertContains(goToProgress, "const progressTarget = this.progressTargetForSections(fraction)")
		assertContains(goToProgress, "if (progressTarget != null)")
		assertContains(goToProgress, "await this.view.goTo(progressTarget)")
		assertContains(goToProgress, "await this.view.goToFraction(fraction)")
		assertContains(bridgeText, "this.view?.book?.sections?.length")
	}

	@Test
	fun androidReaderUsesSectionIndexFallbackWhenFixedLayoutPageTurnDoesNotMove() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val turnPage = bridgeText
			.substringAfter("async turnPage(direction) {")
			.substringBefore("\n  attachScrolledEdgeTurnGestures")

		assertContains(bridgeText, "fixedLayoutAdjacentPageTarget(direction)")
		assertContains(bridgeText, "fixedLayoutCurrentPageIndex()")
		assertContains(turnPage, "const beforePageIndex = this.fixedLayoutCurrentPageIndex()")
		assertContains(turnPage, "const fallbackPageTarget = this.fixedLayoutAdjacentPageTarget(direction)")
		assertContains(turnPage, "const afterPageIndex = this.fixedLayoutCurrentPageIndex()")
		assertContains(turnPage, "if (fallbackPageTarget != null && beforePageIndex === afterPageIndex)")
		assertContains(turnPage, "await this.view.goTo(fallbackPageTarget)")
		assertContains(turnPage, "page-turn:fixed-fallback")
	}

	@Test
	fun androidReaderMapsExplicitReadingFlowModesToFoliateRuntime() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(bridgeText, "readerFlowMode(settings)")
		assertContains(bridgeText, "ReaderFlowPagedVertical")
		assertContains(bridgeText, "ReaderFlowScrolledGaps")
		assertContains(bridgeText, "setAttribute('flow', readerFoliateFlow(flowMode))")
		assertContains(bridgeText, "--reader-scroll-gap")
		assertContains(bridgeText, "writing-mode: vertical-rl")
		assertContains(ebooksSettingsText, "readerFlowMode")
		assertContains(ebooksSettingsText, "PagedVertical(ReaderFlowPagedVertical")
		assertContains(ebooksSettingsText, "ScrollGaps(ReaderFlowScrolledGaps")
		assertContains(searchSettingsText, "readerFlowMode")
		assertContains(searchSettingsText, "ReaderSupportedFlowModes")
	}

	@Test
	fun androidReaderMapsExplicitReadingDirectionToFoliateRuntime() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(bridgeText, "readerDirectionMode(settings)")
		assertContains(bridgeText, "applyReaderDirection")
		assertContains(bridgeText, "this.view.book.dir")
		assertContains(bridgeText, "doc.documentElement")
		assertContains(readerScreenText, "Direction")
		assertContains(readerScreenText, "ReaderSupportedDirections")
		assertContains(readerScreenText, "state.settings.copy(direction = direction)")
		assertContains(ebooksSettingsText, "readerDirection")
		assertContains(ebooksSettingsText, "ReaderDirectionOption")
		assertContains(searchSettingsText, "ebooks.direction")
		assertContains(searchSettingsText, "ReaderSupportedDirections")
	}

	@Test
	fun androidReaderPackagesBundledFontSourcesForWebViewRendering() {
		val root = readerAssetRoot()
		val bridgeText = root.resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()
		val literata = root.resolve("fonts/navic-literata-regular.ttf")
		val atkinson = root.resolve("fonts/navic-atkinson-hyperlegible-regular.otf")
		val openDyslexic = root.resolve("fonts/navic-opendyslexic-regular.otf")

		assertTrue(literata.isFile, "Literata must be bundled for book font rendering")
		assertTrue(atkinson.isFile, "Atkinson Hyperlegible must be bundled for humanist font rendering")
		assertTrue(openDyslexic.isFile, "OpenDyslexic must be bundled for dyslexic font rendering")
		assertTrue(literata.length() > 16_000, "Literata asset should be a real font file")
		assertTrue(atkinson.length() > 16_000, "Atkinson asset should be a real font file")
		assertTrue(openDyslexic.length() > 16_000, "OpenDyslexic asset should be a real font file")
		assertContains(bridgeText, "readerFontFaceCss")
		assertContains(bridgeText, "@font-face")
		assertContains(bridgeText, "Navic Literata")
		assertContains(bridgeText, "Navic Atkinson Hyperlegible")
		assertContains(bridgeText, "Navic OpenDyslexic")
		assertContains(bridgeText, "fonts/navic-literata-regular.ttf")
		assertContains(bridgeText, "fonts/navic-atkinson-hyperlegible-regular.otf")
		assertContains(bridgeText, "fonts/navic-opendyslexic-regular.otf")
		assertContains(bridgeText, "ReaderFontSourceNavic")
		assertContains(bridgeText, "ReaderFontSourceSystem")
		assertContains(bridgeText, "ReaderFontSourcePublisher")
		assertContains(bridgeText, "readerFontFaceCss(settings)")
		assertContains(bridgeText, "readerEffectiveFontFamily(settings)")
		assertContains(bridgeText, "settings?.fontSource")
		assertContains(readerScreenText, "Font source")
		assertContains(readerScreenText, "ReaderSupportedFontSources")
		assertContains(ebooksSettingsText, "readerFontSource")
		assertContains(ebooksSettingsText, "ReaderFontSourceOption")
		assertContains(searchSettingsText, "ebooks.font-source")
		assertContains(searchSettingsText, "ReaderSupportedFontSources")
	}

	@Test
	fun commonReaderChromeExposesDimOverlayControl() {
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(readerScreenText, "ReaderDimOverlay")
		assertContains(readerScreenText, "matchParentSize()")
		assertContains(readerScreenText, "Color.Black.copy")
		assertContains(ebooksSettingsText, "readerDimOverlayPercent")
		assertContains(ebooksSettingsText, "option_ebook_reader_dim_overlay")
		assertContains(searchSettingsText, "ebooks.dim-overlay")
		assertContains(searchSettingsText, "readerDimOverlayPercent")
	}

	@Test
	fun androidReaderExposesKomikkuStyleOrientationControl() {
		val orientationEffectText = readerAndroidFile("ReaderOrientationEffect.android.kt").readText()
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(orientationEffectText, "SCREEN_ORIENTATION_FULL_SENSOR")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_SENSOR_PORTRAIT")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_SENSOR_LANDSCAPE")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_PORTRAIT")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_LANDSCAPE")
		assertContains(orientationEffectText, "SCREEN_ORIENTATION_REVERSE_PORTRAIT")
		assertContains(orientationEffectText, "activity.requestedOrientation = previousOrientation")
		assertContains(readerScreenText, "ReaderOrientationEffect(chromeState.settings.orientation)")
		assertContains(ebooksSettingsText, "readerOrientation")
		assertContains(ebooksSettingsText, "option_ebook_reader_orientation")
		assertContains(searchSettingsText, "ebooks.orientation")
		assertContains(searchSettingsText, "ReaderSupportedOrientations")
	}

	@Test
	fun commonReaderChromeExposesVolumeKeyPageTurnControl() {
		val readerScreenText = readerScreenFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsFile("SettingsSearchResults.kt").readText()

		assertContains(readerScreenText, "onPreviewKeyEvent")
		assertContains(readerScreenText, "Key.VolumeUp")
		assertContains(readerScreenText, "Key.VolumeDown")
		assertContains(readerScreenText, "volumeKeyPageTurns")
		assertContains(readerScreenText, "Volume keys")
		assertContains(ebooksSettingsText, "readerVolumeKeyPageTurns")
		assertContains(ebooksSettingsText, "option_ebook_reader_volume_keys")
		assertContains(searchSettingsText, "ebooks.volume-keys")
		assertContains(searchSettingsText, "readerVolumeKeyPageTurns")
	}

	@Test
	fun commonReaderChromeUsesKomikkuStyleOptionsSheetInsteadOfDockedSettingsList() {
		val readerScreenText = readerScreenFile().readText()
		val bottomChromeBody = readerScreenText.substringAfter("private fun ReaderBottomChrome(")
			.substringBefore("private fun ReaderKomikkuOptionsSheet(")

		assertContains(readerScreenText, "optionsVisible")
		assertContains(readerScreenText, "onToggleOptions: () -> Unit")
		assertContains(readerScreenText, "Icons.Filled.Settings")
		assertContains(readerScreenText, "ReaderKomikkuOptionsSheet(")
		assertContains(readerScreenText, "skipPartiallyExpanded = false")
		assertContains(readerScreenText, "BoxWithConstraints")
		assertContains(readerScreenText, "heightIn(max = maxHeight * 0.75f)")
		assertContains(readerScreenText, "ReaderOptionsTabChip")
		assertFalse(
			bottomChromeBody.contains("ReaderOptionsPanel("),
			"Bottom reader chrome must stay compact; settings belong in the Komikku-style modal sheet."
		)
	}

	@Test
	fun commonReaderOptionsUseKomikkuStyleChipGroups() {
		val readerScreenText = readerScreenFile().readText()
		val readingOptionsBody = readerScreenText.substringAfter("private fun ReaderReadingOptions(")
			.substringBefore("private fun ReaderGeneralOptions(")
		val generalOptionsBody = readerScreenText.substringAfter("private fun ReaderGeneralOptions(")
			.substringBefore("private fun ReaderMediaOptions(")

		assertContains(readerScreenText, "ReaderSettingsChipRow")
		assertContains(readerScreenText, "ReaderOptionChip")
		assertContains(readerScreenText, "ReaderToggleChip")
		assertContains(readerScreenText, "FilterChip(")
		assertContains(readerScreenText, "FlowRow(")
		assertContains(readingOptionsBody, "ReaderSupportedFlowModes")
		assertContains(readingOptionsBody, "ReaderSupportedDirections")
		assertContains(readingOptionsBody, "ReaderSupportedFontFamilies")
		assertContains(generalOptionsBody, "ReaderSupportedThemes")
		assertContains(generalOptionsBody, "ReaderSupportedOrientations")
		assertContains(generalOptionsBody, "ReaderSupportedTapZones")
		assertFalse(
			readingOptionsBody.contains("ReaderCycleRow(") || generalOptionsBody.contains("ReaderCycleRow("),
			"Reading and General reader options should use Komikku-style selectable chip groups instead of cyclic value rows."
		)
	}

	@Test
	fun commonReadaloudChromeSurfacesAudioMetadataLabels() {
		val readerScreenText = readerScreenFile().readText()
		val runtimeHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()

		assertContains(readerScreenText, "activeAudioLabel")
		assertContains(readerScreenText, "ReaderReadaloudMetadataLabel")
		assertContains(readerScreenText, "activeAudioMetadata")
		assertContains(readerScreenText, "Narrator")
		assertContains(readerScreenText, "Quality")
		assertContains(readerScreenText, "Source")
		assertContains(runtimeHostText, "activeAudioLabel =")
		assertContains(runtimeHostText, "activeLabelForPlaybackPosition")
		assertContains(runtimeHostText, "activeAudioMetadata =")
		assertContains(runtimeHostText, "metadataLabelsForPlaybackPosition")
	}

	@Test
	fun commonReadaloudChromeExposesPlaybackSpeedControls() {
		val readerScreenText = readerScreenFile().readText()
		val runtimeHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()

		assertContains(readerScreenText, "onReadaloudSpeedChange")
		assertContains(readerScreenText, "adjustSpeedCommand")
		assertContains(readerScreenText, "ReaderControlStepper(")
		assertContains(readerScreenText, "label = \"Speed\"")
		assertContains(runtimeHostText, "ReaderReadaloudPlaybackCommand.SetSpeed")
		assertContains(runtimeHostText, "controller.setPlaybackSpeed")
	}

	@Test
	fun commonReadaloudChromeExposesSyncHighlightToggle() {
		val readerScreenText = readerScreenFile().readText()
		val runtimeHostText = readerAndroidFile("ReaderReadaloudRuntimeHost.android.kt").readText()
		val preferenceText = readerCommonFile("ReaderPreferenceSettings.kt").readText()

		assertContains(readerScreenText, "onReadaloudSyncChange")
		assertContains(readerScreenText, "toggleSyncCommand")
		assertContains(readerScreenText, "Sync highlight")
		assertContains(readerScreenText, "readaloudSyncEnabled")
		assertContains(preferenceText, "readerReadaloudSyncEnabled")
		assertContains(runtimeHostText, "ReaderReadaloudPlaybackCommand.SetSyncEnabled")
		assertContains(runtimeHostText, "setSyncEnabled")
		assertContains(runtimeHostText, "readaloudSyncEnabled")
	}

	private fun readerAssetRoot(): File =
		listOf(
			File("src/androidMain/assets/reader"),
			File("composeApp/src/androidMain/assets/reader")
		).firstOrNull { it.isDirectory }
			?: error("Could not locate Android reader assets")

	private fun readerWebRuntimeFile(): File =
		listOf(
			File("src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt"),
			File("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebRuntime.kt")
		).firstOrNull { it.isFile }
			?: error("Could not locate Android reader WebView runtime")

	private fun readerWebViewHostFile(): File =
		listOf(
			File("src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt"),
			File("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt")
		).firstOrNull { it.isFile }
			?: error("Could not locate Android reader WebView host")

	private fun settingsFile(fileName: String): File =
		listOf(
			File("src/commonMain/kotlin/paige/navic/ui/screens/settings/$fileName"),
			File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/$fileName")
		).firstOrNull { it.isFile }
			?: error("Could not locate settings file $fileName")

	private fun readerScreenFile(): File =
		listOf(
			File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"),
			File("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt")
		).firstOrNull { it.isFile }
			?: error("Could not locate ReaderScreen.kt")

	private fun readerAndroidFile(fileName: String): File =
		listOf(
			File("src/androidMain/kotlin/paige/navic/ui/screens/reader/$fileName"),
			File("composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/$fileName")
		).firstOrNull { it.isFile }
			?: error("Could not locate Android reader file $fileName")

	private fun readerCommonFile(fileName: String): File =
		listOf(
			File("src/commonMain/kotlin/paige/navic/reader/$fileName"),
			File("composeApp/src/commonMain/kotlin/paige/navic/reader/$fileName")
		).firstOrNull { it.isFile }
			?: error("Could not locate common reader file $fileName")

	private fun File.hasPngAlphaChannel(): Boolean {
		val bytes = readBytes()
		require(bytes.size > 25) { "PNG file is too small: $this" }
		val pngSignature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
		require(bytes.take(8).toByteArray().contentEquals(pngSignature)) { "Not a PNG file: $this" }
		val colorType = bytes[25].toInt() and 0xff
		return colorType == 4 || colorType == 6
	}

	private fun File.averagePngAlpha(): Double {
		val image = ImageIO.read(this) ?: error("Could not read PNG file: $this")
		var total = 0L
		for (y in 0 until image.height) {
			for (x in 0 until image.width) {
				total += (image.getRGB(x, y) ushr 24) and 0xff
			}
		}
		return total.toDouble() / (image.width * image.height).toDouble()
	}
}
