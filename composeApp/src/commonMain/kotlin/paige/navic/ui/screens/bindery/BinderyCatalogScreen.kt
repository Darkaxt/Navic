package paige.navic.ui.screens.bindery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
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
import navic.composeapp.generated.resources.title_audiobooks
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.normalizedBinderyBookGridColumns
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.ArtGrid
import paige.navic.ui.components.layouts.ArtGridPlaceholder
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.layouts.artGridError
import paige.navic.ui.components.layouts.artGridPlaceholder
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.util.ui.withoutTop

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinderyCatalogScreen(
	tab: BinderyCatalogTab? = BinderyCatalogTab.Audiobooks,
	path: String = tab?.path ?: BinderyCatalogTab.Audiobooks.path,
	title: String? = null
) {
	val viewModel = koinViewModel<BinderyCatalogViewModel>(
		key = path,
		parameters = { parametersOf(path) }
	)
	val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
	val hasNextPage by viewModel.hasNextPage.collectAsStateWithLifecycle()
	val isLoadingNextPage by viewModel.isLoadingNextPage.collectAsStateWithLifecycle()
	val collectionArtworkByPath by viewModel.collectionArtworkByPath.collectAsStateWithLifecycle()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val titleText = title ?: stringResource(tab?.titleResource() ?: Res.string.title_audiobooks)
	val binderyConfigured = shouldLoadBinderyUi(
		binderyEnabled = preferenceManager.binderyEnabled,
		opdsBaseUrl = preferenceManager.binderyOpdsBaseUrl,
		apiKey = preferenceManager.binderyApiKey
	)
	val binderyIndicators = integrationLoadingIndicators(
		binderyLoading = binderyConfigured && catalogState is UiState.Loading
	)
	val imageRequestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
	val bookGridColumns = normalizedBinderyBookGridColumns(preferenceManager.binderyBookGridColumns)
	val languageFilter = normalizedBinderyLanguageFilter(preferenceManager.binderyLanguageFilter)

	LaunchedEffect(
		binderyConfigured,
		preferenceManager.binderyOpdsBaseUrl,
		preferenceManager.binderyApiKey,
		preferenceManager.binderyLanguageFilter,
		path
	) {
		if (binderyConfigured) {
			viewModel.refreshCatalog(false, languageFilter)
		} else {
			viewModel.clearCatalog()
		}
	}

	Scaffold(
		topBar = {
			RootTopBar(
				title = { Text(titleText) },
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
				finished = !binderyConfigured || catalogState !is UiState.Loading,
				onRefresh = {
					if (binderyConfigured) {
						viewModel.refreshCatalog(true, languageFilter)
					} else {
						viewModel.clearCatalog()
					}
				},
				key = catalogState
			) {
				ArtGrid(
					modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
					state = viewModel.gridState,
					contentPadding = innerPadding.withoutTop(),
					fixedColumns = bookGridColumns,
					verticalArrangement = if ((catalogState as? UiState.Success)?.data?.let {
							binderyCatalogCards(it, tab).isEmpty()
						} == true) {
						Arrangement.Center
					} else {
						Arrangement.spacedBy(12.dp)
					}
				) {
					if (binderyConfigured) {
						when (val state = catalogState) {
							is UiState.Loading -> {
								if (state.data == null) {
									artGridPlaceholder(coverAspectRatio = binderyPlaceholderCoverAspectRatio(tab, path))
								} else {
									binderyCatalogItems(
										cards = binderyCatalogCards(state.data, tab),
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										collectionArtworkByPath = collectionArtworkByPath,
										hasNextPage = hasNextPage,
										isLoadingNextPage = isLoadingNextPage,
										onResolveCollectionArtwork = viewModel::resolveCollectionArtwork,
										onLoadNextPage = viewModel::loadNextPage,
										onOpenBook = { book ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForBook(book))
										},
										onOpenCatalog = { link ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForLink(link))
										}
									)
								}
							}
							is UiState.Error -> {
								val data = state.data
								if (data == null) {
									artGridError(state)
								} else {
									binderyCatalogItems(
										cards = binderyCatalogCards(data, tab),
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										collectionArtworkByPath = collectionArtworkByPath,
										hasNextPage = hasNextPage,
										isLoadingNextPage = isLoadingNextPage,
										onResolveCollectionArtwork = viewModel::resolveCollectionArtwork,
										onLoadNextPage = viewModel::loadNextPage,
										onOpenBook = { book ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForBook(book))
										},
										onOpenCatalog = { link ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForLink(link))
										}
									)
								}
							}
							is UiState.Success -> binderyCatalogItems(
								cards = binderyCatalogCards(state.data, tab),
								baseUrl = preferenceManager.binderyOpdsBaseUrl,
								imageRequestHeaders = imageRequestHeaders,
								collectionArtworkByPath = collectionArtworkByPath,
								hasNextPage = hasNextPage,
								isLoadingNextPage = isLoadingNextPage,
								onResolveCollectionArtwork = viewModel::resolveCollectionArtwork,
								onLoadNextPage = viewModel::loadNextPage,
								onOpenBook = { book ->
									platformContext.clickSound()
									backStack.add(binderyDestinationForBook(book))
								},
								onOpenCatalog = { link ->
									platformContext.clickSound()
									backStack.add(binderyDestinationForLink(link))
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
					loadingIndicators = binderyIndicators
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = innerPadding.calculateTopPadding() + 8.dp)
			)
		}
	}

	ErrorSnackbar(
		error = (catalogState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.binderyCatalogItems(
	cards: List<BinderyCatalogCard>,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	collectionArtworkByPath: Map<String, String>,
	hasNextPage: Boolean,
	isLoadingNextPage: Boolean,
	onResolveCollectionArtwork: (BinderyCatalogCard.Link) -> Unit,
	onLoadNextPage: () -> Unit,
	onOpenBook: (BinderyCatalogCard.Book) -> Unit,
	onOpenCatalog: (BinderyCatalogCard.Link) -> Unit
) {
	items(cards, key = { it.id }) { card ->
		when (card) {
			is BinderyCatalogCard.Book -> {
				val visualPolicy = binderyCatalogCardVisualPolicy(card)
				ArtGridItem(
					modifier = Modifier
						.animateItem()
						.alpha(card.availabilityAlpha()),
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
					tab = "bindery"
				)
			}
			is BinderyCatalogCard.Link -> {
				LaunchedEffect(card.path, card.imageUrl) {
					onResolveCollectionArtwork(card)
				}
				val imageUrl = card.imageUrl ?: collectionArtworkByPath[card.path]
				val visualPolicy = binderyCatalogCardVisualPolicy(card)
				ArtGridItem(
					modifier = Modifier
						.animateItem()
						.alpha(card.availabilityAlpha()),
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
					tab = "bindery"
				)
			}
		}
	}
	if (hasNextPage) {
		item(
			key = "bindery-load-next-page",
			span = { GridItemSpan(1) }
		) {
			LaunchedEffect(cards.size, hasNextPage) {
				if (!isLoadingNextPage) {
					onLoadNextPage()
				}
			}
			ArtGridPlaceholder(
				modifier = Modifier.animateItem(),
				coverAspectRatio = 2f / 3f
			)
		}
	}
}

private fun BinderyCatalogTab.titleResource(): StringResource =
	when (this) {
		BinderyCatalogTab.Audiobooks -> Res.string.title_audiobooks
		BinderyCatalogTab.Books -> Res.string.title_audiobook_books
		BinderyCatalogTab.Collections -> Res.string.title_audiobook_collections
		BinderyCatalogTab.Authors -> Res.string.title_audiobook_authors
	}

private fun binderyPlaceholderCoverAspectRatio(
	tab: BinderyCatalogTab?,
	path: String
): Float {
	val normalizedPath = path.trim().trimEnd('/').substringBefore('?').lowercase()
	return if (tab == BinderyCatalogTab.Audiobooks ||
		tab == BinderyCatalogTab.Books ||
		normalizedPath == "/opds/books" ||
		normalizedPath == "/opds/formats/audiobook"
	) {
		2f / 3f
	} else {
		1f
	}
}
