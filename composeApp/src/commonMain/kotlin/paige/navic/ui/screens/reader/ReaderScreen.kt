package paige.navic.ui.screens.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.reader.ReaderChromeState
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderCoordinator
import paige.navic.reader.ReaderCoordinatorBackStep
import paige.navic.reader.ReaderCoordinatorStep
import paige.navic.reader.ReaderEngineCommand
import paige.navic.reader.ReaderEngineEvent
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSettingsScope
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.applyReaderCoordinatorStep
import paige.navic.reader.decodeReaderReadingProgress
import paige.navic.reader.encodeReaderReadingProgress
import paige.navic.reader.persistReaderMarksIfChanged
import paige.navic.reader.readerAnnotationState
import paige.navic.reader.readerBookmarkState
import paige.navic.reader.readerWhispersyncPlaybackCommandForSeekTarget
import paige.navic.reader.readerWhispersyncPlaybackCommandsForUserRequest
import paige.navic.reader.readerWhispersyncShouldPausePlaybackOnReaderExit
import paige.navic.reader.ReaderReadingProgressState
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.ui.core.AudiobookMiniPlayerUiState
import paige.navic.ui.screens.bindery.binderyAudiobookPlaybackPlan
import paige.navic.ui.screens.bindery.binderyAudiobookResumeProgressForWhispersyncReader
import paige.navic.ui.screens.bindery.binderyWhispersyncCompanionProgressForReader
import paige.navic.ui.screens.bindery.binderyWhispersyncCompanionProgressJsonWithUpdate
import paige.navic.ui.navigation.Screen
import paige.navic.ui.navigation.performNavicBack
import paige.navic.util.core.Logger
import kotlin.time.Clock

private const val ReaderScreenTag = "ReaderScreen"

@Composable
fun ReaderScreen(reader: Screen.Reader) {
	val preferenceManager = koinInject<PreferenceManager>()
	val binderyRepository = koinInject<BinderyRepository>()
	val audiobookPlaybackManager = koinInject<AudiobookPlaybackManager>()
	val audiobookMiniPlayerState by audiobookPlaybackManager.uiState.collectAsState()
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
						chrome = ReaderChromeState(settings = defaultReaderSettings),
						annotations = preferenceManager.readerAnnotationState(),
						bookmarks = preferenceManager.readerBookmarkState()
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
	val whispersyncAudiobookIdentity = reader.whispersyncAudiobookId
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: reader.whispersyncAudiobookBookFileId?.trim()?.takeIf { it.isNotEmpty() }
	var whispersyncPlaybackPlan by remember(
		reader.bookId,
		reader.resourceHref,
		reader.publicationUrl,
		whispersyncAudiobookIdentity
	) {
		mutableStateOf<ReadaloudPlaybackPlan?>(null)
	}
	val controllerState = coordinator.controller.state
	val settings = controllerState.chrome.settings
	val readaloudSyncEnabled = settings.readaloudSyncEnabled != false
	val whispersyncReadaloudPlaybackState = audiobookMiniPlayerState.toWhispersyncReadaloudPlaybackUiState(
		playbackPlan = whispersyncPlaybackPlan,
		bookId = reader.bookId,
		versionRowId = whispersyncAudiobookIdentity,
		syncEnabled = readaloudSyncEnabled
	)
	val latestWhispersyncPlaybackPlan by rememberUpdatedState(whispersyncPlaybackPlan)
	val latestWhispersyncReadaloudPlaybackState by rememberUpdatedState(whispersyncReadaloudPlaybackState)
	var whispersyncExitPauseDispatched by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf(false)
	}
	var whispersyncPlaybackStartedFromReader by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf(false)
	}
	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current
	val readerFocusRequester = remember { FocusRequester() }
	val navigator = remember(settings.tapZone, settings.tapZoneInvertMode, settings.smallerTapZone, settings.flowMode) {
		komikkuNavigatorForReaderSettings(settings)
	}

	fun applyCoordinatorStep(step: ReaderCoordinatorStep) {
		val previousControllerState = coordinator.controller.state
		applyReaderCoordinatorStep(
			step = step,
			updateCoordinator = { nextCoordinator ->
				preferenceManager.persistReaderMarksIfChanged(
					previous = previousControllerState,
					next = nextCoordinator.controller.state
				)
				coordinator = nextCoordinator
			},
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
					updatedAtMs = updatedAtMs,
					audioSeekTarget = step.whispersyncAudioSeekTarget ?: coordinator.controller.state.whispersync.audioSeekTarget
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
		step.whispersyncActiveSegment?.let { segment ->
			Logger.i(
				ReaderScreenTag,
				"Whispersync activeSegment audio=${segment.audioResource} " +
					"trackIndex=${segment.audioTrackIndex?.toString().orEmpty()} " +
					"positionMs=${segment.positionMs} text=${segment.textHref}:" +
					"${segment.textStart?.toString().orEmpty()}-${segment.textEnd?.toString().orEmpty()} " +
					"fragment=${segment.fragmentId.orEmpty()} ApplyMediaOverlay=${segment.applyMediaOverlay}"
			)
		}
		step.whispersyncAudioSeekTarget?.let { target ->
			val command = readerWhispersyncPlaybackCommandForSeekTarget(
				playbackPlan = whispersyncPlaybackPlan,
				seekTarget = target
			)
			if (command != null) {
				Logger.i(
					ReaderScreenTag,
					"Whispersync audiobook seek audio=${target.audioResource} positionMs=${target.positionMs}"
				)
				audiobookPlaybackManager.dispatch(command)
			} else {
				Logger.w(
					ReaderScreenTag,
					"Whispersync audiobook seek ignored; no playback plan match for audio=${target.audioResource}"
				)
			}
		}
		step.whispersyncPlaybackCommand?.let { playbackCommand ->
			// Dispatched after the seek block so resume-on-page-turn is seek-then-play.
			audiobookPlaybackManager.dispatch(playbackCommand)
		}
	}

	fun pauseWhispersyncAudiobookOnReaderExit(reason: String) {
		if (whispersyncExitPauseDispatched) return
		if (
			readerWhispersyncShouldPausePlaybackOnReaderExit(
				playbackPlanAvailable = latestWhispersyncPlaybackPlan != null,
				playbackState = latestWhispersyncReadaloudPlaybackState,
				playbackStartedFromReader = whispersyncPlaybackStartedFromReader
			)
		) {
			whispersyncExitPauseDispatched = true
			whispersyncPlaybackStartedFromReader = false
			Logger.i(ReaderScreenTag, "Pausing Whispersync audiobook on reader exit reason=$reason")
			audiobookPlaybackManager.dispatch(ReaderReadaloudPlaybackCommand.Pause)
		}
	}

	fun applyReaderBackStep(step: ReaderCoordinatorBackStep) {
		if (step.handled) {
			if (
				!coordinator.controller.state.shellCoverVisible &&
				step.coordinator.controller.state.shellCoverVisible
			) {
				pauseWhispersyncAudiobookOnReaderExit("back-to-shell-cover")
			}
			coordinator = step.coordinator
		} else {
			pauseWhispersyncAudiobookOnReaderExit("app-navigation")
			backStack.performNavicBack()
		}
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

	DisposableEffect(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		onDispose {
			pauseWhispersyncAudiobookOnReaderExit("dispose")
		}
	}

	LaunchedEffect(
		audiobookMiniPlayerState,
		whispersyncPlaybackPlan,
		reader.bookId,
		reader.whispersyncAudiobookId,
		settings.readaloudSyncEnabled
	) {
		whispersyncReadaloudPlaybackState?.let { playbackState ->
			applyCoordinatorStep(coordinator.onReadaloudPlaybackState(playbackState))
		}
	}

	ReaderPublicationRuntimeHost(
		reader = reader,
		onPublicationReady = { publicationUrl, shellCoverUrl, savedProgress ->
			val localStartLocator = ReaderReadingProgressState(
				decodeReaderReadingProgress(preferenceManager.readerReadingProgressJson)
			).startLocatorFor(
				bookId = reader.bookId,
				resourceHref = reader.resourceHref,
				kind = reader.kind
			)
			applyCoordinatorStep(
				coordinator.open(
					reader.toReaderEngineOpenRequest(
						publicationUrl = publicationUrl,
						shellCoverUrl = shellCoverUrl,
						savedProgress = savedProgress,
						localStartLocator = localStartLocator,
						settings = settings
					)
				)
			)
			reader.whispersyncLaunchAttachment()?.let { attachment ->
				coroutineScope.launch {
					val sidecar = withContext(Dispatchers.IO) {
						binderyRepository.getWhispersyncSidecar(attachment.sidecarPath)
					}.fold(
						onSuccess = { sidecar ->
							Logger.i(
								ReaderScreenTag,
								"Whispersync sidecar loaded artifact=${attachment.artifactId} " +
									"audiobook=${attachment.audiobookId} " +
									"bookFile=${attachment.audiobookBookFileId} " +
									"segments=${sidecar.timeline.segments.size}"
							)
							applyCoordinatorStep(coordinator.loadWhispersyncSidecar(sidecar))
							sidecar
						},
						onFailure = { error ->
							applyCoordinatorStep(
								coordinator.reportWhispersyncLoadFailure(
									label = "Whispersync unavailable",
									detail = "artifact=${attachment.artifactId}"
								)
							)
							Logger.w(
								ReaderScreenTag,
								"Whispersync sidecar load failed artifact=${attachment.artifactId} " +
									"path=${attachment.sidecarPath}",
								error
							)
							null
						}
					)
					withContext(Dispatchers.IO) {
						binderyRepository.getWhispersyncAudiobookManifest(
							bookId = reader.bookId,
							audiobookId = attachment.audiobookId,
							audiobookBookFileId = attachment.audiobookBookFileId,
							audiobookManifestHref = sidecar?.audiobookManifestHref
						)
					}.fold(
						onSuccess = { manifest ->
							val requestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
							val resumeProgress = binderyAudiobookResumeProgressForWhispersyncReader(
								audiobookProgressJson = preferenceManager.binderyAudiobookProgressJson,
								companionProgressJson = preferenceManager.binderyWhispersyncCompanionProgressJson,
								bookId = reader.bookId,
								versionRowId = whispersyncAudiobookIdentity ?: attachment.audiobookBookFileId,
								manifest = manifest
							)
							val playbackPlan = binderyAudiobookPlaybackPlan(
								manifest = manifest,
								versionRowId = whispersyncAudiobookIdentity ?: attachment.audiobookBookFileId,
								opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
								requestHeaders = requestHeaders,
								resumeProgress = resumeProgress,
								progressBookId = reader.bookId
							)
							whispersyncPlaybackPlan = playbackPlan
							audiobookPlaybackManager.load(
								playbackPlan = playbackPlan,
								bookId = reader.bookId,
								bookTitle = attachment.audiobookTitle ?: reader.title,
								versionRowId = whispersyncAudiobookIdentity ?: attachment.audiobookBookFileId,
								coverUrl = null,
								coverCacheKey = null,
								imageRequestHeaders = requestHeaders,
								playWhenReady = false
							)
							Logger.i(
								ReaderScreenTag,
								"Whispersync audiobook plan loaded audiobook=${whispersyncAudiobookIdentity ?: attachment.audiobookBookFileId} " +
									"items=${playbackPlan.mediaItems.size}"
							)
						},
						onFailure = { error ->
							whispersyncPlaybackPlan = null
							applyCoordinatorStep(
								coordinator.reportWhispersyncLoadFailure(
									label = "Whispersync audio unavailable",
									detail = "audiobook=${whispersyncAudiobookIdentity ?: attachment.audiobookBookFileId}"
								)
							)
							Logger.w(
								ReaderScreenTag,
								"Whispersync audiobook plan load failed audiobook=${whispersyncAudiobookIdentity ?: attachment.audiobookBookFileId}",
								error
							)
						}
					)
				}
			}
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
	NavigationBackHandler(
		state = rememberNavigationEventState(NavigationEventInfo.None),
		isBackEnabled = true,
		onBackCompleted = {
			applyReaderBackStep(coordinator.onBack())
		}
	)

	KomikkuReaderRoot(
		reader = reader,
		controllerState = controllerState,
		viewState = coordinator.viewState,
		navigator = navigator,
		settingsScope = readerSettingsScope,
		hasBookSettings = hasReaderBookSettings,
		publicationFormat = reader.publicationFormat,
		readaloudPlaybackState = whispersyncReadaloudPlaybackState,
		onEngineHostEvent = { event -> handleEngineHostEvent(event) },
		onViewerAction = { action ->
			val beforeMenuVisible = coordinator.controller.state.menuVisible
			val step = coordinator.onViewerAction(action)
			Logger.i(
				ReaderScreenTag,
				"Reader viewer action=$action menuVisible=$beforeMenuVisible->${step.coordinator.controller.state.menuVisible} " +
					"shellCover=${coordinator.controller.state.shellCoverVisible}->${step.coordinator.controller.state.shellCoverVisible}"
			)
			applyCoordinatorStep(step)
		},
		onWhispersyncPlaybackCommand = { command ->
			val playbackCommands = readerWhispersyncPlaybackCommandsForUserRequest(
				playbackPlan = whispersyncPlaybackPlan,
				session = coordinator.controller.state.whispersync,
				command = command
			)
			when (command) {
				ReaderReadaloudPlaybackCommand.Play -> whispersyncPlaybackStartedFromReader = true
				ReaderReadaloudPlaybackCommand.Pause -> whispersyncPlaybackStartedFromReader = false
				else -> Unit
			}
			val visibleTextRange = coordinator.controller.state.whispersync.visibleTextRange
			playbackCommands.forEach { playbackCommand ->
				if (command is ReaderReadaloudPlaybackCommand.Play && playbackCommand is ReaderReadaloudPlaybackCommand.SeekToTrack) {
					val item = whispersyncPlaybackPlan?.mediaItems?.getOrNull(playbackCommand.trackIndex)
					Logger.i(
						ReaderScreenTag,
						"Whispersync play preseek trackIndex=${playbackCommand.trackIndex} " +
							"positionMs=${playbackCommand.positionMs} " +
							"audio=${item?.resourceKey ?: item?.uri ?: item?.mediaId.orEmpty()} " +
							"visibleTextRange=${visibleTextRange?.textHref.orEmpty()}:" +
							"${visibleTextRange?.visibleStart?.toString().orEmpty()}-" +
							"${visibleTextRange?.visibleEnd?.toString().orEmpty()}"
					)
				}
				audiobookPlaybackManager.dispatch(playbackCommand)
			}
		},
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
		onWhispersyncPlayer = {
			applyCoordinatorStep(coordinator.openWhispersyncPlayerDialog())
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
			applyReaderBackStep(coordinator.onNavigateBack())
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
		onNavigateToBookmark = { bookmark ->
			applyCoordinatorStep(coordinator.navigateToBookmark(bookmark))
		},
		onNavigateToAnnotation = { annotation ->
			applyCoordinatorStep(coordinator.navigateToAnnotation(annotation))
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
			applyCoordinatorStep(coordinator.dismissSelectionActions())
		},
		onStartSelectionNote = {
			applyCoordinatorStep(coordinator.startSelectionNote())
		},
		onSaveSelectionNote = { note ->
			Logger.i(ReaderScreenTag, "Reader selection note save length=${note.length}")
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
		onRepairWhispersyncMismatch = {
			applyCoordinatorStep(coordinator.repairWhispersyncMismatch())
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

private fun AudiobookMiniPlayerUiState.toWhispersyncReadaloudPlaybackUiState(
	playbackPlan: ReadaloudPlaybackPlan?,
	bookId: String,
	versionRowId: String?,
	syncEnabled: Boolean
): ReaderReadaloudPlaybackUiState? {
	if (playbackPlan == null || !isAvailable) return null
	if (this.bookId != bookId) return null
	if (versionRowId != null && this.versionRowId != versionRowId) return null
	val item = playbackPlan.mediaItems.getOrNull(trackIndex)
		?: mediaId?.let { id -> playbackPlan.mediaItems.firstOrNull { item -> item.mediaId == id } }
	val audioResource = item?.resourceKey ?: item?.uri ?: mediaId ?: return null
	return ReaderReadaloudPlaybackUiState(
		isAvailable = isAvailable,
		isPlaying = isPlaying,
		trackIndex = trackIndex,
		audioResource = audioResource,
		positionMs = positionMs,
		durationMs = durationMs,
		playbackSpeed = playbackSpeed,
		activeAudioMetadata = activeAudioMetadata,
		syncEnabled = syncEnabled
	)
}
