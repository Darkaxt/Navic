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
		val bridgeHelpers = root.resolve("navic-reader-helpers.js")
		val bridgeSettings = root.resolve("navic-reader-settings.js")
		val foliatePackage = root.resolve("vendor/foliate-js/package.json")
		val foliateView = root.resolve("vendor/foliate-js/view.js")
		val foliateFixedLayout = root.resolve("vendor/foliate-js/fixed-layout.js")
		val foliatePdfAdapter = root.resolve("vendor/foliate-js/pdf.js")
		val pdfJs = root.resolve("vendor/foliate-js/vendor/pdfjs/pdf.js")
		val pdfJsWorker = root.resolve("vendor/foliate-js/vendor/pdfjs/pdf.worker.js")

		assertTrue(runtimeManifest.isFile, "reader runtime manifest must be packaged")
		assertTrue(index.isFile, "reader index.html must be packaged")
		assertTrue(bridge.isFile, "Navic reader bridge must be packaged")
		assertTrue(bridgeHelpers.isFile, "Navic reader helper module must be packaged")
		assertTrue(bridgeSettings.isFile, "Navic reader settings module must be packaged")
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
		val bridgeText = readerBridgeText(root)
		assertContains(bridgeText, "window.NavicReaderBridge")
		assertContains(bridgeText, "selectionChanged")
		assertContains(bridgeText, "applyOverlayFragment")
		assertContains(bridgeText, "applyHighlights")
		assertContains(bridgeText, "publicationReady")
		assertContains(bridgeText, "overlayFragmentActive")
		assertContains(bridgeText, "normalizeSearchResult")
		assertContains(bridgeText, "sectionTitle")
		assertContains(bridgeText, "postToc")
		assertContains(bridgeText, "flattenTocItems")
		assertContains(bridgeText, "type: 'toc'")
		assertContains(bridgeText, "margin-inline")
		assertContains(bridgeText, "[NavicReader]")
		assertContains(bridgeText, "openPublication:start")
		assertContains(bridgeText, "reportError")
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
	fun adbReaderSmokeCapturesFocusedReaderDiagnostics() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()

		assertContains(scriptText, "[switch] \$CaptureReaderDiagnostics")
		assertContains(scriptText, "surface-texture-scroll")
		assertContains(scriptText, "surface-texture-update")
		assertContains(scriptText, "Reader surface touch down")
		assertContains(scriptText, "Reader surface tap action=")
		assertContains(scriptText, "Reader bridge raw")
		assertContains(scriptText, "readerContentTapHandled")
		assertContains(scriptText, "reader-diagnostics-summary.txt")
		assertContains(scriptText, "reader-texture-diagnostics.log")
		assertContains(scriptText, "reader-touch-diagnostics.log")
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
			"width: layoutWidth",
			message = "PDF pages must expose natural layout dimensions before fixed-layout paint"
		)
		assertContains(foliatePdfAdapterText, "height: layoutHeight")
		assertContains(foliatePdfAdapterText, "pixelWidth: pageWidth")
		assertContains(foliatePdfAdapterText, "pixelHeight: pageHeight")
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
	fun androidPdfRuntimeUsesNaturalLayoutBoxAndPrefetchesAdjacentPages() {
		val root = readerAssetRoot()
		val foliateFixedLayoutText = root.resolve("vendor/foliate-js/fixed-layout.js").readText()
		val foliatePdfAdapterText = root.resolve("vendor/foliate-js/pdf.js").readText()

		assertContains(foliatePdfAdapterText, "const PdfPageFitWidthRatio = 0.94")
		assertContains(foliatePdfAdapterText, "const layoutWidth = naturalPdfSize.width")
		assertContains(foliatePdfAdapterText, "const layoutHeight = naturalPdfSize.height")
		assertContains(foliatePdfAdapterText, "pixelWidth: pageWidth")
		assertContains(foliatePdfAdapterText, "pixelHeight: pageHeight")
		assertContains(foliatePdfAdapterText, "fitWidthRatio: PdfPageFitWidthRatio")
		assertContains(foliatePdfAdapterText, "cache.set(i, loadPromise)")
		assertContains(foliatePdfAdapterText, "prefetchPdfPage(i + 1)")
		assertContains(foliatePdfAdapterText, "prefetchPdfPage(i - 1)")
		assertContains(foliatePdfAdapterText, "if (PdfDiagnosticsEnabled) logCanvasBitmap")
		assertContains(foliateFixedLayoutText, "fitWidthRatio: srcOption?.fitWidthRatio")
		assertContains(foliateFixedLayoutText, "const fitWidthRatio = normalizedFitWidthRatio(target.fitWidthRatio)")
		assertContains(foliateFixedLayoutText, "const viewportFitWidth = viewportWidth * fitWidthRatio")
	}

	@Test
	fun androidPaginatorKeepsEpubIframesInsideVisibleViewport() {
		val root = readerAssetRoot()
		val bridgeText = readerBridgeText(root)
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

}
