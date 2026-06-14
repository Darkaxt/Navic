package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import paige.navic.reader.ReaderEngineViewState
import paige.navic.reader.ReaderFlowPaged
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.defaultReaderSettings

class ReaderViewerLifecycleSlotTest {
	@Test
	fun lifecycleSlotRetainsSameViewerAndDestroysPreviousViewerWhenKeyChangesLikeKomikkuUpdateViewer() {
		val destroyed = mutableListOf<ReaderViewerKey>()
		val created = mutableListOf<ReaderViewerKey>()
		val slot = ReaderViewerLifecycleSlot(
			createViewer = { viewState ->
				RecordingReaderViewer(
					key = readerViewerKeyFor(viewState),
					viewState = viewState,
					destroyed = destroyed
				).also { created += it.key }
			}
		)
		val paged = webViewPublication(flowMode = ReaderFlowPaged, paged = true)
		val pagedWithCommand = paged.copy(commandKey = 1L)
		val scrolled = paged.copy(settings = paged.settings.copy(flowMode = ReaderFlowScrolled, paged = false))

		val first = slot.update(paged)
		val sameViewer = slot.update(pagedWithCommand)
		val replacement = slot.update(scrolled)
		slot.dispose()

		assertEquals(
			listOf(readerViewerKeyFor(paged), readerViewerKeyFor(scrolled)),
			created
		)
		assertSame(first, sameViewer)
		assertEquals(pagedWithCommand, sameViewer.viewState)
		assertEquals(
			listOf(readerViewerKeyFor(paged), readerViewerKeyFor(scrolled)),
			destroyed
		)
		assertEquals(EmptyReaderViewer(), slot.viewer)
		assertEquals(readerViewerKeyFor(scrolled), replacement.key)
	}

	private class RecordingReaderViewer(
		override val key: ReaderViewerKey,
		override var viewState: ReaderEngineViewState,
		private val destroyed: MutableList<ReaderViewerKey>
	) : ReaderViewer {
		override val shellCoverUrl: String? = null
		override val shellCoverTitle: String? = null

		override fun withViewState(viewState: ReaderEngineViewState): ReaderViewer {
			this.viewState = viewState
			return this
		}

		override fun viewerActionFor(region: KomikkuNavigationRegion) =
			readerViewerActionFor(region)

		override fun destroy() {
			destroyed += key
		}
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
