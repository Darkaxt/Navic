package paige.navic.reader

import java.io.File
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FoliateAnxParityTest {

	private val root: File = sequence {
		var candidate = kotlin.io.path.Path("").toAbsolutePath()
		while (true) {
			yield(candidate)
			candidate = candidate.parent ?: break
		}
	}.first { candidate ->
		candidate.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt").exists()
	}.toFile()

	private fun anxReferenceFile(relativePath: String): File =
		listOf(
			root.resolve("tmp/references/anx-reader/$relativePath"),
			root.resolve("../tmp/references/anx-reader/$relativePath")
		).firstOrNull { it.isFile }
			?: error("Could not locate Anx reference: $relativePath")

	private val anxPlayerText: String by lazy {
		anxReferenceFile("lib/page/book_player/epub_player.dart").readText()
	}

	private val anxViewText: String by lazy {
		anxReferenceFile("assets/foliate-js/src/view.js").readText()
	}

	private val anxBookStyleText: String by lazy {
		anxReferenceFile("lib/models/book_style.dart").readText()
	}

	private val anxFootnotesText: String by lazy {
		anxReferenceFile("assets/foliate-js/src/footnotes.js").readText()
	}

	private val anxBookText: String by lazy {
		anxReferenceFile("assets/foliate-js/src/book.js").readText()
	}

	private val navicViewText: String by lazy {
		readerAssetRoot().resolve("vendor/foliate-js/view.js").readText()
	}

	private val navicFoliateReaderText: String by lazy {
		readerAssetRoot().resolve("vendor/foliate-js/reader.js").readText()
	}

	private val navicContentInteractionsText: String by lazy {
		readerAssetRoot().resolve("navic-reader-content-interactions.js").readText()
	}

	private val navicReaderMainText: String by lazy {
		readerRuntimeImplementationText()
	}

	private val navicReaderHelpersText: String by lazy {
		readerBridgeText()
	}

	private val navicReaderPaginationText: String by lazy {
		readerAssetRoot().resolve("navic-reader-pagination.js").readText()
	}

	private val navicPaginatorText: String by lazy {
		readerAssetRoot().resolve("vendor/foliate-js/paginator.js").readText()
	}

	private val readerEngineWebViewHostText: String by lazy {
		root.resolve("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt")
			.readText()
	}

	private val foliateEpubEngineAdapterText: String by lazy {
		readerCommonFile("FoliateEpubEngineAdapter.kt").readText()
	}

	private val readerBridgeProtocolText: String by lazy {
		readerCommonFile("ReaderBridgeProtocol.kt").readText()
	}

	private val readerChromeStateText: String by lazy {
		readerCommonFile("ReaderChromeState.kt").readText()
	}

	private val readerPreferenceSettingsText: String by lazy {
		readerCommonFile("ReaderPreferenceSettings.kt").readText()
	}

	private val nativeFrameHostText: String by lazy {
		readerNativeFrameHostFile().readText()
	}

	private val readerControllerText: String by lazy {
		readerCommonFile("ReaderController.kt").readText()
	}

	private val readerAppBarsText: String by lazy {
		readerCommonUiFile("ReaderAppBars.kt").readText()
	}

	private sealed class GapStatus {
		data class Exists(
			val note: String,
			val behaviorRoute: List<RouteStop> = emptyList()
		) : GapStatus()
		data class Missing(val targetPhase: Int, val note: String) : GapStatus()
		data class Partial(val targetPhase: Int, val note: String) : GapStatus()
		data class ProductDivergence(val navicRoute: List<RouteStop>, val rationale: String) : GapStatus()
		data class OutOfScope(val rationale: String) : GapStatus()
	}

	private data class RouteStop(val file: File, val symbol: String, val mustBeAbsent: Boolean = false)

	private fun routeStop(path: String, symbol: String): RouteStop {
		val file = when {
			path.endsWith(".kt") && path.startsWith("composeApp/") -> root.resolve(path)
			path.endsWith(".kt") -> readerCommonFile(path.substringAfterLast('/'))
			path.endsWith(".js") && path.startsWith("vendor/") -> readerAssetRoot().resolve(path)
			path.endsWith(".js") -> readerAssetRoot().resolve(path)
			else -> root.resolve(path)
		}
		return RouteStop(file, symbol)
	}

	private fun exists(note: String, vararg behaviorRoute: RouteStop): GapStatus.Exists =
		GapStatus.Exists(note = note, behaviorRoute = behaviorRoute.toList())

	private fun controllerTest(symbol: String): RouteStop =
		routeStop("composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderControllerTest.kt", symbol)

	private fun coordinatorTest(symbol: String): RouteStop =
		routeStop("composeApp/src/commonTest/kotlin/paige/navic/reader/ReaderCoordinatorTest.kt", symbol)

	private fun readerUiRoute(fileName: String, symbol: String): RouteStop =
		routeStop("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/$fileName", symbol)

	private val knownGaps: Map<String, GapStatus> = mapOf(
		"onClick" to GapStatus.ProductDivergence(
			navicRoute = listOf(
				RouteStop(readerNativeFrameHostFile(), "onSingleTapConfirmed"),
				RouteStop(readerNativeFrameHostFile(), "dispatchSingleTapAction"),
				RouteStop(readerAssetRoot().resolve("navic-reader-content-interactions.js"), "readerContentTapHandled"),
				RouteStop(readerCommonFile("ReaderBridgeProtocol.kt"), "ContentTapHandled"),
				RouteStop(readerCommonFile("FoliateEpubEngineAdapter.kt"), "ContentActionClaimed")
			),
			rationale = "Komikku owns short taps; content interaction is long-press. ContentTapHandled carries typed ReaderContentAction."
		),
		"onImageClick" to GapStatus.ProductDivergence(
			navicRoute = listOf(
				RouteStop(readerNativeFrameHostFile(), "onContentLongPress"),
				RouteStop(readerNativeFrameHostFile(), "onLongTapConfirmed"),
				RouteStop(readerCommonFile("ReaderBridgeProtocol.kt"), "ContentLongPressAt"),
				RouteStop(readerCommonFile("FoliateEpubEngineAdapter.kt"), "ContentLongPressAt"),
				RouteStop(readerAssetRoot().resolve("navic-reader-content-interactions.js"), "toggleSepiaImageOverlayFromEvent"),
				RouteStop(readerAssetRoot().resolve("navic-reader-content-interactions.js"), "handleNativeTapZoneContentLongPressAt")
			),
			rationale = "Anx opens ImageViewer; Navic toggles sepia overlay. Different behavior, not parity. Sepia toggle is the user's intended long-press behavior."
		),
		"handleBookmark" to GapStatus.ProductDivergence(
			navicRoute = listOf(
				RouteStop(readerCommonUiFile("ReaderAppBars.kt"), "onToggleBookmarked"),
				RouteStop(readerCommonFile("ReaderController.kt"), "currentLocationBookmarked"),
				RouteStop(readerCommonFile("ReaderController.kt"), "toggleCurrentBookmark")
			),
			rationale = "Komikku owns bookmark UI/control. No WebView annotation. Behavior (bookmark toggle at current location) is parity."
		),
		"click-image" to GapStatus.ProductDivergence(
			navicRoute = listOf(
				RouteStop(readerAssetRoot().resolve("vendor/foliate-js/view.js"), "click-image", mustBeAbsent = true),
				RouteStop(readerAssetRoot().resolve("navic-reader-content-interactions.js"), "readerContentTapHandled")
			),
			rationale = "Anx's click-as-content conflicts with Komikku's short-tap-as-navigation. Omission is correct by design."
		),
		"click-view" to GapStatus.ProductDivergence(
			navicRoute = listOf(
				RouteStop(readerAssetRoot().resolve("vendor/foliate-js/view.js"), "click-view", mustBeAbsent = true),
				RouteStop(readerNativeFrameHostFile(), "onSingleTapConfirmed")
			),
			rationale = "Same as click-image. Navic handles view taps at the native Komikku tap-zone layer."
		),
		"translateText" to GapStatus.OutOfScope(
			rationale = "Anx-specific translation service integration (text -> translation API). Not a reader behavior parity item."
		),
		"onRelocated" to exists(
			"LocationChanged with Anx relocation payload parity",
			controllerTest("engineRelocationFeedsControllerChromeWithoutEmittingEngineCommands"),
			coordinatorTest("relocationsRouteProgressSaveIntentThroughControllerWithoutEngineBridgeCommands"),
			routeStop("ReaderController.kt", "chapterProgress = state.chapterProgress.updatedFrom")
		),
		"onSetToc" to exists(
			"Toc",
			controllerTest("engineCapabilityEventsFeedControllerStateWithoutOwningChrome"),
			routeStop("ReaderController.kt", "is ReaderEngineEvent.Toc")
		),
		"onSearch" to exists(
			"SearchResults",
			coordinatorTest("searchRoutesThroughControllerAndCurrentEngineAdapter"),
			readerUiRoute("ReaderSearchDialog.kt", "KomikkuReaderSearchDialog"),
			readerUiRoute("ReaderRoot.kt", "ReaderControllerDialog.Search")
		),
		"renderAnnotations" to exists(
			"ApplyAnnotations command",
			coordinatorTest("selectionHighlightsRouteThroughControllerAndCurrentEngineAdapter"),
			routeStop("ReaderController.kt", "fun addSelectionHighlight"),
			readerUiRoute("ReaderSelectionActions.kt", "onHighlightSelection")
		),
		"relocate" to exists(
			"locationChanged with Anx relocation payload parity",
			controllerTest("engineRelocationFeedsControllerChromeWithoutEmittingEngineCommands"),
			coordinatorTest("relocationsRouteProgressSaveIntentThroughControllerWithoutEngineBridgeCommands"),
			routeStop("ReaderController.kt", "chrome = nextChrome")
		),
		"onLoadEnd" to exists(
			"LoadDoc event with serializable payload",
			controllerTest("loadedDocumentEventsFeedControllerStateWithoutNavigationCommands"),
			controllerTest("loadedDocumentBecomesChapterNavigationAnchorBeforeRelocationCatchesUp"),
			routeStop("ReaderController.kt", "loadedDocument = document"),
			routeStop("ReaderController.kt", "chapterProgress = state.chapterProgress.updatedFrom(document)")
		),
		"onExternalLink" to exists(
			"ExternalLink distinct event",
			controllerTest("externalLinksOpenControllerOwnedExternalLinkPrompt"),
			readerUiRoute("ReaderExternalLinkDialog.kt", "KomikkuReaderExternalLinkDialog"),
			readerUiRoute("ReaderScreen.kt", "uriHandler.openUri")
		),
		"onSelectionCleared" to exists(
			"SelectionCleared distinct event",
			controllerTest("selectionActionStateIsControllerOwnedAndClearedByEngine"),
			readerUiRoute("ReaderSelectionActions.kt", "KomikkuReaderSelectionActions"),
			readerUiRoute("ReaderRoot.kt", "selectionActions = controllerState.selectionActions")
		),
		"onAnnotationClick" to exists(
			"AnnotationClick from show-annotation",
			controllerTest("annotationClicksOpenControllerOwnedAnnotationPopup"),
			readerUiRoute("ReaderAnnotationDialog.kt", "KomikkuReaderAnnotationDialog"),
			readerUiRoute("ReaderRoot.kt", "controllerState.annotationPopup")
		),
		"onPushState" to exists(
			"PushState event updates history capabilities and surfaces the controller-owned history capsule",
			controllerTest("pushStateSurfacesHistoryCapsuleWhenHistoryIsAvailable"),
			readerUiRoute("ReaderRoot.kt", "KomikkuReaderHistoryCapsule"),
			readerUiRoute("ReaderScreen.kt", "coordinator.navigateHistoryBack()"),
			readerUiRoute("ReaderScreen.kt", "coordinator.navigateHistoryForward()"),
			routeStop("ReaderController.kt", "engineNavigation = ReaderEngineNavigationState"),
			routeStop("ReaderController.kt", "visible = event.canGoBack || event.canGoForward"),
			routeStop("ReaderController.kt", "ReaderEngineCommand.NavigateHistory"),
			routeStop("FoliateEpubEngineAdapter.kt", "ReaderBridgeCommand.HistoryBack"),
			routeStop("FoliateEpubEngineAdapter.kt", "ReaderBridgeCommand.HistoryForward"),
			routeStop("navic-reader.js", "case 'historyBack':"),
			routeStop("navic-reader.js", "case 'historyForward':")
		),
		"onFootnoteClose" to exists(
			"Footnote popup lifecycle with controller-owned open and close routes",
			controllerTest("footnoteOpenShowsControllerOwnedFootnotePopupAndCloseClearsIt"),
			readerUiRoute("ReaderFootnoteDialog.kt", "KomikkuReaderFootnoteDialog"),
			readerUiRoute("ReaderRoot.kt", "controllerState.footnotePopup"),
			routeStop("ReaderController.kt", "footnotePopup ="),
			routeStop("navic-reader-content-interactions.js", "type: 'footnoteOpen'"),
			routeStop("navic-reader-content-interactions.js", "readerResolvedFootnoteOpenPayload"),
			routeStop("navic-reader-content-interactions.js", "book?.resolveHref"),
			routeStop("navic-reader-content-interactions.js", "section.load"),
			routeStop("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt", "footnoteOpen("),
			routeStop("ReaderController.kt", "ReaderOverlayInteraction.FootnoteClosed")
		),
		"onPullUp" to exists(
			"PullUp event from scroll-end hook without implicit reader chrome toggling",
			controllerTest("ambiguousPullUpRecordsBridgeParityWithoutOpeningReaderMenu"),
			routeStop("ReaderController.kt", "ReaderOverlayInteraction.PullUp"),
			routeStop("ReaderController.kt", "menuVisible = state.menuVisible")
		),
		"link" to exists(
			"InternalLinkRequested with prevented/source semantics",
			controllerTest("anxBridgeEventsFeedControllerStateInsteadOfBeingDiscarded"),
			routeStop("ReaderController.kt", "lastLinkInteraction = ReaderLinkInteraction.Internal"),
			routeStop("navic-reader.js", "type: 'internalLink'")
		),
		"load" to exists(
			"LoadDoc event",
			controllerTest("loadedDocumentEventsFeedControllerStateWithoutNavigationCommands"),
			controllerTest("loadedDocumentPreventsChapterPageSeekFromTargetingPreviousSection"),
			routeStop("ReaderController.kt", "loadedDocument = document"),
			routeStop("ReaderController.kt", "chapterProgress = state.chapterProgress.updatedFrom(document)")
		),
		"external-link" to exists(
			"ExternalLink event",
			controllerTest("externalLinksOpenControllerOwnedExternalLinkPrompt"),
			readerUiRoute("ReaderExternalLinkDialog.kt", "KomikkuReaderExternalLinkDialog")
		),
		"draw-annotation" to exists(
			"AnnotationDrawn event",
			controllerTest("anxBridgeEventsFeedControllerStateInsteadOfBeingDiscarded"),
			routeStop("navic-reader.js", "Overlayer.highlight"),
			routeStop("ReaderController.kt", "ReaderAnnotationInteractionKind.Drawn")
		),
		"show-annotation" to exists(
			"AnnotationClick event",
			controllerTest("annotationClicksOpenControllerOwnedAnnotationPopup"),
			readerUiRoute("ReaderAnnotationDialog.kt", "KomikkuReaderAnnotationDialog")
		),
		"create-overlay" to exists(
			"OverlayCreated event",
			controllerTest("anxBridgeEventsFeedControllerStateInsteadOfBeingDiscarded"),
			routeStop("ReaderController.kt", "ReaderOverlayInteraction.Created")
		),
		"onSelectionEnd" to exists(
			"SelectionChanged carries Anx text/cfi/footnote/contextText/pos payload",
			controllerTest("selectionActionStateIsControllerOwnedAndClearedByEngine"),
			readerUiRoute("ReaderSelectionActions.kt", "KomikkuReaderSelectionActions"),
			readerUiRoute("ReaderRoot.kt", "selectionActions = controllerState.selectionActions")
		),
		"fontSize" to GapStatus.Exists("Navic has fontSizePercent (different scaling but concept exists)"),
		"fontFamily" to GapStatus.Exists("Navic has fontFamily"),
		"lineHeight" to GapStatus.Exists("Navic has lineHeight"),
		"paragraphSpacing" to GapStatus.Exists("Navic has paragraphSpacingPercent (different scaling but concept exists)"),
		"sideMargin" to GapStatus.Exists("Renderer gap matches Anx sideMargin semantics"),
		"fontWeight" to GapStatus.Exists("ReaderSettings and runtime CSS expose Anx fontWeight"),
		"letterSpacing" to GapStatus.Exists("ReaderSettings and runtime CSS expose Anx letterSpacing"),
		"wordSpacing" to GapStatus.Exists("ReaderSettings and runtime CSS expose Anx wordSpacing"),
		"topMargin" to GapStatus.Exists("Renderer top-margin exposes Anx topMargin"),
		"bottomMargin" to GapStatus.Exists("Renderer bottom-margin exposes Anx bottomMargin"),
		"indent" to GapStatus.Exists("ReaderSettings and runtime CSS expose Anx text indent"),
		"headingFontSize" to GapStatus.Exists("ReaderSettings and runtime CSS expose Anx headingFontSize"),
		"maxColumnCount" to GapStatus.Exists("ReaderSettings and runtime pagination expose Anx adaptive column count"),
		"columnThreshold" to GapStatus.Exists("ReaderSettings and runtime pagination expose Anx adaptive column threshold"),
	)

	private val anxHandlerNames: List<String> by lazy {
		Regex("""handlerName:\s*'([^']+)'""")
			.findAll(anxPlayerText)
			.map { it.groupValues[1] }
			.toList()
	}

	private val foliateEmitNames: List<String> by lazy {
		Regex("""#emit\(\s*'([^']+)'""")
			.findAll(anxViewText)
			.map { it.groupValues[1] }
			.distinct()
			.toList()
	}

	private val anxStyleFields: List<String> by lazy {
		val classBody = anxBookStyleText
			.substringAfter("class BookStyle {")
			.substringBefore("BookStyle({")
		Regex("""^\s+(?:double|int|String)\s+(\w+);""", RegexOption.MULTILINE)
			.findAll(classBody)
			.map { it.groupValues[1] }
			.toList()
	}

	private fun String.lineNumberFor(symbol: String): Int {
		val index = indexOf(symbol)
		if (index < 0) return -1
		return substring(0, index).count { it == '\n' } + 1
	}

	@Test
	fun everyAnxHandlerIsDocumentedInKnownGaps() {
		assertTrue(anxHandlerNames.isNotEmpty(), "Should extract handler names from epub_player.dart")
		val undocumented = anxHandlerNames.filter { it !in knownGaps }
		assertTrue(
			undocumented.isEmpty(),
			"Every Anx handlerName callback must be documented in knownGaps. Undocumented: $undocumented"
		)
	}

	@Test
	fun everyFoliateEmitIsDocumentedInKnownGaps() {
		assertTrue(foliateEmitNames.isNotEmpty(), "Should extract emit names from Anx view.js")
		val undocumented = foliateEmitNames.filter { it !in knownGaps }
		assertTrue(
			undocumented.isEmpty(),
			"Every Foliate #emit event must be documented in knownGaps. Undocumented: $undocumented"
		)
	}

	@Test
	fun everyAnxStyleDimensionIsDocumentedInKnownGaps() {
		assertTrue(anxStyleFields.isNotEmpty(), "Should extract style fields from book_style.dart")
		val undocumented = anxStyleFields.filter { it !in knownGaps }
		assertTrue(
			undocumented.isEmpty(),
			"Every Anx book_style.dart field must be documented in knownGaps. Undocumented: $undocumented"
		)
	}

	@Test
	fun productDivergenceRoutesAreVerified() {
		for ((key, status) in knownGaps) {
			if (status !is GapStatus.ProductDivergence) continue

			// Phase 1: verify each stop's symbol exists (or is absent) in its file
			val stopTexts = mutableMapOf<RouteStop, String>()
			for (stop in status.navicRoute) {
				assertTrue(stop.file.isFile, "Route file for $key must exist: ${stop.file.path}")
				val fileText = stop.file.readText()
				stopTexts[stop] = fileText
				if (stop.mustBeAbsent) {
					assertTrue(
						!fileText.contains(stop.symbol),
						"$key route: ${stop.file.name} must NOT contain '${stop.symbol}' (intentional omission)"
					)
				} else {
					assertTrue(
						fileText.contains(stop.symbol),
						"$key route: ${stop.file.name} must contain '${stop.symbol}'"
					)
				}
			}

			// Phase 2: verify intra-file ordering — consecutive stops in the same file
			// must have the first symbol on an earlier line than the second.
			// This proves they're in the same call-chain region, not unrelated.
			for (i in 0 until status.navicRoute.size - 1) {
				val current = status.navicRoute[i]
				val next = status.navicRoute[i + 1]
				if (current.mustBeAbsent || next.mustBeAbsent) continue
				if (current.file == next.file) {
					val text = stopTexts[current]!!
					val currentLine = text.lineNumberFor(current.symbol)
					val nextLine = text.lineNumberFor(next.symbol)
					assertTrue(
						currentLine >= 0 && nextLine >= 0,
						"$key route: both symbols must be found in ${current.file.name}"
					)
					assertTrue(
						currentLine <= nextLine,
						"$key route: '${current.symbol}' (line $currentLine) must appear before '${next.symbol}' (line $nextLine) in ${current.file.name}"
					)
				}
			}

		}
	}

	@Test
	fun outOfScopeEntriesHaveRationale() {
		for ((key, status) in knownGaps) {
			if (status !is GapStatus.OutOfScope) continue
			assertTrue(
				status.rationale.isNotBlank(),
				"$key is marked OutOfScope but has no rationale"
			)
			assertTrue(
				anxPlayerText.contains(key),
				"OutOfScope entry $key must exist in Anx reference"
			)
		}
	}

	@Test
	fun existsEntriesForAnxReaderBehaviorHaveVerifiedControllerOrUiRoutes() {
		val behaviorKeys = setOf(
			"onRelocated",
			"onSetToc",
			"onSearch",
			"renderAnnotations",
			"relocate",
			"onLoadEnd",
			"onExternalLink",
			"onSelectionCleared",
			"onAnnotationClick",
			"onPushState",
			"onFootnoteClose",
			"onPullUp",
			"link",
			"load",
			"external-link",
			"draw-annotation",
			"show-annotation",
			"create-overlay",
			"onSelectionEnd"
		)

		for (key in behaviorKeys) {
			val status = assertIs<GapStatus.Exists>(
				knownGaps[key],
				"$key must be tracked as an implemented behavior, not a missing or partial gap."
			)
			assertTrue(
				status.behaviorRoute.isNotEmpty(),
				"$key is marked Exists but has no verified controller/UI behavior route."
			)
			for (stop in status.behaviorRoute) {
				assertTrue(stop.file.isFile, "$key behavior route file must exist: ${stop.file.path}")
				val fileText = stop.file.readText()
				assertTrue(
					fileText.contains(stop.symbol),
					"$key behavior route: ${stop.file.name} must contain '${stop.symbol}'"
				)
			}
		}
	}

	@Test
	fun internalLinkEmitIsSuppressedInNativeTapZoneModeAndBridgedToEngine() {
		assertIs<GapStatus.Exists>(
			knownGaps["link"],
			"Anx/Foliate link emit is Phase 2 scope and must not remain a documented missing gap."
		)
		assertTrue(
			navicReaderMainText.contains("addEventListener('link'") &&
				navicReaderMainText.contains("nativeTapZones") &&
				navicReaderMainText.contains("event.preventDefault()") &&
				navicReaderMainText.contains("type: 'internalLink'") &&
				navicReaderMainText.contains("source: 'native-short-tap'"),
			"navic-reader.js must suppress Foliate internal link emits in native tap-zone mode and post internalLink."
		)
		assertTrue(
			navicContentInteractionsText.contains("type: 'internalLink'") &&
				navicContentInteractionsText.contains("prevented: false") &&
				navicContentInteractionsText.contains("source"),
			"Long-press link activation must post internalLink with prevented=false before direct goTo navigation."
		)
		assertTrue(
			readerBridgeProtocolText.contains("InternalLinkRequested") &&
				readerBridgeProtocolText.contains("\"internalLink\""),
			"ReaderBridgeProtocol must decode the internalLink bridge event."
		)
		assertTrue(
			foliateEpubEngineAdapterText.contains("ReaderEngineEvent.InternalLinkRequested"),
			"FoliateEpubEngineAdapter must map InternalLinkRequested into the engine event boundary."
		)
		assertTrue(
			readerEngineWebViewHostText.contains("ReaderBridgeEvent.InternalLinkRequested") &&
				readerEngineWebViewHostText.contains("internalLink("),
			"ReaderEngineWebViewHost must expose an ADB-visible debug label for internalLink."
		)
	}

	@Test
	fun searchResultsStreamProgressAndPartialResultsLikeAnx() {
		assertTrue(
			anxBookText.contains("for await (const result of reader.view.search") &&
				anxBookText.contains("callFlutter('onSearch', { process: result.progress })") &&
				anxBookText.contains("callFlutter('onSearch', result)"),
			"Anx streams search progress and each result instead of buffering the entire book search before notifying Flutter."
		)
		val searchBody = navicReaderMainText
			.substringAfter("async search(query)")
			.substringBefore("clearSearch()")
		assertTrue(
			searchBody.contains("postSearchResults(") &&
				searchBody.contains("progress") &&
				searchBody.contains("complete: false") &&
				searchBody.contains("complete: true"),
			"Navic reader search must post bridge-visible progress and partial result updates while Foliate search is still running."
		)
		assertTrue(
			searchBody.indexOf("postSearchResults(") < searchBody.indexOf("complete: true"),
			"Navic search must emit at least one partial bridge update before the final complete=true update."
		)
	}

	@Test
	fun phase3AnxBridgeEventsArePlumbedThroughRuntimeBridgeAndEngine() {
		val runtimeText = readerRuntimeImplementationText()
		data class Phase3Event(
			val bridgeEvent: String,
			val bridgeType: String,
			val engineEvent: String,
			val knownGapKeys: List<String>
		)
		val bridgeEvents = listOf(
			Phase3Event("ExternalLink", "externalLink", "ExternalLinkOpened", listOf("onExternalLink", "external-link")),
			Phase3Event("SelectionCleared", "selectionCleared", "SelectionCleared", listOf("onSelectionCleared")),
			Phase3Event("AnnotationClick", "annotationClick", "AnnotationClicked", listOf("onAnnotationClick", "show-annotation")),
			Phase3Event("AnnotationDrawn", "annotationDrawn", "AnnotationDrawn", listOf("draw-annotation")),
			Phase3Event("OverlayCreated", "overlayCreated", "OverlayCreated", listOf("create-overlay")),
			Phase3Event("LoadDoc", "loadDoc", "DocLoaded", listOf("onLoadEnd", "load")),
			Phase3Event("PushState", "pushState", "NavigationStateChanged", listOf("onPushState")),
			Phase3Event("FootnoteClose", "footnoteClose", "FootnoteClose", listOf("onFootnoteClose")),
			Phase3Event("PullUp", "pullUp", "PullUp", listOf("onPullUp"))
		)

		for (event in bridgeEvents) {
			for (gapKey in event.knownGapKeys) {
				assertIs<GapStatus.Exists>(
					knownGaps[gapKey],
					"${event.bridgeEvent} gap key '$gapKey' must not remain a documented missing Phase 3 gap."
				)
			}
			assertTrue(
				readerBridgeProtocolText.contains(event.bridgeEvent) &&
					readerBridgeProtocolText.contains("\"${event.bridgeType}\""),
				"ReaderBridgeProtocol must decode ${event.bridgeEvent} from bridge type '${event.bridgeType}'."
			)
			assertTrue(
				foliateEpubEngineAdapterText.contains("ReaderEngineEvent.${event.engineEvent}"),
				"FoliateEpubEngineAdapter must map bridge event ${event.bridgeEvent} into ${event.engineEvent}."
			)
			assertTrue(
				readerEngineWebViewHostText.contains("ReaderBridgeEvent.${event.bridgeEvent}") &&
					readerEngineWebViewHostText.contains("${event.bridgeType}("),
				"ReaderEngineWebViewHost must expose an ADB-visible debug label for ${event.bridgeType}."
			)
		}
		assertTrue(
			navicReaderMainText.contains("addEventListener('external-link'") &&
				runtimeText.contains("type: 'externalLink'"),
			"Runtime must bridge Foliate external-link emits to externalLink."
		)
		assertTrue(
			navicReaderMainText.contains("addEventListener('draw-annotation'") &&
				runtimeText.contains("type: 'annotationDrawn'"),
			"Runtime must bridge Foliate draw-annotation emits to annotationDrawn."
		)
		assertTrue(
			navicReaderMainText.contains("addEventListener('show-annotation'") &&
				runtimeText.contains("type: 'annotationClick'"),
			"Runtime must bridge Foliate show-annotation emits to annotationClick."
		)
		assertTrue(
			navicReaderMainText.contains("addEventListener('create-overlay'") &&
				runtimeText.contains("type: 'overlayCreated'"),
			"Runtime must bridge Foliate create-overlay emits to overlayCreated."
		)
		assertTrue(
			runtimeText.contains("type: 'loadDoc'") &&
				runtimeText.contains("type: 'pushState'") &&
				runtimeText.contains("type: 'selectionCleared'") &&
				runtimeText.contains("type: 'footnoteClose'") &&
				runtimeText.contains("type: 'pullUp'"),
			"Runtime must post all Phase 3 Anx bridge events."
		)
	}

	@Test
	fun drawAnnotationRuntimePaintsFoliateOverlayBeforeReportingBridgeEvent() {
		val anxAnnotationBody = anxViewText
			.substringAfter("async addAnnotation(annotation, remove)")
			.substringBefore("deleteAnnotation(annotation)")
		val foliateReaderAnnotationBody = navicFoliateReaderText
			.substringAfter("addEventListener('draw-annotation'")
			.substringBefore("addEventListener('show-annotation'")

		assertTrue(
			anxAnnotationBody.contains("draw") &&
				anxAnnotationBody.contains("draw-annotation"),
			"Anx/Foliate draw-annotation emits a draw callback, not just an informational event."
		)
		assertTrue(
			foliateReaderAnnotationBody.contains("draw(Overlayer.highlight") &&
				foliateReaderAnnotationBody.contains("{ color }"),
			"Foliate's reader paints annotation ranges by calling draw(Overlayer.highlight, { color })."
		)
		assertTrue(
			navicReaderMainText.contains("import { Overlayer } from './vendor/foliate-js/overlayer.js'") &&
				navicReaderMainText.contains("readerDrawNoteAnnotation") &&
				navicReaderMainText.contains("annotation.note") &&
				navicReaderMainText.contains("Overlayer.squiggly") &&
				navicReaderMainText.contains("hasNote ? readerDrawNoteAnnotation : Overlayer.highlight") &&
				navicReaderMainText.contains("type: 'annotationDrawn'"),
			"Navic must paint annotation ranges before reporting annotationDrawn, with note-bearing annotations visibly distinct from plain highlights."
		)
	}

	@Test
	fun phase3AnxBridgeEventsHaveControllerBehaviorRoutes() {
		data class ControllerRoute(
			val engineEvent: String,
			val controllerStateSymbol: String,
			val noOpBranch: String
		)
		val controllerRoutes = listOf(
			ControllerRoute(
				engineEvent = "InternalLinkRequested",
				controllerStateSymbol = "lastLinkInteraction",
				noOpBranch = "is ReaderEngineEvent.InternalLinkRequested -> ReaderControllerStep(this)"
			),
			ControllerRoute(
				engineEvent = "ExternalLinkOpened",
				controllerStateSymbol = "lastLinkInteraction",
				noOpBranch = "is ReaderEngineEvent.ExternalLinkOpened -> ReaderControllerStep(this)"
			),
			ControllerRoute(
				engineEvent = "AnnotationClicked",
				controllerStateSymbol = "lastAnnotationInteraction",
				noOpBranch = "is ReaderEngineEvent.AnnotationClicked -> ReaderControllerStep(this)"
			),
			ControllerRoute(
				engineEvent = "AnnotationDrawn",
				controllerStateSymbol = "lastAnnotationInteraction",
				noOpBranch = "is ReaderEngineEvent.AnnotationDrawn -> ReaderControllerStep(this)"
			),
			ControllerRoute(
				engineEvent = "OverlayCreated",
				controllerStateSymbol = "lastOverlayInteraction",
				noOpBranch = "is ReaderEngineEvent.OverlayCreated -> ReaderControllerStep(this)"
			),
			ControllerRoute(
				engineEvent = "DocLoaded",
				controllerStateSymbol = "loadedDocument",
				noOpBranch = "is ReaderEngineEvent.DocLoaded -> ReaderControllerStep(this)"
			),
			ControllerRoute(
				engineEvent = "NavigationStateChanged",
				controllerStateSymbol = "engineNavigation",
				noOpBranch = "is ReaderEngineEvent.NavigationStateChanged -> ReaderControllerStep(this)"
			),
			ControllerRoute(
				engineEvent = "FootnoteClose",
				controllerStateSymbol = "lastOverlayInteraction",
				noOpBranch = "ReaderEngineEvent.FootnoteClose -> ReaderControllerStep(this)"
			),
			ControllerRoute(
				engineEvent = "PullUp",
				controllerStateSymbol = "lastOverlayInteraction",
				noOpBranch = "is ReaderEngineEvent.PullUp -> ReaderControllerStep(this)"
			)
		)

		for (route in controllerRoutes) {
			assertTrue(
				readerControllerText.contains("ReaderEngineEvent.${route.engineEvent}"),
				"ReaderController must handle ${route.engineEvent}."
			)
			assertTrue(
				readerControllerText.contains(route.controllerStateSymbol),
				"${route.engineEvent} must feed controller state symbol '${route.controllerStateSymbol}', not just pass through the adapter."
			)
			assertTrue(
				!readerControllerText.contains(route.noOpBranch),
				"${route.engineEvent} must not be discarded with `${route.noOpBranch}`."
			)
		}
	}

	@Test
	fun footnotePopupRouteResolvesCrossSectionTargetsLikeAnxFootnoteHandler() {
		for (symbol in listOf("book.resolveHref(href)", "document.createElement('foliate-view')", "view.open(book)", "view.goTo(index)")) {
			assertTrue(
				anxFootnotesText.contains(symbol),
				"Anx FootnoteHandler must prove '$symbol' is part of the reference cross-section footnote route."
			)
		}
		assertTrue(
			navicContentInteractionsText.contains("readerResolvedFootnoteOpenPayload"),
			"Navic footnote route must have an async resolved-footnote helper instead of only reading the current document."
		)
		assertTrue(
			navicContentInteractionsText.contains("this.view?.book?.resolveHref") ||
				navicContentInteractionsText.contains("book.resolveHref") ||
				navicContentInteractionsText.contains("book?.resolveHref"),
			"Navic footnote route must use Foliate book.resolveHref so same-document and cross-section references share Anx semantics."
		)
		assertTrue(
			navicContentInteractionsText.contains("section.load"),
			"Navic footnote route must load the resolved target section before extracting cross-section footnote content."
		)
		assertTrue(
			navicContentInteractionsText.contains("readerFootnoteElementFromTarget"),
			"Navic footnote route must handle Foliate anchor results that are Element or Range targets."
		)
	}

	@Test
	fun phase4RelocationPayloadMatchesAnxLastLocationContract() {
		val runtimeText = readerRuntimeImplementationText()
		val anxRelocationBody = anxViewText
			.substringAfter("#onRelocate({ reason, range, index, fraction, size })")
			.substringBefore("if (cfi &&")

		for (symbol in listOf("reason", "range", "index", "fraction", "size", "tocItem", "pageItem", "cfi", "chapterLocation")) {
			assertTrue(
				anxRelocationBody.contains(symbol),
				"Anx relocation body must prove '$symbol' is part of the reference relocation contract."
			)
		}

		for (field in listOf("rangeCfi", "reason", "fraction", "size", "tocItemLabel", "pageItemLabel")) {
			assertTrue(
				readerBridgeProtocolText.contains("val $field"),
				"ReaderLocator must expose Anx relocation field '$field'."
			)
			assertTrue(
				readerBridgeProtocolText.contains("json.") && readerBridgeProtocolText.contains("\"$field\""),
				"ReaderBridgeProtocol must decode relocation field '$field' from locationChanged."
			)
			assertTrue(
				runtimeText.contains("$field:"),
				"Runtime locationChanged message must post relocation field '$field'."
			)
		}

		assertTrue(
			runtimeText.contains("rangeCfi: detail.cfi || null") ||
				runtimeText.contains("rangeCfi: detail.cfi ?? null"),
			"Runtime must use detail.cfi as the serializable range representation, not String(range)."
		)
		assertTrue(
				!runtimeText.contains("String(detail.range)") &&
				!runtimeText.contains("String(range)") &&
				!runtimeText.contains("`\${detail.range}`"),
			"Runtime must never stringify DOM Range objects into rangeCfi."
		)
		assertTrue(
			readerEngineWebViewHostText.contains("rangeCfi=") &&
				readerEngineWebViewHostText.contains("reason="),
			"ReaderEngineWebViewHost locationChanged debug label must expose rangeCfi and reason for ADB validation."
		)
	}

	@Test
	fun phase5SelectionPayloadMatchesAnxSelectionEndContract() {
		val runtimeText = readerRuntimeImplementationText()
		val anxSelectionBody = anxPlayerText
			.substringAfter("handlerName: 'onSelectionEnd'")
			.substringBefore("handlerName: 'onSelectionCleared'")

		for (symbol in listOf("cfi", "text", "footnote", "contextText", "pos", "left", "top", "right", "bottom")) {
			assertTrue(
				anxSelectionBody.contains(symbol),
				"Anx selection handler must prove '$symbol' is part of the reference selection contract."
			)
		}

		assertIs<GapStatus.Exists>(
			knownGaps["onSelectionEnd"],
			"onSelectionEnd must not remain a documented missing or partial Phase 5 gap."
		)
		for (field in listOf("footnote", "contextText", "posLeft", "posTop", "posRight", "posBottom")) {
			assertTrue(
				readerBridgeProtocolText.contains("val $field"),
				"SelectionChanged must expose Anx selection field '$field'."
			)
		}
		for (field in listOf("footnote", "contextText")) {
			assertTrue(
				readerBridgeProtocolText.contains("\"$field\"") &&
					foliateEpubEngineAdapterText.contains("$field = event.$field") &&
					readerControllerText.contains("$field = event.$field"),
				"Selection field '$field' must decode through bridge, adapter, and controller."
			)
		}
		for (field in listOf("left", "top", "right", "bottom")) {
			assertTrue(
				readerBridgeProtocolText.contains("\"$field\"") &&
					runtimeText.contains("$field:"),
				"Selection pos.$field must decode from bridge and be posted by runtime."
			)
		}
		assertTrue(
			runtimeText.contains("getBoundingClientRect") &&
				runtimeText.contains("type: 'selectionChanged'"),
			"Runtime must post selectionChanged with DOM selection bounds from getBoundingClientRect."
		)
		assertTrue(
			readerEngineWebViewHostText.contains("selectionChanged(") &&
				readerEngineWebViewHostText.contains("footnote=") &&
				readerEngineWebViewHostText.contains("pos="),
			"ReaderEngineWebViewHost must expose selection footnote and position in ADB-visible debug labels."
		)
	}

	@Test
	fun phase6StyleDimensionsMatchAnxBookStyleContract() {
		val anxStyleBody = anxBookStyleText
			.substringAfter("class BookStyle {")
			.substringBefore("BookStyle({")
		val phase6Fields = listOf(
			"fontWeight",
			"letterSpacing",
			"wordSpacing",
			"sideMargin",
			"topMargin",
			"bottomMargin",
			"indent",
			"headingFontSize"
		)

		for (field in phase6Fields) {
			assertTrue(
				anxStyleBody.contains(field),
				"Anx BookStyle must prove '$field' is part of the reference style contract."
			)
			assertIs<GapStatus.Exists>(
				knownGaps[field],
				"$field must not remain a documented missing or partial Phase 6 gap."
			)
			assertTrue(
				readerBridgeProtocolText.contains("val $field") &&
					readerBridgeProtocolText.contains("\"$field\""),
				"ReaderBridgeProtocol must serialize Anx style field '$field'."
			)
			assertTrue(
				readerChromeStateText.contains("$field: Double") &&
					readerChromeStateText.contains("$field = $field"),
				"ReaderChromeState normalization must carry Anx style field '$field'."
			)
			assertTrue(
				readerPreferenceSettingsText.contains(field),
				"ReaderPreferenceSettings must persist and restore Anx style field '$field'."
			)
			assertTrue(
				navicReaderHelpersText.contains(field) &&
					navicReaderPaginationText.contains(field),
				"Runtime helpers and pagination fingerprint must include Anx style field '$field'."
			)
		}
		assertTrue(
			navicReaderHelpersText.contains("font-weight: ${'$'}{fontWeight}") &&
				navicReaderHelpersText.contains("letter-spacing: ${'$'}{letterSpacing}px") &&
				navicReaderHelpersText.contains("word-spacing: ${'$'}{wordSpacing}px") &&
				navicReaderHelpersText.contains("text-indent: ${'$'}{textIndent}em") &&
				navicReaderHelpersText.contains("calc(2em * ${'$'}{headingFontSize})"),
			"readerContentCss must apply Anx text style dimensions with matching CSS semantics."
		)
		assertTrue(
			navicReaderHelpersText.contains("--reader-content-font-size") &&
				navicReaderHelpersText.contains("font-size: var(--reader-content-font-size") &&
				!navicReaderHelpersText.contains("font-size: 1rem !important") &&
				navicReaderHelpersText.contains("p span,\n  p font,") &&
				navicReaderHelpersText.contains("body > span:not(:has(img)):not(:has(svg)):not(:has(canvas))"),
			"Reader font-size controls must scale publisher inline ebook body text, not only headings; prose resets must inherit the reader-scaled body size instead of pinning to 1rem."
		)
		assertTrue(
			navicReaderMainText.contains("setAttribute('top-margin'") &&
				navicReaderMainText.contains("setAttribute('bottom-margin'") &&
				navicReaderMainText.contains("setAttribute('gap'"),
			"Navic Foliate renderer setup must apply Anx top/bottom/gap attributes."
		)
		val paginatorObservedAttributes = navicPaginatorText
			.substringAfter("static observedAttributes = [")
			.substringBefore("]")
		assertTrue(
			paginatorObservedAttributes.contains("'top-margin'") &&
				paginatorObservedAttributes.contains("'bottom-margin'"),
			"Bundled Foliate paginator must observe Anx topMargin/bottomMargin attributes; handling them in attributeChangedCallback is inert unless the custom element observes them."
		)
		assertTrue(
			navicPaginatorText.contains("case 'top-margin':") &&
				navicPaginatorText.contains("case 'bottom-margin':") &&
				navicPaginatorText.contains("--_top-margin") &&
				navicPaginatorText.contains("--_bottom-margin") &&
				navicPaginatorText.contains("var(--_top-margin)") &&
				navicPaginatorText.contains("var(--_bottom-margin)"),
			"Bundled Foliate paginator must consume Anx topMargin/bottomMargin separately; a single uniform margin is not Anx parity."
		)
	}

	@Test
	fun foliatePaginatorObservesAnxVerticalMarginAttributes() {
		val paginatorObservedAttributes = navicPaginatorText
			.substringAfter("static observedAttributes = [")
			.substringBefore("]")

		assertTrue(
			paginatorObservedAttributes.contains("'top-margin'"),
			"Foliate paginator must observe the top-margin attribute that Navic sets from Anx topMargin."
		)
		assertTrue(
			paginatorObservedAttributes.contains("'bottom-margin'"),
			"Foliate paginator must observe the bottom-margin attribute that Navic sets from Anx bottomMargin."
		)
	}

	@Test
	fun phase8AdaptiveCompositionFieldsMatchAnxBookStyleContract() {
		val anxStyleBody = anxBookStyleText
			.substringAfter("class BookStyle {")
			.substringBefore("BookStyle({")
		val phase8Fields = listOf("maxColumnCount", "columnThreshold")

		for (field in phase8Fields) {
			assertTrue(
				anxStyleBody.contains(field),
				"Anx BookStyle must prove '$field' is part of the reference style contract."
			)
			assertIs<GapStatus.Exists>(
				knownGaps[field],
				"$field must not remain a documented missing or partial Phase 8 gap."
			)
			assertTrue(
				readerBridgeProtocolText.contains("val $field") &&
					readerBridgeProtocolText.contains("\"$field\""),
				"ReaderBridgeProtocol must serialize Anx adaptive composition field '$field'."
			)
			assertTrue(
				readerChromeStateText.contains("$field:") &&
					readerChromeStateText.contains("$field = $field"),
				"ReaderChromeState normalization must carry Anx adaptive composition field '$field'."
			)
			assertTrue(
				readerPreferenceSettingsText.contains(field),
				"ReaderPreferenceSettings must persist and restore Anx adaptive composition field '$field'."
			)
			assertTrue(
				navicReaderHelpersText.contains(field) &&
					navicReaderPaginationText.contains(field),
				"Runtime helpers and pagination fingerprint must include Anx adaptive composition field '$field'."
			)
		}
		assertTrue(
			navicReaderHelpersText.contains("readerMaxColumnCountValue") &&
				navicReaderHelpersText.contains("readerColumnThresholdValue") &&
				navicReaderHelpersText.contains("columnThreshold: `${'$'}{Math.round(columnThreshold)}px`") &&
				navicReaderMainText.contains("setAttribute('column-threshold', pageBox.columnThreshold)") &&
				navicPaginatorText.contains("'column-threshold'") &&
				navicPaginatorText.contains("maxColumnCount === 0"),
			"readerAdaptiveFoliatePageBox must carry Anx maxColumnCount/columnThreshold settings through Navic shell resolution and the bundled paginator."
		)
		val adaptivePageBoxBody = navicReaderHelpersText
			.substringAfter("export const readerAdaptiveFoliatePageBox")
			.substringBefore("\n}\n\nexport const readerTypographyCss")
		assertTrue(
			!adaptivePageBoxBody.contains(": columnThreshold"),
			"Auto column mode must not hard-cap the full page inline size to columnThreshold; threshold decides splitting, not tablet page width."
		)
		assertTrue(
			adaptivePageBoxBody.contains("readerMaxColumnCountValue(settings)") &&
				adaptivePageBoxBody.contains("readerLandscapeSpreadColumnCount") &&
				!adaptivePageBoxBody.contains("readerEffectiveMaxColumnCount"),
			"Auto column mode must stay Anx-driven for portrait/narrow surfaces, while wide landscape must resolve to a two-page spread instead of a collapsed centered column."
		)
		assertTrue(
			!adaptivePageBoxBody.contains("marginPercent") &&
				!adaptivePageBoxBody.contains("naturalInlineReserve") &&
				!adaptivePageBoxBody.contains("naturalBlockReserve"),
			"readerAdaptiveFoliatePageBox must not apply Navic's legacy marginPercent reserve on top of Anx side/top/bottom margins; the Foliate attributes own page margins."
		)
		val paginatorBeforeRender = navicPaginatorText
			.substringAfter("#beforeRender({")
			.substringBefore("\n    render()")
		assertTrue(
			paginatorBeforeRender.contains("const maxInlineSize = parseFloat(style.getPropertyValue('--_max-inline-size'))") &&
				paginatorBeforeRender.contains("const columnThreshold = parseFloat(style.getPropertyValue('--_column-threshold')) || maxInlineSize") &&
				paginatorBeforeRender.contains("Math.ceil(size / columnThreshold)") &&
				!paginatorBeforeRender.contains("const maxInlineSize = parseFloat(style.getPropertyValue('--_column-threshold'))"),
			"Foliate paginator must use columnThreshold only to choose spread splitting; max-inline-size must remain the actual adaptive page width on tablet/fold portrait layouts."
		)
	}

	@Test
	fun foliateEpubEngineAdapterHasAnxCitation() {
		assertTrue(
			foliateEpubEngineAdapterText.contains("tmp/references/anx-reader/lib/page/book_player/epub_player.dart"),
			"FoliateEpubEngineAdapter.kt must cite its Anx reference source"
		)
	}

	@Test
	fun readerBridgeProtocolHasAnxCitation() {
		assertTrue(
			readerBridgeProtocolText.contains("tmp/references/anx-reader/lib/page/book_player/epub_player.dart"),
			"ReaderBridgeProtocol.kt must cite its Anx reference source"
		)
	}

	@Test
	fun navicReaderJsHasAnxCitation() {
		assertTrue(
			navicReaderMainText.contains("tmp/references/anx-reader/lib/page/book_player/epub_player.dart"),
			"navic-reader.js must cite its Anx reference source"
		)
	}

	@Test
	fun navicReaderContentInteractionsJsHasAnxCitation() {
		assertTrue(
			navicContentInteractionsText.contains("tmp/references/anx-reader/lib/page/book_player/epub_player.dart"),
			"navic-reader-content-interactions.js must cite its Anx reference source"
		)
	}
}
