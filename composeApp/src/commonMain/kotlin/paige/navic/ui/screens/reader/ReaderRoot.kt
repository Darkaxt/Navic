package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import paige.navic.reader.ReaderControllerDialog
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderEngineViewState
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderSearchResult
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSettingsScope
import paige.navic.reader.ReaderTocItem
import paige.navic.reader.ReaderViewerAction
import paige.navic.ui.navigation.Screen

@Composable
internal fun KomikkuReaderRoot(
	reader: Screen.Reader,
	controllerState: ReaderControllerState,
	viewState: ReaderEngineViewState,
	navigator: KomikkuReaderNavigator,
	settingsScope: ReaderSettingsScope,
	hasBookSettings: Boolean,
	publicationFormat: ReaderPublicationFormat,
	onEngineHostEvent: (ReaderEngineHostEvent) -> Unit,
	onViewerAction: (ReaderViewerAction) -> Unit,
	onPreviousChapter: () -> Unit,
	onNextChapter: () -> Unit,
	onGoToChapterPage: (Int) -> Unit,
	onHistoryBack: () -> Unit,
	onHistoryForward: () -> Unit,
	onDismissHistory: () -> Unit,
	onContents: () -> Unit,
	onSearch: () -> Unit,
	onSearchQuery: (String) -> Unit,
	onNavigateToSearchResult: (ReaderSearchResult) -> Unit,
	onDismissSearch: () -> Unit,
	onNavigateBack: () -> Unit,
	onSettings: () -> Unit,
	onShowMenus: () -> Unit,
	onHideMenus: () -> Unit,
	onNavigateToTocItem: (ReaderTocItem) -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	onHighlightSelection: () -> Unit,
	onCopySelection: (String) -> Unit,
	onStartSelectionNote: () -> Unit,
	onSaveSelectionNote: (String) -> Unit,
	onDismissSelectionNote: () -> Unit,
	onDismissAnnotationPopup: () -> Unit,
	onDismissFootnotePopup: () -> Unit,
	onOpenExternalLink: (String) -> Unit,
	onDismissExternalLinkPrompt: () -> Unit,
	onSettingsChange: (ReaderSettings) -> Unit,
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit,
	onDismissDialog: () -> Unit,
	modifier: Modifier = Modifier
) {
	val viewerSlot = remember { ReaderViewerLifecycleSlot() }
	val viewer = remember(viewerSlot, viewState) { viewerSlot.update(viewState) }
	val shellCoverUrl = viewer.shellCoverUrl
	val shellCoverTitle = shellCoverTitleFor(reader, controllerState, viewer)

	DisposableEffect(viewerSlot) {
		onDispose { viewerSlot.dispose() }
	}

	KomikkuReaderNativeFrameHost(
		navigator = navigator,
		navigationOverlayVisible = controllerState.menuVisible && controllerState.chrome.settings.showTapZones == true,
		shellCoverVisible = controllerState.shellCoverVisible,
		shellCoverUrl = shellCoverUrl,
		shellCoverTitle = shellCoverTitle,
		viewerKey = viewer.key,
		grayscaleEnabled = controllerState.chrome.settings.grayscaleEnabled == true,
		invertedColors = controllerState.chrome.settings.invertedColors == true,
		onViewerAction = { action ->
			onViewerAction(
				if (controllerState.shellCoverVisible) {
					readerShellCoverViewerActionFor(action)
				} else {
					viewer.viewerActionFor(action)
				}
			)
		},
		onReadableDragPreview = { deltaX, width, phase ->
			onViewerAction(
				ReaderViewerAction.PreviewPageDrag(
					deltaX = deltaX.toDouble(),
					viewWidth = width.toDouble(),
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
		modifier = modifier,
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
			KomikkuComposeOverlay(
				reader = reader,
				controllerState = controllerState,
				onPreviousChapter = onPreviousChapter,
				onNextChapter = onNextChapter,
				onGoToChapterPage = onGoToChapterPage,
				onHistoryBack = onHistoryBack,
				onHistoryForward = onHistoryForward,
				onDismissHistory = onDismissHistory,
				onContents = onContents,
				onSearch = onSearch,
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
				onNavigateToTocItem = onNavigateToTocItem,
				onToggleCurrentBookmark = onToggleCurrentBookmark,
				onHighlightSelection = onHighlightSelection,
				onCopySelection = onCopySelection,
				onStartSelectionNote = onStartSelectionNote,
				onSaveSelectionNote = onSaveSelectionNote,
				onDismissSelectionNote = onDismissSelectionNote,
				onDismissAnnotationPopup = onDismissAnnotationPopup,
				onDismissFootnotePopup = onDismissFootnotePopup,
				onOpenExternalLink = onOpenExternalLink,
				onDismissExternalLinkPrompt = onDismissExternalLinkPrompt,
				onSettingsChange = onSettingsChange,
				onSettingsScopeChange = onSettingsScopeChange,
				onResetBookSettings = onResetBookSettings,
				onDismissDialog = onDismissDialog,
				modifier = Modifier.fillMaxSize()
			)
		}
	)
}

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
	onPreviousChapter: () -> Unit,
	onNextChapter: () -> Unit,
	onGoToChapterPage: (Int) -> Unit,
	onHistoryBack: () -> Unit,
	onHistoryForward: () -> Unit,
	onDismissHistory: () -> Unit,
	onContents: () -> Unit,
	onSearch: () -> Unit,
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
	onNavigateToTocItem: (ReaderTocItem) -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	onHighlightSelection: () -> Unit,
	onCopySelection: (String) -> Unit,
	onStartSelectionNote: () -> Unit,
	onSaveSelectionNote: (String) -> Unit,
	onDismissSelectionNote: () -> Unit,
	onDismissAnnotationPopup: () -> Unit,
	onDismissFootnotePopup: () -> Unit,
	onOpenExternalLink: (String) -> Unit,
	onDismissExternalLinkPrompt: () -> Unit,
	onSettingsChange: (ReaderSettings) -> Unit,
	onSettingsScopeChange: (ReaderSettingsScope) -> Unit,
	onResetBookSettings: () -> Unit,
	onDismissDialog: () -> Unit,
	modifier: Modifier = Modifier
) {
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
		if (!controllerState.shellCoverVisible) {
			KomikkuReaderHistoryCapsule(
				navigation = controllerState.engineNavigation,
				onHistoryBack = onHistoryBack,
				onHistoryForward = onHistoryForward,
				onDismissHistory = onDismissHistory,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = if (controllerState.menuVisible) 96.dp else 40.dp)
			)
		}
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
			KomikkuPaginationProfileStatusBadge(
				profile = controllerState.paginationProfile,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.padding(bottom = if (controllerState.menuVisible) 92.dp else 28.dp)
			)
		}
		when (controllerState.dialog) {
			ReaderControllerDialog.Contents -> KomikkuReaderContentsDialog(
				toc = controllerState.toc,
				onNavigateTo = onNavigateToTocItem,
				onDismissRequest = onDismissDialog
			)
			ReaderControllerDialog.ReadingMode -> KomikkuReaderSettingsDialog(
				settings = controllerState.chrome.settings,
				initialTab = 0,
				settingsScope = settingsScope,
				hasBookSettings = hasBookSettings,
				publicationFormat = publicationFormat,
				onSettingsChange = onSettingsChange,
				onSettingsScopeChange = onSettingsScopeChange,
				onResetBookSettings = onResetBookSettings,
				onShowMenus = onShowMenus,
				onHideMenus = onHideMenus,
				onDismissRequest = onDismissDialog
			)
			ReaderControllerDialog.Settings -> KomikkuReaderSettingsDialog(
				settings = controllerState.chrome.settings,
				initialTab = 1,
				settingsScope = settingsScope,
				hasBookSettings = hasBookSettings,
				publicationFormat = publicationFormat,
				onSettingsChange = onSettingsChange,
				onSettingsScopeChange = onSettingsScopeChange,
				onResetBookSettings = onResetBookSettings,
				onShowMenus = onShowMenus,
				onHideMenus = onHideMenus,
				onDismissRequest = onDismissDialog
			)
			ReaderControllerDialog.Search -> KomikkuReaderSearchDialog(
				search = controllerState.search,
				onSearchQuery = onSearchQuery,
				onNavigateToSearchResult = onNavigateToSearchResult,
				onDismissSearch = onDismissSearch
			)
			null -> Unit
		}
		controllerState.selectionNoteDraft?.let { draft ->
			KomikkuReaderSelectionNoteDialog(
				draft = draft,
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
