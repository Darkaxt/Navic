package paige.navic.ui.screens.bindery

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_no_search_results
import navic.composeapp.generated.resources.title_all
import navic.composeapp.generated.resources.title_audiobook_authors
import navic.composeapp.generated.resources.title_audiobook_books
import navic.composeapp.generated.resources.title_audiobook_collections
import navic.composeapp.generated.resources.title_audiobook_findings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Check
import paige.navic.icons.outlined.NoSearchResults
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.ErrorBox
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.layouts.ArtGrid
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.artGridPlaceholder
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.search.components.SearchScreenTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinderySearchScreen(
	nested: Boolean,
	initialQuery: String = ""
) {
	val viewModel = koinViewModel<BinderySearchViewModel>()
	val preferenceManager = koinInject<PreferenceManager>()
	val state by viewModel.searchState.collectAsStateWithLifecycle()
	val actionError by viewModel.actionError.collectAsStateWithLifecycle()
	val actionInFlight by viewModel.actionInFlight.collectAsStateWithLifecycle()
	val query = viewModel.searchQuery
	val imageRequestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
	val languageFilter = normalizedBinderyLanguageFilter(preferenceManager.binderyLanguageFilter)
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	var selectedCategory by remember { mutableStateOf(BinderySearchCategory.All) }

	LaunchedEffect(initialQuery) {
		viewModel.setInitialQuery(initialQuery)
	}

	Scaffold(
		topBar = {
			Column(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.surface)
					.padding(TopAppBarDefaults.windowInsets.asPaddingValues())
			) {
				SearchScreenTopBar(
					query = query,
					nested = nested,
					onSearch = {}
				)
				BinderySearchChips(
					selectedCategory = selectedCategory,
					onCategorySelect = { selectedCategory = it }
				)
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (!nested || preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { contentPadding ->
		AnimatedContent(
			state,
			modifier = Modifier.fillMaxSize()
		) { uiState ->
			when (uiState) {
				is UiState.Loading -> ArtGrid(contentPadding = contentPadding) {
					artGridPlaceholder(coverAspectRatio = 2f / 3f)
				}
				is UiState.Error -> ErrorBox(uiState, padding = contentPadding)
				is UiState.Success -> {
					val results = binderySearchResultsForCategory(uiState.data, selectedCategory)
					Box(Modifier.fillMaxSize()) {
						if (query.text.isNotBlank() && results.isEmpty()) {
							ContentUnavailable(
								icon = Icons.Outlined.NoSearchResults,
								label = stringResource(Res.string.info_no_search_results)
							)
						}
						ArtGrid(
							state = viewModel.gridState,
							contentPadding = contentPadding,
							verticalArrangement = Arrangement.spacedBy(12.dp)
						) {
							itemsIndexed(
								results,
								key = { index, result ->
									binderyUiStableKey(
										prefix = "bindery-search-${result.tab}",
										index = index,
										result.card.id,
										result.card.title
									)
								}
							) { _, result ->
								BinderySearchResultItem(
									modifier = Modifier.animateItem(),
									result = result,
									baseUrl = preferenceManager.binderyOpdsBaseUrl,
									imageRequestHeaders = imageRequestHeaders,
									languageFilter = languageFilter,
									actionInFlight = actionInFlight,
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
									},
									onAction = viewModel::performAction
								)
							}
						}
					}
				}
			}
		}
	}

	ErrorSnackbar(
		error = actionError,
		onClearError = viewModel::clearActionError
	)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BinderySearchChips(
	selectedCategory: BinderySearchCategory,
	onCategorySelect: (BinderySearchCategory) -> Unit
) {
	val platformContext = LocalPlatformContext.current
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		BinderySearchCategory.entries.forEach { category ->
			val isSelected = category == selectedCategory
			FilterChip(
				modifier = Modifier.animateContentSize(
					if (isSelected) {
						MaterialTheme.motionScheme.fastSpatialSpec()
					} else {
						MaterialTheme.motionScheme.defaultEffectsSpec()
					}
				),
				selected = isSelected,
				onClick = {
					platformContext.clickSound()
					onCategorySelect(category)
				},
				label = {
					Text(
						stringResource(category.titleResource()),
						maxLines = 1
					)
				},
				shape = MaterialTheme.shapes.small,
				leadingIcon = if (isSelected) {
					{
						Icon(
							imageVector = Icons.Outlined.Check,
							contentDescription = null,
							modifier = Modifier.size(FilterChipDefaults.IconSize)
						)
					}
				} else {
					null
				}
			)
		}
	}
}

@Composable
private fun BinderySearchResultItem(
	modifier: Modifier = Modifier,
	result: BinderySearchResult,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	languageFilter: String?,
	actionInFlight: Set<String>,
	onOpenBook: (BinderyCatalogCard.Book) -> Unit,
	onOpenCatalog: (BinderyCatalogCard.Link) -> Unit,
	onOpenFinding: (BinderyCatalogCard.Finding) -> Unit,
	onAction: (BinderyLink) -> Unit
) {
	when (val card = result.card) {
		is BinderyCatalogCard.Book -> {
			val visualPolicy = binderyCatalogCardVisualPolicy(card)
			val action = card.primaryAction()
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
				coverOverlay = if (action != null) {
					{
						BinderyCardActionButton(
							action = action,
							loading = action.link.href in actionInFlight,
							onAction = onAction,
							modifier = Modifier
								.align(Alignment.BottomEnd)
								.padding(8.dp)
						)
					}
				} else {
					null
				},
				fallbackKind = "Book",
				id = card.id,
				tab = "bindery-search"
			)
		}
		is BinderyCatalogCard.Finding -> {
			val visualPolicy = binderyCatalogCardVisualPolicy(card)
			val action = card.primaryAction()
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
				coverOverlay = if (action != null) {
					{
						BinderyCardActionButton(
							action = action,
							loading = action.link.href in actionInFlight,
							onAction = onAction,
							modifier = Modifier
								.align(Alignment.BottomEnd)
								.padding(8.dp)
						)
					}
				} else {
					null
				},
				fallbackKind = "Finding",
				id = card.id,
				tab = "bindery-search"
			)
		}
		is BinderyCatalogCard.Link -> {
			val visualPolicy = binderyCatalogCardVisualPolicy(card)
			val action = card.primaryAction()
			ArtGridItem(
				modifier = modifier.alpha(card.availabilityAlpha(languageFilter)),
				onClick = { onOpenCatalog(card) },
				coverArtId = null,
				imageUrl = card.imageUrl?.let { binderyEndpoint(baseUrl, it) },
				imageRequestHeaders = imageRequestHeaders,
				title = card.title,
				subtitle = card.subtitle,
				ownershipStatus = card.availabilityStatus(languageFilter),
				coverAspectRatio = visualPolicy.coverAspectRatio,
				coverContentScale = if (visualPolicy.imageContentScaleFit) ContentScale.Fit else ContentScale.Crop,
				coverOverlay = if (action != null) {
					{
						BinderyCardActionButton(
							action = action,
							loading = action.link.href in actionInFlight,
							onAction = onAction,
							modifier = Modifier
								.align(Alignment.BottomEnd)
								.padding(8.dp)
						)
					}
				} else {
					null
				},
				fallbackKind = card.subtitle,
				id = card.id,
				tab = "bindery-search"
			)
		}
	}
}

private fun BinderySearchCategory.titleResource(): StringResource =
	when (this) {
		BinderySearchCategory.All -> Res.string.title_all
		BinderySearchCategory.Books -> Res.string.title_audiobook_books
		BinderySearchCategory.Findings -> Res.string.title_audiobook_findings
		BinderySearchCategory.Collections -> Res.string.title_audiobook_collections
		BinderySearchCategory.Authors -> Res.string.title_audiobook_authors
	}
