package paige.navic.reader

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderRuntimeShellProgressTest {
	@Test
	fun androidReaderShellOwnsAnxStyleViewportSurface() {
		val root = readerAssetRoot()
		val indexText = root.resolve("index.html").readText()
		val bridgeText = readerBridgeText(root)

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
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "case 'nextPage'")
		assertContains(bridgeText, "case 'previousPage'")
		assertContains(bridgeText, "nextPage()")
		assertContains(bridgeText, "previousPage()")
		assertContains(bridgeText, "this.view?.next?.()")
		assertContains(bridgeText, "this.view?.prev?.()")
		assertContains(bridgeText, "page-turn:start")
		assertContains(bridgeText, "page-turn:done")
	}

	@Test
	fun embeddedCoverSuppressionPostsInitialVisibleLocationBeforeFirstPageTurn() {
		val bridgeText = readerBridgeText()
		val suppressionBody = bridgeText
			.substringAfter("suppressLoadedEmbeddedCoverPage(doc, index) {")
			.substringBefore("\n  attachContentDocumentBehaviors")

		assertContains(suppressionBody, "cover:embedded-page-suppressed")
		assertContains(suppressionBody, "this.view.renderer?.render?.()")
		assertContains(suppressionBody, "this.scheduleReaderPageNumberRefresh('embedded-cover-suppressed')")
		assertContains(
			suppressionBody,
			"this.scheduleCommittedRelocation(this.lastRelocateDetail, 'embedded-cover-suppressed')",
			message = "After suppressing an embedded cover, the reader must post the first visible page location before the next page command can cancel the pending initial relocation."
		)
	}

	@Test
	fun androidReaderBridgeExposesViewportScrollCommandSeparateFromPageTurns() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "case 'scrollViewport'")
		assertContains(bridgeText, "scrollViewport(command.direction)")
		assertContains(bridgeText, "async scrollViewport(direction)")
		assertContains(bridgeText, "viewport-scroll:start")
		assertContains(bridgeText, "viewport-scroll:done")
		assertContains(bridgeText, "renderer.scrolled")
		assertContains(bridgeText, "renderer.scrollBy")
	}

	@Test
	fun androidReaderBridgeExposesProgressSeekCommand() {
		val bridgeText = readerBridgeText()
		val readerScreenText = readerScreenFile().readText()
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val chapterNavigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()

		assertContains(bridgeText, "case 'goToProgress'")
		assertContains(bridgeText, "async goToProgress(progress)")
		assertContains(bridgeText, "this.view?.goToFraction")
		assertContains(bridgeText, "progress-seek")
		assertContains(bridgeText, "case 'goToChapterProgress'")
		assertContains(bridgeText, "async goToChapterProgress(href, progress)")
		assertContains(bridgeText, "chapter-progress-seek")
		assertContains(bridgeText, "this.view.renderer.goTo({ index, anchor: fraction })")
		assertFalse(
			bridgeText.contains("this.view.goTo({ href: targetHref, fraction })"),
			"Foliate treats an object with fraction as a whole-book fraction target; chapter rail seeks must resolve href to a section index and use a section-local anchor."
		)
		assertContains(readerScreenText, "coordinator.navigateTo(")
		assertContains(readerScreenText, "coordinator.navigateToChapterPage(pageIndex)")
		assertFalse(
			readerScreenText.contains("ReaderBridgeCommand.GoToProgress"),
			"The Komikku reader shell must ask the controller to navigate, not dispatch raw Foliate bridge progress commands."
		)
		assertFalse(
			readerScreenText.contains("coordinator.dispatchBridgeCommand(ReaderBridgeCommand.GoToProgress"),
			"Progress rail gestures must remain above the engine bridge so PDF/EPUB adapters can translate them independently."
		)
		assertContains(chapterNavigatorText, "Slider(")
		assertContains(appBarsText, "onGoToChapterPage: (Int) -> Unit")
	}

	@Test
	fun androidReaderShowsPaginationProfilingStatusInKomikkuOverlay() {
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val profileBadgeText = readerCommonUiFile("ReaderPaginationProfileBadge.kt").readText()
		val controllerText = readerCommonFile("ReaderController.kt").readText()
		val bridgeText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertContains(bridgeText, "PaginationProfileStatusChanged")
		assertContains(controllerText, "paginationProfile: ReaderPaginationProfileStatus")
		assertContains(readerRootText, "KomikkuPaginationProfileStatusBadge(")
		assertContains(readerRootText, "controllerState.paginationProfile")
		assertContains(profileBadgeText, "LinearProgressIndicator(")
		assertContains(profileBadgeText, "profile.label")
	}

	@Test
	fun androidReaderBridgePortsAnxStyleScrolledEdgePageTurns() {
		val bridgeText = readerBridgeText()

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
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val chapterNavigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()

		assertContains(appBarsText, "onPreviousPage: () -> Unit")
		assertContains(appBarsText, "onNextPage: () -> Unit")
		assertContains(readerScreenText, "ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)")
		assertContains(readerScreenText, "ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)")
		assertFalse(
			readerScreenText.contains("ReaderBridgeCommand.PreviousPage"),
			"The Komikku reader shell must not dispatch raw bridge page-turn commands."
		)
		assertFalse(
			readerScreenText.contains("ReaderBridgeCommand.NextPage"),
			"The Komikku reader shell must not dispatch raw bridge page-turn commands."
		)
		assertContains(chapterNavigatorText, "Icons.Outlined.SkipPrevious")
		assertContains(chapterNavigatorText, "Icons.Outlined.SkipNext")
		assertFalse(
			chapterNavigatorText.contains("Icons.Filled.SkipPrevious") ||
				chapterNavigatorText.contains("Icons.Filled.SkipNext"),
			"Komikku page-turn controls use outlined skip icons; Navic must not lock the reader chrome to filled icons."
		)
	}

	@Test
	fun readerChromeIsImmersiveAndDrivenByNativeReaderSurfaceTaps() {
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val readerCoordinatorText = readerCommonFile("ReaderCoordinator.kt").readText()
		val foliateAdapterText = readerCommonFile("FoliateEpubEngineAdapter.kt").readText()

		assertContains(readerScreenText, "KomikkuReaderRoot(")
		assertContains(readerRootText, "KomikkuReaderNativeFrameHost(")
		assertContains(nativeFrameHostText, "KomikkuReaderNativeViewerContainer")
		assertContains(nativeFrameHostText, "override fun onSingleTapConfirmed(event: MotionEvent): Boolean")
		assertContains(nativeFrameHostText, "navigator.getAction(")
		assertContains(nativeFrameHostText, "onAction(action)")
		assertContains(readerRootText, "readerShellCoverViewerActionFor(action)")
		assertContains(readerRootText, "viewer.viewerActionFor(action)")
		assertContains(runtimeText, "attachReaderTapZoneGesture")
		assertContains(runtimeText, "post({ type: 'readerCenterTap' })")
		assertContains(readerScreenText, "fun handleEngineHostEvent(event: ReaderEngineHostEvent)")
		assertContains(readerScreenText, "onEngineHostEvent = { event -> handleEngineHostEvent(event) }")
		assertContains(readerScreenText, "applyCoordinatorStep(coordinator.onEngineHostEvent(event))")
		assertContains(readerCoordinatorText, "fun onEngineHostEvent(event: ReaderEngineHostEvent)")
		assertContains(foliateAdapterText, "override fun onHostEvent(event: ReaderEngineHostEvent)")
		assertContains(foliateAdapterText, "is ReaderEngineHostEvent.FoliateBridge -> onBridgeEvent(event.event)")
		assertContains(readerRootText, "controllerState.menuVisible")
		assertContains(readerRootText, "visible = controllerState.menuVisible")
		assertFalse(
			webViewHostText.contains("ReaderSurfaceHost") ||
				webViewHostText.contains("dispatchReaderWideTap"),
			"The renderer WebView host must not own reader-wide tap/page commands in the Komikku backbone."
		)
		assertFalse(
			readerScreenText.contains("ReaderBridgeEvent.CenterTap") || readerRootText.contains("ReaderBridgeEvent.CenterTap"),
			"ReaderScreen must not handle raw Foliate center taps; the engine host and coordinator own that boundary."
		)
		assertFalse(
			readerScreenText.contains("ReaderNativeTapOverlay(") || readerRootText.contains("ReaderNativeTapOverlay("),
			"The Komikku-style touch manager must be the native Android reader surface, not a Compose overlay."
		)
		assertFalse(
			readerScreenText.contains("RootTopBar("),
			"ReaderScreen must not show the global search/settings/account top bar in the reading area."
		)
	}

	@Test
	fun androidReaderSupportsPdfSurfaceNavigationAndScrolling() {
		val bridgeText = readerBridgeText()
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val surfaceGesture = bridgeText
			.substringAfter("attachSurfaceTapGesture(element) {")
			.substringBefore("\n  readerTapZoneActionForPoint")

		assertContains(bridgeText, "attachSurfaceTapGesture")
		assertContains(bridgeText, "this.attachSurfaceTapGesture(this.view)")
		assertContains(bridgeText, "this.attachReaderTapZoneGesture(this.view)")
		assertContains(bridgeText, "this.view?.isFixedLayout === true")
		assertContains(bridgeText, "overflow: fixedLayout ? 'auto' : 'hidden'")
		assertContains(bridgeText, "FixedLayoutSurfaceSwipeThreshold")
		assertContains(bridgeText, "turnFixedLayoutSwipePage(deltaX)")
		assertContains(surfaceGesture, "element.addEventListener('touchstart'")
		assertContains(surfaceGesture, "element.addEventListener('touchmove'")
		assertContains(surfaceGesture, "element.addEventListener('touchend'")
		assertContains(surfaceGesture, "element.addEventListener('touchcancel'")
		assertContains(surfaceGesture, "Math.abs(deltaX) >= FixedLayoutSurfaceSwipeThreshold")
		assertContains(surfaceGesture, "await this.turnFixedLayoutSwipePage(deltaX)")
		assertContains(surfaceGesture, "markReaderSurfaceTapHandled(element, event)")
		assertFalse(
			surfaceGesture.contains("handleReaderTapZone"),
			"PDF/WebView surface swipe handling must stay separate from readable tap-zone classification."
		)
		assertContains(bridgeText, "startLocator?.progress")
		assertContains(bridgeText, "await this.goToProgress(progress)")
	}

	@Test
	fun androidReaderKeepsSyntheticTapSuppressionForFixedLayoutSwipeNavigation() {
		val bridgeText = readerBridgeText()
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val surfaceTouchEnd = bridgeText
			.substringAfter("element.addEventListener('touchend', async event => {")
			.substringBefore("\n    }, { passive: false })")
		val surfaceClick = bridgeText
			.substringAfter("element.addEventListener('click', event => {")
			.substringBefore("\n    }, { passive: false })")

		assertContains(bridgeText, "markReaderSurfaceTapHandled")
		assertContains(bridgeText, "__navicSuppressNextSurfaceClickUntil")
		assertFalse(runtimeText.contains("markReaderDocumentTapHandled"))
		assertFalse(runtimeText.contains("__navicSuppressNextTapClickUntil"))
		assertContains(surfaceTouchEnd, "markReaderSurfaceTapHandled(element, event)")
		assertTrue(
			surfaceTouchEnd.indexOf("markReaderSurfaceTapHandled(element, event)") <
				surfaceTouchEnd.indexOf("await this.turnFixedLayoutSwipePage(deltaX)"),
			"Fixed-layout swipe handlers must mark the touch as handled before awaiting navigation so Android WebView synthetic clicks cannot turn a second page."
		)
		assertContains(surfaceClick, "shouldSuppressReaderSurfaceClick(element, event)")
		assertContains(surfaceClick, "event.preventDefault()")
	}

	@Test
	fun readableContentTapsAreObservedByNativeSurfaceAfterChildDispatchLikeKomikku() {
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val bridgeText = readerBridgeText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val dispatchTouchEvent = nativeFrameHostText
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\tprivate fun handleSwipeTouchEvent")

		assertContains(nativeFrameHostText, "override fun dispatchTouchEvent(event: MotionEvent): Boolean")
		assertContains(nativeFrameHostText, "val handled = super.dispatchTouchEvent(event)")
		assertContains(nativeFrameHostText, "handleSwipeTouchEvent(event)")
		assertContains(nativeFrameHostText, "gestureDetector.onTouchEvent(event)")
		assertContains(nativeFrameHostText, "MotionEvent.ACTION_DOWN")
		assertContains(nativeFrameHostText, "override fun onSingleTapConfirmed(event: MotionEvent): Boolean")
		assertContains(nativeFrameHostText, "navigator.getAction(")
		assertContains(nativeFrameHostText, "val consumed = handled || nativeSwipeIntercepted || horizontalSwipeDispatched")
		assertFalse(
			nativeFrameHostText.contains("nativeShortTapIntercepted"),
			"Komikku's Pager does not intercept plain ACTION_UP taps; it lets the child receive the stream, then observes confirmed taps through GestureDetector."
		)
		assertTrue(
			dispatchTouchEvent.indexOf("val handled = super.dispatchTouchEvent(event)") <
				dispatchTouchEvent.indexOf("gestureDetector.onTouchEvent(event)"),
			"Komikku's pager gives the child/page stream first, then observes confirmed taps without stealing drags."
		)
		assertFalse(
			webViewHostText.contains("event.action =") || webViewHostText.contains("event.setAction("),
			"The native surface must observe the child touch stream without rewriting WebView/Foliate events."
		)
		assertFalse(
			webViewHostText.contains("ReaderSurfaceHost") ||
				webViewHostText.contains("dispatchReaderWideTap"),
			"Readable content taps are observed by the native frame, not the renderer WebView host."
		)
		assertContains(bridgeText, "this.attachReaderTapZoneGesture(this.view)")
		assertContains(bridgeText, "this.attachReaderTapZoneGesture(doc)")
		assertContains(bridgeText, "target.addEventListener('touchstart'")
		assertContains(bridgeText, "target.addEventListener('touchend'")
		assertContains(bridgeText, "CenterTapMovementSlop")
	}

	@Test
	fun androidWebViewIsWrappedBySingleNativeReaderSurfaceGestureManager() {
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val readerScreenText = readerScreenFile().readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val viewerHostText = readerViewerHostFile().readText()

		assertContains(readerScreenText, "KomikkuReaderRoot(")
		assertContains(readerRootText, "KomikkuReaderNativeFrameHost(")
		assertContains(readerRootText, "viewerContent = {")
		assertContains(readerRootText, "ReaderViewerHost(")
		assertContains(nativeFrameHostText, "private val viewerContainer = KomikkuReaderNativeViewerContainer(context)")
		assertContains(nativeFrameHostText, "private val shellCoverView = KomikkuReaderNativeShellCoverView(context)")
		assertContains(nativeFrameHostText, "viewerContainer.setShellCoverView(shellCoverView)")
		assertContains(nativeFrameHostText, "viewerContainer.replaceViewerContent(viewerView)")
		assertContains(viewerHostText, "ReaderEngineWebViewHost(")
		assertFalse(
			webViewHostText.contains("ReaderSurfaceHost") ||
				webViewHostText.contains("ReaderAndroidTapZoneObserver") ||
				webViewHostText.contains("readerWideTapsEnabled"),
			"The old split manager made cover and content taps diverge; keep the reader surface manager in the native frame."
		)
		assertFalse(webViewHostText.contains("setOnTouchListener"))
	}

	@Test
	fun nativeShellCoverIsRenderedByReaderShellViewNotWebViewHtml() {
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()

		assertContains(
			nativeFrameHostText,
			"private class KomikkuReaderNativeShellCoverView",
			message = "The shell cover must be an Android view owned by the reader shell, not an HTML page in a second WebView."
		)
		assertContains(nativeFrameHostText, "fun setShellCover(coverUrl: String?, title: String)")
		assertContains(nativeFrameHostText, "readerShellCoverFileFor(")
		assertContains(nativeFrameHostText, "BitmapFactory.decodeFile")
		assertContains(nativeFrameHostText, "canvas.drawColor(Color.BLACK)")
		assertContains(nativeFrameHostText, "canvas.drawBitmap")
		assertContains(nativeFrameHostText, "Paint(Paint.ANTI_ALIAS_FLAG")
		assertFalse(
			webViewHostText.contains("shellCoverWebView"),
			"Keeping a second cover WebView preserves the cover-only input/sizing bug class."
		)
		assertFalse(
			webViewHostText.contains("readerCoverWebView"),
			"Reader cover creation must not instantiate a WebView."
		)
		assertFalse(
			webViewHostText.contains("readerShellCoverHtml"),
			"The native shell cover must not be fixed by editing shell-cover HTML/CSS."
		)
		assertFalse(
			webViewHostText.contains("loadDataWithBaseURL"),
			"The shell cover must draw a decoded cover image, not load an HTML wrapper."
		)
	}

	@Test
	fun nativeShellCoverSupportsHorizontalSwipeAndNativeReadableDragPreview() {
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val dispatchTouchEvent = nativeFrameHostText
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\tprivate fun handleSwipeTouchEvent")
		val handleTouch = nativeFrameHostText
			.substringAfter("private fun handleSwipeTouchEvent(event: MotionEvent) {")
			.substringBefore("\n\tprivate fun dispatchHorizontalSwipeViewerAction")
		val actionMove = handleTouch
			.substringAfter("MotionEvent.ACTION_MOVE,")
			.substringBefore("if (event.actionMasked == MotionEvent.ACTION_UP)")
		val shellCoverSwipe = nativeFrameHostText
			.substringAfter("private fun dispatchHorizontalSwipeViewerAction(deltaX: Float, deltaY: Float): Boolean {")
			.substringBefore("\n\tprivate fun nativeTapMovedBeyondSlop")

		assertContains(nativeFrameHostText, "private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()")
		assertContains(handleTouch, "dispatchHorizontalSwipeViewerAction(")
		assertContains(handleTouch, "val shellCoverVisible = shellCoverView?.visibility == VISIBLE")
		assertContains(handleTouch, "if (shellCoverVisible)")
		assertContains(handleTouch, "updateReadableViewerDragOffset(dx, ReaderPageDragPreviewPhase.Update)")
		assertContains(handleTouch, "ReaderPageDragPreviewPhase.Release")
		assertContains(handleTouch, "ReaderPageDragPreviewPhase.Cancel")
		assertContains(nativeFrameHostText, "Reader native drag preview")
		assertContains(actionMove, "dispatchHorizontalSwipeViewerAction(")
		assertContains(handleTouch, "MotionEvent.ACTION_UP")
		assertContains(shellCoverSwipe, "readerShellCoverSwipeAction(")
		assertContains(shellCoverSwipe, "readerNativeReaderSwipeAction(")
		assertContains(shellCoverSwipe, "touchSlopPx")
		assertContains(shellCoverSwipe, "onAction(KomikkuNavigationRegion.NEXT)")
		assertContains(shellCoverSwipe, "onAction(KomikkuNavigationRegion.PREV)")
		assertContains(dispatchTouchEvent, "val handled = super.dispatchTouchEvent(event)")
		assertContains(nativeFrameHostText, "private fun updateReadableViewerDragOffset(")
		assertContains(nativeFrameHostText, "onReadableDragPreview(deltaX, width, phase)")
		assertFalse(
			nativeFrameHostText.contains("viewerContentContainer.translationX = deltaX"),
			"Readable drag preview must not slide the whole WebView over the native background."
		)
	}

	@Test
	fun nativeShellCoverSwipeUsesTapSlopSizedHorizontalDragContract() {
		assertEquals(null, readerShellCoverSwipeAction(deltaX = -9f, deltaY = 1f, thresholdPx = 10f))
		assertEquals(ReaderTapZoneAction.Right, readerShellCoverSwipeAction(deltaX = -11f, deltaY = 2f, thresholdPx = 10f))
		assertEquals(ReaderTapZoneAction.Left, readerShellCoverSwipeAction(deltaX = 11f, deltaY = 2f, thresholdPx = 10f))
		assertEquals(
			ReaderTapZoneAction.Right,
			readerShellCoverSwipeAction(deltaX = -11f, deltaY = 13f, thresholdPx = 10f),
			"Shell-cover drags should tolerate natural vertical drift because there is no readable WebView scroll stream to protect."
		)

		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val shellCoverSwipe = nativeFrameHostText
			.substringAfter("private fun dispatchHorizontalSwipeViewerAction(deltaX: Float, deltaY: Float): Boolean {")
			.substringBefore("\n\tprivate fun nativeTapMovedBeyondSlop")

		assertContains(
			nativeFrameHostText,
			"private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()",
			message = "Cover-only drags should trigger once they exceed normal tap slop, not Android's larger paging slop."
		)
		assertContains(
			shellCoverSwipe,
			"readerShellCoverSwipeAction(",
			message = "Android cover-drag handling must use the behavior-tested shell-cover swipe decision."
		)
	}

	@Test
	fun nativeShellCoverLogsDragCandidatesBeforeSwipeDispatch() {
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val handleTouch = nativeFrameHostText
			.substringAfter("private fun handleSwipeTouchEvent(event: MotionEvent) {")
			.substringBefore("\n\tprivate fun dispatchHorizontalSwipeViewerAction")
		val actionDown = handleTouch
			.substringAfter("MotionEvent.ACTION_DOWN -> {")
			.substringBefore("\n\t\t\tMotionEvent.ACTION_POINTER_DOWN")
		val actionMove = handleTouch
			.substringAfter("MotionEvent.ACTION_MOVE,")
			.substringBefore("if (event.actionMasked == MotionEvent.ACTION_UP)")

		assertContains(nativeFrameHostText, "private var shellCoverDragDiagnosticLogged: Boolean = false")
		assertContains(actionDown, "shellCoverDragDiagnosticLogged = false")
		assertContains(actionMove, "logReaderDragCandidate(dx, dy)")
		assertContains(nativeFrameHostText, "private fun logReaderDragCandidate(deltaX: Float, deltaY: Float)")
		assertContains(nativeFrameHostText, "Reader shell cover drag candidate")
		assertContains(nativeFrameHostText, "Reader native drag candidate")
		assertTrue(
			actionMove.indexOf("logReaderDragCandidate(dx, dy)") <
				actionMove.indexOf("dispatchHorizontalSwipeViewerAction("),
			"Cover drag diagnostics must run before swipe dispatch so failed drags still leave ADB evidence."
		)
	}

	@Test
	fun nativeShellCoverTouchStreamSharesKomikkuChildFirstGestureOwner() {
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val dispatchTouchEvent = nativeFrameHostText
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\tprivate fun handleSwipeTouchEvent")
		val interceptTouchEvent = nativeFrameHostText
			.substringAfter("override fun onInterceptTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\t}\n\n\t")

		assertContains(dispatchTouchEvent, "val handled = super.dispatchTouchEvent(event)")
		assertContains(dispatchTouchEvent, "handleSwipeTouchEvent(event)")
		assertContains(dispatchTouchEvent, "gestureDetector.onTouchEvent(event)")
		assertContains(dispatchTouchEvent, "val consumed = handled || nativeSwipeIntercepted || horizontalSwipeDispatched")
		assertContains(dispatchTouchEvent, "return consumed")
		assertFalse(
			interceptTouchEvent.contains("nativeShortTapIntercepted") ||
				interceptTouchEvent.contains("return nativeTapCandidate"),
			"Shell-cover taps must follow Komikku's child-first Pager dispatch; the native container must not intercept ACTION_UP just to own short taps."
		)
		assertTrue(
			dispatchTouchEvent.indexOf("val handled = super.dispatchTouchEvent(event)") <
				dispatchTouchEvent.indexOf("handleSwipeTouchEvent(event)"),
			"Shell-cover drags must observe the same child-dispatched stream instead of bypassing the cover renderer."
		)
		assertTrue(
			dispatchTouchEvent.indexOf("handleSwipeTouchEvent(event)") <
				dispatchTouchEvent.indexOf("gestureDetector.onTouchEvent(event)"),
			"Shell-cover drag observation and reader tap detection must share the same native surface stream."
		)
		assertContains(dispatchTouchEvent, "handled")
	}

	@Test
	fun nativeReaderSurfaceDoesNotDiscardPlainImageTapsBeforeTapZoneDispatch() {
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()

		assertContains(nativeFrameHostText, "navigator.getAction(")
		assertContains(nativeFrameHostText, "onAction(action)")
		assertFalse(
			nativeFrameHostText.contains("WebView.HitTestResult.IMAGE_TYPE") ||
				webViewHostText.contains("readerContentHandledTap("),
			"Plain image hits, including image-heavy EPUB cover pages, must not blanket-block native previous/next/menu tap zones."
		)
	}

	@Test
	fun nativeReaderSurfaceCenterMenuIsOwnedByNativeFrameInsteadOfWebViewHitTesting() {
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val singleTap = nativeFrameHostText
			.substringAfter("override fun onSingleTapConfirmed(event: MotionEvent): Boolean {")
			.substringBefore("\n\t\t\t}")
		val viewerContainerBody = nativeFrameHostText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("private class KomikkuGestureDetectorWithLongTap")

		assertContains(singleTap, "navigator.getAction(")
		assertContains(singleTap, "dispatchSingleTapAction(action)")
		assertContains(viewerContainerBody, "if (action != KomikkuNavigationRegion.MENU)")
		assertFalse(
			viewerContainerBody.contains("dispatchMenuActionAfterContentHitTest") ||
				viewerContainerBody.contains("readerContentActionAtPoint") ||
				viewerContainerBody.contains("findReaderWebView") ||
				singleTap.contains("WebView.HitTestResult.IMAGE_TYPE") ||
				webViewHostText.contains("readerContentHandledCenterTap("),
			"Short center-menu taps must be native-owned; WebView content hit testing belongs to deliberate long press."
		)
		assertTrue(
			singleTap.indexOf("navigator.getAction(") <
				singleTap.indexOf("dispatchSingleTapAction(action)"),
			"Native frame tap classification must decide and dispatch the viewer action without a menu-only renderer hit test."
		)
	}

	@Test
	fun nativeShellCoverReturnUsesReaderShellStateBeforeLocatorStateCatchesUp() {
		val controllerText = readerCommonFile("ReaderController.kt").readText()
		val chromeStateText = readerCommonFile("ReaderChromeState.kt").readText()
		val shellCoverViewerAction = controllerText
			.substringAfter("private fun onShellCoverViewerAction(action: ReaderViewerAction): ReaderControllerStep {")
			.substringBefore("\n\tprivate fun turnPage")
		val pageTurn = controllerText
			.substringAfter("private fun turnPage(direction: ReaderPageTurnDirection): ReaderControllerStep =")
			.substringBefore("\n\tprivate fun scrollViewport")

		assertContains(
			controllerText,
			"nativeShellCoverUrl = normalizedRequest.nativeShellCoverUrl",
			message = "The native shell cover must be a real virtual reader page, not only a locator-derived condition from Compose."
		)
		assertContains(
			shellCoverViewerAction,
			"shellCoverVisible = false",
			message = "Next from the native cover must leave the virtual cover before delegating normal page state to Foliate."
		)
		assertContains(
			pageTurn,
			"state.canReturnToShellCover",
			message = "Previous from the first readable page must return to the native cover using controller-owned shell state before delegating to Foliate."
		)
		assertContains(
			pageTurn,
			"readerShouldReturnToNativeShellCover(",
			message = "The shell-cover return boundary must be decided before creating an engine page-turn command."
		)
		assertContains(chromeStateText, "fun readerShouldReturnToNativeShellCover(")
	}

	@Test
	fun androidReaderPreservesProgressOnlyResumeLocatorsForFixedLayoutPublications() {
		val readerViewerHostText = readerViewerHostFile().readText()
		val webViewHostText = readerEngineWebViewHostFile().readText()

		assertContains(readerViewerHostText, "startProgress = engineRenderer.startLocator?.progress")
		assertContains(webViewHostText, "startProgress: Double?")
		assertContains(webViewHostText, "startProgress?.toString().orEmpty()")
		assertContains(webViewHostText, "progress = startProgress")
		assertContains(webViewHostText, "it.cfi != null || it.href != null || it.progress != null")
	}

	@Test
	fun androidReaderPublishesPostReadyLocationSnapshotAfterResumeSeek() {
		val bridgeText = readerBridgeText()
		val openPublication = bridgeText
			.substringAfter("async openPublication({ url, mediaOverlayEnabled = false, startLocator = null, settings = null }) {")
			.substringBefore("\n  close()")
		val onRelocate = bridgeText
			.substringAfter("onRelocate(detail) {")
			.substringBefore("\n  cancelPendingCommittedRelocation")

		assertContains(bridgeText, "lastRelocateDetail = null")
		assertContains(bridgeText, "postLocationChanged(detail")
		assertContains(bridgeText, "postCurrentLocationSnapshot('initial-resume')")
		assertContains(onRelocate, "this.lastRelocateDetail = detail")
		assertContains(onRelocate, "this.scheduleCommittedRelocation(detail)")
		assertTrue(
			openPublication.indexOf("post({ type: 'publicationReady' })") <
				openPublication.indexOf("this.postCurrentLocationSnapshot('initial-resume')"),
			"PublicationReady must be sent before the synthetic location snapshot so native progress saving is armed."
		)
	}

	@Test
	fun androidReaderReportsFixedLayoutPagePositionToChrome() {
		val bridgeText = readerBridgeText()
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
	fun androidReaderReportsDynamicReflowablePagePositionToChrome() {
		val bridgeText = readerBridgeText()
		val readerScreenText = readerScreenFile().readText()

		assertContains(bridgeText, "reflowablePagePosition(detail)")
		assertContains(bridgeText, "reflowableLocationPagePosition(detail)")
		assertContains(bridgeText, "reflowableSectionPagePosition()")
		assertContains(bridgeText, "readerPaginationProfilePosition(detail, sectionPosition)")
		assertContains(bridgeText, "readerEnsurePaginationProfile(detail, sectionPosition)")
		assertContains(bridgeText, "const sectionIndex = Number(detail?.section?.current ?? detail?.index)")
		assertContains(bridgeText, "spineIndex: sectionIndex")
		assertContains(bridgeText, "readerPaginationRenderFingerprint()")
		assertContains(bridgeText, "readCachedPaginationProfile(fingerprint)")
		assertContains(bridgeText, "writeCachedPaginationProfile(freshProfile)")
		assertContains(bridgeText, "reflowableWholeBookPagePosition(detail)")
		assertContains(bridgeText, "const renderer = this.view?.renderer")
		assertContains(bridgeText, "if (!renderer || renderer.scrolled) return null")
		assertContains(bridgeText, "let page")
		assertContains(bridgeText, "let pages")
		assertContains(bridgeText, "page = Number(renderer.page)")
		assertContains(bridgeText, "pages = Number(renderer.pages)")
		assertContains(bridgeText, "reflowable-section-pages:pending")
		assertContains(bridgeText, "const pageCount = Math.max(1, Math.round(pages) - 1)")
		assertContains(bridgeText, "pageIndex: Math.min(pageCount - 1, Math.max(0, Math.floor(page - 1)))")
		assertContains(bridgeText, "const sectionSizes = this.reflowableSectionSizes()")
		assertContains(bridgeText, "reflowableBookPageModel")
		assertContains(bridgeText, "reflowableStableBookPageModel(normalizedSectionIndex, sectionPosition, sectionSizes)")
		assertContains(bridgeText, "Math.ceil(model.totalReadableSize / model.readableUnitsPerPage)")
		assertContains(bridgeText, "this.reflowableBookPageModel = null")
		assertContains(bridgeText, "reflowable-page-model:set")
		assertFalse(
			bridgeText.contains("currentSectionSize / sectionPosition.pageCount"),
			"Reflowable EPUB totals must not be estimated from the current section only, because that makes # / # change between chapters."
		)
		assertContains(bridgeText, "readerPageListPageCount()")
		assertContains(bridgeText, "pageCountSource: 'pagination-profile'")
		assertContains(bridgeText, "pageCountSource: 'page-list'")
		assertContains(bridgeText, "pageCountSource: 'location'")
		assertContains(bridgeText, "pageCountSource: model.source")
		assertFalse(
			bridgeText.contains("this.reflowableBookPageModel.source !== 'rendered-section' && canUseRenderedSection"),
			"Reflowable EPUB totals must not be upgraded from the currently rendered section, because normal relocation events may lack pageItem and then the denominator changes while reading."
		)
		assertContains(
			bridgeText,
			"pageIndex: Math.min(pageCount - 1, Math.max(0, Math.floor(page - 1)))",
			message = "Foliate's 1-based section page must be converted to a zero-based page index after EPUB cover suppression."
		)
		assertContains(bridgeText, "readerPagePosition(detail)")
		assertContains(bridgeText, "this.readerPaginationProfilePosition(detail, sectionPosition) ||")
		assertContains(bridgeText, "this.reflowableWholeBookPagePosition(detail) ||")
		assertContains(bridgeText, "this.reflowableLocationPagePosition(detail) ||")
		assertContains(bridgeText, "ensureReaderPageNumberLayer")
		assertContains(bridgeText, "dataset.navicPageNumberLayer")
		assertContains(bridgeText, "return `${'$'}{currentPage} / ${'$'}{pageCount}`")
		assertContains(bridgeText, "readerPageNumberPositionWithPageCount(pagePosition, this.currentPagePosition?.pageCount)")
		assertContains(bridgeText, "readerPageNumberLabel(pageNumberPosition)")
		assertFalse(
			bridgeText.contains("return String(currentPage)"),
			"Page number labels must keep the # / # design instead of falling back to a bare current page."
		)
		assertContains(bridgeText, "this.updateReaderPageNumberLayer(pagePosition)")
		assertContains(bridgeText, "font-family': 'var(--reader-page-number-font-family")
		assertFalse(
			readerScreenText.contains("ReaderPageNumberOverlay("),
			"Page numbers must be drawn in the reader surface, not as a native Material overlay."
		)
	}

	@Test
	fun androidReaderPostsPageModelDiagnosticsWithLocationChanges() {
		val bridgeText = readerBridgeText()
		val postLocationBody = bridgeText
			.substringAfter("postLocationChanged(detail, reason = 'relocate') {")
			.substringBefore("\n  onRelocate(detail) {")

		assertContains(postLocationBody, "pageCountSource: pagePosition?.pageCountSource || null")
		assertContains(postLocationBody, "paginationFingerprint: this.paginationFingerprint || null")
		assertContains(postLocationBody, "paginationProfilePageCount: diagnosticNumber(this.paginationProfile?.pageCount)")
		assertContains(postLocationBody, "paginationProfileObservedChapterCount: diagnosticNumber(this.paginationProfile?.observedChapterCount)")
		assertContains(postLocationBody, "paginationProfileEstimatedChapterCount: diagnosticNumber(this.paginationProfile?.estimatedChapterCount)")
		assertContains(postLocationBody, "rawLocationCurrent: diagnosticNumber(detail.location?.current)")
		assertContains(postLocationBody, "rawLocationTotal: diagnosticNumber(detail.location?.total)")
		assertContains(postLocationBody, "readerTrace('location:page-model'")
		assertContains(postLocationBody, "log('location-page-model'")
	}

	@Test
	fun androidReaderUsesStableLocationTotalsForReflowablePageNumbers() {
		val bridgeText = readerBridgeText()
		val closeBody = bridgeText
			.substringAfter("close() {")
			.substringBefore("applyReaderViewportLayout(label = 'unknown') {")

		assertContains(bridgeText, "reflowableLocationPagePosition(detail)")
		assertContains(bridgeText, "const location = detail?.location")
		assertContains(bridgeText, "Math.floor(clampedProgress * pageCount)")
		assertContains(bridgeText, "const advancedProgressWithinSection =")
		assertContains(bridgeText, "const progressedToNewBucketWithinSection =")
		assertContains(bridgeText, "this.reflowableLastLocationProgress")
		assertContains(bridgeText, "const advancedToLaterSection =")
		assertContains(bridgeText, "this.reflowableLastLocationSignature")
		assertContains(bridgeText, "this.reflowableLastLocationProgressBucket")
		assertContains(bridgeText, "const canApplyStartOffset = pagePosition.pageCountSource !== 'location'")
		assertContains(bridgeText, "pageCountSource: 'location'")
		assertContains(bridgeText, "pageCountSource: 'pagination-profile'")
		assertContains(bridgeText, "paginationProfile = null")
		assertContains(bridgeText, "paginationFingerprint = null")
		assertContains(bridgeText, "observedChapterPageCounts = new Map()")
		assertContains(
			bridgeText,
			"return this.readerPaginationProfilePosition(detail, sectionPosition) ||",
			message = "Reflowable EPUB labels must prefer the deterministic pagination profile before whole-book, location, page-list, or section fallbacks."
		)
		assertContains(
			bridgeText,
			"this.reflowableWholeBookPagePosition(detail) ||",
			message = "The previous stable whole-book model remains the first fallback while pagination profiles warm."
		)
		assertFalse(
			bridgeText.contains("return this.readerPageListPosition(detail) || this.reflowableWholeBookPagePosition(detail)"),
			"Reflowable EPUB labels must not opportunistically switch to sparse page-list totals."
		)
		assertFalse(
			bridgeText.contains("const prefersOwnPageCount ="),
			"Book-level reflowable positions must keep their own denominator instead of being overwritten by a stale fallback count."
		)
		assertContains(
			closeBody,
			"this.reflowablePageIndexOffset = null",
			message = "The first-page offset must be scoped to one publication, otherwise a later book can start at the wrong visible page number."
		)
		assertContains(closeBody, "this.reflowableLastLocationSignature = null")
		assertContains(closeBody, "this.reflowableLastLocationPageIndex = null")
		assertContains(closeBody, "this.reflowableLastLocationSectionIndex = null")
		assertContains(closeBody, "this.reflowableLastLocationProgressBucket = null")
	}

	@Test
	fun androidReaderClampsDelayedPassiveReflowableRelocationsAfterPageTurns() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "passiveCommittedRelocationPosition(pagePosition, detail, reason)")
		assertContains(bridgeText, "if (String(reason || '') !== 'relocate-committed') return pagePosition")
		assertContains(bridgeText, "this.committedRelocateDetail")
		assertContains(bridgeText, "this.detailSectionKey(detail)")
		assertContains(bridgeText, "this.recentPageTurnDirection")
		assertContains(bridgeText, "this.recentPageTurnDirection = direction")
		assertContains(bridgeText, "const recentPageTurnDirection = this.recentPageTurnDirection")
		assertContains(bridgeText, "const canClampAcrossSections")
		assertContains(
			bridgeText,
			"if (!sameSection && !canClampAcrossSections) return pagePosition",
			message = "Passive relocations after sequential page turns must clamp across frontmatter/section boundaries, but unrelated passive jumps must not."
		)
		assertContains(bridgeText, "Math.abs(candidatePageIndex - currentPageIndex) <= 1")
		assertContains(bridgeText, "const direction = hasRecentPageTurn")
		assertContains(bridgeText, "pageIndex: Math.min(pageCount - 1, Math.max(0, currentPageIndex + direction))")
		assertContains(
			bridgeText,
			"this.scheduleCommittedRelocation(this.lastRelocateDetail, 'go-to')",
			message = "Link and explicit href navigation must mark the next relocation as a real jump, not as a passive page-turn aftershock."
		)
		assertContains(bridgeText, "this.recentPageTurnDirection = null")
		assertContains(
			bridgeText,
			"this.scheduleCommittedRelocation(this.lastRelocateDetail, 'progress-seek')",
			message = "Progress rail navigation must mark the next relocation as a real jump, not as a passive page-turn aftershock."
		)
	}

}
