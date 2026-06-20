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
