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

		assertContains(bridgeText, "case 'goToProgress'")
		assertContains(bridgeText, "async goToProgress(progress)")
		assertContains(bridgeText, "this.view?.goToFraction")
		assertContains(bridgeText, "progress-seek")
		assertContains(readerScreenText, "coordinator.navigateTo(")
		assertContains(readerScreenText, "ReaderLocator(progress = progress)")
		assertFalse(
			readerScreenText.contains("ReaderBridgeCommand.GoToProgress"),
			"The Komikku reader shell must ask the controller to navigate, not dispatch raw Foliate bridge progress commands."
		)
		assertFalse(
			readerScreenText.contains("coordinator.dispatchBridgeCommand(ReaderBridgeCommand.GoToProgress"),
			"Progress rail gestures must remain above the engine bridge so PDF/EPUB adapters can translate them independently."
		)
		assertContains(readerScreenText, "Slider(")
		assertContains(readerScreenText, "onGoToProgress: (Double) -> Unit")
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

		assertContains(readerScreenText, "onPreviousPage: () -> Unit")
		assertContains(readerScreenText, "onNextPage: () -> Unit")
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
		assertContains(readerScreenText, "Icons.Filled.SkipPrevious")
		assertContains(readerScreenText, "Icons.Filled.SkipNext")
	}

	@Test
	fun readerChromeIsImmersiveAndDrivenByNativeReaderSurfaceTaps() {
		val bridgeText = readerBridgeText()
		val runtimeText = readerAssetRoot().resolve("navic-reader.js").readText()
		val readerScreenText = readerScreenFile().readText()
		val webViewHostText = readerWebViewHostFile().readText()

		assertContains(webViewHostText, "ReaderSurfaceHost")
		assertContains(webViewHostText, "dispatchReaderWideTap")
		assertContains(webViewHostText, "ReaderBridgeEvent.CenterTap")
		assertContains(webViewHostText, "ReaderBridgeCommand.PreviousPage")
		assertContains(webViewHostText, "ReaderBridgeCommand.NextPage")
		assertContains(runtimeText, "attachReaderTapZoneGesture")
		assertContains(runtimeText, "post({ type: 'readerCenterTap' })")
		assertContains(readerScreenText, "event is ReaderBridgeEvent.CenterTap")
		assertContains(readerScreenText, "chromeVisible")
		assertContains(readerScreenText, "if (chromeVisible)")
		assertFalse(
			readerScreenText.contains("ReaderNativeTapOverlay("),
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
		val webViewHostText = readerWebViewHostFile().readText()
		val bridgeText = readerBridgeText()
		val dispatchTouchEvent = webViewHostText
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\tprivate val readerGestureDetector")

		assertContains(webViewHostText, "override fun dispatchTouchEvent(event: MotionEvent): Boolean")
		assertContains(webViewHostText, "val childHandled = super.dispatchTouchEvent(event)")
		assertContains(webViewHostText, "readerGestureDetector.onTouchEvent(event)")
		assertContains(webViewHostText, "MotionEvent.ACTION_DOWN")
		assertContains(webViewHostText, "override fun onSingleTapConfirmed(event: MotionEvent): Boolean")
		assertContains(webViewHostText, "dispatchReaderWideTap(event)")
		assertContains(webViewHostText, "childHandled")
		assertContains(webViewHostText, "return if (readerWideTapsEnabled && shellCoverWasVisible)")
		assertContains(webViewHostText, "readerTapZonePageTurnDirectionFor(")
		assertTrue(
			dispatchTouchEvent.indexOf("val childHandled = super.dispatchTouchEvent(event)") <
				dispatchTouchEvent.indexOf("readerGestureDetector.onTouchEvent(event)"),
			"Komikku's pager gives the child/page stream first, then observes confirmed taps without stealing drags."
		)
		assertFalse(
			webViewHostText.contains("event.action =") || webViewHostText.contains("event.setAction("),
			"The native surface must observe the child touch stream without rewriting WebView/Foliate events."
		)
		assertContains(bridgeText, "this.attachReaderTapZoneGesture(this.view)")
		assertContains(bridgeText, "this.attachReaderTapZoneGesture(doc)")
		assertContains(bridgeText, "target.addEventListener('touchstart'")
		assertContains(bridgeText, "target.addEventListener('touchend'")
		assertContains(bridgeText, "CenterTapMovementSlop")
	}

	@Test
	fun androidWebViewIsWrappedBySingleNativeReaderSurfaceGestureManager() {
		val webViewHostText = readerWebViewHostFile().readText()

		assertContains(webViewHostText, "ReaderSurfaceHost")
		assertContains(webViewHostText, "addView(")
		assertContains(webViewHostText, "readerWebView,")
		assertContains(webViewHostText, "ReaderShellCoverView")
		assertContains(webViewHostText, "shellCoverView")
		assertContains(webViewHostText, "readerWideTapsEnabled")
		assertContains(webViewHostText, "onReaderCommand")
		assertContains(webViewHostText, "onReaderCenterTap")
		assertFalse(
			webViewHostText.contains("ReaderAndroidTapZoneObserver"),
			"The old split manager made cover and content taps diverge; keep only the reader surface manager."
		)
		assertFalse(webViewHostText.contains("setOnTouchListener"))
	}

	@Test
	fun nativeShellCoverIsRenderedByReaderShellViewNotWebViewHtml() {
		val webViewHostText = readerWebViewHostFile().readText()

		assertContains(
			webViewHostText,
			"private class ReaderShellCoverView",
			message = "The shell cover must be an Android view owned by the reader shell, not an HTML page in a second WebView."
		)
		assertContains(webViewHostText, "fun updateCover(coverUrl: String?, title: String)")
		assertContains(webViewHostText, "readerPublicationCacheFileForAssetUrl(")
		assertContains(webViewHostText, "BitmapFactory.decodeFile")
		assertContains(webViewHostText, "canvas.drawBitmap")
		assertContains(webViewHostText, "Paint(Paint.ANTI_ALIAS_FLAG")
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
	fun nativeShellCoverSupportsHorizontalSwipeWithoutHijackingReadableDrags() {
		val webViewHostText = readerWebViewHostFile().readText()
		val handleTouch = webViewHostText
			.substringAfter("private fun handleReaderSurfaceTouch(event: MotionEvent) {")
			.substringBefore("\n\tprivate fun clearTapCandidate()")
		val actionMove = handleTouch
			.substringAfter("MotionEvent.ACTION_MOVE -> {")
			.substringBefore("\n\t\t\tMotionEvent.ACTION_UP -> {")
		val shellCoverSwipe = webViewHostText
			.substringAfter("private fun dispatchReaderShellCoverSwipe(deltaX: Float, deltaY: Float): Boolean {")
			.substringBefore("\n\tprivate fun dispatchReaderWideTap")

		assertContains(webViewHostText, "private val shellCoverSwipeThresholdPx")
		assertContains(handleTouch, "dispatchReaderShellCoverSwipe(")
		assertContains(actionMove, "dispatchReaderShellCoverSwipe(")
		assertContains(actionMove, "clearTapCandidate()")
		assertContains(handleTouch, "MotionEvent.ACTION_UP")
		assertContains(shellCoverSwipe, "if (!shellCoverVisible) return false")
		assertContains(shellCoverSwipe, "readerShellCoverSwipeAction(")
		assertContains(shellCoverSwipe, "shellCoverSwipeThresholdPx")
		assertContains(shellCoverSwipe, "readerTapZonePageTurnDirectionFor(")
		assertContains(shellCoverSwipe, "dispatchReaderPageTurnCommand(command)")
		assertContains(webViewHostText, "val shellCoverWasVisible = shellCoverVisible")
		assertContains(webViewHostText, "val childHandled = super.dispatchTouchEvent(event)")
		assertContains(webViewHostText, "return if (readerWideTapsEnabled && shellCoverWasVisible)")
		assertFalse(
			webViewHostText.contains("return dispatchReaderShellCoverSwipe"),
			"Shell-cover swipe detection must observe the already-dispatched child stream, not consume readable WebView drags."
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

		val webViewHostText = readerWebViewHostFile().readText()
		val shellCoverSwipe = webViewHostText
			.substringAfter("private fun dispatchReaderShellCoverSwipe(deltaX: Float, deltaY: Float): Boolean {")
			.substringBefore("\n\tprivate fun dispatchReaderWideTap")

		assertContains(
			webViewHostText,
			"private val shellCoverSwipeThresholdPx = tapSlopPx",
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
		val webViewHostText = readerWebViewHostFile().readText()
		val handleTouch = webViewHostText
			.substringAfter("private fun handleReaderSurfaceTouch(event: MotionEvent) {")
			.substringBefore("\n\tprivate fun clearTapCandidate()")
		val actionDown = handleTouch
			.substringAfter("MotionEvent.ACTION_DOWN -> {")
			.substringBefore("\n\t\t\tMotionEvent.ACTION_POINTER_DOWN")
		val actionMove = handleTouch
			.substringAfter("MotionEvent.ACTION_MOVE -> {")
			.substringBefore("\n\t\t\tMotionEvent.ACTION_UP -> {")

		assertContains(webViewHostText, "private var shellCoverDragDiagnosticLogged: Boolean = false")
		assertContains(actionDown, "shellCoverDragDiagnosticLogged = false")
		assertContains(actionMove, "logReaderShellCoverDragCandidate(dx, dy)")
		assertContains(webViewHostText, "private fun logReaderShellCoverDragCandidate(deltaX: Float, deltaY: Float)")
		assertContains(webViewHostText, "Reader shell cover drag candidate")
		assertTrue(
			actionMove.indexOf("logReaderShellCoverDragCandidate(dx, dy)") <
				actionMove.indexOf("dispatchReaderShellCoverSwipe(dx, dy)"),
			"Cover drag diagnostics must run before swipe dispatch so failed drags still leave ADB evidence."
		)
	}

	@Test
	fun nativeShellCoverTouchStreamSharesKomikkuChildFirstGestureOwner() {
		val webViewHostText = readerWebViewHostFile().readText()
		val dispatchTouchEvent = webViewHostText
			.substringAfter("override fun dispatchTouchEvent(event: MotionEvent): Boolean {")
			.substringBefore("\n\tprivate val readerGestureDetector")

		assertContains(dispatchTouchEvent, "val shellCoverWasVisible = shellCoverVisible")
		assertContains(dispatchTouchEvent, "if (readerWideTapsEnabled)")
		assertContains(dispatchTouchEvent, "val childHandled = super.dispatchTouchEvent(event)")
		assertContains(dispatchTouchEvent, "handleReaderSurfaceTouch(event)")
		assertContains(dispatchTouchEvent, "readerGestureDetector.onTouchEvent(event)")
		assertContains(dispatchTouchEvent, "return if (readerWideTapsEnabled && shellCoverWasVisible)")
		assertTrue(
			dispatchTouchEvent.indexOf("val childHandled = super.dispatchTouchEvent(event)") <
				dispatchTouchEvent.indexOf("handleReaderSurfaceTouch(event)"),
			"Shell-cover drags must observe the same child-dispatched stream instead of bypassing the cover renderer."
		)
		assertTrue(
			dispatchTouchEvent.indexOf("handleReaderSurfaceTouch(event)") <
				dispatchTouchEvent.indexOf("readerGestureDetector.onTouchEvent(event)"),
			"Shell-cover drag observation and reader tap detection must share the same native surface stream."
		)
		assertContains(dispatchTouchEvent, "childHandled")
	}

	@Test
	fun nativeReaderSurfaceDoesNotDiscardPlainImageTapsBeforeTapZoneDispatch() {
		val webViewHostText = readerWebViewHostFile().readText()
		val contentHandledTap = webViewHostText
			.substringAfter("private fun readerContentHandledTap(hitType: Int): Boolean =")
			.substringBefore("\n}")

		assertContains(webViewHostText, "Reader surface tap ignored for content hitType=")
		assertContains(webViewHostText, "readerTapZoneActionAt(")
		assertContains(contentHandledTap, "WebView.HitTestResult.SRC_ANCHOR_TYPE")
		assertContains(contentHandledTap, "WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE")
		assertFalse(
			contentHandledTap.contains("WebView.HitTestResult.IMAGE_TYPE"),
			"Plain image hits, including image-heavy EPUB cover pages, must not blanket-block native previous/next/menu tap zones."
		)
	}

	@Test
	fun nativeReaderSurfaceCenterMenuIsNotSuppressedByRawImageHitType() {
		val webViewHostText = readerWebViewHostFile().readText()
		val dispatchWideTap = webViewHostText
			.substringAfter("private fun dispatchReaderWideTap(event: MotionEvent) {")
			.substringBefore("\n\tfun markContentTapHandled()")
		val centerHandledTap = webViewHostText
			.substringAfter("private fun readerContentHandledCenterTap(hitType: Int): Boolean =")
			.substringBefore("\n\n\tprivate fun scheduleReaderCenterTap")

		assertContains(dispatchWideTap, "readerContentHandledCenterTap(contentHitType)")
		assertContains(dispatchWideTap, "scheduleReaderCenterTap(")
		assertFalse(
			centerHandledTap.contains("WebView.HitTestResult.IMAGE_TYPE"),
			"Raw WebView IMAGE_TYPE is too broad: image-heavy pages and covers must still let center taps toggle chrome unless the content JS explicitly claims the interaction."
		)
		assertTrue(
			dispatchWideTap.indexOf("readerTapZoneActionAt(") <
				dispatchWideTap.indexOf("readerContentHandledCenterTap(contentHitType)"),
			"Image hit suppression must be center/menu-only so edge image taps can still turn pages."
		)
	}

	@Test
	fun nativeShellCoverReturnUsesReaderShellStateBeforeLocatorStateCatchesUp() {
		val webViewHostText = readerWebViewHostFile().readText()
		val pageTurn = webViewHostText
			.substringAfter("private fun dispatchReaderPageTurnCommand(command: ReaderBridgeCommand) {")
			.substringBefore("\n\tprivate fun showShellCover()")

		assertContains(
			webViewHostText,
			"private var shellCoverReturnAvailable: Boolean = false",
			message = "The native shell cover must be a real virtual reader page, not only a locator-derived condition from Compose."
		)
		assertContains(
			pageTurn,
			"shellCoverReturnAvailable = true",
			message = "Next from the native cover must arm a one-step return before location events or recomposition catch up."
		)
		assertContains(
			pageTurn,
			"command == ReaderBridgeCommand.PreviousPage && (shellCoverReturnAvailable || canReturnToShellCover)",
			message = "Previous from the first readable page must return to the native cover using shell-owned state before delegating to Foliate."
		)
		assertContains(
			pageTurn,
			"shellCoverReturnAvailable = false",
			message = "Leaving the first readable page must clear the shell-cover return affordance."
		)
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
		assertContains(bridgeText, "this.reflowableWholeBookPagePosition(detail) || this.reflowableLocationPagePosition(detail)")
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
		assertContains(
			bridgeText,
			"return this.reflowableWholeBookPagePosition(detail) || this.reflowableLocationPagePosition(detail) || this.readerPageListPosition(detail) || this.reflowableSectionPagePosition()",
			message = "Reflowable EPUB labels must prefer the stable whole-book model before location, page-list, or section fallbacks."
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
		assertContains(bridgeText, "Math.abs(candidatePageIndex - currentPageIndex) <= 1")
		assertContains(bridgeText, "const direction = candidatePageIndex > currentPageIndex ? 1 : -1")
		assertContains(bridgeText, "pageIndex: Math.min(pageCount - 1, Math.max(0, currentPageIndex + direction))")
		assertContains(
			bridgeText,
			"this.scheduleCommittedRelocation(this.lastRelocateDetail, 'go-to')",
			message = "Link and explicit href navigation must mark the next relocation as a real jump, not as a passive page-turn aftershock."
		)
		assertContains(
			bridgeText,
			"this.scheduleCommittedRelocation(this.lastRelocateDetail, 'progress-seek')",
			message = "Progress rail navigation must mark the next relocation as a real jump, not as a passive page-turn aftershock."
		)
	}

}
