package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import paige.navic.reader.ReaderAnnotation
import paige.navic.reader.ReaderBookmark
import paige.navic.reader.ReaderControllerDialog
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderDragAnimationCanvas
import paige.navic.reader.ReaderEngineCapability
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderEngineViewState
import paige.navic.reader.ReaderFlowPagedVertical
import paige.navic.reader.ReaderListeningSettings
import paige.navic.reader.ReaderPagePreparationPresentation
import paige.navic.reader.ReaderPagePreparationState
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderSearchResult
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSettingsScope
import paige.navic.reader.ReaderTocItem
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.ReaderWhispersyncPlaybackControlState
import paige.navic.reader.normalizedReaderFlowMode
import paige.navic.reader.normalizedReaderDragAnimationMode
import paige.navic.reader.readerWhispersyncPlaybackControlState
import paige.navic.reader.readerPageRasterSnapshotKey
import paige.navic.reader.readerPageTurnContentReadyKey
import paige.navic.reader.supportsReaderEngineCapability
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger

private const val KomikkuReaderRootTag = "KomikkuReaderRoot"

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
	onEngineHostEvent: (ReaderEngineHostEvent) -> Unit,
	onViewerAction: (ReaderViewerAction) -> Unit,
	onPageTurnBoundary: (ReaderPageTurnDirection) -> Unit,
	onWhispersyncPlaybackCommand: (ReaderReadaloudPlaybackCommand) -> Unit,
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
	modifier: Modifier = Modifier
) {
	val viewerSlot = remember { ReaderViewerLifecycleSlot() }
	val viewer = remember(viewerSlot, viewState) { viewerSlot.update(viewState) }
	var pagePreparationState by remember { mutableStateOf(ReaderPagePreparationState()) }
	var pagePreparationRetryKey by remember { mutableStateOf(0) }
	val shellCoverUrl = viewer.shellCoverUrl
	val shellCoverTitle = shellCoverTitleFor(reader, controllerState, viewer)
	val mediaOverlayAvailable = controllerState.supportsReaderEngineCapability(ReaderEngineCapability.MediaOverlay)

	DisposableEffect(viewerSlot) {
		onDispose { viewerSlot.dispose() }
	}

	Box(modifier = modifier.fillMaxSize()) {
		val whispersyncPlaybackControl = readerWhispersyncPlaybackControlState(
			status = controllerState.whispersync.status,
			playbackState = readaloudPlaybackState,
			hasConfirmedVisibleCue = controllerState.activeMediaOverlay != null
		).let { control ->
			if (controllerState.shellCoverVisible || !mediaOverlayAvailable) control.copy(visible = false) else control
		}
		val overlayVisible = controllerState.hasVisibleReaderOverlay() || whispersyncPlaybackControl.visible
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
			pageTurnReadingDirection = controllerState.chrome.settings.direction,
			pageTurnBitmapQuality = controllerState.chrome.settings.pageBitmapQuality,
			pageTurnSnapshotKey = controllerState.readerSettingsPresentationSnapshotKey
				?: controllerState.chrome.settings.readerPageRasterSnapshotKey(),
			pageTurnContentReadyKey = readerPageTurnContentReadyKey(
				controllerState.paginationProfile
			),
			pageTurnPaginationStatus = controllerState.paginationProfile.status,
			pageTurnVisualPageIndex = controllerState.chrome.currentLocator?.pageIndex,
			pageTurnVisualLocationReason = controllerState.chrome.currentLocator?.reason,
			pageTurnFoliateSessionId = controllerState.foliateSessionId,
			pageTurnSettlementAck = controllerState.pageTurnSettlementAck,
			pagePreparationCoverVisible = pagePreparationState.presentation == ReaderPagePreparationPresentation.Cover,
			pageOperationPolicy = pagePreparationState.operationPolicy,
			pagePreparationRetryKey = pagePreparationRetryKey,
			onPagePreparationStateChange = { state -> pagePreparationState = state },
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
				if (controllerState.paginationProfile.status != "measuring") {
					ReaderPagePreparationOverlay(
						state = pagePreparationState,
						onRetry = { pagePreparationRetryKey += 1 },
						modifier = Modifier.matchParentSize()
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

	Box(modifier = modifier) {
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
				KomikkuWhispersyncPlaybackControl(
					control = whispersyncPlaybackControl,
					readerTheme = controllerState.chrome.settings.theme,
					onCommand = onWhispersyncPlaybackCommand,
					onOpenPlayer = onWhispersyncPlayer,
					modifier = Modifier
						.align(Alignment.TopStart)
						.padding(top = if (controllerState.menuVisible) 116.dp else 28.dp, start = 28.dp)
				)
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
					hasConfirmedVisibleCue = controllerState.activeMediaOverlay != null,
					onCommand = onWhispersyncPlaybackCommand,
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
