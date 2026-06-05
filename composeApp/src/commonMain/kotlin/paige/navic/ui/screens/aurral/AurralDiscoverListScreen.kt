package paige.navic.ui.screens.aurral

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.count_artists
import navic.composeapp.generated.resources.info_aurral_discover_empty
import navic.composeapp.generated.resources.title_aurral_based_on_library
import navic.composeapp.generated.resources.title_aurral_because_you_like
import navic.composeapp.generated.resources.title_aurral_discover
import navic.composeapp.generated.resources.title_aurral_explore_by_tag
import navic.composeapp.generated.resources.title_aurral_global_top
import navic.composeapp.generated.resources.title_aurral_recently_added
import navic.composeapp.generated.resources.title_aurral_recent_releases
import navic.composeapp.generated.resources.title_aurral_recommended_for_you
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.AurralRepository
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.AurralIntegrationServices
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.ArtGrid
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.artGridError
import paige.navic.ui.components.layouts.artGridPlaceholder
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.toArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.viewmodels.ArtistListViewModel
import paige.navic.ui.screens.library.components.AurralDiscoverArtistCard
import paige.navic.ui.screens.library.components.AurralDiscoverTagWall
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Artist
import paige.navic.util.ui.withoutTop

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AurralDiscoverListScreen(
	collectionKind: String? = null,
	tag: String? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val aurralRepository = koinInject<AurralRepository>()
	val artistPhotoCacheDao = koinInject<ArtistPhotoCacheDao>()
	val backStack = LocalNavStack.current
	val tagFilter = tag?.trim()?.takeIf { it.isNotEmpty() }
	val selectedCollection = aurralDiscoverCollectionKind(collectionKind)
	val viewModel = koinViewModel<AurralHubViewModel>(
		key = tagFilter?.let { "aurralDiscoverList:tag:$it" }
			?: selectedCollection?.let { "aurralDiscoverList:collection:${it.name}" }
			?: "aurralDiscoverList"
	)
	val localArtistsViewModel = koinViewModel<ArtistListViewModel>(
		key = "aurralDiscoverListLocalArtists",
		parameters = { parametersOf(DomainArtistListType.AlphabeticalByName) }
	)
	val discoveryState by viewModel.discovery.collectAsStateWithLifecycle()
	val localArtistsState by localArtistsViewModel.artistsState.collectAsStateWithLifecycle()
	val confirmationQueue by aurralRepository.confirmationQueue.collectAsStateWithLifecycle()
	val cachedArtistPhotos by artistPhotoCacheDao.observeArtistPhotoCache()
		.collectAsStateWithLifecycle(emptyList())
	val artistPhotoCacheEntries = cachedArtistPhotos.map { entry ->
		entry.toArtistHeaderImageCacheEntry()
	}
	val gridState = rememberLazyGridState()
	val configured = shouldLoadAurralUi(
		aurralEnabled = preferenceManager.aurralEnabled,
		baseUrl = preferenceManager.aurralBaseUrl
	)
	val aurralDiscoverIntegrationIndicators = integrationLoadingIndicators(
		aurralLoading = configured && discoveryState is UiState.Loading
	)

	LaunchedEffect(
		configured,
		selectedCollection,
		preferenceManager.aurralBaseUrl,
		preferenceManager.aurralUsername,
		preferenceManager.aurralPassword
	) {
		if (configured) {
			viewModel.refreshDiscovery(hydrateMissingImages = false)
		} else {
			viewModel.clearServiceStatus()
		}
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				title = {
					Text(
						tagFilter?.let { "#$it" }
							?: selectedCollection?.let { stringResource(it.titleResource()) }
							?: stringResource(Res.string.title_aurral_discover)
					)
				}
			)
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		AurralConfirmationQueueSnackbar(aurralRepository)
		Box(Modifier.fillMaxSize()) {
			PullToRefreshBox(
				modifier = Modifier
					.padding(top = innerPadding.calculateTopPadding())
					.background(MaterialTheme.colorScheme.surface),
				finished = discoveryState !is UiState.Loading && localArtistsState !is UiState.Loading,
				onRefresh = {
					if (configured) viewModel.refreshDiscovery(hydrateMissingImages = false)
					localArtistsViewModel.refreshArtists(false)
				},
				key = discoveryState
			) {
				val artists = discoveryState.data
					?.let { discovery ->
						if (tagFilter != null) {
							aurralDiscoverTagArtists(
								discovery = discovery,
								tag = tagFilter,
								artistPhotoCacheEntries = artistPhotoCacheEntries,
								artistArtworkPriority = preferenceManager.artistArtworkPriority,
								externalArtworkEnabled = preferenceManager.aurralEnabled
							)
						} else if (selectedCollection != null) {
							aurralDiscoverCollectionArtists(
								discovery = discovery,
								kind = selectedCollection,
								artistPhotoCacheEntries = artistPhotoCacheEntries,
								artistArtworkPriority = preferenceManager.artistArtworkPriority,
								externalArtworkEnabled = preferenceManager.aurralEnabled
							)
						} else {
							aurralDiscoverListArtists(
								discovery = discovery,
								artistPhotoCacheEntries = artistPhotoCacheEntries,
								artistArtworkPriority = preferenceManager.artistArtworkPriority,
								externalArtworkEnabled = preferenceManager.aurralEnabled
							)
						}
					}
					.orEmpty()
				val tags = discoveryState.data
					?.takeIf { selectedCollection == AurralDiscoveryCollectionKind.TopTags && tagFilter == null }
					?.let { discovery -> aurralDiscoverTopTags(discovery) }
					.orEmpty()
				val localArtists = localArtistsState.data.orEmpty()
				ArtGrid(
					state = gridState,
					contentPadding = innerPadding.withoutTop(),
					verticalArrangement = if (artists.isEmpty() && tags.isEmpty()) {
						Arrangement.Center
					} else {
						Arrangement.spacedBy(12.dp)
					}
				) {
					if (tags.isNotEmpty()) {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Row(
								Modifier
									.background(MaterialTheme.colorScheme.surface)
									.padding(bottom = 8.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								Text(
									"${tags.size} tags",
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
						item(span = { GridItemSpan(maxLineSpan) }) {
							AurralDiscoverTagWall(
								tags = tags,
								onOpenTag = { tag -> backStack.add(Screen.AurralDiscoverTag(tag)) }
							)
						}
					} else if (artists.isNotEmpty()) {
						item(span = { GridItemSpan(maxLineSpan) }) {
							Row(
								Modifier
									.background(MaterialTheme.colorScheme.surface)
									.padding(bottom = 8.dp),
								verticalAlignment = Alignment.CenterVertically
							) {
								Text(
									pluralStringResource(
										Res.plurals.count_artists,
										artists.size,
										artists.size
									),
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
						items(artists, key = { it.id.trim().ifEmpty { it.name } }) { artist ->
							AurralDiscoverArtistCard(
								modifier = Modifier.animateItem(),
								artist = artist,
								confirmationQueue = confirmationQueue,
								onOpenArtist = {
									aurralArtistRecommendationRoute(
										artist = artist,
										localArtists = localArtists
									)?.let(backStack::add)
								}
							)
						}
					} else {
						when (val state = discoveryState) {
							is UiState.Loading -> artGridPlaceholder()
							is UiState.Error -> artGridError(state)
							is UiState.Success -> item(span = { GridItemSpan(maxLineSpan) }) {
								ContentUnavailable(
									icon = Icons.Outlined.Artist,
									label = stringResource(Res.string.info_aurral_discover_empty)
								)
							}
						}
					}
				}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = aurralDiscoverIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = aurralDiscoverIntegrationIndicators,
					relevantServices = AurralIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(
						start = 12.dp,
						top = innerPadding.calculateTopPadding() + 8.dp
					)
			)
		}
	}
}

private fun AurralDiscoveryCollectionKind.titleResource(): StringResource =
	when (this) {
		AurralDiscoveryCollectionKind.RecentlyAddedArtists -> Res.string.title_aurral_recently_added
		AurralDiscoveryCollectionKind.RecentReleases -> Res.string.title_aurral_recent_releases
		AurralDiscoveryCollectionKind.RecommendedArtists -> Res.string.title_aurral_recommended_for_you
		AurralDiscoveryCollectionKind.BasedOnArtists -> Res.string.title_aurral_based_on_library
		AurralDiscoveryCollectionKind.GlobalTopArtists -> Res.string.title_aurral_global_top
		AurralDiscoveryCollectionKind.GenreArtists -> Res.string.title_aurral_because_you_like
		AurralDiscoveryCollectionKind.TopTags -> Res.string.title_aurral_explore_by_tag
	}
