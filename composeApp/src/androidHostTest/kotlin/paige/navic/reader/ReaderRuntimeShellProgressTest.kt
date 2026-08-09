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
		val helperText = readerAssetRoot().resolve("navic-reader-helpers.js").readText()
		val suppressionBody = bridgeText
			.substringAfter("suppressLoadedEmbeddedCoverPage(doc, index) {")
			.substringBefore("\n  attachContentDocumentBehaviors")

		assertContains(helperText, "readerCoverTokenPattern")
		assertContains(helperText, "readerEmbeddedCoverImage = (doc, section = null, index = 0)")
		assertContains(helperText, "readerCoverTokenPattern.test(coverTokenText)")
		assertContains(
			helperText,
			"if (!readerCoverTokenPattern.test(coverTokenText)) return null",
			message = "Embedded-cover suppression must not hide normal leading illustrations or title-page art without an explicit cover signal."
		)
		assertContains(suppressionBody, "const section = this.view?.book?.sections?.[sectionIndex]")
		assertContains(suppressionBody, "suppressReaderEmbeddedCoverPage(doc, section, sectionIndex)")
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
		assertContains(bridgeText, "async function scrollViewport(direction)")
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
		assertContains(bridgeText, "async function goToProgress(progress, reason = 'progress-seek')")
		assertContains(bridgeText, "case 'diagnosticLocationSnapshot'")
		assertContains(bridgeText, "postCurrentLocationSnapshot(command.reason || 'diagnostic-snapshot'")
		assertContains(bridgeText, "this.view?.goToFraction")
		assertContains(bridgeText, "progress-seek")
		assertContains(bridgeText, "case 'goToChapterProgress'")
		assertContains(bridgeText, "async function goToChapterProgress(")
		assertContains(bridgeText, "reason = 'chapter-progress-seek'")
		assertContains(bridgeText, "chapter-progress-seek")
		assertContains(bridgeText, "this.view.renderer.goTo({ index, anchor: targetAnchor })")
		assertFalse(
			bridgeText.contains("this.view.goTo({ href: targetHref, fraction })"),
			"Foliate treats an object with fraction as a whole-book fraction target; chapter rail seeks must resolve href to a section index and use a section-local anchor."
		)
		assertContains(readerScreenText, "coordinator.dispatch { navigateTo(")
		assertContains(readerScreenText, "coordinator.dispatch { navigateToChapterPage(pageIndex) }")
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
	fun androidReaderPageNumberUsesConfiguredReaderFontBeforeVisiblePublisherFont() {
		val bridgeText = readerBridgeText()
		val pageNumberFont = bridgeText
			.substringAfter("function readerPageNumberFontFamily(settings = this.readerSettings) {")
			.substringBefore("\nfunction readerPageNumberVisibleContentFontFamily")

		assertContains(pageNumberFont, "const configured = readerEffectiveFontFamily(settings)")
		assertContains(pageNumberFont, "const selected = String(settings?.fontFamily || '').trim()")
		assertContains(pageNumberFont, "selected && selected !== 'inherit'")
		assertTrue(
			pageNumberFont.indexOf("if (configured) return configured") <
				pageNumberFont.indexOf("const visibleContentFont = readerPageNumberVisibleContentFontFamily.call(this)"),
			"When the reader has a concrete configured ebook font, the organic page number must use it before sampling an arbitrary visible publisher font."
		)
		assertTrue(
			pageNumberFont.indexOf("if (selected && selected !== 'inherit') return selected") <
				pageNumberFont.indexOf("const visibleContentFont = readerPageNumberVisibleContentFontFamily.call(this)"),
			"Selected Dys/Navic font stacks must still win when readerEffectiveFontFamily returns empty for publisher mode."
		)
	}

	@Test
	fun androidReaderDiagnosticLocationSnapshotBypassesDuplicateSuppression() {
		val bridgeText = readerBridgeText()

		assertContains(bridgeText, "forceDuplicatePost: true")
		assertContains(bridgeText, "postCurrentLocationSnapshot(reason = 'snapshot', options = {})")
		assertContains(bridgeText, "postLocationChanged(detail, reason, options)")
		assertContains(bridgeText, "postLocationChanged(detail, reason = 'relocate', options = {})")
		assertContains(bridgeText, "locationKey === this.lastPostedLocationKey && !options.forceDuplicatePost")
		assertContains(bridgeText, "message,")
	}

	@Test
	fun androidReaderLocationAcknowledgementIsSessionBoundAndConsumedOnlyAfterDelivery() {
		val root = readerAssetRoot()
		val runtime = root.resolve("navic-reader.js").readText()
		val location = root.resolve("navic-reader-location.js").readText()
		val core = root.resolve("navic-reader-bridge-core.js").readText()
		val postLocation = location
			.substringAfter("function postLocationChanged(")
			.substringBefore("function postCurrentVisibleTextRange(")

		assertContains(runtime, "foliateSessionId = ''")
		assertContains(runtime, "const normalizedFoliateSessionId")
		assertContains(runtime, "this.foliateSessionId = normalizedFoliateSessionId")
		assertContains(location, "foliateSessionId: this.foliateSessionId")
		assertContains(location, "pageTurnSettleToken: settlement?.token")
		assertContains(location, "pageTurnSettleSessionId: settlement?.foliateSessionId")
		assertContains(location, "pageTurnSettleRasterGeneration: settlement?.rasterGeneration")
		assertContains(location, "pageTurnSettleTextureGeneration: settlement?.textureGeneration")
		assertContains(postLocation, "const delivered = post(message)")
		assertContains(postLocation, "skipped: 'bridge-delivery-failed'")
		assertTrue(
			postLocation.indexOf("if (!delivered)") < postLocation.indexOf("this.lastPostedLocationKey = locationKey")
		)
		assertTrue(
			postLocation.indexOf("this.lastPostedLocationKey = locationKey") <
				postLocation.indexOf("this.consumeNativePageTurnSettlement(settlement.token)")
		)
		assertFalse(postLocation.contains("readerTrace('location:post', { reason, message })"))
		assertContains(core, "message?.foliateSessionId || ''")
		assertContains(core, "message?.pageTurnSettleToken || ''")
		assertContains(core, "message?.pageTurnSettleSessionId || ''")
		assertContains(core, "message?.pageTurnSettleRasterGeneration")
		assertContains(core, "message?.pageTurnSettleTextureGeneration")
	}

	@Test
	fun androidReaderDiagnosticPullUpExercisesScrolledEdgeBridgePath() {
		val bridgeText = readerBridgeText()
		val helperText = repoFile("tools/reader-harness/src/adb-webview-eval.mjs").readText()

		assertContains(bridgeText, "case 'diagnosticScrolledEdgePullUp':")
		assertContains(bridgeText, "diagnosticScrolledEdgePullUp()")
		assertContains(bridgeText, "turnScrolledEdgePage(-(ScrollEdgeTurnSwipeThreshold + 10))")
		assertContains(helperText, "type: 'diagnosticScrolledEdgePullUp'")
		assertContains(helperText, "diagnosticScrolledEdgePullUp did not post pullUp")
		assertContains(helperText, "Reader bridge event: pullUp")
	}

	@Test
	fun androidReaderChapterRailSeekCommitsWithControlledReasonInsteadOfPassiveClamp() {
		val bridgeText = readerBridgeText()
		val chapterProgressSeek = bridgeText
			.substringAfter("async function goToChapterProgress(")
			.substringBefore("\nfunction nextPage()")
		val onRelocate = bridgeText.substringAfter("onRelocate(detail) {")
			.substringBefore("\n  cancelPendingCommittedRelocation")
		val scheduleCommittedRelocation = bridgeText.substringAfter("scheduleCommittedRelocation(detail, reason = 'relocate-committed') {")
			.substringBefore("\n  suppressLoadedCoverDocument")

		assertContains(bridgeText, "reason = 'chapter-progress-seek'")
		assertContains(bridgeText, "beginControlledRelocation(reason)")
		assertContains(bridgeText, "consumeControlledRelocationReason('relocate-committed')")
		assertContains(bridgeText, "scheduleControlledRelocationFallback(reason)")
		assertContains(bridgeText, "readerRelocationReasonIsExplicit(reason)")
		assertContains(bridgeText, "relocateSequence += 1")
		assertTrue(
			chapterProgressSeek.indexOf("beginControlledRelocation(reason)") <
				chapterProgressSeek.indexOf("this.view.renderer.goTo({ index, anchor: targetAnchor })"),
			"Chapter rail seeks must arm their controlled relocation reason before Foliate emits relocate events."
		)
		assertContains(
			onRelocate,
			"this.scheduleCommittedRelocation(detail, this.consumeControlledRelocationReason('relocate-committed'))"
		)
		assertFalse(
			onRelocate.contains("this.scheduleCommittedRelocation(detail)\n"),
			"Controlled navigation relocations must not be scheduled as generic relocate-committed events; that path activates passive one-page clamping and prevents rail endpoint seeks."
		)
		assertFalse(
			chapterProgressSeek.contains("this.scheduleCommittedRelocation(this.lastRelocateDetail, 'chapter-progress-seek')"),
			"Chapter rail seeks must not immediately commit the previous lastRelocateDetail; the controlled reason has to survive until the next real Foliate relocate detail."
		)
		assertContains(scheduleCommittedRelocation, "const previousReason = this.pendingRelocateReason")
		assertContains(scheduleCommittedRelocation, "this.pendingRelocateReason = preserveExplicitReason ? previousReason : reason")
		assertContains(scheduleCommittedRelocation, "readerRelocationReasonIsExplicit(previousReason)")
		assertContains(scheduleCommittedRelocation, "!readerRelocationReasonIsExplicit(reason)")
	}

	@Test
	fun androidReaderChapterRailSeekPreservesNativeTargetPageThroughBridge() {
		val bridgeText = readerBridgeText()
		val entrypointDispatch = readerAssetRoot().resolve("navic-reader.js").readText()
			.substringAfter("case 'goToChapterProgress':")
			.substringBefore("case 'nextPage':")
		val chapterProgressSeek = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
			.substringAfter("async function goToChapterProgress(")
			.substringBefore("\nfunction nextPage()")

		assertContains(
			entrypointDispatch,
			"command.chapterPageIndex",
			message = "Komikku rail seeks must keep the native chapter page target when crossing the bridge."
		)
		assertContains(
			entrypointDispatch,
			"command.chapterPageCount",
			message = "Komikku rail seeks must keep the native chapter page count when crossing the bridge."
		)
		assertContains(
			chapterProgressSeek,
			"targetPageIndex",
			message = "Runtime chapter seeks must use the exact native rail target, not only a lossy progress fraction."
		)
		assertContains(chapterProgressSeek, "targetPageCount")
	}

	@Test
	fun androidReaderShowsPaginationProfilingStatusInKomikkuOverlay() {
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val profileBadgeText = readerCommonUiFile("ReaderPaginationProfileBadge.kt").readText()
		val controllerStateText = readerCommonFile("ReaderControllerState.kt").readText()
		val bridgeText = readerCommonFile("ReaderBridgeProtocol.kt").readText()

		assertContains(bridgeText, "PaginationProfileStatusChanged")
		assertContains(controllerStateText, "paginationProfile: ReaderPaginationProfileStatus")
		assertContains(readerRootText, "KomikkuPaginationProfileStatusBadge(")
		assertContains(readerRootText, "controllerState.paginationProfile")
		assertContains(profileBadgeText, "LinearProgressIndicator(")
		assertContains(profileBadgeText, "profile.label")
	}

	@Test
	fun androidReaderBridgePortsAnxStyleScrolledEdgePageTurns() {
		val bridgeText = readerBridgeText()
		val scrolledEdgeGestureText = bridgeText
			.substringAfter("function attachScrolledEdgeTurnGestures")
			.substringBefore("\nfunction effectiveReaderDirection")

		assertContains(bridgeText, "ScrollEdgeTurnSwipeThreshold")
		assertContains(bridgeText, "attachScrolledEdgeTurnGestures")
		assertContains(bridgeText, "doc.addEventListener('touchstart'")
		assertContains(bridgeText, "doc.addEventListener('touchmove'")
		assertContains(bridgeText, "doc.addEventListener('touchend'")
		assertContains(bridgeText, "renderer.scrolled")
		assertContains(bridgeText, "renderer.viewSize - renderer.end")
		assertContains(bridgeText, "renderer.start <= ScrollEdgeTurnSlop")
		assertContains(bridgeText, "page-turn:edge-swipe")
		assertContains(bridgeText, "post({ type: 'pullUp', source: 'scrolled-edge-swipe' })")
		assertEquals(
			3,
			Regex("""doc\.addEventListener\('touch(?:start|move|end)'[\s\S]*?\}, \{ capture: true, passive: true \}\)""")
				.findAll(scrolledEdgeGestureText)
				.count(),
			"Scrolled-edge turn gestures must listen in capture phase so native tap-zone touch suppression cannot starve real Android swipes."
		)
	}

	@Test
	fun commonReaderSurfaceExposesPageTurnControlsOutsideChapterNavigator() {
		val readerScreenText = readerScreenFile().readText()
		val appBarsText = readerCommonUiFile("ReaderAppBars.kt").readText()
		val chapterNavigatorText = readerCommonUiFile("ReaderChapterNavigator.kt").readText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()

		assertContains(readerScreenText, "ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)")
		assertContains(readerScreenText, "ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)")
		assertContains(nativeFrameHostText, "KomikkuNavigationRegion.PREV")
		assertContains(nativeFrameHostText, "KomikkuNavigationRegion.NEXT")
		assertContains(appBarsText, "onPreviousChapter: () -> Unit")
		assertContains(appBarsText, "onNextChapter: () -> Unit")
		assertFalse(
			appBarsText.contains("onPreviousPage: () -> Unit") ||
				appBarsText.contains("onNextPage: () -> Unit"),
			"Page turns belong to the native tap/drag surface; the Komikku chapter navigator must not own page-turn callbacks."
		)
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
	fun shellCoverPageTurnsWaitForInteractivePagePreparation() {
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()

		assertContains(
			readerRootText,
			"pageTurnAllowed = pagePreparationState.interactiveReady &&"
		)
		assertContains(
			readerRootText,
			"controllerState.paginationProfile.status != \"measuring\""
		)
		assertContains(readerRootText, "?.let(onViewerAction)")
	}

	@Test
	fun shellCoverShowsPaginationProgressBeforeEnteringThePublication() {
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val overlay = readerRootText.substringAfter("private fun KomikkuComposeOverlay(")
		val paginationBadge = overlay.indexOf("KomikkuPaginationProfileStatusBadge(")
		val contentOnlyOverlays = overlay.indexOf("if (!controllerState.shellCoverVisible)")

		assertTrue(paginationBadge >= 0)
		assertTrue(contentOnlyOverlays > paginationBadge)
		assertContains(
			readerRootText,
			"if (controllerState.paginationProfile.status != \"measuring\")"
		)
	}

	@Test
	fun readerChromeIsImmersiveAndDrivenByNativeReaderSurfaceTaps() {
		val runtimeText = readerBridgeText()
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
		assertContains(readerRootText, "readerShellCoverViewerActionFor(")
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
		val runtimeText = readerBridgeText()
		val surfaceGesture = bridgeText
			.substringAfter("attachSurfaceTapGesture(element) {")
			.substringBefore("\nfunction readerTapZoneActionForPoint")

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
		val runtimeText = readerRuntimeImplementationText()
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
	fun importedReadableContentReceivesProvisionalDownBeforeCurlClaim() {
		val webViewHostText = readerEngineWebViewHostFile().readText()
		val bridgeText = readerBridgeText()
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val intercept = nativeFrameHostText
			.substringAfter(
				"override fun onInterceptTouchEvent(event: MotionEvent): Boolean"
			)
			.substringBefore(
				"override fun onTouchEvent(event: MotionEvent): Boolean"
			)
		val apply = nativeFrameHostText
			.substringAfter("private fun applyPointerRoute(")
			.substringBefore("private fun dispatchContentCancel(")
		val content = apply
			.substringAfter("ReaderPagePointerRoute.Content -> {")
			.substringBefore(
				"is ReaderPagePointerRoute.ContentTerminal -> {"
			)

		assertContains(
			intercept,
			"physicalDispatchMode == " +
				"ReaderPagePhysicalDispatchMode.PlayLikeCurl"
		)
		assertContains(intercept, "return false")
		assertContains(content, "MotionEvent.obtain(event)")
		assertContains(content, "viewerContentContainer.dispatchTouchEvent(event)")
		assertFalse(content.contains("super.dispatchTouchEvent(event)"))
		assertContains(
			content,
			"playLikeCurlGestureDetector.onTouchEvent(event)"
		)
		assertTrue(
			content.indexOf("viewerContentContainer.dispatchTouchEvent(event)") <
				content.indexOf("playLikeCurlGestureDetector.onTouchEvent(event)"),
			"Foliate must receive the provisional event before curl evaluates ownership."
		)
		assertFalse(content.contains("playLikeCurlController.onPageTouchEvent("))
		assertFalse(
			webViewHostText.contains("event.action =") ||
				webViewHostText.contains("event.setAction("),
			"The WebView host must not rewrite its own pointer stream."
		)
		assertContains(bridgeText, "this.attachReaderTapZoneGesture(this.view)")
		assertContains(bridgeText, "this.attachReaderTapZoneGesture(doc)")
	}

	@Test
	fun visibleReaderChromeBlocksNativeEdgeTapZonesButKeepsCenterMenuToggle() {
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val platformHostText = readerCommonUiFile("ReaderPlatformHosts.kt").readText()
		val readerRootText = readerCommonUiFile("ReaderRoot.kt").readText()
		val legacyTap = nativeFrameHostText
			.substringAfter("private fun onLegacySingleTapConfirmed(")
			.substringBefore("private fun onPlayLikeCurlSingleTapConfirmed(")
		val typedTap = nativeFrameHostText
			.substringAfter("private fun onPlayLikeCurlSingleTapConfirmed(")
			.substringBefore("private fun dispatchLegacySingleTapAction(")

		assertContains(platformHostText, "chromeOverlayVisible: Boolean")
		assertContains(nativeFrameHostText, "chromeOverlayVisible: Boolean")
		assertContains(nativeFrameHostText, "setChromeOverlayVisible(chromeOverlayVisible)")
		assertContains(nativeFrameHostText, "var chromeOverlayVisible: Boolean = false")
		assertContains(readerRootText, "chromeOverlayVisible = controllerState.menuVisible")
		listOf(
			legacyTap to "dispatchLegacySingleTapAction(action)",
			typedTap to "dispatchPlayLikeCurlSingleTapAction("
		).forEach { (body, dispatcher) ->
			assertContains(body, "chromeOverlayVisible &&")
			assertContains(body, "action != KomikkuNavigationRegion.MENU")
			assertTrue(body.indexOf("chromeOverlayVisible &&") < body.indexOf(dispatcher))
		}
		assertContains(typedTap, "gestureId = tap.gestureId")
		assertContains(typedTap, "tap.x")
		assertContains(typedTap, "tap.y")
		assertContains(typedTap, "completeHostDelayedTap(")
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
		assertContains(nativeFrameHostText, "fun setShellCover(coverUrl: String?, title: String, coverBackdropEnabled: Boolean)")
		assertContains(nativeFrameHostText, "readerShellCoverFileFor(")
		assertContains(nativeFrameHostText, "BitmapFactory.decodeFile")
		assertContains(nativeFrameHostText, "coverBackdropEnabled")
		assertFalse(
			nativeFrameHostText.contains("canvas.drawColor(Color.BLACK)"),
			"Native shell cover must not render a flat black stage when the cover-backdrop setting is enabled."
		)
		assertContains(nativeFrameHostText, "drawDiffuseCoverBackdrop")
		assertFalse(
			nativeFrameHostText.contains("drawNativeBackCoverPlane") ||
				nativeFrameHostText.contains("readerDominantCoverColor") ||
				nativeFrameHostText.contains("backCoverRect"),
			"Native shell cover must not draw the rejected back-cover square behind the real cover."
		)
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
		val legacyDispatch = nativeFrameHostText
			.substringAfter("private fun dispatchLegacyReaderPointerEvent(")
			.substringBefore("private fun dispatchPlayLikeCurlPointerEvent(")
		val handleTouch = nativeFrameHostText
			.substringAfter("private fun handleSwipeTouchEvent(event: MotionEvent) {")
			.substringBefore("\n\tprivate fun dispatchHorizontalSwipeViewerAction")
		val actionMove = handleTouch
			.substringAfter("MotionEvent.ACTION_MOVE -> {")
			.substringBefore("MotionEvent.ACTION_UP -> {")
		val shellCoverSwipe = nativeFrameHostText
			.substringAfter("private fun dispatchHorizontalSwipeViewerAction(deltaX: Float, deltaY: Float): Boolean {")
			.substringBefore("\n\tprivate fun nativeTapMovedBeyondSlop")

		assertContains(nativeFrameHostText, "private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop.toFloat()")
		assertContains(handleTouch, "dispatchHorizontalSwipeViewerAction(")
		assertContains(handleTouch, "if (shellCoverVisible)")
		assertContains(handleTouch, "updateReadableViewerDragOffset(dx, dy, ReaderPageDragPreviewPhase.Update)")
		assertContains(handleTouch, "ReaderPageDragPreviewPhase.Release")
		assertContains(handleTouch, "ReaderPageDragPreviewPhase.Cancel")
		assertContains(nativeFrameHostText, "Reader native drag preview")
		assertFalse(
			actionMove.contains("dispatchHorizontalSwipeViewerAction("),
			"Legacy drag preview must not commit navigation before ACTION_UP."
		)
		assertContains(handleTouch, "MotionEvent.ACTION_UP")
		assertContains(shellCoverSwipe, "readerShellCoverSwipeAction(")
		assertContains(shellCoverSwipe, "readableSwipeAction(")
		assertContains(shellCoverSwipe, "touchSlopPx")
		assertContains(shellCoverSwipe, "onAction(KomikkuNavigationRegion.NEXT)")
		assertContains(shellCoverSwipe, "onAction(KomikkuNavigationRegion.PREV)")
		assertContains(legacyDispatch, "val handled = super.dispatchTouchEvent(event)")
		assertContains(legacyDispatch, "handleSwipeTouchEvent(event)")
		assertContains(legacyDispatch, "legacyGestureDetector.onTouchEvent(event)")
		assertContains(nativeFrameHostText, "private fun updateReadableViewerDragOffset(")
		assertContains(nativeFrameHostText, "onReadableDragPreview(deltaX, deltaY, width, height, phase)")
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
	fun importedCurlUsesRouterTouchSlopWhileLegacyPreviewKeepsPagingSlop() {
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val imported = nativeFrameHostText
			.substringAfter("private fun dispatchPlayLikeCurlPointerEvent(")
			.substringBefore("private fun applyPointerRoute(")
		val legacySlop = nativeFrameHostText
			.substringAfter(
				"private fun nativeHorizontalSwipeMovedBeyondSlop("
			)
			.substringBefore("private fun clearLegacyNativeTapState(")
		val handleTouch = nativeFrameHostText
			.substringAfter("private fun handleSwipeTouchEvent(event: MotionEvent) {")
			.substringBefore("private fun dispatchHorizontalSwipeViewerAction(")
		val actionUp = handleTouch
			.substringAfter("MotionEvent.ACTION_UP -> {")
			.substringBefore("MotionEvent.ACTION_CANCEL,")
		val dispatchSlop = nativeFrameHostText
			.substringAfter(
				"private fun readerSwipeThresholdPx(shellCoverVisible: Boolean): Float ="
			)
			.substringBefore("private fun clearLegacyNativeTapState(")
		val typedTap = nativeFrameHostText
			.substringAfter("private fun onPlayLikeCurlSingleTapConfirmed(")
			.substringBefore("private fun dispatchLegacySingleTapAction(")

		assertContains(imported, "scaledTouchSlop")
		assertContains(imported, "ReaderPageHostPointerEvent.Move(")
		assertFalse(imported.contains("scaledPagingTouchSlop"))
		assertContains(
			nativeFrameHostText,
			"private val readablePageDragSlopPx = " +
				"ViewConfiguration.get(context).scaledPagingTouchSlop.toFloat()"
		)
		assertContains(legacySlop, "thresholdPx = readableDragActivationSlopPx()")
		assertContains(legacySlop, "else -> readablePageDragSlopPx")
		assertContains(legacySlop, "readerShellCoverSwipeAction(")
		assertContains(legacySlop, "readableSwipeAction(")
		assertContains(actionUp, "thresholdPx = readableDragActivationSlopPx()")
		assertContains(dispatchSlop, "if (shellCoverVisible)")
		assertContains(dispatchSlop, "touchSlopPx")
		assertContains(dispatchSlop, "else")
		assertContains(dispatchSlop, "readablePageDragSlopPx")
		assertFalse(typedTap.contains("nativeTapCandidate"))
		assertFalse(typedTap.contains("nativeTapLongConfirmed"))
		assertFalse(typedTap.contains("nativeTapCancelledByDrag"))
	}

	@Test
	fun dragCancellationTargetsTheDetectorThatOwnedTheFrozenStream() {
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val contentCancel = nativeFrameHostText
			.substringAfter("private fun dispatchContentCancel(")
			.substringBefore("private fun recycleRetainedContentDown(")
		val legacyCancel = nativeFrameHostText
			.substringAfter("private fun cancelPendingLongTapForDrag(")
			.substringBefore("private fun nativeTapMovedBeyondSlop")
		val handleTouch = nativeFrameHostText
			.substringAfter("private fun handleSwipeTouchEvent(event: MotionEvent) {")
			.substringBefore("private fun dispatchHorizontalSwipeViewerAction(")
		val actionMove = handleTouch
			.substringAfter("MotionEvent.ACTION_MOVE -> {")
			.substringBefore("MotionEvent.ACTION_UP -> {")
		val actionUp = handleTouch
			.substringAfter("MotionEvent.ACTION_UP -> {")
			.substringBefore("MotionEvent.ACTION_CANCEL,")
		val detector = nativeFrameHostText
			.substringAfter("internal class KomikkuGestureDetectorWithLongTap")
			.substringBefore(
				"private class KomikkuReaderNativeNavigationOverlayView"
			)

		assertContains(
			contentCancel,
			"playLikeCurlGestureDetector.cancelForDrag(cancel)"
		)
		assertFalse(contentCancel.contains("legacyGestureDetector"))
		assertContains(legacyCancel, "legacyGestureDetector.cancelForDrag(event)")
		assertFalse(legacyCancel.contains("playLikeCurlGestureDetector"))
		assertTrue(
			actionMove.indexOf("cancelPendingLongTapForDrag(dx, dy, event)") in
				0 until actionMove.indexOf("nativeHorizontalSwipeMovedBeyondSlop("),
			"Move must cancel the frozen long-tap detector before starting drag preview."
		)
		assertTrue(
			actionUp.indexOf("cancelPendingLongTapForDrag(dx, dy, event)") in
				0 until actionUp.indexOf("dispatchHorizontalSwipeViewerAction("),
			"Up must cancel the frozen long-tap detector before dispatching a swipe."
		)
		assertContains(detector, "fun cancelForDrag(event: MotionEvent)")
		assertContains(detector, "MotionEvent.ACTION_CANCEL")
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
	fun shellCoverAndNonCurlRollbackKeepLegacyChildFirstDispatch() {
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val legacy = nativeFrameHostText
			.substringAfter(
				"private fun dispatchLegacyReaderPointerEvent("
			)
			.substringBefore(
				"private fun dispatchPlayLikeCurlPointerEvent("
			)
		val mode = nativeFrameHostText
			.substringAfter(
				"private fun shouldUsePlayLikeCurlPointerRouter()"
			)
			.substringBefore(
				"override fun dispatchTouchEvent(event: MotionEvent): Boolean"
			)

		assertContains(legacy, "val handled = super.dispatchTouchEvent(event)")
		assertContains(legacy, "handleSwipeTouchEvent(event)")
		assertContains(legacy, "legacyGestureDetector.onTouchEvent(event)")
		assertFalse(legacy.contains("pageInputSettlementHostController"))
		assertFalse(legacy.contains("playLikeCurlGestureDetector"))
		assertContains(mode, "pageTurnCanvasEnabled")
		assertContains(mode, "!verticalPageDragPreview")
		assertContains(mode, "!shellCoverVisible")
	}

	@Test
	fun horizontalCurlClaimCancelsFoliateBeforeRendererReceivesDownAndMove() {
		val nativeFrameHostText = readerNativeFrameHostFile().readText()
		val claim = nativeFrameHostText
			.substringAfter("is ReaderPagePointerRoute.ClaimCurl -> {")
			.substringBefore("is ReaderPagePointerRoute.Curl -> {")

		val cancelIndex = claim.indexOf("dispatchContentCancel(event)")
		val downIndex = claim.indexOf("originalDown,")
		val moveIndex = claim.indexOf("dispatchClaimedReaderPageCurlEvent(event)")
		assertTrue(cancelIndex >= 0)
		assertTrue(downIndex > cancelIndex)
		assertTrue(moveIndex > downIndex)
		assertFalse(
			claim.substringAfter("dispatchClaimedReaderPageCurlEvent(event)")
				.contains("super.dispatchTouchEvent(event)"),
			"The claimed move must not also reach Foliate."
		)
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
			.substringAfter("private fun onPlayLikeCurlSingleTapConfirmed(")
			.substringBefore("private fun dispatchLegacySingleTapAction(")
		val viewerContainerBody = nativeFrameHostText
			.substringAfter("private class KomikkuReaderNativeViewerContainer")
			.substringBefore("internal class KomikkuGestureDetectorWithLongTap")

		assertContains(singleTap, "navigator.getAction(")
		assertContains(singleTap, "dispatchPlayLikeCurlSingleTapAction(")
		assertContains(singleTap, "gestureId = tap.gestureId")
		assertContains(singleTap, "tap.x")
		assertContains(singleTap, "tap.y")
		assertContains(singleTap, "takeOldestDelayedTap(")
		assertFalse(singleTap.contains("nativeTapCandidate"))
		assertFalse(singleTap.contains("nativeTapLongConfirmed"))
		assertFalse(singleTap.contains("nativeTapCancelledByDrag"))
		assertTrue(
			singleTap.indexOf("takeDelayedTap(") <
				singleTap.indexOf("navigator.getAction(")
		)
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
				singleTap.indexOf("dispatchPlayLikeCurlSingleTapAction("),
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
		val shellCoverDismissalBranch = shellCoverViewerAction
			.substringAfter(
				"action == ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next) ||"
			)
			.substringBefore("else -> ReaderControllerStep(this)")
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
			"ReaderShellCoverDismissalRequest(",
			message = "Next from the native cover must create a request-bound dismissal and wait for relocation acknowledgement."
		)
		assertContains(
			shellCoverViewerAction,
			"relocationReason = readerShellCoverDismissalReason(it.requestId)",
			message = "The controlled relocation must carry the shell-cover dismissal request identity."
		)
		assertFalse(
			shellCoverDismissalBranch.contains("shellCoverVisible = false"),
			message = "The virtual cover must remain fail-closed until Foliate acknowledges readable content."
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
	fun tokenizedShellCoverDismissalNormalizesPersistedLocatorForFoliateNavigation() {
		val runtime = readerAssetRoot().resolve("navic-reader.js").readText()
		val location = readerAssetRoot().resolve("navic-reader-location.js").readText()
		val pageTurns = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val dispatch = runtime
			.substringAfter("dispatch(command) {")
			.substringBefore("async openPublication")
		val goToLocator = location
			.substringAfter("function goToLocator(locator, reason = 'go-to') {")
			.substringBefore("\n\nexport const NavicReaderLocationMethods")
		val goToProgress = pageTurns
			.substringAfter("async function goToProgress(")
			.substringBefore("\nasync function goToChapterProgress")

		assertContains(dispatch, "return this.goToLocator(command.locator, command.reason || 'go-to')")
		assertContains(goToLocator, "const cfi = String(locator?.cfi || '').trim()")
		assertContains(goToLocator, "if (cfi) return this.goTo(cfi, reason)")
		assertContains(goToLocator, "const href = String(locator?.href || '').trim()")
		assertContains(goToLocator, "const chapterProgress = Number(locator?.chapterProgress)")
		assertContains(goToLocator, "return this.goToChapterProgress(")
		assertContains(goToLocator, "locator.chapterPageIndex")
		assertContains(goToLocator, "locator.chapterPageCount")
		assertContains(goToLocator, "reason")
		assertContains(goToLocator, "const progress = Number(locator?.progress)")
		assertContains(goToLocator, "return this.goToProgress(progress, reason)")
		assertContains(goToLocator, "const pageIndex = Number(locator?.pageIndex)")
		assertContains(goToLocator, "const pageCount = Number(locator?.pageCount)")
		assertContains(goToLocator, "return this.goToProgress(pageIndex / (pageCount - 1), reason)")
		assertContains(goToLocator, "if (href) return this.goTo(href, reason)")
		assertTrue(goToLocator.indexOf("const cfi") < goToLocator.indexOf("const chapterProgress"))
		assertTrue(goToLocator.indexOf("const chapterProgress") < goToLocator.indexOf("const progress"))
		assertTrue(
			goToLocator.indexOf("return this.goToProgress(progress, reason)") <
				goToLocator.indexOf("if (href) return this.goTo(href, reason)")
		)
		assertContains(goToProgress, "progress, reason = 'progress-seek'")
		assertContains(goToProgress, "this.beginControlledRelocation(reason)")
		assertContains(goToProgress, "this.scheduleControlledRelocationFallback(reason)")
	}

	@Test
	fun tokenizedShellCoverDismissalForcesSameLocatorAcknowledgement() {
		val location = readerAssetRoot().resolve("navic-reader-location.js").readText()
		val committedRelocation = location
			.substringAfter("function scheduleCommittedRelocation(detail, reason = 'relocate-committed') {")
			.substringBefore("\nfunction goToLocator")

		assertContains(
			committedRelocation,
			"const forceDuplicatePost = String(pendingReason || '').startsWith('shell-cover-dismiss:')"
		)
		assertContains(
			committedRelocation,
			"this.postLocationChanged(pendingDetail, pendingReason, { forceDuplicatePost })"
		)
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
		val openPublication = readerAssetRoot().resolve("navic-reader.js").readText()
			.substringAfter("async openPublication({")
			.substringBefore("\n  close()")
		val onRelocate = bridgeText
			.substringAfter("onRelocate(detail) {")
			.substringBefore("\n  cancelPendingCommittedRelocation")

		assertContains(bridgeText, "lastRelocateDetail = null")
		assertContains(bridgeText, "postLocationChanged(detail")
		assertContains(bridgeText, "postCurrentLocationSnapshot('initial-resume')")
		assertContains(onRelocate, "this.lastRelocateDetail = detail")
		assertContains(onRelocate, "this.exactPageTurnNavigationInProgress")
		assertContains(onRelocate, "this.scheduleCommittedRelocation(detail, this.consumeControlledRelocationReason('relocate-committed'))")
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
		assertContains(bridgeText, "reflowableSectionPagePosition(detail)")
		assertContains(bridgeText, "readerPaginationProfilePosition(detail, sectionPosition)")
		assertContains(bridgeText, "readerEnsurePaginationProfile(detail, sectionPosition)")
		assertContains(bridgeText, "const sectionIndex = Number(detail?.section?.current ?? detail?.index)")
		assertContains(bridgeText, "spineIndex: sectionIndex")
		assertContains(bridgeText, "readerPaginationRenderFingerprint()")
		assertContains(bridgeText, "readCachedPaginationProfile(fingerprint)")
		assertContains(bridgeText, "paginationProfileIsAuthoritative")
		assertFalse(
			bridgeText.contains("writeCachedPaginationProfile(freshProfile)"),
			"Raw relocation-derived profiles are provisional session state and must not enter persistent cache."
		)
		assertContains(bridgeText, "reflowableWholeBookPagePosition(detail)")
		assertContains(bridgeText, "const renderer = this.view?.renderer")
		assertContains(bridgeText, "if (!renderer) return null")
		assertContains(bridgeText, "if (renderer.scrolled) return this.reflowableScrolledSectionPagePosition()")
		assertContains(bridgeText, "let page")
		assertContains(bridgeText, "let pages")
		assertContains(bridgeText, "page = Number(renderer.page)")
		assertContains(bridgeText, "pages = Number(renderer.pages)")
		assertContains(bridgeText, "reflowable-section-pages:pending")
		assertContains(bridgeText, "reflowablePaginatedTextPageCount(pages)")
		assertContains(bridgeText, "reflowablePaginatedRawTextPageCount(pages)")
		assertContains(bridgeText, "return this.reflowablePaginatedRawTextPageCount(pages)")
		val visualTextPageCount = bridgeText
			.substringAfter("function reflowablePaginatedTextPageCount(pages) {")
			.substringBefore("\nfunction reflowableChapterProgressAnchor")
		assertFalse(
			visualTextPageCount.contains("return Math.max(1, Math.round(pages) - 2)"),
			"Foliate paginated sections expose a trailing blank visual column at the terminal page. Navic must not count that blank column as readable chapter content."
		)
		assertFalse(
			visualTextPageCount.contains("this.reflowablePaginatedRawTextPageCount(pages) - 1"),
			"Foliate already maps readable text pages as pages - 2; subtracting one more collapses short chapters and desynchronizes page turns."
		)
		assertContains(bridgeText, "const pageIndex = Math.min(pageCount - 1, Math.max(0, Math.floor(page - 1)))")
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
			"const pageIndex = Math.min(pageCount - 1, Math.max(0, Math.floor(page - 1)))",
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
		assertContains(bridgeText, "'font-family': fontFamily")
		assertFalse(
			bridgeText.contains("'font-variant-numeric': 'oldstyle-nums tabular-nums'"),
			"The organic page number should look like ebook text, not a separate numeric UI overlay."
		)
		assertContains(bridgeText, "function applyRootReaderFontFaces(settings")
		assertContains(bridgeText, "navic-reader-root-font-face")
		assertContains(bridgeText, "readerFontFaceCss(settings)")
		assertContains(bridgeText, "this.applyRootReaderFontFaces(settings)")
		assertContains(bridgeText, "function readerPageNumberVisibleContentFontFamily()")
		assertContains(bridgeText, "const visibleContentFont = readerPageNumberVisibleContentFontFamily.call(this)")
		assertFalse(
			bridgeText.contains("this.readerPageNumberVisibleContentFontFamily()"),
			"Page-number font probing must not call a module-local helper as a runtime method; that crashes the WebView before EPUB render."
		)
		assertContains(bridgeText, "if (visibleContentFont) return visibleContentFont")
		assertContains(bridgeText, "doc.defaultView?.getComputedStyle?.(element)")
		assertFalse(
			readerScreenText.contains("ReaderPageNumberOverlay("),
			"Page numbers must be drawn in the reader surface, not as a native Material overlay."
		)
	}

	@Test
	fun androidReaderSkipsTrailingBlankFoliateColumnForChapterEndpoints() {
		val bridgeText = readerBridgeText()
		val pageTurnsText = readerAssetRoot().resolve("navic-reader-page-turns.js").readText()
		val chapterProgressSeek = pageTurnsText
			.substringAfter("async function goToChapterProgress(")
			.substringBefore("\nfunction nextPage()")
		val boundaryBody = pageTurnsText
			.substringAfter("function nativeDragPreviewAtSectionBoundary(renderer, direction) {")
			.substringBefore("\nfunction safeNativeDragPreviewAtSectionBoundary(renderer, direction) {")

		assertContains(bridgeText, "reflowablePaginatedRawTextPageCount(pages)")
		assertContains(bridgeText, "reflowablePaginatedVisualTextPageCount(pages)")
		assertContains(bridgeText, "return this.reflowablePaginatedRawTextPageCount(pages)")
		assertContains(bridgeText, "reflowableChapterProgressAnchor(progress, renderer = this.view?.renderer)")
		assertContains(bridgeText, "return clampedProgress")
		assertFalse(
			bridgeText.contains("return (visualTextPageCount - 1) / (rawTextPageCount - 1)"),
			"Chapter endpoint navigation should not be pulled one readable page short now that visual count matches Foliate's text page count."
		)
		assertContains(chapterProgressSeek, "const targetAnchor = this.reflowableChapterProgressAnchor(targetFraction)")
		assertContains(chapterProgressSeek, "anchor: targetAnchor")
		assertFalse(
			chapterProgressSeek.contains("anchor: fraction"),
			"Chapter progress endpoint 1.0 must not ask Foliate to render the terminal blank column."
		)
		assertContains(boundaryBody, "const lastVisualPage = this.reflowableLastVisualRendererPage(renderer)")
		assertContains(boundaryBody, "return page >= lastVisualPage")
		assertFalse(
			boundaryBody.contains("page >= pages - 2"),
			"Native drag/page-turn boundary detection must trigger at the last visual content page, not at Foliate's blank terminal column."
		)
	}

	@Test
	fun androidReaderPostsPageModelDiagnosticsWithLocationChanges() {
		val bridgeText = readerBridgeText()
		val postLocationBody = bridgeText
			.substringAfter("postLocationChanged(detail, reason = 'relocate', options = {}) {")
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
	fun androidReaderDoesNotBackfillChapterProgressFromWholeBookPageModel() {
		val bridgeText = readerBridgeText()
		val chapterPagePositionBody = bridgeText
			.substringAfter("chapterPagePosition(detail, fallback = null) {")
			.substringBefore("\nfunction detailSectionKey")

		assertContains(chapterPagePositionBody, "const resolved = this.view?.isFixedLayout === true")
		assertContains(chapterPagePositionBody, "? pagePosition || fallback")
		assertContains(chapterPagePositionBody, ": pagePosition")
		assertFalse(
			chapterPagePositionBody.contains("const resolved = pagePosition || fallback"),
			"Komikku's chapter rail is chapter-scoped; reflowable EPUB rail state must not fall back to whole-book pagePosition when section page math is missing."
		)
	}

	@Test
	fun androidReaderPublishesScrolledSectionPseudoPagesForKomikkuChapterRail() {
		val bridgeText = readerBridgeText()
		val scrolledSectionBody = bridgeText
			.substringAfter("function reflowableScrolledSectionPagePosition() {")
			.substringBefore("\nfunction reflowableSectionPagePosition")
		val reflowableSectionBody = bridgeText
			.substringAfter("function reflowableSectionPagePosition(detail = this.lastRelocateDetail) {")
			.substringBefore("\nfunction reflowableLocationPagePosition")
		val chapterPagePositionBody = bridgeText
			.substringAfter("chapterPagePosition(detail, fallback = null) {")
			.substringBefore("\nfunction detailSectionKey")

		assertContains(bridgeText, "reflowableScrolledSectionPagePosition()")
		assertContains(scrolledSectionBody, "renderer.scrolled")
		assertContains(scrolledSectionBody, "scrolledRendererViewportSize(renderer)")
		assertContains(scrolledSectionBody, "Math.ceil(viewSize / viewportSize)")
		assertContains(scrolledSectionBody, "viewSize - end <= ScrollEdgeTurnSlop")
		assertContains(scrolledSectionBody, "pageCountSource: 'scrolled-section'")
		assertContains(
			reflowableSectionBody,
			"if (renderer.scrolled) return this.reflowableScrolledSectionPagePosition()",
			message = "Scrolled EPUB chapters still need chapter-local pseudo-pages so the Komikku vertical rail has a middle progress slider."
		)
		assertContains(
			chapterPagePositionBody,
			": this.reflowableSectionPagePosition(detail)",
			message = "The native rail must continue to use chapter-local section math, now including scrolled section pseudo-pages."
		)
		assertFalse(
			chapterPagePositionBody.contains("reflowableWholeBookPagePosition") ||
				chapterPagePositionBody.contains("readerPagePosition(detail)"),
			"Komikku chapter rails must never borrow whole-book page math just to make the slider appear."
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
		assertContains(bridgeText, "pageCountSource: 'pagination-profile'")
		assertContains(bridgeText, "paginationProfile = null")
		assertContains(bridgeText, "paginationFingerprint = null")
		assertContains(bridgeText, "observedChapterPageCounts = new Map()")
		assertContains(
			bridgeText,
			"const pagePosition = this.readerPaginationProfilePosition(detail, sectionPosition) ||",
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
			"async goTo(locator, reason = 'go-to')",
			message = "Link and explicit href navigation must carry an explicit controlled-relocation reason, not blend into passive page-turn aftershocks."
		)
		assertContains(
			bridgeText,
			"this.scheduleControlledRelocationFallback(reason)",
			message = "Link and explicit href navigation must mark the next relocation as a real jump, not as a passive page-turn aftershock."
		)
		assertContains(bridgeText, "this.recentPageTurnDirection = null")
		assertContains(
			bridgeText,
			"async function goToProgress(progress, reason = 'progress-seek')",
			message = "Progress rail navigation must retain its explicit relocation reason while controlled shell-cover requests can supply their own token."
		)
		assertContains(bridgeText, "this.scheduleControlledRelocationFallback(reason)")
	}

	@Test
	fun androidReaderRejectsStaleControlledPageTurnRelocationsBeforeConsumingReason() {
		val bridgeText = readerBridgeText()
		val onRelocateBody = bridgeText
			.substringAfter("function onRelocate(detail) {")
			.substringBefore("\n\nfunction cancelPendingCommittedRelocation")
		val startPageTurnBody = bridgeText
			.substringAfter("function startPageTurn(direction) {")
			.substringBefore("\n\nfunction startNextQueuedPageTurn")
		val fallbackBody = bridgeText
			.substringAfter("function scheduleControlledRelocationFallback(reason) {")
			.substringBefore("\n\nfunction onRelocate")
		val staleGuardIndex = onRelocateBody.indexOf("this.pageTurnRelocationDetailIsStale(detail, this.controlledRelocateReason)")
		val sequenceIncrementIndex = onRelocateBody.indexOf("this.relocateSequence += 1", staleGuardIndex)
		val lastRelocateIndex = onRelocateBody.indexOf("this.lastRelocateDetail = detail", staleGuardIndex)
		val consumeReasonIndex = onRelocateBody.indexOf("this.consumeControlledRelocationReason")
		val fallbackStaleGuardIndex = fallbackBody.indexOf("this.pageTurnRelocationDetailIsStale(this.lastRelocateDetail, reason)")
		val fallbackCommitIndex = fallbackBody.indexOf("this.scheduleCommittedRelocation(this.lastRelocateDetail, this.consumeControlledRelocationReason(reason))")

		assertContains(bridgeText, "pageTurnRelocationDetailIsStale(detail, reason)")
		assertContains(bridgeText, "if (!String(reason || '').startsWith('page-turn:')) return false")
		assertContains(bridgeText, "const currentSectionKey = this.detailSectionKey(this.committedRelocateDetail)")
		assertContains(bridgeText, "const candidateSectionKey = this.detailSectionKey(detail)")
		assertContains(bridgeText, "if (!currentSectionKey || currentSectionKey !== candidateSectionKey) return false")
		assertContains(bridgeText, "const currentLocationCurrent = Number(this.committedRelocateDetail?.location?.current)")
		assertContains(bridgeText, "const candidateLocationCurrent = Number(detail?.location?.current)")
		assertContains(bridgeText, "if (direction === 'next' && candidateLocationCurrent > currentLocationCurrent) return false")
		assertContains(bridgeText, "if (direction === 'previous' && candidateLocationCurrent < currentLocationCurrent) return false")
		assertContains(bridgeText, "const candidatePagePosition = this.readerPagePosition(detail)")
		assertContains(bridgeText, "direction === 'next'")
		assertContains(bridgeText, "candidatePageIndex <= currentPageIndex")
		assertContains(bridgeText, "candidatePageIndex >= currentPageIndex")
		assertContains(bridgeText, "relocate:ignored-stale-page-turn")
		assertContains(bridgeText, "scheduleSettledControlledPageTurnRelocation(direction)")
		assertContains(bridgeText, "if (this.controlledRelocateReason !== reason) return false")
		assertContains(bridgeText, "this.scheduleCommittedRelocation(detail, this.consumeControlledRelocationReason(reason))")
		assertContains(
			onRelocateBody,
			"if (this.pageTurnRelocationDetailIsStale(detail, this.controlledRelocateReason)) {",
			message = "Controlled page turns must reject old-section relocation events before they can consume the explicit page-turn reason."
		)
		assertContains(
			onRelocateBody,
			"this.handleDuplicatePageTurnRelocation?.(detail, this.controlledRelocateReason)",
			message = "When Foliate keeps emitting the old locator at a section edge, the existing adjacent-section fallback must run before the stale event is dropped."
		)
		assertTrue(staleGuardIndex >= 0, "The stale page-turn relocation guard must run from onRelocate.")
		assertTrue(
			sequenceIncrementIndex < 0 || staleGuardIndex < sequenceIncrementIndex,
			"Stale page-turn relocations must not increment relocateSequence before fallback can run."
		)
		assertTrue(
			lastRelocateIndex < 0 || staleGuardIndex < lastRelocateIndex,
			"Stale page-turn relocations must not overwrite lastRelocateDetail before being rejected."
		)
		assertTrue(
			consumeReasonIndex < 0 || staleGuardIndex < consumeReasonIndex,
			"Stale page-turn relocations must not consume the controlled page-turn reason before being rejected."
		)
		assertContains(
			startPageTurnBody,
			"this.scheduleSettledControlledPageTurnRelocation(direction)",
			message = "Valid target relocations that arrive during the Foliate turn window must be committed when the turn settles."
		)
		assertTrue(
			fallbackStaleGuardIndex >= 0 && fallbackCommitIndex >= 0 && fallbackStaleGuardIndex < fallbackCommitIndex,
			"Controlled fallback must not fabricate a target-page post from a stale old-section relocation."
		)
	}

	@Test
	fun androidReaderDoesNotApplyExplicitPageTurnTargetAcrossDifferentSections() {
		val bridgeText = readerBridgeText()
		val committedPageTurnBody = bridgeText
			.substringAfter("function committedPageTurnPosition(pagePosition, detail, reason) {")
			.substringBefore("\n\nfunction passiveCommittedRelocationPosition")
		val updateBody = bridgeText
			.substringAfter("function tryUpdateReaderPageNumberLayer(detail = this.lastRelocateDetail, fallback = this.currentPagePosition, reason = '') {")
			.substringBefore("\n\nfunction scheduleReaderPageNumberRefresh")
		val sameSectionIndex = committedPageTurnBody.indexOf("const sameSection = Boolean(currentSectionKey && currentSectionKey === candidateSectionKey)")
		val explicitTargetIndex = committedPageTurnBody.indexOf("Number.isFinite(explicitTargetPageIndex)")

		assertContains(
			updateBody,
			"this.committedPageTurnPosition(candidatePagePosition, detail, reason)",
			message = "Page-turn commit normalization must receive relocation detail so explicit targets cannot be projected onto a different section."
		)
		assertContains(committedPageTurnBody, "const currentSectionKey = this.detailSectionKey(this.committedRelocateDetail)")
		assertContains(committedPageTurnBody, "const candidateSectionKey = this.detailSectionKey(detail)")
		assertContains(committedPageTurnBody, "const sameSection = Boolean(currentSectionKey && currentSectionKey === candidateSectionKey)")
		assertContains(
			committedPageTurnBody,
			"sameSection &&",
			message = "Explicit target page clamping is only valid while the relocation detail is still in the committed section."
		)
		assertTrue(
			sameSectionIndex >= 0 && explicitTargetIndex >= 0 && sameSectionIndex < explicitTargetIndex,
			"The same-section guard must be computed before explicit page-turn target clamping."
		)
	}

	@Test
	fun androidReaderRejectsUnchangedPageTurnLocatorBeforeRendererDerivedPageMath() {
		val bridgeText = readerBridgeText()
		val staleGuardBody = bridgeText
			.substringAfter("function pageTurnRelocationDetailIsStale(detail, reason) {")
			.substringBefore("\n\nfunction scheduleSettledControlledPageTurnRelocation")
		val unchangedLocatorIndex = staleGuardBody.indexOf("const unchangedLocator =")
		val readerPagePositionIndex = staleGuardBody.indexOf("const candidatePagePosition = this.readerPagePosition(detail)")

		assertContains(staleGuardBody, "const currentHref = this.sectionHrefForDetail(this.committedRelocateDetail)")
		assertContains(staleGuardBody, "const candidateHref = this.sectionHrefForDetail(detail)")
		assertContains(staleGuardBody, "const currentCfi = String(this.committedRelocateDetail?.cfi || this.committedRelocateDetail?.rangeCfi || '')")
		assertContains(staleGuardBody, "const candidateCfi = String(detail?.cfi || detail?.rangeCfi || '')")
		assertContains(staleGuardBody, "const unchangedLocator =")
		assertContains(staleGuardBody, "if (unchangedLocator) {")
		assertContains(staleGuardBody, "relocate:ignored-unchanged-page-turn")
		assertContains(staleGuardBody, "const currentChapterPageIndex = Number(this.currentPagePosition?.chapterPageIndex)")
		assertContains(staleGuardBody, "const currentChapterPageCount = Number(this.currentPagePosition?.chapterPageCount)")
		assertContains(staleGuardBody, "const sameSectionBoundaryTurn =")
		assertContains(staleGuardBody, "relocate:ignored-boundary-page-turn")
		assertTrue(
			unchangedLocatorIndex >= 0 && readerPagePositionIndex >= 0 && unchangedLocatorIndex < readerPagePositionIndex,
			"Unchanged page-turn locators must be rejected before renderer-derived page math can fabricate movement from a stale CFI."
		)
	}

}
