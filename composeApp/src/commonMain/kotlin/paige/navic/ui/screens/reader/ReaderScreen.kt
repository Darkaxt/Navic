package paige.navic.ui.screens.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.reader.ReaderChromeState
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderCoordinator
import paige.navic.reader.ReaderCoordinatorStep
import paige.navic.reader.ReaderEngineCommand
import paige.navic.reader.ReaderEngineEvent
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSettingsScope
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.applyReaderCoordinatorStep
import paige.navic.reader.decodeReaderReadingProgress
import paige.navic.reader.encodeReaderReadingProgress
import paige.navic.reader.ReaderReadingProgressState
import paige.navic.ui.screens.bindery.binderyWhispersyncCompanionProgressForReader
import paige.navic.ui.screens.bindery.binderyWhispersyncCompanionProgressJsonWithUpdate
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.Logger
import kotlin.time.Clock

private const val ReaderScreenTag = "ReaderScreen"

@Composable
fun ReaderScreen(reader: Screen.Reader) {
	val preferenceManager = koinInject<PreferenceManager>()
	val binderyRepository = koinInject<BinderyRepository>()
	val backStack = LocalNavStack.current
	val uriHandler = LocalUriHandler.current
	val coroutineScope = rememberCoroutineScope()
	val hasReaderBookSettings = readerHasBookSettings(preferenceManager, reader.bookId)
	var readerSettingsScope by remember(reader.publicationUrl, reader.bookId) {
		mutableStateOf(readerInitialSettingsScope(hasReaderBookSettings))
	}
	val defaultReaderSettings = remember(
		reader.publicationUrl,
		reader.bookId,
		readerSettingsScope,
		preferenceManager.readerFontFamily,
		preferenceManager.readerFontSource,
		preferenceManager.readerCustomFontFamily,
		preferenceManager.readerCustomFontUrl,
		preferenceManager.readerFontSizePercent,
		preferenceManager.readerLineHeightPercent,
		preferenceManager.readerParagraphSpacingPercent,
		preferenceManager.readerMarginPercent,
		preferenceManager.readerFontWeight,
		preferenceManager.readerLetterSpacing,
		preferenceManager.readerWordSpacing,
		preferenceManager.readerSideMargin,
		preferenceManager.readerTopMargin,
		preferenceManager.readerBottomMargin,
		preferenceManager.readerTextIndent,
		preferenceManager.readerHeadingFontSize,
		preferenceManager.readerDimOverlayPercent,
		preferenceManager.readerColorFilterEnabled,
		preferenceManager.readerColorFilterArgb,
		preferenceManager.readerColorFilterMode,
		preferenceManager.readerGrayscaleEnabled,
		preferenceManager.readerInvertedColors,
		preferenceManager.readerOrientation,
		preferenceManager.readerTheme,
		preferenceManager.readerDirection,
		preferenceManager.readerNavBarType,
		preferenceManager.readerFlowMode,
		preferenceManager.readerPaged,
		preferenceManager.readerTapZone,
		preferenceManager.readerSmallerTapZone,
		preferenceManager.readerShowTapZones,
		preferenceManager.readerPublisherStylesEnabled,
		preferenceManager.readerFullscreen,
		preferenceManager.readerKeepScreenOn,
		preferenceManager.readerReadaloudSyncEnabled,
		preferenceManager.readerVolumeKeyPageTurns,
		preferenceManager.readerWebContentsDebuggingEnabled,
		preferenceManager.readerBookSettingsJson
	) {
		preferenceManager.readerSettingsForScope(
			bookId = reader.bookId,
			scope = readerSettingsScope
		)
	}
	var coordinator by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf(
			ReaderCoordinator(
				controller = ReaderController(
					state = ReaderControllerState(
						chrome = ReaderChromeState(settings = defaultReaderSettings)
					)
				)
			)
		)
	}
	var lastReaderEngineHostEvent by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf<ReaderEngineHostEvent?>(null)
	}
	var readerEngineHostEventKey by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf(0L)
	}
	var readaloudCommand by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf<ReaderReadaloudPlaybackCommand?>(null)
	}
	var readaloudCommandKey by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf(0L)
	}
	val controllerState = coordinator.controller.state
	val settings = controllerState.chrome.settings
	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current
	val readerFocusRequester = remember { FocusRequester() }
	val navigator = remember(settings.tapZone, settings.tapZoneInvertMode, settings.smallerTapZone, settings.flowMode) {
		komikkuNavigatorForReaderSettings(settings)
	}

	fun applyCoordinatorStep(step: ReaderCoordinatorStep) {
		applyReaderCoordinatorStep(
			step = step,
			updateCoordinator = { coordinator = it },
			saveProgress = { progress ->
				val updatedAtMs = Clock.System.now().toEpochMilliseconds()
				val localProgress = progress.copy(updatedAt = updatedAtMs.toString())
				preferenceManager.readerReadingProgressJson = encodeReaderReadingProgress(
					ReaderReadingProgressState(
						decodeReaderReadingProgress(preferenceManager.readerReadingProgressJson)
					).upsert(localProgress).progresses
				)
				binderyWhispersyncCompanionProgressForReader(
					reader = reader,
					progress = localProgress,
					updatedAtMs = updatedAtMs
				)?.let { companionProgress ->
					preferenceManager.binderyWhispersyncCompanionProgressJson =
						binderyWhispersyncCompanionProgressJsonWithUpdate(
							json = preferenceManager.binderyWhispersyncCompanionProgressJson,
							progress = companionProgress
						)
				}
				coroutineScope.launch(Dispatchers.IO) {
					binderyRepository.putReadingProgress(progress).onFailure { error ->
						Logger.w(ReaderScreenTag, "Reader progress save failed", error)
					}
				}
			}
		)
	}

	fun applyReadaloudEngineCommand(command: ReaderEngineCommand) {
		applyCoordinatorStep(coordinator.onReadaloudEngineCommand(command))
	}

	fun handleEngineHostEvent(event: ReaderEngineHostEvent) {
		if (event is ReaderEngineHostEvent.FoliateBridge) {
			lastReaderEngineHostEvent = event
			readerEngineHostEventKey += 1L
		}
		applyCoordinatorStep(coordinator.onEngineHostEvent(event))
	}

	fun applyReaderSettings(nextSettings: ReaderSettings) {
		val normalized = preferenceManager.persistReaderSettingsForScope(
			bookId = reader.bookId,
			scope = readerSettingsScope,
			settings = nextSettings
		)
		applyCoordinatorStep(coordinator.applySettings(normalized))
	}

	fun selectReaderSettingsScope(scope: ReaderSettingsScope) {
		readerSettingsScope = scope
		val nextSettings = preferenceManager.readerSettingsForSelectedScope(
			bookId = reader.bookId,
			currentSettings = settings,
			scope = scope,
			hasBookSettings = hasReaderBookSettings
		)
		applyCoordinatorStep(coordinator.applySettings(nextSettings))
	}

	fun resetReaderBookSettings() {
		readerSettingsScope = ReaderSettingsScope.Global
		applyCoordinatorStep(
			coordinator.applySettings(
				preferenceManager.resetReaderBookSettingsToGlobal(reader.bookId)
			)
		)
	}

	LaunchedEffect(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		readerFocusRequester.requestFocus()
	}

	ReaderPublicationRuntimeHost(
		reader = reader,
		onPublicationReady = { publicationUrl, shellCoverUrl, savedProgress ->
			applyCoordinatorStep(
				coordinator.open(
					reader.toReaderEngineOpenRequest(
						publicationUrl = publicationUrl,
						shellCoverUrl = shellCoverUrl,
						savedProgress = savedProgress,
						settings = settings
					)
				)
			)
		},
		onError = { message ->
			applyCoordinatorStep(
				coordinator.onEngineEvent(
					ReaderEngineEvent.Error(
						message = message,
						code = "publication_runtime"
					)
				)
			)
		}
	)

	ReaderReadaloudRuntimeHost(
		reader = reader,
		readaloudSyncEnabled = settings.readaloudSyncEnabled != false,
		readerHostEvent = lastReaderEngineHostEvent,
		readerHostEventKey = readerEngineHostEventKey,
		onPublicationReady = { publicationUrl ->
			applyCoordinatorStep(
				coordinator.open(
					reader.toReaderEngineOpenRequest(
						publicationUrl = publicationUrl,
						shellCoverUrl = null,
						savedProgress = null,
						settings = settings
					)
				)
			)
		},
		onEngineCommand = { command, _ ->
			applyReadaloudEngineCommand(command)
		},
		playbackCommand = readaloudCommand,
		playbackCommandKey = readaloudCommandKey,
		onPlaybackState = { playbackState ->
			applyCoordinatorStep(coordinator.onReadaloudPlaybackState(playbackState))
		},
		onError = { message ->
			applyCoordinatorStep(
				coordinator.onEngineEvent(
					ReaderEngineEvent.Error(
						message = message,
						code = "readaloud_runtime"
					)
				)
			)
		}
	)

	ReaderOrientationEffect(orientation = settings.orientation)
	ReaderSystemBarsEffect(
		fullscreen = settings.fullscreen != false,
		systemBarsVisible = controllerState.menuVisible || settings.fullscreen == false
	)

	KomikkuReaderRoot(
		reader = reader,
		controllerState = controllerState,
		viewState = coordinator.viewState,
		navigator = navigator,
		settingsScope = readerSettingsScope,
		hasBookSettings = hasReaderBookSettings,
		publicationFormat = reader.publicationFormat,
		onEngineHostEvent = { event -> handleEngineHostEvent(event) },
		onViewerAction = { action -> applyCoordinatorStep(coordinator.onViewerAction(action)) },
		onPreviousChapter = {
			applyCoordinatorStep(coordinator.navigateToPreviousChapter())
		},
		onNextChapter = {
			applyCoordinatorStep(coordinator.navigateToNextChapter())
		},
		onGoToChapterPage = { pageIndex ->
			applyCoordinatorStep(coordinator.navigateToChapterPage(pageIndex))
		},
		onHistoryBack = {
			applyCoordinatorStep(coordinator.navigateHistoryBack())
		},
		onHistoryForward = {
			applyCoordinatorStep(coordinator.navigateHistoryForward())
		},
		onDismissHistory = {
			applyCoordinatorStep(coordinator.dismissHistoryNavigation())
		},
		onContents = {
			applyCoordinatorStep(coordinator.openContentsDialog())
		},
		onSearch = {
			applyCoordinatorStep(coordinator.openSearchDialog())
		},
		onSearchQuery = { query ->
			applyCoordinatorStep(coordinator.search(query))
		},
		onNavigateToSearchResult = { result ->
			val navigateStep = coordinator.navigateToSearchResult(result)
			applyCoordinatorStep(navigateStep)
			applyCoordinatorStep(navigateStep.coordinator.closeDialog())
		},
		onDismissSearch = {
			applyCoordinatorStep(coordinator.closeSearchDialog())
		},
		onNavigateBack = {
			if (backStack.size > 1) {
				backStack.removeLastOrNull()
			}
		},
		onSettings = {
			applyCoordinatorStep(coordinator.openSettingsDialog())
		},
		onShowMenus = {
			applyCoordinatorStep(coordinator.showMenus())
		},
		onHideMenus = {
			applyCoordinatorStep(coordinator.hideMenus())
		},
		onNavigateToTocItem = { tocItem ->
			tocItem.href?.let { href ->
				val navigateStep = coordinator.navigateTo(ReaderLocator(href = href))
				applyCoordinatorStep(navigateStep)
				applyCoordinatorStep(navigateStep.coordinator.closeDialog())
			}
		},
		onToggleCurrentBookmark = {
			applyCoordinatorStep(coordinator.toggleCurrentBookmark())
		},
		onHighlightSelection = {
			applyCoordinatorStep(coordinator.addSelectionHighlight())
		},
		onCopySelection = { text ->
			clipboard.setText(AnnotatedString(text))
			Logger.i(ReaderScreenTag, "Reader selection copied length=${text.length}")
		},
		onStartSelectionNote = {
			applyCoordinatorStep(coordinator.startSelectionNote())
		},
		onSaveSelectionNote = { note ->
			applyCoordinatorStep(coordinator.saveSelectionNote(note))
		},
		onDismissSelectionNote = {
			applyCoordinatorStep(coordinator.dismissSelectionNote())
		},
		onDismissAnnotationPopup = {
			applyCoordinatorStep(coordinator.dismissAnnotationPopup())
		},
		onDismissFootnotePopup = {
			applyCoordinatorStep(coordinator.dismissFootnotePopup())
		},
		onOpenExternalLink = { url ->
			uriHandler.openUri(url)
			applyCoordinatorStep(coordinator.dismissExternalLinkPrompt())
		},
		onDismissExternalLinkPrompt = {
			applyCoordinatorStep(coordinator.dismissExternalLinkPrompt())
		},
		onSettingsChange = { settings ->
			applyReaderSettings(settings)
		},
		onSettingsScopeChange = { scope ->
			selectReaderSettingsScope(scope)
		},
		onResetBookSettings = {
			resetReaderBookSettings()
		},
		onDismissDialog = {
			applyCoordinatorStep(coordinator.closeDialog())
		},
		modifier = Modifier
			.fillMaxSize()
			.focusRequester(readerFocusRequester)
			.focusable()
			.onPreviewKeyEvent { event ->
				if (settings.volumeKeyPageTurns != true || event.type != KeyEventType.KeyDown) {
					return@onPreviewKeyEvent false
				}
				when (event.key) {
					Key.VolumeUp -> {
						applyCoordinatorStep(
							coordinator.onViewerAction(
								ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
							)
						)
						true
					}
					Key.VolumeDown -> {
						applyCoordinatorStep(
							coordinator.onViewerAction(
								ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
							)
						)
						true
					}
					else -> false
				}
			}
	)
}
