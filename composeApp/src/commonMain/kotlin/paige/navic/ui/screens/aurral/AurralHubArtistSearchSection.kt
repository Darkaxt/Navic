package paige.navic.ui.screens.aurral

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_create_aurral_flow
import navic.composeapp.generated.resources.action_open_aurral_settings
import navic.composeapp.generated.resources.action_open_station
import navic.composeapp.generated.resources.action_play_flow
import navic.composeapp.generated.resources.action_play_station
import navic.composeapp.generated.resources.action_refresh
import navic.composeapp.generated.resources.action_search_aurral_albums
import navic.composeapp.generated.resources.action_search_aurral_artists
import navic.composeapp.generated.resources.action_see_all
import navic.composeapp.generated.resources.action_start_aurral_flow
import navic.composeapp.generated.resources.info_aurral_album_search_empty
import navic.composeapp.generated.resources.info_aurral_album_search_failed
import navic.composeapp.generated.resources.info_aurral_flow_action_failed
import navic.composeapp.generated.resources.info_aurral_flow_action_queued
import navic.composeapp.generated.resources.info_aurral_flow_action_updated
import navic.composeapp.generated.resources.info_aurral_flow_permission_required
import navic.composeapp.generated.resources.info_aurral_flow_sources_unavailable
import navic.composeapp.generated.resources.info_aurral_search_empty
import navic.composeapp.generated.resources.info_aurral_search_failed
import navic.composeapp.generated.resources.info_aurral_discover_empty
import navic.composeapp.generated.resources.info_aurral_discover_failed
import navic.composeapp.generated.resources.info_aurral_discover_monitor_added
import navic.composeapp.generated.resources.info_aurral_discover_monitor_failed
import navic.composeapp.generated.resources.info_aurral_flows_empty
import navic.composeapp.generated.resources.info_aurral_acquisition_queue_empty
import navic.composeapp.generated.resources.info_aurral_hub_disabled
import navic.composeapp.generated.resources.info_aurral_hub_missing_url
import navic.composeapp.generated.resources.info_aurral_service_status_failed
import navic.composeapp.generated.resources.info_aurral_service_status_loading
import navic.composeapp.generated.resources.info_aurral_service_status_unavailable
import navic.composeapp.generated.resources.title_aurral
import navic.composeapp.generated.resources.title_aurral_acquisition_queue
import navic.composeapp.generated.resources.title_aurral_based_on_library
import navic.composeapp.generated.resources.title_aurral_because_you_like
import navic.composeapp.generated.resources.title_aurral_discover
import navic.composeapp.generated.resources.title_aurral_explore_by_tag
import navic.composeapp.generated.resources.title_aurral_flows
import navic.composeapp.generated.resources.title_aurral_global_top
import navic.composeapp.generated.resources.title_aurral_recently_added
import navic.composeapp.generated.resources.title_aurral_recent_releases
import navic.composeapp.generated.resources.title_aurral_recommended_for_you
import navic.composeapp.generated.resources.title_aurral_requests
import navic.composeapp.generated.resources.title_aurral_search
import navic.composeapp.generated.resources.option_aurral_artist_search
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.LocalSnackbarState
import paige.navic.data.database.dao.ArtistPhotoCacheDao
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralAlbumSearchResult
import paige.navic.domain.repositories.AurralArtistSearchResult
import paige.navic.domain.repositories.AurralConfirmationQueueItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.domain.repositories.AurralFlowActionResult
import paige.navic.domain.repositories.AurralFlowSummary
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.AurralServiceStatus
import paige.navic.data.remote.aurral.aurralRequestHeadersForUrl
import paige.navic.data.remote.aurral.configuredAurralBaseUrl
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Add
import paige.navic.icons.filled.Settings
import paige.navic.icons.outlined.PlaylistPlay
import paige.navic.icons.outlined.Refresh
import paige.navic.icons.outlined.Search
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.AurralArtistMonitorBadge
import paige.navic.ui.components.common.AurralOwnershipStatusDot
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.Form
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.FormRow
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.AurralIntegrationServices
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.TopBarButton
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.artist.AurralMonitorActionState
import paige.navic.ui.screens.artist.ArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.toArtistHeaderImageCacheEntry
import paige.navic.ui.screens.artist.viewmodels.ArtistListViewModel
import paige.navic.ui.screens.library.components.AurralDiscoverTagWall

@Composable
internal fun AurralHubArtistSearchSection(
	query: String,
	artistState: UiState<AurralArtistSearchResult?>,
	albumState: UiState<AurralAlbumSearchResult?>,
	actionState: UiState<Unit?>,
	activeArtistId: String?,
	canMonitorArtist: Boolean,
	confirmationQueue: List<AurralConfirmationQueueItem>,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry>,
	preferenceManager: PreferenceManager,
	onQueryChange: (String) -> Unit,
	onSearchArtists: () -> Unit,
	onSearchAlbums: () -> Unit,
	onMonitorArtist: (AurralDiscoverArtist) -> Unit,
	onOpenArtist: (AurralDiscoverArtist) -> Unit,
	onOpenAlbum: (AurralAlbumSearchItem) -> Unit
) {
	AurralHubSectionTitle(stringResource(Res.string.title_aurral_search))
	val trimmedQuery = query.trim()
	val artists = artistState.data?.artists
		?.let {
			aurralHubSearchArtists(
				aurralDiscoverArtistsWithCachedPhotos(
					artists = it,
					entries = artistPhotoCacheEntries,
					artistArtworkPriority = preferenceManager.artistArtworkPriority,
					externalArtworkEnabled = preferenceManager.aurralEnabled
				)
			)
		}
		.orEmpty()
	val albums = albumState.data?.albums?.let { aurralHubSearchAlbums(it) }.orEmpty()
	val searchingArtists = artistState is UiState.Loading
	val searchingAlbums = albumState is UiState.Loading
	val searching = searchingArtists || searchingAlbums
	val actionInProgress = actionState is UiState.Loading

	Form(Modifier.fillMaxWidth()) {
		FormRow(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
			TextField(
				value = query,
				onValueChange = onQueryChange,
				label = { Text(stringResource(Res.string.option_aurral_artist_search)) },
				singleLine = true,
				enabled = !searching,
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
				keyboardActions = KeyboardActions(
					onSearch = {
						if (trimmedQuery.isNotEmpty()) onSearchArtists()
					}
				),
				modifier = Modifier.fillMaxWidth()
			)
		}
		if (artists.isNotEmpty()) {
			artists.forEach { artist ->
				AurralHubDiscoverArtistRow(
					artist = artist,
					canMonitorArtist = canMonitorArtist,
					actionInProgress = actionInProgress,
					active = activeArtistId == artist.id,
					monitorState = aurralDiscoverArtistMonitorActionState(artist, confirmationQueue),
					preferenceManager = preferenceManager,
					onMonitorArtist = onMonitorArtist,
					onOpenArtist = onOpenArtist
				)
			}
		} else if (trimmedQuery.isNotEmpty() && artistState is UiState.Success && artistState.data != null) {
			FormRow {
				Text(stringResource(Res.string.info_aurral_search_empty))
			}
		}
		if (albums.isNotEmpty()) {
			albums.forEach { album ->
				AurralHubAlbumSearchRow(
					album = album,
					preferenceManager = preferenceManager,
					onOpenAlbum = onOpenAlbum
				)
			}
		} else if (trimmedQuery.isNotEmpty() && albumState is UiState.Success && albumState.data != null) {
			FormRow {
				Text(stringResource(Res.string.info_aurral_album_search_empty))
			}
		}
	}

	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		AurralSearchButton(
			text = stringResource(Res.string.action_search_aurral_artists),
			onClick = onSearchArtists,
			enabled = trimmedQuery.isNotEmpty() && !searchingArtists,
			modifier = Modifier.weight(1f)
		)
		AurralSearchButton(
			text = stringResource(Res.string.action_search_aurral_albums),
			onClick = onSearchAlbums,
			enabled = trimmedQuery.isNotEmpty() && !searchingAlbums,
			modifier = Modifier.weight(1f)
		)
	}

	if (searching) {
		LinearProgressIndicator(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 16.dp)
		)
	}
	if (artistState is UiState.Error) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_search_failed,
						artistState.error.message ?: artistState.error::class.simpleName ?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	}
	if (albumState is UiState.Error) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_album_search_failed,
						albumState.error.message ?: albumState.error::class.simpleName ?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	}
}

@Composable
private fun AurralSearchButton(
	text: String,
	onClick: () -> Unit,
	enabled: Boolean,
	modifier: Modifier = Modifier
) {
	FormRow(
		modifier = modifier,
		onClick = if (enabled) onClick else null,
		horizontalArrangement = Arrangement.Center,
		contentPadding = PaddingValues(14.dp),
		rounding = 5.dp,
		color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = .5f)
	) {
		Icon(
			Icons.Outlined.Search,
			null,
			modifier = Modifier.size(18.dp),
			tint = MaterialTheme.colorScheme.onPrimary
		)
		Spacer(Modifier.width(8.dp))
		Text(text, color = MaterialTheme.colorScheme.onPrimary)
	}
}
