package paige.navic.ui.screens.bindery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_audiobook_authors
import navic.composeapp.generated.resources.title_audiobook_books
import navic.composeapp.generated.resources.title_audiobook_collections
import navic.composeapp.generated.resources.title_audiobook_findings
import navic.composeapp.generated.resources.title_audiobook_genres
import navic.composeapp.generated.resources.title_audiobook_last_read
import navic.composeapp.generated.resources.title_audiobook_most_popular
import navic.composeapp.generated.resources.title_audiobook_recently_added
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
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.components.layouts.artGridError
import paige.navic.ui.components.layouts.artGridPlaceholder
import paige.navic.ui.components.layouts.horizontalSectionWithAvailableWidth
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
	val binderyIndicators = integrationLoadingIndicators(
		binderyLoading = binderyConfigured && hubState is UiState.Loading
	)
	val imageRequestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
	val bookGridColumns = normalizedBinderyBookGridColumns(preferenceManager.binderyBookGridColumns)
	val languageFilter = normalizedBinderyLanguageFilter(preferenceManager.binderyLanguageFilter)
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
		Box(Modifier.fillMaxSize()) {
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
									binderyHubRows(
										rows = data.rows,
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										bookGridColumns = bookGridColumns,
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
									binderyHubRows(
										rows = data.rows,
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										bookGridColumns = bookGridColumns,
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
							is UiState.Success -> binderyHubRows(
								rows = state.data.rows,
								baseUrl = preferenceManager.binderyOpdsBaseUrl,
								imageRequestHeaders = imageRequestHeaders,
								bookGridColumns = bookGridColumns,
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
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.binderyHubRows(
	rows: List<BinderyHubCatalogRow>,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	bookGridColumns: Int,
	collectionArtworkByPath: Map<String, String>,
	onResolveCollectionArtwork: (BinderyCatalogCard.Link) -> Unit,
	onOpenBook: (BinderyCatalogCard.Book) -> Unit,
	onOpenCatalog: (BinderyCatalogCard.Link) -> Unit,
	onOpenFinding: (BinderyCatalogCard.Finding) -> Unit
) {
	rows.forEach { row ->
		val cards = row.cards
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
				modifier = modifier.alpha(card.availabilityAlpha()),
				onClick = { onOpenBook(card) },
				coverArtId = null,
				imageUrl = card.imageUrl?.let { binderyEndpoint(baseUrl, it) },
				imageRequestHeaders = imageRequestHeaders,
				title = card.title,
				subtitle = card.subtitle,
				ownershipStatus = card.availabilityStatus(),
				coverAspectRatio = visualPolicy.coverAspectRatio,
				coverContentScale = if (visualPolicy.imageContentScaleFit) ContentScale.Fit else ContentScale.Crop,
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
				modifier = modifier.alpha(card.availabilityAlpha()),
				onClick = { onOpenCatalog(card) },
				coverArtId = null,
				imageUrl = imageUrl?.let { binderyEndpoint(baseUrl, it) },
				imageRequestHeaders = imageRequestHeaders,
				title = card.title,
				subtitle = card.subtitle,
				ownershipStatus = card.availabilityStatus(),
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
				modifier = modifier.alpha(card.availabilityAlpha()),
				onClick = { onOpenFinding(card) },
				coverArtId = null,
				imageUrl = card.imageUrl?.let { binderyEndpoint(baseUrl, it) },
				imageRequestHeaders = imageRequestHeaders,
				title = card.title,
				subtitle = card.subtitle,
				ownershipStatus = card.availabilityStatus(),
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
