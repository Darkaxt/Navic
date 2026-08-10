package paige.navic.reader

const val ReaderPullUpSourceScrolledEdgeSwipe = "scrolled-edge-swipe"

enum class ReaderEngineCapability {
	Search,
	MediaOverlay
}

private val ReaderTextPublicationCapabilities = setOf(
	ReaderEngineCapability.Search,
	ReaderEngineCapability.MediaOverlay
)

val ReaderPublicationFormat.readerEngineCapabilities: Set<ReaderEngineCapability>
	get() = when (this) {
		ReaderPublicationFormat.Epub,
		ReaderPublicationFormat.Azw3,
		ReaderPublicationFormat.Mobi,
		ReaderPublicationFormat.Fb2 -> ReaderTextPublicationCapabilities

		ReaderPublicationFormat.Pdf,
		ReaderPublicationFormat.Cbz -> emptySet()
	}

fun ReaderPublicationFormat.supportsReaderEngineCapability(capability: ReaderEngineCapability): Boolean =
	capability in readerEngineCapabilities

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
	val suppressWebShellCover: Boolean = false,
	val startLocator: ReaderLocator? = null,
	val settings: ReaderSettings = defaultReaderSettings(),
	val nativeShellCoverUrl: String? = null,
	val nativeShellCoverTint: String? = null,
	val canReturnToShellCover: Boolean = false,
	val startLocatorConflict: ReaderStartLocatorConflict? = null
)

sealed interface ReaderEngineCommand {
	data class OpenPublication(val request: ReaderEngineOpenRequest) : ReaderEngineCommand
	data class NavigateTo(
		val locator: ReaderLocator,
		val relocationReason: String? = null
	) : ReaderEngineCommand
	data class Search(val query: String) : ReaderEngineCommand
	data object ClearSearch : ReaderEngineCommand
	data class TurnPage(val direction: ReaderPageTurnDirection) : ReaderEngineCommand
	data class PreviewPageDrag(
		val deltaX: Double,
		val deltaY: Double = 0.0,
		val viewWidth: Double? = null,
		val viewHeight: Double? = null,
		val phase: ReaderPageDragPreviewPhase = ReaderPageDragPreviewPhase.Update
	) : ReaderEngineCommand
	data class ScrollViewport(val direction: ReaderViewportScrollDirection) : ReaderEngineCommand
	data class ContentLongPressAt(
		val x: Double,
		val y: Double,
		val viewWidth: Double? = null,
		val viewHeight: Double? = null,
		val selectText: Boolean = true
	) : ReaderEngineCommand
	data class ApplySettings(val settings: ReaderSettings) : ReaderEngineCommand
	data class ApplyAnnotations(val annotations: List<ReaderAnnotation>) : ReaderEngineCommand
	data class RequestVisibleTextRange(val source: String) : ReaderEngineCommand
	data class InstallRawTextProvenance(
		val descriptor: ReaderRawTextProvenanceDescriptor
	) : ReaderEngineCommand
	data class ApplyMediaOverlay(val fragment: ReaderOverlayFragment) : ReaderEngineCommand
	data class UpdateMediaOverlayProgress(val fragment: ReaderOverlayFragment) : ReaderEngineCommand
	data object ClearMediaOverlay : ReaderEngineCommand
}

val ReaderEngineCommand.requiredCapability: ReaderEngineCapability?
	get() = when (this) {
		is ReaderEngineCommand.Search,
		ReaderEngineCommand.ClearSearch -> ReaderEngineCapability.Search

		is ReaderEngineCommand.RequestVisibleTextRange,
		is ReaderEngineCommand.InstallRawTextProvenance,
		is ReaderEngineCommand.ApplyMediaOverlay,
		is ReaderEngineCommand.UpdateMediaOverlayProgress,
		ReaderEngineCommand.ClearMediaOverlay -> ReaderEngineCapability.MediaOverlay

		else -> null
	}

enum class ReaderPageTurnDirection {
	Previous,
	Next
}

enum class ReaderPageDragPreviewPhase {
	Update,
	Release,
	Cancel
}

enum class ReaderViewportScrollDirection {
	Up,
	Down
}

sealed interface ReaderEngineEvent {
	data object PublicationReady : ReaderEngineEvent
	data class Relocated(
		val locator: ReaderLocator,
		val foliateSessionId: String,
		val tocTitle: String? = null,
		val pageTurnSettleToken: String? = null,
		val pageTurnSettleSessionId: String? = null,
		val pageTurnSettleRasterGeneration: Long? = null,
		val pageTurnSettleTextureGeneration: Long? = null
	) : ReaderEngineEvent {
		init {
			require(foliateSessionId.isNotBlank())
		}
	}

	data class TocItemChanged(
		val href: String? = null,
		val title: String? = null
	) : ReaderEngineEvent

	data class PaginationProfileStatusChanged(
		val profile: ReaderPaginationProfileStatus
	) : ReaderEngineEvent

	data class SettingsPresentationCommitted(
		val snapshotKey: Int
	) : ReaderEngineEvent

	data class ContentActionClaimed(val claim: ReaderContentActionClaim) : ReaderEngineEvent {
		constructor(action: ReaderContentAction) : this(ReaderContentActionClaim(action = action))
		val action: ReaderContentAction
			get() = claim.action
	}
	data class InternalLinkRequested(
		val href: String? = null,
		val prevented: Boolean = false,
		val source: String? = null
	) : ReaderEngineEvent
	data class ExternalLinkOpened(
		val href: String? = null,
		val anchorHref: String? = null
	) : ReaderEngineEvent
	data class SearchResults(
		val query: String,
		val results: List<ReaderSearchResult>,
		val progress: Double? = null,
		val complete: Boolean = false
	) : ReaderEngineEvent

	data class Toc(val items: List<ReaderTocItem>) : ReaderEngineEvent
	data class SelectionChanged(
		val text: String? = null,
		val cfi: String? = null,
		val href: String? = null,
		val footnote: Boolean? = null,
		val contextText: String? = null,
		val posLeft: Double? = null,
		val posTop: Double? = null,
		val posRight: Double? = null,
		val posBottom: Double? = null
	) : ReaderEngineEvent
	data object SelectionCleared : ReaderEngineEvent
	data class AnnotationClicked(
		val value: String? = null,
		val index: Int? = null,
		val rangeCfi: String? = null
	) : ReaderEngineEvent
	data class AnnotationDrawn(
		val value: String? = null,
		val index: Int? = null,
		val rangeCfi: String? = null
	) : ReaderEngineEvent
	data class OverlayCreated(
		val index: Int? = null
	) : ReaderEngineEvent
	data class DocLoaded(
		val index: Int? = null,
		val href: String? = null,
		val title: String? = null,
		val sectionId: String? = null
	) : ReaderEngineEvent
	data class FootnoteOpened(
		val href: String? = null,
		val text: String? = null,
		val noteType: String? = null,
		val hidden: Boolean = false
	) : ReaderEngineEvent
	data object FootnoteClose : ReaderEngineEvent
	data class PullUp(val source: String? = null) : ReaderEngineEvent
	data class VisibleTextRange(
		val textHref: String,
		val visibleStart: Int,
		val visibleEnd: Int,
		val rangeCfi: String? = null,
		val source: String? = null,
		val rawProvenanceId: String? = null,
		val rawSpineIndex: Int? = null,
		val rawByteStart: Int? = null,
		val rawByteEnd: Int? = null
	) : ReaderEngineEvent
	data class TextPoint(
		val textHref: String,
		val textOffset: Int,
		val rangeCfi: String? = null,
		val source: String? = null,
		val rawProvenanceId: String? = null,
		val rawByteOffset: Int? = null
	) : ReaderEngineEvent
	data class RawTextProvenanceStatusChanged(
		val provenanceId: String,
		val status: RawTextProvenanceStatus,
		val reason: RawTextProvenanceReason? = null
	) : ReaderEngineEvent

	data class MediaOverlayActive(val fragment: ReaderOverlayFragment) : ReaderEngineEvent
	data class MediaOverlayInactive(
		val fragmentId: String? = null,
		val overlayRequestId: Long? = null,
		val coordinateMode: ReaderOverlayCoordinateMode? = null,
		val reason: String? = null
	) : ReaderEngineEvent
	data class Error(val message: String, val code: String? = null) : ReaderEngineEvent
}

val ReaderEngineEvent.requiredCapability: ReaderEngineCapability?
	get() = when (this) {
		is ReaderEngineEvent.SearchResults -> ReaderEngineCapability.Search
		is ReaderEngineEvent.VisibleTextRange,
		is ReaderEngineEvent.TextPoint,
		is ReaderEngineEvent.RawTextProvenanceStatusChanged,
		is ReaderEngineEvent.MediaOverlayActive,
		is ReaderEngineEvent.MediaOverlayInactive -> ReaderEngineCapability.MediaOverlay
		else -> null
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

data class ReaderContentActionClaim(
	val action: ReaderContentAction = ReaderContentAction.Generic,
	val source: String? = null,
	val href: String? = null,
	val src: String? = null,
	val text: String? = null,
	val cfi: String? = null,
	val x: Double? = null,
	val y: Double? = null
)

sealed interface ReaderEngineViewState {
	data object Empty : ReaderEngineViewState

	data class WebViewPublication(
		val publicationUrl: String,
		val title: String,
		val kind: ReaderPublicationKind,
		val mediaOverlayEnabled: Boolean,
		val externalShellCover: Boolean,
		val suppressWebShellCover: Boolean = false,
		val nativeShellCoverUrl: String?,
		val nativeShellCoverTint: String? = null,
		val canReturnToShellCover: Boolean,
		val settings: ReaderSettings,
		val startLocator: ReaderLocator?,
		val rawTextProvenanceDescriptors: List<ReaderRawTextProvenanceDescriptor> = emptyList(),
		val command: ReaderEngineHostCommand? = null,
		val commandKey: Long = 0L
	) : ReaderEngineViewState
}

interface ReaderEngine {
	val format: ReaderPublicationFormat
	val capabilities: Set<ReaderEngineCapability>
		get() = format.readerEngineCapabilities
	fun onCommand(command: ReaderEngineCommand): ReaderEngineStep
	fun onHostEvent(event: ReaderEngineHostEvent): ReaderEngineEvent? = null
}

fun ReaderEngine.supports(command: ReaderEngineCommand): Boolean =
	command.requiredCapability?.let { it in capabilities } != false

fun ReaderEngine.supports(event: ReaderEngineEvent): Boolean =
	event.requiredCapability?.let { it in capabilities } != false

data class ReaderEngineStep(
	val engine: ReaderEngine,
	val viewState: ReaderEngineViewState = ReaderEngineViewState.Empty
)
