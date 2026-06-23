package paige.navic.ui.screens.library

import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.settings.ArtworkSourcePriority
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralDiscoverySummary
import paige.navic.ui.screens.aurral.AurralDiscoveryCollectionRow
import paige.navic.ui.screens.aurral.aurralDiscoveryCollectionRows
import paige.navic.ui.screens.aurral.aurralHubDiscoverArtists
import paige.navic.ui.screens.artist.ArtistHeaderImageCacheEntry
import paige.navic.ui.core.UiState

fun libraryAlbumAurralRequests(
	showAurralHub: Boolean,
	requests: List<AurralAlbumRequest>
): List<AurralAlbumRequest> =
	if (showAurralHub) requests else emptyList()

fun libraryAurralDiscoverArtists(
	aurralConfigured: Boolean,
	discovery: AurralDiscoverySummary?,
	limit: Int = 8,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoverArtist> =
	if (aurralConfigured && discovery != null) {
		aurralHubDiscoverArtists(
			discovery = discovery,
			limit = limit,
			artistPhotoCacheEntries = artistPhotoCacheEntries,
			artistArtworkPriority = artistArtworkPriority,
			externalArtworkEnabled = externalArtworkEnabled
		)
	} else {
		emptyList()
	}

fun libraryAurralCollectionRows(
	aurralConfigured: Boolean,
	discovery: AurralDiscoverySummary?,
	limit: Int = 8,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): List<AurralDiscoveryCollectionRow> =
	if (aurralConfigured && discovery != null) {
		aurralDiscoveryCollectionRows(
			discovery = discovery,
			limit = limit,
			artistPhotoCacheEntries = artistPhotoCacheEntries,
			artistArtworkPriority = artistArtworkPriority,
			externalArtworkEnabled = externalArtworkEnabled
		)
			.mapNotNull(::withoutFallbackArtworkCards)
	} else {
		emptyList()
	}

fun libraryAurralCollectionRowsState(
	aurralConfigured: Boolean,
	discoveryState: UiState<AurralDiscoverySummary?>,
	artistPhotoCacheEntries: List<ArtistHeaderImageCacheEntry> = emptyList(),
	artistArtworkPriority: ArtworkSourcePriority = ArtworkSourcePriority.AurralFirst,
	externalArtworkEnabled: Boolean = true
): UiState<List<AurralDiscoveryCollectionRow>> {
	val rows = libraryAurralCollectionRows(
		aurralConfigured = aurralConfigured,
		discovery = discoveryState.data,
		artistPhotoCacheEntries = artistPhotoCacheEntries,
		artistArtworkPriority = artistArtworkPriority,
		externalArtworkEnabled = externalArtworkEnabled
	)
	return when (discoveryState) {
		is UiState.Loading -> if (rows.isEmpty()) UiState.Success(rows) else UiState.Loading(rows)
		is UiState.Success -> UiState.Success(rows)
		is UiState.Error -> UiState.Success(rows)
	}
}

fun libraryLocalOwnershipStatus(
	aurralConfigured: Boolean
): AurralOwnershipStatus? =
	if (aurralConfigured) AurralOwnershipStatus.Owned else null

fun libraryAurralLoadingPlaceholderVisible(
	state: UiState<List<AurralDiscoveryCollectionRow>>
): Boolean =
	state is UiState.Loading && state.data.orEmpty().isEmpty()

private fun withoutFallbackArtworkCards(
	row: AurralDiscoveryCollectionRow
): AurralDiscoveryCollectionRow? =
	when (row) {
		is AurralDiscoveryCollectionRow.Artists -> row.copy(
			artists = row.artists.filter { artist -> !artist.imageUrl.isNullOrBlank() }
		).takeIf { it.artists.isNotEmpty() }

		is AurralDiscoveryCollectionRow.Albums -> row.copy(
			albums = row.albums.filter { album -> !album.coverUrl.isNullOrBlank() }
		).takeIf { it.albums.isNotEmpty() }

		is AurralDiscoveryCollectionRow.Tags -> row
	}
