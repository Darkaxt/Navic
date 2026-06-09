package paige.navic.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.ReaderReadaloudPlaybackCommand
import paige.navic.reader.ReaderReadaloudPlaybackUiState
import paige.navic.reader.ReaderSerifFontFamily
import paige.navic.reader.ReaderSettings
import paige.navic.reader.ReaderSearchResult
import paige.navic.reader.ReaderTocItem
import paige.navic.reader.decodeReaderAnnotations
import paige.navic.reader.decodeReaderBookmarks
import paige.navic.reader.encodeReaderAnnotations
import paige.navic.reader.encodeReaderBookmarks
import paige.navic.reader.normalizedReaderSettings
import paige.navic.reader.readerBookmarkFromLocator
import paige.navic.reader.readerDefaultSettings
import paige.navic.reader.readerReadaloudControlsVisible
import paige.navic.reader.setReaderDefaultSettings
import paige.navic.reader.toBinderyReadingProgress
import paige.navic.reader.toReaderStartLocatorFor
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(reader: Screen.Reader) {
	val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
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
		preferenceManager.readerMarginPercent,
		preferenceManager.readerTheme,
		preferenceManager.readerPaged,
		preferenceManager.readerWebContentsDebuggingEnabled
	) {
		preferenceManager.readerDefaultSettings()
	}
	var chromeState by remember(reader.publicationUrl, defaultReaderSettings) {
		mutableStateOf(ReaderChromeState(settings = defaultReaderSettings))
	}
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

	fun updateChromeSettings(nextState: ReaderChromeState) {
		val normalizedState = nextState.copy(settings = nextState.settings.normalizedReaderSettings())
		chromeState = normalizedState
		preferenceManager.setReaderDefaultSettings(normalizedState.settings)
		dispatchReaderCommand(normalizedState.toSettingsCommand())
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
		modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = {
			RootTopBar(
				title = { Text(reader.title) },
				scrollBehavior = scrollBehavior
			)
		},
		bottomBar = {
			ReaderBottomChrome(
				title = reader.title,
				state = chromeState,
				showReadaloudControls = readerReadaloudControlsVisible(
					kind = reader.kind,
					mediaOverlayEnabled = reader.mediaOverlayEnabled
				),
				onSettingsChange = { nextState ->
					platformContext.clickSound()
					updateChromeSettings(nextState)
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
				},
				onReadaloudToggle = {
					val command = chromeState.readaloudPlayback.toggleCommand() ?: return@ReaderBottomChrome
					platformContext.clickSound()
					readaloudCommand = command
					readaloudCommandKey += 1L
				}
			)
		}
	) { innerPadding ->
		Box(
			Modifier
				.padding(innerPadding)
				.fillMaxSize()
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
}

@Composable
private fun ReaderBottomChrome(
	title: String,
	state: ReaderChromeState,
	showReadaloudControls: Boolean,
	onSettingsChange: (ReaderChromeState) -> Unit,
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
	onReadaloudToggle: () -> Unit
) {
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
				Column(Modifier.weight(1f)) {
					Text(
						text = state.currentSectionTitle ?: title,
						style = MaterialTheme.typography.labelLarge,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Text(
						text = state.progressLabel,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1
					)
				}
				if (showReadaloudControls) {
					ReaderReadaloudButton(
						state = state.readaloudPlayback,
						onClick = onReadaloudToggle
					)
				}
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
			ReaderTypographyControls(
				state = state,
				onSettingsChange = onSettingsChange,
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
			)
		}
	}
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

@Composable
private fun ReaderTypographyControls(
	state: ReaderChromeState,
	onSettingsChange: (ReaderChromeState) -> Unit,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier.horizontalScroll(rememberScrollState()),
		horizontalArrangement = Arrangement.spacedBy(2.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		IconButton(onClick = { onSettingsChange(state.adjustFontSize(-8)) }) {
			Text(
				text = "A-",
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.SemiBold
			)
		}
		Text(
			text = "${state.settings.fontSizePercent ?: 100}%",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		IconButton(onClick = { onSettingsChange(state.adjustFontSize(8)) }) {
			Text(
				text = "A+",
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.SemiBold
			)
		}
		IconButton(onClick = { onSettingsChange(state.toggleFontFamily()) }) {
			Text(
				text = if (state.settings.fontFamily == ReaderSerifFontFamily) "Sans" else "Serif",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.SemiBold
			)
		}
		IconButton(onClick = { onSettingsChange(state.adjustLineHeight(-0.1)) }) {
			Text(
				text = "LH-",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.SemiBold
			)
		}
		Text(
			text = "${state.settings.lineHeight ?: 1.55}",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		IconButton(onClick = { onSettingsChange(state.adjustLineHeight(0.1)) }) {
			Text(
				text = "LH+",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.SemiBold
			)
		}
		IconButton(onClick = { onSettingsChange(state.adjustMargin(-4)) }) {
			Text(
				text = "M-",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.SemiBold
			)
		}
		Text(
			text = "${state.settings.marginPercent ?: 0}%",
			style = MaterialTheme.typography.labelSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		IconButton(onClick = { onSettingsChange(state.adjustMargin(4)) }) {
			Text(
				text = "M+",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.SemiBold
			)
		}
		IconButton(onClick = { onSettingsChange(state.toggleTheme()) }) {
			Text(
				text = if (state.settings.theme == "dark") "Light" else "Dark",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.SemiBold
			)
		}
		IconButton(onClick = { onSettingsChange(state.togglePagedMode()) }) {
			Text(
				text = if (state.settings.paged != false) "Page" else "Scroll",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.SemiBold
			)
		}
	}
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
expect fun ReaderPublicationRuntimeHost(
	reader: Screen.Reader,
	onPublicationReady: (String) -> Unit,
	onError: (String) -> Unit
)

@Composable
expect fun ReaderReadaloudRuntimeHost(
	reader: Screen.Reader,
	readerEvent: ReaderBridgeEvent?,
	readerEventKey: Long,
	onPublicationReady: (String) -> Unit,
	onReaderCommand: (ReaderBridgeCommand, Long) -> Unit,
	playbackCommand: ReaderReadaloudPlaybackCommand?,
	playbackCommandKey: Long,
	onPlaybackState: (ReaderReadaloudPlaybackUiState) -> Unit,
	onError: (String) -> Unit
)
