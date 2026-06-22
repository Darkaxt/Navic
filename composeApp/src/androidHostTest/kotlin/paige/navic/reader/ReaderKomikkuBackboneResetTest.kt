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
		val readerRoot = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		)
		val readerAppBars = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt"
		)
		val activeText = activeReaderScreen.readText()
		val viewerHostText = readerViewerHost.readText()
		val readerRootText = readerRoot.readText()
		val appBarsText = readerAppBars.readText()

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
		assertTrue(readerRootText.contains("KomikkuReaderNativeFrameHost("))
		assertTrue(readerRootText.contains("ReaderViewerHost("))
		assertTrue(readerRootText.contains("KomikkuComposeOverlay("))
		assertTrue(readerRootText.contains("KomikkuReaderAppBars("))
		assertTrue(appBarsText.contains("KomikkuChapterNavigator("))
		assertTrue(appBarsText.contains("KomikkuReaderBottomBar("))
		assertTrue(appBarsText.contains("Ported from Komikku ReaderAppBars"))
		assertFalse(
			activeText.contains("KomikkuReaderPageIndicator(") || readerRootText.contains("KomikkuReaderPageIndicator("),
			"Page numbers must stay organic inside the rendered book surface, not as a Compose/mobile overlay."
		)
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
		assertTrue(
			activeText.contains("ReaderReadaloudRuntimeHost("),
			"Active ReaderScreen.kt must reattach readaloud through the runtime adapter once the Komikku shell owns the viewer."
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
			activeText.contains("private fun KomikkuReaderContainer") || readerRootText.contains("private fun KomikkuReaderContainer"),
			"The active common reader must not emulate Komikku's native reader_container with a Compose helper."
		)
		assertFalse(
			activeText.contains("private fun KomikkuReaderGestureLayer") || readerRootText.contains("private fun KomikkuReaderGestureLayer"),
			"The active common reader must not keep Compose as the owner of reader-wide gestures."
		)
	}

	@Test
	fun activeReaderScreenReattachesReadaloudThroughCoordinatorAdapterBoundary() {
		val activeReaderScreen = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		)
		val platformHosts = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		)
		val androidReadaloudHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/ReaderReadaloudRuntimeHost.android.kt"
		)
		val controller = root.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderController.kt")
		val coordinator = root.resolve("composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderCoordinator.kt")
		val activeText = activeReaderScreen.readText()
		val platformText = platformHosts.readText()
		val androidHostText = androidReadaloudHost.readText()
		val controllerText = controller.readText()
		val coordinatorText = coordinator.readText()

		assertTrue(
			activeText.contains("var lastReaderEngineHostEvent") &&
				activeText.contains("var readerEngineHostEventKey"),
			"ReaderScreen must fan typed engine-host events into the readaloud adapter without letting WebView own chrome."
		)
		assertTrue(
			activeText.contains("ReaderReadaloudRuntimeHost(") &&
				activeText.contains("readerHostEvent = lastReaderEngineHostEvent") &&
				activeText.contains("readerHostEventKey = readerEngineHostEventKey"),
			"The readaloud runtime must receive renderer events through the typed Komikku shell boundary."
		)
		assertTrue(
			activeText.contains("coordinator.onReadaloudEngineCommand(command)") &&
				activeText.contains("coordinator.onReadaloudPlaybackState(playbackState)"),
			"Readaloud overlay and playback outputs must route through ReaderCoordinator/ReaderController."
		)
		assertTrue(
			platformText.contains("readerHostEvent: ReaderEngineHostEvent?") &&
				platformText.contains("onEngineCommand: (ReaderEngineCommand, Long) -> Unit"),
			"Readaloud runtime host must emit engine-level commands, not legacy bridge commands."
		)
		assertFalse(
			platformText.contains("onReaderCommand: (ReaderBridgeCommand, Long) -> Unit"),
			"The active platform readaloud contract must not expose legacy ReaderBridgeCommand ownership."
		)
		assertTrue(
			androidHostText.contains("currentOnEngineCommand") &&
				androidHostText.contains("nextState.engineCommand") &&
				androidHostText.contains("currentOnEngineCommand("),
			"Android readaloud runtime must pass sync commands through the engine-command callback."
		)
		assertFalse(
			androidHostText.contains("toLegacyReaderBridgeCommand"),
			"Android readaloud runtime must not convert sync back to legacy bridge commands."
		)
		assertTrue(
			controllerText.contains("fun onReadaloudPlaybackState(") &&
				coordinatorText.contains("fun onReadaloudPlaybackState(") &&
				coordinatorText.contains("fun onReadaloudEngineCommand("),
			"Readaloud state and sync commands must have explicit controller/coordinator entry points."
		)
	}

	@Test
	fun activeSourceTreeDoesNotKeepLegacyReaderOptionsPanel() {
		val activeOptionsPanel = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt"
		)
		val vaultOptionsPanel = root.resolve(
			"vault/reader/2026-06-13-pre-komikku-reset/composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderOptionsPanel.kt"
		)

		assertTrue(
			vaultOptionsPanel.exists(),
			"The pre-Komikku reader options panel must remain available in the vault for reference."
		)
		assertFalse(
			activeOptionsPanel.exists(),
			"The active reader tree must not keep the legacy docked ReaderOptionsPanel; Komikku settings live in controller-owned overlay dialogs."
		)
	}

	@Test
	fun activeSourceTreeDoesNotExposeLegacyReaderWebViewHost() {
		val platformHosts = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		)
		val activeAndroidHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt"
		)
		val activeIosHost = root.resolve(
			"composeApp/src/iosMain/kotlin/paige/navic/reader/ReaderWebViewHost.ios.kt"
		)
		val vaultAndroidHost = root.resolve(
			"vault/reader/2026-06-13-pre-komikku-reset/composeApp/src/androidMain/kotlin/paige/navic/reader/ReaderWebViewHost.android.kt"
		)

		assertTrue(
			vaultAndroidHost.exists(),
			"The pre-Komikku Android WebView host must remain available in the vault for reference."
		)
		assertFalse(
			platformHosts.readText().contains("expect fun ReaderWebViewHost("),
			"The active common reader platform contract must not expose the legacy WebView host; EPUB/PDF content mounts through ReaderEngineWebViewHost."
		)
		assertFalse(
			activeAndroidHost.exists(),
			"The active Android tree must not keep ReaderWebViewHost.android.kt because it owns pre-Komikku shell, cover, and gesture behavior."
		)
		assertFalse(
			activeIosHost.exists(),
			"The active iOS tree must not keep the legacy ReaderWebViewHost stub after moving publication rendering to the engine-host boundary."
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
		val readerRoot = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		)
		val viewerText = readerViewerFile.readText()
		val hostText = readerViewerHost.readText()
		val rootText = readerRoot.readText()

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
			rootText.contains("engineRenderer = viewer.engineRenderer"),
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
		val readerAppBars = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt"
		)
		val activeText = activeReaderScreen.readText()
		val appBarsText = readerAppBars.readText()
		val topBarBody = appBarsText
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
	fun komikkuAppBarsOwnSideNavigatorAsWeightedAppBarSlot() {
		val appBarsText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt"
		).readText()
		val komikkuAppBarsText = root.resolve(
			"tmp/references/komikku/app/src/main/java/eu/kanade/presentation/reader/appbars/ReaderAppBars.kt"
		).readText()
		val appBarsBody = appBarsText
			.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")
		val komikkuAppBarsBody = komikkuAppBarsText
			.substringAfter("fun ReaderAppBars(")
			.substringBefore("\n}\n")

		assertTrue(
			komikkuAppBarsBody.contains("Column(modifier = Modifier.fillMaxHeight())"),
			"Komikku ReaderAppBars uses a full-height Column as the app-bar host."
		)
		assertTrue(
			komikkuAppBarsBody.contains(".weight(1f)") &&
				komikkuAppBarsBody.contains(".align(Alignment.Start)") &&
				komikkuAppBarsBody.contains(".align(Alignment.End)"),
			"Komikku places the side chapter navigator in the weighted middle slot, aligned to Start or End."
		)
		assertTrue(
			appBarsBody.contains("Column(modifier = modifier.fillMaxHeight())"),
			"Navic's KomikkuReaderAppBars must match Komikku's full-height Column host."
		)
		assertTrue(
			appBarsBody.contains("ReaderNavBarTypeVerticalRight ->") &&
				appBarsBody.contains(".weight(1f)") &&
				appBarsBody.contains(".align(Alignment.End)") &&
				appBarsBody.contains("ReaderNavBarTypeVerticalLeft ->") &&
				appBarsBody.contains(".align(Alignment.Start)"),
			"The side progress navigator must live in Komikku's weighted middle slot instead of a Navic-only centered overlay."
		)
		assertFalse(
			appBarsBody.contains("Box(modifier = modifier.fillMaxSize())") ||
				appBarsBody.contains("Alignment.CenterEnd") ||
				appBarsBody.contains("Alignment.CenterStart") ||
				appBarsBody.contains("KomikkuReaderVerticalRailHeightFraction"),
			"The active Komikku app bars must not preserve the eta68 centered-Box/height-fraction workaround."
		)
	}

	@Test
	fun komikkuChapterNavigatorUsesChapterLocalControllerProgressInsteadOfBookProgress() {
		val activeText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val appBarsText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt"
		).readText()
		val appBarsBody = appBarsText
			.substringAfter("internal fun KomikkuReaderAppBars(")
			.substringBefore("\n}\n\n@Composable\nprivate fun KomikkuReaderTopBar(")

		assertTrue(
			activeText.contains("onGoToChapterPage = { pageIndex ->") &&
				activeText.contains("coordinator.navigateToChapterPage(pageIndex)"),
			"ReaderScreen must route Komikku rail seeks through a controller-owned chapter-page action."
		)
		assertTrue(
			appBarsBody.contains("val chapterProgress = controllerState.chapterProgress"),
			"Komikku app bars must read chapter-local page state from the controller, not from global locator fields."
		)
		assertTrue(
			appBarsBody.contains("currentPage = chapterProgress.displayPage") &&
				appBarsBody.contains("totalPages = chapterProgress.pageCount") &&
				appBarsBody.contains("onGoToChapterPage(pageIndex)"),
			"The Komikku side navigator must display and seek within the current chapter."
		)
		assertFalse(
			appBarsBody.contains("locator?.pageCount") ||
				appBarsBody.contains("locator?.pageIndex") ||
				appBarsBody.contains("onGoToProgress((pageIndex.toDouble() / (pageCount - 1))"),
			"The Komikku side navigator must not reuse global book page count/index or convert rail seeks into book progress."
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
		val readerNavigationFile = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderNavigation.kt"
		)
		val activeReaderScreen = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		)
		val readerRoot = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		)
		val androidNativeHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		)
		val navigationText = navigationFile.readText()
		val readerNavigationText = readerNavigationFile.readText()
		val activeText = activeReaderScreen.readText()
		val readerRootText = readerRoot.readText()
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
		assertTrue(activeText.contains("settings.tapZoneInvertMode"))
		assertTrue(readerNavigationText.contains("navigation.invertMode = komikkuTappingInvertMode(settings.tapZoneInvertMode)"))
		assertTrue(readerNavigationText.contains("KomikkuReaderNavigator("))
		assertTrue(readerRootText.contains("KomikkuReaderNativeFrameHost("))
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
		val readerRoot = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		)
		val platformHosts = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		)
		val androidHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		)
		val activeText = activeReaderScreen.readText()
		val readerRootText = readerRoot.readText()
		val platformText = platformHosts.readText()

		assertTrue(
			androidHost.exists(),
			"Android must provide a native FrameLayout reader root, not only the common Compose fallback."
		)
		val androidText = androidHost.readText()

		assertTrue(readerRootText.contains("KomikkuReaderNativeFrameHost("))
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
		assertTrue(
			readerRootText.contains("Box(modifier = modifier.fillMaxSize())") &&
				readerRootText.indexOf("KomikkuReaderNativeFrameHost(") <
				readerRootText.indexOf("composeOverlay = {"),
			"The common reader shell must render through the native frame so Android can layer chrome above WebView/cover surfaces."
		)
		assertTrue(
			readerRootText.contains("composeOverlay = {") &&
				readerRootText.contains("if (overlayVisible)") &&
				readerRootText.contains("KomikkuComposeOverlay(") &&
				readerRootText.contains("controllerState.hasVisibleReaderOverlay()"),
			"The native frame must receive the real Komikku chrome overlay and only render it when controller state requires it."
		)
		assertTrue(
			androidText.contains("private val composeOverlay = ComposeView(context)") &&
				androidText.contains("fun setComposeOverlay(") &&
				androidText.contains("composeOverlay.bringToFront()"),
			"The Android frame host must own the visible chrome overlay as the top native child above the WebView and shell cover."
		)
		assertTrue(androidText.contains("super.dispatchTouchEvent(event)"))
		assertTrue(androidText.contains("gestureDetector.onTouchEvent(event)"))
		assertTrue(androidText.indexOf("super.dispatchTouchEvent(event)") < androidText.indexOf("gestureDetector.onTouchEvent(event)"))
		assertFalse(
			activeText.contains("Box(\n\t\tmodifier = Modifier\n\t\t\t.fillMaxSize()\n\t\t\t.background(Color(0xFF202329))") ||
				readerRootText.contains("Box(\n\t\tmodifier = Modifier\n\t\t\t.fillMaxSize()\n\t\t\t.background(Color(0xFF202329))"),
			"The active reader root must not remain a Compose-only Box emulating Komikku's native hierarchy."
		)
	}

	@Test
	fun nativeKomikkuFrameAppliesViewerLayerColorMatrixLikeKomikku() {
		val readerRoot = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val activeReaderScreen = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val platformHosts = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		).readText()
		val androidHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val komikkuReaderActivity = root.resolve(
			"tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt"
		).readText()

		assertTrue(komikkuReaderActivity.contains("ColorMatrixColorFilter"))
		assertTrue(komikkuReaderActivity.contains("setSaturation(0f)"))
		assertTrue(komikkuReaderActivity.contains("postConcat("))
		assertTrue(komikkuReaderActivity.contains("binding.viewerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)"))

		assertTrue(readerRoot.contains("grayscaleEnabled = controllerState.chrome.settings.grayscaleEnabled == true"))
		assertTrue(readerRoot.contains("invertedColors = controllerState.chrome.settings.invertedColors == true"))
		assertTrue(platformHosts.contains("grayscaleEnabled: Boolean"))
		assertTrue(platformHosts.contains("invertedColors: Boolean"))
		assertTrue(androidHost.contains("import android.graphics.ColorMatrix"))
		assertTrue(androidHost.contains("import android.graphics.ColorMatrixColorFilter"))
		assertTrue(androidHost.contains("private fun getCombinedReaderLayerPaint("))
		assertTrue(androidHost.contains("setSaturation(0f)"))
		assertTrue(androidHost.contains("postConcat("))
		assertTrue(androidHost.contains("ColorMatrixColorFilter"))
		assertTrue(androidHost.contains("viewerContainer.setLayerType(View.LAYER_TYPE_HARDWARE, paint)"))
		assertFalse(
			(activeReaderScreen.contains("drawRect(Color.White") || readerRoot.contains("drawRect(Color.White")) &&
				readerRoot.contains("grayscaleEnabled"),
			"Grayscale/invert must be a native viewer-container layer paint like Komikku, not another Compose tint overlay."
		)
	}

	@Test
	fun nativeKomikkuFrameDispatchesShellCoverSwipesThroughViewerActionBoundary() {
		val androidHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		)
		val androidText = androidHost.readText()
		val viewerContainerBody = androidText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuReaderNativeNavigationOverlayView")
		val dispatchTouchEvent = viewerContainerBody
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\tprivate val gestureDetector")

		assertTrue(
			viewerContainerBody.contains("ViewConfiguration.get(context).scaledTouchSlop"),
			"Komikku-native swipe dispatch must use Android's touch slop, not a hard-coded reader surface threshold."
		)
		assertTrue(viewerContainerBody.contains("MotionEvent.ACTION_DOWN"))
		assertTrue(viewerContainerBody.contains("MotionEvent.ACTION_MOVE"))
		assertTrue(viewerContainerBody.contains("MotionEvent.ACTION_UP"))
		assertTrue(viewerContainerBody.contains("MotionEvent.ACTION_CANCEL"))
		assertTrue(
			viewerContainerBody.contains("dispatchHorizontalSwipeViewerAction("),
			"Shell-cover horizontal drags must be routed by the native viewer container before the engine bridge sees any page command."
		)
		assertTrue(viewerContainerBody.contains("readerShellCoverSwipeAction("))
		assertTrue(viewerContainerBody.contains("onAction(KomikkuNavigationRegion.NEXT)"))
		assertTrue(viewerContainerBody.contains("onAction(KomikkuNavigationRegion.PREV)"))
		assertTrue(
			dispatchTouchEvent.indexOf("val handled = super.dispatchTouchEvent(event)") <
				dispatchTouchEvent.indexOf("handleSwipeTouchEvent(event)"),
			"Swipe observation must stay child-first like Komikku's Pager.dispatchTouchEvent."
		)
		assertTrue(
			dispatchTouchEvent.indexOf("handleSwipeTouchEvent(event)") <
				dispatchTouchEvent.indexOf("gestureDetector.onTouchEvent(event)"),
			"Swipe handling should cancel tap detection before the single-tap gesture detector can open chrome."
		)
		assertFalse(
			viewerContainerBody.contains("ReaderBridgeCommand.NextPage") ||
				viewerContainerBody.contains("ReaderBridgeCommand.PreviousPage"),
			"Native frame swipes must emit viewer actions only; Foliate command translation stays behind ReaderEngine."
		)
	}

	@Test
	fun nativeKomikkuFrameOwnsReadableDragPreviewWhileNativeFrameOwnsTaps() {
		val androidHost = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		)
		val androidText = androidHost.readText()
		val viewerContainerBody = androidText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuReaderNativeNavigationOverlayView")
		val interceptTouchEvent = viewerContainerBody
			.substringAfter("override fun onInterceptTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\t\telse -> return false")
		val handleTouch = viewerContainerBody
			.substringAfter("private fun handleSwipeTouchEvent(event: MotionEvent) {")
			.substringBefore("\n\tprivate fun dispatchHorizontalSwipeViewerAction")
		val swipeAction = viewerContainerBody
			.substringAfter("private fun dispatchHorizontalSwipeViewerAction(deltaX: Float, deltaY: Float): Boolean {")
			.substringBefore("\n\tprivate fun updateShellCoverDragOffset")

		assertFalse(
			viewerContainerBody.contains("Reader native swipe action="),
			"Readable drags must not be converted into immediate tap-like native page-turn actions; they need a drag preview before release."
		)
		assertTrue(
			androidText.contains("Reader native drag preview"),
			"Readable EPUB/PDF drag diagnostics should prove the native frame owns drag preview instead of blindly delegating to WebView."
		)
		assertTrue(
			interceptTouchEvent.contains("nativeHorizontalSwipeMovedBeyondSlop(event.x, event.y)") &&
				interceptTouchEvent.contains("nativeSwipeIntercepted = true") &&
				interceptTouchEvent.contains("return true"),
			"Horizontal drags should be intercepted by the native viewer container so readable content can follow the finger."
		)
		assertTrue(
				handleTouch.contains("val shellCoverVisible = shellCoverView?.visibility == VISIBLE") &&
				handleTouch.contains("if (shellCoverVisible)") &&
				handleTouch.contains("updateReadableViewerDragOffset(dx, dy, ReaderPageDragPreviewPhase.Update)") &&
				handleTouch.contains("ReaderPageDragPreviewPhase.Release") &&
				handleTouch.contains("ReaderPageDragPreviewPhase.Cancel") &&
				handleTouch.contains("dispatchHorizontalSwipeViewerAction("),
			"Shell-cover and readable drags both stay under the native top manager; readable drags must drive renderer preview before release."
		)
		assertTrue(
			swipeAction.contains("val shellCoverVisible = shellCoverView?.visibility == VISIBLE") &&
				swipeAction.contains("if (shellCoverVisible)") &&
				swipeAction.contains("readerShellCoverSwipeAction(") &&
				swipeAction.contains("readableSwipeAction("),
			"Cover and readable drags need separate native swipe contracts under the same top-level frame."
		)
		assertFalse(
			viewerContainerBody.contains("ReaderBridgeCommand.NextPage") ||
				viewerContainerBody.contains("ReaderBridgeCommand.PreviousPage"),
			"Native swipes must still emit viewer actions only; engine command translation stays behind the controller/engine boundary."
		)
	}

	@Test
	fun readableDragPreviewIsDrivenThroughRendererInsteadOfSlidingWebViewOverBlack() {
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val platformHostsText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		).readText()
		val readerRootText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val viewerActionText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderViewerAction.kt"
		).readText()
		val engineText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt"
		).readText()
		val adapterText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/FoliateEpubEngineAdapter.kt"
		).readText()
		val bridgeText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt"
		).readText()
		val runtimeText = readerRuntimeImplementationText()
		val viewerContainerBody = androidHostText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuGestureDetectorWithLongTap")

		assertFalse(
			viewerContainerBody.contains("viewerContentContainer.translationX = deltaX"),
			"Moving the whole WebView exposes the parent background during drag; readable drag preview must drive Foliate's own paginated renderer."
		)
		assertTrue(
			platformHostsText.contains("onReadableDragPreview:") &&
				androidHostText.contains("onReadableDragPreview:") &&
				readerRootText.contains("ReaderViewerAction.PreviewPageDrag"),
			"The native frame should route readable drag deltas through the reader controller instead of directly manipulating the WebView container."
		)
		assertTrue(
			viewerActionText.contains("data class PreviewPageDrag") &&
				engineText.contains("data class PreviewPageDrag") &&
				adapterText.contains("ReaderBridgeCommand.PreviewPageDrag") &&
				bridgeText.contains("data class PreviewPageDrag"),
			"Readable drag preview must be a typed controller/engine/bridge command so EPUB and PDF adapters can own renderer-specific behavior."
		)
		assertTrue(
				runtimeText.contains("case 'previewPageDrag':") &&
				runtimeText.contains("previewPageDrag(command)") &&
				runtimeText.contains("readerRendererReadyForPageDrag(renderer)") &&
				runtimeText.contains("renderer.scrollBy(-incrementalDelta.x, -incrementalDelta.y)") &&
				runtimeText.contains("preloadPageDragPreviewTargets(") &&
				runtimeText.indexOf("preloadPageDragPreviewTargets(") < runtimeText.indexOf("updatePageDragPreviewLayer({") &&
				runtimeText.contains("safeNativeDragPreviewAtSectionBoundary(renderer, direction)") &&
				runtimeText.contains("updatePageDragPreviewLayer({") &&
				runtimeText.contains("dataset.navicPageDragPreviewLayer") &&
				runtimeText.contains("adjacentReadableSectionIndex(direction)"),
			"Foliate runtime preview should preload adjacent-section content before drag exposure, scroll same-section columns, and mount a clipped underlay instead of exposing a black native background."
		)
		assertTrue(
			runtimeText.contains("function buildPageDragPreviewTargetKey(") &&
				runtimeText.contains("this.buildPageDragPreviewTargetKey("),
			"Page drag preview target-key generation must not reuse the pageDragPreviewTargetKey instance field name; class fields shadow prototype methods at runtime."
		)
	}

	@Test
	fun nativeKomikkuFrameOwnsShortTapsAndLeavesLongPressForWebViewContent() {
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val activeReaderText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val platformHostText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderPlatformHosts.kt"
		).readText()
		val bridgeText = readerBridgeText()
		val viewerContainerBody = androidHostText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuReaderNativeNavigationOverlayView")
		val claimInteractiveTouch = bridgeText
			.substringAfter("claimReaderInteractiveContentTouch(doc, event) {")
			.substringBefore("\n  readerContentActionInDocumentAtPoint")

		assertTrue(
			androidHostText.contains("private class KomikkuGestureDetectorWithLongTap") &&
				androidHostText.contains("override fun onLongTapConfirmed(event: MotionEvent)"),
			"Native reader input must port Komikku's long-tap detector instead of using a plain GestureDetector-only tap fallback."
		)
		assertTrue(
			viewerContainerBody.contains("override fun onInterceptTouchEvent(event: MotionEvent): Boolean") &&
				!viewerContainerBody.contains("nativeShortTapIntercepted"),
			"Short taps must follow Komikku's child-first Pager dispatch and be classified by the native GestureDetector, not by an ACTION_UP intercept workaround."
		)
		assertTrue(
			viewerContainerBody.contains("nativeTapLongConfirmed = true") &&
				viewerContainerBody.contains("performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)"),
			"Long press must switch the stream back to content behavior and give the same feedback Komikku gives for long tap."
		)
		assertTrue(
			platformHostText.contains("onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit") &&
				androidHostText.contains("onContentLongPress: (x: Float, y: Float, width: Int, height: Int) -> Unit") &&
				androidHostText.contains("setOnContentLongPress") &&
				viewerContainerBody.contains("onContentLongPress(event.x, event.y, width, height)") &&
				activeReaderText.contains("ReaderViewerAction.ContentLongPressAt"),
			"Native long press must enter the typed reader controller path instead of relying on WebView contextmenu as a side effect."
		)
		assertTrue(
			claimInteractiveTouch.contains("if (this.nativeTapZones === true) return false"),
			"When native tap zones are active, JS must not claim touchstart/pointerdown for links or images; those claims suppress reader-owned short taps."
		)
		assertFalse(
			viewerContainerBody.contains("val handled = super.dispatchTouchEvent(event)\n\t\thandleSwipeTouchEvent(event)\n\t\tgestureDetector.onTouchEvent(event)\n\t\treturn handled"),
			"The active native frame must not remain a child-first WebView tap fallback that lets content clicks race reader region taps."
		)
	}

	@Test
	fun nativeKomikkuFrameEmitsAdbReadableInputDiagnostics() {
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val adbSmokeText = root.resolve("scripts/adb-reader-smoke.ps1").readText()

		assertTrue(
			androidHostText.contains("Reader native tap action="),
			"Native tap diagnostics must identify center/menu and page-zone actions from the top input frame."
		)
		assertTrue(
			androidHostText.contains("Reader native drag preview"),
			"Native readable-page drag diagnostics must prove the top input frame owns the drag preview instead of delegating pager movement blindly."
		)
		assertTrue(
			androidHostText.contains("Reader native drag candidate"),
			"Normal readable-page drag diagnostics must not be mislabeled as shell-cover drag candidates."
		)
		assertTrue(
			androidHostText.contains("Reader native long tap"),
			"Deliberate content interaction diagnostics must expose native long taps separately from short reader tap zones."
		)
		assertTrue(
			androidHostText.contains("Reader shell cover swipe action="),
			"Shell-cover swipe diagnostics must remain visible in adb after the Komikku reset."
		)
		assertTrue(
			androidHostText.contains("Reader shell cover command action="),
			"Morning adb checks need to distinguish cover gesture recognition from controller command dispatch."
		)
		assertTrue(
				adbSmokeText.contains("Reader native tap action=") &&
				adbSmokeText.contains("Reader native drag preview") &&
				adbSmokeText.contains("Reader native drag candidate") &&
				adbSmokeText.contains("Reader native long tap") &&
				adbSmokeText.contains("Reader shell cover swipe action=") &&
				adbSmokeText.contains("Reader shell cover command action="),
			"adb reader smoke must accept the active Komikku native-frame diagnostics instead of only legacy Reader surface logs."
		)
		assertTrue(
			adbSmokeText.contains("readerNativeDragPreview=") &&
				adbSmokeText.contains("readerNativeDragCandidate=") &&
				adbSmokeText.contains("readerNativeLongTap="),
			"adb reader smoke summary must separate normal-page drag and long-tap candidates from shell-cover candidates."
		)
	}

	@Test
	fun adbKomikkuReaderMatrixRunsNamedNativeFrameChecks() {
		val matrixScript = root.resolve("scripts/adb-reader-komikku-matrix.ps1")
		val smokeScript = root.resolve("scripts/adb-reader-smoke.ps1")

		assertTrue(matrixScript.exists(), "Morning adb validation must have a repeatable Komikku reader matrix script.")
		val matrixText = matrixScript.readText()
		val smokeText = smokeScript.readText()
		assertTrue(matrixText.contains("adb-reader-smoke.ps1"))
		assertTrue(matrixText.contains("[string] \$Package"))
		assertTrue(matrixText.contains("[string] \$DeviceSerial"))
		assertTrue(matrixText.contains("[string] \$ApkPath"))
		assertTrue(matrixText.contains("[string] \$ExpectedVersionName"))
		assertTrue(matrixText.contains("[string] \$ArtifactRoot"))
		assertTrue(matrixText.contains("baseline-current-reader"))
		assertTrue(matrixText.contains("center-tap-toggle"))
		assertTrue(matrixText.contains("native-long-press-center"))
		assertTrue(matrixText.contains("edge-tap-next"))
		assertTrue(matrixText.contains("edge-tap-previous"))
		assertTrue(matrixText.contains("drag-next"))
		assertTrue(matrixText.contains("drag-previous"))
		assertTrue(matrixText.contains("texture-next-walk"))
		assertTrue(matrixText.contains("texture-previous-walk"))
		assertTrue(matrixText.contains("cover-center-tap-toggle"))
		assertTrue(matrixText.contains("cover-drag-next"))
		assertTrue(matrixText.contains("pdf-baseline"))
		assertTrue(matrixText.contains("pdf-edge-tap-next"))
		assertTrue(matrixText.contains("pdf-edge-tap-previous"))
		assertTrue(matrixText.contains("pdf-drag-next"))
		assertTrue(matrixText.contains("pdf-drag-previous"))
		assertTrue(matrixText.contains("CaptureReaderDiagnostics = \$true"))
		assertTrue(matrixText.contains("\$smokeArgs.LongPressFraction = \$LongPressFraction"))
		assertTrue(matrixText.contains("\$smokeArgs.ValidateReaderTaps = \$true"))
		assertTrue(matrixText.contains("\$smokeArgs.RequireReaderTapAction = \$true"))
		assertTrue(matrixText.contains("\$smokeArgs.RequireNativeLongTap = \$true"))
		assertTrue(matrixText.contains("\$smokeArgs.RequireShellCoverSwipe = \$true"))
		assertTrue(matrixText.contains("\$smokeArgs.RequireShellCoverCommand = \$true"))
		assertTrue(matrixText.contains("\$smokeArgs.RequireTextureDiagnostics = \$true"))
		assertTrue(matrixText.contains("\$smokeArgs.RequirePdfDiagnostics = \$true"))
		assertTrue(matrixText.contains("[switch] \$IncludePdfChecks"))
		assertTrue(
			smokeText.contains("[ValidateSet(\"\", \"next\", \"previous\")]") &&
				smokeText.contains("[string] \$RequireTextureDirection"),
			"adb reader smoke must support requiring a concrete texture movement direction, not only the presence of texture logs."
		)
		assertTrue(
			smokeText.contains("textureDirectionSamples=") &&
				smokeText.contains("wrongTextureDirection=") &&
				smokeText.contains("surface-texture-scroll"),
			"adb reader smoke must parse surface-texture-scroll offsets and summarize whether any sample moved opposite the requested direction."
		)
		assertTrue(
			matrixText.contains("[ValidateSet(\"\", \"next\", \"previous\")]") &&
				matrixText.contains("[string] \$RequireTextureDirection") &&
				matrixText.contains("-RequireTextureDirection", ignoreCase = false),
			"The Komikku matrix wrapper must pass concrete texture direction expectations through to adb-reader-smoke."
		)
		assertTrue(
			matrixText.contains("-Name \"edge-tap-next\"") &&
				matrixText.contains("-RequireTextureDirection \"next\"") &&
				matrixText.contains("-Name \"edge-tap-previous\"") &&
				matrixText.contains("-RequireTextureDirection \"previous\"") &&
				matrixText.contains("-Name \"drag-next\"") &&
				matrixText.contains("-Name \"drag-previous\""),
			"The morning matrix must fail when forward/backward edge taps or drags invert the paper texture movement."
		)
		assertTrue(
			matrixText.contains("\$ReaderNextWalkTapFractions") &&
				matrixText.contains("\$ReaderPreviousWalkTapFractions") &&
				matrixText.contains("-Name \"texture-next-walk\"") &&
				matrixText.contains("-Name \"texture-previous-walk\"") &&
				matrixText.contains("-TapFraction \$ReaderNextWalkTapFractions") &&
				matrixText.contains("-TapFraction \$ReaderPreviousWalkTapFractions"),
			"The morning matrix must include a multi-page texture walk because the reported inversion appears after several transitions."
		)
		assertTrue(
			matrixText.contains("\$smokeArgs.NoLaunch = \$true"),
			"After the first launch/install step, matrix steps must keep the same open reader state instead of relaunching into the library."
		)
		assertTrue(
			matrixText.contains("[switch] \$ContinueOnFailure"),
			"The morning matrix needs an explicit full-diagnostics mode so one broken gesture does not prevent collecting the rest of the evidence."
		)
		assertTrue(
			matrixText.contains("reader-matrix-summary.csv") &&
				matrixText.contains("reader-matrix-failures.txt"),
			"The matrix root must include an aggregate pass/fail summary, not only per-step folders."
		)
		assertTrue(
			matrixText.contains("Record-ReaderMatrixResult") &&
				matrixText.contains("\$matrixFailures.Count") &&
				matrixText.contains("Komikku reader matrix failed"),
			"The matrix must record step outcomes and still exit non-zero after a full diagnostic run with failures."
		)
		assertTrue(
			matrixText.contains("\$smokeArgs = @{") &&
				matrixText.contains("\$smokeArgs.DeviceSerial = \$DeviceSerial") &&
				matrixText.contains("& \$smokeScript @smokeArgs") &&
				!matrixText.contains("& \$smokeScript @args"),
			"The matrix wrapper must forward adb-reader-smoke.ps1 parameters by name; positional array forwarding binds Package/TapFraction to the wrong parameters."
		)
	}

	@Test
	fun adbKomikkuReaderMatrixSeparatesCoverAndReadableContentState() {
		val matrixScript = root.resolve("scripts/adb-reader-komikku-matrix.ps1")

		assertTrue(matrixScript.exists(), "Komikku reader matrix script must be present.")
		val matrixText = matrixScript.readText()

		assertTrue(
			matrixText.contains("function Invoke-ReaderCoverMatrixSteps"),
			"The matrix must isolate shell-cover checks so they run while the native cover is still visible."
		)
		assertTrue(
			matrixText.contains("function Invoke-ReadableContentMatrixSteps"),
			"The matrix must isolate readable-content checks so EPUB taps/drags are not tested against the shell cover."
		)
		assertTrue(
			matrixText.contains("-Name \"enter-readable-content\""),
			"The matrix needs an explicit transition step for runs that skip cover checks but still need readable EPUB content."
		)
		val coverFunction = matrixText.indexOf("function Invoke-ReaderCoverMatrixSteps")
		val readableFunction = matrixText.indexOf("function Invoke-ReadableContentMatrixSteps")
		val coverInvocation = matrixText.lastIndexOf("Invoke-ReaderCoverMatrixSteps")
		val readableInvocation = matrixText.lastIndexOf("Invoke-ReadableContentMatrixSteps")
		assertTrue(
			coverFunction in 0 until readableFunction,
			"The matrix script should define the cover-state group before the readable-content group."
		)
		assertTrue(
			coverInvocation > readableFunction && coverInvocation < readableInvocation,
			"The matrix must invoke the cover-state group before invoking readable-content checks."
		)
		assertTrue(
			matrixText.indexOf("-Name \"cover-drag-next\"") in coverFunction until readableFunction,
			"`cover-drag-next` must belong to the cover-state group because it validates and exits the shell cover."
		)
		assertTrue(
			matrixText.indexOf("-Name \"edge-tap-next\"") > readableFunction &&
				matrixText.indexOf("-Name \"drag-next\"") > readableFunction &&
				matrixText.indexOf("-Name \"texture-next-walk\"") > readableFunction,
			"Edge taps, readable drags, and texture walks must belong to the readable-content group, not the cover-state group."
		)
		val edgeTapNext = matrixText.indexOf("-Name \"edge-tap-next\"")
		val dragNext = matrixText.indexOf("-Name \"drag-next\"")
		val textureNextWalk = matrixText.indexOf("-Name \"texture-next-walk\"")
		val edgeTapPrevious = matrixText.indexOf("-Name \"edge-tap-previous\"")
		val dragPrevious = matrixText.indexOf("-Name \"drag-previous\"")
		val texturePreviousWalk = matrixText.indexOf("-Name \"texture-previous-walk\"")
		assertTrue(
			edgeTapNext in 0 until dragNext &&
				dragNext in 0 until textureNextWalk &&
				textureNextWalk in 0 until edgeTapPrevious &&
				edgeTapPrevious in 0 until dragPrevious &&
				dragPrevious in 0 until texturePreviousWalk,
			"The readable matrix must run forward checks first. Previous checks can cross the native-cover boundary when started from page 1, which makes later readable-drag assertions false positives."
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
		val readerRootText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
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
			readerRootText.contains("readerShellCoverViewerActionFor(action)") &&
				readerRootText.contains("viewer.viewerActionFor(action)"),
			"ReaderScreen must translate native tap regions through the shell-cover mapper or active viewer before reaching the controller."
		)
		assertFalse(
			controllerText.contains("ReaderControllerNavigationAction") ||
				coordinatorText.contains("ReaderControllerNavigationAction") ||
				readerRootText.contains("ReaderControllerNavigationAction"),
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
		val readerRootText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
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
			readerRootText.contains("ReaderViewerHost("),
			"ReaderScreen should mount a viewer host into Komikku's viewer_container slot."
		)
		assertFalse(
			readerScreenText.contains("ReaderEngineWebViewHost(") || readerRootText.contains("ReaderEngineWebViewHost("),
			"ReaderScreen must not mount the Foliate/WebView renderer directly."
		)
		assertFalse(
			readerScreenText.contains("ReaderEngineViewState.WebViewPublication") ||
				readerRootText.contains("ReaderEngineViewState.WebViewPublication"),
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
		val readerRootText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
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
			readerRootText.contains("shellCoverVisible = controllerState.shellCoverVisible") &&
				readerRootText.contains("val shellCoverUrl = viewer.shellCoverUrl") &&
				readerRootText.contains("val shellCoverTitle = shellCoverTitleFor(reader, controllerState, viewer)") &&
				readerRootText.contains("shellCoverUrl = shellCoverUrl") &&
				readerRootText.contains("shellCoverTitle = shellCoverTitle"),
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
		val readerRootText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val readerAppBarsText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderAppBars.kt"
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
			readerRootText.contains("KomikkuReaderSettingsDialog(") &&
				readerRootText.contains("ReaderControllerDialog.Settings -> KomikkuReaderSettingsDialog(") &&
				readerRootText.contains("onDismissRequest = onDismissDialog"),
			"Settings must render as a Komikku-style overlay dialog above the viewer, not as the old docked options panel."
		)
		assertFalse(
			controllerText.contains("ReadingMode") || readerRootText.contains("ReaderControllerDialog.ReadingMode"),
			"The Komikku shell must not keep a second controller route that opens the same settings dialog."
		)
		assertFalse(
			readerScreenText.contains("ReaderOptionsPanel(") || readerRootText.contains("ReaderOptionsPanel("),
			"The active Komikku reader must not reattach the old reader options panel as its settings surface."
		)
		assertFalse(
			readerAppBarsText.contains("IconButton(onClick = {}) {\n\t\t\t\tIcon(Icons.Filled.Settings"),
			"The active settings icon must not keep an empty callback."
		)
	}

	@Test
	fun settingsDialogAppliesTapZoneSettingsThroughControllerSettingsCommand() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val readerRootText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val settingsDialogText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt"
		).readText()
		val developerSettingsText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/settings/DeveloperScreen.kt"
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
			readerRootText.contains("onSettingsChange: (ReaderSettings) -> Unit") &&
				settingsDialogText.contains("onSettingsChange: (ReaderSettings) -> Unit"),
			"The Komikku settings dialog must accept a settings-change callback instead of staying a static display shell."
		)
		assertTrue(
			readerScreenText.contains("onSettingsChange = { settings ->") &&
				readerScreenText.contains("applyReaderSettings(settings)") &&
				readerScreenText.contains("coordinator.applySettings(normalized)"),
			"ReaderScreen must persist normalized settings before routing them through ReaderCoordinator.applySettings."
		)
		assertTrue(
			settingsDialogText.contains("Smaller tap zones") &&
				settingsDialogText.contains("settings.copy(smallerTapZone = settings.smallerTapZone != true)"),
			"Reader-facing tap-zone settings should still route through ReaderSettings, not through a local UI flag."
		)
		assertFalse(
			settingsDialogText.contains("Show tap zones") ||
				settingsDialogText.contains("settings.copy(showTapZones = settings.showTapZones != true)"),
			"Tap-zone visualization is diagnostic UI and must not return to the reader settings dialog."
		)
		assertTrue(
			developerSettingsText.contains("readerShowTapZones") &&
				developerSettingsText.contains("option_ebook_reader_show_tap_zones"),
			"Tap-zone visualization belongs in Developer Options while still feeding ReaderSettings through preferences."
		)
		assertFalse(
			readerScreenText.contains("ReaderOptionsPanel(") || readerRootText.contains("ReaderOptionsPanel("),
			"The active Komikku settings path must not resurrect the old docked ReaderOptionsPanel."
		)
	}

	@Test
	fun settingsDialogUsesKomikkuTapZonePresetControlsInsteadOfStaticLabels() {
		val settingsDialogText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt"
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
			settingsDialogText.contains("private val KomikkuTapZoneOptions = listOf("),
			"Navic must keep an explicit Komikku-order tap-zone list instead of relying on ReaderSupportedTapZones order."
		)
		assertTrue(
			settingsDialogText.indexOf("ReaderTapZoneDefault to \"Default\"") <
				settingsDialogText.indexOf("ReaderTapZoneLShaped to \"L shaped\"") &&
				settingsDialogText.indexOf("ReaderTapZoneLShaped to \"L shaped\"") <
				settingsDialogText.indexOf("ReaderTapZoneKindle to \"Kindle-ish\"") &&
				settingsDialogText.indexOf("ReaderTapZoneKindle to \"Kindle-ish\"") <
				settingsDialogText.indexOf("ReaderTapZoneEdge to \"Edge\"") &&
				settingsDialogText.indexOf("ReaderTapZoneEdge to \"Edge\"") <
				settingsDialogText.indexOf("ReaderTapZoneRightLeft to \"Right and Left\"") &&
				settingsDialogText.indexOf("ReaderTapZoneRightLeft to \"Right and Left\"") <
				settingsDialogText.indexOf("ReaderTapZoneDisabled to \"Disabled\""),
			"Navic's settings dialog tap-zone order must match Komikku: default, L-shaped, Kindle-ish, edge, right/left, disabled."
		)
		assertTrue(
			settingsDialogText.contains("SettingsSelectableChipRow(") &&
				settingsDialogText.contains("FilterChip(") &&
				settingsDialogText.contains("onSettingsChange(settings.copy(tapZone = tapZone))"),
			"Tap-zone presets must be real settings chips routed through ReaderSettings."
		)
		assertTrue(
			settingsDialogText.contains("Tapping inversion") &&
				settingsDialogText.contains("KomikkuTapZoneInvertOptions") &&
				settingsDialogText.contains("onSettingsChange(settings.copy(tapZoneInvertMode = tapZoneInvertMode))"),
			"Komikku's tapping inversion chips must be routed through ReaderSettings and the ported navigator."
		)
		assertTrue(
			settingsDialogText.contains("Smaller tap zones") &&
				settingsDialogText.contains("settings.copy(smallerTapZone = settings.smallerTapZone != true)"),
			"Komikku's smaller tap-zone checkbox must migrate as a real settings control."
		)
		assertFalse(
			settingsDialogText.contains("KomikkuSettingsDialogLine(\"Tap zones:"),
			"Tap zones must no longer be displayed only as a static line in the Komikku settings dialog."
		)
	}

	@Test
	fun settingsDialogUsesKomikkuReadingModePresetControlsInsteadOfStaticLabels() {
		val settingsDialogText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderSettingsDialog.kt"
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
			settingsDialogText.contains("private val KomikkuReadingModeOptions = listOf("),
			"Navic must keep an explicit Komikku-order reading-mode list instead of showing flow/direction as labels."
		)
		assertTrue(
			settingsDialogText.indexOf("label = \"Default\"") <
				settingsDialogText.indexOf("label = \"Paged (left to right)\"") &&
				settingsDialogText.indexOf("label = \"Paged (left to right)\"") <
				settingsDialogText.indexOf("label = \"Paged (right to left)\"") &&
				settingsDialogText.indexOf("label = \"Paged (right to left)\"") <
				settingsDialogText.indexOf("label = \"Paged (vertical)\"") &&
				settingsDialogText.indexOf("label = \"Paged (vertical)\"") <
				settingsDialogText.indexOf("label = \"Long strip\"") &&
				settingsDialogText.indexOf("label = \"Long strip\"") <
				settingsDialogText.indexOf("label = \"Long strip with gaps\""),
			"Navic's reading-mode options must follow Komikku's entry order."
		)
		assertTrue(
			settingsDialogText.contains("KomikkuSettingsReadingModeRow(") &&
				settingsDialogText.contains("onSettingsChange(settings.copy(") &&
				settingsDialogText.contains("flowMode = option.flowMode") &&
				settingsDialogText.contains("paged = option.paged") &&
				settingsDialogText.contains("direction = option.direction"),
			"Reading-mode chips must write Navic flow, paged, and direction settings through the controller path."
		)
		assertFalse(
			settingsDialogText.contains("KomikkuSettingsDialogLine(\"Reading mode:") ||
				settingsDialogText.contains("KomikkuSettingsDialogLine(\"Direction:"),
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
		val readerRootText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val viewerHostText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewerHost.kt"
		).readText()

		assertTrue(
			readerRootText.contains("val viewerSlot = remember { ReaderViewerLifecycleSlot() }") &&
				readerRootText.contains("val viewer = remember(viewerSlot, viewState) { viewerSlot.update(viewState) }") &&
				readerRootText.contains("DisposableEffect(viewerSlot)") &&
				readerRootText.contains("viewerSlot.dispose()"),
			"KomikkuReaderRoot must create, retain, swap, and dispose the active ReaderViewer through an explicit lifecycle slot."
		)
		assertTrue(
			readerRootText.contains("viewerKey = viewer.key"),
			"The native frame slot must be keyed by the retained viewer instance, not by a parallel identity calculation."
		)
		assertTrue(
			readerRootText.contains("readerShellCoverViewerActionFor(action)") &&
				readerRootText.contains("viewer.viewerActionFor(action)"),
			"Native tap regions must become shell-cover or viewer-owned actions through the retained active viewer."
		)
		assertTrue(
			readerRootText.contains("engineRenderer = viewer.engineRenderer"),
			"ReaderViewerHost must receive the retained active viewer's engine renderer descriptor from the reader root."
		)
		assertFalse(
			readerRootText.contains("readerViewerFor(viewState).viewerActionFor(action)") ||
				readerRootText.contains("readerViewerFor(viewState).navigationActionFor(action)") ||
				readerRootText.contains("viewer.navigationActionFor(action)"),
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
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
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
			.substringBefore("override fun onDetachedFromWindow()")

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
			"Android native frame must dispose both the active viewer composition and the native top chrome overlay composition."
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
	fun readerScreenConsumesWhispersyncSeekTargetsThroughAudiobookBoundary() {
		val readerScreenText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt"
		).readText()
		val readerRootText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val statusBadgeText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncStatusBadge.kt"
		).readText()

		assertTrue(
			readerScreenText.contains("koinInject<AudiobookPlaybackManager>()"),
			"ReaderScreen must obtain the shared audiobook manager at the app boundary for Whispersync playback."
		)
		assertTrue(
			readerScreenText.contains("binderyAudiobookPlaybackPlan("),
			"ReaderScreen must load the selected Bindery audiobook into a playback plan for the paired ebook session."
		)
		assertTrue(
			readerScreenText.contains("readerWhispersyncPlaybackCommandForSeekTarget("),
			"ReaderScreen must resolve controller-owned Whispersync seek targets through the playback policy."
		)
		assertTrue(
			readerScreenText.contains("step.whispersyncAudioSeekTarget"),
			"ReaderScreen must consume ReaderCoordinatorStep.whispersyncAudioSeekTarget instead of dropping it."
		)
		assertTrue(
			readerScreenText.contains("audiobookPlaybackManager.dispatch(command)"),
			"ReaderScreen must dispatch resolved Whispersync audio commands through the shared audiobook manager."
		)
		assertTrue(
			readerScreenText.contains("val whispersyncReadaloudPlaybackState = audiobookMiniPlayerState.toWhispersyncReadaloudPlaybackUiState(") &&
				readerScreenText.contains("readaloudPlaybackState = whispersyncReadaloudPlaybackState"),
			"ReaderScreen must pass the active audiobook session into the native reader shell for the page-level Whispersync control."
		)
		assertTrue(
			readerScreenText.contains("onWhispersyncPlaybackCommand = { command ->"),
			"ReaderScreen must expose the native Whispersync control as an audiobook manager command, not as WebView-owned UI."
		)
		assertTrue(
			readerRootText.contains("readerWhispersyncPlaybackControlState("),
			"ReaderRoot must derive the Whispersync playback control state at the Komikku shell boundary."
		)
		assertTrue(
			readerRootText.contains("KomikkuWhispersyncPlaybackControl("),
			"ReaderRoot must render the native top-left Whispersync playback affordance."
		)
		assertTrue(
			statusBadgeText.contains("ReaderWhispersyncPlaybackControlState"),
			"The Whispersync UI file must contain a dedicated playback control instead of overloading the mismatch badge."
		)
		assertTrue(
			statusBadgeText.contains("KomikkuWhispersyncPlaybackControl") &&
				statusBadgeText.contains("modifier = modifier.pointerInput(Unit)"),
			"The visible Whispersync playback control must shield its own touch area so disabled/loading states cannot leak taps to page navigation."
		)
		assertTrue(
			statusBadgeText.contains("onRepairMismatch: () -> Unit"),
			"The mismatch badge must expose a controller-owned repair action instead of being a passive UI-only warning."
		)
		assertTrue(
			readerRootText.contains("onRepairWhispersyncMismatch: () -> Unit"),
			"ReaderRoot must route Whispersync mismatch repair through the Komikku overlay boundary."
		)
		assertTrue(
			readerScreenText.contains("coordinator.repairWhispersyncMismatch()"),
			"ReaderScreen must route mismatch repair back through ReaderCoordinator, not directly to the audio player."
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
			readerScreenText.contains("decodeReaderReadingProgress(preferenceManager.readerReadingProgressJson)") &&
				readerScreenText.contains("startLocatorFor(") &&
				readerScreenText.contains("localStartLocator ="),
			"ReaderScreen must feed local readerReadingProgressJson into the open request factory so cached EPUBs resume when Bindery progress lookup is unavailable."
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

	@Test
	fun nativeShellCoverUsesCoverSpecificSideZoneNavigation() {
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val activeReaderText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt"
		).readText()
		val viewerText = root.resolve(
			"composeApp/src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewer.kt"
		).readText()
		val viewerContainerBody = androidHostText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuGestureDetectorWithLongTap")
		val singleTap = viewerContainerBody
			.substringAfter("override fun onSingleTapConfirmed(event: MotionEvent): Boolean {")
			.substringBefore("\n\t\t\t}")

		assertTrue(
			viewerContainerBody.contains("shellCoverNavigator") &&
				viewerContainerBody.contains("KomikkuRightAndLeftNavigation("),
			"Shell cover taps should use Komikku's simple side-zone shape instead of the active EPUB reading tap-zone preset."
		)
		assertTrue(
			singleTap.contains("if (shellCoverView?.visibility == VISIBLE)") &&
				singleTap.contains("shellCoverNavigator.getAction(point)") &&
				singleTap.contains("navigator.getAction(point)"),
			"Native tap classification must switch navigator by shell-cover visibility before emitting a viewer action."
		)
		assertTrue(
			activeReaderText.contains("readerShellCoverViewerActionFor(action)") &&
				activeReaderText.contains("if (controllerState.shellCoverVisible)"),
			"The common shell must map cover side regions physically before the normal reader direction mapping can invert them."
		)
		assertTrue(
			viewerText.contains("fun readerShellCoverViewerActionFor(") &&
				viewerText.contains("KomikkuNavigationRegion.RIGHT -> ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)") &&
				viewerText.contains("KomikkuNavigationRegion.LEFT -> ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)"),
			"Cover-side navigation must be physical: right enters the book, left/previous stays at the cover boundary."
		)
	}

	@Test
	fun nativeDragMotionCancelsPendingLongTapBeforeDelegatingOrDispatchingSwipe() {
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val viewerContainerBody = androidHostText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuGestureDetectorWithLongTap")
		val moveBranch = viewerContainerBody
			.substringAfter("MotionEvent.ACTION_MOVE,")
			.substringBefore("if (event.actionMasked == MotionEvent.ACTION_UP)")
		val gestureDetectorBody = androidHostText
			.substringAfter("private class KomikkuGestureDetectorWithLongTap")
			.substringBefore("private class KomikkuReaderNativeNavigationOverlayView")

		assertTrue(
			gestureDetectorBody.contains("fun cancelPendingLongTap()"),
			"The native top manager needs an explicit long-tap cancellation hook for drag motion observed outside child ownership."
		)
		assertTrue(
			moveBranch.contains("cancelPendingLongTapForDrag(dx, dy)") &&
				viewerContainerBody.contains("private fun cancelPendingLongTapForDrag(deltaX: Float, deltaY: Float)") &&
				viewerContainerBody.contains("gestureDetector.cancelPendingLongTap()"),
			"Any drag crossing touch slop must cancel the pending native long tap before cover swipe dispatch or readable drag delegation."
		)
		assertTrue(
			moveBranch.indexOf("cancelPendingLongTapForDrag(dx, dy)") <
				moveBranch.indexOf("dispatchHorizontalSwipeViewerAction("),
			"A cover swipe must not dispatch while the old long-tap callback is still armed."
		)
		assertTrue(
			moveBranch.indexOf("cancelPendingLongTapForDrag(dx, dy)") <
				moveBranch.indexOf("updateReadableViewerDragOffset(dx, dy, ReaderPageDragPreviewPhase.Update)"),
			"A readable EPUB/PDF drag must cancel native long-tap detection before the renderer preview keeps the drag stream."
		)
	}

	@Test
	fun shellCoverDragFollowsMoveAndDispatchesPageTurnOnlyOnRelease() {
		val androidHostText = root.resolve(
			"composeApp/src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		val viewerContainerBody = androidHostText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuGestureDetectorWithLongTap")
		val swipeHandlerBody = viewerContainerBody
			.substringAfter("private fun handleSwipeTouchEvent(event: MotionEvent)")
			.substringBefore("\n\tprivate fun dispatchHorizontalSwipeViewerAction")
		val moveBranch = swipeHandlerBody
			.substringAfter("MotionEvent.ACTION_MOVE -> {")
			.substringBefore("\n\t\t\t}")
		val upBranch = swipeHandlerBody
			.substringAfter("MotionEvent.ACTION_UP -> {")
			.substringBefore("\n\t\t\t}")

		assertTrue(
			moveBranch.contains("updateShellCoverDragOffset(dx)") &&
				moveBranch.contains("updateReadableViewerDragOffset(dx, dy, ReaderPageDragPreviewPhase.Update)"),
			"Move events should provide visual drag feedback for both cover and readable pages without committing navigation."
		)
		assertFalse(
			moveBranch.contains("dispatchHorizontalSwipeViewerAction("),
			"Shell-cover navigation must not dispatch during MOVE; that is what makes the cover pop away instead of following the finger."
		)
		assertTrue(
			upBranch.contains("dispatchHorizontalSwipeViewerAction("),
			"Shell-cover page entry should commit only when the finger is released."
		)
		assertTrue(
			upBranch.indexOf("updateShellCoverDragOffset(dx)") <
				upBranch.indexOf("dispatchHorizontalSwipeViewerAction("),
			"The release frame should keep the cover at the final drag offset before deciding whether to enter the book."
		)
	}
}
