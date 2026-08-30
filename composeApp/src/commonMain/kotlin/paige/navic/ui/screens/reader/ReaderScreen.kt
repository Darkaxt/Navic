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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalNavStack
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.data.remote.bindery.binderyApiKeyHeaders
import paige.navic.reader.ReaderChromeState
import paige.navic.reader.ReaderController
import paige.navic.reader.ReaderControllerState
import paige.navic.reader.ReaderCoordinator
import paige.navic.reader.ReaderCoordinatorBackStep
import paige.navic.reader.ReaderCoordinatorStep
import paige.navic.reader.ReaderEngineCommand
import paige.navic.reader.ReaderEngineEvent
import paige.navic.reader.ReaderEngineHostEvent
import paige.navic.reader.ReaderEngineOpenRequest
import paige.navic.reader.ReaderEngineViewState
import paige.navic.reader.ReaderListeningSettings
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderProcessStateViewModel
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderReadaloudReaderInteraction
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSettingsScope
import paige.navic.reader.ReaderViewerAction
import paige.navic.reader.ReaderWhispersyncCueMapHoldOutcome
import paige.navic.reader.ReaderWhispersyncStatusMessage
import paige.navic.reader.ReaderWordSyncBoundaryCancellation
import paige.navic.reader.ReaderWordSyncBoundaryDispatch
import paige.navic.reader.ReaderWordSyncBoundaryScheduler
import paige.navic.reader.ReaderWordSyncEffect
import paige.navic.reader.ReaderWordSyncPlaybackIdentity
import paige.navic.reader.ReaderWordSyncTimelineSnapshot
import paige.navic.reader.WordSyncPublicationVerificationSession
import paige.navic.reader.WordSyncPublicationVerifier
import paige.navic.reader.restoreProcessState
import paige.navic.reader.whispersyncPlaybackCommandLogValue
import paige.navic.reader.ReadaloudPlaybackPlan
import paige.navic.reader.WhispersyncSyncLogTag
import paige.navic.reader.acceptsWordSyncGeneration
import paige.navic.reader.applyReaderCoordinatorStep
import paige.navic.reader.configureWordSync
import paige.navic.reader.decodeReaderReadingProgress
import paige.navic.reader.encodeReaderReadingProgress
import paige.navic.reader.onReadaloudPlaybackState
import paige.navic.reader.onWordSyncBoundary
import paige.navic.reader.onWordSyncClear
import paige.navic.reader.onWordSyncChapterFailed
import paige.navic.reader.onWordSyncChapterVerified
import paige.navic.reader.onWordSyncIndexFailed
import paige.navic.reader.onWordSyncIndexVerified
import paige.navic.reader.persistReaderMarksIfChanged
import paige.navic.reader.normalizedReaderListeningSettings
import paige.navic.reader.readerAnnotationState
import paige.navic.reader.readerBookmarkState
import paige.navic.reader.readerListeningSettings
import paige.navic.reader.readerWhispersyncPlaybackCommandForSeekTarget
import paige.navic.reader.ReaderReadingProgressState
import paige.navic.reader.setReaderListeningSettings
import paige.navic.reader.withReaderListeningSettings
import paige.navic.reader.whispersyncLogValue
import paige.navic.reader.wordSyncBoundaries
import paige.navic.shared.AudiobookPlaybackManager
import paige.navic.shared.AudiobookPlaybackTimelineSnapshot
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
	val processStateViewModel = koinViewModel<ReaderProcessStateViewModel>(
		key = reader.readerProcessStateViewModelKey()
	)
	val audiobookMiniPlayerState by audiobookPlaybackManager.uiState.collectAsState()
	val playbackTimelineRevision by
		audiobookPlaybackManager.playbackTimelineRevision.collectAsState()
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
		preferenceManager.readerWhispersyncHighlightLeadMs,
		preferenceManager.readerVolumeKeyPageTurns,
		preferenceManager.readerWebContentsDebuggingEnabled,
		preferenceManager.readerBookSettingsJson
	) {
		preferenceManager.readerSettingsForScope(
			bookId = reader.bookId,
			scope = readerSettingsScope
		)
	}
	var listeningSettings by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf(preferenceManager.readerListeningSettings())
	}
	var coordinator by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf(
			ReaderCoordinator(
				controller = ReaderController(
					state = ReaderControllerState(
						chrome = ReaderChromeState(settings = defaultReaderSettings.withReaderListeningSettings(listeningSettings)),
						annotations = preferenceManager.readerAnnotationState(),
						bookmarks = preferenceManager.readerBookmarkState()
					)
				)
			)
		)
	}
	var lastReadaloudReaderInteraction by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf<ReaderReadaloudReaderInteraction?>(null)
	}
	var readaloudReaderInteractionKey by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
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
	var wordSyncPublicationVerifier by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf<WordSyncPublicationVerifier?>(null)
	}
	var wordSyncVerificationSession by remember(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		mutableStateOf<WordSyncPublicationVerificationSession?>(null)
	}
	val controllerState = coordinator.controller.state
	val settings = controllerState.chrome.settings
	val runtimeSettings = settings.withReaderListeningSettings(listeningSettings)
	val readaloudSyncEnabled = runtimeSettings.readaloudSyncEnabled != false
	val whispersyncReadaloudPlaybackState = audiobookMiniPlayerState.toWhispersyncReadaloudPlaybackUiState(
		playbackPlan = whispersyncPlaybackPlan,
		bookId = reader.bookId,
		versionRowId = whispersyncAudiobookIdentity,
		syncEnabled = readaloudSyncEnabled
	)
	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current
	val readerFocusRequester = remember { FocusRequester() }
	val navigator = remember(settings.tapZone, settings.tapZoneInvertMode, settings.smallerTapZone, settings.flowMode) {
		komikkuNavigatorForReaderSettings(settings)
	}

	fun applyCoordinatorStep(
		step: ReaderCoordinatorStep,
		retainProcessState: Boolean = true
	) {
		val previousControllerState = coordinator.controller.state
		applyReaderCoordinatorStep(
			step = step,
			updateCoordinator = { nextCoordinator ->
				preferenceManager.persistReaderMarksIfChanged(
					previous = previousControllerState,
					next = nextCoordinator.controller.state
				)
				coordinator = nextCoordinator
				if (retainProcessState) {
					processStateViewModel.retain(nextCoordinator.controller.state)
				}
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
					audioSeekTarget = step.whispersyncAudioSeekTarget
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
		step.readaloudReaderInteraction?.let { interaction ->
			lastReadaloudReaderInteraction = interaction
			readaloudReaderInteractionKey += 1L
		}
		step.whispersyncAudioSeekTarget?.let { target ->
			val command = readerWhispersyncPlaybackCommandForSeekTarget(
				playbackPlan = whispersyncPlaybackPlan,
				seekTarget = target
			)
			if (command != null) {
				Logger.i(
					WhispersyncSyncLogTag,
					"Whispersync audio seek state=dispatch matched=true active=true " +
						"command=${command::class.simpleName ?: "unknown"}"
				)
				audiobookPlaybackManager.dispatch(command)
			} else {
				Logger.w(
					WhispersyncSyncLogTag,
					"Whispersync audio seek state=ignored matched=false active=false " +
						"command=none reason=no-playback-plan-match"
				)
			}
		}
		step.readaloudPlaybackCommand?.let { command ->
			Logger.i(
				WhispersyncSyncLogTag,
				"Whispersync playback command source=controller " +
					"command=${command.whispersyncPlaybackCommandLogValue()}"
			)
			audiobookPlaybackManager.dispatch(command)
		}
		step.wordSyncEffects.forEach { effect ->
			when (effect) {
				is ReaderWordSyncEffect.LoadIndex -> {
					val verifier = wordSyncPublicationVerifier
					if (verifier == null) {
						applyCoordinatorStep(coordinator.onWordSyncIndexFailed(effect.generation))
					} else {
						coroutineScope.launch {
							val result = withContext(Dispatchers.IO) {
								binderyRepository.getWordSyncIndex(
									identity = effect.reference.identity,
									discovery = effect.reference.discovery
								).mapCatching { index -> index to verifier.verify(index) }
							}
							result.fold(
								onSuccess = { (index, session) ->
									if (coordinator.acceptsWordSyncGeneration(effect.generation)) {
										wordSyncVerificationSession = session
										applyCoordinatorStep(
											coordinator.onWordSyncIndexVerified(
												generation = effect.generation,
												index = index,
												provenance = session.provenance
											)
										)
									}
								},
								onFailure = {
									if (coordinator.acceptsWordSyncGeneration(effect.generation)) {
										wordSyncVerificationSession = null
										applyCoordinatorStep(coordinator.onWordSyncIndexFailed(effect.generation))
										Logger.w(ReaderScreenTag, "WordSync index load or verification failed")
									}
								}
							)
						}
					}
				}
				is ReaderWordSyncEffect.LoadChapter -> {
					val session = wordSyncVerificationSession
					if (session == null) {
						applyCoordinatorStep(
							coordinator.onWordSyncChapterFailed(effect.generation, effect.summary.chapterKey)
						)
					} else {
						coroutineScope.launch {
							val result = withContext(Dispatchers.IO) {
								binderyRepository.getWordSyncChapter(
									identity = effect.identity,
									chapter = effect.summary
								).mapCatching { chapter ->
									session.verifyChapter(chapter)
									chapter
								}
							}
							result.fold(
								onSuccess = { chapter ->
									if (coordinator.acceptsWordSyncGeneration(effect.generation)) {
										applyCoordinatorStep(
											coordinator.onWordSyncChapterVerified(effect.generation, chapter)
										)
									}
								},
								onFailure = {
									if (coordinator.acceptsWordSyncGeneration(effect.generation)) {
										applyCoordinatorStep(
											coordinator.onWordSyncChapterFailed(
												effect.generation,
												effect.summary.chapterKey
											)
										)
										Logger.w(ReaderScreenTag, "WordSync chapter load or verification failed")
									}
								}
							)
						}
					}
				}
			}
		}
	}

	val currentWhispersyncPlaybackPlan = rememberUpdatedState(whispersyncPlaybackPlan)
	val currentWordSyncBoundaryHandler =
		rememberUpdatedState<(ReaderWordSyncBoundaryDispatch) -> Unit> { dispatch ->
			val previousCommandKey =
				(coordinator.viewState as? ReaderEngineViewState.WebViewPublication)?.commandKey
			val step = coordinator.onWordSyncBoundary(dispatch)
			val nextCommandKey =
				(step.coordinator.viewState as? ReaderEngineViewState.WebViewPublication)?.commandKey
			val published = nextCommandKey != null && nextCommandKey != previousCommandKey
			Logger.i(
				WhispersyncSyncLogTag,
				"WordSync boundary state=dispatch active=$published " +
					"command=${if (published) "update-overlay" else "none"} " +
					"mode=word-exact count=${dispatch.coalescedCount}"
			)
			applyCoordinatorStep(step)
		}
	val currentWordSyncClearHandler =
		rememberUpdatedState<(ReaderWordSyncTimelineSnapshot) -> Unit> { timeline ->
			applyCoordinatorStep(coordinator.onWordSyncClear(timeline))
		}
	val wordSyncBoundaryScheduler = remember(
		reader.bookId,
		reader.resourceHref,
		reader.publicationUrl,
		audiobookPlaybackManager,
		coroutineScope
	) {
		ReaderWordSyncBoundaryScheduler(
			currentTimeline = {
				audiobookPlaybackManager.currentPlaybackTimelineSnapshot()
					?.toReaderWordSyncTimelineSnapshot(currentWhispersyncPlaybackPlan.value)
			},
			schedule = { delayMs, action ->
				val job = coroutineScope.launch {
					delay(delayMs)
					action()
				}
				ReaderWordSyncBoundaryCancellation(job::cancel)
			},
			onBoundary = { dispatch -> currentWordSyncBoundaryHandler.value(dispatch) },
			onClear = { timeline -> currentWordSyncClearHandler.value(timeline) }
		)
	}
	val currentPlaybackTimelineSnapshot =
		audiobookPlaybackManager.currentPlaybackTimelineSnapshot()
	val currentWordSyncTimeline = currentPlaybackTimelineSnapshot
		?.toReaderWordSyncTimelineSnapshot(whispersyncPlaybackPlan)
	val currentWordSyncTimelineReason =
		currentPlaybackTimelineSnapshot.wordSyncTimelineLogReason(whispersyncPlaybackPlan)
	val scheduledWordSyncBoundaries = coordinator.wordSyncBoundaries(
		currentWordSyncTimeline?.toWordSyncPlaybackIdentity()
	)

	LaunchedEffect(wordSyncBoundaryScheduler, scheduledWordSyncBoundaries) {
		Logger.i(
			WhispersyncSyncLogTag,
			"WordSync boundary state=timeline matched=${currentWordSyncTimeline != null} " +
				"active=${scheduledWordSyncBoundaries.isNotEmpty()} " +
				"count=${scheduledWordSyncBoundaries.size}"
		)
		wordSyncBoundaryScheduler.replaceTimeline(scheduledWordSyncBoundaries)
	}
	LaunchedEffect(wordSyncBoundaryScheduler, playbackTimelineRevision) {
		Logger.i(
			WhispersyncSyncLogTag,
			"WordSync boundary state=refresh matched=${currentWordSyncTimeline != null} " +
				"active=${scheduledWordSyncBoundaries.isNotEmpty()} " +
				"reason=$currentWordSyncTimelineReason count=${scheduledWordSyncBoundaries.size}"
		)
		wordSyncBoundaryScheduler.refreshTimeline()
	}
	DisposableEffect(wordSyncBoundaryScheduler) {
		onDispose(wordSyncBoundaryScheduler::stop)
	}

	fun openReaderPublication(request: ReaderEngineOpenRequest) {
		request.startLocatorConflict?.let { conflict ->
			Logger.i(
				ReaderScreenTag,
				"Reader progress conflict selected=${conflict.selectedSource} policy=${conflict.policy} " +
					"remoteProgress=${conflict.remoteCandidate.locator.progress} " +
					"remoteUpdatedAt=${conflict.remoteCandidate.updatedAt} " +
					"localProgress=${conflict.localCandidate.locator.progress} " +
					"localUpdatedAt=${conflict.localCandidate.updatedAt}"
			)
		}
		val retainedState = processStateViewModel.restore(request.publication)
		applyCoordinatorStep(
			step = coordinator.dispatch { open(request) },
			retainProcessState = false
		)
		retainedState?.let { snapshot ->
			applyCoordinatorStep(coordinator.dispatch { restoreProcessState(snapshot) })
		}
	}

	fun applyReaderBackStep(step: ReaderCoordinatorBackStep) {
		if (step.handled) {
			coordinator = step.coordinator
			processStateViewModel.retain(step.coordinator.controller.state)
			step.readaloudPlaybackCommand?.let { command ->
				Logger.i(
					WhispersyncSyncLogTag,
					"Whispersync playback command source=back " +
						"command=${command.whispersyncPlaybackCommandLogValue()}"
				)
				audiobookPlaybackManager.dispatch(command)
			}
		} else {
			processStateViewModel.clear()
			backStack.performNavicBack()
		}
	}

	fun applyReadaloudEngineCommand(command: ReaderEngineCommand) {
		applyCoordinatorStep(coordinator.onReadaloudEngineCommand(command))
	}

	fun handleEngineHostEvent(event: ReaderEngineHostEvent) {
		applyCoordinatorStep(coordinator.onEngineHostEvent(event))
	}

	fun applyReaderSettings(nextSettings: ReaderSettings) {
		val normalized = preferenceManager.persistReaderSettingsForScope(
			bookId = reader.bookId,
			scope = readerSettingsScope,
			settings = nextSettings
		)
		applyCoordinatorStep(coordinator.dispatch { applySettings(normalized) })
	}

	fun applyReaderListeningSettings(nextSettings: ReaderListeningSettings) {
		val normalized = nextSettings.normalizedReaderListeningSettings()
		listeningSettings = normalized
		preferenceManager.setReaderListeningSettings(normalized)
		applyCoordinatorStep(coordinator.dispatch { applySettings(settings.withReaderListeningSettings(normalized)) })
		audiobookPlaybackManager.dispatch(ReaderReadaloudPlaybackCommand.SetSpeed(normalized.playbackSpeed))
		if (!normalized.listeningEnabled) {
			Logger.i(
				WhispersyncSyncLogTag,
				"Whispersync playback command source=listening-settings command=${ReaderReadaloudPlaybackCommand.StopAndReset}"
			)
			audiobookPlaybackManager.dispatch(ReaderReadaloudPlaybackCommand.StopAndReset)
		}
	}

	fun selectReaderSettingsScope(scope: ReaderSettingsScope) {
		readerSettingsScope = scope
		val nextSettings = preferenceManager.readerSettingsForSelectedScope(
			bookId = reader.bookId,
			currentSettings = settings,
			scope = scope,
			hasBookSettings = hasReaderBookSettings
		)
		applyCoordinatorStep(coordinator.dispatch { applySettings(nextSettings) })
	}

	fun resetReaderBookSettings() {
		readerSettingsScope = ReaderSettingsScope.Global
		applyCoordinatorStep(
			coordinator.dispatch { applySettings(
				preferenceManager.resetReaderBookSettingsToGlobal(reader.bookId)
			) }
		)
	}

	LaunchedEffect(reader.bookId, reader.resourceHref, reader.publicationUrl) {
		readerFocusRequester.requestFocus()
	}

	LaunchedEffect(
		audiobookMiniPlayerState,
		playbackTimelineRevision,
		whispersyncPlaybackPlan,
		wordSyncPublicationVerifier,
		reader.bookId,
		reader.whispersyncAudiobookId,
		readaloudSyncEnabled
	) {
		whispersyncReadaloudPlaybackState?.let { playbackState ->
			val playbackIdentity = playbackState.toWordSyncPlaybackIdentity(
				whispersyncPlaybackPlan
			)
			applyCoordinatorStep(
				coordinator.onReadaloudPlaybackState(
					playbackState = playbackState,
					playbackIdentity = playbackIdentity,
					publishOverlayProgress = !coordinator.hasExactWordSyncBoundaryPresentation(
						playbackIdentity
					)
				)
			)
		}
	}

	LaunchedEffect(readaloudSyncEnabled, whispersyncPlaybackPlan, whispersyncAudiobookIdentity) {
		if (!readaloudSyncEnabled && whispersyncPlaybackPlan != null) {
			Logger.i(
				WhispersyncSyncLogTag,
				"Whispersync playback command source=sync-disabled command=${ReaderReadaloudPlaybackCommand.StopAndReset}"
			)
			audiobookPlaybackManager.dispatch(ReaderReadaloudPlaybackCommand.StopAndReset)
		}
	}

	ReaderPublicationRuntimeHost(
		reader = reader,
		onPublicationReady = {
				publicationUrl,
				shellCoverUrl,
				shellCoverTint,
				savedProgress,
				wordSyncVerifier ->
			val localProgress = ReaderReadingProgressState(
				decodeReaderReadingProgress(preferenceManager.readerReadingProgressJson)
			).startProgressFor(
				bookId = reader.bookId,
				resourceHref = reader.resourceHref,
				kind = reader.kind
			)
			openReaderPublication(
				reader.toReaderEngineOpenRequest(
					publicationUrl = publicationUrl,
					shellCoverUrl = shellCoverUrl,
					shellCoverTint = shellCoverTint,
					savedProgress = savedProgress,
					localProgress = localProgress,
					settings = runtimeSettings
				)
			)
			val attachment = reader.whispersyncLaunchAttachment()
			val wordSyncReference = attachment?.wordSync?.takeIf { reference ->
				wordSyncVerifier != null &&
					attachment.audiobookBookFileId.toLongOrNull() == reference.identity.audiobookBookFileId
			}
			wordSyncPublicationVerifier = wordSyncVerifier.takeIf { wordSyncReference != null }
			wordSyncVerificationSession = null
			applyCoordinatorStep(coordinator.configureWordSync(wordSyncReference))
			attachment?.let { attachment ->
				coroutineScope.launch {
					val sidecar = withContext(Dispatchers.IO) {
						binderyRepository.getWhispersyncSidecar(attachment.sidecarPath)
					}.fold(
						onSuccess = { sidecar ->
							Logger.i(
								ReaderScreenTag,
								"Whispersync sidecar state=loaded matched=true active=false " +
									"count=${sidecar.timeline.segments.size}"
							)
							applyCoordinatorStep(coordinator.dispatch { loadWhispersyncSidecar(sidecar) })
							sidecar
						},
						onFailure = { _ ->
							applyCoordinatorStep(
								coordinator.dispatch { reportWhispersyncLoadFailure(
									message = ReaderWhispersyncStatusMessage.Unavailable,
									detail = null
								) }
							)
							Logger.w(
								ReaderScreenTag,
								"Whispersync sidecar state=failed matched=false active=false " +
									"reason=load-failed"
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
								manifest = manifest,
								audiobookBookFileId = attachment.audiobookBookFileId
							)
							val playbackPlan = binderyAudiobookPlaybackPlan(
								manifest = manifest,
								versionRowId = whispersyncAudiobookIdentity ?: attachment.audiobookBookFileId,
								opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
								requestHeaders = requestHeaders,
								resumeProgress = resumeProgress,
								progressBookId = reader.bookId,
								audiobookBookFileId = attachment.audiobookBookFileId
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
							audiobookPlaybackManager.dispatch(
								ReaderReadaloudPlaybackCommand.SetSpeed(listeningSettings.playbackSpeed)
							)
							Logger.i(
								ReaderScreenTag,
								"Whispersync audiobook state=loaded matched=true active=false " +
									"count=${playbackPlan.mediaItems.size}"
							)
						},
						onFailure = { _ ->
							whispersyncPlaybackPlan = null
							applyCoordinatorStep(
								coordinator.dispatch { reportWhispersyncLoadFailure(
									message = ReaderWhispersyncStatusMessage.AudioUnavailable,
									detail = null
								) }
							)
							Logger.w(
								ReaderScreenTag,
								"Whispersync audiobook state=failed matched=false active=false " +
									"reason=load-failed"
							)
						}
					)
				}
			}
		},
		onError = { message ->
			applyCoordinatorStep(
				coordinator.dispatch { onEngineEvent(
					ReaderEngineEvent.Error(
						message = message,
						code = "publication_runtime"
					)
				) }
			)
		}
	)

	ReaderReadaloudRuntimeHost(
		reader = reader,
		readaloudSyncEnabled = readaloudSyncEnabled,
		readerInteraction = lastReadaloudReaderInteraction,
		readerInteractionKey = readaloudReaderInteractionKey,
		onPublicationReady = { publicationUrl ->
			openReaderPublication(
				reader.toReaderEngineOpenRequest(
					publicationUrl = publicationUrl,
					shellCoverUrl = null,
					shellCoverTint = null,
					savedProgress = null,
					settings = runtimeSettings
				)
			)
		},
		onEngineCommand = { command, _ ->
			applyReadaloudEngineCommand(command)
		},
		playbackCommand = readaloudCommand,
		playbackCommandKey = readaloudCommandKey,
		onPlaybackState = { playbackState ->
			applyCoordinatorStep(
				coordinator.onReadaloudPlaybackState(
					playbackState = playbackState,
					playbackIdentity = playbackState.toWordSyncPlaybackIdentity(whispersyncPlaybackPlan)
				)
			)
		},
		onError = { message ->
			applyCoordinatorStep(
				coordinator.dispatch { onEngineEvent(
					ReaderEngineEvent.Error(
						message = message,
						code = "readaloud_runtime"
					)
				) }
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
			applyReaderBackStep(coordinator.dispatchBack { onBack() })
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
		whispersyncCapable = reader.whispersyncLaunchAttachment() != null,
		listeningSettings = listeningSettings,
		readaloudPlaybackState = whispersyncReadaloudPlaybackState,
		onEngineHostEvent = { event -> handleEngineHostEvent(event) },
		onViewerAction = { action ->
			val beforeMenuVisible = coordinator.controller.state.menuVisible
			val step = coordinator.dispatch { onViewerAction(action) }
			Logger.i(
				ReaderScreenTag,
				"Reader viewer action=$action menuVisible=$beforeMenuVisible->${step.coordinator.controller.state.menuVisible} " +
					"shellCover=${coordinator.controller.state.shellCoverVisible}->${step.coordinator.controller.state.shellCoverVisible}"
			)
			applyCoordinatorStep(step)
		},
		onPageTurnBoundary = { direction ->
			val beforeShellCoverVisible = coordinator.controller.state.shellCoverVisible
			val step = coordinator.dispatch { onPageTurnBoundary(direction) }
			Logger.i(
				ReaderScreenTag,
				"Reader renderer boundary direction=$direction " +
					"shellCover=$beforeShellCoverVisible->${step.coordinator.controller.state.shellCoverVisible}"
			)
			applyCoordinatorStep(step)
		},
		onWhispersyncPlaybackCommand = { command ->
			Logger.i(
				WhispersyncSyncLogTag,
				"Whispersync playback command state=controller-owned active=true " +
					"command=${command.whispersyncPlaybackCommandLogValue()}"
			)
			applyCoordinatorStep(
				coordinator.dispatch { onWhispersyncPlaybackCommand(command) }
			)
		},
		onToggleWhispersyncCueMap = {
			applyCoordinatorStep(coordinator.dispatch { toggleWhispersyncCueMap() })
		},
		onWhispersyncCueMapChromeInterception = {
			applyCoordinatorStep(
				coordinator.dispatch {
					cancelWhispersyncCueMapHold(
						ReaderWhispersyncCueMapHoldOutcome.CancelledChromeInterception
					)
				}
			)
		},
		onPreviousChapter = {
			applyCoordinatorStep(coordinator.dispatch { navigateToPreviousChapter() })
		},
		onNextChapter = {
			applyCoordinatorStep(coordinator.dispatch { navigateToNextChapter() })
		},
		onGoToChapterPage = { pageIndex ->
			applyCoordinatorStep(coordinator.dispatch { navigateToChapterPage(pageIndex) })
		},
		onContents = {
			applyCoordinatorStep(coordinator.dispatch { openContentsDialog() })
		},
		onSearch = {
			applyCoordinatorStep(coordinator.dispatch { openSearchDialog() })
		},
		onWhispersyncPlayer = {
			applyCoordinatorStep(coordinator.dispatch { openWhispersyncPlayerDialog() })
		},
		onSearchInputChange = { query ->
			applyCoordinatorStep(coordinator.dispatch { updateSearchInput(query) })
		},
		onSearchQuery = { query ->
			applyCoordinatorStep(coordinator.dispatch { search(query) })
		},
		onNavigateToSearchResult = { result ->
			val navigateStep = coordinator.dispatch { navigateToSearchResult(result) }
			applyCoordinatorStep(navigateStep)
			applyCoordinatorStep(navigateStep.coordinator.dispatch { closeDialog() })
		},
		onDismissSearch = {
			applyCoordinatorStep(coordinator.dispatch { closeSearchDialog() })
		},
		onNavigateBack = {
			applyReaderBackStep(coordinator.dispatchBack { onNavigateBack() })
		},
		onSettings = {
			applyCoordinatorStep(coordinator.dispatch { openSettingsDialog() })
		},
		onShowMenus = {
			applyCoordinatorStep(coordinator.dispatch { showMenus() })
		},
		onHideMenus = {
			applyCoordinatorStep(coordinator.dispatch { hideMenus() })
		},
		onNavigateToTocItem = { tocItem ->
			tocItem.href?.let { href ->
				val navigateStep = coordinator.dispatch { navigateTo(ReaderLocator(href = href)) }
				applyCoordinatorStep(navigateStep)
				applyCoordinatorStep(navigateStep.coordinator.dispatch { closeDialog() })
			}
		},
		onNavigateToBookmark = { bookmark ->
			applyCoordinatorStep(coordinator.dispatch { navigateToBookmark(bookmark) })
		},
		onNavigateToAnnotation = { annotation ->
			applyCoordinatorStep(coordinator.dispatch { navigateToAnnotation(annotation) })
		},
		onToggleCurrentBookmark = {
			applyCoordinatorStep(coordinator.dispatch { toggleCurrentBookmark() })
		},
		onHighlightSelection = {
			applyCoordinatorStep(coordinator.dispatch { addSelectionHighlight() })
		},
		onCopySelection = { text ->
			clipboard.setText(AnnotatedString(text))
			Logger.i(ReaderScreenTag, "Reader selection copied length=${text.length}")
			applyCoordinatorStep(coordinator.dispatch { dismissSelectionActions() })
		},
		onStartSelectionNote = {
			applyCoordinatorStep(coordinator.dispatch { startSelectionNote() })
		},
		onSelectionNoteDraftChange = { note ->
			applyCoordinatorStep(coordinator.dispatch { updateSelectionNoteDraft(note) })
		},
		onSaveSelectionNote = { note ->
			Logger.i(ReaderScreenTag, "Reader selection note save length=${note.length}")
			applyCoordinatorStep(coordinator.dispatch { saveSelectionNote(note) })
		},
		onDismissSelectionNote = {
			applyCoordinatorStep(coordinator.dispatch { dismissSelectionNote() })
		},
		onDismissAnnotationPopup = {
			applyCoordinatorStep(coordinator.dispatch { dismissAnnotationPopup() })
		},
		onDismissFootnotePopup = {
			applyCoordinatorStep(coordinator.dispatch { dismissFootnotePopup() })
		},
		onOpenExternalLink = { url ->
			uriHandler.openUri(url)
			applyCoordinatorStep(coordinator.dispatch { dismissExternalLinkPrompt() })
		},
		onDismissExternalLinkPrompt = {
			applyCoordinatorStep(coordinator.dispatch { dismissExternalLinkPrompt() })
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
			applyCoordinatorStep(coordinator.dispatch { repairWhispersyncMismatch() })
		},
		onListeningSettingsChange = { nextSettings ->
			applyReaderListeningSettings(nextSettings)
		},
		onDismissDialog = {
			applyCoordinatorStep(coordinator.dispatch { closeDialog() })
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
							coordinator.dispatch { onViewerAction(
								ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Previous)
							) }
						)
						true
					}
					Key.VolumeDown -> {
						applyCoordinatorStep(
							coordinator.dispatch { onViewerAction(
								ReaderViewerAction.TurnPage(ReaderPageTurnDirection.Next)
							) }
						)
						true
					}
					else -> false
				}
			}
	)
}

private fun Screen.Reader.readerProcessStateViewModelKey(): String =
	"reader-process:${bookId.length}:$bookId:${resourceHref.length}:$resourceHref:${kind.name}:${publicationFormat.name}"

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

private fun ReaderReadaloudPlaybackUiState.toWordSyncPlaybackIdentity(
	playbackPlan: ReadaloudPlaybackPlan?
): ReaderWordSyncPlaybackIdentity? {
	val resourceId = audioResource?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val planResourceId = playbackPlan?.mediaItems
		?.getOrNull(trackIndex)
		?.resourceKey
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: return null
	if (resourceId != planResourceId) return null
	return ReaderWordSyncPlaybackIdentity(
		audioResourceId = planResourceId,
		audioTrackIndex = trackIndex,
		positionMs = positionMs.coerceAtLeast(0L),
		playbackSpeed = playbackSpeed
	)
}

private fun AudiobookPlaybackTimelineSnapshot.toReaderWordSyncTimelineSnapshot(
	playbackPlan: ReadaloudPlaybackPlan?
): ReaderWordSyncTimelineSnapshot? {
	val plan = playbackPlan ?: return null
	if (position.sessionId != plan.sessionId || position.trackIndex !in plan.mediaItems.indices) {
		return null
	}
	val item = plan.mediaItems[position.trackIndex]
	if (position.mediaId != null && position.mediaId != item.mediaId) return null
	val resourceId = item.resourceKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	return ReaderWordSyncTimelineSnapshot(
		sessionGeneration = sessionGeneration,
		timelineRevision = timelineRevision,
		audioResourceId = resourceId,
		audioTrackIndex = position.trackIndex,
		positionMs = position.positionMs.coerceAtLeast(0L),
		playbackSpeed = position.playbackSpeed,
		isPlaying = position.isPlaying
	)
}

private fun AudiobookPlaybackTimelineSnapshot?.wordSyncTimelineLogReason(
	playbackPlan: ReadaloudPlaybackPlan?
): String {
	val snapshot = this ?: return "no-snapshot"
	val plan = playbackPlan ?: return "no-plan"
	if (snapshot.position.sessionId != plan.sessionId) return "session-mismatch"
	if (snapshot.position.trackIndex !in plan.mediaItems.indices) return "track-mismatch"
	val item = plan.mediaItems[snapshot.position.trackIndex]
	if (snapshot.position.mediaId != null && snapshot.position.mediaId != item.mediaId) {
		return "media-mismatch"
	}
	if (item.resourceKey?.trim().isNullOrEmpty()) return "resource-missing"
	return "ready"
}

private fun ReaderWordSyncTimelineSnapshot.toWordSyncPlaybackIdentity() =
	ReaderWordSyncPlaybackIdentity(
		audioResourceId = audioResourceId,
		audioTrackIndex = audioTrackIndex,
		positionMs = positionMs,
		playbackSpeed = playbackSpeed
	)
