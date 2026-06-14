package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import paige.navic.ui.screens.reader.KomikkuNavigationRegion
import paige.navic.ui.screens.reader.PagedPublicationReaderViewer
import paige.navic.ui.screens.reader.ReaderViewerMode
import paige.navic.ui.screens.reader.VerticalPagedPublicationReaderViewer
import paige.navic.ui.screens.reader.WebtoonPublicationReaderViewer
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
