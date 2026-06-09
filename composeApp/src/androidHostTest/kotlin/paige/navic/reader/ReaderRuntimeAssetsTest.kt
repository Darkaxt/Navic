package paige.navic.reader

import java.io.File
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
	fun androidReaderStylesEbookHyperlinksAsInlineFastForwardAffordances() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()

		assertContains(bridgeText, "a:any-link")
		assertContains(bridgeText, "color: inherit !important")
		assertContains(bridgeText, "text-decoration: none !important")
		assertFalse(
			bridgeText.contains("content: ' >>'"),
			"Hyperlinks must not expose the literal ASCII fast-forward marker."
		)
		assertContains(bridgeText, "content: ' »'")
		assertContains(bridgeText, "vertical-align: sub")
		assertContains(bridgeText, "font-size: 0.72em")
		assertContains(bridgeText, "closestElement")
		assertContains(bridgeText, "parentElement?.closest")
		assertContains(bridgeText, "attachLinkNavigation")
		assertContains(bridgeText, "closestElement(event.target, 'a[href]')")
		assertContains(bridgeText, "event.stopPropagation()")
		assertContains(bridgeText, "await this.goTo(href)")
		assertContains(bridgeText, "link:navigate")
	}

	@Test
	fun androidReaderInjectsThemeColorsIntoPublicationDocuments() {
		val bridgeText = readerAssetRoot().resolve("navic-reader.js").readText()

		assertContains(bridgeText, "const palette = readerThemePalette(settings.theme)")
		assertContains(bridgeText, "this.readerSettings = settings")
		assertContains(bridgeText, "this.applyDocumentTheme(doc, settings)")
		assertContains(bridgeText, "applyThemeToLoadedContent")
		assertContains(bridgeText, "applyDocumentTheme(doc, settings)")
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
		assertContains(bridgeText, "closestElement(event.target, 'img')")
		assertContains(bridgeText, "data-navic-sepia-overlay")
		assertContains(bridgeText, "img:not([data-navic-sepia-overlay=\"off\"])")
		assertContains(bridgeText, "background-color: var(--reader-background) !important")
		assertContains(bridgeText, "mix-blend-mode: multiply")
		assertContains(bridgeText, "mix-blend-mode: normal !important")
		assertContains(bridgeText, "event.stopImmediatePropagation()")
		assertContains(bridgeText, "image:sepia-overlay")
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
		assertContains(bridgeText, "settings.publisherStyles === true")
		assertContains(bridgeText, "paragraphSpacingPercent")
		assertContains(ebooksSettingsText, "readerParagraphSpacingPercent")
		assertContains(ebooksSettingsText, "readerPublisherStylesEnabled")
		assertContains(ebooksSettingsText, "option_ebook_reader_paragraph_spacing")
		assertContains(ebooksSettingsText, "option_ebook_reader_publisher_styles")
		assertContains(searchSettingsText, "ebooks.paragraph-spacing")
		assertContains(searchSettingsText, "ebooks.publisher-styles")
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
}
