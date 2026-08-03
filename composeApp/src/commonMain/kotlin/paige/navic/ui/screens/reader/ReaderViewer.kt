package paige.navic.ui.screens.reader

// Ported from Komikku viewer lifecycle sources:
// - tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt updateViewer()
//   creates the reading-mode viewer, destroys the previous viewer, clears viewerContainer, and mounts newViewer.getView().
// - tmp/references/komikku/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/Viewer.kt
//   defines getView(), destroy(), setChapters(), moveToPage(), and input-handler responsibilities.
import paige.navic.reader.ReaderEngineRenderer
import paige.navic.reader.ReaderEngineViewState
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderNavBarTypeBottom
import paige.navic.reader.ReaderNavigationMode
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderTapZoneAction
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.ReaderViewportScrollDirection
import paige.navic.reader.normalizedReaderFlowMode
import paige.navic.reader.normalizedReaderNavBarType
import paige.navic.reader.readerNavigationModeFor
import paige.navic.reader.readerTapZonePageTurnDirectionFor

enum class ReaderViewerKind {
	Empty,
	WebViewPublication
}

private enum class ReaderViewerImplementation {
	Paged,
	PagedVertical,
	Scrolled
}

data class ReaderViewerKey(
	val kind: ReaderViewerKind,
	val identity: String = "",
	val mode: ReaderNavigationMode? = null,
	val verticalPagination: Boolean = false
)

interface ReaderViewer {
	val key: ReaderViewerKey
	val viewState: ReaderEngineViewState
	val engineRenderer: ReaderEngineRenderer
	val shellCoverUrl: String?
	val shellCoverTitle: String?
	fun withViewState(viewState: ReaderEngineViewState): ReaderViewer
	fun viewerActionFor(region: KomikkuNavigationRegion): ReaderViewerAction
	fun moveToPage(locator: ReaderLocator): ReaderViewerAction =
		ReaderViewerAction.NavigateTo(locator)
	fun destroy()
}

class ReaderViewerLifecycleSlot(
	private val createViewer: (ReaderEngineViewState) -> ReaderViewer = ::readerViewerFor
) {
	private var activeViewer: ReaderViewer? = null

	val viewer: ReaderViewer
		get() = activeViewer ?: EmptyReaderViewer()

	fun update(viewState: ReaderEngineViewState): ReaderViewer {
		val current = activeViewer
		val nextKey = readerViewerKeyFor(viewState)
		val nextViewer = if (current != null && current.key == nextKey) {
			current.withViewState(viewState)
		} else {
			current?.destroy()
			createViewer(viewState)
		}
		activeViewer = nextViewer
		return nextViewer
	}

	fun dispose() {
		activeViewer?.destroy()
		activeViewer = null
	}
}

data class EmptyReaderViewer(
	override val viewState: ReaderEngineViewState.Empty = ReaderEngineViewState.Empty
) : ReaderViewer {
	override val key: ReaderViewerKey = ReaderViewerKey(ReaderViewerKind.Empty)
	override val engineRenderer: ReaderEngineRenderer = ReaderEngineRenderer.Empty
	override val shellCoverUrl: String? = null
	override val shellCoverTitle: String? = null

	override fun withViewState(viewState: ReaderEngineViewState): ReaderViewer =
		if (viewState is ReaderEngineViewState.Empty) copy(viewState = viewState) else readerViewerFor(viewState)

	override fun viewerActionFor(region: KomikkuNavigationRegion): ReaderViewerAction =
		readerViewerActionFor(region)

	override fun destroy() = Unit
}

sealed class WebViewPublicationReaderViewer(
	override val viewState: ReaderEngineViewState.WebViewPublication,
	private val navigationMode: ReaderNavigationMode,
	private val verticalPagination: Boolean = false
) : ReaderViewer {
	override val key: ReaderViewerKey = ReaderViewerKey(
		kind = ReaderViewerKind.WebViewPublication,
		identity = viewState.publicationUrl,
		mode = navigationMode,
		verticalPagination = verticalPagination
	)
	override val engineRenderer: ReaderEngineRenderer = ReaderEngineRenderer.FoliatePublication.from(viewState)
	override val shellCoverUrl: String? = viewState.nativeShellCoverUrl
	override val shellCoverTitle: String? = viewState.title.takeIf { it.isNotBlank() }

	override fun withViewState(viewState: ReaderEngineViewState): ReaderViewer =
		if (viewState is ReaderEngineViewState.WebViewPublication &&
			readerViewerKeyFor(viewState) == key
		) {
			withPublicationViewState(viewState)
		} else {
			readerViewerFor(viewState)
		}

	protected abstract fun withPublicationViewState(
		viewState: ReaderEngineViewState.WebViewPublication
	): WebViewPublicationReaderViewer

	open override fun viewerActionFor(region: KomikkuNavigationRegion): ReaderViewerAction =
		readerViewerActionFor(region, viewState.settings.direction)

	override fun destroy() = Unit
}

class PagedPublicationReaderViewer(
	viewState: ReaderEngineViewState.WebViewPublication
) : WebViewPublicationReaderViewer(viewState, ReaderNavigationMode.Paged) {
	override fun withPublicationViewState(
		viewState: ReaderEngineViewState.WebViewPublication
	): WebViewPublicationReaderViewer = PagedPublicationReaderViewer(viewState)
}

class VerticalPagedPublicationReaderViewer(
	viewState: ReaderEngineViewState.WebViewPublication
) : WebViewPublicationReaderViewer(
	viewState = viewState,
	navigationMode = ReaderNavigationMode.Paged,
	verticalPagination = true
) {
	override fun withPublicationViewState(
		viewState: ReaderEngineViewState.WebViewPublication
	): WebViewPublicationReaderViewer = VerticalPagedPublicationReaderViewer(viewState)
}

class ScrolledPublicationReaderViewer(
	viewState: ReaderEngineViewState.WebViewPublication
) : WebViewPublicationReaderViewer(viewState, ReaderNavigationMode.Scrolled) {
	override fun withPublicationViewState(
		viewState: ReaderEngineViewState.WebViewPublication
	): WebViewPublicationReaderViewer = ScrolledPublicationReaderViewer(viewState)

	override fun viewerActionFor(region: KomikkuNavigationRegion): ReaderViewerAction =
		when (region) {
			KomikkuNavigationRegion.MENU -> ReaderViewerAction.Menu
			KomikkuNavigationRegion.NEXT,
			KomikkuNavigationRegion.RIGHT -> ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Down)
			KomikkuNavigationRegion.PREV,
			KomikkuNavigationRegion.LEFT -> ReaderViewerAction.ScrollViewport(ReaderViewportScrollDirection.Up)
		}
}

fun readerViewerKeyFor(viewState: ReaderEngineViewState): ReaderViewerKey =
	when (viewState) {
		ReaderEngineViewState.Empty -> ReaderViewerKey(ReaderViewerKind.Empty)
		is ReaderEngineViewState.WebViewPublication -> ReaderViewerKey(
			kind = ReaderViewerKind.WebViewPublication,
			identity = viewState.publicationUrl,
			mode = readerNavigationModeFor(viewState.settings),
			verticalPagination = readerViewerImplementationFor(viewState.settings) ==
				ReaderViewerImplementation.PagedVertical
		)
	}

fun readerViewerFor(viewState: ReaderEngineViewState): ReaderViewer =
	when (viewState) {
		ReaderEngineViewState.Empty -> EmptyReaderViewer()
		is ReaderEngineViewState.WebViewPublication -> when (readerViewerImplementationFor(viewState.settings)) {
			ReaderViewerImplementation.Paged -> PagedPublicationReaderViewer(viewState)
			ReaderViewerImplementation.PagedVertical -> VerticalPagedPublicationReaderViewer(viewState)
			ReaderViewerImplementation.Scrolled -> ScrolledPublicationReaderViewer(viewState)
		}
	}

private fun readerViewerImplementationFor(settings: ReaderSettings): ReaderViewerImplementation =
	when (normalizedReaderFlowMode(settings.flowMode, settings.paged)) {
		ReaderFlowPagedVertical -> ReaderViewerImplementation.PagedVertical
		ReaderFlowScrolled,
		ReaderFlowScrolledGaps -> ReaderViewerImplementation.Scrolled
		else -> ReaderViewerImplementation.Paged
	}

fun readerEffectiveNavBarTypeFor(settings: ReaderSettings): String {
	val requested = normalizedReaderNavBarType(settings.navBarType)
	return when (readerViewerImplementationFor(settings)) {
		ReaderViewerImplementation.PagedVertical,
		ReaderViewerImplementation.Scrolled -> requested
		ReaderViewerImplementation.Paged -> ReaderNavBarTypeBottom
	}
}

fun readerViewerActionFor(
	region: KomikkuNavigationRegion,
	direction: String? = ReaderDirectionDefault
): ReaderViewerAction =
	when (region) {
		KomikkuNavigationRegion.MENU -> ReaderViewerAction.Menu
		KomikkuNavigationRegion.PREV -> ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
		KomikkuNavigationRegion.NEXT -> ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		KomikkuNavigationRegion.LEFT -> ReaderViewerAction.TurnPage(
			readerTapZonePageTurnDirectionFor(ReaderTapZoneAction.Left, direction)
				?: ReaderPageTurnDirection.Previous
		)
		KomikkuNavigationRegion.RIGHT -> ReaderViewerAction.TurnPage(
			readerTapZonePageTurnDirectionFor(ReaderTapZoneAction.Right, direction)
				?: ReaderPageTurnDirection.Next
		)
	}

fun readerShellCoverViewerActionFor(region: KomikkuNavigationRegion): ReaderViewerAction =
	when (region) {
		KomikkuNavigationRegion.MENU -> ReaderViewerAction.Menu
		KomikkuNavigationRegion.NEXT -> ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		KomikkuNavigationRegion.RIGHT -> ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
		KomikkuNavigationRegion.PREV -> ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
		KomikkuNavigationRegion.LEFT -> ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
	}

fun readerShellCoverViewerActionFor(
	region: KomikkuNavigationRegion,
	pageTurnAllowed: Boolean
): ReaderViewerAction? = when {
	region == KomikkuNavigationRegion.MENU -> ReaderViewerAction.Menu
	!pageTurnAllowed -> null
	else -> readerShellCoverViewerActionFor(region)
}
