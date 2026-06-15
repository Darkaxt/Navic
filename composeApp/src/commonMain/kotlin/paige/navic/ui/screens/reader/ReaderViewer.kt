package paige.navic.ui.screens.reader

import paige.navic.reader.ReaderEngineRenderer
import paige.navic.reader.ReaderEngineViewState
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderFlowScrolled
import paige.navic.reader.ReaderFlowScrolledGaps
import paige.navic.reader.ReaderDirectionDefault
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderTapZoneAction
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.ReaderViewportScrollDirection
import paige.navic.reader.normalizedReaderFlowMode
import paige.navic.reader.readerTapZonePageTurnDirectionFor

enum class ReaderViewerKind {
	Empty,
	WebViewPublication
}

enum class ReaderViewerMode {
	Empty,
	Paged,
	PagedVertical,
	Scrolled
}

data class ReaderViewerKey(
	val kind: ReaderViewerKind,
	val identity: String = "",
	val mode: ReaderViewerMode = ReaderViewerMode.Empty
)

interface ReaderViewer {
	val key: ReaderViewerKey
	val viewState: ReaderEngineViewState
	val engineRenderer: ReaderEngineRenderer
	val shellCoverUrl: String?
	val shellCoverTitle: String?
	fun withViewState(viewState: ReaderEngineViewState): ReaderViewer
	fun viewerActionFor(region: KomikkuNavigationRegion): ReaderViewerAction
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
	private val viewerMode: ReaderViewerMode
) : ReaderViewer {
	override val key: ReaderViewerKey = ReaderViewerKey(
		kind = ReaderViewerKind.WebViewPublication,
		identity = viewState.publicationUrl,
		mode = viewerMode
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
) : WebViewPublicationReaderViewer(viewState, ReaderViewerMode.Paged) {
	override fun withPublicationViewState(
		viewState: ReaderEngineViewState.WebViewPublication
	): WebViewPublicationReaderViewer = PagedPublicationReaderViewer(viewState)
}

class VerticalPagedPublicationReaderViewer(
	viewState: ReaderEngineViewState.WebViewPublication
) : WebViewPublicationReaderViewer(viewState, ReaderViewerMode.PagedVertical) {
	override fun withPublicationViewState(
		viewState: ReaderEngineViewState.WebViewPublication
	): WebViewPublicationReaderViewer = VerticalPagedPublicationReaderViewer(viewState)
}

class WebtoonPublicationReaderViewer(
	viewState: ReaderEngineViewState.WebViewPublication
) : WebViewPublicationReaderViewer(viewState, ReaderViewerMode.Scrolled) {
	override fun withPublicationViewState(
		viewState: ReaderEngineViewState.WebViewPublication
	): WebViewPublicationReaderViewer = WebtoonPublicationReaderViewer(viewState)

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
			mode = readerViewerModeFor(viewState.settings)
		)
	}

fun readerViewerFor(viewState: ReaderEngineViewState): ReaderViewer =
	when (viewState) {
		ReaderEngineViewState.Empty -> EmptyReaderViewer()
		is ReaderEngineViewState.WebViewPublication -> when (readerViewerModeFor(viewState.settings)) {
			ReaderViewerMode.Paged -> PagedPublicationReaderViewer(viewState)
			ReaderViewerMode.PagedVertical -> VerticalPagedPublicationReaderViewer(viewState)
			ReaderViewerMode.Scrolled -> WebtoonPublicationReaderViewer(viewState)
			ReaderViewerMode.Empty -> EmptyReaderViewer()
		}
	}

fun readerViewerModeFor(settings: ReaderSettings): ReaderViewerMode =
	when (normalizedReaderFlowMode(settings.flowMode, settings.paged)) {
		ReaderFlowPagedVertical -> ReaderViewerMode.PagedVertical
		ReaderFlowScrolled,
		ReaderFlowScrolledGaps -> ReaderViewerMode.Scrolled
		else -> ReaderViewerMode.Paged
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
