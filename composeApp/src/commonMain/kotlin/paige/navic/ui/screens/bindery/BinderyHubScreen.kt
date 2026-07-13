package paige.navic.ui.screens.bindery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_open
import navic.composeapp.generated.resources.option_ebook_only
import navic.composeapp.generated.resources.title_open_with_whispersync
import navic.composeapp.generated.resources.title_audiobook_authors
import navic.composeapp.generated.resources.title_audiobook_books
import navic.composeapp.generated.resources.title_audiobook_collections
import navic.composeapp.generated.resources.title_audiobook_continue_listening
import navic.composeapp.generated.resources.title_audiobook_continue_reading
import navic.composeapp.generated.resources.title_audiobook_findings
import navic.composeapp.generated.resources.title_audiobook_genres
import navic.composeapp.generated.resources.title_audiobook_last_read
import navic.composeapp.generated.resources.title_audiobook_most_popular
import navic.composeapp.generated.resources.title_audiobook_recently_added
import navic.composeapp.generated.resources.title_audiobook_whispersync_ready
import navic.composeapp.generated.resources.title_audiobook_wanted
import navic.composeapp.generated.resources.title_audiobooks
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.binderyCarouselCardWidthDp
import paige.navic.domain.models.normalizedBinderyBookGridColumns
import paige.navic.domain.models.OptionalIntegrationFailure
import paige.navic.domain.models.OptionalIntegrationFailureKind
import paige.navic.domain.models.OptionalIntegrationResult
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.icons.Icons
import paige.navic.icons.filled.Author
import paige.navic.icons.outlined.Book
import paige.navic.icons.outlined.CollectionBooks
import paige.navic.icons.outlined.History
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.BinderyIntegrationServices
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.OptionalIntegrationStatus
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.components.layouts.artGridError
import paige.navic.ui.components.layouts.artGridPlaceholder
import paige.navic.ui.components.layouts.horizontalSectionWithAvailableWidth
import paige.navic.ui.components.sheets.ModalBottomSheet
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.library.components.libraryScreenOverviewButton
import paige.navic.util.ui.withoutTop
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinderyHubScreen() {
	val viewModel = koinViewModel<BinderyHubViewModel>()
	val hubState by viewModel.hubState.collectAsStateWithLifecycle()
	val hubAvailability by viewModel.hubAvailability.collectAsStateWithLifecycle()
	val collectionArtworkByPath by viewModel.collectionArtworkByPath.collectAsStateWithLifecycle()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	val preferenceManager = koinInject<PreferenceManager>()
	val platformContext = LocalPlatformContext.current
	val backStack = LocalNavStack.current
	val binderyConfigured = shouldLoadBinderyUi(
		binderyEnabled = preferenceManager.binderyEnabled,
		opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
		apiKey = preferenceManager.binderyApiKey
	)
	val displayedHubAvailability = when {
		!preferenceManager.binderyEnabled -> OptionalIntegrationResult.Unavailable(
			OptionalIntegrationFailure(
				kind = OptionalIntegrationFailureKind.Disabled,
				message = "Bindery is disabled."
			)
		)
		!binderyConfigured -> OptionalIntegrationResult.Unavailable(
			OptionalIntegrationFailure(
				kind = OptionalIntegrationFailureKind.Misconfigured,
				message = "Bindery configuration is required."
			)
		)
		else -> hubAvailability
	}
	val binderyIndicators = integrationLoadingIndicators(
		binderyLoading = binderyConfigured && hubState is UiState.Loading
	)
	val imageRequestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
	val bookGridColumns = normalizedBinderyBookGridColumns(preferenceManager.binderyBookGridColumns)
	val languageFilter = normalizedBinderyLanguageFilter(preferenceManager.binderyLanguageFilter)
	var continueWhispersyncPrompt by remember {
		mutableStateOf<BinderyContinueReadingLaunchDecision.AskWhispersync?>(null)
	}
	var continueWhispersyncTargetAspectRatio by remember {
		mutableStateOf<Double?>(null)
	}
	BackToTopScrollHandler(viewModel.gridState)

	LaunchedEffect(
		binderyConfigured,
		preferenceManager.binderyOpdsBaseUrl,
		preferenceManager.binderyApiKey,
		preferenceManager.binderyLanguageFilter
	) {
		if (binderyConfigured) {
			viewModel.refreshHub(false, languageFilter)
		} else {
			viewModel.clearHub()
		}
	}

	Scaffold(
		topBar = {
			RootTopBar(
				title = { Text(stringResource(Res.string.title_audiobooks)) },
				scrollBehavior = scrollBehavior
			)
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			RootBottomBar(scrolled = scrollManager.isTriggered)
		}
	) { innerPadding ->
		BoxWithConstraints(Modifier.fillMaxSize()) {
			val readerLaunchTargetAspectRatio = binderyFullscreenCoverTargetAspectRatio(
				widthDp = maxWidth.value,
				heightDp = maxHeight.value
			)
			PullToRefreshBox(
				modifier = Modifier
					.padding(top = innerPadding.calculateTopPadding())
					.background(MaterialTheme.colorScheme.surface),
				finished = !binderyConfigured || hubState !is UiState.Loading,
				onRefresh = {
					if (binderyConfigured) {
						viewModel.refreshHub(true, languageFilter)
					} else {
						viewModel.clearHub()
					}
				},
				key = hubState
			) {
				LazyVerticalGrid(
					modifier = Modifier
						.fillMaxSize()
						.nestedScroll(scrollBehavior.nestedScrollConnection),
					columns = GridCells.Fixed(2),
					state = viewModel.gridState,
					contentPadding = innerPadding.withoutTop() + PaddingValues(top = 8.dp),
					verticalArrangement = Arrangement.spacedBy(5.dp),
					horizontalArrangement = Arrangement.spacedBy(5.dp)
				) {
					displayedHubAvailability?.let { availability ->
						item(span = { GridItemSpan(maxLineSpan) }) {
							OptionalIntegrationStatus(
								result = availability,
								modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
							)
						}
					}
					if (binderyConfigured) {
						libraryScreenOverviewButton(
							icon = Icons.Outlined.Book,
							label = Res.string.title_audiobook_books,
							destination = Screen.BinderyBooks,
							start = true
						)
						libraryScreenOverviewButton(
							icon = Icons.Outlined.CollectionBooks,
							label = Res.string.title_audiobook_collections,
							destination = Screen.BinderyCollections,
							start = false
						)
						libraryScreenOverviewButton(
							icon = Icons.Filled.Author,
							label = Res.string.title_audiobook_authors,
							destination = Screen.BinderyAuthors,
							start = true
						)
						libraryScreenOverviewButton(
							icon = Icons.Outlined.History,
							label = Res.string.title_audiobook_recently_added,
							destination = Screen.BinderyCatalog("/opds/recent", "Recently Added"),
							start = false
						)
					}

					if (binderyConfigured) {
						when (val state = hubState) {
							is UiState.Loading -> {
								val data = state.data
								if (data == null) {
									artGridPlaceholder()
								} else {
									binderyContinueRows(
										continueListening = data.continueListening,
										continueReading = data.continueReading,
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										bookGridColumns = bookGridColumns,
										onOpenListening = { item ->
											platformContext.clickSound()
											backStack.add(item.destination)
										},
										onOpenReading = { item ->
											platformContext.clickSound()
											when (
												val decision = binderyContinueReadingLaunchDecision(
													item = item,
													opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
													fullscreenCoverTargetAspectRatio = readerLaunchTargetAspectRatio
												)
											) {
												is BinderyContinueReadingLaunchDecision.OpenEbook ->
													backStack.add(decision.destination)
												is BinderyContinueReadingLaunchDecision.AskWhispersync -> {
													continueWhispersyncTargetAspectRatio = readerLaunchTargetAspectRatio
													continueWhispersyncPrompt = decision
												}
											}
										}
									)
									binderyHubDiscoveryRows(
										state = data,
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										bookGridColumns = bookGridColumns,
										languageFilter = languageFilter,
										collectionArtworkByPath = collectionArtworkByPath,
										onResolveCollectionArtwork = viewModel::resolveCollectionArtwork,
										onOpenBook = { book ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForBook(book))
										},
										onOpenCatalog = { link ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForLink(link))
										},
										onOpenFinding = { finding ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForCard(finding))
										}
									)
								}
							}
							is UiState.Error -> {
								val data = state.data
								if (data == null) {
									artGridError(state)
								} else {
									binderyContinueRows(
										continueListening = data.continueListening,
										continueReading = data.continueReading,
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										bookGridColumns = bookGridColumns,
										onOpenListening = { item ->
											platformContext.clickSound()
											backStack.add(item.destination)
										},
										onOpenReading = { item ->
											platformContext.clickSound()
											when (
												val decision = binderyContinueReadingLaunchDecision(
													item = item,
													opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
													fullscreenCoverTargetAspectRatio = readerLaunchTargetAspectRatio
												)
											) {
												is BinderyContinueReadingLaunchDecision.OpenEbook ->
													backStack.add(decision.destination)
												is BinderyContinueReadingLaunchDecision.AskWhispersync -> {
													continueWhispersyncTargetAspectRatio = readerLaunchTargetAspectRatio
													continueWhispersyncPrompt = decision
												}
											}
										}
									)
									binderyHubDiscoveryRows(
										state = data,
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										bookGridColumns = bookGridColumns,
										languageFilter = languageFilter,
										collectionArtworkByPath = collectionArtworkByPath,
										onResolveCollectionArtwork = viewModel::resolveCollectionArtwork,
										onOpenBook = { book ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForBook(book))
										},
										onOpenCatalog = { link ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForLink(link))
										},
										onOpenFinding = { finding ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForCard(finding))
										}
									)
								}
							}
							is UiState.Success -> {
								binderyContinueRows(
									continueListening = state.data.continueListening,
									continueReading = state.data.continueReading,
									baseUrl = preferenceManager.binderyOpdsBaseUrl,
									imageRequestHeaders = imageRequestHeaders,
									bookGridColumns = bookGridColumns,
									onOpenListening = { item ->
										platformContext.clickSound()
										backStack.add(item.destination)
									},
									onOpenReading = { item ->
										platformContext.clickSound()
										when (
											val decision = binderyContinueReadingLaunchDecision(
												item = item,
												opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
												fullscreenCoverTargetAspectRatio = readerLaunchTargetAspectRatio
											)
										) {
											is BinderyContinueReadingLaunchDecision.OpenEbook ->
												backStack.add(decision.destination)
											is BinderyContinueReadingLaunchDecision.AskWhispersync -> {
												continueWhispersyncTargetAspectRatio = readerLaunchTargetAspectRatio
												continueWhispersyncPrompt = decision
											}
										}
									}
								)
								binderyHubDiscoveryRows(
									state = state.data,
									baseUrl = preferenceManager.binderyOpdsBaseUrl,
									imageRequestHeaders = imageRequestHeaders,
									bookGridColumns = bookGridColumns,
									languageFilter = languageFilter,
									collectionArtworkByPath = collectionArtworkByPath,
									onResolveCollectionArtwork = viewModel::resolveCollectionArtwork,
									onOpenBook = { book ->
										platformContext.clickSound()
										backStack.add(binderyDestinationForBook(book))
									},
									onOpenCatalog = { link ->
										platformContext.clickSound()
										backStack.add(binderyDestinationForLink(link))
									},
									onOpenFinding = { finding ->
										platformContext.clickSound()
										backStack.add(binderyDestinationForCard(finding))
									}
								)
							}
						}
					}
				}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = binderyIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = binderyIndicators,
					relevantServices = BinderyIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}

	ErrorSnackbar(
		error = (hubState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)

	continueWhispersyncPrompt?.let { prompt ->
		BinderyContinueWhispersyncSheet(
			decision = prompt,
			opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
			fullscreenCoverTargetAspectRatio = continueWhispersyncTargetAspectRatio,
			onDismissRequest = {
				continueWhispersyncTargetAspectRatio = null
				continueWhispersyncPrompt = null
			},
			onOpenReader = { destination ->
				continueWhispersyncTargetAspectRatio = null
				continueWhispersyncPrompt = null
				backStack.add(destination)
			}
		)
	}
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.binderyContinueRows(
	continueListening: List<BinderyContinueListeningItem>,
	continueReading: List<BinderyContinueReadingItem>,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	bookGridColumns: Int,
	onOpenListening: (BinderyContinueListeningItem) -> Unit,
	onOpenReading: (BinderyContinueReadingItem) -> Unit
) {
	binderyContinueListeningRow(
		items = continueListening,
		baseUrl = baseUrl,
		imageRequestHeaders = imageRequestHeaders,
		bookGridColumns = bookGridColumns,
		onOpen = onOpenListening
	)
	binderyContinueReadingRow(
		items = continueReading,
		baseUrl = baseUrl,
		imageRequestHeaders = imageRequestHeaders,
		bookGridColumns = bookGridColumns,
		onOpen = onOpenReading
	)
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.binderyContinueListeningRow(
	items: List<BinderyContinueListeningItem>,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	bookGridColumns: Int,
	onOpen: (BinderyContinueListeningItem) -> Unit
) {
	if (items.isEmpty()) return
	horizontalSectionWithAvailableWidth(
		title = Res.string.title_audiobook_continue_listening,
		destination = Screen.Audiobooks,
		state = UiState.Success(items),
		key = { it.key },
		seeAll = false
	) { item, availableWidth ->
		val cardWidth = binderyCarouselCardWidthDp(
			columns = bookGridColumns,
			availableWidthDp = availableWidth.value.roundToInt()
		).dp
		var coverAspectRatio by remember(item.key, item.imageHref) {
			mutableStateOf(binderyContinueListeningCoverAspectRatio(width = null, height = null))
		}
		ArtGridItem(
			modifier = Modifier.animateItem().width(cardWidth),
			onClick = { onOpen(item) },
			coverArtId = null,
			imageUrl = item.imageHref?.let { binderyEndpoint(baseUrl, it) },
			imageRequestHeaders = imageRequestHeaders,
			title = item.title,
			subtitle = item.subtitle,
			coverAspectRatio = coverAspectRatio,
			coverContentScale = if (coverAspectRatio >= 1f) ContentScale.Crop else ContentScale.Fit,
			fallbackKind = "Audiobook",
			onImageSizeResolved = { width, height ->
				val resolved = binderyContinueListeningCoverAspectRatio(width, height)
				if (coverAspectRatio != resolved) {
					coverAspectRatio = resolved
				}
			},
			id = item.key,
			tab = "bindery-continue-listening"
		)
	}
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.binderyContinueReadingRow(
	items: List<BinderyContinueReadingItem>,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	bookGridColumns: Int,
	onOpen: (BinderyContinueReadingItem) -> Unit
) {
	if (items.isEmpty()) return
	horizontalSectionWithAvailableWidth(
		title = Res.string.title_audiobook_continue_reading,
		destination = Screen.Audiobooks,
		state = UiState.Success(items),
		key = { it.key },
		seeAll = false
	) { item, availableWidth ->
		val cardWidth = binderyCarouselCardWidthDp(
			columns = bookGridColumns,
			availableWidthDp = availableWidth.value.roundToInt()
		).dp
		ArtGridItem(
			modifier = Modifier.animateItem().width(cardWidth),
			onClick = { onOpen(item) },
			coverArtId = null,
			imageUrl = item.imageHref?.let { binderyEndpoint(baseUrl, it) },
			imageRequestHeaders = imageRequestHeaders,
			title = item.title,
			subtitle = item.subtitle,
			coverAspectRatio = 2f / 3f,
			coverContentScale = ContentScale.Fit,
			fallbackKind = "Book",
			id = item.key,
			tab = "bindery-continue-reading"
		)
	}
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.binderyHubDiscoveryRows(
	state: BinderyHubState,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	bookGridColumns: Int,
	languageFilter: String?,
	collectionArtworkByPath: Map<String, String>,
	onResolveCollectionArtwork: (BinderyCatalogCard.Link) -> Unit,
	onOpenBook: (BinderyCatalogCard.Book) -> Unit,
	onOpenCatalog: (BinderyCatalogCard.Link) -> Unit,
	onOpenFinding: (BinderyCatalogCard.Finding) -> Unit
) {
	binderyWhispersyncReadyAudiobookRow(
		items = state.whispersyncReadyAudiobooks(languageFilter),
		baseUrl = baseUrl,
		imageRequestHeaders = imageRequestHeaders,
		bookGridColumns = bookGridColumns,
		languageFilter = languageFilter,
		collectionArtworkByPath = collectionArtworkByPath,
		onResolveCollectionArtwork = onResolveCollectionArtwork,
		onOpenBook = onOpenBook,
		onOpenCatalog = onOpenCatalog,
		onOpenFinding = onOpenFinding
	)
	binderyHubRows(
		rows = state.rows,
		baseUrl = baseUrl,
		imageRequestHeaders = imageRequestHeaders,
		bookGridColumns = bookGridColumns,
		languageFilter = languageFilter,
		collectionArtworkByPath = collectionArtworkByPath,
		onResolveCollectionArtwork = onResolveCollectionArtwork,
		onOpenBook = onOpenBook,
		onOpenCatalog = onOpenCatalog,
		onOpenFinding = onOpenFinding
	)
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.binderyWhispersyncReadyAudiobookRow(
	items: List<BinderyCatalogCard.Book>,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	bookGridColumns: Int,
	languageFilter: String?,
	collectionArtworkByPath: Map<String, String>,
	onResolveCollectionArtwork: (BinderyCatalogCard.Link) -> Unit,
	onOpenBook: (BinderyCatalogCard.Book) -> Unit,
	onOpenCatalog: (BinderyCatalogCard.Link) -> Unit,
	onOpenFinding: (BinderyCatalogCard.Finding) -> Unit
) {
	if (items.isEmpty()) return
	horizontalSectionWithAvailableWidth(
		title = Res.string.title_audiobook_whispersync_ready,
		destination = Screen.Audiobooks,
		state = UiState.Success(items),
		key = { card -> "whispersync-ready-${card.id}" },
		seeAll = false
	) { card, availableWidth ->
		val cardWidth = binderyCarouselCardWidthDp(
			columns = bookGridColumns,
			availableWidthDp = availableWidth.value.roundToInt()
		).dp
		BinderyHubCard(
			modifier = Modifier.animateItem().width(cardWidth),
			card = card,
			baseUrl = baseUrl,
			imageRequestHeaders = imageRequestHeaders,
			languageFilter = languageFilter,
			collectionArtworkByPath = collectionArtworkByPath,
			onResolveCollectionArtwork = onResolveCollectionArtwork,
			onOpenBook = onOpenBook,
			onOpenCatalog = onOpenCatalog,
			onOpenFinding = onOpenFinding
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinderyContinueWhispersyncSheet(
	decision: BinderyContinueReadingLaunchDecision.AskWhispersync,
	opdsBaseUrl: String,
	fullscreenCoverTargetAspectRatio: Double?,
	onDismissRequest: () -> Unit,
	onOpenReader: (Screen.Reader) -> Unit
) {
	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 20.dp, vertical = 8.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(
				text = stringResource(Res.string.title_open_with_whispersync),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
			Text(
				text = decision.ebookDestination.title,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			Button(
				onClick = { onOpenReader(decision.ebookDestination) },
				modifier = Modifier.fillMaxWidth()
			) {
				Text(stringResource(Res.string.option_ebook_only))
			}
			decision.matches.forEach { match ->
				val destination = binderyContinueReadingWhispersyncDestination(
					decision = decision,
					match = match,
					opdsBaseUrl = opdsBaseUrl,
					fullscreenCoverTargetAspectRatio = fullscreenCoverTargetAspectRatio
				)
				Surface(
					modifier = Modifier.fillMaxWidth(),
					shape = RoundedCornerShape(8.dp),
					color = MaterialTheme.colorScheme.surfaceContainerHighest
				) {
					Row(
						modifier = Modifier.padding(12.dp),
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(
							modifier = Modifier.weight(1f),
							verticalArrangement = Arrangement.spacedBy(3.dp)
						) {
							Text(
								text = match.oppositeTitle,
								style = MaterialTheme.typography.titleSmall,
								maxLines = 2,
								overflow = TextOverflow.Ellipsis
							)
							Text(
								text = listOfNotNull(
									"Ready",
									match.coveragePercent?.let { "Coverage $it%" },
									match.scorePercent?.let { "Score $it%" }
								).joinToString("  "),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Button(
							onClick = {
								destination?.let(onOpenReader)
							},
							enabled = destination != null
						) {
							Text(stringResource(Res.string.action_open))
						}
					}
				}
			}
			TextButton(
				onClick = onDismissRequest,
				modifier = Modifier.align(Alignment.End)
			) {
				Text(stringResource(Res.string.action_cancel))
			}
		}
	}
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.binderyHubRows(
	rows: List<BinderyHubCatalogRow>,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	bookGridColumns: Int,
	languageFilter: String?,
	collectionArtworkByPath: Map<String, String>,
	onResolveCollectionArtwork: (BinderyCatalogCard.Link) -> Unit,
	onOpenBook: (BinderyCatalogCard.Book) -> Unit,
	onOpenCatalog: (BinderyCatalogCard.Link) -> Unit,
	onOpenFinding: (BinderyCatalogCard.Finding) -> Unit
) {
	rows.forEach { row ->
		val cards = row.cards(languageFilter)
		if (cards.isEmpty()) return@forEach
		horizontalSectionWithAvailableWidth(
			title = row.row.kind.titleResource(),
			destination = Screen.BinderyCatalog(row.row.path, row.row.title),
			state = UiState.Success(cards),
			key = { it.id },
			seeAll = true
		) { card, availableWidth ->
			val cardWidth = binderyCarouselCardWidthDp(
				columns = bookGridColumns,
				availableWidthDp = availableWidth.value.roundToInt()
			).dp
			BinderyHubCard(
				modifier = Modifier.animateItem().width(cardWidth),
				card = card,
				baseUrl = baseUrl,
				imageRequestHeaders = imageRequestHeaders,
				languageFilter = languageFilter,
				collectionArtworkByPath = collectionArtworkByPath,
				onResolveCollectionArtwork = onResolveCollectionArtwork,
				onOpenBook = onOpenBook,
				onOpenCatalog = onOpenCatalog,
				onOpenFinding = onOpenFinding
			)
		}
	}
}

@Composable
private fun BinderyHubCard(
	modifier: Modifier = Modifier,
	card: BinderyCatalogCard,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	languageFilter: String?,
	collectionArtworkByPath: Map<String, String>,
	onResolveCollectionArtwork: (BinderyCatalogCard.Link) -> Unit,
	onOpenBook: (BinderyCatalogCard.Book) -> Unit,
	onOpenCatalog: (BinderyCatalogCard.Link) -> Unit,
	onOpenFinding: (BinderyCatalogCard.Finding) -> Unit
) {
	when (card) {
		is BinderyCatalogCard.Book -> {
			val visualPolicy = binderyCatalogCardVisualPolicy(card)
			ArtGridItem(
				modifier = modifier.alpha(card.availabilityAlpha(languageFilter)),
				onClick = { onOpenBook(card) },
				coverArtId = null,
				imageUrl = card.imageUrl?.let { binderyEndpoint(baseUrl, it) },
				imageRequestHeaders = imageRequestHeaders,
				title = card.title,
				subtitle = card.subtitle,
				ownershipStatus = card.availabilityStatus(languageFilter),
				coverAspectRatio = visualPolicy.coverAspectRatio,
				coverContentScale = if (visualPolicy.imageContentScaleFit) ContentScale.Fit else ContentScale.Crop,
				coverOverlay = binderyBookCoverOverlay(
					hasActionableWhispersync = card.hasActionableWhispersync,
					action = null,
					loading = false,
					onAction = {}
				),
				fallbackKind = "Book",
				id = card.id,
				tab = "bindery-hub"
			)
		}
		is BinderyCatalogCard.Link -> {
			LaunchedEffect(card.path, card.imageUrl) {
				onResolveCollectionArtwork(card)
			}
			val imageUrl = card.imageUrl ?: collectionArtworkByPath[card.path]
			val visualPolicy = binderyCatalogCardVisualPolicy(card)
			ArtGridItem(
				modifier = modifier.alpha(card.availabilityAlpha(languageFilter)),
				onClick = { onOpenCatalog(card) },
				coverArtId = null,
				imageUrl = imageUrl?.let { binderyEndpoint(baseUrl, it) },
				imageRequestHeaders = imageRequestHeaders,
				title = card.title,
				subtitle = card.subtitle,
				ownershipStatus = card.availabilityStatus(languageFilter),
				coverAspectRatio = visualPolicy.coverAspectRatio,
				coverContentScale = if (visualPolicy.imageContentScaleFit) ContentScale.Fit else ContentScale.Crop,
				fallbackKind = card.subtitle,
				id = card.id,
				tab = "bindery-hub"
			)
		}
		is BinderyCatalogCard.Finding -> {
			val visualPolicy = binderyCatalogCardVisualPolicy(card)
			ArtGridItem(
				modifier = modifier.alpha(card.availabilityAlpha(languageFilter)),
				onClick = { onOpenFinding(card) },
				coverArtId = null,
				imageUrl = card.imageUrl?.let { binderyEndpoint(baseUrl, it) },
				imageRequestHeaders = imageRequestHeaders,
				title = card.title,
				subtitle = card.subtitle,
				ownershipStatus = card.availabilityStatus(languageFilter),
				coverAspectRatio = visualPolicy.coverAspectRatio,
				coverContentScale = if (visualPolicy.imageContentScaleFit) ContentScale.Fit else ContentScale.Crop,
				fallbackKind = "Finding",
				id = card.id,
				tab = "bindery-hub"
			)
		}
	}
}

private fun BinderyHubRowKind.titleResource(): StringResource =
	when (this) {
		BinderyHubRowKind.LastRead -> Res.string.title_audiobook_last_read
		BinderyHubRowKind.RecentlyAdded -> Res.string.title_audiobook_recently_added
		BinderyHubRowKind.MostPopular -> Res.string.title_audiobook_most_popular
		BinderyHubRowKind.Audiobooks -> Res.string.title_audiobooks
		BinderyHubRowKind.Genres -> Res.string.title_audiobook_genres
		BinderyHubRowKind.Findings -> Res.string.title_audiobook_findings
		BinderyHubRowKind.Authors -> Res.string.title_audiobook_authors
		BinderyHubRowKind.Collections -> Res.string.title_audiobook_collections
		BinderyHubRowKind.Wanted -> Res.string.title_audiobook_wanted
	}
