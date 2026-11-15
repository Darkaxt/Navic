package paige.navic.reader

enum class ReaderControllerDialog {
	Contents,
	Search,
	Settings,
	WhispersyncPlayer
}

data class ReaderLoadedDocument(
	val index: Int? = null,
	val href: String? = null,
	val title: String? = null,
	val sectionId: String? = null
)

data class ReaderPageTurnSettlementAck(
	val token: String,
	val pageIndex: Int,
	val foliateSessionId: String,
	val rasterGeneration: Long,
	val textureGeneration: Long
)

data class ReaderShellCoverDismissalRequest(
	val requestId: Long,
	val locator: ReaderLocator,
	val foliateSessionId: String?
)

data class RawTextProvenanceState(
	val status: RawTextProvenanceStatus,
	val reason: RawTextProvenanceReason? = null
)

data class ReaderControllerState(
	val publication: ReaderPublicationIdentity? = null,
	val activeEngine: ReaderPublicationFormat? = null,
	val chrome: ReaderChromeState = ReaderChromeState(),
	val chapterProgress: ReaderChapterProgressState = ReaderChapterProgressState(),
	val loadedDocument: ReaderLoadedDocument? = null,
	val lastLinkInteraction: ReaderLinkInteraction? = null,
	val externalLinkPrompt: ReaderExternalLinkPromptState? = null,
	val lastAnnotationInteraction: ReaderAnnotationInteraction? = null,
	val annotationPopup: ReaderAnnotationPopupState? = null,
	val footnotePopup: ReaderFootnotePopupState? = null,
	val lastOverlayInteraction: ReaderOverlayInteraction? = null,
	val shellCoverVisible: Boolean = false,
	val nativeShellCoverUrl: String? = null,
	val nativeShellCoverReturnLocatorKey: String? = null,
	val canReturnToShellCover: Boolean = false,
	val shellCoverDismissalRequestSequence: Long = 0L,
	val pendingShellCoverDismissal: ReaderShellCoverDismissalRequest? = null,
	val menuVisible: Boolean = false,
	val dialog: ReaderControllerDialog? = null,
	val search: ReaderSearchState = ReaderSearchState(),
	val toc: List<ReaderTocItem> = emptyList(),
	val selection: ReaderSelection? = null,
	val selectionNoteDraft: ReaderSelectionNoteDraft? = null,
	val annotations: ReaderAnnotationState = ReaderAnnotationState(),
	val bookmarks: ReaderBookmarkState = ReaderBookmarkState(),
	val readingProgress: ReaderReadingProgressState = ReaderReadingProgressState(),
	val paginationProfile: ReaderPaginationProfileStatus = ReaderPaginationProfileStatus(),
	val readerSettingsPresentationSnapshotKey: Int? = null,
	val foliateSessionId: String? = null,
	val pageTurnSettlementAck: ReaderPageTurnSettlementAck? = null,
	val destinationCommitIdentity: ReaderDestinationCommitIdentity? = null,
	val whispersync: ReaderWhispersyncSessionState = ReaderWhispersyncSessionState(),
	val rawTextProvenanceById: Map<String, RawTextProvenanceState> = emptyMap(),
	val activeMediaOverlay: ReaderOverlayFragment? = null,
	val activeMediaOverlayAnchorReceipt: ReaderWhispersyncAnchorReceipt? = null,
	// The engine confirmed the active overlay without an anchor receipt (anchor refused).
	val activeMediaOverlayAnchorRefused: Boolean = false,
	val audioMetadataLabel: String? = null,
	val lastContentActionClaim: ReaderContentActionClaim? = null,
	val errorMessage: String? = null,
	val errorCode: String? = null
) {
	val canBookmarkCurrentLocation: Boolean
		get() {
			val currentPublication = publication ?: return false
			return readerBookmarkFromLocator(
				bookId = currentPublication.bookId,
				bookTitle = currentPublication.title,
				locator = chrome.currentLocator,
				sectionTitle = chrome.currentSectionTitle
			) != null
		}

	val currentLocationBookmarked: Boolean
		get() {
			val currentPublication = publication ?: return false
			return bookmarks.isBookmarked(currentPublication.bookId, chrome.currentLocator)
		}

	val canNavigateToPreviousChapter: Boolean
		get() = adjacentTocChapter(direction = -1) != null

	val canNavigateToNextChapter: Boolean
		get() = adjacentTocChapter(direction = 1) != null

	val selectionActions: ReaderSelectionActionState
		get() {
			val selectedText = selection?.text.normalizedReaderSelectionValue()
			val selectedCfi = selection?.cfi.normalizedReaderSelectionValue()
			val selectedHref = selection?.href.normalizedReaderSelectionValue()
			val canCopy = selectedText != null
			val canAnchorSelection = canCopy && selectedCfi != null && publication != null
			return ReaderSelectionActionState(
				selectedText = selectedText,
				selectedCfi = selectedCfi,
				selectedHref = selectedHref,
				canCopy = canCopy,
				canHighlight = canAnchorSelection,
				canNote = canAnchorSelection
			)
		}
}

fun ReaderControllerState.supportsReaderEngineCapability(capability: ReaderEngineCapability): Boolean =
	activeEngine?.supportsReaderEngineCapability(capability) != false

/**
 * Whether the native host should hold live-presentation authority for the Whispersync overlay:
 * the engine confirmed the active overlay, with an anchor receipt or with an explicit refusal.
 * A fragment still awaiting its confirmation (every new word after a silent gap) has no receipt
 * yet but was not refused; treating it as a lost anchor re-presented the page on every gap, which
 * made the engine reject the following words as stale and paused playback (#24).
 */
val ReaderControllerState.whispersyncAnchorAuthorityOverlayActive: Boolean
	get() = activeMediaOverlay != null &&
		(activeMediaOverlayAnchorReceipt != null || activeMediaOverlayAnchorRefused)
