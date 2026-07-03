package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.io.File
import paige.navic.ui.screens.reader.KomikkuNavigationRegion
import paige.navic.ui.screens.reader.PagedPublicationReaderViewer
import paige.navic.ui.screens.reader.ReaderViewerMode
import paige.navic.ui.screens.reader.VerticalPagedPublicationReaderViewer
import paige.navic.ui.screens.reader.WebtoonPublicationReaderViewer
import paige.navic.ui.screens.reader.readerShellCoverViewerActionFor
import paige.navic.ui.screens.reader.readerEffectiveNavBarTypeFor
import paige.navic.ui.screens.reader.readerViewerKeyFor
import paige.navic.ui.screens.reader.readerViewerFor

class ReaderViewerTest {
	@Test
	fun readerViewerKeyChangesWhenReadingModeRequiresDifferentViewerImplementation() {
		val paged = ReaderEngineViewState.WebViewPublication(
			publicationUrl = "https://example.test/book.epub",
			title = "Book",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false,
			externalShellCover = true,
			nativeShellCoverUrl = null,
			canReturnToShellCover = true,
			settings = defaultReaderSettings().copy(flowMode = ReaderFlowPaged, paged = true),
			startLocator = null
		)
		val scrolled = paged.copy(
			settings = paged.settings.copy(flowMode = ReaderFlowScrolled, paged = false)
		)

		assertEquals(ReaderViewerMode.Paged, readerViewerKeyFor(paged).mode)
		assertEquals(ReaderViewerMode.Scrolled, readerViewerKeyFor(scrolled).mode)
		assertNotEquals(
			readerViewerKeyFor(paged),
			readerViewerKeyFor(scrolled),
			"Komikku swaps viewer implementations when reading mode changes, so Navic viewer keys must differ too."
		)
	}

	@Test
	fun readerViewerFactoryCreatesConcreteKomikkuViewerVariantsFromReadingMode() {
		val paged = webViewPublication(flowMode = ReaderFlowPaged, paged = true)
		val verticalPaged = webViewPublication(flowMode = ReaderFlowPagedVertical, paged = true)
		val scrolled = webViewPublication(flowMode = ReaderFlowScrolled, paged = false)
		val scrolledGaps = webViewPublication(flowMode = ReaderFlowScrolledGaps, paged = false)

		assertTrue(readerViewerFor(paged) is PagedPublicationReaderViewer)
		assertTrue(readerViewerFor(verticalPaged) is VerticalPagedPublicationReaderViewer)
		assertTrue(readerViewerFor(scrolled) is WebtoonPublicationReaderViewer)
		assertTrue(readerViewerFor(scrolledGaps) is WebtoonPublicationReaderViewer)
	}

	@Test
	fun komikkuVerticalRailIsEffectiveOnlyForVerticalViewerModes() {
		assertEquals(
			ReaderNavBarTypeBottom,
			readerEffectiveNavBarTypeFor(
				defaultReaderSettings().copy(
					flowMode = ReaderFlowPaged,
					paged = true,
					navBarType = ReaderNavBarTypeVerticalRight
				)
			),
			"Komikku keeps paged readers on the bottom navigator; vertical seekbars belong to vertical viewers."
		)
		assertEquals(
			ReaderNavBarTypeVerticalRight,
			readerEffectiveNavBarTypeFor(
				defaultReaderSettings().copy(
					flowMode = ReaderFlowScrolled,
					paged = false,
					navBarType = ReaderNavBarTypeVerticalRight
				)
			)
		)
		assertEquals(
			ReaderNavBarTypeVerticalLeft,
			readerEffectiveNavBarTypeFor(
				defaultReaderSettings().copy(
					flowMode = ReaderFlowPagedVertical,
					paged = true,
					navBarType = ReaderNavBarTypeVerticalLeft
				)
			)
		)
		assertEquals(
			ReaderNavBarTypeBottom,
			readerEffectiveNavBarTypeFor(
				defaultReaderSettings().copy(
					flowMode = ReaderFlowScrolled,
					paged = false,
					navBarType = ReaderNavBarTypeBottom
				)
			)
		)
	}

	@Test
	fun publicationViewerExposesShellCoverMetadataWithoutScreenInspectingEngineViewState() {
		val viewState = webViewPublication(flowMode = ReaderFlowPaged, paged = true).copy(
			title = "The Hobbit",
			nativeShellCoverUrl = "https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg"
		)
		val viewer = readerViewerFor(viewState)

		assertEquals("https://appassets.androidplatform.net/reader-cache/book-1/cover.jpg", viewer.shellCoverUrl)
		assertEquals("The Hobbit", viewer.shellCoverTitle)
	}

	@Test
	fun composeShellDoesNotOwnReaderPageNumberOverlay() {
		val screenSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").readText()
		val viewerSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderViewer.kt").readText()

		assertFalse(
			screenSource.contains("KomikkuReaderPageIndicator"),
			"Reader page numbers must stay on the book surface. The Compose shell must not keep a " +
				"native/mobile page-number overlay implementation that can be re-enabled later."
		)
		assertFalse(
			viewerSource.contains("shouldShowNativeReaderPageIndicator"),
			"Reader page numbers must stay on the book surface. The viewer model must not keep " +
			"a native page-indicator policy hook for the Compose shell."
		)
	}

	@Test
	fun komikkuBottomBarActionsAreNotDeadButtons() {
		val screenSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").readText()

		assertFalse(
			screenSource.contains("IconButton(onClick = {}) {\n\t\t\t\tIcon(Icons.Outlined.List"),
			"Komikku bottom-bar contents action must route through the controller, not a no-op button."
		)
		assertFalse(
			screenSource.contains("IconButton(onClick = {}) {\n\t\t\t\tIcon(Icons.Outlined.Book"),
			"Komikku bottom-bar reading-mode action must route through the controller, not a no-op button."
		)
	}

	@Test
	fun whispersyncPlayerSurfaceIsKomikkuOwnedReaderChrome() {
		val rootSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt").readText()
		val screenSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").readText()
		val whispersyncChromeSource =
			File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderWhispersyncStatusBadge.kt").readText()

		assertTrue(
			rootSource.contains("KomikkuWhispersyncPlaybackControl") &&
				rootSource.contains("onOpenPlayer = onWhispersyncPlayer") &&
				whispersyncChromeSource.contains("Icons.Outlined.Headset"),
			"The reader chrome must expose a Komikku-owned headset overlay when Whispersync is available."
		)
		assertTrue(
			rootSource.contains("ReaderControllerDialog.WhispersyncPlayer") &&
				rootSource.contains("KomikkuWhispersyncPlayerDialog"),
			"The Whispersync player must be a controller-owned reader dialog, not a WebView-owned popup."
		)
		assertTrue(
			screenSource.contains("coordinator.openWhispersyncPlayerDialog()"),
			"The reader screen must route the audiobook action through the controller."
		)
		assertFalse(
			rootSource.contains("BinderyAudiobookPlayerScreen"),
			"The reader must not embed the full Bindery audiobook screen inside the Komikku shell."
		)
	}

	@Test
	fun whispersyncPlaybackIsBoundToReaderLifecycleAndVisibleTextRange() {
		val screenSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").readText()
		val applyBackStep = screenSource
			.substringAfter("fun applyReaderBackStep(step: ReaderCoordinatorBackStep)")
			.substringBefore("\n\tfun applyReadaloudEngineCommand")

		assertTrue(
			screenSource.contains("DisposableEffect(reader.bookId, reader.resourceHref, reader.publicationUrl)") &&
				screenSource.contains("readerWhispersyncShouldPausePlaybackOnReaderExit") &&
				screenSource.contains("playbackStartedFromReader = whispersyncPlaybackStartedFromReader") &&
				screenSource.contains("audiobookPlaybackManager.dispatch(ReaderReadaloudPlaybackCommand.Pause)"),
			"Leaving the reader must pause active or reader-started Whispersync audio instead of letting it continue detached."
		)
		assertTrue(
			screenSource.contains("fun pauseWhispersyncAudiobookOnReaderExit(reason: String)") &&
				applyBackStep.contains("pauseWhispersyncAudiobookOnReaderExit(\"back-to-shell-cover\")") &&
				applyBackStep.contains("pauseWhispersyncAudiobookOnReaderExit(\"app-navigation\")") &&
				applyBackStep.indexOf("pauseWhispersyncAudiobookOnReaderExit(\"app-navigation\")") <
				applyBackStep.indexOf("backStack.performNavicBack()"),
			"Reader Back must pause active Whispersync playback before returning to the native cover or leaving through app navigation; dispose-only pause is too late."
		)
		assertTrue(
			screenSource.contains("readerWhispersyncPlaybackCommandsForUserRequest") &&
				screenSource.contains("session = coordinator.controller.state.whispersync") &&
				screenSource.contains("ReaderReadaloudPlaybackCommand.Play -> whispersyncPlaybackStartedFromReader = true") &&
				screenSource.contains("Whispersync play preseek") &&
				screenSource.contains("visibleTextRange=") &&
				screenSource.contains("audiobookPlaybackManager.dispatch(playbackCommand)"),
			"Whispersync Play must pass through the visible-text-range policy before dispatching audiobook commands."
		)
		assertFalse(
			screenSource.contains("onWhispersyncPlaybackCommand = { command ->\n\t\t\taudiobookPlaybackManager.dispatch(command)"),
			"Whispersync Play must not bypass the current EPUB visible range by dispatching raw commands."
		)
	}

	@Test
	fun shortTapsRemainNativeUiOnlyAndCannotBecomeContentBridgeCommands() {
		val bridgeSource = File("src/commonMain/kotlin/paige/navic/reader/ReaderBridgeProtocol.kt").readText()
		val engineSource = File("src/commonMain/kotlin/paige/navic/reader/ReaderEngine.kt").readText()
		val viewerActionSource = File("src/commonMain/kotlin/paige/navic/reader/ReaderViewerAction.kt").readText()
		val controllerSource = File("src/commonMain/kotlin/paige/navic/reader/ReaderController.kt").readText()
		val nativeFrameSource =
			File("src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt")
				.readText()
		val runtimeSource = File("src/androidMain/assets/reader/navic-reader.js").readText() +
			File("src/androidMain/assets/reader/navic-reader-content-interactions.js").readText()

		val sources = listOf(bridgeSource, engineSource, viewerActionSource, controllerSource, nativeFrameSource, runtimeSource)
		assertTrue(
			sources.any { it.contains("ContentLongPressAt") } &&
				runtimeSource.contains("contentLongPressAt"),
			"EPUB/content interaction must stay routed through the explicit long-press bridge."
		)
		sources.forEach { source ->
			assertFalse(
				source.contains("ContentTapAt") || source.contains("contentTapAt"),
				"Short taps belong to Komikku-native reader UI; do not add a generic content-tap bridge."
			)
		}
		assertTrue(
			runtimeSource.contains("readerCenterTap"),
			"Center short taps may only surface native reader chrome."
		)
		val nativeSingleTapHandler = nativeFrameSource
			.substringAfter("override fun onSingleTapConfirmed(event: MotionEvent): Boolean {")
			.substringBefore("override fun onLongTapConfirmed(event: MotionEvent)")
		assertTrue(
			nativeSingleTapHandler.contains("dispatchSingleTapAction(action)"),
			"Native short taps must dispatch only Komikku navigation/UI regions."
		)
		assertFalse(
			nativeSingleTapHandler.contains("onContentLongPress") ||
				nativeSingleTapHandler.contains("ContentLongPressAt"),
			"Native short taps must never enter the ebook/content interaction bridge."
		)
		val nativeLongTapHandler = nativeFrameSource
			.substringAfter("override fun onLongTapConfirmed(event: MotionEvent) {")
			.substringBefore("\n\t\t\t}\n\t\t}")
		assertTrue(
			nativeLongTapHandler.contains("onContentLongPress(event.x, event.y, width, height)"),
			"Explicit long press is the only native gesture allowed to enter ebook/content interaction."
		)
		assertFalse(
			runtimeSource.contains("source: 'media-touch'") ||
				runtimeSource.contains("source: 'link-touch'"),
			"Short touches must not emit ebook content action claims; ebook interaction is long-press only."
		)
		val nativeCenterShortTapHandler = runtimeSource
			.substringAfter("if (action === KomikkuNavigationRegionMenu) {")
			.substringBefore("const command = this.readerTapZoneCommand(action)")
		assertTrue(
			nativeCenterShortTapHandler.contains("post({ type: 'readerCenterTap' })"),
			"Center short taps must post only the native reader chrome event."
		)
		assertFalse(
			nativeCenterShortTapHandler.contains("readerTextOffsetAtDocumentPoint") ||
				nativeCenterShortTapHandler.contains("postWhispersyncTextLongPressAt") ||
				nativeCenterShortTapHandler.contains("handleNativeTapZoneContentLongPress"),
			"Short taps must not query text offsets or route Whispersync sentence selection through the content bridge."
		)
		val linkNavigationShortClickHandler = runtimeSource
			.substringAfter("function attachLinkNavigation")
			.substringAfter("doc.addEventListener('click', async event => {")
			.substringBefore("}, { capture: true })")
		assertFalse(
			linkNavigationShortClickHandler.contains("activateReaderLinkFromEvent"),
			"Short link clicks must not navigate ebook content directly; use explicit long press."
		)
		val imageShortTouchHandler = runtimeSource
			.substringAfter("function attachSepiaImageOverlayToggle")
			.substringAfter("doc.addEventListener('touchend', event => {")
			.substringBefore("doc.addEventListener('touchcancel'")
		val imageShortClickHandler = runtimeSource
			.substringAfter("function attachSepiaImageOverlayToggle")
			.substringAfter("doc.addEventListener('click', event => {")
			.substringBefore("}, { capture: true, passive: false })")
		assertFalse(
			imageShortTouchHandler.contains("toggleSepiaImageOverlayFromEvent") ||
				imageShortClickHandler.contains("toggleSepiaImageOverlayFromEvent"),
			"Short image taps must not toggle ebook image state directly; use explicit long press."
		)
	}

	@Test
	fun readerContentsDialogSurfacesSavedMarksThroughControllerRoutes() {
		val contentsSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderContentsDialog.kt").readText()
		val rootSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderRoot.kt").readText()
		val screenSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").readText()

		assertTrue(
			contentsSource.contains("ReaderContentsTab.Bookmarks"),
			"Reader contents must expose saved bookmarks, not only table-of-contents entries."
		)
		assertTrue(
			contentsSource.contains("ReaderContentsTab.Notes"),
			"Reader contents must expose saved highlights and notes so annotations are discoverable later."
		)
		assertTrue(
			contentsSource.contains("onNavigateToBookmark(bookmark)") &&
				contentsSource.contains("onNavigateToAnnotation(annotation)"),
			"Saved mark rows must route through explicit controller callbacks."
		)
		assertTrue(
			rootSource.contains("controllerState.bookmarks.bookmarksForBook(bookId)") &&
				rootSource.contains("controllerState.annotations.annotationsForBook(bookId)"),
			"ReaderRoot must filter saved marks to the current book before showing them in the contents sheet."
		)
		assertTrue(
			screenSource.contains("coordinator.navigateToBookmark(bookmark)") &&
				screenSource.contains("coordinator.navigateToAnnotation(annotation)"),
			"Saved mark navigation must be controller-owned, not a WebView-only side effect."
		)
	}

	@Test
	fun komikkuReaderChromeDoesNotKeepDeadIconButtons() {
		val screenSource = File("src/commonMain/kotlin/paige/navic/ui/screens/reader/ReaderScreen.kt").readText()

		assertFalse(
			screenSource.contains("IconButton(onClick = {})"),
			"Komikku reader chrome actions must route through controller/navigation callbacks, not no-op buttons."
		)
	}

	@Test
	fun readerViewerOwnsKomikkuNavigationRegionMapping() {
		val viewer = readerViewerFor(
			webViewPublication(flowMode = ReaderFlowPaged, paged = true)
		)

		assertEquals(ReaderViewerAction.Menu, viewer.viewerActionFor(KomikkuNavigationRegion.MENU))
		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
			viewer.viewerActionFor(KomikkuNavigationRegion.NEXT)
		)
		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous),
			viewer.viewerActionFor(KomikkuNavigationRegion.PREV)
		)
		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous),
			viewer.viewerActionFor(KomikkuNavigationRegion.LEFT)
		)
		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
			viewer.viewerActionFor(KomikkuNavigationRegion.RIGHT)
		)
	}

	@Test
	fun readerViewerMoveToPageReturnsControllerNavigationActionLikeKomikkuViewerContract() {
		val locator = ReaderLocator(
			href = "EPUB/Text/chapter-02.xhtml",
			cfi = "epubcfi(/6/8!/4/2:16)",
			progress = 0.34
		)
		val viewer = readerViewerFor(
			webViewPublication(flowMode = ReaderFlowPaged, paged = true)
		)

		assertEquals(
			ReaderViewerAction.NavigateTo(locator),
			viewer.moveToPage(locator),
			"Komikku Viewer.moveToPage(page) maps to Navic's locator navigation action; the engine should not own this UI decision."
		)
	}

	@Test
	fun pagedViewerMapsPhysicalRegionsThroughReaderDirectionLikeKomikkuPagerVariants() {
		val viewer = readerViewerFor(
			webViewPublication(flowMode = ReaderFlowPaged, paged = true).copy(
				settings = defaultReaderSettings().copy(
					flowMode = ReaderFlowPaged,
					paged = true,
					direction = ReaderDirectionRtl
				)
			)
		)

		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
			viewer.viewerActionFor(KomikkuNavigationRegion.LEFT)
		)
		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous),
			viewer.viewerActionFor(KomikkuNavigationRegion.RIGHT)
		)
	}

	@Test
	fun shellCoverUsesPhysicalSideZonesWithoutReaderDirectionInversion() {
		assertEquals(ReaderViewerAction.Menu, readerShellCoverViewerActionFor(KomikkuNavigationRegion.MENU))
		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
			readerShellCoverViewerActionFor(KomikkuNavigationRegion.RIGHT)
		)
		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next),
			readerShellCoverViewerActionFor(KomikkuNavigationRegion.NEXT)
		)
		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous),
			readerShellCoverViewerActionFor(KomikkuNavigationRegion.LEFT)
		)
		assertEquals(
			ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous),
			readerShellCoverViewerActionFor(KomikkuNavigationRegion.PREV)
		)
	}

	@Test
	fun webtoonViewerMapsNavigationRegionsToScrollActionsLikeKomikku() {
		val viewer = readerViewerFor(
			webViewPublication(flowMode = ReaderFlowScrolled, paged = false)
		)

		assertEquals(ReaderViewerAction.Menu, viewer.viewerActionFor(KomikkuNavigationRegion.MENU))
		assertEquals(
			ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down),
			viewer.viewerActionFor(KomikkuNavigationRegion.NEXT)
		)
		assertEquals(
			ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Up),
			viewer.viewerActionFor(KomikkuNavigationRegion.PREV)
		)
		assertEquals(
			ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Up),
			viewer.viewerActionFor(KomikkuNavigationRegion.LEFT)
		)
		assertEquals(
			ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down),
			viewer.viewerActionFor(KomikkuNavigationRegion.RIGHT)
		)
	}

	private fun webViewPublication(
		flowMode: String,
		paged: Boolean
	): ReaderEngineViewState.WebViewPublication =
		ReaderEngineViewState.WebViewPublication(
			publicationUrl = "https://example.test/book.epub",
			title = "Book",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false,
			externalShellCover = true,
			nativeShellCoverUrl = null,
			canReturnToShellCover = true,
			settings = defaultReaderSettings().copy(flowMode = flowMode, paged = paged),
			startLocator = null
		)
}
