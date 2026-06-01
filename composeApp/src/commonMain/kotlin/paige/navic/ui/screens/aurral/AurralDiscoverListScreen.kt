package paige.navic.ui.screens.aurral

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.configuredAurralBaseUrl
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.layouts.ArtGrid
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.artGridError
import paige.navic.ui.components.layouts.artGridPlaceholder
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.artist.viewmodels.ArtistListViewModel
import paige.navic.ui.screens.library.components.AurralDiscoverArtistCard
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
	val gridState = rememberLazyGridState()
	val configured = preferenceManager.aurralEnabled &&
		configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null

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
						aurralDiscoverTagArtists(discovery, tagFilter)
					} else if (selectedCollection != null) {
						aurralDiscoverCollectionArtists(discovery, selectedCollection)
					} else {
						aurralDiscoverListArtists(discovery)
					}
				}
				.orEmpty()
			val localArtists = localArtistsState.data.orEmpty()
			ArtGrid(
				state = gridState,
				contentPadding = innerPadding.withoutTop(),
				verticalArrangement = if (artists.isEmpty()) {
					Arrangement.Center
				} else {
					Arrangement.spacedBy(12.dp)
				}
			) {
				if (artists.isNotEmpty()) {
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
