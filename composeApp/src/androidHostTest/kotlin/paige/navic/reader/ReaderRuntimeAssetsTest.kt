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
		val bridgeCore = root.resolve("navic-reader-bridge-core.js")
		val bridgeHelpers = root.resolve("navic-reader-helpers.js")
		val bridgeSettingsCore = root.resolve("navic-reader-settings-core.js")
		val bridgeSettings = root.resolve("navic-reader-settings.js")
		val bridgeMedia = root.resolve("navic-reader-media.js")
		val bridgeIdentity = root.resolve("navic-reader-identity.js")
		val bridgePaginationModel = root.resolve("navic-reader-pagination-model.js")
		val bridgeMotion = root.resolve("navic-reader-motion.js")
		val bridgePageTurns = root.resolve("navic-reader-page-turns.js")
		val bridgeContentInteractions = root.resolve("navic-reader-content-interactions.js")
		val bridgePagination = root.resolve("navic-reader-pagination.js")
		val bridgeAppearance = root.resolve("navic-reader-appearance.js")
		val bridgeShellCover = root.resolve("navic-reader-shell-cover.js")
		val bridgeViewport = root.resolve("navic-reader-viewport.js")
		val bridgeLocation = root.resolve("navic-reader-location.js")
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
		assertTrue(bridgeIdentity.isFile, "Navic reader identity module must be packaged")
		assertTrue(bridgePaginationModel.isFile, "Navic reader pagination model module must be packaged")
		assertTrue(bridgeMotion.isFile, "Navic reader motion module must be packaged")
		assertTrue(bridgePageTurns.isFile, "Navic reader page-turn module must be packaged")
		assertTrue(bridgeContentInteractions.isFile, "Navic reader content-interaction module must be packaged")
		assertTrue(bridgePagination.isFile, "Navic reader pagination module must be packaged")
		assertTrue(bridgeAppearance.isFile, "Navic reader appearance module must be packaged")
		assertTrue(bridgeShellCover.isFile, "Navic reader shell-cover module must be packaged")
		assertTrue(bridgeViewport.isFile, "Navic reader viewport module must be packaged")
		assertTrue(bridgeLocation.isFile, "Navic reader location module must be packaged")
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
		assertContains(bridgeText, "highlightMediaOverlayTextRange")
		assertContains(bridgeText, "textStart")
		assertContains(bridgeText, "textEnd")
		assertContains(bridgeText, "applyHighlights")
		assertContains(bridgeText, "publicationReady")
		assertContains(bridgeText, "overlayFragmentActive")
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
			helper.readLines().size <= 1_200,
			"navic-reader-helpers.js should stay below 1200 lines; settings and media-tap contracts belong in focused modules."
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
			bridge.readLines().size <= 1_200,
			"navic-reader.js should stay below 1200 lines; shell-cover, viewport, and location behavior belong in focused method modules."
		)
		listOf(
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
		assertContains(scriptText, "Get-TextFileRaw -Path \$bridgeDiagnosticsPath")
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
		assertContains(scriptText, "textureScrollLines=$((@(Select-String")
		assertContains(scriptText, "textureUpdateLines=$((@(Select-String")
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
		assertContains(probe, "reason: 'media-overlay-follow'")
		assertContains(probe, "visibleTextRange")
		assertContains(probe, "visibleRange.source !== 'media-overlay-follow'")
		assertContains(probe, "source=media-overlay-follow")
	}

	@Test
	fun androidReaderDoesNotLetMediaOverlayFollowInterruptUserRelocation() {
		val bridgeText = readerBridgeText()
		val applyOverlayFragment = bridgeText
			.substringAfter("async applyOverlayFragment(fragment) {")
			.substringBefore("\n  highlightMediaOverlayTextRange")

		assertContains(bridgeText, "mediaOverlayFollowShouldDeferForUserRelocation()")
		assertContains(applyOverlayFragment, "this.mediaOverlayFollowShouldDeferForUserRelocation()")
		assertContains(applyOverlayFragment, "media-overlay-follow:deferred")
		assertTrue(
			applyOverlayFragment.indexOf("this.mediaOverlayFollowShouldDeferForUserRelocation()") <
				applyOverlayFragment.indexOf("await this.goTo(targetHref, 'media-overlay-follow')"),
			"Playback-driven media-overlay follow must not start a second relocation over an active user go-to/page-turn."
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
		assertContains(probe, "Whispersync audiobook seek")
		assertContains(probe, "positionMs=263360")
		assertContains(probe, "overlayFragmentActive")
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
	fun adbWebViewEvalHelperCanReadRuntimeStateWithoutMutatingReader() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val runtimeStateProbe = helperText
			.substringAfter("async function runRuntimeStateProbe(page)")
			.substringBefore("async function runImageHitTargetsProbe(page)")

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
		assertContains(pageBoxProbe, "renderer.getAttribute('max-inline-size')")
		assertContains(pageBoxProbe, "renderer.getAttribute('max-block-size')")
		assertContains(pageBoxProbe, "renderer.getAttribute('max-column-count')")
		assertContains(pageBoxProbe, "renderer.getAttribute('top-margin')")
		assertContains(pageBoxProbe, "renderer.getAttribute('bottom-margin')")
		assertContains(pageBoxProbe, "closedShadowRoot")
		assertContains(pageBoxProbe, "rendererRect")
		assertContains(pageBoxProbe, "contentRects")
		assertContains(pageBoxProbe, "documentToViewportWidthRatio")
		assertContains(pageBoxProbe, "bodyToDocumentWidthRatio")
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
	fun adbWebViewEvalHelperCanProbePublisherStyleFontSizeOverride() {
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()
		val publisherProbe = helperText
			.substringAfter("async function runPublisherStyleFontSizeProbe(page)")
			.substringBefore("async function main()")

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
			.substringBefore("async function main()")

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
		assertContains(chapterProgressProbe, "endpoint(href, 1)")
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
		assertContains(currentChapterProgressProbe, "endpoint(href, 0)")
		assertContains(currentChapterProgressProbe, "endpoint(href, 1)")
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
		assertContains(postProbeGestureBlock, "tapText:")
		assertContains(postProbeGestureBlock, "tapDesc:")
		assertContains(postProbeGestureBlock, "tapDescFraction:")
		assertContains(postProbeGestureBlock, "Get-AdbUiNodeCenter")
		assertContains(postProbeGestureBlock, "Get-AdbUiNodeFractionPoint")
		assertContains(postProbeGestureBlock, "Invoke-PostProbeUiNodeAction")
		assertContains(postProbeGestureBlock, "Invoke-PostProbeUiNodeFractionAction")
		assertContains(postProbeGestureBlock, "Invoke-Adb @(\"shell\", \"input\", \"text\", \$text)")
		assertContains(postProbeGestureBlock, "Invoke-Adb @(\"shell\", \"input\", \"keyevent\", \$keyEvent)")
		assertContains(scriptText, "Dispatching reader engine command: \$requiredEngineCommand")
		assertContains(scriptText, "required engine command '\$requiredEngineCommand' was not captured")
		assertContains(scriptText, "foreach (\$requiredReaderLog in \$RequireReaderLog)")
		assertContains(scriptText, "required reader log '\$requiredReaderLog' was not captured")
		assertContains(scriptText, "Use tapDescFraction:value,xFraction,yFraction or tapDescFraction:value,xFraction,yFraction,waitMs.")
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
	fun adbReaderSmokeCanRequireNativeChapterRailEndpointAfterPostActionProbe() {
		val scriptText = repoScriptFile("adb-reader-smoke.ps1").readText()
		val matrixText = repoScriptFile("adb-reader-komikku-matrix.ps1").readText()
		val endpointValidationBlock = scriptText
			.substringAfter("function Assert-ReaderDevtoolsLocationEndpoint")
			.substringBefore("function Get-ReaderDevtoolsPdfVisibleResult")

		assertContains(scriptText, "[ValidateSet(\"\", \"start\", \"end\")]")
		assertContains(scriptText, "[string] \$RequirePostActionChapterPageEndpoint = \"\"")
		assertContains(endpointValidationBlock, "reader-devtools-post-action-probe.json")
		assertContains(endpointValidationBlock, "chapterPageIndex")
		assertContains(endpointValidationBlock, "chapterPageCount")
		assertContains(endpointValidationBlock, "Expected first chapter page")
		assertContains(endpointValidationBlock, "Expected last chapter page")
		assertContains(matrixText, "[switch] \$IncludeRailEndpointChecks")
		assertContains(matrixText, "[string[]] \$PostProbeAction = @()")
		assertContains(matrixText, "chapter-rail-native-start")
		assertContains(matrixText, "chapter-rail-native-end")
		assertContains(matrixText, "tapDescFraction:Chapter page slider,0.0,0.5")
		assertContains(matrixText, "tapDescFraction:Chapter page slider,1.0,0.5")
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
		assertContains(bodyAfterHelper, "Invoke-Adb @(\"logcat\", \"-d\", \"--pid=\$processId\", \"-v\", \"time\")")
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
		assertTrue(
			scriptText.indexOf("Assert-FocusedAndroidPackage -Package \$Package") <
				scriptText.indexOf("Invoke-AdbExecOutToFile -Arguments @(\"exec-out\", \"screencap\", \"-p\")"),
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
		assertContains(bridgeText, "pagination-profile:retained")
		assertContains(bridgeText, "observedChapterCount")
		assertContains(bridgeText, "source: observedPageCount ? 'observed' : 'estimated'")
		assertContains(bridgeText, "spineIndex: index")
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
		val replaceBackground = paginatorText
			.substringAfter("#replaceBackground(background, columnCount) {")
			.substringBefore("\n    #applyVisibleViewport()")

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
