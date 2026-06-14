package paige.navic.reader

import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderKomikkuBackboneResetTest {
	private val root = sequence {
		var candidate = kotlin.io.path.Path("").toAbsolutePath()
		while (true) {
			yield(candidate)
			candidate = candidate.parent ?: break
		}
	}.first { candidate ->
		candidate.resolve("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").exists()
	}

	@Test
	fun currentReaderImplementationIsVaultedAndNoLongerTheActiveEntryPoint() {
		val vaultRoot = root.resolve("vault/reader/2026-06-13-pre-komikku-reset")
		val activeReaderScreen = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		)
		val readerViewerHost = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewerHost.kt"
		)
		val activeText = activeReaderScreen.readText()
		val viewerHostText = readerViewerHost.readText()

		assertTrue(
			vaultRoot.resolve("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").exists(),
			"Old ReaderScreen.kt must be preserved in the vault before replacing the active reader."
		)
		assertTrue(
			vaultRoot.resolve("composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt").exists(),
			"Old ReaderOptionsPanel.kt must be preserved in the vault before replacing active reader options."
		)
		assertTrue(
			vaultRoot.resolve("composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt").exists(),
			"Old Android WebView reader host must be preserved in the vault."
		)
		assertTrue(
			vaultRoot.resolve("composeApp/src/androidMain/assets/reader/navic-reader.js").exists(),
			"Old Foliate runtime script must be preserved in the vault."
		)

		assertTrue(activeText.contains("KomikkuReaderRoot("))
		assertTrue(activeText.contains("KomikkuReaderNativeFrameHost("))
		assertTrue(activeText.contains("ReaderViewerHost("))
		assertTrue(activeText.contains("KomikkuComposeOverlay("))
		assertTrue(activeText.contains("KomikkuReaderAppBars("))
		assertTrue(activeText.contains("KomikkuChapterNavigator("))
		assertTrue(activeText.contains("KomikkuReaderBottomBar("))
		assertTrue(activeText.contains("KomikkuReaderPageIndicator("))
		assertTrue(activeText.contains("Ported from Komikku ReaderAppBars"))
		assertTrue(activeText.contains("Ported from Komikku ReaderPageIndicator"))
		assertTrue(
			activeText.contains("ReaderCoordinator("),
			"Active ReaderScreen.kt must route shell state through ReaderCoordinator instead of local page/menu ownership."
		)
		assertTrue(
			activeText.contains("ReaderPublicationRuntimeHost("),
			"Publication resolution should be reattached as a runtime input to the controller, not bypassed."
		)
		assertTrue(
			viewerHostText.contains("ReaderEngineRenderer.FoliatePublication"),
			"ReaderViewerHost.kt should mount EPUB through an engine renderer descriptor supplied by the active Komikku viewer."
		)
		assertTrue(
			viewerHostText.contains("ReaderEngineWebViewHost("),
			"Komikku-mounted EPUB content must use a dedicated engine WebView host, not the legacy reader surface host."
		)
		assertFalse(
			viewerHostText.contains("is WebViewPublicationReaderViewer"),
			"ReaderViewerHost.kt must not inspect concrete viewer classes; the viewer owns lifecycle/action mapping and supplies an engine renderer descriptor."
		)
		assertFalse(
			activeText.contains("ReaderEngineWebViewHost("),
			"Active ReaderScreen.kt must not select concrete renderer hosts inline."
		)
		assertFalse(
			activeText.contains("ReaderWebViewHost("),
			"The Komikku path must not mount the legacy WebView host because it still owns surface-level reader behavior."
		)
		assertFalse(
			activeText.contains("var menuVisible by remember"),
			"The active reader must not keep menu visibility as local screen-owned state."
		)
		assertFalse(
			activeText.contains("var virtualPage"),
			"The active reader must not keep a fake local page counter once controller/engine state is reattached."
		)
		assertFalse(
			activeText.contains("ReaderReadaloudRuntimeHost("),
			"Active ReaderScreen.kt must not instantiate the old readaloud runtime while the new backbone is being built."
		)
		assertFalse(
			activeText.contains("Scaffold("),
			"The Komikku reset root must not use a Scaffold content slot that can resize the viewer."
		)
		assertFalse(
			activeText.contains("bottomBar"),
			"The Komikku reset root must not give reader controls bottom-bar layout ownership."
		)
		assertFalse(
			activeText.contains("private fun KomikkuReaderContainer"),
			"The active common reader must not emulate Komikku's native reader_container with a Compose helper."
		)
		assertFalse(
			activeText.contains("private fun KomikkuReaderGestureLayer"),
			"The active common reader must not keep Compose as the owner of reader-wide gestures."
		)
	}

	@Test
	fun readerViewerHostConsumesEngineRendererDescriptorInsteadOfConcreteViewerClass() {
		val readerViewerFile = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewer.kt"
		)
		val readerViewerHost = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewerHost.kt"
		)
		val readerScreen = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		)
		val viewerText = readerViewerFile.readText()
		val hostText = readerViewerHost.readText()
		val screenText = readerScreen.readText()

		assertTrue(
			viewerText.contains("val engineRenderer: ReaderEngineRenderer"),
			"Komikku viewers must expose a render-only engine descriptor as their boundary with EPUB/PDF engines."
		)
		assertTrue(
			viewerText.contains("ReaderEngineRenderer.FoliatePublication.from(viewState)"),
			"Foliate-backed EPUB/PDF viewers must adapt their view state into a renderer descriptor before the host mounts content."
		)
		assertTrue(
			hostText.contains("engineRenderer: ReaderEngineRenderer"),
			"ReaderViewerHost must consume the engine renderer descriptor, not the concrete viewer implementation."
		)
		assertTrue(
			screenText.contains("engineRenderer = viewer.engineRenderer"),
			"KomikkuReaderRoot must pass the active viewer's engine renderer descriptor into the viewer container."
		)
		assertFalse(
			hostText.contains("viewer: ReaderViewer"),
			"ReaderViewerHost must not receive the viewer object; viewer lifecycle and navigation ownership stay outside renderer mounting."
		)
		assertFalse(
			hostText.contains("is WebViewPublicationReaderViewer"),
			"Renderer mounting must not branch on concrete viewer classes."
		)
	}

	@Test
	fun readerTopChromeUsesKomikkuBookmarkPageMarkInsteadOfMusicStar() {
		val activeReaderScreen = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		)
		val activeText = activeReaderScreen.readText()
		val topBarBody = activeText
			.substringAfter("private fun KomikkuReaderTopBar(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderBottomBar(")

		assertTrue(activeText.contains("onToggleCurrentBookmark = {"))
		assertTrue(activeText.contains("coordinator.toggleCurrentBookmark()"))
		assertTrue(topBarBody.contains("bookmarked: Boolean"))
		assertTrue(topBarBody.contains("canBookmark: Boolean"))
		assertTrue(topBarBody.contains("onToggleBookmarked: () -> Unit"))
		assertTrue(topBarBody.contains("Icons.Outlined.Bookmark"))
		assertTrue(topBarBody.contains("Icons.Outlined.BookmarkBorder"))
		assertFalse(
			topBarBody.contains("Icons.Outlined.Star") || topBarBody.contains("Icons.Filled.Star"),
			"Reader top chrome must use Komikku's bookmark/page-mark affordance, not the music favorite star."
		)
	}

	@Test
	fun komikkuEpubEngineHostDoesNotReuseLegacySurfaceGestureLayer() {
		val platformHosts = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		)
		val androidEngineHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt"
		)
		val iosEngineHost = root.resolve(
			"composeApp/src/iosMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.ios.kt"
		)

		assertTrue(platformHosts.readText().contains("expect fun ReaderEngineWebViewHost("))
		assertTrue(androidEngineHost.exists(), "Android must provide an engine-only WebView host for Komikku content.")
		assertTrue(iosEngineHost.exists(), "iOS must keep the expect/actual surface complete.")

		val androidText = androidEngineHost.readText()
		assertTrue(androidText.contains("actual fun ReaderEngineWebViewHost("))
		assertTrue(androidText.contains("ReaderWebRuntime.configure("))
		assertTrue(androidText.contains("ReaderBridgeCommand.OpenPublication("))
		assertFalse(
			androidText.contains("ReaderSurfaceHost"),
			"The engine-only host must not construct the legacy reader surface gesture layer."
		)
		assertFalse(
			androidText.contains("ReaderShellCoverView"),
			"The engine-only host must not own shell-cover rendering; the Komikku controller owns it."
		)
		assertFalse(
			androidText.contains("readerWideTapsEnabled"),
			"The engine-only host must not expose legacy reader-wide tap toggles."
		)
	}

	@Test
	fun newBackboneUsesPortedKomikkuViewerNavigationInsteadOfOnlyNavicTapHelpers() {
		val navigationFile = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/KomikkuViewerNavigation.kt"
		)
		val activeReaderScreen = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		)
		val androidNativeHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		)
		val navigationText = navigationFile.readText()
		val activeText = activeReaderScreen.readText()
		val androidText = androidNativeHost.readText()

		assertTrue(navigationText.contains("Ported from Komikku"))
		assertTrue(navigationText.contains("abstract class KomikkuViewerNavigation"))
		assertTrue(navigationText.contains("class KomikkuLNavigation"))
		assertTrue(navigationText.contains("class KomikkuKindlishNavigation"))
		assertTrue(navigationText.contains("class KomikkuEdgeNavigation"))
		assertTrue(navigationText.contains("class KomikkuRightAndLeftNavigation"))
		assertTrue(navigationText.contains("class KomikkuDisabledNavigation"))
		assertTrue(navigationText.contains("constantMenuRegion"))
		assertTrue(navigationText.contains("KomikkuTappingInvertMode"))
		assertTrue(navigationText.contains("fun invert(invertMode: KomikkuTappingInvertMode)"))
		assertTrue(navigationText.contains("getAction(pos: KomikkuPoint)"))
		assertTrue(activeText.contains("KomikkuReaderNavigator("))
		assertTrue(activeText.contains("KomikkuReaderNativeFrameHost("))
		assertTrue(androidText.contains("navigator.getAction("))
		assertTrue(androidText.contains("KomikkuReaderNativeNavigationOverlayView"))
		assertTrue(androidText.contains("navigator?.getRegions()?.forEach"))
		assertFalse(
			activeText.contains("readerTapZoneActionAt("),
			"The reset backbone should route taps through the ported Komikku navigator, not the old Navic tap-zone helper."
		)

		val viewerContainerBody = androidText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuReaderNativeNavigationOverlayView")
		assertTrue(
			viewerContainerBody.contains("override fun dispatchTouchEvent(event: MotionEvent): Boolean"),
			"The gesture layer must be owned by the native viewer container, matching Komikku's viewer-owned tap dispatch."
		)
	}

	@Test
	fun androidReaderRootUsesNativeKomikkuFrameLayoutHierarchy() {
		val activeReaderScreen = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		)
		val platformHosts = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		)
		val androidHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		)
		val activeText = activeReaderScreen.readText()
		val platformText = platformHosts.readText()

		assertTrue(
			androidHost.exists(),
			"Android must provide a native FrameLayout reader root, not only the common Compose fallback."
		)
		val androidText = androidHost.readText()

		assertTrue(activeText.contains("KomikkuReaderNativeFrameHost("))
		assertTrue(platformText.contains("expect fun KomikkuReaderNativeFrameHost("))
		assertTrue(androidText.contains("actual fun KomikkuReaderNativeFrameHost("))
		assertTrue(androidText.contains("AndroidView("))
		assertTrue(androidText.contains("FrameLayout(context)"))
		assertTrue(androidText.contains("readerContainer"))
		assertTrue(androidText.contains("viewerContainer"))
		assertTrue(androidText.contains("KomikkuReaderNativeNavigationOverlayView"))
		assertTrue(androidText.contains("ComposeView(context)"))
		assertTrue(androidText.contains("ViewGroup.FOCUS_BLOCK_DESCENDANTS"))
		assertTrue(androidText.contains("navigationOverlay.isClickable = false"))
		assertTrue(androidText.contains("navigationOverlay.isFocusable = false"))
		assertTrue(androidText.contains("super.dispatchTouchEvent(event)"))
		assertTrue(androidText.contains("gestureDetector.onTouchEvent(event)"))
		assertTrue(androidText.indexOf("super.dispatchTouchEvent(event)") < androidText.indexOf("gestureDetector.onTouchEvent(event)"))
		assertFalse(
			activeText.contains("Box(\n\t\tmodifier = Modifier\n\t\t\t.fillMaxSize()\n\t\t\t.background(Color(0xFF202329))"),
			"The active reader root must not remain a Compose-only Box emulating Komikku's native hierarchy."
		)
	}

	@Test
	fun commonControllerCoordinatorAndChromeDoNotExposeRawFoliateBridgeCommands() {
		val controllerText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt"
		).readText()
		val coordinatorText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt"
		).readText()
		val chromeText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderChromeState.kt"
		).readText()

		assertFalse(
			controllerText.contains("ReaderBridgeCommand"),
			"ReaderController must emit typed ReaderEngineCommand values, not raw Foliate/WebView bridge commands."
		)
		assertFalse(
			controllerText.contains("ReaderBridgeEvent"),
			"ReaderController must consume ReaderEngineEvent values directly, not rebuild bridge protocol events."
		)
		assertFalse(
			controllerText.contains("DispatchBridgeCommand"),
			"ReaderController must not keep a raw bridge escape hatch in the engine command model."
		)
		assertFalse(
			coordinatorText.contains("dispatchBridgeCommand"),
			"ReaderCoordinator must not expose raw bridge dispatch to the Komikku shell."
		)
		assertFalse(
			coordinatorText.contains("ReaderBridgeEvent"),
			"ReaderCoordinator must consume typed engine-host events, not raw Foliate bridge events."
		)
		assertFalse(
			chromeText.contains("ReaderBridgeCommand"),
			"ReaderChromeState must stay a shell/settings model and must not construct Foliate bridge commands."
		)
		assertFalse(
			chromeText.contains("ReaderBridgeEvent"),
			"ReaderChromeState must update from reader-domain location data, not from WebView bridge events."
		)
		assertFalse(
			chromeText.contains("toSettingsCommand"),
			"Reader settings must flow through ReaderEngineCommand.ApplySettings, not a chrome-owned bridge command helper."
		)
	}

	@Test
	fun readerMovementApiExposesViewerActionsInsteadOfLegacyControllerNavigationActions() {
		val controllerText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt"
		).readText()
		val coordinatorText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt"
		).readText()
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()

		assertTrue(
			controllerText.contains("fun onViewerAction(action: ReaderViewerAction): ReaderControllerStep"),
			"ReaderController must execute movement through the viewer-owned action contract."
		)
		assertTrue(
			coordinatorText.contains("fun onViewerAction(action: ReaderViewerAction): ReaderCoordinatorStep"),
			"ReaderCoordinator must expose viewer-owned movement to the shell."
		)
		assertTrue(
			readerScreenText.contains("onViewerAction(viewer.viewerActionFor(action))"),
			"ReaderScreen must translate native tap regions through the active viewer before reaching the controller."
		)
		assertFalse(
			controllerText.contains("ReaderControllerNavigationAction") ||
				coordinatorText.contains("ReaderControllerNavigationAction") ||
				readerScreenText.contains("ReaderControllerNavigationAction"),
			"Reader movement must not keep a parallel controller-navigation enum that can reclaim LEFT/RIGHT/scroll semantics from the viewer."
		)
		assertFalse(
			controllerText.contains("fun onReaderNavigationAction") ||
				coordinatorText.contains("fun onNavigationAction"),
			"Reader movement must enter through onViewerAction only."
		)
	}

	@Test
	fun activeKomikkuShellAndViewStateDoNotExposeRawFoliateBridgeProtocol() {
		val activeReaderText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val platformHostsText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		).readText()
		val androidEngineHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.android.kt"
		).readText()
		val iosEngineHostText = root.resolve(
			"composeApp/src/iosMain/kotlin/paige/navic/reader/ReaderEngineWebViewHost.ios.kt"
		).readText()
		val adapterText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt"
		).readText()
		val engineContractText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt"
		).readText()

		val expectEngineHostSignature = platformHostsText
			.substringAfter("expect fun ReaderEngineWebViewHost(")
			.substringBefore("\n)\n\n@Composable\nexpect fun ReaderOrientationEffect")
		val androidEngineHostSignature = androidEngineHostText
			.substringAfter("actual fun ReaderEngineWebViewHost(")
			.substringBefore("\n) {")
		val iosEngineHostSignature = iosEngineHostText
			.substringAfter("actual fun ReaderEngineWebViewHost(")
			.substringBefore("\n) {")
		val webViewPublicationState = engineContractText
			.substringAfter("data class WebViewPublication(")
			.substringBefore("\n\t) : ReaderEngineViewState")

		assertFalse(
			activeReaderText.contains("ReaderBridgeCommand"),
			"Active ReaderScreen.kt must not know about raw Foliate/WebView bridge commands."
		)
		assertFalse(
			activeReaderText.contains("ReaderBridgeEvent"),
			"Active ReaderScreen.kt must consume engine-host events, not raw WebView bridge events."
		)
		assertFalse(
			expectEngineHostSignature.contains("ReaderBridgeCommand") ||
				expectEngineHostSignature.contains("ReaderBridgeEvent"),
			"The common ReaderEngineWebViewHost contract must expose typed engine-host protocol, not raw bridge protocol."
		)
		assertFalse(
			androidEngineHostSignature.contains("ReaderBridgeCommand") ||
				androidEngineHostSignature.contains("ReaderBridgeEvent"),
			"Android ReaderEngineWebViewHost actual must hide raw bridge protocol behind the engine-host contract."
		)
		assertFalse(
			iosEngineHostSignature.contains("ReaderBridgeCommand") ||
				iosEngineHostSignature.contains("ReaderBridgeEvent"),
			"iOS ReaderEngineWebViewHost actual must hide raw bridge protocol behind the engine-host contract."
		)
		assertFalse(
			webViewPublicationState.contains("ReaderBridgeCommand"),
			"ReaderEngineViewState.WebViewPublication must not expose raw Foliate bridge commands to the shell."
		)
		assertTrue(
			platformHostsText.contains("ReaderEngineHostCommand") &&
				platformHostsText.contains("ReaderEngineHostEvent"),
			"The active engine host boundary should use explicit typed host command/event wrappers."
		)
		assertTrue(
			adapterText.contains("ReaderEngineHostCommand") &&
				adapterText.contains("ReaderEngineHostEvent"),
			"The Foliate adapter should be the layer that wraps/unwraps raw bridge protocol for the engine host."
		)
		val engineAdapterContract = engineContractText
			.substringAfter("interface ReaderEngine {")
			.substringBefore("\n}\n\ndata class ReaderEngineStep")
		assertFalse(
			engineAdapterContract.contains("ReaderBridgeEvent"),
			"The engine adapter contract must expose typed engine-host events, not raw Foliate bridge events."
		)
	}

	@Test
	fun genericReaderEngineContractIsNotOwnedByFoliateAdapter() {
		val engineContractPath = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt"
		)
		val foliateAdapterText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt"
		).readText()
		val controllerText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt"
		).readText()

		assertTrue(
			engineContractPath.exists(),
			"Generic reader engine contracts must live in ReaderEngine.kt, not in the Foliate adapter file."
		)
		val engineContractText = engineContractPath.readText()
		assertTrue(
			engineContractText.contains("interface ReaderEngine"),
			"ReaderEngine.kt must expose the renderer capability boundary that EPUB/PDF engines implement."
		)
		assertTrue(
			engineContractText.contains("sealed interface ReaderEngineCommand") &&
				engineContractText.contains("sealed interface ReaderEngineEvent") &&
				engineContractText.contains("sealed interface ReaderEngineViewState"),
			"ReaderEngine.kt must own the command/event/view-state model shared by all renderers."
		)
		assertFalse(
			foliateAdapterText.contains("interface ReaderEngineAdapter") ||
				foliateAdapterText.contains("sealed interface ReaderEngineViewState") ||
				foliateAdapterText.contains("data class ReaderEngineAdapterStep"),
			"FoliateEpubEngineAdapter must implement the generic engine contract, not define it."
		)
		assertFalse(
			controllerText.contains("sealed interface ReaderEngineCommand") ||
				controllerText.contains("sealed interface ReaderEngineEvent"),
			"ReaderController.kt must use generic engine contracts, not own the renderer protocol model."
		)
	}

	@Test
	fun readerScreenMountsViewerHostInsteadOfSelectingRendererViewsInline() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val viewerHostPath = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewerHost.kt"
		)

		assertTrue(
			viewerHostPath.exists(),
			"Komikku shell must mount a dedicated ReaderViewerHost instead of selecting renderer views inline."
		)
		val viewerHostText = viewerHostPath.readText()
		assertTrue(
			readerScreenText.contains("ReaderViewerHost("),
			"ReaderScreen should mount a viewer host into Komikku's viewer_container slot."
		)
		assertFalse(
			readerScreenText.contains("ReaderEngineWebViewHost("),
			"ReaderScreen must not mount the Foliate/WebView renderer directly."
		)
		assertFalse(
			readerScreenText.contains("ReaderEngineViewState.WebViewPublication"),
			"ReaderScreen must not switch on renderer-specific view-state variants."
		)
		assertTrue(
			viewerHostText.contains("ReaderEngineWebViewHost(") &&
				viewerHostText.contains("is ReaderEngineRenderer.FoliatePublication"),
			"ReaderViewerHost is the boundary allowed to translate the active engine renderer descriptor into a concrete renderer host."
		)
		assertFalse(
			viewerHostText.contains("is WebViewPublicationReaderViewer"),
			"ReaderViewerHost must not branch on concrete viewer classes when mounting renderer content."
		)
	}

	@Test
	fun shellCoverIsOwnedByNativeFrameHostNotCommonViewerCompose() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val viewerHostText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewerHost.kt"
		).readText()
		val platformHostsText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		).readText()
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()

		assertTrue(
			readerScreenText.contains("shellCoverVisible = controllerState.shellCoverVisible") &&
				readerScreenText.contains("val shellCoverUrl = viewer.shellCoverUrl") &&
				readerScreenText.contains("val shellCoverTitle = shellCoverTitleFor(reader, controllerState, viewer)") &&
				readerScreenText.contains("shellCoverUrl = shellCoverUrl") &&
				readerScreenText.contains("shellCoverTitle = shellCoverTitle"),
			"ReaderScreen must pass controller-owned shell cover state into the native Komikku frame host."
		)
		assertTrue(
			platformHostsText.contains("shellCoverVisible: Boolean") &&
				platformHostsText.contains("shellCoverUrl: String?") &&
				platformHostsText.contains("shellCoverTitle: String"),
			"The Komikku native frame host contract must own shell-cover inputs instead of hiding them in viewer content."
		)
		assertTrue(
			androidHostText.contains("KomikkuReaderNativeShellCoverView") &&
				androidHostText.contains("fun setShellCover(") &&
				androidHostText.contains("BitmapFactory.decodeFile(") &&
				androidHostText.contains("canvas.drawBitmap("),
			"Android must render the synthetic shell cover as a native frame-host layer, not a common Compose image."
		)
		assertFalse(
			viewerHostText.contains("AsyncImage") ||
				viewerHostText.contains("ReaderShellCoverSurface") ||
				viewerHostText.contains("shellCoverVisible"),
			"ReaderViewerHost must not draw the synthetic shell cover as common Compose content."
		)
	}

	@Test
	fun nativeShellCoverIsMountedInsideViewerContainerSoKomikkuGestureOwnerSeesCoverTouches() {
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val rootBody = androidHostText
			.substringAfter("private class KomikkuReaderNativeFrameRoot")
			.substringBefore("private class KomikkuReaderNativeShellCoverView")
		val viewerContainerBody = androidHostText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuReaderNativeNavigationOverlayView")
		val setViewerContentBody = androidHostText
			.substringAfter("fun setViewerContent(viewerKey: ReaderViewerKey")
			.substringBefore("fun setComposeOverlay")

		assertTrue(
			viewerContainerBody.contains("private val viewerContentContainer = FrameLayout(context)") &&
				viewerContainerBody.contains("fun setShellCoverView(shellCoverView: View)") &&
				viewerContainerBody.contains("addView(\n\t\t\tshellCoverView"),
			"The synthetic shell cover must be mounted inside the native viewer container so the Komikku gesture owner receives cover touches."
		)
		assertTrue(
			viewerContainerBody.contains("fun replaceViewerContent(viewerView: View)") &&
				viewerContainerBody.contains("viewerContentContainer.removeAllViews()") &&
				viewerContainerBody.contains("viewerContentContainer.addView(") &&
				setViewerContentBody.contains("viewerContainer.replaceViewerContent(viewerView)"),
			"Viewer swaps must replace only renderer content; removing viewerContainer children would detach the shell-cover input layer."
		)
		assertFalse(
			setViewerContentBody.contains("viewerContainer.removeAllViews()") ||
				setViewerContentBody.contains("viewerContainer.addView("),
			"Komikku viewer swaps must not treat the whole gesture owner as disposable renderer content."
		)
		assertFalse(
			rootBody.contains("addView(\n\t\t\tshellCoverView"),
			"The shell cover must not be a root sibling above the viewer container; that bypasses the viewer-owned gesture path."
		)
	}

	@Test
	fun activeKomikkuShellOpensControllerOwnedSettingsDialogInsteadOfEmptySettingsButton() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val controllerText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt"
		).readText()

		assertTrue(
			controllerText.contains("enum class ReaderControllerDialog") &&
				controllerText.contains("Settings"),
			"Reader dialogs must be controller-owned state, mirroring Komikku ReaderViewModel.Dialog.Settings."
		)
		assertTrue(
			readerScreenText.contains("onSettings = {") &&
				readerScreenText.contains("coordinator.openSettingsDialog()"),
			"The Komikku bottom settings button must open a controller-owned settings dialog instead of using an empty callback."
		)
		assertTrue(
			readerScreenText.contains("KomikkuReaderSettingsDialog(") &&
				readerScreenText.contains("controllerState.dialog == ReaderControllerDialog.Settings") &&
				readerScreenText.contains("onDismissRequest = onDismissDialog"),
			"Settings must render as a Komikku-style overlay dialog above the viewer, not as the old docked options panel."
		)
		assertFalse(
			readerScreenText.contains("ReaderOptionsPanel("),
			"The active Komikku reader must not reattach the old reader options panel as its settings surface."
		)
		assertFalse(
			readerScreenText.contains("IconButton(onClick = {}) {\n\t\t\t\tIcon(Icons.Filled.Settings"),
			"The active settings icon must not keep an empty callback."
		)
	}

	@Test
	fun settingsDialogAppliesTapZoneOverlayThroughControllerSettingsCommand() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val readingModePageText = root.resolve(
			"tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt"
		).readText()

		assertTrue(
			readingModePageText.contains("TapZonesItems(") &&
				readingModePageText.contains("screenModel.preferences.navigationModePager()::set") &&
				readingModePageText.contains("CheckboxItem(") &&
				readingModePageText.contains("screenModel.preferences.smallerTapZone()"),
			"The active settings migration must keep using Komikku's settings-page model as the source for tap-zone controls."
		)
		assertTrue(
			readerScreenText.contains("onSettingsChange: (ReaderSettings) -> Unit"),
			"The Komikku settings dialog must accept a settings-change callback instead of staying a static display shell."
		)
		assertTrue(
			readerScreenText.contains("onSettingsChange = { settings ->") &&
				readerScreenText.contains("coordinator.applySettings(settings)"),
			"ReaderScreen must route settings changes through ReaderCoordinator.applySettings so the controller and engine stay the owners."
		)
		assertTrue(
			readerScreenText.contains("Show tap zones") &&
				readerScreenText.contains("settings.copy(showTapZones = showTapZones)"),
			"The first migrated control should toggle tap-zone visualization through ReaderSettings, not through a local UI flag."
		)
		assertFalse(
			readerScreenText.contains("ReaderOptionsPanel("),
			"The active Komikku settings path must not resurrect the old docked ReaderOptionsPanel."
		)
	}

	@Test
	fun settingsDialogUsesKomikkuTapZonePresetControlsInsteadOfStaticLabels() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val readerPreferencesText = root.resolve(
			"tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt"
		).readText()
		val readingModePageText = root.resolve(
			"tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt"
		).readText()

		assertTrue(
			readerPreferencesText.contains("val TapZones = listOf(") &&
				readerPreferencesText.contains("MR.strings.l_nav") &&
				readerPreferencesText.contains("MR.strings.kindlish_nav") &&
				readerPreferencesText.contains("MR.strings.edge_nav"),
			"The settings dialog must keep using Komikku's tap-zone preset catalog as the source behavior."
		)
		assertTrue(
			readingModePageText.contains("ReaderPreferences.TapZones.mapIndexed") &&
				readingModePageText.contains("FilterChip(") &&
				readingModePageText.contains("onClick = { onSelect(index) }"),
			"Komikku exposes tap-zone presets as selectable chips, not a read-only settings label."
		)
		assertTrue(
			readerScreenText.contains("private val KomikkuTapZoneOptions = listOf("),
			"Navic must keep an explicit Komikku-order tap-zone list instead of relying on ReaderSupportedTapZones order."
		)
		assertTrue(
			readerScreenText.indexOf("ReaderTapZoneDefault to \"Default\"") <
				readerScreenText.indexOf("ReaderTapZoneLShaped to \"L shaped\"") &&
				readerScreenText.indexOf("ReaderTapZoneLShaped to \"L shaped\"") <
				readerScreenText.indexOf("ReaderTapZoneKindle to \"Kindle-ish\"") &&
				readerScreenText.indexOf("ReaderTapZoneKindle to \"Kindle-ish\"") <
				readerScreenText.indexOf("ReaderTapZoneEdge to \"Edge\"") &&
				readerScreenText.indexOf("ReaderTapZoneEdge to \"Edge\"") <
				readerScreenText.indexOf("ReaderTapZoneRightLeft to \"Right and Left\"") &&
				readerScreenText.indexOf("ReaderTapZoneRightLeft to \"Right and Left\"") <
				readerScreenText.indexOf("ReaderTapZoneDisabled to \"Disabled\""),
			"Navic's settings dialog tap-zone order must match Komikku: default, L-shaped, Kindle-ish, edge, right/left, disabled."
		)
		assertTrue(
			readerScreenText.contains("KomikkuSettingsChipRow(") &&
				readerScreenText.contains("FilterChip(") &&
				readerScreenText.contains("onSettingsChange(settings.copy(tapZone = tapZone))"),
			"Tap-zone presets must be real settings chips routed through ReaderSettings."
		)
		assertTrue(
			readerScreenText.contains("Smaller tap zones") &&
				readerScreenText.contains("settings.copy(smallerTapZone = smallerTapZone)"),
			"Komikku's smaller tap-zone checkbox must migrate as a real settings control."
		)
		assertFalse(
			readerScreenText.contains("KomikkuSettingsDialogLine(\"Tap zones:"),
			"Tap zones must no longer be displayed only as a static line in the Komikku settings dialog."
		)
	}

	@Test
	fun settingsDialogUsesKomikkuReadingModePresetControlsInsteadOfStaticLabels() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val readingModePageText = root.resolve(
			"tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/settings/ReadingModePage.kt"
		).readText()
		val readingModeModelText = root.resolve(
			"tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReadingMode.kt"
		).readText()

		assertTrue(
			readingModePageText.contains("SettingsChipRow(MR.strings.pref_category_reading_mode)") &&
				readingModePageText.contains("ReadingMode.entries.map") &&
				readingModePageText.contains("FilterChip(") &&
				readingModePageText.contains("screenModel.onChangeReadingMode(it)"),
			"Komikku exposes reading mode as selectable chips in the Reading mode settings page."
		)
		assertTrue(
			readingModeModelText.contains("DEFAULT(") &&
				readingModeModelText.contains("LEFT_TO_RIGHT(") &&
				readingModeModelText.contains("RIGHT_TO_LEFT(") &&
				readingModeModelText.contains("VERTICAL(") &&
				readingModeModelText.contains("WEBTOON(") &&
				readingModeModelText.contains("CONTINUOUS_VERTICAL("),
			"Navic must map the full Komikku reading-mode catalog, not only the old paged/scrolled toggle."
		)
		assertTrue(
			readerScreenText.contains("private val KomikkuReadingModeOptions = listOf("),
			"Navic must keep an explicit Komikku-order reading-mode list instead of showing flow/direction as labels."
		)
		assertTrue(
			readerScreenText.indexOf("label = \"Default\"") <
				readerScreenText.indexOf("label = \"Paged (left to right)\"") &&
				readerScreenText.indexOf("label = \"Paged (left to right)\"") <
				readerScreenText.indexOf("label = \"Paged (right to left)\"") &&
				readerScreenText.indexOf("label = \"Paged (right to left)\"") <
				readerScreenText.indexOf("label = \"Paged (vertical)\"") &&
				readerScreenText.indexOf("label = \"Paged (vertical)\"") <
				readerScreenText.indexOf("label = \"Long strip\"") &&
				readerScreenText.indexOf("label = \"Long strip\"") <
				readerScreenText.indexOf("label = \"Long strip with gaps\""),
			"Navic's reading-mode options must follow Komikku's entry order."
		)
		assertTrue(
			readerScreenText.contains("KomikkuSettingsReadingModeRow(") &&
				readerScreenText.contains("onSettingsChange(settings.copy(") &&
				readerScreenText.contains("flowMode = option.flowMode") &&
				readerScreenText.contains("paged = option.paged") &&
				readerScreenText.contains("direction = option.direction"),
			"Reading-mode chips must write Navic flow, paged, and direction settings through the controller path."
		)
		assertFalse(
			readerScreenText.contains("KomikkuSettingsDialogLine(\"Reading mode:") ||
				readerScreenText.contains("KomikkuSettingsDialogLine(\"Direction:"),
			"Reading mode and direction must no longer be read-only labels in the Komikku settings dialog."
		)
	}

	@Test
	fun readerViewerHostUsesKomikkuViewerLifecycleBoundary() {
		val viewerFile = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewer.kt"
		)
		val viewerHost = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewerHost.kt"
		)

		assertTrue(
			viewerFile.exists(),
			"Navic needs a Komikku-equivalent ReaderViewer lifecycle boundary before adding more EPUB/PDF features."
		)
		val viewerText = viewerFile.readText()
		val viewerHostText = viewerHost.readText()

		assertTrue(viewerText.contains("interface ReaderViewer"))
		assertFalse(viewerText.contains("sealed interface ReaderViewer"))
		assertTrue(viewerText.contains("fun destroy()"))
		assertTrue(viewerText.contains("class ReaderViewerLifecycleSlot"))
		assertTrue(viewerText.contains("fun readerViewerFor("))
		assertTrue(viewerText.contains("ReaderViewerKind.WebViewPublication"))
		assertTrue(viewerText.contains("val engineRenderer: ReaderEngineRenderer"))
		assertTrue(viewerHostText.contains("engineRenderer: ReaderEngineRenderer"))
		assertTrue(viewerHostText.contains("ReaderEngineContent("))
		assertFalse(
			viewerHostText.contains("readerViewerFor(") || viewerHostText.contains("DisposableEffect"),
			"ReaderViewerHost must mount the active viewer supplied by the Komikku root, not create a second viewer lifecycle."
		)
		assertFalse(
			viewerHostText.contains("when (viewState)"),
			"ReaderViewerHost must not keep acting like an inline renderer switch; it should mount a viewer boundary."
		)
	}

	@Test
	fun readerRootKeepsSingleActiveViewerForHostAndNavigationActions() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val viewerHostText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewerHost.kt"
		).readText()

		assertTrue(
			readerScreenText.contains("val viewerSlot = remember { ReaderViewerLifecycleSlot() }") &&
				readerScreenText.contains("val viewer = remember(viewerSlot, viewState) { viewerSlot.update(viewState) }") &&
				readerScreenText.contains("DisposableEffect(viewerSlot)") &&
				readerScreenText.contains("viewerSlot.dispose()"),
			"KomikkuReaderRoot must create, retain, swap, and dispose the active ReaderViewer through an explicit lifecycle slot."
		)
		assertTrue(
			readerScreenText.contains("viewerKey = viewer.key"),
			"The native frame slot must be keyed by the retained viewer instance, not by a parallel identity calculation."
		)
		assertTrue(
			readerScreenText.contains("onViewerAction(viewer.viewerActionFor(action))"),
			"Native tap regions must become viewer-owned actions through the retained active viewer."
		)
		assertTrue(
			readerScreenText.contains("engineRenderer = viewer.engineRenderer"),
			"ReaderViewerHost must receive the retained active viewer's engine renderer descriptor from the reader root."
		)
		assertFalse(
			readerScreenText.contains("readerViewerFor(viewState).viewerActionFor(action)") ||
				readerScreenText.contains("readerViewerFor(viewState).navigationActionFor(action)") ||
				readerScreenText.contains("viewer.navigationActionFor(action)"),
			"ReaderScreen must not create a throwaway viewer or route native tap regions through the legacy navigation action path."
		)
		assertTrue(
			viewerHostText.contains("engineRenderer: ReaderEngineRenderer"),
			"ReaderViewerHost must mount renderer content from the active viewer's engine descriptor supplied by KomikkuReaderRoot."
		)
		assertFalse(
			viewerHostText.contains("readerViewerFor(") || viewerHostText.contains("DisposableEffect"),
			"ReaderViewerHost must not create or dispose a second viewer instance."
		)
	}

	@Test
	fun nativeFrameHostSwapsViewerContentByReaderViewerKeyLikeKomikkuUpdateViewer() {
		val platformHostsText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		).readText()
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()

		assertTrue(
			platformHostsText.contains("viewerKey: ReaderViewerKey"),
			"Native frame host API must receive the active viewer identity, mirroring Komikku updateViewer swaps."
		)
		assertTrue(
			readerScreenText.contains("viewerKey = viewer.key"),
			"ReaderScreen must pass the retained viewer identity into the native viewer_container."
		)
		assertTrue(
			androidHostText.contains("private var currentViewerKey: ReaderViewerKey? = null"),
			"Android native frame must remember which viewer is currently mounted."
		)
		assertTrue(
			androidHostText.contains("fun setViewerContent(viewerKey: ReaderViewerKey"),
			"Android native frame must key viewer content updates by ReaderViewerKey."
		)
		assertTrue(
			androidHostText.contains("viewerContainer.replaceViewerContent(viewerView)") &&
				androidHostText.contains("viewerContentContainer.removeAllViews()") &&
				androidHostText.contains("viewerContentContainer.addView("),
			"Android native frame must replace the renderer child inside the viewer container when the viewer key changes."
		)
		assertFalse(
			androidHostText.contains("viewerContainer.removeAllViews()"),
			"Android native frame must not remove all viewer-container children because shell-cover input lives there too."
		)
		assertFalse(
			androidHostText.contains("private val viewerComposeView = ComposeView(context)"),
			"Android native frame must not keep a permanent viewer child that survives all viewer swaps."
		)
	}

	@Test
	fun nativeFrameHostDisposesOldViewerCompositionWhenSwappingLikeKomikkuUpdateViewer() {
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()

		val swapBody = androidHostText
			.substringAfter("fun setViewerContent(viewerKey: ReaderViewerKey")
			.substringBefore("fun setComposeOverlay")

		assertTrue(
			swapBody.contains("currentViewerComposeView?.disposeComposition()") &&
				swapBody.indexOf("currentViewerComposeView?.disposeComposition()") <
				swapBody.indexOf("viewerContainer.replaceViewerContent(viewerView)"),
			"Android native frame must dispose the old viewer composition before replacing the renderer child."
		)
		assertTrue(
			androidHostText.contains("override fun onDetachedFromWindow()") &&
				androidHostText.contains("currentViewerComposeView?.disposeComposition()") &&
				androidHostText.contains("composeOverlay.disposeComposition()"),
			"Android native frame must dispose active viewer and overlay compositions when the root leaves the window."
		)
	}

	@Test
	fun readerScreenPersistsControllerProgressIntentThroughBinderyBoundary() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()

		assertTrue(
			readerScreenText.contains("applyReaderCoordinatorStep("),
			"ReaderScreen must consume coordinator steps through the app-boundary helper instead of dropping side effects."
		)
		assertTrue(
			readerScreenText.contains("koinInject<BinderyRepository>()"),
			"ReaderScreen must obtain BinderyRepository at the app boundary for persisted reading progress."
		)
		assertTrue(
			readerScreenText.contains("rememberCoroutineScope()"),
			"ReaderScreen should launch progress persistence from a Compose-owned scope."
		)
		assertTrue(
			readerScreenText.contains("putReadingProgress(progress)"),
			"ReaderScreen must persist ReaderCoordinatorStep.progressToSave through BinderyRepository."
		)
		assertFalse(
			readerScreenText.contains("fun applyCoordinatorStep(step: ReaderCoordinatorStep) {\r\n\t\tcoordinator = step.coordinator\r\n\t}") ||
				readerScreenText.contains("fun applyCoordinatorStep(step: ReaderCoordinatorStep) {\n\t\tcoordinator = step.coordinator\n\t}"),
			"ReaderScreen must not reduce coordinator steps to state assignment and discard progressToSave."
		)
	}

	@Test
	fun readerPublicationRuntimeLoadsSavedBinderyProgressBeforeOpeningEngine() {
		val platformHostsText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		).readText()
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val androidRuntimeHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderPublicationRuntimeHost.android.kt"
		).readText()

		assertTrue(
			platformHostsText.contains("onPublicationReady: (String, String?, BinderyReadingProgress?) -> Unit"),
			"Publication runtime must return saved progress through the app boundary before the engine open request is built."
		)
		assertTrue(
			readerScreenText.contains("savedProgress = savedProgress"),
			"ReaderScreen must feed saved Bindery progress into the open request factory."
		)
		assertTrue(
			androidRuntimeHostText.contains("getReadingProgress("),
			"Android publication runtime must ask Bindery for saved reading progress while resolving the publication."
		)
		assertTrue(
			androidRuntimeHostText.contains("toReaderStartLocatorForReader("),
			"Android publication runtime must validate saved progress against the active book/resource/kind before opening."
		)
		assertFalse(
			readerScreenText.contains("binderyRepository.getReadingProgress("),
			"ReaderScreen should not fetch saved progress itself; the runtime host already owns publication preparation."
		)
	}
}
