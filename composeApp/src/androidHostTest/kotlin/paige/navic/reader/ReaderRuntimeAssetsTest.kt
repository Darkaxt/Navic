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
	fun androidReaderRuntimeAcknowledgesSuccessfulTrackedCommandsAndDeduplicatesTheirIds() {
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val dispatchBlock = runtimeText
			.substringAfter("const acknowledgedCommandIds")
			.substringBefore("  armNativePageTurnSettle:")

		assertContains(dispatchBlock, "new Set()")
		assertContains(dispatchBlock, "const commandId = typeof command?.commandId === 'string'")
		assertContains(dispatchBlock, "acknowledgedCommandIds.has(commandId)")
		assertContains(dispatchBlock, "post({ type: 'commandAck', commandId })")
		assertContains(dispatchBlock, "Promise.resolve(result)")
		assertContains(dispatchBlock, ".then(value => {")
		assertContains(dispatchBlock, "acknowledgedCommandIds.add(commandId)")
		assertContains(dispatchBlock, ".finally(() => {")
		assertTrue(
			dispatchBlock.indexOf("runtime.dispatch(command)") < dispatchBlock.indexOf("acknowledgedCommandIds.add(commandId)"),
			"A tracked command must be acknowledged only after its runtime dispatch completes successfully."
		)
		assertTrue(
			dispatchBlock.indexOf("acknowledgedCommandIds.has(commandId)") < dispatchBlock.indexOf("runtime.dispatch(command)"),
			"A duplicate tracked command must be acknowledged without executing its runtime command again."
		)
	}

	@Test
	fun androidReaderAssetsPackageFoliateRuntimeAndNavicBridge() {
		val root = readerAssetRoot()
		val runtimeManifest = root.resolve("runtime.json")
		val index = root.resolve("index.html")
		val bridge = root.resolve("navic-reader.js")
		val bridgeCore = root.resolve("navic-reader-bridge-core.js")
		val bridgeHelpers = root.resolve("navic-reader-helpers.js")
		val bridgeSettingsCore = root.resolve("navic-reader-settings-core.js")
		val bridgeSettings = root.resolve("navic-reader-settings.js")
		val bridgeMedia = root.resolve("navic-reader-media.js")
		val bridgeMediaOverlay = root.resolve("navic-reader-media-overlay.js")
		val bridgeIdentity = root.resolve("navic-reader-identity.js")
		val bridgePaginationModel = root.resolve("navic-reader-pagination-model.js")
		val bridgeMotion = root.resolve("navic-reader-motion.js")
		val bridgePageTurns = root.resolve("navic-reader-page-turns.js")
		val bridgeContentInteractions = root.resolve("navic-reader-content-interactions.js")
		val bridgePaginatorCommit = root.resolve("navic-reader-paginator-commit.js")
		val bridgePagination = root.resolve("navic-reader-pagination.js")
		val bridgeAppearance = root.resolve("navic-reader-appearance.js")
		val bridgeShellCover = root.resolve("navic-reader-shell-cover.js")
		val bridgeViewport = root.resolve("navic-reader-viewport.js")
		val bridgeLocation = root.resolve("navic-reader-location.js")
		val bridgeBaselineHmac = root.resolve("navic-reader-baseline-hmac.js")
		val foliatePackage = root.resolve("vendor/foliate-js/package.json")
		val foliateView = root.resolve("vendor/foliate-js/view.js")
		val foliateFixedLayout = root.resolve("vendor/foliate-js/fixed-layout.js")
		val foliatePdfAdapter = root.resolve("vendor/foliate-js/pdf.js")
		val pdfJs = root.resolve("vendor/foliate-js/vendor/pdfjs/pdf.js")
		val pdfJsWorker = root.resolve("vendor/foliate-js/vendor/pdfjs/pdf.worker.js")

		assertTrue(runtimeManifest.isFile, "reader runtime manifest must be packaged")
		assertTrue(index.isFile, "reader index.html must be packaged")
		assertTrue(bridge.isFile, "Navic reader bridge must be packaged")
		assertTrue(bridgeCore.isFile, "Navic reader bridge core module must be packaged")
		assertTrue(bridgeHelpers.isFile, "Navic reader helper module must be packaged")
		assertTrue(bridgeSettingsCore.isFile, "Navic reader settings core module must be packaged")
		assertTrue(bridgeSettings.isFile, "Navic reader settings module must be packaged")
		assertTrue(bridgeMedia.isFile, "Navic reader media tap module must be packaged")
		assertTrue(bridgeMediaOverlay.isFile, "Navic reader media-overlay module must be packaged")
		assertTrue(bridgeIdentity.isFile, "Navic reader identity module must be packaged")
		assertTrue(bridgePaginationModel.isFile, "Navic reader pagination model module must be packaged")
		assertTrue(bridgeMotion.isFile, "Navic reader motion module must be packaged")
		assertTrue(bridgePageTurns.isFile, "Navic reader page-turn module must be packaged")
		assertTrue(bridgeContentInteractions.isFile, "Navic reader content-interaction module must be packaged")
		assertTrue(bridgePaginatorCommit.isFile, "Navic reader paginator-commit module must be packaged")
		assertTrue(bridgePagination.isFile, "Navic reader pagination module must be packaged")
		assertTrue(bridgeAppearance.isFile, "Navic reader appearance module must be packaged")
		assertTrue(bridgeShellCover.isFile, "Navic reader shell-cover module must be packaged")
		assertTrue(bridgeViewport.isFile, "Navic reader viewport module must be packaged")
		assertTrue(bridgeLocation.isFile, "Navic reader location module must be packaged")
		assertTrue(bridgeBaselineHmac.isFile, "Navic reader baseline HMAC module must be packaged")
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
		assertContains(bridgeText, "./navic-reader-baseline-hmac.js")
		assertContains(bridgeText, "./navic-reader-paginator-commit.js")
		assertContains(bridgePaginatorCommit.readText(), "readerCommitTextPage")
		assertContains(bridgeBaselineHmac.readText(), "generateKey(ReaderBaselineHmacAlgorithm, false, ['sign'])")
		assertContains(bridgeText, "window.NavicReaderBridge")
		assertContains(bridgeText, "selectionChanged")
		assertContains(bridgeText, "applyOverlayFragment")
		assertContains(bridgeText, "highlightMediaOverlayTextRange")
		assertContains(bridgeText, "textStart")
		assertContains(bridgeText, "textEnd")
		assertContains(bridgeText, "applyHighlights")
		assertContains(bridgeText, "publicationReady")
		assertContains(bridgeText, "overlayFragmentActive")
		assertContains(bridgeText, "activeMediaOverlaySnapshot")
		assertContains(bridgeText, "mediaOverlayActiveFragment ? { ...runtime.mediaOverlayActiveFragment } : null")
		assertContains(bridgeText, "normalizeSearchResult")
		assertContains(bridgeText, "sectionTitle")
		assertContains(bridgeText, "postToc")
		assertContains(bridgeText, "flattenTocItems")
		assertContains(bridgeText, "type: 'toc'")
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
	fun androidReaderHelpersAreSplitIntoFocusedSupportModules() {
		val root = readerAssetRoot()
		val helper = root.resolve("navic-reader-helpers.js")
		val helperText = helper.readText()

		assertTrue(
			helper.readLines().size <= 1_800,
			"navic-reader-helpers.js should stay below 1800 lines; settings and media-tap contracts belong in focused modules."
		)
		listOf(
			"navic-reader-bridge-core.js",
			"navic-reader-settings-core.js",
			"navic-reader-media.js",
			"navic-reader-identity.js",
			"navic-reader-pagination-model.js",
			"navic-reader-typography.js"
		).forEach { fileName ->
			val module = root.resolve(fileName)
			assertTrue(module.isFile, "$fileName must exist so helper changes stay focused.")
			assertContains(helperText, "./$fileName")
			assertContains(module.readText(), "export const")
		}
	}

	@Test
	fun androidReaderRuntimeIsSplitIntoFocusedBridgeModules() {
		val root = readerAssetRoot()
		val bridge = root.resolve("navic-reader.js")
		val bridgeText = bridge.readText()
		val lineCount = bridge.readLines().size

		assertTrue(
			lineCount < 2_400,
			"navic-reader.js should stay below 2400 lines; risky listener/pagination work belongs in focused bridge modules."
		)
		listOf(
			"navic-reader-motion.js",
			"navic-reader-page-turns.js",
			"navic-reader-content-interactions.js",
			"navic-reader-pagination.js",
			"navic-reader-appearance.js"
		).forEach { fileName ->
			val module = root.resolve(fileName)
			assertTrue(module.isFile, "$fileName must exist so GLM/Codex do not have to edit the whole bridge.")
			assertContains(bridgeText, "./$fileName")
			assertContains(module.readText(), "export const")
		}
		assertContains(bridgeText, "Object.assign(NavicReaderRuntime.prototype")
	}

	@Test
	fun androidReaderEntrypointKeepsRuntimeMethodGroupsSplit() {
		val root = readerAssetRoot()
		val bridge = root.resolve("navic-reader.js")
		val bridgeText = bridge.readText()

		assertTrue(
			bridge.readLines().size <= 1_650,
			"navic-reader.js should stay below 1650 lines; shell-cover, viewport, and location behavior belong in focused method modules."
		)
		listOf(
			"navic-reader-media-overlay.js",
			"navic-reader-shell-cover.js",
			"navic-reader-viewport.js",
			"navic-reader-location.js"
		).forEach { fileName ->
			val module = root.resolve(fileName)
			assertTrue(module.isFile, "$fileName must exist so runtime entrypoint changes stay focused.")
			assertContains(bridgeText, "./$fileName")
			assertContains(module.readText(), "export const NavicReader")
		}
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
	fun androidReaderWebViewRuntimeUsesNormalCacheForBundledAssets() {
		val runtimeText = readerWebRuntimeFile().readText()

		assertContains(
			runtimeText,
			"cacheMode = WebSettings.LOAD_DEFAULT",
			message = "APK-backed appassets should use normal WebView caching across renderer generations."
		)
		assertFalse(
			runtimeText.contains("WebSettings.LOAD_NO_CACHE"),
			"Reader configuration must not force every local asset request to bypass WebView cache."
		)
		assertFalse(
			runtimeText.contains("webView.clearCache(true)"),
			"Reader configuration must not clear process-global WebView cache."
		)
	}

	@Test
	fun androidReaderWebViewDebuggingIsControlledByDeveloperSetting() {
		val hostText = readerEngineWebViewHostFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val developerSettingsText = settingsFile("DeveloperScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()

		assertContains(hostText, "enableDebugging = settings.webContentsDebuggingEnabled == true")
		assertFalse(
			ebooksSettingsText.contains("option_ebook_reader_web_debugging"),
			"WebView debugging is a developer diagnostic and should not be shown in Ebook reader settings."
		)
		assertContains(developerSettingsText, "readerWebContentsDebuggingEnabled")
		assertContains(developerSettingsText, "option_ebook_reader_web_debugging")
		assertContains(searchSettingsText, "developer.web-debugging")
		assertFalse(
			searchSettingsText.contains("ebooks.web-debugging"),
			"Settings search should route WebView debugging to Developer Options, not Ebooks."
		)
		assertContains(searchSettingsText, "readerWebContentsDebuggingEnabled")
	}

	@Test
	fun settingsSearchResultsStaysFocusedOnRenderingSearchResults() {
		val searchResults = settingsFile("SettingsSearchResults.kt")
		val lineCount = searchResults.readLines().size

		assertTrue(
			lineCount <= 300,
			"SettingsSearchResults.kt should stay as the search renderer; section-specific row registries belong in focused SettingsSearch* files."
		)
		assertTrue(settingsFile("SettingsSearchAppearanceRows.kt").isFile)
		assertTrue(settingsFile("SettingsSearchPlaybackRows.kt").isFile)
		assertTrue(settingsFile("SettingsSearchEbookRows.kt").isFile)
		assertTrue(settingsFile("SettingsSearchStorageRows.kt").isFile)
		assertTrue(settingsFile("SettingsSearchIntegrationRows.kt").isFile)
		assertTrue(settingsFile("SettingsSearchDeveloperRows.kt").isFile)
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
		assertContains(scriptText, "[string[]] \$RequireReaderBridgeEvent = @()")
		assertContains(scriptText, "[string] \$ReaderDevtoolsProbe")
		assertContains(scriptText, "adb-webview-eval.mjs")
		assertContains(scriptText, "reader-devtools-probe.json")
		assertContains(scriptText, "internal-link-native")
		assertContains(scriptText, "phase3-events")
		assertContains(scriptText, "external-link-prompt")
		assertContains(scriptText, "history-controls")
		assertContains(scriptText, "selection-payload")
		assertContains(scriptText, "relocation-payload")
		assertContains(scriptText, "page-box")
		assertContains(scriptText, "visible-page-content")
		assertContains(scriptText, "font-size-publisher-styles")
		assertContains(scriptText, "chapter-progress-endpoints")
		assertContains(scriptText, "whispersync-audio-follow")
		assertContains(scriptText, "whispersync-char-offset-overlay")
		assertContains(scriptText, "reader-bridge-events.log")
		assertContains(scriptText, "requiredBridgeEvents=")
		assertContains(scriptText, "Reader bridge event: \$requiredBridgeEvent")
		assertContains(scriptText, "required bridge event '\$requiredBridgeEvent' was not captured")
		assertContains(scriptText, "function Get-TextFileRaw")
		assertContains(scriptText, "function Test-TextMatches")
		assertContains(scriptText, "\$bridgeDiagnosticsText = \$bridgeDiagnosticLines -join \"`n\"")
		assertContains(scriptText, "bridgeEvent:\$requiredBridgeEvent=\$(Test-TextMatches")
		assertContains(scriptText, "-not (Test-TextMatches -Text \$bridgeDiagnosticsText")
		assertFalse(
			scriptText.contains("\$bridgeDiagnosticsText -notmatch"),
			"Bridge event validation must not use raw -notmatch because empty Get-Content -Raw output can skip failure paths."
		)
		assertFalse(
			scriptText.contains("\$bridgeDiagnosticsText -match"),
			"Bridge event diagnostics must use Test-TextMatches so empty logs are treated as empty strings."
		)
		assertContains(scriptText, "expectedLogLabels")
		assertContains(scriptText, "ConvertFrom-Json")
		assertContains(scriptText, "Reader DevTools probe '\$ProbeName' expected log label")
		assertContains(scriptText, "readerContentTapHandled")
		assertContains(scriptText, "reader-diagnostics-summary.txt")
		assertContains(scriptText, "reader-texture-diagnostics.log")
		assertContains(scriptText, "reader-touch-diagnostics.log")
		assertContains(scriptText, "textureScrollLines=\$(@(\$textureLines | Where-Object")
		assertContains(scriptText, "textureUpdateLines=\$(@(\$textureLines | Where-Object")
		assertContains(scriptText, "[string] \$DeviceSerial")
		assertContains(scriptText, "\$env:ANDROID_SERIAL = \$DeviceSerial")
		assertContains(scriptText, "\$previousErrorActionPreference = \$ErrorActionPreference")
		assertContains(scriptText, "\$ErrorActionPreference = \"Continue\"")
		assertContains(scriptText, "\$output = & adb @Arguments 2>&1")
		assertContains(scriptText, "Invoke-Adb @(\"shell\", \"monkey\", \"-p\", \$Package, \"1\")")
		assertContains(scriptText, "[string[]] \$Lines = @()")
		assertContains(scriptText, "return @(\$samples.ToArray())")
	}

	@Test
	fun adbReaderSmokeVerifiesTwentyExactTurnsThroughAnExclusivePrivacySafeProbe() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val projectionText = repoFile(
			"tools/reader-harness/src/paginator-commit-receipt-acceptance.mjs"
		).readText()
		val receiptProjection = projectionText.substringBefore("const PaginatorNativeTapActions")
		val acceptance = helperText
			.substringAfter("async function runPaginatorCommitReceiptsProbe(page)")
			.substringBefore("async function main()")
		val smokeAcceptance = scriptText
			.substringAfter("if (\$VerifyPaginatorCommitReceipts) {")
			.substringBefore("if (\$TapPreset -eq \"ReaderHorizontalZones\")")

		assertContains(scriptText, "[switch] \$VerifyPaginatorCommitReceipts")
		assertContains(scriptText, "[switch] \$RequirePaginatorChapterTransition")
		assertContains(scriptText, "requires -PrivacySafeEvidence")
		assertContains(scriptText, "requires -PreserveLogcat")
		assertContains(scriptText, "exclusive privacy-safe acceptance mode")
		assertContains(smokeAcceptance, "acceptedForwardTurns = 20")
		assertContains(smokeAcceptance, "paginator-commit-receipts.json")
		assertContains(smokeAcceptance, "\$artifactFiles.Count -ne 1")
		assertContains(helperText, "'paginator-commit-receipts': runPaginatorCommitReceiptsProbe")
		assertContains(acceptance, "window.__navicReaderTrace = sink")
		assertContains(acceptance, "pageTurnRasterPreparationPlan?.(authoritativePageIndex)")
		assertContains(acceptance, "readPaginatorNativeEventSnapshot(nativePid)")
		assertContains(acceptance, "committedNativeTurn")
		assertContains(acceptance, "completedNativeRelocation")
		assertContains(acceptance, "shouldRetryPaginatorWarmup({")
		assertContains(acceptance, "for (let settlement = 1; settlement <= expectedCount")
		assertContains(acceptance, "runAdb(['shell', 'input', 'tap'")
		assertContains(acceptance, "receipt.pageIndex === state.context?.currentPageIndex")
		assertContains(acceptance, "state.pendingStatePresent === false")
		assertContains(acceptance, "state.settledStatePresent === false")
		assertContains(acceptance, "accepted.pageIndex !== intendedPageIndex")
		assertContains(acceptance, "chapterTransitions.push")
		assertContains(projectionText, "entry?.type !== 'page-turn:exact-settled'")
		assertContains(projectionText, "projectPaginatorNativeLogLine(line)")
		assertContains(projectionText, "state?.committedNativeTurn !== true")
		assertContains(helperText, "'KomikkuReaderNativeFrameHost:I'")
		assertContains(helperText, "'ReaderPlayLikeCurlFoliate:I'")
		assertContains(projectionText, "{ state: 'accepted', pageIndex }")
		assertFalse(receiptProjection.contains("href"))
		assertFalse(receiptProjection.contains("cfi"))
		assertFalse(receiptProjection.contains("title"))
		assertFalse(receiptProjection.contains("text"))
		assertFalse(receiptProjection.contains("url"))
	}

	@Test
	fun adbWebViewEvalHelperInjectsReaderBridgeEventsThroughDevTools() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()

		assertContains(helperText, "/json/list")
		assertContains(helperText, "new WebSocket")
		assertContains(helperText, "Runtime.evaluate")
		assertContains(helperText, "webview_devtools_remote_")
		assertContains(helperText, "adb forward")
		assertContains(helperText, "internal-link-native")
		assertContains(helperText, "phase3-events")
		assertContains(helperText, "external-link-prompt")
		assertContains(helperText, "history-controls")
		assertContains(helperText, "selection-payload")
		assertContains(helperText, "relocation-payload")
		assertContains(helperText, "visible-range")
		assertContains(helperText, "whispersync-audio-follow")
		assertContains(helperText, "whispersync-char-offset-overlay")
		assertContains(helperText, "runtime-state")
		assertContains(helperText, "page-box")
		assertContains(helperText, "chapter-progress-endpoints")
		assertContains(helperText, "native-drag-preview-texture")
		assertContains(helperText, "runNativeDragPreviewTextureProbe")
		assertContains(helperText, "data-navic-page-drag-preview-paper-layer")
		assertContains(helperText, "data-navic-page-drag-preview-border-layer")
		assertContains(helperText, "data-navic-page-drag-preview-texture-surface")
		assertContains(helperText, "NavicReaderBridge.dispatch")
		assertContains(helperText, "type: 'diagnosticLocationSnapshot'")
		assertContains(helperText, "new CustomEvent('link'")
		assertContains(helperText, "new CustomEvent('external-link'")
		assertContains(helperText, "new CustomEvent('draw-annotation'")
		assertContains(helperText, "new CustomEvent('show-annotation'")
		assertContains(helperText, "new CustomEvent('create-overlay'")
		assertContains(helperText, "type: 'diagnosticScrolledEdgePullUp'")
		assertContains(helperText, "diagnosticScrolledEdgePullUp did not post pullUp")
		assertContains(helperText, "Reader bridge event: pullUp")
		assertContains(helperText, "selectionchange")
		assertContains(helperText, "Reader bridge event: locationChanged")
		assertContains(helperText, "Reader bridge event: visibleTextRange")
		assertContains(helperText, "source=media-overlay-follow")
		assertContains(helperText, "defaultPrevented")
		assertContains(helperText, "native-short-tap")
	}

	@Test
	fun adbWebViewEvalChapterProgressProbeUsesExactNativeRailTargets() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val strongestProbe = helperText
			.substringAfter("async function runChapterProgressEndpointsProbe(page)")
			.substringBefore("async function runLocationSnapshotProbe(page)")
		val currentProbe = helperText
			.substringAfter("async function runCurrentChapterProgressEndpointsProbe(page)")
			.substringBefore("async function runPageBoxProbe(page)")

		assertContains(strongestProbe, "chapterPageIndex,")
		assertContains(strongestProbe, "chapterPageCount,")
		assertContains(currentProbe, "chapterPageIndex,")
		assertContains(currentProbe, "chapterPageCount,")
		assertContains(
			strongestProbe,
			"endpoint(href, 0, 0, bestCandidate.chapterPageCount)",
			message = "The strongest-candidate probe must exercise the same exact first-page rail command as native UI."
		)
		assertContains(
			strongestProbe,
			"endpoint(href, 1, bestCandidate.chapterPageCount - 1, bestCandidate.chapterPageCount)",
			message = "The strongest-candidate probe must exercise the same exact last-page rail command as native UI."
		)
		assertContains(
			currentProbe,
			"endpoint(href, 0, 0, initialChapterPageCount)",
			message = "The current-chapter probe must exercise the same exact first-page rail command as native UI."
		)
		assertContains(
			currentProbe,
			"endpoint(href, 1, initialChapterPageCount - 1, initialChapterPageCount)",
			message = "The current-chapter probe must exercise the same exact last-page rail command as native UI."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanProbeExternalLinkPromptWithoutPhase3SideEffects() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val externalPromptProbe = helperText
			.substringAfter("async function runExternalLinkPromptProbe(page)")
			.substringBefore("async function runAnnotationRoundTripProbe(page)")

		assertContains(helperText, "'external-link-prompt': runExternalLinkPromptProbe")
		assertContains(externalPromptProbe, "probe: 'external-link-prompt'")
		assertContains(externalPromptProbe, "https://example.test/navic-external-prompt")
		assertContains(externalPromptProbe, "new CustomEvent('external-link'")
		assertContains(externalPromptProbe, "Reader bridge event: externalLink")
		assertFalse(
			externalPromptProbe.contains("new CustomEvent('show-annotation'"),
			"The external-link prompt probe must not leave the native UI on the annotation popup."
		)
		assertFalse(
			externalPromptProbe.contains("diagnosticScrolledEdgePullUp"),
			"The external-link prompt probe must not toggle pull-up/menu state while proving the link dialog."
		)
	}

	@Test
	fun adbReaderSmokeAllowsEmptyDevtoolsProbeNames() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val probeFunction = scriptText
			.substringAfter("function Invoke-ReaderDevtoolsProbe")
			.substringBefore("\nInvoke-ReaderDevtoolsProbe -ProbeName")
		val assertFunction = scriptText
			.substringAfter("function Assert-ReaderDevtoolsProbeLogLabels")
			.substringBefore("\nAssert-ReaderDevtoolsProbeLogLabels -ProbeName")

		assertContains(probeFunction, "if ([string]::IsNullOrWhiteSpace(${'$'}ProbeName))")
		assertFalse(
			probeFunction.contains("[Parameter(Mandatory = ${'$'}true)]\r\n        [string] ${'$'}ProbeName") ||
				probeFunction.contains("[Parameter(Mandatory = ${'$'}true)]\n        [string] ${'$'}ProbeName"),
			"Reader smoke steps without DevTools probes must not fail before Invoke-ReaderDevtoolsProbe can skip an empty ProbeName."
		)
		assertContains(assertFunction, "if ([string]::IsNullOrWhiteSpace(${'$'}ProbeName))")
		assertFalse(
			assertFunction.contains("[Parameter(Mandatory = ${'$'}true)]\r\n        [string] ${'$'}ProbeName") ||
				assertFunction.contains("[Parameter(Mandatory = ${'$'}true)]\n        [string] ${'$'}ProbeName"),
			"Reader smoke steps without DevTools probes must not fail before Assert-ReaderDevtoolsProbeLogLabels can skip an empty ProbeName."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanProbeWhispersyncAudioFollowVisibleRangeSource() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val probe = helperText
			.substringAfter("async function runWhispersyncAudioFollowProbe(page)")
			.substringBefore("async function runChapterProgressEndpointsProbe(page)")

		assertContains(scriptText, "whispersync-audio-follow")
		assertContains(helperText, "'whispersync-audio-follow': runWhispersyncAudioFollowProbe")
		assertContains(probe, "probe: 'whispersync-audio-follow'")
		assertContains(probe, "type: 'applyOverlayFragment'")
		assertContains(probe, "textStart")
		assertContains(probe, "textEnd")
		assertContains(probe, "reason: 'media-overlay-follow'")
		assertContains(probe, "visibleTextRange")
		assertContains(probe, "visibleRange.source !== 'media-overlay-follow'")
		assertContains(probe, "media-overlay-follow:already-visible")
		assertContains(probe, "overlayFragmentActive")
		assertContains(probe, "source=media-overlay-follow")
	}

	@Test
	fun androidReaderDoesNotLetMediaOverlayFollowInterruptUserRelocation() {
		val bridgeText = readerBridgeText()
		val applyOverlayFragment = bridgeText
			.substringAfter("async applyOverlayFragment(fragment) {")
			.substringBefore("\n  updateOverlayFragmentProgress")

		assertContains(bridgeText, "mediaOverlayFollowShouldDeferForUserRelocation()")
		assertContains(applyOverlayFragment, "this.mediaOverlayFollowShouldDeferForUserRelocation()")
		assertContains(applyOverlayFragment, "media-overlay-follow:deferred")
		assertFalse(
			applyOverlayFragment.contains("await this.goTo(targetHref, 'media-overlay-follow')"),
			"Playback-driven media-overlay follow must not start a relocation from the highlight application path."
		)
	}

	@Test
	fun androidReaderDoesNotNavigateForAlreadyVisibleMediaOverlayTextRange() {
		val bridgeText = readerBridgeText()
		val applyOverlayFragment = bridgeText
			.substringAfter("async applyOverlayFragment(fragment) {")
			.substringBefore("\n  updateOverlayFragmentProgress")
		val locationText = readerAssetRoot().resolve("navic-reader-location.js").readText()

		assertContains(locationText, "function currentVisibleTextRangeForHref(href = '', preferredDomRange = null)")
		assertContains(locationText, "readerVisibleTextRangeForDocument(content.doc)")
		assertContains(locationText, "readerHrefMatches(textHref, targetHref)")
		assertContains(locationText, "const textHref = section?.href || content?.href || targetHref")
		assertContains(bridgeText, "mediaOverlayFragmentAlreadyVisible(fragment)")
		assertContains(applyOverlayFragment, "this.mediaOverlayFragmentAlreadyVisible(fragment)")
		assertContains(applyOverlayFragment, "media-overlay-follow:already-visible")
		assertFalse(
			applyOverlayFragment.contains("await this.goTo(targetHref, 'media-overlay-follow')"),
			"Character-range media-overlay cues that are already visible must be highlighted in place instead of forcing a section-level media-overlay relocation."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanProbeWhispersyncPageScopedControl() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val probe = helperText
			.substringAfter("async function runWhispersyncPageScopedControlProbe(page)")
			.substringBefore("async function runChapterProgressEndpointsProbe(page)")

		assertContains(scriptText, "whispersync-page-scoped-control")
		assertContains(helperText, "'whispersync-page-scoped-control': runWhispersyncPageScopedControlProbe")
		assertContains(probe, "probe: 'whispersync-page-scoped-control'")
		assertContains(probe, "OEBPS/xhtml/Authorforeword.xhtml")
		assertContains(probe, "OEBPS/xhtml/mini_toc.xhtml")
		assertContains(probe, "page-scoped-control-cue-covered")
		assertContains(probe, "page-scoped-control-unsupported")
		assertContains(probe, "visibleTextRange")
		assertContains(probe, "waitForTargetVisibleRange")
		assertContains(probe, "snapshotAttempt")
		assertContains(probe, "expectedLogLabels")
		assertContains(probe, "Whispersync audiobook seek")
		assertContains(probe, "Dispatching reader engine command: clearOverlay")
		assertFalse(
			probe.contains("await settleFrames(8)\n      const snapshot"),
			"Page-scoped Whispersync smoke navigation must wait for the requested href, not sample stale saved state after a fixed frame count."
		)
		assertFalse(
			probe.contains("await Promise.resolve(readerBridgeDispatch({\n        type: 'goToHref'"),
			"Page-scoped smoke navigation must not await goToHref; Foliate may emit loadDoc without settling the navigation promise."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanProbeWhispersyncCompanionProgressPersistence() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val probe = helperText
			.substringAfter("async function runWhispersyncCompanionProgressProbe(page)")
			.substringBefore("async function runWhispersyncCharOffsetOverlayProbe(page)")

		assertContains(scriptText, "whispersync-companion-progress")
		assertContains(helperText, "'whispersync-companion-progress': runWhispersyncCompanionProgressProbe")
		assertContains(probe, "probe: 'whispersync-companion-progress'")
		assertContains(probe, "OEBPS/xhtml/Authorforeword.xhtml")
		assertContains(probe, "whispersync-companion-progress-cue")
		assertContains(probe, "visibleTextRange")
		assertContains(probe, "waitForTargetVisibleRange")
		assertContains(probe, "snapshotAttempt")
		assertContains(probe, "overlayFragmentActive")
		assertContains(probe, "waitForAppOwnedOverlay")
		assertContains(probe, "activeMediaOverlaySnapshot")
		assertContains(probe, "payload?.overlayRequestId != null")
		assertContains(probe, "payload?.textHref === cueHref")
		assertContains(probe, "textEnd > textStart")
		assertContains(probe, "textEnd > Number(visibleRange?.visibleStart)")
		assertContains(probe, "textStart < Number(visibleRange?.visibleEnd)")
		assertContains(probe, "progressEnd < Number(visibleRange?.visibleEnd)")
		assertFalse(
			probe.contains("cueClipBeginSeconds"),
			"The app-owned cue identity must come from its overlap with the requested visible range, not a stale synthetic seek timestamp."
		)
		assertContains(probe, "Expected app-owned overlayFragmentActive for the requested cue")
		assertFalse(
			probe.contains("type: 'applyOverlayFragment'"),
			"Companion progress must observe the app-owned cue overlay instead of replacing it with a synthetic fragment."
		)
		assertFalse(
			probe.contains("await settleFrames(8)\n      const snapshot"),
			"Companion progress Whispersync smoke navigation must wait for the requested href, not sample stale saved state after a fixed frame count."
		)
		assertFalse(
			probe.contains("OEBPS/xhtml/mini_toc.xhtml"),
			"Companion progress persistence must stay on the cue-covered page; jumping to unsupported content can overwrite the exact companion entry."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanProbeWhispersyncCharacterOffsetOverlay() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val probe = helperText
			.substringAfter("async function runWhispersyncCharOffsetOverlayProbe(page)")
			.substringBefore("async function runChapterProgressEndpointsProbe(page)")

		assertContains(scriptText, "whispersync-char-offset-overlay")
		assertContains(helperText, "'whispersync-char-offset-overlay': runWhispersyncCharOffsetOverlayProbe")
		assertContains(probe, "probe: 'whispersync-char-offset-overlay'")
		assertContains(probe, "textStart")
		assertContains(probe, "textEnd")
		assertContains(probe, "type: 'applyOverlayFragment'")
		assertContains(probe, "data-navic-media-overlay-range")
		assertContains(probe, "navic-active-overlay-fragment")
		assertContains(probe, "overlayFragmentActive")
		assertContains(probe, "type: 'clearOverlay'")
	}

	@Test
	fun androidReaderMediaOverlayHighlightSurvivesThemeBackgroundReset() {
		val typographyText = readerAssetRoot().resolve("navic-reader-typography.js").readText()
		val documentThemeCss = typographyText
			.substringAfter("export const readerDocumentThemeCss = settings =>")
			.substringBefore("export const readerContentCss = settings =>")
		val overlayCss = typographyText
			.substringAfter(".\${overlayClass} {")
			.substringBefore("}")

		assertContains(
			documentThemeCss,
			":not(.\${overlayClass})",
			message = "Theme background cleanup must not make Whispersync overlay marker spans transparent."
		)
		assertContains(
			overlayCss,
			"background-color:",
			message = "Whispersync text ranges must paint with background-color so document cleanup cannot erase them."
		)
		assertContains(
			overlayCss,
			"!important",
			message = "Whispersync text ranges must win over EPUB and theme background reset rules."
		)
		assertContains(
			overlayCss,
			"box-decoration-break: clone",
			message = "Wrapped Whispersync text ranges must paint each visual line consistently."
		)
	}

	@Test
	fun androidReaderRuntimePostsVisibleTextRangeFromRenderedFoliateContent() {
		val root = readerAssetRoot()
		val locationText = root.resolve("navic-reader-location.js").readText()
		val bridgeText = readerBridgeText(root)

		assertContains(locationText, "postCurrentVisibleTextRange")
		assertContains(locationText, "visibleTextRange")
		assertContains(locationText, "renderer.getContents?.()")
		assertContains(locationText, "createTreeWalker")
		assertContains(locationText, "NodeFilter.SHOW_TEXT")
		assertContains(locationText, "getBoundingClientRect")
		assertContains(locationText, "visibleStart")
		assertContains(locationText, "visibleEnd")
		assertContains(locationText, "lastPostedVisibleTextRangeKey")
		assertContains(locationText, "function postCurrentVisibleTextRange(detail = {}, options = {})")
		assertContains(locationText, "visibleTextRangeResult")
		assertContains(
			bridgeText,
			"this.postCurrentVisibleTextRange(detail, { ...options, source: reason || null })",
			message = "Visible text range must be emitted from committed relocation snapshots, not from controller-owned UI state."
		)
	}

	@Test
	fun androidReaderLongPressPostsWhispersyncTextPointOffset() {
		val root = readerAssetRoot()
		val helperText = root.resolve("navic-reader-helpers.js").readText()
		val interactionText = root.resolve("navic-reader-content-interactions.js").readText()

		assertContains(helperText, "export const readerMediaOverlayTextOffsetForRange")
		assertContains(helperText, "readerMediaOverlayTextEntries(doc)")
		assertContains(interactionText, "readerMediaOverlayTextOffsetForRange")
		assertContains(interactionText, "type: 'textPoint'")
		assertContains(interactionText, "textHref")
		assertContains(interactionText, "textOffset: Math.floor(textOffset)")
		assertContains(interactionText, "source")
	}

	@Test
	fun adbWebViewEvalHelperCanReadRuntimeStateWithoutMutatingReader() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val runtimeStateProbe = helperText
			.substringAfter("async function runRuntimeStateProbe(page)")
			.substringBefore("async function runTextureSlotsProbe(page)")

		assertContains(runtimeStateProbe, "probe: 'runtime-state'")
		assertContains(runtimeStateProbe, "document.body.dataset.navicReaderFlowMode")
		assertContains(runtimeStateProbe, "renderer?.scrolled")
		assertContains(runtimeStateProbe, "renderer?.start")
		assertContains(runtimeStateProbe, "renderer?.end")
		assertContains(runtimeStateProbe, "renderer?.viewSize")
		assertContains(runtimeStateProbe, "contentCount")
		assertFalse(
			runtimeStateProbe.contains("NavicReaderBridge.dispatch"),
			"Runtime state inspection must stay read-only and must not use diagnostic bridge commands."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanReadRendererPageBoxWithoutMutatingContent() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val pageBoxProbe = helperText
			.substringAfter("async function runPageBoxProbe(page)")
			.substringBefore("async function runFontSizeProbe(page)")

		assertContains(pageBoxProbe, "probe: 'page-box'")
		assertContains(pageBoxProbe, "const readerRoot = document.body || document.documentElement")
		assertContains(pageBoxProbe, "renderer.getAttribute('max-inline-size')")
		assertContains(pageBoxProbe, "renderer.getAttribute('max-block-size')")
		assertContains(pageBoxProbe, "renderer.getAttribute('max-column-count')")
		assertContains(pageBoxProbe, "renderer.getAttribute('top-margin')")
		assertContains(pageBoxProbe, "renderer.getAttribute('bottom-margin')")
		assertContains(pageBoxProbe, "closedShadowRoot")
		assertContains(pageBoxProbe, "rendererRect")
		assertContains(pageBoxProbe, "contentRects")
		assertContains(pageBoxProbe, "navicReaderShellGeometry")
		assertContains(pageBoxProbe, "navicReaderShellGeometryMode")
		assertContains(pageBoxProbe, "navicReaderShellGutterWidth")
		assertContains(pageBoxProbe, "navicReaderShellRect")
		assertContains(pageBoxProbe, "navicReaderShellContentRects")
		assertContains(pageBoxProbe, "shellGeometry")
		assertContains(pageBoxProbe, "shellGeometryMode")
		assertContains(pageBoxProbe, "rendererShellRect")
		assertContains(pageBoxProbe, "rendererShellContentRects")
		assertContains(pageBoxProbe, "documentToViewportWidthRatio")
		assertContains(pageBoxProbe, "bodyToDocumentWidthRatio")
		assertContains(pageBoxProbe, "readerShellContentAlignment")
		assertContains(pageBoxProbe, "expectedContentRect")
		assertContains(pageBoxProbe, "centerDeltaPx")
		assertContains(pageBoxProbe, "centerDeltaRatio")
		assertContains(pageBoxProbe, "firstProse")
		assertContains(pageBoxProbe, "chapterOpening")
		assertContains(pageBoxProbe, "data-navic-chapter-opening-margin-capped")
		assertContains(pageBoxProbe, "marginBlockStart")
		assertContains(pageBoxProbe, "fontSize")
		assertContains(pageBoxProbe, "maxWidth")
		assertContains(pageBoxProbe, "contentDocument")
		assertContains(pageBoxProbe, "transientState")
		assertContains(pageBoxProbe, "activeOverlayMarkerCount")
		assertContains(pageBoxProbe, "activeMediaOverlayMarkerCount")
		assertContains(pageBoxProbe, "selectedTextLength")
		assertFalse(
			pageBoxProbe.contains("NavicReaderBridge.dispatch"),
			"Page-box probing must be read-only and must not trigger reader commands."
		)
		assertFalse(
			pageBoxProbe.contains("createElement"),
			"Page-box probing must not inject diagnostic DOM into the reader."
		)
	}

	@Test
	fun adbWebViewEvalHelperReportsDecorativePaperTextureState() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val textureProbe = helperText
			.substringAfter("async function runTextureSlotsProbe(page)")
			.substringBefore("async function runNativeDragPreviewTextureProbe(page)")

		assertContains(textureProbe, "data-navic-surface-paper-texture-layer")
		assertContains(textureProbe, "staticTextureLayerPresent")
		assertContains(textureProbe, "staticTextureLayerImageSet")
		assertContains(textureProbe, "staticBorderLayerPresent")
		assertContains(textureProbe, "staticStainLayerPresent")
		assertContains(textureProbe, "staticGutterLayerPresent")
		assertFalse(
			textureProbe.contains("staticPaperShell"),
			"Reader diagnostics must not keep probing the rejected synthetic static paper shell."
		)
		assertFalse(
			textureProbe.contains("await window.NavicReaderBridge.dispatch"),
			"Texture slot probe must not block on applySettings dispatch; the reader can be relocating while the probe samples static shell layers.",
		)
		assertFalse(
			textureProbe.contains("requestAnimationFrame"),
			"Texture slot probe must not wait on requestAnimationFrame; Android WebView DevTools can report the target hidden even when the activity is focused.",
		)
	}

	@Test
	fun adbWebViewEvalHelperCanReadVisiblePageContentWithoutMutatingContent() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val visiblePageProbe = helperText
			.substringAfter("async function runVisiblePageContentProbe(page)")
			.substringBefore("async function runFontSizeProbe(page)")

		assertContains(helperText, "visible-page-content")
		assertContains(helperText, "'visible-page-content': runVisiblePageContentProbe")
		assertContains(visiblePageProbe, "probe: 'visible-page-content'")
		assertContains(visiblePageProbe, "rendererPage")
		assertContains(visiblePageProbe, "rendererPages")
		assertContains(visiblePageProbe, "visibleTextLength")
		assertContains(visiblePageProbe, "visibleElementCount")
		assertContains(visiblePageProbe, "viewportIntersectionRatio")
		assertContains(visiblePageProbe, "rendererContainerPosition")
		assertContains(visiblePageProbe, "adjustedRect")
		assertContains(visiblePageProbe, "textSample")
		assertFalse(
			visiblePageProbe.contains("NavicReaderBridge.dispatch"),
			"Visible-page probing must be read-only and must not trigger reader commands."
		)
		assertFalse(
			visiblePageProbe.contains("createElement"),
			"Visible-page probing must not inject diagnostic DOM into the reader."
		)
	}

	@Test
	fun adbWebViewEvalHelperRelocationProbeReturnsEvidenceAfterDiagnosticDispatch() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()

		assertContains(helperText, "locationSnapshotResult = await Promise.resolve(dispatchResult)")
		assertContains(helperText, "locationSnapshotResult?.message?.type === 'locationChanged'")
		assertContains(helperText, "|| returnedLocation")
		assertContains(helperText, "observedPayloads.find(payload => payload.type === 'locationChanged')")
		assertContains(helperText, "diagnosticLocationSnapshot did not emit locationChanged")
		assertFalse(
			helperText.contains("await observedLocation"),
			"Relocation payload probing must not wait indefinitely for a bridge message after the diagnostic dispatch has settled."
		)
	}

	@Test
	fun adbWebViewEvalHelperSelectionProbeRequiresFootnoteEvidence() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()

		assertContains(helperText, "paragraph.setAttribute('role', 'doc-footnote')")
		assertContains(helperText, "Reader bridge event: selectionChanged(footnote=true")
	}

	@Test
	fun adbWebViewEvalHelperCanProbeAnnotationNoteRoundTrip() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val annotationProbe = helperText
			.substringAfter("async function runAnnotationRoundTripProbe(page)")
			.substringBefore("async function runHistoryControlsProbe(page)")

		assertContains(helperText, "'annotation-roundtrip': runAnnotationRoundTripProbe")
		assertContains(annotationProbe, "probe: 'annotation-roundtrip'")
		assertContains(annotationProbe, "type: 'applyHighlights'")
		assertContains(annotationProbe, "note: 'Navic annotation roundtrip note'")
		assertContains(annotationProbe, "data-navic-note-annotation")
		assertContains(annotationProbe, "new CustomEvent('draw-annotation'")
		assertContains(annotationProbe, "new CustomEvent('show-annotation'")
		assertContains(annotationProbe, "Reader bridge event: annotationDrawn")
		assertContains(annotationProbe, "Reader bridge event: annotationClick")
		assertContains(annotationProbe, "noteMarkerCreated")
	}

	@Test
	fun adbWebViewEvalHelperFontSizeProbeCleansSyntheticParagraph() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val fontSizeProbe = helperText
			.substringAfter("async function runFontSizeProbe(page)")
			.substringBefore("async function main()")

		assertContains(fontSizeProbe, "probe.remove()")
		assertContains(fontSizeProbe, "finally")
		assertContains(fontSizeProbe, "data-navic-font-size-probe=\"true\"], [data-navic-publisher-font-size-probe=\"true\"")
		assertContains(fontSizeProbe, "fontSizePercent: Number.isFinite(originalPercent) ? originalPercent : 140")
	}

	@Test
	fun adbWebViewEvalHelperFontSizeProbeFailsWhenExistingProseDoesNotScale() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val fontSizeProbe = helperText
			.substringAfter("async function runFontSizeProbe(page)")
			.substringBefore("async function runPublisherStyleFontSizeProbe(page)")

		assertContains(fontSizeProbe, "existingProseDelta")
		assertContains(fontSizeProbe, "Existing prose text did not scale with reader Font size")
		assertContains(fontSizeProbe, "existingDeltas.filter")
	}

	@Test
	fun adbWebViewEvalHelperCanReadCurrentAppliedFontSizeWithoutMutatingSettings() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val currentFontSizeProbe = helperText
			.substringAfter("async function runCurrentFontSizeProbe(page)")
			.substringBefore("async function runFontSizeProbe(page)")
		val smokeScript = repoScriptFile("adb-reader-smoke.ps1").readText()
		val matrixScript = repoScriptFile("adb-reader-komikku-matrix.ps1").readText()

		assertContains(helperText, "'font-size-current': runCurrentFontSizeProbe")
		assertContains(smokeScript, "\"font-size-current\"")
		assertContains(matrixScript, "\"font-size-current\"")
		assertContains(currentFontSizeProbe, "probe: 'font-size-current'")
		assertContains(currentFontSizeProbe, "currentFontSizePercent")
		assertContains(currentFontSizeProbe, "existingProseMetrics")
		assertContains(currentFontSizeProbe, "contentFontSizeVariable")
		assertFalse(
			currentFontSizeProbe.contains("type: 'applySettings'"),
			"The current-font probe must measure the native UI result; it must not mutate settings itself."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanProbePublisherStyleFontSizeOverride() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val publisherProbe = helperText
			.substringAfter("async function runPublisherStyleFontSizeProbe(page)")
			.substringBefore("async function runRuntimeStateProbe(page)")

		assertContains(helperText, "'font-size-publisher-styles': runPublisherStyleFontSizeProbe")
		assertContains(publisherProbe, "publisherStyles: true")
		assertContains(publisherProbe, "font-size: 12px")
		assertContains(publisherProbe, "publisher-important-wrapper")
		assertContains(publisherProbe, "publisherClassImportantDelta")
		assertContains(publisherProbe, "publisherParagraphDelta")
		assertContains(publisherProbe, "probe.remove()")
		assertContains(publisherProbe, "publisherStyles: originalPublisherStyles")
	}

	@Test
	fun readerHarnessFontCssSmokeCoversInlineImportantPublisherProse() {
		val harnessText = repoFile("tools/reader-harness/src/run-reader-harness.mjs").readText()
		val fontCssSmoke = harnessText
			.substringAfter("if (mode === 'font-css-smoke') {")
			.substringBefore("if (mode === 'epub-frontmatter') {")

		assertContains(fontCssSmoke, "inline-important-body")
		assertContains(fontCssSmoke, "important-class-body")
		assertContains(fontCssSmoke, "normalizeReaderInlineTypography(doc, { fontSizePercent })")
		assertContains(fontCssSmoke, "publisherInlineImportantBodyDelta")
		assertContains(fontCssSmoke, "publisherImportantClassBodyDelta")
		assertContains(
			fontCssSmoke,
			"Expected font-size control to scale publisher inline-important body text",
			message = "The browser harness must keep reproducing inline-important publisher prose, because CSS selectors alone cannot override that cascade case."
		)
		assertContains(
			fontCssSmoke,
			"Expected font-size control to scale publisher class-important body text",
			message = "The browser harness must keep reproducing class-important publisher prose, because high-specificity EPUB CSS can pin body text while headings continue to scale."
		)
	}

	@Test
	fun adbWebViewEvalFontSizeProbesDoNotDependOnAnimationFrames() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val fontSizeProbe = helperText
			.substringAfter("async function runFontSizeProbe(page)")
			.substringBefore("async function runPublisherStyleFontSizeProbe(page)")
		val publisherProbe = helperText
			.substringAfter("async function runPublisherStyleFontSizeProbe(page)")
			.substringBefore("async function runRuntimeStateProbe(page)")

		assertFalse(
			fontSizeProbe.contains("requestAnimationFrame"),
			"Font-size probing must not wait on requestAnimationFrame because Android WebView DevTools can expose a non-visible target where animation frames are paused."
		)
		assertFalse(
			publisherProbe.contains("requestAnimationFrame"),
			"Publisher-style font-size probing must not wait on requestAnimationFrame because Android WebView DevTools can expose a non-visible target where animation frames are paused."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanProbeChapterProgressEndpoints() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val chapterProgressProbe = helperText
			.substringAfter("async function runChapterProgressEndpointsProbe(page)")
			.substringBefore("async function main()")

		assertContains(helperText, "'chapter-progress-endpoints': runChapterProgressEndpointsProbe")
		assertContains(chapterProgressProbe, "__navicChapterProgressProbePromise")
		assertContains(chapterProgressProbe, "type: 'goToChapterProgress'")
		assertContains(chapterProgressProbe, "Array.from(view?.book?.sections || [])")
		assertContains(chapterProgressProbe, "chapter-progress-candidate")
		assertContains(chapterProgressProbe, "candidateAttempts.push({ href, error")
		assertContains(chapterProgressProbe, "successfulCandidates.push")
		assertContains(chapterProgressProbe, "bestCandidate")
		assertContains(chapterProgressProbe, "chapterPageCount > bestCandidate.chapterPageCount")
		assertContains(chapterProgressProbe, "endpoint(href, 0)")
		assertContains(chapterProgressProbe, "endpoint(href, 0, 0, bestCandidate.chapterPageCount)")
		assertContains(chapterProgressProbe, "endpoint(href, 1, bestCandidate.chapterPageCount - 1, bestCandidate.chapterPageCount)")
		assertContains(chapterProgressProbe, "chapterPageIndex")
		assertContains(chapterProgressProbe, "chapterPageCount")
		assertContains(chapterProgressProbe, "Expected chapter-progress endpoint 0")
		assertContains(chapterProgressProbe, "Expected chapter-progress endpoint 1")
	}

	@Test
	fun adbWebViewEvalHelperCanProbeCurrentChapterProgressEndpointsWithoutSpineScan() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val currentChapterProgressProbe = helperText
			.substringAfter("async function runCurrentChapterProgressEndpointsProbe(page)")
			.substringBefore("async function runPageBoxProbe(page)")

		assertContains(scriptText, "chapter-progress-current-endpoints")
		assertContains(helperText, "'chapter-progress-current-endpoints': runCurrentChapterProgressEndpointsProbe")
		assertContains(currentChapterProgressProbe, "probe: 'chapter-progress-current-endpoints'")
		assertContains(currentChapterProgressProbe, "initialLocation.href")
		assertContains(currentChapterProgressProbe, "endpoint(href, 0, 0, initialChapterPageCount)")
		assertContains(currentChapterProgressProbe, "endpoint(href, 1, initialChapterPageCount - 1, initialChapterPageCount)")
		assertContains(currentChapterProgressProbe, "chapterPageIndex")
		assertContains(currentChapterProgressProbe, "chapterPageCount")
		assertFalse(
			currentChapterProgressProbe.contains("Array.from(view?.book?.sections || [])"),
			"Current-chapter endpoint probing must not scan the whole spine; invalid section targets can leave the DevTools evaluation pending."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanProbeLocationSnapshotWithoutNavigation() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val locationSnapshotProbe = helperText
			.substringAfter("async function runLocationSnapshotProbe(page)")
			.substringBefore("async function runCurrentChapterProgressEndpointsProbe(page)")

		assertContains(scriptText, "location-snapshot")
		assertContains(helperText, "'location-snapshot': runLocationSnapshotProbe")
		assertContains(locationSnapshotProbe, "probe: 'location-snapshot'")
		assertContains(locationSnapshotProbe, "type: 'diagnosticLocationSnapshot'")
		assertContains(locationSnapshotProbe, "location-snapshot")
		assertContains(locationSnapshotProbe, "Reader bridge event: locationChanged")
		assertFalse(
			locationSnapshotProbe.contains("goToChapterProgress"),
			"Location snapshot probing must not navigate; Stage 6B native rail gates need a non-mutating before/after reader location."
		)
	}

	@Test
	fun adbReaderSmokeCanTapNativeSelectionActionsAfterDevtoolsProbe() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val postProbeGestureBlock = scriptText
			.substringAfter("if (-not [string]::IsNullOrWhiteSpace(\$ReaderDevtoolsProbe))")
			.substringAfter("if (\$probeExitCode -ne 0)")
			.substringBefore("Invoke-AdbExecOutToFile")

		assertContains(scriptText, "[string[]] \$PostProbeTap = @()")
		assertContains(scriptText, "[string[]] \$PostProbeTapFraction = @()")
		assertContains(scriptText, "[string[]] \$PostProbeAction = @()")
		assertContains(scriptText, "\$expandedPostProbeActions")
		assertContains(scriptText, "-split '\\|'")
		assertContains(scriptText, "[string[]] \$RequireReaderEngineCommand = @()")
		assertContains(scriptText, "[string[]] \$RequireReaderLog = @()")
		assertContains(scriptText, "\$PostProbeTap += Convert-TapFraction")
		assertContains(postProbeGestureBlock, "foreach (\$tapSpec in \$PostProbeTap)")
		assertContains(postProbeGestureBlock, "Invoke-Adb @(\"shell\", \"input\", \"tap\", \$x, \$y)")
		assertContains(postProbeGestureBlock, "foreach (\$postProbeActionEntry in \$PostProbeAction)")
		assertContains(postProbeGestureBlock, "tapFraction:")
		assertContains(postProbeGestureBlock, "tapFractionUntilDescPresent:")
		assertContains(postProbeGestureBlock, "tapTextWhenPresent:")
		assertContains(postProbeGestureBlock, "tapDescWhenPresent:")
		assertContains(postProbeGestureBlock, "waitDesc:")
		assertContains(postProbeGestureBlock, "tapText:")
		assertContains(postProbeGestureBlock, "tapDesc:")
		assertContains(postProbeGestureBlock, "tapDescIfPresent:")
		assertContains(postProbeGestureBlock, "tapDescFraction:")
		assertContains(postProbeGestureBlock, "swipeDescFraction:")
		assertContains(postProbeGestureBlock, "Get-AdbUiNodeCenter")
		assertContains(postProbeGestureBlock, "Get-AdbUiNodeFractionPoint")
		assertContains(postProbeGestureBlock, "Invoke-PostProbeUiNodeFractionSwipe")
		assertContains(postProbeGestureBlock, "Invoke-PostProbeUiNodeAction")
		assertContains(postProbeGestureBlock, "Invoke-PostProbeUiNodeActionIfPresent")
		assertContains(postProbeGestureBlock, "Invoke-PostProbeUiNodeActionWhenPresent")
		assertContains(postProbeGestureBlock, "Invoke-PostProbeUiNodeWaitUntilPresent")
		assertContains(postProbeGestureBlock, "Invoke-PostProbeUiNodeFractionAction")
		assertContains(postProbeGestureBlock, "Invoke-PostProbeTapFractionUntilDescPresent")
		assertContains(postProbeGestureBlock, "Invoke-Adb @(\"shell\", \"input\", \"text\", \$text)")
		assertContains(postProbeGestureBlock, "Invoke-Adb @(\"shell\", \"input\", \"keyevent\", \$keyEvent)")
		assertContains(scriptText, "Dispatching reader engine command: \$requiredEngineCommand")
		assertContains(scriptText, "required engine command '\$requiredEngineCommand' was not captured")
		assertContains(scriptText, "foreach (\$requiredReaderLog in \$RequireReaderLog)")
		assertContains(scriptText, "required reader log '\$requiredReaderLog' was not captured")
		assertContains(scriptText, "Use tapDescFraction:value,xFraction,yFraction or tapDescFraction:value,xFraction,yFraction,waitMs.")
		assertContains(scriptText, "Use swipeDescFraction:value,x1Fraction,y1Fraction,x2Fraction,y2Fraction or swipeDescFraction:value,x1Fraction,y1Fraction,x2Fraction,y2Fraction,durationMs,waitMs.")
		assertContains(scriptText, "Use tapTextWhenPresent:value,maxAttempts,waitMs.")
		assertContains(scriptText, "Use tapDescWhenPresent:value,maxAttempts,waitMs.")
		assertContains(scriptText, "Use waitDesc:value,maxAttempts,waitMs.")
	}

	@Test
	fun adbWebViewEvalHelperCanCreateVisibleSelectionPayloadForNativeActions() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val visibleSelectionProbe = helperText
			.substringAfter("async function runVisibleSelectionPayloadProbe(page)")
			.substringBefore("async function runRelocationPayloadProbe(page)")

		assertContains(scriptText, "visible-selection-payload")
		assertContains(helperText, "'visible-selection-payload': runVisibleSelectionPayloadProbe")
		assertContains(visibleSelectionProbe, "probe: 'visible-selection-payload'")
		assertContains(visibleSelectionProbe, "findVisibleSelectionCandidate")
		assertContains(visibleSelectionProbe, "NodeFilter.SHOW_TEXT")
		assertContains(visibleSelectionProbe, "adjustedRect")
		assertContains(visibleSelectionProbe, "viewportIntersectionRatio")
		assertContains(visibleSelectionProbe, "getBoundingClientRect")
		assertContains(visibleSelectionProbe, "getClientRects")
		assertContains(visibleSelectionProbe, "selectionchange")
		assertContains(visibleSelectionProbe, "Reader bridge event: selectionChanged(footnote=false")
		assertContains(visibleSelectionProbe, "Could not create visible selection")
		assertFalse(
			visibleSelectionProbe.contains("doc.body.appendChild(paragraph)"),
			"Visible selection validation must not rely on appending an offscreen synthetic paragraph."
		)
	}

	@Test
	fun adbWebViewEvalHelperCanClearVisibleSelectionThroughTheRealDocument() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val clearSelectionProbe = helperText
			.substringAfter("async function runVisibleSelectionClearProbe(page)")
			.substringBefore("async function runRelocationPayloadProbe(page)")

		assertContains(scriptText, "visible-selection-clear")
		assertContains(helperText, "'visible-selection-clear': runVisibleSelectionClearProbe")
		assertContains(clearSelectionProbe, "probe: 'visible-selection-clear'")
		assertContains(clearSelectionProbe, "runVisibleSelectionPayloadProbe(page)")
		assertContains(clearSelectionProbe, "removeAllRanges()")
		assertContains(clearSelectionProbe, "selectionchange")
		assertContains(clearSelectionProbe, "selectionCleared")
		assertContains(clearSelectionProbe, "Reader bridge event: selectionCleared")
		assertFalse(
			clearSelectionProbe.contains("doc.body.appendChild"),
			"Selection clear validation must clear the currently loaded EPUB document, not a synthetic offscreen document."
		)
	}

	@Test
	fun adbReaderSmokeCanRunSecondDevtoolsProbeAfterNativePostActions() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val postActionProbeBlock = scriptText
			.substringAfter("foreach (\$postProbeActionEntry in \$PostProbeAction)")
			.substringBefore("Invoke-AdbExecOutToFile")
		val logValidationBlock = scriptText
			.substringAfter("\$readerLogText = Get-TextFileRaw -Path (Join-Path \$ArtifactDir \"logcat-reader.log\")")
			.substringBefore("foreach (\$requiredEngineCommand in \$RequireReaderEngineCommand)")

		assertContains(scriptText, "[string] \$PostActionReaderDevtoolsProbe = \"\"")
		assertContains(scriptText, "function Invoke-ReaderDevtoolsProbe")
		assertContains(scriptText, "reader-devtools-post-action-probe.json")
		assertContains(postActionProbeBlock, "\$PostActionReaderDevtoolsProbe")
		assertContains(postActionProbeBlock, "Invoke-ReaderDevtoolsProbe")
		assertContains(logValidationBlock, "\$ReaderDevtoolsProbe")
		assertContains(logValidationBlock, "\$PostActionReaderDevtoolsProbe")
		assertContains(logValidationBlock, "reader-devtools-post-action-probe.json")
	}

	@Test
	fun adbReaderSmokeCanRejectReaderConsoleErrors() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val logValidationBlock = scriptText
			.substringAfter("\$readerLogText = Get-TextFileRaw -Path (Join-Path \$ArtifactDir \"logcat-reader.log\")")
			.substringBefore("foreach (\$requiredEngineCommand in \$RequireReaderEngineCommand)")

		assertContains(scriptText, "[switch] \$RequireNoReaderConsoleErrors")
		assertContains(logValidationBlock, "\$RequireNoReaderConsoleErrors")
		assertContains(logValidationBlock, "Reader console ERROR:")
		assertContains(logValidationBlock, "required no reader console errors")
	}

	@Test
	fun adbReaderSmokeMatchesVersionNameExactly() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val versionBlock = scriptText
			.substringAfter("if (-not [string]::IsNullOrWhiteSpace(\$ExpectedVersionName)) {")
			.substringBefore("Assert-FocusedAndroidPackage")

		assertContains(versionBlock, "(?m)^\\s*versionName=(?<Value>[^\\r\\n]*)\\r?\$")
		assertContains(versionBlock, "Groups['Value'].Value.Trim() -cne \$ExpectedVersionName")
		assertFalse(versionBlock.contains("did not contain expected versionName"))
	}

	@Test
	fun readerQaRunnerRevalidatesSourceIdentityAtCompletion() {
		val scriptText = repoScriptFile("adb-reader-playlikecurl-qa.ps1").readText()
		val completionBlock = scriptText
			.substringAfter("Assert-InstalledReaderDevIdentity 'ReaderDev post-run'")
			.substringBefore("\$resolvedArtifactRoot =")

		assertContains(scriptText, "function Assert-RunnerSourceIdentity")
		assertContains(completionBlock, "Assert-RunnerSourceIdentity 'ReaderDev post-run'")
		assertContains(scriptText, "Runner Git commit changed while")
		assertContains(scriptText, "FrozenCommit source tree changed while")
		assertContains(scriptText, "Precommit candidate tree changed while")
	}

	@Test
	fun foliateSearchAnnotationsDoNotRejectOnInvalidCfiResolution() {
		val viewText = repoFile("composeApp/src/androidMain/assets/reader/vendor/foliate-js/view.js").readText()
		val searchAnnotationBlock = viewText
			.substringAfter("if (value.startsWith(SEARCH_PREFIX))")
			.substringBefore("const { index, anchor } = await this.resolveNavigation(value)")

		assertContains(searchAnnotationBlock, "try {")
		assertContains(searchAnnotationBlock, "const range = doc ? anchor(doc) : anchor")
		assertContains(searchAnnotationBlock, "Could not render search annotation")
		assertContains(searchAnnotationBlock, "} catch")
		assertContains(searchAnnotationBlock, "return")
	}

	@Test
	fun adbReaderSmokeCanRequireNativeChapterRailEndpointAfterPostActionProbe() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val matrixText = repoScriptFile("adb-reader-komikku-matrix.ps1").readText()
		val endpointValidationBlock = scriptText
			.substringAfter("function Assert-ReaderDevtoolsLocationEndpoint")
			.substringBefore("function Get-ReaderDevtoolsPdfVisibleResult")
		val fractionPointBlock = scriptText
			.substringAfter("function Get-AdbUiNodeFractionPoint")
			.substringBefore("function Invoke-PostProbeUiNodeAction")
		val prepareLaunchBlock = matrixText
			.substringAfter("function Invoke-ReaderMatrixPrepareLaunch")
			.substringBefore("function Invoke-ReaderCoverMatrixSteps")
		val railEndpointBlock = matrixText
			.substringAfter("function Invoke-ReaderRailEndpointMatrixSteps")
			.substringBefore("if (\$PrepareReaderLaunch)")

		assertContains(scriptText, "[ValidateSet(\"\", \"start\", \"end\")]")
		assertContains(scriptText, "[string] \$RequirePostActionChapterPageEndpoint = \"\"")
		assertContains(endpointValidationBlock, "reader-devtools-post-action-probe.json")
		assertContains(endpointValidationBlock, "chapterPageIndex")
		assertContains(endpointValidationBlock, "chapterPageCount")
		assertContains(endpointValidationBlock, "Expected first chapter page")
		assertContains(endpointValidationBlock, "Expected last chapter page")
		assertContains(matrixText, "[switch] \$IncludeRailEndpointChecks")
		assertContains(matrixText, "[switch] \$OnlyRailEndpointChecks")
		assertContains(matrixText, "[string[]] \$PostProbeAction = @()")
		assertContains(matrixText, "chapter-rail-native-start")
		assertContains(matrixText, "chapter-rail-native-end")
		assertContains(matrixText, "if (\$OnlyRailEndpointChecks)")
		assertFalse(
			railEndpointBlock.contains("-ReaderDevtoolsProbe \"chapter-progress-endpoints\""),
			"The rail endpoint matrix must not use the whole-spine chapter-progress probe; it can hang on large EPUBs."
		)
		assertContains(railEndpointBlock, "-ReaderDevtoolsProbe \"chapter-progress-current-endpoints\"")
		assertContains(railEndpointBlock, "-TapFraction @(\"0.50,0.50,700\")")
		assertContains(matrixText, "tapFractionUntilDescPresent:Chapter page slider,0.50,0.50")
		assertContains(matrixText, "tapDescIfPresent:Close history controls")
		assertContains(matrixText, "tapDescFraction:Chapter page slider,0.0,0.5")
		assertContains(matrixText, "tapDescFraction:Chapter page slider,1.0,0.5")
		assertContains(scriptText, "function Clamp-AdbUiCoordinateInsideBounds")
		assertContains(fractionPointBlock, "Clamp-AdbUiCoordinateInsideBounds")
		assertContains(fractionPointBlock, "End \$bounds.Right")
		assertContains(fractionPointBlock, "End \$bounds.Bottom")
		assertContains(prepareLaunchBlock, "\$OnlyRailEndpointChecks")
		assertContains(prepareLaunchBlock, "ignoring Whispersync prepare args during rail endpoint checks")
		assertContains(prepareLaunchBlock, "if (-not \$OnlyRailEndpointChecks)")
		assertContains(matrixText, "PostProbeAction = \$PostProbeAction")
		assertContains(matrixText, "RequirePostActionChapterPageEndpoint = \$RequirePostActionChapterPageEndpoint")
		assertContains(matrixText, "-RequirePostActionChapterPageEndpoint \"start\"")
		assertContains(matrixText, "-RequirePostActionChapterPageEndpoint \"end\"")
	}

	@Test
	fun adbReaderSmokeUsesSerialAwareAdbHelperForCaptureAndDiagnostics() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val bodyAfterHelper = scriptText
			.substringAfter("function Get-AdbScreenSize")

		assertFalse(
			Regex("""(?m)^\s*&?\s*adb\s""").containsMatchIn(bodyAfterHelper),
			"adb-reader-smoke.ps1 must route capture and diagnostic commands through Invoke-Adb so the selected DeviceSerial/ANDROID_SERIAL is consistently honored."
		)
		assertContains(bodyAfterHelper, "Invoke-Adb @(\"shell\", \"wm\", \"size\")")
		assertContains(bodyAfterHelper, "Invoke-Adb @(\"devices\")")
		assertContains(bodyAfterHelper, "Invoke-Adb @(\"shell\", \"pidof\", \$Package)")
		assertContains(bodyAfterHelper, "Invoke-Adb @(\"shell\", \"dumpsys\", \"package\", \$Package)")
		assertContains(bodyAfterHelper, "Invoke-Adb @(\"shell\", \"cat\", \"/proc/net/unix\")")
		assertContains(bodyAfterHelper, "Invoke-Adb @(\"exec-out\", \"uiautomator\", \"dump\", \"/dev/tty\")")
		val logcatCapture = bodyAfterHelper
			.substringAfter("\$logcatFullLines = @(")
			.substringBefore("\$logcatFullText =")
		assertContains(logcatCapture, "Invoke-Adb @(")
		assertContains(
			logcatCapture,
			"\"logcat\", \"-d\", \"--pid=\$processId\", \"-v\", \"time\""
		)
	}

	@Test
	fun adbReaderSmokeFailsWhenFocusedWindowDoesNotBelongToRequestedPackage() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()

		assertContains(scriptText, "function Assert-FocusedAndroidPackage")
		assertContains(scriptText, "dumpsys\", \"window")
		assertContains(scriptText, "focused-window.txt")
		assertContains(scriptText, "mFocusedApp=.*\$escapedPackage\$packageBoundary")
		assertContains(scriptText, "Focused Android window does not belong to package")
		assertContains(scriptText, "Foreground confirmed for \$Package")
		val focusAssertion = scriptText.indexOf(
			"Assert-FocusedAndroidPackage -Package \$Package"
		)
		val privacySafeCapture = scriptText.indexOf(
			"\$screenshotBytes = Invoke-AdbExecOutToMemory"
		)
		val diagnosticCapture = scriptText.indexOf(
			"-OutputPath (Join-Path \$ArtifactDir \"screen.png\")"
		)
		assertTrue(
			focusAssertion >= 0 &&
				privacySafeCapture > focusAssertion &&
				diagnosticCapture > focusAssertion,
			"Smoke capture must prove the requested package owns the focused window before screenshot/window/log artifacts are captured."
		)
	}

	@Test
	fun adbReaderSmokeUsesEffectiveOverrideDisplaySizeForFractionGestures() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val screenSizeFunction = scriptText
			.substringAfter("function Get-AdbScreenSize")
			.substringBefore("\nfunction Convert-TapFraction")

		assertContains(
			screenSizeFunction,
			"[regex]::Matches",
			message = "Fraction gestures must parse all wm size entries instead of accepting the first physical size."
		)
		assertContains(
			screenSizeFunction,
			"\$sizeMatches[\$sizeMatches.Count - 1]",
			message = "When Android reports both Physical size and Override size, adb fractions must target the effective override/logical size used by the reader view."
		)
	}

	@Test
	fun adbReaderSmokeCanDriveEta50SwipeAndContentDiagnostics() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()

		assertContains(scriptText, "[string[]] \$Swipe = @()")
		assertContains(scriptText, "[string[]] \$SwipeFraction = @()")
		assertContains(scriptText, "[string[]] \$LongPress = @()")
		assertContains(scriptText, "[string[]] \$LongPressFraction = @()")
		assertContains(scriptText, "Convert-SwipeFraction")
		assertContains(scriptText, "Convert-LongPressFraction")
		assertContains(scriptText, "Invoke-Adb @(\"shell\", \"input\", \"swipe\"")
		assertContains(scriptText, "[switch] \$RequireShellCoverSwipe")
		assertContains(scriptText, "[switch] \$RequireShellCoverDragDiagnostic")
		assertContains(scriptText, "[switch] \$RequireShellCoverCommand")
		assertContains(scriptText, "[switch] \$RequireNativeLongTap")
		assertContains(scriptText, "[switch] \$RequireContentTapHandled")
		assertContains(scriptText, "[switch] \$RequireNoReaderCenterDispatch")
		assertContains(scriptText, "[switch] \$RequireTextureDiagnostics")
		assertContains(scriptText, "[switch] \$RequirePdfDiagnostics")
		assertContains(scriptText, "shellCoverSwipe=")
		assertContains(scriptText, "shellCoverDragCandidate=")
		assertContains(scriptText, "shellCoverCommand=")
		assertContains(scriptText, "readerNativeLongTap=")
		assertContains(scriptText, "readerContentTapHandled=")
		assertContains(scriptText, "pdfRuntimeDiagnostics=")
		assertContains(scriptText, "textureHasDirection=")
		assertContains(scriptText, "textureHasHref=")
		assertContains(scriptText, "no shell-cover command was captured")
		assertContains(scriptText, "no native reader long tap was captured")
		assertContains(scriptText, "no PDF runtime diagnostics were captured")
		assertContains(scriptText, "reader center dispatch was captured")
	}

	@Test
	fun adbPdfMatrixValidatesVisiblePdfThroughDevtoolsProbe() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val smokeText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val matrixText = repoScriptFile("adb-reader-komikku-matrix.ps1").readText()
		val pdfChecksBlock = matrixText
			.substringAfter("if (\$IncludePdfChecks -or \$OnlyPdfChecks)")
			.substringBefore("Write-Host \"Komikku reader matrix artifacts")

		assertContains(helperText, "async function runPdfVisiblePageProbe(page)")
		assertContains(helperText, "'pdf-visible-page': runPdfVisiblePageProbe")
		assertContains(helperText, "probe: 'pdf-visible-page'")
		assertContains(helperText, "fixedLayout")
		assertContains(helperText, "visiblePdfVisualCount")
		assertContains(smokeText, "\"pdf-visible-page\"")
		assertContains(smokeText, "[int] \$RequirePdfRendererIndex = -1")
		assertContains(smokeText, "PDF renderer index")
		assertContains(matrixText, "[string] \$ReaderDevtoolsProbe = \"\"")
		assertContains(matrixText, "\$smokeArgs.ReaderDevtoolsProbe = \$ReaderDevtoolsProbe")
		assertContains(matrixText, "[int] \$RequirePdfRendererIndex = -1")
		assertContains(matrixText, "\$smokeArgs.RequirePdfRendererIndex = \$RequirePdfRendererIndex")
		assertContains(pdfChecksBlock, "-ReaderDevtoolsProbe \"pdf-visible-page\"")
		assertContains(pdfChecksBlock, "-RequirePdfRendererIndex 1")
	}

	@Test
	fun androidFixedLayoutPageTurnsUseFoliateResolvedIndexTarget() {
		val pageTurnsText = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val fixedDirectBlock = pageTurnsText
			.substringAfter("if (directFixedLayoutPageTarget != null)")
			.substringBefore("} else if (direction === 'next')")

		assertContains(
			fixedDirectBlock,
			"this.view.goTo({ index: directFixedLayoutPageTarget })",
			message = "Foliate fixed-layout goTo expects a resolved target object. Passing a raw page number makes PDF tap/drag turns no-op."
		)
		assertFalse(
			fixedDirectBlock.contains("this.view.goTo(directFixedLayoutPageTarget)"),
			"PDF/fixed-layout navigation must not pass a raw number to Foliate goTo."
		)
	}

	@Test
	fun androidFixedLayoutPublicationsDoNotCreateSyntheticShellCoverOverlay() {
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val openPublicationBlock = runtimeText
			.substringAfter("async openPublication")
			.substringBefore("  onInternalLink(event)")

		assertContains(openPublicationBlock, "const shellCoverAllowed = this.view?.isFixedLayout !== true")
		assertContains(openPublicationBlock, "shellCoverAllowed ? await this.loadShellCover() : null")
		assertContains(openPublicationBlock, "if (shellCoverAllowed && shellCoverUrl) this.showShellCover()")
	}

	@Test
	fun adbKomikkuMatrixRequiresNativeCoverBaselineBeforeCoverSpecificChecks() {
		val smokeText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val matrixText = repoScriptFile("adb-reader-komikku-matrix.ps1").readText()

		assertContains(smokeText, "[switch] \$RequireNativeShellCover")
		assertContains(smokeText, "reader-native-cover-validation.txt")
		assertContains(smokeText, "Reader diagnostics validation failed: native shell cover was not visible")
		assertContains(smokeText, "Get-ReaderNativeShellCoverVisible")
		assertContains(matrixText, "-Name \"baseline-native-cover\"")
		assertContains(matrixText, "\$smokeArgs.RequireNativeShellCover = \$true")
		assertTrue(
			matrixText.indexOf("-Name \"baseline-native-cover\"") <
				matrixText.indexOf("-Name \"cover-center-tap-toggle\""),
			"Cover-specific matrix steps must be gated by a native-cover baseline so readable-page swipes are not mislabeled as cover regressions."
		)
	}

	@Test
	fun androidReaderKeepScreenOnIsControlledByEbookSetting() {
		val hostText = readerEngineWebViewHostFile().readText()
		val ebooksSettingsText = settingsFile("EbooksScreen.kt").readText()
		val searchSettingsText = settingsSearchSourceText()

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
	fun androidReaderRuntimeUsesDeterministicPaginationProfileForPageNumbers() {
		val bridgeText = readerBridgeText(readerAssetRoot())
		val paginationModel = readerAssetRoot().resolve("navic-reader-pagination-model.js").readText()

		assertContains(bridgeText, "readerPaginationFingerprint")
		assertContains(bridgeText, "readerBuildPaginationProfile")
		assertContains(bridgeText, "readerPaginationPositionForLocator")
		assertContains(bridgeText, "readerPaginationObservedChapterEntries")
		assertContains(bridgeText, "paginationProfile")
		assertContains(bridgeText, "readerPaginationProfilePosition")
		assertContains(bridgeText, "pageCountSource: 'pagination-profile'")
		assertContains(bridgeText, "readerPaginationRenderMetadata()")
		assertContains(bridgeText, "readerPaginationFingerprint(this.readerPaginationRenderMetadata())")
		assertContains(bridgeText, "render: this.readerPaginationRenderMetadata()")
		assertContains(bridgeText, "profile?.render")
		assertContains(bridgeText, "profile.render.viewportWidth")
		assertContains(bridgeText, "profile.render.viewportHeight")
		assertContains(bridgeText, "hydrateObservedChapterPageCountsFromProfile(this.paginationProfile)")
		assertContains(bridgeText, "shouldUseFreshPaginationProfile(freshProfile)")
		assertContains(bridgeText, "paginationProfileHasObservedCountIncrease")
		assertContains(bridgeText, "if (this.paginationProfileHasObservedCountIncrease(freshProfile, this.paginationProfile)) return true")
		assertContains(bridgeText, "pageTurnTargetPageIndex")
		assertContains(bridgeText, "explicitTargetPageIndex")
		assertContains(bridgeText, "pagination-profile:retained")
		assertContains(bridgeText, "observedChapterCount")
		assertContains(bridgeText, "source: observedPageCount ? 'observed' : 'estimated'")
		assertContains(bridgeText, "spineIndex: index")
		assertContains(paginationModel, "adaptivePageBox:")
		assertContains(paginationModel, "maxColumnCount: String(input?.adaptivePageBox?.maxColumnCount")
		assertContains(paginationModel, "columnThreshold: String(input?.adaptivePageBox?.columnThreshold")
	}

	@Test
	fun paginationProfilerPublishesOnlyValidatedCurrentCommitReceipts() {
		val pagination = readerAssetRoot().resolve("navic-reader-pagination.js").readText()
		val build = pagination
			.substringAfter("async function buildCompletePaginationProfileInProfilerView(")
			.substringBefore("async function ensureCompletePaginationProfile(")
		val ensure = pagination
			.substringAfter("async function ensureCompletePaginationProfile(")
			.substringBefore("function shouldUseFreshPaginationProfile(")

		assertContains(pagination, "runtimeVersion: 'navic-reader-pagination-profile-3'")
		assertContains(pagination, "const ReaderPaginationProfileAuthorityCommitReceipt = 'paginator-commit-receipt'")
		assertContains(pagination, "const ReaderPaginationProfileMaximumCommitTransactionAttempts = 3")
		assertContains(pagination, "function paginationProfileTaskIsCurrent(")
		assertContains(pagination, "function invalidatePaginationProfileTask(")
		assertContains(build, "await readerCommitTextPage(")
		assertContains(build, "transactionAttempts < ReaderPaginationProfileMaximumCommitTransactionAttempts")
		assertContains(build, "result.status === 'invalidated'")
		assertContains(build, "const receiptIsValid = readerTextPageCommitIsValid(profileView.renderer, result)")
		assertContains(build, "this.paginationProfileWithCommitReceiptAuthority(")
		assertContains(build, "'pagination-profile'")
		assertContains(build, "result.status === 'committed'")
		assertContains(build, "receiptIsValid")
		assertFalse(build.contains("profileView.goTo("))
		assertFalse(build.contains("readerWaitForStableTextPagePosition"))
		assertFalse(build.contains("exactTextPagePosition"))
		assertFalse(build.contains("profileView.renderer.page"))
		assertFalse(build.contains("profileView.renderer.pages"))
		assertFalse(build.contains("requestAnimationFrame"))
		assertTrue(
			build.indexOf("this.applyReaderViewportLayoutToProfilerView(profileView, settings)") <
				build.indexOf("await readerCommitTextPage("),
			"Profiler viewport layout must precede each paginator transaction."
		)
		assertFalse(
			build.substringAfter("await readerCommitTextPage(").contains(
				"this.applyReaderViewportLayoutToProfilerView(profileView, settings)"
			),
			"Profiler layout must not be reapplied after exact commitment."
		)
		assertContains(ensure, "this.invalidatePaginationProfileTask('replacement-task')")
		assertTrue(
			ensure.indexOf("this.invalidatePaginationProfileTask('replacement-task')") <
				ensure.indexOf("this.paginationFingerprint = fingerprint")
		)
		assertContains(ensure, "this.paginationProfileTaskIsCurrent(task)")
		assertTrue(
			ensure.indexOf("this.paginationProfileTaskIsCurrent(task)") <
				ensure.indexOf("this.paginationProfile = profile")
		)
		assertTrue(
			ensure.lastIndexOf("this.paginationProfileTaskIsCurrent(task)", ensure.indexOf("this.writeCachedPaginationProfile(profile)")) <
				ensure.indexOf("this.writeCachedPaginationProfile(profile)")
		)
		val rawRelocation = pagination
			.substringAfter("function readerEnsurePaginationProfile(")
			.substringBefore("function readerPaginationProfilePosition(")
		assertContains(rawRelocation, "this.paginationProfileIsAuthoritative(this.paginationProfile)")
		assertFalse(rawRelocation.contains("this.writeCachedPaginationProfile(freshProfile)"))
		assertContains(pagination, "if (!this.paginationProfileIsAuthoritative(profile)) return null")
	}

	@Test
	fun paginationProfileTasksInvalidateBeforeLifecycleFingerprintChanges() {
		val root = readerAssetRoot()
		val appearance = root.resolve("navic-reader-appearance.js").readText()
		val viewport = root.resolve("navic-reader-viewport.js").readText()
		val runtime = root.resolve("navic-reader.js").readText()
		val settings = appearance
			.substringAfter("function applySettings(settings) {")
			.substringBefore("function applyThemeToLoadedContent(")
		val resize = viewport
			.substringAfter("function applyReaderViewportLayout(label = 'unknown', options = {}) {")
			.substringBefore("function applyReaderViewportLayoutToProfilerView(")
		val close = runtime
			.substringAfter("  close() {")
			.substringBefore("  onLoad(")

		assertTrue(
			settings.indexOf("this.clearPaginationProfileOwnership('settings-change')") in 0 until
				settings.indexOf("this.readerSettings = settings")
		)
		assertTrue(
			settings.indexOf("this.destroyPageTurnPreviewRenderer('settings-change')") in 0 until
				settings.indexOf("this.readerSettings = settings")
		)
		assertTrue(
			resize.indexOf("this.clearPaginationProfileOwnership('viewport-resize')") in 0 until
				resize.indexOf("setStylesImportant(document.documentElement")
		)
		assertTrue(
			resize.indexOf("this.destroyPageTurnPreviewRenderer('viewport-resize')") in 0 until
				resize.indexOf("setStylesImportant(document.documentElement")
		)
		assertTrue(
			settings.indexOf("this.startCompletePaginationProfileReplacementAfterLayout('settings-change')") >
				settings.indexOf("this.applyReaderViewportLayout('settings')")
		)
		assertTrue(
			resize.indexOf("this.startCompletePaginationProfileReplacementAfterLayout('viewport-resize')") >
				resize.indexOf("setStylesImportant(document.documentElement")
		)
		assertContains(
			readerAssetRoot().resolve("navic-reader-pagination.js").readText(),
			"this.currentPagePosition && liveContents.some(content => content?.doc)"
		)
		assertTrue(
			close.indexOf("this.invalidatePaginationProfileTask('reader-close')") in 0 until
				close.indexOf("this.publicationUrl = ''")
		)
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

	@Test
	fun androidPaginatorDoesNotThrowWhenBodyIsTemporarilyUnavailable() {
		val paginatorText = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()

		assertContains(
			paginatorText,
			"const documentStyleRoot = doc =>",
			message = "EPUB page turns can momentarily expose body-less documents; style reads need a safe element fallback."
		)
		assertContains(paginatorText, "doc?.body?.nodeType === 1")
		assertContains(paginatorText, "doc?.documentElement?.nodeType === 1")
		assertContains(paginatorText, "const body = documentStyleRoot(doc)")
		assertContains(paginatorText, "const root = doc?.documentElement?.nodeType === 1 ? doc.documentElement : body")
		val setImageSize = paginatorText
			.substringAfter("setImageSize() {")
			.substringBefore("\n    expand()")
		assertContains(
			setImageSize,
			"const body = documentStyleRoot(doc)",
			message = "Image sizing must also tolerate transient body-less documents during render."
		)
		assertContains(
			setImageSize,
			"if (!body) return",
			message = "Image sizing must skip incomplete transient documents instead of throwing."
		)
		assertFalse(
			setImageSize.contains("doc.body.querySelectorAll"),
			"Direct doc.body image queries throw when Foliate renders a transient body-less document."
		)
		assertFalse(
			paginatorText.contains("defaultView.getComputedStyle(doc.body)"),
			"Direct getComputedStyle(doc.body) throws when Foliate loads a transient body-less document."
		)
		assertFalse(
			paginatorText.contains("doc.defaultView.getComputedStyle(doc.body)"),
			"Direct getComputedStyle(doc.body) throws when Foliate loads a transient body-less document."
		)
	}

	@Test
	fun androidPaginatorDoesNotThrowWhenDocumentElementIsTemporarilyUnavailable() {
		val paginatorText = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val expand = paginatorText
			.substringAfter("expand() {")
			.substringBefore("\n    set overlayer(")
		val destroyView = paginatorText
			.substringAfter("destroy() {")
			.substringBefore("\n    }")
		val replaceBackground = paginatorText
			.substringAfter("#replaceBackground(background, columnCount) {")
			.substringBefore("\n    #applyVisibleViewport()")

		assertContains(
			expand,
			"if (this.#destroyed) return",
			message = "Queued ResizeObserver and font callbacks must become inert after the view is destroyed."
		)
		assertContains(
			expand,
			"const documentElement = doc?.documentElement",
			message = "Late ResizeObserver and font callbacks must tolerate an iframe between documents."
		)
		assertContains(expand, "const contentRoot = documentStyleRoot(doc)")
		assertContains(
			expand,
			"if (!documentElement?.isConnected || !contentRoot?.isConnected) return false",
			message = "Expansion must not measure a detached Foliate content root or transient null document root."
		)
		assertContains(
			expand,
			"this.#contentRange.selectNodeContents(contentRoot)",
			message = "Late body growth must refresh the measured range before comparing the layout signature."
		)
		assertFalse(
			expand.contains("const { documentElement } = this.document"),
			"Destructuring a transient iframe document leaves a null root that throws during measurement."
		)
		assertContains(
			destroyView,
			"this.#destroyed = true",
			message = "Destroyed views must invalidate queued asynchronous expansion callbacks."
		)
		assertContains(
			destroyView,
			"this.#observer.disconnect()",
			message = "Destroyed views must release all ResizeObserver targets without reading a transient iframe body."
		)

		assertContains(
			replaceBackground,
			"const root = doc?.documentElement?.nodeType === 1 ? doc.documentElement : documentStyleRoot(doc)",
			message = "EPUB page turns can momentarily expose a non-element documentElement; background replacement must choose a safe style element."
		)
		assertContains(
			replaceBackground,
			"if (!root || !doc.defaultView) return",
			message = "Paginator background replacement must skip transient documents instead of passing non-elements to getComputedStyle."
		)
		assertFalse(
			replaceBackground.contains("getComputedStyle(doc.documentElement)"),
			"Direct getComputedStyle(doc.documentElement) throws when Foliate exposes a transient non-element root during page turns."
		)
	}

	@Test
	fun androidPaginatorOwnsGenerationScopedTextPageCommitReceipts() {
		val paginator = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val invalidate = paginator
			.substringAfter("#invalidateTextPageCommit(reason) {")
			.substringBefore("\n    #advanceTextLayoutGeneration(reason) {")
		val advance = paginator
			.substringAfter("#advanceTextLayoutGeneration(reason) {")
			.substringBefore("\n    attributeChangedCallback(")
		val commit = paginator
			.substringAfter("async commitTextPage(index, pageIndex, reason = 'navigation') {")
			.substringBefore("\n    validateTextPageCommit(receipt) {")
		val ownership = paginator
			.substringAfter("#interruptedTextPageCommit({")
			.substringBefore("\n    async commitTextPage(")
		val validate = paginator
			.substringAfter("validateTextPageCommit(receipt) {")
			.substringBefore("\n    async goToTextPage(")
		val wrapper = paginator
			.substringAfter("async goToTextPage(index, pageIndex, reason = 'navigation') {")
			.substringBefore("\n    #scrollPrev(distance) {")
		val expand = paginator
			.substringAfter("expand() {")
			.substringBefore("\n    set overlayer(")
		val attributeChanged = paginator
			.substringAfter("attributeChangedCallback(name, oldValue, value) {")
			.substringBefore("\n    open(book) {")
		val open = paginator
			.substringAfter("open(book) {")
			.substringBefore("\n    #createView() {")
		val setStyles = paginator
			.substringAfter("setStyles(styles) {")
			.substringBefore("\n    focusView()")
		val render = paginator
			.substringAfter("render(reason = 'explicit-render') {")
			.substringBefore("\n    get scrolled()")
		val destroy = paginator
			.substringAfterLast("destroy() {")
			.substringBefore("\n    }")

		assertContains(paginator, "#layoutGeneration = 0")
		assertContains(paginator, "#viewGeneration = 0")
		assertContains(paginator, "#commitSequence = 0")
		assertContains(paginator, "#activeTextPageCommitReceipt = null")
		assertContains(paginator, "text-page-commit-invalidated")
		assertTrue(
			invalidate.indexOf("this.#activeTextPageCommitReceipt = null") <
				invalidate.indexOf("this.#dispatchTextPageCommitInvalidated("),
			"Receipt authority must be cleared before invalidation is published."
		)
		assertTrue(
			advance.indexOf("this.#activeTextPageCommitReceipt = null") <
				advance.indexOf("this.#layoutGeneration = nextLayoutGeneration"),
			"Active receipt authority must be cleared before its layout generation advances."
		)
		assertTrue(
			advance.indexOf("this.#layoutGeneration = nextLayoutGeneration") <
				advance.indexOf("this.#dispatchTextPageCommitInvalidated("),
			"Reentrant invalidation listeners must observe the advanced layout generation."
		)

		assertContains(commit, "view.expand()")
		assertContains(commit, "this.#recordContainerLayoutSignature()")
		assertContains(commit, "const fontLayoutGeneration = this.#layoutGeneration")
		assertContains(commit, "await view.document?.fonts?.ready")
		assertContains(commit, "layoutGeneration: fontLayoutGeneration")
		assertContains(commit, "this.#renderCurrentView()")
		assertContains(commit, "const measuredPageCount = this.pages - 2")
		assertContains(commit, "const actualTargetPageIndex = measuredPageCount > 0")
		assertContains(commit, "await this.#scrollToAnchor(actualTargetAnchor, reason)")
		assertContains(commit, "const position = this.exactTextPagePosition()")
		assertContains(commit, "const receipt = Object.freeze({")
		assertTrue(
			commit.indexOf("view.expand()") < commit.indexOf("this.#recordContainerLayoutSignature()") &&
				commit.indexOf("this.#recordContainerLayoutSignature()") <
				commit.indexOf("const fontLayoutGeneration = this.#layoutGeneration") &&
				commit.indexOf("const fontLayoutGeneration = this.#layoutGeneration") <
				commit.indexOf("await view.document?.fonts?.ready") &&
				commit.indexOf("await view.document?.fonts?.ready") <
				commit.indexOf("layoutGeneration: fontLayoutGeneration") &&
				commit.indexOf("layoutGeneration: fontLayoutGeneration") <
				commit.indexOf("this.#renderCurrentView()"),
			"Exact commitment must settle initial measurements, then fence font readiness with its layout generation before rendering."
		)
		assertTrue(
			commit.indexOf("await view.document?.fonts?.ready") < commit.indexOf("this.#renderCurrentView()") &&
				commit.indexOf("this.#renderCurrentView()") < commit.indexOf("await this.#scrollToAnchor(actualTargetAnchor, reason)"),
			"Current-document font readiness must precede transaction render and exact placement."
		)
		assertTrue(
			commit.indexOf("const actualTargetPageIndex = measuredPageCount > 0") <
				commit.indexOf("await this.#scrollToAnchor(actualTargetAnchor, reason)"),
			"Out-of-range requests must choose their bounded actual target before emitting relocation."
		)
		assertTrue(
			commit.indexOf("const position = this.exactTextPagePosition()") <
				commit.indexOf("const receipt = Object.freeze({"),
			"Paginator receipts must describe the actual position read after placement."
		)
		assertContains(commit, "status: 'unsupported'")
		assertContains(commit, "reason: 'unsupported-flow'")
		assertContains(ownership, "if (this.scrolled) return result('unsupported', 'unsupported-flow')")
		assertTrue(
			commit.indexOf("if (this.scrolled)") < commit.indexOf("await this.#acquireExactNavigationLock()"),
			"Scrolled flow must return unsupported before exact-navigation work."
		)

		assertContains(validate, "receipt !== this.#activeTextPageCommitReceipt")
		assertContains(validate, "const position = this.exactTextPagePosition()")
		assertContains(wrapper, "const result = await this.commitTextPage(index, pageIndex, reason)")
		assertContains(wrapper, "return result.status === 'committed'")
		assertFalse(wrapper.contains("#goTo("), "The Boolean compatibility wrapper must not own layout or section navigation.")
		assertFalse(wrapper.contains("#scrollToAnchor("), "The Boolean compatibility wrapper must not place pages independently.")

		assertContains(expand, "if (signature === this.#layoutSignature) return false")
		assertContains(
			expand,
			"if (this.onBeforeExpand?.(signature) === false || this.#destroyed) return false"
		)
		assertTrue(
			expand.indexOf("if (signature === this.#layoutSignature) return false") <
				expand.indexOf("this.onBeforeExpand?.(signature)"),
			"Duplicate measured expansion signatures must be no-ops before invalidation or DOM writes."
		)
		assertContains(paginator, "#observer = new ResizeObserver(() => this.expand())")
		assertContains(paginator, "this.#observer.observe(doc.body)")
		assertContains(paginator, "doc.fonts.ready.then(() => this.expand())")
		assertFalse(
			paginator.contains("#mutationObserver"),
			"Navic body injections must not trigger an extra full-range layout measurement hot path."
		)
		assertContains(expand, "this.#column ? null : [")
		assertContains(expand, "padding,")
		assertContains(expand, "rounded(this.#layout.margin)")
		assertContains(expand, "rounded(this.#layout.gap)")
		assertContains(expand, "rounded(this.#layout.columnWidth)")
		assertContains(paginator, "return !this.#destroyed && view === this.#view &&")
		assertContains(paginator, "layoutGeneration === this.#layoutGeneration")
		assertContains(open, "if (this.#view) this.#discardCandidateView(this.#view)")
		assertTrue(
			open.indexOf("if (this.#view) this.#discardCandidateView(this.#view)") <
				open.indexOf("this.sections = book.sections"),
			"Opening a replacement publication must discard its old committed View before replacing section ownership."
		)

		assertTrue(
			attributeChanged.indexOf("this.#advanceTextLayoutGeneration('attribute-change')") <
				attributeChanged.indexOf("this.#top.style.setProperty("),
			"Changed layout attributes must advance authority before mutating geometry."
		)
		assertTrue(
			setStyles.indexOf("this.#advanceTextLayoutGeneration('style-change')") <
				setStyles.indexOf("\$beforeStyle.textContent = nextStyles[0]"),
			"Changed typography must advance authority before mutating style content."
		)
		assertTrue(
			render.indexOf("this.#advanceTextLayoutGeneration(invalidationReason)") <
				render.indexOf("return this.#renderCurrentView()"),
			"Explicit and resize renders must invalidate before applying layout."
		)
		assertTrue(
			destroy.indexOf("this.#advanceTextLayoutGeneration('paginator-destroyed')") <
				destroy.indexOf("this.#destroyed = true"),
			"Paginator destruction must invalidate its active receipt before destroying state."
		)
	}

	@Test
	fun androidPaginatorInvalidatesLateCallbacksWhenViewsAreReplacedOrDestroyed() {
		val paginatorText = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val view = paginatorText
			.substringAfter("class View {")
			.substringBefore("\n}\n\n// NOTE:")
		val load = view
			.substringAfter("async load(src, afterLoad, beforeRender) {")
			.substringBefore("\n    render(layout)")
		val expand = view
			.substringAfter("expand() {")
			.substringBefore("\n    set overlayer(")
		val destroyView = view
			.substringAfter("destroy() {")
			.substringBefore("\n    }")
		val paginator = paginatorText.substringAfter("export class Paginator extends HTMLElement {")
		val display = paginator
			.substringAfter("async #display(promise) {")
			.substringBefore("\n    #canGoToIndex")
		val renderPaginator = paginator
			.substringAfter("#renderCurrentView() {")
			.substringBefore("\n    render(reason = 'explicit-render')")
		val afterScroll = paginator
			.substringAfter("#afterScroll(reason) {")
			.substringBefore("\n    async #display(promise)")
		val goTo = paginator
			.substringAfter("async #goTo({ index, anchor, select }")
			.substringBefore("\n    async goTo(target)")
		val open = paginator
			.substringAfter("open(book) {")
			.substringBefore("\n    #createView() {")
		val commitTextPage = paginator
			.substringAfter("async commitTextPage(index, pageIndex, reason = 'navigation') {")
			.substringBefore("\n    validateTextPageCommit(receipt)")
		val setStyles = paginator
			.substringAfter("setStyles(styles) {")
			.substringBefore("\n    focusView()")
		val destroyPaginator = paginator
			.substringAfterLast("destroy() {")
			.substringBefore("\n    }")

		assertContains(load, "if (this.#destroyed)")
		assertContains(load, "} catch (error) {")
		assertContains(load, "reject(error)")
		assertContains(expand, "if (this.#committed) this.onExpand()")
		assertContains(renderPaginator, "if (view.committed && view === this.#view)")
		assertContains(afterScroll, "if (!this.#view?.committed) return")
		assertContains(destroyView, "this.#cancelLoad?.()")
		assertContains(paginator, "#loadedSectionIndex = -1")
		assertContains(paginator, "#navigationGeneration = 0")
		assertContains(display, "generation !== this.#navigationGeneration")
		assertContains(display, "this.#discardCandidateView(view)")
		assertContains(
			display,
			"generation !== this.#navigationGeneration || view !== this.#view",
			message = "A destroyed or superseded iframe load must not restore stale paginator state."
		)
		assertTrue(
			display.indexOf("generation !== this.#navigationGeneration || view !== this.#view") <
				display.indexOf("this.#index = index"),
			"Paginator section identity must change only after the winning View finishes loading."
		)
		assertTrue(
			display.indexOf("view.markCommitted()") < display.indexOf("onLoad?.("),
			"Candidate View expansion callbacks must remain suppressed until its section identity commits."
		)
		assertTrue(
			display.indexOf("onLoad?.(") <
				display.indexOf("view !== this.#view) return false"),
			"Synchronous load listeners must not let a destroyed or superseded View continue committing."
		)
		assertContains(display, "return true")
		assertContains(goTo, "generation = ++this.#navigationGeneration")
		assertContains(goTo, "if (this.#destroyed || generation !== this.#navigationGeneration) return false")
		assertContains(goTo, "const targetSection = this.sections[index]")
		assertTrue(
			goTo.indexOf("generation !== this.#navigationGeneration") <
				goTo.indexOf("targetSection.load()"),
			"Superseded target promises must be rejected before allocating their section source."
		)
		assertContains(goTo, "let ownsTargetSection = true")
		assertContains(goTo, "if (!ownsTargetSection) return")
		assertContains(goTo, "targetSection?.unload?.()")
		assertContains(goTo, ".then(() => targetSection.load())")
		assertContains(goTo, "this.#loadedSectionIndex = index")
		assertContains(goTo, "index, src, anchor, generation, onLoad, onCancel, select,")
		assertContains(open, "if (this.#view) this.#discardCandidateView(this.#view)")
		assertContains(open, "this.sections?.[loadedSectionIndex]?.unload?.()")
		assertTrue(
			open.indexOf("if (this.#view) this.#discardCandidateView(this.#view)") <
				open.indexOf("this.sections = book.sections"),
			"Replacement publication sections must not be installed until the old View and section ownership are retired."
		)
		assertContains(commitTextPage, "const navigationGeneration = ++this.#navigationGeneration")
		assertContains(
			commitTextPage,
			"loaded = await this.#goTo({ index, anchor: 0 }, navigationGeneration)"
		)
		assertContains(
			commitTextPage,
			"if (!loaded) return ownership()"
		)
		assertContains(setStyles, "const view = this.#view")
		assertContains(setStyles, "view?.document?.fonts?.ready?.then(() => view.expand())")
		assertFalse(
			setStyles.contains("then(() => this.#view.expand())"),
			"Font readiness must target the View that registered the callback, not a replacement View."
		)
		assertContains(destroyPaginator, "this.#destroyed = true")
		assertContains(destroyPaginator, "++this.#navigationGeneration")
		assertContains(destroyPaginator, "this.sections?.[loadedSectionIndex]?.unload?.()")
	}

	@Test
	fun paginatorCancellationDoesNotPublishNavigationSuccess() {
		val vendorView = readerAssetRoot().resolve("vendor/foliate-js/view.js").readText()
		val goTo = vendorView
			.substringAfter("async goTo(target) {")
			.substringBefore("\n    async goToFraction(frac)")
		val goToFraction = vendorView
			.substringAfter("async goToFraction(frac) {")
			.substringBefore("\n    async select(target)")
		val pageTurnWrappers = vendorView
			.substringAfter("async prev(distance) {")
			.substringBefore("\n    goLeft()")
		val reader = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerGoTo = reader
			.substringAfter("async goTo(locator, reason = 'go-to') {")
			.substringBefore("\n\n  async applyHighlight(")
		val pageTurns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val progress = pageTurns
			.substringAfter("async function goToProgress(progress) {")
			.substringBefore("\n}\n\nasync function goToChapterProgress")
		val chapterProgress = pageTurns
			.substringAfter("async function goToChapterProgress(")
			.substringBefore("\n}\n\nfunction exactPageTurnSettlementMatches")
		val issueTurn = pageTurns
			.substringAfter("function issueReflowablePageTurn(direction) {")
			.substringBefore("\n}\n\nasync function performPageTurn")
		val performTurn = pageTurns
			.substringAfter("async function performPageTurn(direction) {")
			.substringBefore("\n}\n\nfunction attachScrolledEdgeTurnGestures")
		val location = readerAssetRoot().resolve("navic-reader-location.js").readText()
		val beginControlledRelocation = location
			.substringAfter("function beginControlledRelocation(reason) {")
			.substringBefore("\n}\n\nfunction cancelControlledRelocation")
		val cancelControlledRelocation = location
			.substringAfter("function cancelControlledRelocation(owner) {")
			.substringBefore("\n}\n\nfunction consumeControlledRelocationReason")
		val controlledRelocationFallback = location
			.substringAfter("function scheduleControlledRelocationFallback(")
			.substringBefore("\n}\n\nfunction onRelocate")
		val duplicateFallback = pageTurns
			.substringAfter("function handleDuplicatePageTurnRelocation(")
			.substringBefore("\n}\n\nfunction nativeDragPreviewAtSectionBoundary")

		assertContains(goTo, "if (committed === false) return false")
		assertTrue(
			goTo.indexOf("if (committed === false) return false") <
				goTo.indexOf("this.history.pushState(target)"),
			"Canceled Foliate navigation must not enter browser history."
		)
		assertContains(goToFraction, "if (committed === false) return false")
		assertContains(readerGoTo, "committed = await this.view.renderer.goTo(navigationTarget.rendererTarget)")
		assertContains(readerGoTo, "if (committed === false)")
		assertTrue(
			readerGoTo.indexOf("if (committed === false)") <
				readerGoTo.indexOf("this.scheduleControlledRelocationFallback(reason)"),
			"Canceled reader navigation must not schedule a success fallback."
		)
		assertContains(progress, "if (committed === false)")
		assertContains(progress, "this.cancelControlledRelocation(controlledRelocationOwner)")
		assertContains(chapterProgress, "if (committed === false)")
		assertContains(chapterProgress, "this.cancelControlledRelocation(controlledRelocationOwner)")
		assertContains(readerGoTo, "this.cancelControlledRelocation(controlledRelocationOwner)")
		assertContains(beginControlledRelocation, "this.controlledRelocateOwner = owner")
		assertContains(beginControlledRelocation, "return owner")
		assertContains(cancelControlledRelocation, "this.controlledRelocateOwner !== owner")
		assertContains(cancelControlledRelocation, "this.controlledRelocateReason = null")
		assertContains(controlledRelocationFallback, "owner = this.controlledRelocateOwner")
		assertContains(controlledRelocationFallback, "this.controlledRelocateOwner !== owner")
		assertContains(duplicateFallback, "if (committed === false)")
		assertContains(duplicateFallback, "this.cancelControlledRelocation(controlledRelocationOwner)")
		assertTrue(
			duplicateFallback.indexOf("this.scheduleControlledRelocationFallback(fallbackReason)") >
				duplicateFallback.indexOf("if (committed === false)"),
			"Canceled adjacent fallback navigation must not schedule relocation success."
		)
		assertContains(pageTurnWrappers, "return await this.renderer.prev(distance)")
		assertContains(pageTurnWrappers, "return await this.renderer.next(distance)")
		assertContains(issueTurn, "return false")
		assertContains(performTurn, "const previousFixedLayoutPageIndex = this.fixedLayoutNavigationPageIndex")
		assertContains(performTurn, "const previousFixedLayoutDirection = this.fixedLayoutNavigationDirection")
		assertContains(performTurn, "this.fixedLayoutNavigationPageIndex = previousFixedLayoutPageIndex")
		assertContains(performTurn, "this.fixedLayoutNavigationDirection = previousFixedLayoutDirection")
		assertContains(performTurn, "const committed = await reflowableNavigation")
		assertContains(performTurn, "if (committed === false)")
		assertTrue(
			performTurn.indexOf("if (committed === false)") <
				performTurn.indexOf("this.scheduleControlledRelocationFallback(`page-turn:${'$'}{direction}`)"),
			"Rejected paginator turns must not schedule a relocation success fallback."
		)
	}

	@Test
	fun androidPaginatorStyleHelperSkipsTransientNonElements() {
		val paginatorText = readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
		val styleHelper = paginatorText
			.substringAfter("const setStylesImportant = (el, styles) => {")
			.substringBefore("\nconst normalizeFrameSize =")

		assertContains(
			styleHelper,
			"if (!el?.style) return",
			message = "Paginator style writes must tolerate transient null/non-element targets during iframe page-turn lifecycle."
		)
		assertFalse(
			styleHelper.contains("const { style } = el"),
			"Destructuring style from a transient null target throws and aborts texture/page-turn harnesses."
		)
	}

}
