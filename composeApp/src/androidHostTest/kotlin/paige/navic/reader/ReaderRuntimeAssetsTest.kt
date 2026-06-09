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
}
