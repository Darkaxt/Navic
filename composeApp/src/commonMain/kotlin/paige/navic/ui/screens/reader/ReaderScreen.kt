package paige.navic.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.LocalSnackbarState
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyReadingProgress
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.icons.Icons
import paige.navic.icons.filled.Pause
import paige.navic.icons.filled.Play
import paige.navic.icons.filled.Settings
import paige.navic.icons.filled.SkipNext
import paige.navic.icons.filled.SkipPrevious
import paige.navic.icons.filled.Star
import paige.navic.icons.outlined.Book
import paige.navic.icons.outlined.DataTable
import paige.navic.icons.outlined.Search
import paige.navic.icons.outlined.Star
import paige.navic.reader.ReaderAnnotation
import paige.navic.reader.ReaderAnnotationState
import paige.navic.reader.ReaderBookmark
import paige.navic.reader.ReaderBookmarkState
import paige.navic.reader.ReaderBridgeCommand
import paige.navic.reader.ReaderBridgeEvent
import paige.navic.reader.ReaderChromeState
import paige.navic.reader.ReaderLocator
import paige.navic.reader.ReaderOptionsTab
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSearchResult
import paige.navic.reader.ReaderTocItem
import paige.navic.reader.decodeReaderAnnotations
import paige.navic.reader.decodeReaderBookmarks
import paige.navic.reader.encodeReaderAnnotations
import paige.navic.reader.encodeReaderBookmarks
import paige.navic.reader.normalizedReaderOptionsTab
import paige.navic.reader.normalizedReaderSettings
import paige.navic.reader.readerFlowShortLabel
import paige.navic.reader.readerFontFamilyShortLabel
import paige.navic.reader.readerOptionsTabLabel
import paige.navic.reader.readerOptionsTabs
import paige.navic.reader.readerOrientationShortLabel
import paige.navic.reader.readerReadaloudPlaybackSpeedLabel
import paige.navic.reader.readerThemeShortLabel
import paige.navic.reader.readerBookmarkFromLocator
import paige.navic.reader.readerDefaultSettings
import paige.navic.reader.readerDirectionShortLabel
import paige.navic.reader.readerReadaloudControlsVisible
import paige.navic.reader.readerTapZoneShortLabel
import paige.navic.reader.setReaderDefaultSettings
import paige.navic.reader.toBinderyReadingProgress
import paige.navic.reader.toReaderStartLocatorFor
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.sheets.ModalBottomSheet
import paige.navic.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(reader: Screen.Reader) {
	val snackbarState = LocalSnackbarState.current
	val platformContext = LocalPlatformContext.current
	val binderyRepository = koinInject<BinderyRepository>()
	val preferenceManager = koinInject<PreferenceManager>()
	val readerScope = rememberCoroutineScope()
	var lastReaderError by remember(reader.publicationUrl) { mutableStateOf<String?>(null) }
	var preparedPublicationUrl by remember(reader.publicationUrl, reader.kind, reader.mediaOverlayEnabled) {
		mutableStateOf<String?>(null)
	}
	var readerCommand by remember(reader.publicationUrl) { mutableStateOf<ReaderBridgeCommand?>(null) }
	var readerCommandKey by remember(reader.publicationUrl) { mutableStateOf(0L) }
	var lastReaderEvent by remember(reader.publicationUrl) { mutableStateOf<ReaderBridgeEvent?>(null) }
	var readerEventKey by remember(reader.publicationUrl) { mutableStateOf(0L) }
	val defaultReaderSettings = remember(
		reader.publicationUrl,
		preferenceManager.readerFontFamily,
		preferenceManager.readerFontSizePercent,
		preferenceManager.readerLineHeightPercent,
		preferenceManager.readerParagraphSpacingPercent,
		preferenceManager.readerMarginPercent,
		preferenceManager.readerDimOverlayPercent,
		preferenceManager.readerOrientation,
		preferenceManager.readerTheme,
		preferenceManager.readerFlowMode,
		preferenceManager.readerPaged,
		preferenceManager.readerTapZone,
		preferenceManager.readerSmallerTapZone,
		preferenceManager.readerPublisherStylesEnabled,
		preferenceManager.readerFullscreen,
		preferenceManager.readerKeepScreenOn,
		preferenceManager.readerWebContentsDebuggingEnabled
	) {
		preferenceManager.readerDefaultSettings()
	}
	var chromeState by remember(reader.publicationUrl, defaultReaderSettings) {
		mutableStateOf(ReaderChromeState(settings = defaultReaderSettings))
	}
	var chromeVisible by remember(reader.publicationUrl) { mutableStateOf(false) }
	var optionsVisible by remember(reader.publicationUrl) { mutableStateOf(false) }
	var tocVisible by remember(reader.publicationUrl) { mutableStateOf(false) }
	var tocItems by remember(reader.publicationUrl) { mutableStateOf(emptyList<ReaderTocItem>()) }
	var annotationsVisible by remember(reader.publicationUrl) { mutableStateOf(false) }
	var annotationState by remember {
		mutableStateOf(ReaderAnnotationState(decodeReaderAnnotations(preferenceManager.readerAnnotationsJson)))
	}
	var readerSelection by remember(reader.publicationUrl) {
		mutableStateOf<ReaderBridgeEvent.SelectionChanged?>(null)
	}
	var bookmarksVisible by remember(reader.publicationUrl) { mutableStateOf(false) }
	var bookmarkState by remember {
		mutableStateOf(ReaderBookmarkState(decodeReaderBookmarks(preferenceManager.readerBookmarksJson)))
	}
	var searchVisible by remember(reader.publicationUrl) { mutableStateOf(false) }
	var searchQuery by remember(reader.publicationUrl) { mutableStateOf("") }
	var searchResults by remember(reader.publicationUrl) { mutableStateOf(emptyList<ReaderSearchResult>()) }
	val readerSystemBarsVisible = chromeVisible || optionsVisible || tocVisible ||
		annotationsVisible || bookmarksVisible || searchVisible
	ReaderOrientationEffect(chromeState.settings.orientation)
	ReaderSystemBarsEffect(
		fullscreen = chromeState.settings.fullscreen == true,
		systemBarsVisible = readerSystemBarsVisible
	)
	val explicitStartLocator = remember(reader.startCfi, reader.startHref) {
		ReaderLocator(
			cfi = reader.startCfi,
			href = reader.startHref
		).takeIf { it.cfi != null || it.href != null }
	}
	var resumeStartLocator by remember(reader.publicationUrl, reader.resourceHref, reader.kind) {
		mutableStateOf(explicitStartLocator)
	}
	var progressResumeLoaded by remember(reader.publicationUrl, reader.resourceHref, reader.kind) {
		mutableStateOf(explicitStartLocator != null)
	}
	var lastSavedProgress by remember(reader.bookId, reader.resourceHref, reader.kind) {
		mutableStateOf<BinderyReadingProgress?>(null)
	}
	var readaloudCommand by remember(reader.publicationUrl) {
		mutableStateOf<ReaderReadaloudPlaybackCommand?>(null)
	}
	var readaloudCommandKey by remember(reader.publicationUrl) { mutableStateOf(0L) }
	val readerFocusRequester = remember { FocusRequester() }
	val currentBookAnnotations = annotationState.annotationsForBook(reader.bookId)
	val canHighlightSelection = readerSelection?.cfi?.isNotBlank() == true &&
		readerSelection?.text?.isNotBlank() == true
	val currentBookBookmarks = bookmarkState.bookmarksForBook(reader.bookId)
	val canBookmarkCurrentLocation = readerBookmarkFromLocator(
		bookId = reader.bookId,
		bookTitle = reader.title,
		locator = chromeState.currentLocator,
		sectionTitle = chromeState.currentSectionTitle
	) != null
	val currentLocationBookmarked = bookmarkState.isBookmarked(
		bookId = reader.bookId,
		locator = chromeState.currentLocator
	)

	LaunchedEffect(lastReaderError) {
		lastReaderError?.let { snackbarState.showSnackbar(it) }
	}

	LaunchedEffect(reader.publicationUrl) {
		readerFocusRequester.requestFocus()
	}

	LaunchedEffect(reader.bookId, reader.resourceHref, reader.kind, explicitStartLocator) {
		if (explicitStartLocator != null) {
			resumeStartLocator = explicitStartLocator
			progressResumeLoaded = true
			return@LaunchedEffect
		}
		progressResumeLoaded = false
		resumeStartLocator = binderyRepository.getReadingProgress(reader.bookId)
			.getOrNull()
			?.toReaderStartLocatorFor(
				resourceHref = reader.resourceHref,
				kind = reader.kind
			)
		progressResumeLoaded = true
	}

	fun dispatchReaderCommand(command: ReaderBridgeCommand) {
		readerCommand = command
		readerCommandKey += 1L
	}

	fun hideReaderPanels() {
		tocVisible = false
		annotationsVisible = false
		bookmarksVisible = false
		searchVisible = false
		optionsVisible = false
	}

	fun hideReaderChrome() {
		chromeVisible = false
		hideReaderPanels()
	}

	fun toggleReaderChrome() {
		chromeVisible = !chromeVisible
		if (!chromeVisible) hideReaderPanels()
	}

	fun updateChromeSettings(nextState: ReaderChromeState) {
		val normalizedState = nextState.copy(settings = nextState.settings.normalizedReaderSettings())
		chromeState = normalizedState
		preferenceManager.setReaderDefaultSettings(normalizedState.settings)
		dispatchReaderCommand(normalizedState.toSettingsCommand())
	}

	fun dispatchReadaloudCommand(command: ReaderReadaloudPlaybackCommand) {
		if (command is ReaderReadaloudPlaybackCommand.SetSyncEnabled) {
			val updated = chromeState.copy(
				settings = chromeState.settings.copy(readaloudSyncEnabled = command.enabled),
				readaloudPlayback = chromeState.readaloudPlayback.copy(syncEnabled = command.enabled)
			)
			chromeState = updated
			preferenceManager.setReaderDefaultSettings(updated.settings)
		}
		platformContext.clickSound()
		readaloudCommand = command
		readaloudCommandKey += 1L
	}

	fun submitReaderSearch() {
		val query = searchQuery.trim()
		if (query.isNotEmpty()) {
			dispatchReaderCommand(ReaderBridgeCommand.Search(query))
		}
	}

	fun openSearchResult(result: ReaderSearchResult) {
		when {
			result.cfi != null -> dispatchReaderCommand(ReaderBridgeCommand.GoToCfi(result.cfi))
			result.href != null -> dispatchReaderCommand(ReaderBridgeCommand.GoToHref(result.href))
		}
	}

	fun openTocItem(item: ReaderTocItem) {
		item.href?.let { href ->
			dispatchReaderCommand(ReaderBridgeCommand.GoToHref(href))
			tocVisible = false
		}
	}

	fun persistAnnotations(nextState: ReaderAnnotationState) {
		annotationState = nextState
		preferenceManager.readerAnnotationsJson = encodeReaderAnnotations(nextState.annotations)
	}

	fun addSelectionHighlight() {
		val selection = readerSelection ?: return
		val nextState = annotationState.addSelectionHighlight(
			bookId = reader.bookId,
			bookTitle = reader.title,
			selection = selection,
			sectionTitle = chromeState.currentSectionTitle
		)
		persistAnnotations(nextState)
		nextState.annotationsForBook(reader.bookId)
			.firstOrNull { annotation -> annotation.cfi == selection.cfi }
			?.let { annotation ->
				dispatchReaderCommand(
					ReaderBridgeCommand.ApplyHighlight(
						id = annotation.id,
						cfi = annotation.cfi,
						color = annotation.color,
						note = annotation.note
					)
				)
			}
	}

	fun openAnnotation(annotation: ReaderAnnotation) {
		dispatchReaderCommand(ReaderBridgeCommand.GoToCfi(annotation.cfi))
		annotationsVisible = false
	}

	fun persistBookmarks(nextState: ReaderBookmarkState) {
		bookmarkState = nextState
		preferenceManager.readerBookmarksJson = encodeReaderBookmarks(nextState.bookmarks)
	}

	fun toggleCurrentBookmark() {
		persistBookmarks(
			bookmarkState.toggleBookmark(
				bookId = reader.bookId,
				bookTitle = reader.title,
				locator = chromeState.currentLocator,
				sectionTitle = chromeState.currentSectionTitle
			)
		)
	}

	fun openBookmark(bookmark: ReaderBookmark) {
		when {
			bookmark.cfi != null -> dispatchReaderCommand(ReaderBridgeCommand.GoToCfi(bookmark.cfi))
			bookmark.href != null -> dispatchReaderCommand(ReaderBridgeCommand.GoToHref(bookmark.href))
		}
		bookmarksVisible = false
	}

	fun saveReaderProgress(locator: ReaderLocator) {
		val progress = locator.toBinderyReadingProgress(
			bookId = reader.bookId,
			resourceHref = reader.resourceHref,
			kind = reader.kind
		) ?: return
		if (progress == lastSavedProgress) return
		lastSavedProgress = progress
		readerScope.launch {
			binderyRepository.putReadingProgress(progress)
		}
	}

	Scaffold(
		bottomBar = {
			if (chromeVisible) {
				ReaderBottomChrome(
					title = reader.title,
					state = chromeState,
					showReadaloudControls = readerReadaloudControlsVisible(
						kind = reader.kind,
						mediaOverlayEnabled = reader.mediaOverlayEnabled
					),
					onPreviousPage = {
						platformContext.clickSound()
						dispatchReaderCommand(ReaderBridgeCommand.PreviousPage)
					},
					onNextPage = {
						platformContext.clickSound()
						dispatchReaderCommand(ReaderBridgeCommand.NextPage)
					},
					onProgressSeek = { progress ->
						platformContext.clickSound()
						dispatchReaderCommand(ReaderBridgeCommand.GoToProgress(progress.toDouble()))
					},
					onToggleOptions = {
						platformContext.clickSound()
						optionsVisible = true
					},
					tocVisible = tocVisible,
					tocItems = tocItems,
					onToggleToc = {
						platformContext.clickSound()
						tocVisible = !tocVisible
						if (tocVisible) searchVisible = false
						if (tocVisible) annotationsVisible = false
						if (tocVisible) bookmarksVisible = false
					},
					onOpenTocItem = { item ->
						platformContext.clickSound()
						openTocItem(item)
						hideReaderChrome()
					},
					annotationsVisible = annotationsVisible,
					annotations = currentBookAnnotations,
					canHighlightSelection = canHighlightSelection,
					onToggleAnnotations = {
						platformContext.clickSound()
						annotationsVisible = !annotationsVisible
						if (annotationsVisible) tocVisible = false
						if (annotationsVisible) bookmarksVisible = false
						if (annotationsVisible) searchVisible = false
					},
					onAddSelectionHighlight = {
						platformContext.clickSound()
						addSelectionHighlight()
						annotationsVisible = true
					},
					onOpenAnnotation = { annotation ->
						platformContext.clickSound()
						openAnnotation(annotation)
						hideReaderChrome()
					},
					bookmarksVisible = bookmarksVisible,
					bookmarks = currentBookBookmarks,
					currentLocationBookmarked = currentLocationBookmarked,
					canBookmarkCurrentLocation = canBookmarkCurrentLocation,
					onToggleBookmarks = {
						platformContext.clickSound()
						bookmarksVisible = !bookmarksVisible
						if (bookmarksVisible) tocVisible = false
						if (bookmarksVisible) annotationsVisible = false
						if (bookmarksVisible) searchVisible = false
					},
					onToggleCurrentBookmark = {
						platformContext.clickSound()
						toggleCurrentBookmark()
					},
					onOpenBookmark = { bookmark ->
						platformContext.clickSound()
						openBookmark(bookmark)
						hideReaderChrome()
					},
					searchVisible = searchVisible,
					searchQuery = searchQuery,
					searchResults = searchResults,
					onToggleSearch = {
						platformContext.clickSound()
						searchVisible = !searchVisible
						if (searchVisible) tocVisible = false
						if (searchVisible) annotationsVisible = false
						if (searchVisible) bookmarksVisible = false
					},
					onSearchQueryChange = { query -> searchQuery = query },
					onSubmitSearch = {
						platformContext.clickSound()
						submitReaderSearch()
					},
					onOpenSearchResult = { result ->
						platformContext.clickSound()
						openSearchResult(result)
						hideReaderChrome()
					},
					onReadaloudToggle = {
						val command = chromeState.readaloudPlayback.toggleCommand() ?: return@ReaderBottomChrome
						dispatchReadaloudCommand(command)
					},
					onReadaloudSpeedChange = { command ->
						dispatchReadaloudCommand(command)
					},
					onReadaloudSyncChange = { command ->
						dispatchReadaloudCommand(command)
					}
				)
			}
		}
	) { innerPadding ->
		Box(
			Modifier
				.padding(innerPadding)
				.fillMaxSize()
				.focusRequester(readerFocusRequester)
				.focusable()
				.onPreviewKeyEvent { event ->
					if (chromeState.settings.volumeKeyPageTurns != true) {
						return@onPreviewKeyEvent false
					}
					when (event.key) {
						Key.VolumeUp, Key.VolumeDown -> {
							if (event.type == KeyEventType.KeyUp) {
								platformContext.clickSound()
								dispatchReaderCommand(
									if (event.key == Key.VolumeUp) {
										ReaderBridgeCommand.NextPage
									} else {
										ReaderBridgeCommand.PreviousPage
									}
								)
							}
							true
						}
						else -> false
					}
				}
		) {
			ReaderPublicationRuntimeHost(
				reader = reader,
				onPublicationReady = { publicationUrl ->
					lastReaderError = null
					preparedPublicationUrl = publicationUrl
				},
				onError = { message -> lastReaderError = message }
			)
			ReaderReadaloudRuntimeHost(
				reader = reader,
				readaloudSyncEnabled = chromeState.settings.readaloudSyncEnabled != false,
				readerEvent = lastReaderEvent,
				readerEventKey = readerEventKey,
				onPublicationReady = { publicationUrl ->
					lastReaderError = null
					preparedPublicationUrl = publicationUrl
				},
				onReaderCommand = { command, key ->
					readerCommand = command
					readerCommandKey = key
				},
				playbackCommand = readaloudCommand,
				playbackCommandKey = readaloudCommandKey,
				onPlaybackState = { playbackState ->
					chromeState = chromeState.onReadaloudPlaybackState(playbackState)
				},
				onError = { message -> lastReaderError = message }
			)
			if (progressResumeLoaded) preparedPublicationUrl?.let { publicationUrl ->
				val handleReaderEvent: (ReaderBridgeEvent) -> Unit = { event ->
					lastReaderEvent = event
					readerEventKey += 1L
					chromeState = chromeState.onReaderEvent(event)
					if (event is ReaderBridgeEvent.Error) {
						lastReaderError = event.message
					}
					if (event is ReaderBridgeEvent.SearchResults) {
						searchResults = event.results
					}
					if (event is ReaderBridgeEvent.Toc) {
						tocItems = event.items
					}
					if (event is ReaderBridgeEvent.SelectionChanged) {
						readerSelection = event.takeIf { selection ->
							selection.cfi?.isNotBlank() == true &&
								selection.text?.isNotBlank() == true
						}
					}
					if (event is ReaderBridgeEvent.PublicationReady && currentBookAnnotations.isNotEmpty()) {
						dispatchReaderCommand(ReaderBridgeCommand.ApplyHighlights(currentBookAnnotations))
					}
					if (event is ReaderBridgeEvent.LocationChanged) {
						saveReaderProgress(event.locator)
					}
					if (event is ReaderBridgeEvent.CenterTap) {
						toggleReaderChrome()
					}
				}
				ReaderWebViewHost(
					publicationUrl = publicationUrl,
					title = reader.title,
					kind = reader.kind,
					mediaOverlayEnabled = reader.mediaOverlayEnabled,
					settings = chromeState.settings,
					startCfi = resumeStartLocator?.cfi,
					startHref = resumeStartLocator?.href,
					command = readerCommand,
					commandKey = readerCommandKey,
					onEvent = handleReaderEvent,
					modifier = Modifier.fillMaxSize()
				)
				ReaderDimOverlay(
					dimOverlayPercent = chromeState.settings.dimOverlayPercent ?: 0,
					modifier = Modifier.matchParentSize()
				)
			}
			if (!progressResumeLoaded || (preparedPublicationUrl == null && lastReaderError == null)) {
				CircularProgressIndicator(Modifier.align(Alignment.Center))
			}
			if (preparedPublicationUrl == null) {
				lastReaderError?.let { message ->
					ContentUnavailable(
						icon = Icons.Outlined.Book,
						label = message,
						modifier = Modifier
							.align(Alignment.Center)
							.padding(24.dp)
					)
				}
			}
		}
	}
	if (optionsVisible) {
		ReaderKomikkuOptionsSheet(
			state = chromeState,
			showReadaloudControls = readerReadaloudControlsVisible(
				kind = reader.kind,
				mediaOverlayEnabled = reader.mediaOverlayEnabled
			),
			onDismissRequest = { optionsVisible = false },
			onSettingsChange = { nextState ->
				platformContext.clickSound()
				updateChromeSettings(nextState)
			},
			onReadaloudToggle = {
				val command = chromeState.readaloudPlayback.toggleCommand() ?: return@ReaderKomikkuOptionsSheet
				dispatchReadaloudCommand(command)
			},
			onReadaloudSpeedChange = { command ->
				dispatchReadaloudCommand(command)
			},
			onReadaloudSyncChange = { command ->
				dispatchReadaloudCommand(command)
			}
		)
	}
}

@Composable
private fun ReaderDimOverlay(
	dimOverlayPercent: Int,
	modifier: Modifier = Modifier
) {
	val alpha = (dimOverlayPercent.coerceIn(0, 80) / 100f).takeIf { it > 0f } ?: return
	Box(modifier.background(Color.Black.copy(alpha = alpha)))
}

@Composable
private fun ReaderBottomChrome(
	title: String,
	state: ReaderChromeState,
	showReadaloudControls: Boolean,
	onPreviousPage: () -> Unit,
	onNextPage: () -> Unit,
	onProgressSeek: (Float) -> Unit,
	onToggleOptions: () -> Unit,
	tocVisible: Boolean,
	tocItems: List<ReaderTocItem>,
	onToggleToc: () -> Unit,
	onOpenTocItem: (ReaderTocItem) -> Unit,
	annotationsVisible: Boolean,
	annotations: List<ReaderAnnotation>,
	canHighlightSelection: Boolean,
	onToggleAnnotations: () -> Unit,
	onAddSelectionHighlight: () -> Unit,
	onOpenAnnotation: (ReaderAnnotation) -> Unit,
	bookmarksVisible: Boolean,
	bookmarks: List<ReaderBookmark>,
	currentLocationBookmarked: Boolean,
	canBookmarkCurrentLocation: Boolean,
	onToggleBookmarks: () -> Unit,
	onToggleCurrentBookmark: () -> Unit,
	onOpenBookmark: (ReaderBookmark) -> Unit,
	searchVisible: Boolean,
	searchQuery: String,
	searchResults: List<ReaderSearchResult>,
	onToggleSearch: () -> Unit,
	onSearchQueryChange: (String) -> Unit,
	onSubmitSearch: () -> Unit,
	onOpenSearchResult: (ReaderSearchResult) -> Unit,
	onReadaloudToggle: () -> Unit,
	onReadaloudSpeedChange: (ReaderReadaloudPlaybackCommand) -> Unit,
	onReadaloudSyncChange: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	var progressSliderValue by remember(state.progressFraction) {
		mutableStateOf(state.progressFraction ?: 0f)
	}
	Surface(
		tonalElevation = 3.dp,
		shadowElevation = 2.dp,
		color = MaterialTheme.colorScheme.surface
	) {
		Column(Modifier.fillMaxWidth()) {
			LinearProgressIndicator(
				progress = { state.progressFraction ?: 0f },
				modifier = Modifier
					.fillMaxWidth()
					.height(3.dp)
			)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 12.dp, vertical = 6.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				IconButton(onClick = onPreviousPage) {
					Icon(
						imageVector = Icons.Filled.SkipPrevious,
						contentDescription = null
					)
				}
				Column(Modifier.weight(1f)) {
					Text(
						text = state.currentSectionTitle ?: title,
						style = MaterialTheme.typography.labelLarge,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					ReaderReadaloudMetadataLabel(state.readaloudPlayback.activeAudioLabel)
					Text(
						text = state.progressLabel,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1
					)
				}
				IconButton(onClick = onNextPage) {
					Icon(
						imageVector = Icons.Filled.SkipNext,
						contentDescription = null
					)
				}
				if (showReadaloudControls) {
					ReaderReadaloudButton(
						state = state.readaloudPlayback,
						onClick = onReadaloudToggle
					)
				}
			}
			ReaderProgressSeekControl(
				value = progressSliderValue,
				enabled = state.progressFraction != null,
				onValueChange = { value -> progressSliderValue = value.coerceIn(0f, 1f) },
				onValueChangeFinished = { onProgressSeek(progressSliderValue.coerceIn(0f, 1f)) },
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
			)
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.horizontalScroll(rememberScrollState())
					.padding(horizontal = 8.dp),
				horizontalArrangement = Arrangement.spacedBy(2.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				IconButton(
					onClick = onToggleToc,
					enabled = tocItems.isNotEmpty()
				) {
					Icon(
						imageVector = Icons.Outlined.DataTable,
						contentDescription = null
					)
				}
				IconButton(
					onClick = onToggleAnnotations,
					enabled = canHighlightSelection || annotations.isNotEmpty()
				) {
					Text(
						text = "HL",
						style = MaterialTheme.typography.labelSmall,
						fontWeight = FontWeight.SemiBold
					)
				}
				IconButton(
					onClick = onToggleBookmarks,
					enabled = canBookmarkCurrentLocation || bookmarks.isNotEmpty()
				) {
					Icon(
						imageVector = if (currentLocationBookmarked) Icons.Filled.Star else Icons.Outlined.Star,
						contentDescription = null
					)
				}
				IconButton(onClick = onToggleSearch) {
					Icon(
						imageVector = Icons.Outlined.Search,
						contentDescription = null
					)
				}
				IconButton(onClick = onToggleOptions) {
					Icon(
						imageVector = Icons.Filled.Settings,
						contentDescription = null
					)
				}
			}
			if (annotationsVisible) {
				ReaderAnnotationPanel(
					annotations = annotations,
					canHighlightSelection = canHighlightSelection,
					onAddSelectionHighlight = onAddSelectionHighlight,
					onOpenAnnotation = onOpenAnnotation,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp)
				)
			}
			if (bookmarksVisible) {
				ReaderBookmarkPanel(
					bookmarks = bookmarks,
					currentLocationBookmarked = currentLocationBookmarked,
					canBookmarkCurrentLocation = canBookmarkCurrentLocation,
					onToggleCurrentBookmark = onToggleCurrentBookmark,
					onOpenBookmark = onOpenBookmark,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp)
				)
			}
			if (tocVisible && tocItems.isNotEmpty()) {
				ReaderTocPanel(
					items = tocItems,
					currentHref = state.currentLocator?.href,
					onOpenItem = onOpenTocItem,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp)
				)
			}
			if (searchVisible) {
				ReaderSearchPanel(
					query = searchQuery,
					results = searchResults,
					onQueryChange = onSearchQueryChange,
					onSubmitSearch = onSubmitSearch,
					onOpenResult = onOpenSearchResult,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 12.dp)
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderKomikkuOptionsSheet(
	state: ReaderChromeState,
	showReadaloudControls: Boolean,
	onDismissRequest: () -> Unit,
	onSettingsChange: (ReaderChromeState) -> Unit,
	onReadaloudToggle: () -> Unit,
	onReadaloudSpeedChange: (ReaderReadaloudPlaybackCommand) -> Unit,
	onReadaloudSyncChange: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
	var selectedOptionsTab by remember(showReadaloudControls) {
		mutableStateOf(ReaderOptionsTab.Reading)
	}
	val safeSelectedOptionsTab = normalizedReaderOptionsTab(
		tab = selectedOptionsTab,
		showReadaloudControls = showReadaloudControls
	)
	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		sheetState = sheetState,
		containerColor = MaterialTheme.colorScheme.surface,
		tonalElevation = 4.dp
	) {
		BoxWithConstraints {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = maxHeight * 0.75f)
					.verticalScroll(rememberScrollState())
					.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				ReaderOptionsPanel(
					state = state,
					showReadaloudControls = showReadaloudControls,
					selectedTab = safeSelectedOptionsTab,
					onTabSelected = { tab -> selectedOptionsTab = tab },
					onSettingsChange = onSettingsChange,
					onReadaloudToggle = onReadaloudToggle,
					onReadaloudSpeedChange = onReadaloudSpeedChange,
					onReadaloudSyncChange = onReadaloudSyncChange,
					modifier = Modifier.fillMaxWidth()
				)
			}
		}
	}
}

@Composable
private fun ReaderProgressSeekControl(
	value: Float,
	enabled: Boolean,
	onValueChange: (Float) -> Unit,
	onValueChangeFinished: () -> Unit,
	modifier: Modifier = Modifier
) {
	Slider(
		value = value.coerceIn(0f, 1f),
		onValueChange = { nextValue -> onValueChange(nextValue.coerceIn(0f, 1f)) },
		modifier = modifier.height(32.dp),
		enabled = enabled,
		valueRange = 0f..1f,
		onValueChangeFinished = onValueChangeFinished
	)
}

@Composable
private fun ReaderReadaloudMetadataLabel(activeAudioLabel: String?) {
	val label = activeAudioLabel?.trim()?.takeIf { it.isNotEmpty() } ?: return
	Text(
		text = label,
		style = MaterialTheme.typography.labelSmall,
		color = MaterialTheme.colorScheme.primary,
		maxLines = 1,
		overflow = TextOverflow.Ellipsis
	)
}

@Composable
private fun ReaderAnnotationPanel(
	annotations: List<ReaderAnnotation>,
	canHighlightSelection: Boolean,
	onAddSelectionHighlight: () -> Unit,
	onOpenAnnotation: (ReaderAnnotation) -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		if (canHighlightSelection) {
			Surface(
				onClick = onAddSelectionHighlight,
				color = MaterialTheme.colorScheme.surface,
				modifier = Modifier.fillMaxWidth()
			) {
				Text(
					text = "Highlight selection",
					style = MaterialTheme.typography.labelLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(vertical = 8.dp)
				)
			}
		}
		if (annotations.isNotEmpty()) {
			LazyColumn(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 180.dp)
			) {
				items(annotations, key = { it.id }) { annotation ->
					ReaderAnnotationRow(
						annotation = annotation,
						onClick = { onOpenAnnotation(annotation) }
					)
				}
			}
		}
	}
}

@Composable
private fun ReaderAnnotationRow(
	annotation: ReaderAnnotation,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		color = MaterialTheme.colorScheme.surface,
		modifier = Modifier.fillMaxWidth()
	) {
		Column(
			modifier = Modifier.padding(vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp)
		) {
			Text(
				text = annotation.displayTitle,
				style = MaterialTheme.typography.labelMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = annotation.text,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

@Composable
private fun ReaderBookmarkPanel(
	bookmarks: List<ReaderBookmark>,
	currentLocationBookmarked: Boolean,
	canBookmarkCurrentLocation: Boolean,
	onToggleCurrentBookmark: () -> Unit,
	onOpenBookmark: (ReaderBookmark) -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		if (canBookmarkCurrentLocation) {
			Surface(
				onClick = onToggleCurrentBookmark,
				color = MaterialTheme.colorScheme.surface,
				modifier = Modifier.fillMaxWidth()
			) {
				Row(
					modifier = Modifier.padding(vertical = 8.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(
						imageVector = if (currentLocationBookmarked) Icons.Filled.Star else Icons.Outlined.Star,
						contentDescription = null,
						modifier = Modifier.size(20.dp)
					)
					Text(
						text = if (currentLocationBookmarked) "Remove bookmark" else "Bookmark this location",
						style = MaterialTheme.typography.labelLarge,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		}
		if (bookmarks.isNotEmpty()) {
			LazyColumn(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 180.dp)
			) {
				items(bookmarks, key = { it.id }) { bookmark ->
					ReaderBookmarkRow(
						bookmark = bookmark,
						onClick = { onOpenBookmark(bookmark) }
					)
				}
			}
		}
	}
}

@Composable
private fun ReaderBookmarkRow(
	bookmark: ReaderBookmark,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		color = MaterialTheme.colorScheme.surface,
		modifier = Modifier.fillMaxWidth()
	) {
		Column(
			modifier = Modifier.padding(vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp)
		) {
			Text(
				text = bookmark.displayTitle,
				style = MaterialTheme.typography.labelMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = bookmark.href ?: bookmark.cfi.orEmpty(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

@Composable
private fun ReaderTocPanel(
	items: List<ReaderTocItem>,
	currentHref: String?,
	onOpenItem: (ReaderTocItem) -> Unit,
	modifier: Modifier = Modifier
) {
	LazyColumn(
		modifier = modifier
			.fillMaxWidth()
			.heightIn(max = 220.dp)
	) {
		items(items, key = { it.id }) { item ->
			ReaderTocItemRow(
				item = item,
				selected = item.href != null && item.href == currentHref,
				onClick = { onOpenItem(item) }
			)
		}
	}
}

@Composable
private fun ReaderTocItemRow(
	item: ReaderTocItem,
	selected: Boolean,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		color = if (selected) {
			MaterialTheme.colorScheme.primaryContainer
		} else {
			MaterialTheme.colorScheme.surface
		},
		modifier = Modifier.fillMaxWidth()
	) {
		Text(
			text = item.title,
			style = MaterialTheme.typography.bodyMedium,
			color = if (selected) {
				MaterialTheme.colorScheme.onPrimaryContainer
			} else {
				MaterialTheme.colorScheme.onSurface
			},
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(
				start = (item.level * 16).dp,
				top = 8.dp,
				end = 8.dp,
				bottom = 8.dp
			)
		)
	}
}

@Composable
private fun ReaderSearchPanel(
	query: String,
	results: List<ReaderSearchResult>,
	onQueryChange: (String) -> Unit,
	onSubmitSearch: () -> Unit,
	onOpenResult: (ReaderSearchResult) -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(6.dp)
	) {
		TextField(
			value = query,
			onValueChange = onQueryChange,
			singleLine = true,
			placeholder = { Text("Search in book") },
			leadingIcon = { Icon(Icons.Outlined.Search, null) },
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
			keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
			modifier = Modifier.fillMaxWidth()
		)
		if (results.isNotEmpty()) {
			LazyColumn(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 180.dp)
			) {
				items(results, key = { it.id }) { result ->
					ReaderSearchResultRow(
						result = result,
						onClick = { onOpenResult(result) }
					)
				}
			}
		}
	}
}

@Composable
private fun ReaderSearchResultRow(
	result: ReaderSearchResult,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		color = MaterialTheme.colorScheme.surface,
		modifier = Modifier.fillMaxWidth()
	) {
		Column(
			modifier = Modifier.padding(vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(2.dp)
		) {
			Text(
				text = result.sectionTitle ?: result.href ?: "Search result",
				style = MaterialTheme.typography.labelMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = result.excerpt ?: result.cfi ?: result.href.orEmpty(),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderOptionsPanel(
	state: ReaderChromeState,
	showReadaloudControls: Boolean,
	selectedTab: ReaderOptionsTab,
	onTabSelected: (ReaderOptionsTab) -> Unit,
	onSettingsChange: (ReaderChromeState) -> Unit,
	onReadaloudToggle: () -> Unit,
	onReadaloudSpeedChange: (ReaderReadaloudPlaybackCommand) -> Unit,
	onReadaloudSyncChange: (ReaderReadaloudPlaybackCommand) -> Unit,
	modifier: Modifier = Modifier
) {
	val tabs = readerOptionsTabs(showReadaloudControls)
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.horizontalScroll(rememberScrollState()),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			tabs.forEach { tab ->
				ReaderOptionsTabChip(
					label = readerOptionsTabLabel(tab),
					selected = selectedTab == tab,
					onClick = { onTabSelected(tab) },
				)
			}
		}

		when (selectedTab) {
			ReaderOptionsTab.Reading -> ReaderReadingOptions(
				state = state,
				onSettingsChange = onSettingsChange
			)
			ReaderOptionsTab.General -> ReaderGeneralOptions(
				state = state,
				onSettingsChange = onSettingsChange
			)
			ReaderOptionsTab.Media -> ReaderMediaOptions(
				state = state,
				onReadaloudToggle = onReadaloudToggle,
				onReadaloudSpeedChange = onReadaloudSpeedChange,
				onReadaloudSyncChange = onReadaloudSyncChange
			)
		}
	}
}

@Composable
private fun ReaderOptionsTabChip(
	label: String,
	selected: Boolean,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		shape = MaterialTheme.shapes.large,
		color = if (selected) {
			MaterialTheme.colorScheme.primaryContainer
		} else {
			MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
		},
		contentColor = if (selected) {
			MaterialTheme.colorScheme.onPrimaryContainer
		} else {
			MaterialTheme.colorScheme.onSurfaceVariant
		}
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelLarge,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
		)
	}
}

@Composable
private fun ReaderReadingOptions(
	state: ReaderChromeState,
	onSettingsChange: (ReaderChromeState) -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		ReaderCycleRow(
			label = "Reading mode",
			value = readerFlowShortLabel(state.settings.flowMode, state.settings.paged),
			onClick = { onSettingsChange(state.toggleFlowMode()) }
		)
		ReaderCycleRow(
			label = "Direction",
			value = readerDirectionShortLabel(state.settings.direction),
			onClick = { onSettingsChange(state.toggleDirection()) }
		)
		ReaderCycleRow(
			label = "Font",
			value = readerFontFamilyShortLabel(state.settings.fontFamily),
			onClick = { onSettingsChange(state.toggleFontFamily()) }
		)
		ReaderControlStepper(
			label = "Font size",
			value = "${state.settings.fontSizePercent ?: 100}%",
			onDecrease = { onSettingsChange(state.adjustFontSize(-8)) },
			onIncrease = { onSettingsChange(state.adjustFontSize(8)) }
		)
		ReaderControlStepper(
			label = "Line height",
			value = "${state.settings.lineHeight ?: 1.55}",
			onDecrease = { onSettingsChange(state.adjustLineHeight(-0.1)) },
			onIncrease = { onSettingsChange(state.adjustLineHeight(0.1)) }
		)
		ReaderControlStepper(
			label = "Paragraph spacing",
			value = "${state.settings.paragraphSpacingPercent ?: 0}%",
			onDecrease = { onSettingsChange(state.adjustParagraphSpacing(-25)) },
			onIncrease = { onSettingsChange(state.adjustParagraphSpacing(25)) }
		)
		ReaderControlStepper(
			label = "Margins",
			value = "${state.settings.marginPercent ?: 0}%",
			onDecrease = { onSettingsChange(state.adjustMargin(-4)) },
			onIncrease = { onSettingsChange(state.adjustMargin(4)) }
		)
	}
}

@Composable
private fun ReaderGeneralOptions(
	state: ReaderChromeState,
	onSettingsChange: (ReaderChromeState) -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		ReaderCycleRow(
			label = "Theme",
			value = readerThemeShortLabel(state.settings.theme),
			onClick = { onSettingsChange(state.toggleTheme()) }
		)
		ReaderCycleRow(
			label = "Rotation",
			value = readerOrientationShortLabel(state.settings.orientation),
			onClick = { onSettingsChange(state.toggleOrientation()) }
		)
		ReaderToggleRow(
			label = "Fullscreen",
			checked = state.settings.fullscreen == true,
			onCheckedChange = { onSettingsChange(state.toggleFullscreen()) }
		)
		ReaderCycleRow(
			label = "Tap zones",
			value = readerTapZoneShortLabel(state.settings.tapZone),
			onClick = { onSettingsChange(state.toggleTapZone()) }
		)
		ReaderToggleRow(
			label = "Smaller tap zones",
			checked = state.settings.smallerTapZone == true,
			onCheckedChange = { onSettingsChange(state.toggleSmallerTapZone()) }
		)
		ReaderControlStepper(
			label = "Dim overlay",
			value = "${state.settings.dimOverlayPercent ?: 0}%",
			onDecrease = { onSettingsChange(state.adjustDimOverlay(-10)) },
			onIncrease = { onSettingsChange(state.adjustDimOverlay(10)) }
		)
		ReaderToggleRow(
			label = "Publisher styles",
			checked = state.settings.publisherStyles == true,
			onCheckedChange = { onSettingsChange(state.togglePublisherStyles()) }
		)
		ReaderToggleRow(
			label = "Keep screen on",
			checked = state.settings.keepScreenOn == true,
			onCheckedChange = { onSettingsChange(state.toggleKeepScreenOn()) }
		)
		ReaderToggleRow(
			label = "Volume keys",
			checked = state.settings.volumeKeyPageTurns == true,
			onCheckedChange = { onSettingsChange(state.toggleVolumeKeyPageTurns()) }
		)
	}
}

@Composable
private fun ReaderMediaOptions(
	state: ReaderChromeState,
	onReadaloudToggle: () -> Unit,
	onReadaloudSpeedChange: (ReaderReadaloudPlaybackCommand) -> Unit,
	onReadaloudSyncChange: (ReaderReadaloudPlaybackCommand) -> Unit
) {
	val playback = state.readaloudPlayback
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			ReaderReadaloudButton(
				state = playback,
				onClick = onReadaloudToggle
			)
			Column(Modifier.weight(1f)) {
				Text(
					text = if (playback.isPlaying) "Readaloud playing" else "Readaloud paused",
					style = MaterialTheme.typography.labelLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					text = readerPlaybackPositionLabel(playback),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
		ReaderValueRow(
			label = "Audio label",
			value = playback.activeAudioLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "No active label"
		)
		ReaderToggleRow(
			label = "Sync highlight",
			checked = playback.syncEnabled,
			onCheckedChange = {
				playback.toggleSyncCommand()?.let(onReadaloudSyncChange)
			}
		)
		ReaderReadaloudMetadataRows(playback.activeAudioMetadata)
		ReaderControlStepper(
			label = "Speed",
			value = readerReadaloudPlaybackSpeedLabel(playback.playbackSpeed),
			onDecrease = {
				playback.adjustSpeedCommand(-0.25f)?.let(onReadaloudSpeedChange)
			},
			onIncrease = {
				playback.adjustSpeedCommand(0.25f)?.let(onReadaloudSpeedChange)
			}
		)
	}
}

@Composable
private fun ReaderReadaloudMetadataRows(metadata: paige.navic.reader.ReadaloudPlaybackMetadataLabels?) {
	metadata ?: return
	ReaderOptionalValueRow("Chapter", metadata.chapterLabel)
	ReaderOptionalValueRow("Section", metadata.sectionLabel)
	ReaderOptionalValueRow("Narrator", metadata.narratorLabel)
	ReaderOptionalValueRow("Quality", metadata.qualityLabel)
	ReaderOptionalValueRow("Source", metadata.sourceProviderLabel)
	ReaderOptionalValueRow("Format", metadata.formatLabel)
}

@Composable
private fun ReaderOptionalValueRow(
	label: String,
	value: String?
) {
	val displayValue = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
	ReaderValueRow(
		label = label,
		value = displayValue
	)
}

@Composable
private fun ReaderControlStepper(
	label: String,
	value: String,
	onDecrease: () -> Unit,
	onIncrease: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		IconButton(onClick = onDecrease) {
			Text(
				text = "-",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
		}
		Text(
			text = value,
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1
		)
		IconButton(onClick = onIncrease) {
			Text(
				text = "+",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
		}
	}
}

@Composable
private fun ReaderCycleRow(
	label: String,
	value: String,
	onClick: () -> Unit
) {
	Surface(
		onClick = onClick,
		color = MaterialTheme.colorScheme.surface,
		modifier = Modifier.fillMaxWidth()
	) {
		ReaderValueRow(
			label = label,
			value = value,
			modifier = Modifier.padding(vertical = 8.dp)
		)
	}
}

@Composable
private fun ReaderToggleRow(
	label: String,
	checked: Boolean,
	onCheckedChange: () -> Unit
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		Switch(
			checked = checked,
			onCheckedChange = { onCheckedChange() }
		)
	}
}

@Composable
private fun ReaderValueRow(
	label: String,
	value: String,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.weight(1f),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		Text(
			text = value,
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}

private fun readerPlaybackPositionLabel(state: ReaderReadaloudPlaybackUiState): String {
	val duration = state.durationMs
	val position = readerPlaybackTimeLabel(state.positionMs)
	return if (duration == null || duration <= 0L) {
		position
	} else {
		"$position / ${readerPlaybackTimeLabel(duration)}"
	}
}

private fun readerPlaybackTimeLabel(positionMs: Long): String {
	val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
	val minutes = totalSeconds / 60L
	val seconds = totalSeconds % 60L
	return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun ReaderReadaloudButton(
	state: ReaderReadaloudPlaybackUiState,
	onClick: () -> Unit
) {
	IconButton(
		onClick = onClick,
		enabled = state.isAvailable,
		modifier = Modifier.size(48.dp)
	) {
		Icon(
			imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.Play,
			contentDescription = null,
			modifier = Modifier.size(28.dp),
			tint = if (state.isAvailable) {
				MaterialTheme.colorScheme.primary
			} else {
				MaterialTheme.colorScheme.onSurfaceVariant
			}
		)
	}
}

@Composable
expect fun ReaderWebViewHost(
	publicationUrl: String,
	title: String,
	kind: ReaderPublicationKind,
	mediaOverlayEnabled: Boolean,
	settings: ReaderSettings,
	startCfi: String?,
	startHref: String?,
	command: ReaderBridgeCommand? = null,
	commandKey: Long = 0L,
	onEvent: (ReaderBridgeEvent) -> Unit,
	modifier: Modifier = Modifier
)

@Composable
expect fun ReaderOrientationEffect(orientation: String?)

@Composable
expect fun ReaderSystemBarsEffect(
	fullscreen: Boolean,
	systemBarsVisible: Boolean
)

@Composable
expect fun ReaderPublicationRuntimeHost(
	reader: Screen.Reader,
	onPublicationReady: (String) -> Unit,
	onError: (String) -> Unit
)

@Composable
expect fun ReaderReadaloudRuntimeHost(
	reader: Screen.Reader,
	readaloudSyncEnabled: Boolean,
	readerEvent: ReaderBridgeEvent?,
	readerEventKey: Long,
	onPublicationReady: (String) -> Unit,
	onReaderCommand: (ReaderBridgeCommand, Long) -> Unit,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onError: (String) -> Unit
)
