package paige.navic.ui.screens.bindery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_less
import navic.composeapp.generated.resources.action_more
import navic.composeapp.generated.resources.title_audiobook_collections
import navic.composeapp.generated.resources.title_audiobook_publications
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.binderyApiKeyHeaders
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.ArtGrid
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.layouts.artGridError
import paige.navic.ui.components.layouts.artGridPlaceholder
import paige.navic.ui.components.layouts.horizontalSection
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.RootTopBar
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.util.ui.withoutTop

enum class BinderyDetailKind {
	Author,
	Collection
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinderyDetailScreen(
	kind: BinderyDetailKind,
	path: String,
	title: String
) {
	val viewModel = koinViewModel<BinderyCatalogViewModel>(
		key = "bindery-detail-$path",
		parameters = { parametersOf(path) }
	)
	val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
	val relatedCollectionsState by viewModel.relatedCollectionsState.collectAsStateWithLifecycle()
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
		binderyLoading = binderyConfigured &&
			(catalogState is UiState.Loading || relatedCollectionsState is UiState.Loading)
	)
	val imageRequestHeaders = binderyApiKeyHeaders(preferenceManager.binderyApiKey)
	val resolvedTitle = catalogState.data?.title?.takeIf { it.isNotBlank() } ?: title
	val authorCollectionsLink = catalogState.data?.authorCollectionsLink()

	LaunchedEffect(
		binderyConfigured,
		preferenceManager.binderyOpdsBaseUrl,
		preferenceManager.binderyApiKey,
		path
	) {
		if (binderyConfigured) {
			viewModel.refreshCatalog(false)
		} else {
			viewModel.clearCatalog()
		}
	}

	LaunchedEffect(kind, authorCollectionsLink?.path) {
		if (kind == BinderyDetailKind.Author) {
			viewModel.refreshRelatedCollections(authorCollectionsLink?.path, false)
		} else {
			viewModel.refreshRelatedCollections(null, false)
		}
	}

	Scaffold(
		topBar = {
			RootTopBar(
				title = { Text(resolvedTitle) },
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
						viewModel.refreshCatalog(true)
						if (kind == BinderyDetailKind.Author) {
							viewModel.refreshRelatedCollections(authorCollectionsLink?.path, true)
						}
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
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					if (binderyConfigured) {
						when (val state = catalogState) {
							is UiState.Loading -> {
								val data = state.data
								if (data == null) {
									artGridPlaceholder(
										coverAspectRatio = binderyDetailPlaceholderAspectRatio(kind)
									)
								} else {
									binderyDetailItems(
										kind = kind,
										catalog = data,
										relatedCollectionsState = relatedCollectionsState,
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										onOpenCollection = { link ->
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
									binderyDetailItems(
										kind = kind,
										catalog = data,
										relatedCollectionsState = relatedCollectionsState,
										baseUrl = preferenceManager.binderyOpdsBaseUrl,
										imageRequestHeaders = imageRequestHeaders,
										onOpenCollection = { link ->
											platformContext.clickSound()
											backStack.add(binderyDestinationForLink(link))
										}
									)
								}
							}
							is UiState.Success -> binderyDetailItems(
								kind = kind,
								catalog = state.data,
								relatedCollectionsState = relatedCollectionsState,
								baseUrl = preferenceManager.binderyOpdsBaseUrl,
								imageRequestHeaders = imageRequestHeaders,
								onOpenCollection = { link ->
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

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.binderyDetailItems(
	kind: BinderyDetailKind,
	catalog: BinderyCatalog,
	relatedCollectionsState: UiState<BinderyCatalog>,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	onOpenCollection: (BinderyCatalogCard.Link) -> Unit
) {
	val publications = when (kind) {
		BinderyDetailKind.Author -> catalog.publications.sortedForBinderyDetail()
		BinderyDetailKind.Collection -> catalog.publications.sortedForBinderyCollectionDetail()
	}
	item(
		key = "bindery-detail-hero",
		span = { GridItemSpan(maxLineSpan) }
	) {
		BinderyDetailHero(
			kind = kind,
			catalog = catalog,
			baseUrl = baseUrl,
			imageRequestHeaders = imageRequestHeaders,
			modifier = Modifier.animateItem()
		)
	}
	if (kind == BinderyDetailKind.Author && catalog.authorCollectionsLink() != null) {
		horizontalSection(
			title = Res.string.title_audiobook_collections,
			destination = Screen.BinderyCatalog(
				path = catalog.authorCollectionsLink()?.path.orEmpty(),
				title = catalog.authorCollectionsLink()?.title ?: "Collections"
			),
			state = relatedCollectionsState.toCollectionCardsState(),
			key = { it.id },
			seeAll = false
		) { card ->
			BinderyRelatedCollectionCard(
				modifier = Modifier.animateItem().width(150.dp),
				card = card,
				baseUrl = baseUrl,
				imageRequestHeaders = imageRequestHeaders,
				onOpenCollection = onOpenCollection
			)
		}
	}
	if (publications.isNotEmpty()) {
		item(
			key = "bindery-detail-publications-title",
			span = { GridItemSpan(maxLineSpan) }
		) {
			Text(
				text = stringResource(Res.string.title_audiobook_publications),
				style = MaterialTheme.typography.titleMediumEmphasized,
				fontWeight = FontWeight(600),
				modifier = Modifier
					.animateItem()
					.fillMaxWidth()
					.padding(top = 4.dp)
			)
		}
		items(
			items = publications,
			key = { publication -> publication.id ?: publication.title }
		) { publication ->
			BinderyPublicationGridItem(
				modifier = Modifier.animateItem(),
				publication = publication,
				kind = kind,
				baseUrl = baseUrl,
				imageRequestHeaders = imageRequestHeaders
			)
		}
	}
}

private fun UiState<BinderyCatalog>.toCollectionCardsState(): UiState<List<BinderyCatalogCard.Link>> =
	when (this) {
		is UiState.Error -> UiState.Error(
			error = error,
			data = data?.let { catalog ->
				binderyCatalogCards(catalog, BinderyCatalogTab.Collections).filterIsInstance<BinderyCatalogCard.Link>()
			}
		)
		is UiState.Loading -> UiState.Loading(
			data = data?.let { catalog ->
				binderyCatalogCards(catalog, BinderyCatalogTab.Collections).filterIsInstance<BinderyCatalogCard.Link>()
			}
		)
		is UiState.Success -> UiState.Success(
			binderyCatalogCards(data, BinderyCatalogTab.Collections).filterIsInstance<BinderyCatalogCard.Link>()
		)
	}

@Composable
private fun BinderyRelatedCollectionCard(
	modifier: Modifier = Modifier,
	card: BinderyCatalogCard.Link,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	onOpenCollection: (BinderyCatalogCard.Link) -> Unit
) {
	val visualPolicy = binderyCatalogCardVisualPolicy(card)
	ArtGridItem(
		modifier = modifier,
		onClick = { onOpenCollection(card) },
		coverArtId = null,
		imageUrl = card.imageUrl?.let { binderyEndpoint(baseUrl, it) },
		imageRequestHeaders = imageRequestHeaders,
		title = card.title,
		subtitle = card.collectionSummarySubtitle(),
		coverAspectRatio = visualPolicy.coverAspectRatio,
		coverContentScale = if (visualPolicy.imageContentScaleFit) ContentScale.Fit else ContentScale.Crop,
		fallbackKind = card.subtitle,
		id = card.id,
		tab = "bindery-author-collections"
	)
}

@Composable
private fun BinderyDetailHero(
	kind: BinderyDetailKind,
	catalog: BinderyCatalog,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>,
	modifier: Modifier = Modifier
) {
	var expanded by rememberSaveable(catalog.identifier, catalog.title) { mutableStateOf(false) }
	val imageHref = catalog.images.firstOrNull()?.href
		?: if (kind == BinderyDetailKind.Collection) catalog.firstPublicationImageHref() else null
	val detailText = catalog.detailMetadataText(kind)
	val description = catalog.description?.trim()?.takeIf { it.isNotEmpty() }
	val coverAspectRatio = binderyDetailPlaceholderAspectRatio(kind)
	val coverWidth = if (kind == BinderyDetailKind.Collection) 112.dp else 120.dp

	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.Top
	) {
		CoverArt(
			coverArtId = null,
			imageUrl = imageHref?.let { binderyEndpoint(baseUrl, it) },
			imageRequestHeaders = imageRequestHeaders,
			contentDescription = catalog.title,
			fallbackKind = binderyDetailFallbackKind(kind),
			modifier = Modifier
				.width(coverWidth)
				.aspectRatio(coverAspectRatio),
			square = false,
			contentScale = if (kind == BinderyDetailKind.Collection) ContentScale.Fit else ContentScale.Crop
		)
		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = catalog.title,
				style = MaterialTheme.typography.headlineSmall,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis
			)
			detailText?.let {
				Text(
					text = it,
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.primary,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis
				)
			}
			description?.let {
				Text(
					text = it,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = if (expanded) Int.MAX_VALUE else 8,
					overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
				)
				TextButton(
					onClick = { expanded = !expanded },
					modifier = Modifier.padding(top = 0.dp)
				) {
					Text(stringResource(if (expanded) Res.string.action_less else Res.string.action_more))
				}
			}
		}
	}
}

@Composable
private fun BinderyPublicationGridItem(
	modifier: Modifier = Modifier,
	publication: BinderyPublication,
	kind: BinderyDetailKind,
	baseUrl: String,
	imageRequestHeaders: Map<String, String>
) {
	ArtGridItem(
		modifier = modifier,
		onClick = {},
		coverArtId = null,
		imageUrl = publication.images.firstOrNull()?.href?.let { binderyEndpoint(baseUrl, it) },
		imageRequestHeaders = imageRequestHeaders,
		title = publication.title,
		subtitle = publication.detailSubtitle(kind),
		coverAspectRatio = 2f / 3f,
		coverContentScale = ContentScale.Fit,
		fallbackKind = "Book",
		id = publication.id ?: publication.title,
		tab = "bindery-detail"
	)
}

private fun BinderyPublication.detailSubtitle(kind: BinderyDetailKind): String? =
	when (kind) {
		BinderyDetailKind.Author -> publishedYear()
		BinderyDetailKind.Collection -> listOfNotNull(collectionPositionLabel(), author, publishedYear())
			.joinToString(separator = " - ")
			.takeIf { it.isNotBlank() }
	}

private fun BinderyPublication.collectionPositionLabel(): String? =
	properties["collectionPosition"]
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.let { "#$it" }

private fun BinderyCatalogCard.Link.collectionSummarySubtitle(): String? =
	listOfNotNull(
		properties["memberCount"]?.toIntOrNull()?.let { count ->
			if (count == 1) "1 book" else "$count books"
		},
		collectionYearRangeText(properties),
		properties["sourceProvider"]?.displayToken()
	).joinToString(separator = " / ").takeIf { it.isNotBlank() } ?: subtitle

private fun BinderyPublication.publishedYear(): String? =
	published?.trim()?.take(4)?.takeIf { year ->
		year.length == 4 && year.all(Char::isDigit)
	}

private fun BinderyCatalog.detailMetadataText(kind: BinderyDetailKind): String? =
	when (kind) {
		BinderyDetailKind.Author -> subjects
			.filterNot { subject -> subject.equals("hardcover", ignoreCase = true) }
			.joinToString(separator = " / ")
			.takeIf { it.isNotBlank() }
		BinderyDetailKind.Collection -> listOfNotNull(
			properties["collectionType"]?.displayToken(),
			properties["memberCount"]?.toIntOrNull()?.let { count ->
				if (count == 1) "1 book" else "$count books"
			},
			collectionYearRangeText(properties),
			properties["sourceProvider"]?.displayToken()
		).joinToString(separator = " / ").takeIf { it.isNotBlank() }
	}

private fun collectionYearRangeText(properties: Map<String, String>): String? {
	val start = properties["startYear"]?.toIntOrNull()
	val end = properties["endYear"]?.toIntOrNull()
	return when {
		start != null && end != null && start != end -> "$start-$end"
		start != null -> start.toString()
		end != null -> end.toString()
		else -> null
	}
}

private fun String.displayToken(): String =
	trim()
		.replace('-', ' ')
		.replace('_', ' ')
		.split(Regex("\\s+"))
		.filter { it.isNotEmpty() }
		.joinToString(separator = " ") { token ->
			token.replaceFirstChar { char ->
				if (char.isLowerCase()) char.titlecase() else char.toString()
			}
		}

private fun binderyDetailPlaceholderAspectRatio(kind: BinderyDetailKind): Float =
	when (kind) {
		BinderyDetailKind.Author -> 1f
		BinderyDetailKind.Collection -> 2f / 3f
	}

private fun binderyDetailFallbackKind(kind: BinderyDetailKind): String =
	when (kind) {
		BinderyDetailKind.Author -> "Author"
		BinderyDetailKind.Collection -> "Collection"
	}
