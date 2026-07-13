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
import paige.navic.ui.components.common.GeneratedArtworkVariant
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.AurralIntegrationServices
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.aurralAlbumArtworkRenderSpec
import paige.navic.ui.components.common.generatedArtworkSpec
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
internal fun AurralHubDiscoverSection(
	state: UiState<AurralDiscoverySummary?>,
	actionState: UiState<Unit?>,
	activeArtistId: String?,
	canMonitorArtist: Boolean,
	confirmationQueue: List<AurralConfirmationQueueItem>,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry>,
	preferenceManager: PreferenceManager,
	onMonitorArtist: (AurralDiscoverArtist) -> Unit,
	onOpenArtist: (AurralDiscoverArtist) -> Unit,
	onOpenAlbum: (AurralAlbumSearchItem) -> Unit,
	onOpenDiscoverCollection: (AurralDiscoveryCollectionRow) -> Unit,
	onOpenTag: (String) -> Unit
) {
	AurralHubSectionTitle(stringResource(Res.string.title_aurral_discover))
	val discovery = state.data
	val rows = discovery
		?.let {
			aurralDiscoveryCollectionRows(
				discovery = it,
				artistPhotoCacheEntries = artistPhotoCacheEntries,
				artistArtworkPriority = preferenceManager.artistArtworkPriority,
				externalArtworkEnabled = preferenceManager.aurralEnabled
			)
		}
		.orEmpty()
	val actionInProgress = actionState is UiState.Loading

	when {
		rows.isEmpty() -> Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(stringResource(Res.string.info_aurral_discover_empty))
			}
		}

		else -> rows.forEach { row ->
			AurralHubDiscoveryCollectionTitle(row.collectionTitle())
			Form(Modifier.fillMaxWidth()) {
				when (row) {
					is AurralDiscoveryCollectionRow.Artists -> row.artists.forEach { artist ->
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

					is AurralDiscoveryCollectionRow.Albums -> row.albums.forEach { album ->
						AurralHubAlbumSearchRow(
							album = album,
							preferenceManager = preferenceManager,
							onOpenAlbum = onOpenAlbum
						)
					}

					is AurralDiscoveryCollectionRow.Tags -> FormRow(
						contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
					) {
						AurralDiscoverTagWall(
							tags = row.tags,
							modifier = Modifier.fillMaxWidth(),
							onOpenTag = onOpenTag
						)
					}
				}
			}
			if (aurralDiscoverCollectionRoute(row) != null) {
				FormButton(
					onClick = { onOpenDiscoverCollection(row) },
					color = MaterialTheme.colorScheme.secondaryContainer
				) {
					Text(stringResource(Res.string.action_see_all))
				}
			}
		}
	}

	if (state is UiState.Loading) {
		LinearProgressIndicator(
			modifier = Modifier
				.fillMaxWidth()
				.padding(bottom = 16.dp)
		)
	}
	if (state is UiState.Error) {
		Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_discover_failed,
						state.error.message ?: state.error::class.simpleName ?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
	}
	when (actionState) {
		is UiState.Error -> Form(Modifier.fillMaxWidth()) {
			FormRow {
				Text(
					text = stringResource(
						Res.string.info_aurral_discover_monitor_failed,
						actionState.error.message ?: actionState.error::class.simpleName ?: "Unknown error"
					),
					color = MaterialTheme.colorScheme.error
				)
			}
		}
		is UiState.Success -> if (actionState.data != null) {
			Form(Modifier.fillMaxWidth()) {
				FormRow {
					Text(stringResource(Res.string.info_aurral_discover_monitor_added))
				}
			}
		}
		else -> Unit
	}
}

@Composable
private fun AurralDiscoveryCollectionRow.collectionTitle(): String =
	when (this) {
		is AurralDiscoveryCollectionRow.Artists ->
			if (kind == AurralDiscoveryCollectionKind.GenreArtists) {
				stringResource(kind.aurralHubTitleResource(), tag.orEmpty())
			} else {
				stringResource(kind.aurralHubTitleResource())
			}

		is AurralDiscoveryCollectionRow.Albums -> stringResource(kind.aurralHubTitleResource())
		is AurralDiscoveryCollectionRow.Tags -> stringResource(kind.aurralHubTitleResource())
	}

@Composable
private fun AurralHubDiscoveryCollectionTitle(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.titleSmall,
		fontWeight = FontWeight.SemiBold,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = 10.dp, start = 4.dp, bottom = 4.dp)
	)
}

@Composable
fun AurralHubDiscoverArtistRow(
	artist: AurralDiscoverArtist,
	canMonitorArtist: Boolean,
	actionInProgress: Boolean,
	active: Boolean,
	monitorState: AurralMonitorActionState,
	preferenceManager: PreferenceManager,
	onMonitorArtist: (AurralDiscoverArtist) -> Unit,
	onOpenArtist: (AurralDiscoverArtist) -> Unit
) {
	val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
	val requestHeaders = preferenceManager.aurralRequestHeadersMap()
	val imageRequestHeaders = if (baseUrl != null) {
		aurralRequestHeadersForUrl(baseUrl, artist.imageUrl, requestHeaders)
	} else {
		emptyMap()
	}
	val generatedArtwork = generatedArtworkSpec(
		kindLabel = "Artist",
		primaryLabel = artist.name,
		seed = artist.id,
		variant = GeneratedArtworkVariant.SheetThumbnail
	)

	FormRow(
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
		onClick = { onOpenArtist(artist) }
	) {
		CoverArt(
			modifier = Modifier.size(56.dp),
			coverArtId = null,
			imageUrl = artist.imageUrl,
			imageRequestHeaders = imageRequestHeaders,
			contentDescription = artist.name,
			generatedArtwork = generatedArtwork
		)
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(start = 12.dp)
		) {
			Text(
				text = artist.name,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = aurralDiscoverArtistDetail(artist),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	if (canMonitorArtist) {
		val monitorEnabled = !actionInProgress && monitorState == AurralMonitorActionState.NotMonitored
		IconButton(
			onClick = { onMonitorArtist(artist) },
			enabled = monitorEnabled
		) {
			if (active) {
				CircularProgressIndicator(modifier = Modifier.size(20.dp))
			} else {
				AurralArtistMonitorBadge(state = monitorState)
			}
		}
	}
}
}

@Composable
internal fun AurralHubAlbumSearchRow(
	album: AurralAlbumSearchItem,
	preferenceManager: PreferenceManager,
	onOpenAlbum: (AurralAlbumSearchItem) -> Unit
) {
	val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
	val requestHeaders = preferenceManager.aurralRequestHeadersMap()
	val imageRequestHeaders = if (baseUrl != null) {
		aurralRequestHeadersForUrl(baseUrl, album.coverUrl, requestHeaders)
	} else {
		emptyMap()
	}
	val ownershipStatus = aurralSearchAlbumOwnershipStatus(album)
	val colorFilter = remember(ownershipStatus) {
		if (ownershipStatus == AurralOwnershipStatus.Missing ||
			ownershipStatus == AurralOwnershipStatus.Failed
		) {
			ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
		} else {
			null
		}
	}
	val artworkSpec = aurralAlbumArtworkRenderSpec(
		id = album.id,
		title = album.title,
		coverUrl = album.coverUrl,
		primaryType = album.primaryType,
		imageRequestHeaders = imageRequestHeaders,
		variant = GeneratedArtworkVariant.SheetThumbnail
	)

	FormRow(
		contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
		onClick = { onOpenAlbum(album) }
	) {
		Box {
			CoverArt(
				modifier = Modifier.size(56.dp),
				coverArtId = artworkSpec.coverArtId,
				imageUrl = artworkSpec.imageUrl,
				imageCacheKey = artworkSpec.imageCacheKey,
				imageRequestHeaders = artworkSpec.imageRequestHeaders,
				contentDescription = artworkSpec.contentDescription,
				generatedArtwork = artworkSpec.generatedArtwork,
				colorFilter = colorFilter
			)
			AurralOwnershipStatusDot(
				status = ownershipStatus,
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(5.dp),
				size = 9.dp
			)
		}
		Column(
			modifier = Modifier
				.weight(1f)
				.padding(start = 12.dp)
		) {
			Text(
				text = album.title,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = aurralAlbumSearchDetail(album),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

private fun aurralAlbumSearchDetail(album: AurralAlbumSearchItem): String {
	val year = album.releaseDate?.trim()?.take(4)?.takeIf { value ->
		value.length == 4 && value.all { it.isDigit() }
	}
	val type = album.primaryType ?: album.secondaryTypes.firstOrNull()
	val status = when {
		album.inLibrary -> "in library"
		else -> album.status
	}
	return listOfNotNull(
		album.artistName,
		year,
		type,
		status
	).joinToString(" • ")
}
