package paige.navic.reader

data class ReaderPublicationIdentity(
	val bookId: String,
	val title: String = "",
	val resourceHref: String,
	val kind: ReaderPublicationKind = ReaderPublicationKind.Ebook,
	val format: ReaderPublicationFormat = ReaderPublicationFormat.Epub
)

data class ReaderEngineOpenRequest(
	val publication: ReaderPublicationIdentity,
	val url: String,
	val mediaOverlayEnabled: Boolean = false,
	val externalShellCover: Boolean = false,
	val startLocator: ReaderLocator? = null,
	val settings: ReaderSettings = defaultReaderSettings(),
	val nativeShellCoverUrl: String? = null,
	val canReturnToShellCover: Boolean = false
)

sealed interface ReaderEngineCommand {
	data class OpenPublication(val request: ReaderEngineOpenRequest) : ReaderEngineCommand
	data class NavigateTo(val locator: ReaderLocator) : ReaderEngineCommand
	data class Search(val query: String) : ReaderEngineCommand
	data class TurnPage(val direction: ReaderPageTurnDirection) : ReaderEngineCommand
	data class ScrollViewport(val direction: ReaderViewportScrollDirection) : ReaderEngineCommand
	data class ApplySettings(val settings: ReaderSettings) : ReaderEngineCommand
	data class ApplyAnnotations(val annotations: List<ReaderAnnotation>) : ReaderEngineCommand
	data class ApplyMediaOverlay(val fragment: ReaderOverlayFragment) : ReaderEngineCommand
	data object ClearMediaOverlay : ReaderEngineCommand
}

enum class ReaderPageTurnDirection {
	Previous,
	Next
}

enum class ReaderViewportScrollDirection {
	Up,
	Down
}

sealed interface ReaderEngineEvent {
	data object PublicationReady : ReaderEngineEvent
	data class Relocated(
		val locator: ReaderLocator,
		val tocTitle: String? = null
	) : ReaderEngineEvent

	data class TocItemChanged(
		val href: String? = null,
		val title: String? = null
	) : ReaderEngineEvent

	data class ContentActionClaimed(val action: ReaderContentAction) : ReaderEngineEvent
	data class SearchResults(
		val query: String,
		val results: List<ReaderSearchResult>
	) : ReaderEngineEvent

	data class Toc(val items: List<ReaderTocItem>) : ReaderEngineEvent
	data class SelectionChanged(
		val text: String? = null,
		val cfi: String? = null,
		val href: String? = null
	) : ReaderEngineEvent

	data class MediaOverlayActive(val fragment: ReaderOverlayFragment) : ReaderEngineEvent
	data class MediaOverlayInactive(val fragmentId: String? = null) : ReaderEngineEvent
	data class Error(val message: String, val code: String? = null) : ReaderEngineEvent
}

enum class ReaderContentAction {
	Generic,
	Link,
	Image,
	Selection,
	FormControl,
	MediaControl,
	Annotation,
	Footnote
}

sealed interface ReaderEngineViewState {
	data object Empty : ReaderEngineViewState

	data class WebViewPublication(
		val publicationUrl: String,
		val title: String,
		val kind: ReaderPublicationKind,
		val mediaOverlayEnabled: Boolean,
		val externalShellCover: Boolean,
		val nativeShellCoverUrl: String?,
		val canReturnToShellCover: Boolean,
		val settings: ReaderSettings,
		val startLocator: ReaderLocator?,
		val command: ReaderEngineHostCommand? = null,
		val commandKey: Long = 0L
	) : ReaderEngineViewState
}

interface ReaderEngine {
	val format: ReaderPublicationFormat
	fun onCommand(command: ReaderEngineCommand): ReaderEngineStep
	fun onHostEvent(event: ReaderEngineHostEvent): ReaderEngineEvent? = null
}

data class ReaderEngineStep(
	val engine: ReaderEngine,
	val viewState: ReaderEngineViewState = ReaderEngineViewState.Empty
)
