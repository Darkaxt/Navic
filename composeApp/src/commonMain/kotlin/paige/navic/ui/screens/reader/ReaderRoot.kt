package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import paige.navic.reader.DefaultReaderWhispersyncHighlightColorArgb
import paige.navic.reader.ReaderAnnotation
import paige.navic.reader.readerPagedLayoutShowsTwoPages
import paige.navic.reader.ReaderBookmark
import paige.navic.reader.ReaderWordSyncFlowClock
import paige.navic.reader.ReaderBridgeEvent
import paige.navic.reader.ReaderControllerDialog
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderDragAnimationCanvas
import paige.navic.reader.ReaderEngineCapability
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderEngineViewState
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderListeningSettings
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPagePreparationPresentation
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageCurlTurnPhase
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderSearchResult
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSettingsScope
import paige.navic.reader.ReaderPageCacheScope
import paige.navic.reader.normalizeReaderPageCacheScope
import paige.navic.reader.ReaderTocItem
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.ReaderWhispersyncPlaybackControlState
import paige.navic.reader.normalizedReaderFlowMode
import paige.navic.reader.normalizedReaderDragAnimationMode
import paige.navic.reader.readerLivingAdvancedEnabled
import paige.navic.reader.readerLivingPlatesEnabled
import paige.navic.reader.readerWhispersyncPlaybackControlPresentation
import paige.navic.reader.readerWhispersyncPlaybackControlState
import paige.navic.reader.readerWhispersyncTransportControls
import paige.navic.reader.readAlongHoldReason
import paige.navic.reader.productionDiagnosticSurface
import paige.navic.reader.readerPageLayoutKey
import paige.navic.reader.readerPageRasterSnapshotKey
import paige.navic.reader.readerPageTurnContentReadyKey
import paige.navic.reader.supportsReaderEngineCapability
import paige.navic.reader.whispersyncAnchorAuthorityOverlayActive
import paige.navic.reader.ReaderBookPreparationPhase
import paige.navic.reader.ReaderBookPreparationProgress
import paige.navic.reader.ReaderPreparationOrientation
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger

private const val KomikkuReaderRootTag = "KomikkuReaderRoot"

/**
 * #70: the orientation the overlay offers to finish without (Done): the one still missing when
 * it cannot be prepared in place, or when its in-place preparation ended incomplete.
 */
internal fun readerBookPreparationAwaiting(
	missing: ReaderPreparationOrientation?,
	alternate: ReaderPreparationOrientation?,
	progress: ReaderBookPreparationProgress
): ReaderPreparationOrientation? = missing?.takeIf {
	alternate == null ||
		(progress.orientation == missing && progress.phase == ReaderBookPreparationPhase.Incomplete)
}

@Composable
internal fun KomikkuReaderRoot(
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	viewState: ReaderEngineViewState,
	navigator: KomikkuReaderNavigator,
	settingsScope: ReaderSettingsScope,
	hasBookSettings: Boolean,
	publicationFormat: ReaderPublicationFormat,
	whispersyncCapable: Boolean,
	listeningSettings: ReaderListeningSettings,
	readaloudPlaybackState: ReaderReadaloudPlaybackUiState?,
	wordSyncFlowClock: ReaderWordSyncFlowClock?,
	onEngineHostEvent: (ReaderEngineHostEvent) -> Unit,
	onViewerAction: (ReaderViewerAction) -> Unit,
	onPageTurnBoundary: (ReaderPageTurnDirection) -> Unit,
	onPageCurlTurnPhase: (ReaderPageCurlTurnPhase) -> Unit,
	/** Why read-along audio must wait (the page is not READY), or null; see readAlongHoldReason. */
	onPageReadinessChange: (String?) -> Unit = {},
	onWhispersyncPlaybackCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
	onToggleWhispersyncCueMap: () -> Unit,
	onWhispersyncCueMapChromeInterception: () -> Unit,
	onPreviousChapter: () -> Unit,
	onNextChapter: () -> Unit,
	onChapterWithAudio: (ReaderPageTurnDirection) -> Unit,
	onGoToChapterPage: (Int) -> Unit,
	onContents: () -> Unit,
	onSearch: () -> Unit,
	onWhispersyncPlayer: () -> Unit,
	onSearchInputChange: (String) -> Unit,
	onSearchQuery: (String) -> Unit,
	onNavigateToSearchResult: (ReaderSearchResult) -> Unit,
	onDismissSearch: () -> Unit,
	onNavigateBack: () -> Unit,
	onSettings: () -> Unit,
	onShowMenus: () -> Unit,
	onHideMenus: () -> Unit,
	onNavigateToTocItem: (ReaderTocItem) -> Unit,
	onNavigateToBookmark: (ReaderBookmark) -> Unit,
	onNavigateToAnnotation: (ReaderAnnotation) -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	onHighlightSelection: () -> Unit,
	onCopySelection: (String) -> Unit,
	onStartSelectionNote: () -> Unit,
	onSelectionNoteDraftChange: (String) -> Unit,
	onSaveSelectionNote: (String) -> Unit,
	onDismissSelectionNote: () -> Unit,
	onDismissAnnotationPopup: () -> Unit,
	onDismissFootnotePopup: () -> Unit,
	onOpenExternalLink: (String) -> Unit,
	onDismissExternalLinkPrompt: () -> Unit,
	onSettingsChange: (ReaderSettings) -> Unit,
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit,
	onRepairWhispersyncMismatch: () -> Unit,
	onDismissDialog: () -> Unit,
	onListeningSettingsChange: (ReaderListeningSettings) -> Unit,
	/**
	 * #66, #68: the whole book was prepared in this orientation; records it and returns the
	 * orientation "Both layouts" still needs, or null when the book is prepared.
	 */
	onBookPrepared: (ReaderPreparationOrientation?) -> ReaderPreparationOrientation? = { null },
	/** Leaves the reader: Cancel, Done, or a moment after the whole book is prepared. */
	onCancelBookPreparation: () -> Unit = {},
	/** #70: whether the other orientation can be prepared without rotating the device. */
	bookPreparationAlternateAvailable: Boolean = true,
	modifier: Modifier = Modifier
) {
	val viewerSlot = remember { ReaderViewerLifecycleSlot() }
	val viewer = remember(viewerSlot, viewState) { viewerSlot.update(viewState) }
	var pagePreparationState by remember { mutableStateOf(ReaderPagePreparationState()) }
	var pagePreparationRetryKey by remember { mutableStateOf(0) }
	var bookPreparationProgress by remember { mutableStateOf(ReaderBookPreparationProgress()) }
	var bookPreparationRetryKey by remember { mutableStateOf(0) }
	val bookPreparationActive = reader.prepareBookPages
	// "Book" cache scope: the opening screen also shows the whole book's cached share.
	val bookCacheScope = !bookPreparationActive &&
		normalizeReaderPageCacheScope(controllerState.chrome.settings.pageCacheScope) == ReaderPageCacheScope.FullBook
	val bookCacheCoverage = rememberReaderPageCacheCoverage(
		destinations = if (bookCacheScope) listOf(reader) else emptyList(),
		refreshKey = pagePreparationState.completedCount
	)
	val bookCacheFraction = bookCacheCoverage?.takeIf { bookCacheScope && it.totalPages > 0 }
		?.let { coverage -> coverage.cachedPages.toFloat() / coverage.totalPages }
	// The cache scope is chosen on the open sheet before the reader starts; the opening screen only
	// shows progress and enters the book as soon as its pages are ready.
	// Read-along audio holds while the page is not READY.
	val pageReadinessHoldReason = pagePreparationState.readAlongHoldReason()
	LaunchedEffect(pageReadinessHoldReason, controllerState.whispersync.pageHoldReason) {
		if (pageReadinessHoldReason != controllerState.whispersync.pageHoldReason) {
			onPageReadinessChange(pageReadinessHoldReason)
		}
	}
	// #70: the orientation still missing after the current one is prepared. The reader prepares
	// it in place when it can; otherwise (or when that fails) the overlay offers Done.
	var bookPreparationMissing by remember { mutableStateOf<ReaderPreparationOrientation?>(null) }
	val bookPreparationAlternate = bookPreparationMissing.takeIf { bookPreparationAlternateAvailable }
	LaunchedEffect(bookPreparationActive, bookPreparationProgress.phase, bookPreparationProgress.orientation) {
		if (bookPreparationActive && bookPreparationProgress.phase == ReaderBookPreparationPhase.Complete) {
			// Recorded at once: the other orientation's progress replaces this report right away.
			val missing = onBookPrepared(bookPreparationProgress.orientation)
			if (missing == null) {
				kotlinx.coroutines.delay(800)
				onCancelBookPreparation()
			} else {
				bookPreparationMissing = missing
			}
		}
	}
	val bookPreparationAwaiting = readerBookPreparationAwaiting(
		missing = bookPreparationMissing,
		alternate = bookPreparationAlternate,
		progress = bookPreparationProgress
	)
	val shellCoverUrl = viewer.shellCoverUrl
	val shellCoverTitle = shellCoverTitleFor(reader, controllerState, viewer)
	val mediaOverlayAvailable = controllerState.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)

	DisposableEffect(viewerSlot) {
		onDispose { viewerSlot.dispose() }
	}

	Box(modifier = modifier.fillMaxSize()) {
		val whispersyncPlaybackControl = readerWhispersyncPlaybackControlPresentation(
			control = readerWhispersyncPlaybackControlState(
				status = controllerState.whispersync.status,
				playbackState = readaloudPlaybackState,
				hasPreparedVisibleTarget = controllerState.whispersync.preparedVisibleTarget != null,
				pageHeld = controllerState.whispersync.pageHoldReason != null
			),
			shellCoverVisible = controllerState.shellCoverVisible,
			mediaOverlayAvailable = mediaOverlayAvailable
		)
		val cueMapAvailable = controllerState.whispersync.available && komikkuReaderDebugToolsEnabled()
		val overlayVisible = controllerState.hasVisibleReaderOverlay() ||
			whispersyncPlaybackControl.visible || cueMapAvailable
		LaunchedEffect(
			controllerState.menuVisible,
			controllerState.dialog,
			controllerState.selectionActions.visible,
			controllerState.whispersync.cueMap.enabled
		) {
			if (
				controllerState.whispersync.cueMap.enabled &&
				(
					controllerState.menuVisible || controllerState.dialog != null ||
					controllerState.selectionActions.visible
				)
			) {
				onWhispersyncCueMapChromeInterception()
			}
		}
		SideEffect {
			Logger.i(
				KomikkuReaderRootTag,
				"Reader chrome overlay visible=$overlayVisible menu=${controllerState.menuVisible} " +
					"shellCover=${controllerState.shellCoverVisible} dialog=${controllerState.dialog}"
			)
		}
		KomikkuReaderNativeFrameHost(
			navigator = navigator,
			navigationOverlayVisible = controllerState.menuVisible && controllerState.chrome.settings.showTapZones == true,
			chromeOverlayVisible = controllerState.menuVisible,
			shellCoverVisible = controllerState.shellCoverVisible,
			shellCoverUrl = shellCoverUrl,
			shellCoverTitle = shellCoverTitle,
			coverBackdropEnabled = controllerState.chrome.settings.coverBackdropEnabled != false,
			backCoverTint = viewer.backCoverTint,
			viewerKey = viewer.key,
			grayscaleEnabled = controllerState.chrome.settings.grayscaleEnabled == true,
			invertedColors = controllerState.chrome.settings.invertedColors == true,
			verticalPageDragPreview = normalizedReaderFlowMode(
				controllerState.chrome.settings.flowMode,
				controllerState.chrome.settings.paged
			) == ReaderFlowPagedVertical,
			pageTurnCanvasEnabled =
				normalizedReaderDragAnimationMode(controllerState.chrome.settings.dragAnimationMode) ==
					ReaderDragAnimationCanvas,
			livingPlatesEnabled = readerLivingPlatesEnabled(
				controllerState.chrome.settings.dragAnimationMode,
				controllerState.chrome.settings.animationLevel
			),
			livingAdvancedEnabled = readerLivingAdvancedEnabled(
				controllerState.chrome.settings.dragAnimationMode,
				controllerState.chrome.settings.animationLevel
			),
			pageTurnReadingDirection = controllerState.chrome.settings.direction,
			pageTurnBitmapQuality = controllerState.chrome.settings.pageBitmapQuality,
			pageCacheScope = controllerState.chrome.settings.pageCacheScope,
			pageCacheLayouts = controllerState.chrome.settings.pageCacheLayouts,
			pageTurnSnapshotKey = controllerState.readerSettingsPresentationSnapshotKey
				?: controllerState.chrome.settings.readerPageRasterSnapshotKey(),
			pageTurnLayoutKey = controllerState.chrome.settings.readerPageLayoutKey(),
			pageTurnContentReadyKey = readerPageTurnContentReadyKey(
				controllerState.paginationProfile
			),
			pageTurnPaginationStatus = controllerState.paginationProfile.status,
			pageTurnVisualPageIndex = controllerState.chrome.currentLocator?.pageIndex,
			pageTurnVisualLocationReason = controllerState.chrome.currentLocator?.reason,
			pageTurnFoliateSessionId = controllerState.foliateSessionId,
			pageTurnSettlementAck = controllerState.pageTurnSettlementAck,
			whispersyncOverlayActive = controllerState.whispersyncAnchorAuthorityOverlayActive,
			whispersyncAnchorReceipt = controllerState.activeMediaOverlayAnchorReceipt,
			whispersyncFlowClock = wordSyncFlowClock,
			whispersyncHighlightColorArgb =
				controllerState.chrome.settings.whispersyncHighlightColorArgb
					?: DefaultReaderWhispersyncHighlightColorArgb,
			whispersyncCueMapState = controllerState.whispersync.cueMap,
			onWhispersyncCueMapHoldOutcome = { sourceOrdinal, outcome ->
				val receipt = controllerState.whispersync.cueMap.geometryReceipt
				if (receipt != null) {
					onEngineHostEvent(
						ReaderEngineHostEvent.FoliateBridge(
							ReaderBridgeEvent.WhispersyncCueMapHoldOutcome(
								sourceOrdinal = sourceOrdinal,
								revisionDigest = receipt.revisionDigest,
								presentationGeneration = receipt.presentationGeneration,
								outcome = outcome
							)
						)
					)
				}
			},
			onWhispersyncCueMapSeekRequested = { sourceOrdinal ->
				val receipt = controllerState.whispersync.cueMap.geometryReceipt
				if (receipt != null) {
					onEngineHostEvent(
						ReaderEngineHostEvent.FoliateBridge(
							ReaderBridgeEvent.WhispersyncCueMapSeekRequested(
								sourceOrdinal = sourceOrdinal,
								revisionDigest = receipt.revisionDigest,
								presentationGeneration = receipt.presentationGeneration,
								destinationCommitIdentity = receipt.destinationCommitIdentity
							)
						)
					)
				}
			},
			pagePreparationCoverVisible =
				controllerState.errorMessage == null &&
					pagePreparationState.presentation == ReaderPagePreparationPresentation.Cover,
			pageOperationPolicy = pagePreparationState.operationPolicy,
			pagePreparationRetryKey = pagePreparationRetryKey,
			onPagePreparationStateChange = { state -> pagePreparationState = state },
			bookPreparationActive = bookPreparationActive,
			bookPreparationRetryKey = bookPreparationRetryKey,
			bookPreparationAlternate = bookPreparationAlternate,
			onBookPreparationProgress = { progress -> bookPreparationProgress = progress },
			onStartupShellPrepared = {
				onViewerAction(ReaderViewerAction.NativeShellPrepared)
			},
			onViewerAction = { action ->
				val viewerAction = if (controllerState.shellCoverVisible) {
					readerShellCoverViewerActionFor(
						region = action,
						pageTurnAllowed = pagePreparationState.interactiveReady &&
							controllerState.paginationProfile.status != "measuring"
					)
				} else {
					viewer.viewerActionFor(action)
				}
				viewerAction?.let(onViewerAction)
			},
			onPageTurnBoundary = onPageTurnBoundary,
			onPageCurlTurnPhase = onPageCurlTurnPhase,
			onReadableDragPreview = { deltaX, deltaY, width, height, phase ->
				onViewerAction(
					ReaderViewerAction.PreviewPageDrag(
						deltaX = deltaX.toDouble(),
						deltaY = deltaY.toDouble(),
						viewWidth = width.toDouble(),
						viewHeight = height.toDouble(),
						phase = phase
					)
				)
			},
			onContentLongPress = { x, y, width, height ->
				onViewerAction(
					ReaderViewerAction.ContentLongPressAt(
						x = x.toDouble(),
						y = y.toDouble(),
						viewWidth = width.toDouble(),
						viewHeight = height.toDouble()
					)
				)
			},
			modifier = Modifier.matchParentSize(),
			viewerContent = {
				ReaderViewerHost(
					readerTitle = reader.title,
					controllerState = controllerState,
					engineRenderer = viewer.engineRenderer,
					onEngineHostEvent = onEngineHostEvent,
					modifier = Modifier.fillMaxSize()
				)
			},
			composeOverlay = {
				if (overlayVisible) {
					KomikkuComposeOverlay(
						reader = reader,
						controllerState = controllerState,
						whispersyncPlaybackControl = whispersyncPlaybackControl,
						readaloudPlaybackState = readaloudPlaybackState,
						onWhispersyncPlaybackCommand = onWhispersyncPlaybackCommand,
						onTurnPageWithAudio = { direction -> onViewerAction(ReaderViewerAction.TurnPage(direction, withAudio = true)) },
						onChapterWithAudio = onChapterWithAudio,
						onToggleWhispersyncCueMap = onToggleWhispersyncCueMap,
						onPreviousChapter = onPreviousChapter,
						onNextChapter = onNextChapter,
						onGoToChapterPage = onGoToChapterPage,
						onContents = onContents,
						onSearch = onSearch,
						onWhispersyncPlayer = onWhispersyncPlayer,
						onSearchInputChange = onSearchInputChange,
						onSearchQuery = onSearchQuery,
						onNavigateToSearchResult = onNavigateToSearchResult,
						onDismissSearch = onDismissSearch,
						onNavigateBack = onNavigateBack,
						onSettings = onSettings,
						onShowMenus = onShowMenus,
						onHideMenus = onHideMenus,
						settingsScope = settingsScope,
						hasBookSettings = hasBookSettings,
						publicationFormat = publicationFormat,
						whispersyncCapable = whispersyncCapable && mediaOverlayAvailable,
						listeningSettings = listeningSettings,
						onNavigateToTocItem = onNavigateToTocItem,
						onToggleCurrentBookmark = onToggleCurrentBookmark,
						onHighlightSelection = onHighlightSelection,
						onCopySelection = onCopySelection,
						onStartSelectionNote = onStartSelectionNote,
						onSelectionNoteDraftChange = onSelectionNoteDraftChange,
						onSaveSelectionNote = onSaveSelectionNote,
						onDismissSelectionNote = onDismissSelectionNote,
						onDismissAnnotationPopup = onDismissAnnotationPopup,
						onDismissFootnotePopup = onDismissFootnotePopup,
						onOpenExternalLink = onOpenExternalLink,
						onDismissExternalLinkPrompt = onDismissExternalLinkPrompt,
						onSettingsChange = onSettingsChange,
						onSettingsScopeChange = onSettingsScopeChange,
						onResetBookSettings = onResetBookSettings,
						onRepairWhispersyncMismatch = onRepairWhispersyncMismatch,
						onListeningSettingsChange = onListeningSettingsChange,
						onNavigateToBookmark = onNavigateToBookmark,
						onNavigateToAnnotation = onNavigateToAnnotation,
						onDismissDialog = onDismissDialog,
						modifier = Modifier.matchParentSize()
					)
				}
				// The book card reports a chapter preparation's progress and failure itself, so the
				// compact chapter card would only peek out behind it (#71). A page that is not ready
				// is still covered.
				if (
					controllerState.errorMessage == null &&
						controllerState.paginationProfile.status != "measuring" &&
						!(bookPreparationActive &&
							pagePreparationState.presentation == ReaderPagePreparationPresentation.Compact &&
							!pagePreparationState.layoutReviewPending)
				) {
					// The Keep layout card sits above the open menu's slider and bottom bar.
					val reviewAboveMenu = pagePreparationState.layoutReviewPending && controllerState.menuVisible
					ReaderPagePreparationOverlay(
						state = pagePreparationState,
						onRetry = { pagePreparationRetryKey += 1 },
						modifier = Modifier
							.matchParentSize()
							.padding(bottom = if (reviewAboveMenu) 136.dp else 0.dp)
					)
				}
				if (controllerState.errorMessage == null && readerOpeningPreparationVisible(pagePreparationState)) {
					ReaderOpeningPreparationScreen(
						title = reader.title,
						state = pagePreparationState,
						profile = controllerState.paginationProfile,
						onRetry = { pagePreparationRetryKey += 1 },
						modifier = Modifier.matchParentSize(),
						bookCacheFraction = bookCacheFraction ?: 0f.takeIf { bookCacheScope }
					)
				}
				if (bookPreparationActive) {
					// A failed page preparation stops the book run, and this overlay covers that
					// failure's Retry card, so it reports the run incomplete and offers that retry.
					val pagePreparationFailed = pagePreparationState.phase == ReaderPagePreparationPhase.Failed &&
						pagePreparationState.retryable
					ReaderBookPreparationOverlay(
						progress = if (pagePreparationFailed) {
							bookPreparationProgress.copy(phase = ReaderBookPreparationPhase.Incomplete)
						} else {
							bookPreparationProgress
						},
						onRetry = {
							if (pagePreparationFailed) pagePreparationRetryKey += 1 else bookPreparationRetryKey += 1
						},
						onCancel = onCancelBookPreparation,
						modifier = Modifier.matchParentSize(),
						awaitingOrientation = bookPreparationAwaiting,
						onDone = onCancelBookPreparation
					)
				}
			}
		)
	}
}

private fun ReaderControllerState.hasVisibleReaderOverlay(): Boolean =
	menuVisible ||
		dialog != null ||
		selectionActions.visible ||
		selectionNoteDraft != null ||
		annotationPopup != null ||
		footnotePopup != null ||
		externalLinkPrompt != null ||
		whispersync.status.requiresAttention ||
		paginationProfile.status == "measuring" ||
		paginationProfile.status == "failed"

private fun shellCoverTitleFor(
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	viewer: ReaderViewer
): String =
	viewer.shellCoverTitle
		?: controllerState.publication?.title?.takeIf { it.isNotBlank() }
		?: reader.title

@Composable
private fun KomikkuComposeOverlay(
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	whispersyncPlaybackControl: ReaderWhispersyncPlaybackControlState,
	readaloudPlaybackState: ReaderReadaloudPlaybackUiState?,
	onWhispersyncPlaybackCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
	onTurnPageWithAudio: (ReaderPageTurnDirection) -> Unit,
	onChapterWithAudio: (ReaderPageTurnDirection) -> Unit,
	onToggleWhispersyncCueMap: () -> Unit,
	onPreviousChapter: () -> Unit,
	onNextChapter: () -> Unit,
	onGoToChapterPage: (Int) -> Unit,
	onContents: () -> Unit,
	onSearch: () -> Unit,
	onWhispersyncPlayer: () -> Unit,
	onSearchInputChange: (String) -> Unit,
	onSearchQuery: (String) -> Unit,
	onNavigateToSearchResult: (ReaderSearchResult) -> Unit,
	onDismissSearch: () -> Unit,
	onNavigateBack: () -> Unit,
	onSettings: () -> Unit,
	onShowMenus: () -> Unit,
	onHideMenus: () -> Unit,
	settingsScope: ReaderSettingsScope,
	hasBookSettings: Boolean,
	publicationFormat: ReaderPublicationFormat,
	whispersyncCapable: Boolean,
	listeningSettings: ReaderListeningSettings,
	onNavigateToTocItem: (ReaderTocItem) -> Unit,
	onNavigateToBookmark: (ReaderBookmark) -> Unit,
	onNavigateToAnnotation: (ReaderAnnotation) -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	onHighlightSelection: () -> Unit,
	onCopySelection: (String) -> Unit,
	onStartSelectionNote: () -> Unit,
	onSelectionNoteDraftChange: (String) -> Unit,
	onSaveSelectionNote: (String) -> Unit,
	onDismissSelectionNote: () -> Unit,
	onDismissAnnotationPopup: () -> Unit,
	onDismissFootnotePopup: () -> Unit,
	onOpenExternalLink: (String) -> Unit,
	onDismissExternalLinkPrompt: () -> Unit,
	onSettingsChange: (ReaderSettings) -> Unit,
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit,
	onRepairWhispersyncMismatch: () -> Unit,
	onListeningSettingsChange: (ReaderListeningSettings) -> Unit,
	onDismissDialog: () -> Unit,
	modifier: Modifier = Modifier
) {
	val cueMapAvailable = controllerState.whispersync.available && komikkuReaderDebugToolsEnabled()
	val bookId = controllerState.publication?.bookId
	val bookmarks = if (bookId == null) {
		emptyList()
	} else {
		controllerState.bookmarks.bookmarksForBook(bookId)
	}
	val annotations = if (bookId == null) {
		emptyList()
	} else {
		controllerState.annotations.annotationsForBook(bookId)
	}

	BoxWithConstraints(modifier = modifier) {
		KomikkuReaderContentOverlay(
			brightness = -(controllerState.chrome.settings.dimOverlayPercent ?: 0),
			color = readerColorFilterColor(controllerState.chrome.settings),
			colorBlendMode = readerColorFilterBlendMode(controllerState.chrome.settings.colorFilterMode),
			modifier = Modifier.matchParentSize()
		)
		KomikkuReaderAppBars(
			visible = controllerState.menuVisible,
			reader = reader,
			controllerState = controllerState,
			onPreviousChapter = onPreviousChapter,
			onNextChapter = onNextChapter,
			onGoToChapterPage = onGoToChapterPage,
			onContents = onContents,
			onSearch = onSearch,
			onNavigateBack = onNavigateBack,
			onSettings = onSettings,
			onToggleCurrentBookmark = onToggleCurrentBookmark,
			onWhispersyncPlayer = onWhispersyncPlayer.takeIf {
				whispersyncPlaybackControl.visible || controllerState.whispersync.available
			},
			modifier = Modifier.matchParentSize()
		)
		KomikkuPaginationProfileStatusBadge(
			profile = controllerState.paginationProfile,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = if (controllerState.menuVisible) 92.dp else 28.dp)
		)
		if (!controllerState.shellCoverVisible) {
			KomikkuReaderSelectionActions(
				selectionActions = controllerState.selectionActions,
				onHighlightSelection = onHighlightSelection,
				onCopySelection = onCopySelection,
				onStartSelectionNote = onStartSelectionNote,
				modifier = Modifier
					.align(Alignment.TopCenter)
					.padding(top = if (controllerState.menuVisible) 96.dp else 24.dp)
			)
			if (controllerState.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)) {
				KomikkuWhispersyncStatusBadge(
					status = controllerState.whispersync.status,
					onRepairMismatch = onRepairWhispersyncMismatch,
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.padding(bottom = if (controllerState.menuVisible) 156.dp else 76.dp)
				)
			}
		}
		// The badges live in the top margin; with the chrome open they would sit on the text (#31).
		// Centred in the top margin in line with the page number below (the left page's on a two-page
		// spread); the selection actions take that spot.
		val controlsOverLeftPage = readerPagedLayoutShowsTwoPages(
			settings = controllerState.chrome.settings,
			widthDp = maxWidth.value,
			heightDp = maxHeight.value
		)
		if (
			!controllerState.menuVisible &&
			!controllerState.selectionActions.visible &&
			(whispersyncPlaybackControl.visible || controllerState.whispersync.available)
		) {
			Row(
				modifier = if (controlsOverLeftPage) {
					Modifier
						.align(Alignment.TopStart)
						.fillMaxWidth(0.5f)
						.wrapContentWidth(Alignment.CenterHorizontally)
						.padding(top = 28.dp)
				} else {
					Modifier
						.align(Alignment.TopCenter)
						.padding(top = 28.dp)
				}
			) {
				// The in-page transport is the whole player surface; the full player opens from the menu bar.
				if (whispersyncPlaybackControl.visible) {
					KomikkuWhispersyncTransportControls(
						transport = readerWhispersyncTransportControls(
							control = whispersyncPlaybackControl,
							playbackState = readaloudPlaybackState,
							userPaused = controllerState.whispersync.userPaused,
							transportPhase = controllerState.whispersync.transportPhase
						),
						readerTheme = controllerState.chrome.settings.theme,
						onCommand = onWhispersyncPlaybackCommand,
						onTurnPageWithAudio = onTurnPageWithAudio
					)
				}
				if (cueMapAvailable) {
					val cueMapDiagnosticSurface = controllerState.whispersync.cueMap.productionDiagnosticSurface(
						revisionDigest = requireNotNull(controllerState.whispersync.sidecar).revisionDigest
					)
					KomikkuWhispersyncCueMapControl(
						enabled = controllerState.whispersync.cueMap.enabled,
						diagnosticLabel = cueMapDiagnosticSurface.label,
						readerTheme = controllerState.chrome.settings.theme,
						onToggle = onToggleWhispersyncCueMap
					)
				}
			}
		}
		when (controllerState.dialog) {
			ReaderControllerDialog.Contents -> KomikkuReaderContentsDialog(
				toc = controllerState.toc,
				bookmarks = bookmarks,
				annotations = annotations,
				onNavigateTo = onNavigateToTocItem,
				onNavigateToBookmark = onNavigateToBookmark,
				onNavigateToAnnotation = onNavigateToAnnotation,
				onDismissRequest = onDismissDialog
			)
			ReaderControllerDialog.Settings -> KomikkuReaderSettingsDialog(
				settings = controllerState.chrome.settings,
				initialTab = 1,
				settingsScope = settingsScope,
				hasBookSettings = hasBookSettings,
				publicationFormat = publicationFormat,
				whispersyncCapable = whispersyncCapable,
				listeningSettings = listeningSettings,
				readaloudPlaybackState = readaloudPlaybackState ?: ReaderReadaloudPlaybackUiState(),
				onSettingsChange = onSettingsChange,
				onListeningSettingsChange = onListeningSettingsChange,
				onWhispersyncPlaybackCommand = onWhispersyncPlaybackCommand,
				onSettingsScopeChange = onSettingsScopeChange,
				onResetBookSettings = onResetBookSettings,
				onShowMenus = onShowMenus,
				onHideMenus = onHideMenus,
				onDismissRequest = onDismissDialog
			)
			ReaderControllerDialog.Search -> if (
				controllerState.supportsReaderEngineCapability(ReaderEngineCapability.Search)
			) {
				KomikkuReaderSearchDialog(
					search = controllerState.search,
					onSearchInputChange = onSearchInputChange,
					onSearchQuery = onSearchQuery,
					onNavigateToSearchResult = onNavigateToSearchResult,
					onDismissSearch = onDismissSearch
				)
			}
			ReaderControllerDialog.WhispersyncPlayer -> if (
				controllerState.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)
			) {
				KomikkuWhispersyncPlayerDialog(
					status = controllerState.whispersync.status,
					playbackState = readaloudPlaybackState ?: ReaderReadaloudPlaybackUiState(),
					hasPreparedVisibleTarget =
						controllerState.whispersync.preparedVisibleTarget != null,
					canStartPlayback = controllerState.whispersync.canStartPlayback,
					onCommand = onWhispersyncPlaybackCommand,
					onTurnPageWithAudio = onTurnPageWithAudio,
					onChapterWithAudio = onChapterWithAudio,
					onDismissRequest = onDismissDialog
				)
			}
			null -> Unit
		}
		controllerState.selectionNoteDraft?.let { draft ->
			KomikkuReaderSelectionNoteDialog(
				draft = draft,
				onSelectionNoteDraftChange = onSelectionNoteDraftChange,
				onSaveSelectionNote = onSaveSelectionNote,
				onDismissSelectionNote = onDismissSelectionNote
			)
		}
		controllerState.annotationPopup?.let { annotation ->
			KomikkuReaderAnnotationDialog(
				annotation = annotation,
				onDismissAnnotationPopup = onDismissAnnotationPopup
			)
		}
		controllerState.footnotePopup?.let { footnote ->
			KomikkuReaderFootnoteDialog(
				footnote = footnote,
				onDismissFootnotePopup = onDismissFootnotePopup
			)
		}
		controllerState.externalLinkPrompt?.let { link ->
			KomikkuReaderExternalLinkDialog(
				link = link,
				onOpenExternalLink = onOpenExternalLink,
				onDismissExternalLinkPrompt = onDismissExternalLinkPrompt
			)
		}
	}
}
