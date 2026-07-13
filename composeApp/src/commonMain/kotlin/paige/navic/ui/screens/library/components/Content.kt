package paige.navic.ui.screens.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_sort_frequent
import navic.composeapp.generated.resources.option_sort_newest
import navic.composeapp.generated.resources.option_sort_quick_picks
import navic.composeapp.generated.resources.option_sort_random
import navic.composeapp.generated.resources.option_sort_recent
import navic.composeapp.generated.resources.option_sort_starred
import navic.composeapp.generated.resources.title_artists
import navic.composeapp.generated.resources.title_aurral_based_on_library
import navic.composeapp.generated.resources.title_aurral_because_you_like
import navic.composeapp.generated.resources.title_aurral_explore_by_tag
import navic.composeapp.generated.resources.title_aurral_global_top
import navic.composeapp.generated.resources.title_aurral_recently_added
import navic.composeapp.generated.resources.title_aurral_recent_releases
import navic.composeapp.generated.resources.title_aurral_recommended_for_you
import navic.composeapp.generated.resources.title_genre_mixes
import navic.composeapp.generated.resources.title_genres
import navic.composeapp.generated.resources.title_mood_mixes
import navic.composeapp.generated.resources.title_most_played
import navic.composeapp.generated.resources.title_playlists
import navic.composeapp.generated.resources.title_stations
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.repositories.AurralDiscoverArtist
import paige.navic.domain.repositories.AurralRepository
import paige.navic.data.remote.aurral.aurralRequestHeadersForUrl
import paige.navic.data.remote.aurral.configuredAurralBaseUrl
import paige.navic.ui.navigation.Screen
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainGenre
import paige.navic.domain.models.DomainMostPlayedShortcut
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongListType
import paige.navic.domain.models.PlaybackOriginType
import paige.navic.domain.models.genreMixPlaylists
import paige.navic.domain.models.moodMixPlaylists
import paige.navic.domain.models.regularPlaylists
import paige.navic.domain.models.stationPlaylists
import paige.navic.icons.Icons
import paige.navic.icons.outlined.History
import paige.navic.icons.outlined.LibraryAdd
import paige.navic.icons.outlined.Shuffle
import paige.navic.icons.outlined.Star
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.layouts.horizontalSection
import paige.navic.ui.components.layouts.header
import paige.navic.ui.screens.aurral.AurralAlbumSearchCard
import paige.navic.ui.screens.aurral.AurralDiscoveryCollectionKind
import paige.navic.ui.screens.aurral.AurralDiscoveryCollectionRow
import paige.navic.ui.screens.aurral.aurralDiscoverCollectionRoute
import paige.navic.ui.screens.aurral.aurralMonitorStateForLocalArtist
import paige.navic.ui.screens.album.components.AlbumListScreenItem
import paige.navic.ui.screens.artist.rememberArtistCreditDestinationResolver
import paige.navic.ui.screens.artist.ArtistsScreenItem
import paige.navic.ui.screens.genre.components.GenreListScreenCard
import paige.navic.ui.screens.library.LibraryRowId
import paige.navic.ui.screens.library.libraryRowIdForAurralKind
import paige.navic.ui.screens.library.libraryAurralLoadingPlaceholderVisible
import paige.navic.ui.screens.library.libraryLocalOwnershipStatus
import paige.navic.ui.screens.library.mostPlayedShortcutDestination
import paige.navic.ui.navigation.PlaylistListKind
import paige.navic.ui.screens.playlist.components.PlaylistListScreenItem
import paige.navic.ui.core.UiState
import paige.navic.util.ui.withoutTop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreenContent(
	scrollBehavior: TopAppBarScrollBehavior,
	innerPadding: PaddingValues,
	onSetShareId: (String) -> Unit,
	gridState: LazyGridState = rememberLazyGridState(),
	libraryRows: List<LibraryRowId>,

	// quick picks
	quickPicksEnabled: Boolean,
	quickPicksState: UiState<ImmutableList<DomainSong>>,
	selectedQuickPick: DomainSong?,
	selectedQuickPickIsStarred: Boolean,
	selectedQuickPickRating: Int,
	quickPickDownloads: List<DownloadEntity>,
	onSelectQuickPick: (DomainSong) -> Unit,
	onClearQuickPickSelection: () -> Unit,
	onStarSelectedQuickPick: (Boolean) -> Unit,
	onStartQuickPickRadio: (DomainSong) -> Unit,
	onPlayQuickPickNext: (DomainSong) -> Unit,
	onAddQuickPickToQueue: (DomainSong) -> Unit,
	onPlayQuickPick: (DomainSong) -> Unit,
	onRateSelectedQuickPick: (Int) -> Unit,
	onDownloadQuickPick: (DomainSong) -> Unit,
	onCancelQuickPickDownload: (DomainSong) -> Unit,
	onDeleteQuickPickDownload: (DomainSong) -> Unit,

	// most played
	mostPlayedShortcutsState: UiState<ImmutableList<DomainMostPlayedShortcut>>,

	// albums
	albumsState: UiState<ImmutableList<DomainAlbum>>,
	newestAlbumsState: UiState<ImmutableList<DomainAlbum>>,
	starredAlbumsState: UiState<ImmutableList<DomainAlbum>>,
	aurralAlbumRequests: List<AurralAlbumRequest>,
	aurralAlbumMatchesByLocalAlbumId: Map<String, AurralAlbumSearchItem> = emptyMap(),
	selectedAlbum: DomainAlbum?,
	selectedAlbumIsStarred: Boolean,
	selectedAlbumRating: Int,
	onSelectAlbum: (DomainAlbum) -> Unit,
	onClearAlbumSelection: () -> Unit,
	onStarSelectedAlbum: (Boolean) -> Unit,
	onRateSelectedAlbum: (Int) -> Unit,
	onPlayAlbumNext: () -> Unit,
	onAddAlbumToQueue: () -> Unit,

	// artists
	artistsState: UiState<ImmutableList<DomainArtist>>,
	selectedArtist: DomainArtist?,
	selectedArtistAlbums: ImmutableList<DomainAlbum>?,
	selectedArtistIsStarred: Boolean,
	onSelectArtist: (DomainArtist) -> Unit,
	onClearArtistSelection: () -> Unit,
	onStarSelectedArtist: (Boolean) -> Unit,
	onPlayArtistNext: () -> Unit,
	onAddArtistToQueue: () -> Unit,
	aurralLibraryArtists: List<AurralDiscoverArtist>,
	aurralCollectionRowsState: UiState<List<AurralDiscoveryCollectionRow>>,
	onOpenAurralDiscoverArtist: (AurralDiscoverArtist) -> Unit,
	onOpenAurralDiscoverAlbum: (AurralAlbumSearchItem) -> Unit,

	// playlists
	playlistsState: UiState<ImmutableList<DomainPlaylist>>,
	selectedPlaylist: DomainPlaylist?,
	onSelectPlaylist: (DomainPlaylist) -> Unit,
	onClearPlaylistSelection: () -> Unit,
	onDeletePlaylist: (String) -> Unit,
	onPlayPlaylistNext: () -> Unit,
	onAddPlaylistToQueue: () -> Unit,

	// genres
	genresState: UiState<ImmutableList<DomainGenre>>,

) {
	val backStack = LocalNavStack.current
	val scope = rememberCoroutineScope()
	val resolveArtistCreditDestination = rememberArtistCreditDestinationResolver()
	val preferenceManager = koinInject<PreferenceManager>()
	val aurralRepository = koinInject<AurralRepository>()
	val aurralConfirmationQueue by aurralRepository.confirmationQueue.collectAsStateWithLifecycle()
	val aurralBaseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
	val aurralRequestHeaders = preferenceManager.aurralRequestHeadersMap()
	val localOwnershipStatus = libraryLocalOwnershipStatus(
		aurralConfigured = preferenceManager.aurralEnabled && aurralBaseUrl != null
	)
	fun aurralImageRequestHeaders(imageUrl: String?): Map<String, String> =
		if (aurralBaseUrl != null) {
			aurralRequestHeadersForUrl(aurralBaseUrl, imageUrl, aurralRequestHeaders)
		} else {
			emptyMap()
		}
	val stationPlaylistsState = playlistsState.filterPlaylists(stationsOnly = true)
	val regularPlaylistsState = playlistsState.filterPlaylists(stationsOnly = false)
	val moodMixPlaylistsState = playlistsState.filterPlaylistKind(PlaylistListKind.MoodMixes)
	val genreMixPlaylistsState = playlistsState.filterPlaylistKind(PlaylistListKind.GenreMixes)

	fun LazyGridScope.quickPicksRow() {
		if (!quickPicksEnabled) return
		horizontalSection(
			title = Res.string.option_sort_quick_picks,
			destination = Screen.SongList(true, listType = DomainSongListType.QuickPicks),
			state = quickPicksState,
			key = { it.id },
			seeAll = true
		) { song ->
			QuickPickSongCard(
				modifier = Modifier.animateItem().width(150.dp),
				song = song,
				selected = song == selectedQuickPick,
				starred = selectedQuickPickIsStarred,
				rating = selectedQuickPickRating,
				download = quickPickDownloads.find { it.songId == song.id },
				ownershipStatus = localOwnershipStatus,
				onSelect = { onSelectQuickPick(song) },
				onDeselect = onClearQuickPickSelection,
				onSetStarred = onStarSelectedQuickPick,
				onSetShareId = onSetShareId,
				onStartSongRadio = { onStartQuickPickRadio(song) },
				onPlayNext = { onPlayQuickPickNext(song) },
				onAddToQueue = { onAddQuickPickToQueue(song) },
				onClick = { onPlayQuickPick(song) },
				onSetRating = onRateSelectedQuickPick,
				onDownload = { onDownloadQuickPick(song) },
				onCancelDownload = { onCancelQuickPickDownload(song) },
				onDeleteDownload = { onDeleteQuickPickDownload(song) }
			)
		}
	}

	fun LazyGridScope.mostPlayedRow() {
		horizontalSection(
			title = Res.string.title_most_played,
			destination = Screen.Library(true),
			state = mostPlayedShortcutsState,
			key = { "${it.type.name}:${it.id}" },
			seeAll = false
		) { shortcut ->
			MostPlayedShortcutCard(
				modifier = Modifier.animateItem().width(150.dp),
				shortcut = shortcut,
				imageRequestHeaders = aurralImageRequestHeaders(shortcut.coverArtId),
				onOpen = {
					if (shortcut.type == PlaybackOriginType.Artist) {
						scope.launch {
							resolveArtistCreditDestination(
								shortcut.id,
								shortcut.title,
								false
							)?.let(backStack::add)
						}
					} else {
						backStack.add(mostPlayedShortcutDestination(shortcut))
					}
				}
			)
		}
	}

	fun LazyGridScope.albumRow(
		title: StringResource,
		listType: DomainAlbumListType,
		state: UiState<ImmutableList<DomainAlbum>>
	) {
		horizontalSection(
			title = title,
			destination = Screen.AlbumList(true, listType),
			state = state,
			key = { it.id },
			seeAll = true
		) { album ->
			AlbumListScreenItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "library",
				album = album,
				aurralAlbumMatch = aurralAlbumMatchesByLocalAlbumId[album.id],
				aurralAlbumRequests = aurralAlbumRequests,
				ownershipStatus = localOwnershipStatus,
				selected = album == selectedAlbum,
				starred = selectedAlbumIsStarred,
				onSelect = { onSelectAlbum(album) },
				onDeselect = { onClearAlbumSelection() },
				onSetStarred = { onStarSelectedAlbum(it) },
				onSetShareId = { onSetShareId(it) },
				onPlayNext = onPlayAlbumNext,
				onAddToQueue = onAddAlbumToQueue,
				rating = selectedAlbumRating,
				onSetRating = onRateSelectedAlbum
			)
		}
	}

	fun LazyGridScope.stationsRow() {
		if (stationPlaylistsState.data.orEmpty().isEmpty()) return
		horizontalSection(
			title = Res.string.title_stations,
			destination = Screen.PlaylistList(nested = true, stationsOnly = true),
			state = stationPlaylistsState,
			key = { it.id },
			seeAll = true
		) { playlist ->
			PlaylistListScreenItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "stations",
				playlist = playlist,
				selected = playlist == selectedPlaylist,
				onSelect = { onSelectPlaylist(playlist) },
				onDeselect = { onClearPlaylistSelection() },
				onSetDeletionId = { onDeletePlaylist(it) },
				onSetShareId = { onSetShareId(it) },
				onPlayNext = onPlayPlaylistNext,
				onAddToQueue = onAddPlaylistToQueue
			)
		}
	}

	fun LazyGridScope.playlistsRow() {
		horizontalSection(
			title = Res.string.title_playlists,
			destination = Screen.PlaylistList(true),
			state = regularPlaylistsState,
			key = { it.id },
			seeAll = true
		) { playlist ->
			PlaylistListScreenItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "library",
				playlist = playlist,
				selected = playlist == selectedPlaylist,
				onSelect = { onSelectPlaylist(playlist) },
				onDeselect = { onClearPlaylistSelection() },
				onSetDeletionId = { onDeletePlaylist(it) },
				onSetShareId = { onSetShareId(it) },
				onPlayNext = onPlayPlaylistNext,
				onAddToQueue = onAddPlaylistToQueue
			)
		}
	}

	fun LazyGridScope.moodMixesRow() {
		if (moodMixPlaylistsState.data.orEmpty().isEmpty()) return
		horizontalSection(
			title = Res.string.title_mood_mixes,
			destination = Screen.PlaylistList(nested = true, kind = PlaylistListKind.MoodMixes),
			state = moodMixPlaylistsState,
			key = { it.id },
			seeAll = true
		) { playlist ->
			PlaylistListScreenItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "mood_mixes",
				playlist = playlist,
				selected = playlist == selectedPlaylist,
				onSelect = { onSelectPlaylist(playlist) },
				onDeselect = { onClearPlaylistSelection() },
				onSetDeletionId = { onDeletePlaylist(it) },
				onSetShareId = { onSetShareId(it) },
				onPlayNext = onPlayPlaylistNext,
				onAddToQueue = onAddPlaylistToQueue
			)
		}
	}

	fun LazyGridScope.genreMixesRow() {
		if (genreMixPlaylistsState.data.orEmpty().isEmpty()) return
		horizontalSection(
			title = Res.string.title_genre_mixes,
			destination = Screen.PlaylistList(nested = true, kind = PlaylistListKind.GenreMixes),
			state = genreMixPlaylistsState,
			key = { it.id },
			seeAll = true
		) { playlist ->
			PlaylistListScreenItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "genre_mixes",
				playlist = playlist,
				selected = playlist == selectedPlaylist,
				onSelect = { onSelectPlaylist(playlist) },
				onDeselect = { onClearPlaylistSelection() },
				onSetDeletionId = { onDeletePlaylist(it) },
				onSetShareId = { onSetShareId(it) },
				onPlayNext = onPlayPlaylistNext,
				onAddToQueue = onAddPlaylistToQueue
			)
		}
	}

	fun LazyGridScope.artistsRow() {
		horizontalSection(
			title = Res.string.title_artists,
			destination = Screen.ArtistList(true),
			state = artistsState,
			key = { it.id },
			seeAll = true
		) { artist ->
			ArtistsScreenItem(
				modifier = Modifier.animateItem().width(150.dp),
				tab = "library",
				artist = artist,
				selected = artist == selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				starred = selectedArtistIsStarred,
				aurralMonitorState = aurralMonitorStateForLocalArtist(
					artist = artist,
					libraryArtists = aurralLibraryArtists,
					confirmationQueue = aurralConfirmationQueue
				),
				onSelect = { onSelectArtist(artist) },
				onDeselect = { onClearArtistSelection() },
				onSetStarred = { onStarSelectedArtist(it) },
				onPlayNext = onPlayArtistNext,
				onAddToQueue = onAddArtistToQueue
			)
		}
	}

	fun LazyGridScope.genresRow() {
		horizontalSection(
			title = Res.string.title_genres,
			destination = Screen.GenreList(true),
			state = genresState,
			key = { it.name },
			seeAll = true
		) { genreWithAlbums ->
			GenreListScreenCard(genre = genreWithAlbums)
		}
	}

	fun LazyGridScope.aurralLoadingPlaceholderRow() {
		if (!libraryAurralLoadingPlaceholderVisible(aurralCollectionRowsState)) return
		horizontalSection(
			title = Res.string.title_aurral_recently_added,
			destination = Screen.AurralHub,
			state = UiState.Loading(emptyList<AurralDiscoverArtist>()),
			key = { it.id.trim().ifEmpty { it.name } },
			seeAll = true
		) { artist ->
			AurralDiscoverArtistCard(
				modifier = Modifier.animateItem().width(150.dp),
				artist = artist,
				confirmationQueue = aurralConfirmationQueue,
				onOpenArtist = onOpenAurralDiscoverArtist
			)
		}
	}

	fun LazyGridScope.aurralRows(rowId: LibraryRowId) {
		aurralCollectionRowsState.data.orEmpty()
			.filter { row -> libraryRowIdForAurralKind(row.kind) == rowId }
			.forEach { row ->
				when (row) {
					is AurralDiscoveryCollectionRow.Artists -> {
						val destination = aurralDiscoverCollectionRoute(row) ?: Screen.AurralDiscoverList
						horizontalSection(
							title = row.kind.titleResource(),
							titleFormatArgs = if (row.kind == AurralDiscoveryCollectionKind.GenreArtists) {
								listOf(row.tag.orEmpty())
							} else {
								emptyList()
							},
							destination = destination,
							state = UiState.Success(row.artists),
							key = { it.id.trim().ifEmpty { it.name } },
							seeAll = true
						) { artist ->
							AurralDiscoverArtistCard(
								modifier = Modifier.animateItem().width(150.dp),
								artist = artist,
								confirmationQueue = aurralConfirmationQueue,
								onOpenArtist = onOpenAurralDiscoverArtist
							)
						}
					}

					is AurralDiscoveryCollectionRow.Albums -> horizontalSection(
						title = row.kind.titleResource(),
						destination = Screen.AurralHub,
						state = UiState.Success(row.albums),
						key = { album -> album.id.trim().ifEmpty { "${album.artistMbid}:${album.title}" } },
						seeAll = false
					) { album ->
						AurralAlbumSearchCard(
							modifier = Modifier.animateItem().width(150.dp),
							album = album,
							imageRequestHeaders = aurralImageRequestHeaders(album.coverUrl),
							onClick = { onOpenAurralDiscoverAlbum(album) }
						)
					}

					is AurralDiscoveryCollectionRow.Tags -> {
						val destination = aurralDiscoverCollectionRoute(row) ?: Screen.AurralHub
						aurralTagBrickSection(
							title = row.kind.titleResource(),
							destination = destination,
							tags = row.tags,
							seeAll = destination != Screen.AurralHub,
							onOpenTag = { backStack.add(Screen.AurralDiscoverTag(it)) }
						)
					}
				}
			}
	}

	BackToTopScrollHandler(gridState)

	LazyVerticalGrid(
		modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		state = gridState,
		columns = GridCells.Fixed(2),
		contentPadding = innerPadding.withoutTop() + PaddingValues(top = 8.dp),
		verticalArrangement = Arrangement.spacedBy(5.dp),
		horizontalArrangement = Arrangement.spacedBy(5.dp),
	) {
		libraryScreenOverviewButton(
			icon = Icons.Outlined.LibraryAdd,
			label = Res.string.option_sort_newest,
			destination = Screen.AlbumList(true, DomainAlbumListType.Newest),
			start = true
		)
		libraryScreenOverviewButton(
			icon = Icons.Outlined.Shuffle,
			label = Res.string.option_sort_random,
			destination = Screen.AlbumList(true, DomainAlbumListType.Random),
			start = false
		)
		libraryScreenOverviewButton(
			icon = Icons.Outlined.Star,
			label = Res.string.option_sort_starred,
			destination = Screen.Starred(),
			start = true
		)
		libraryScreenOverviewButton(
			icon = Icons.Outlined.History,
			label = Res.string.option_sort_frequent,
			destination = Screen.AlbumList(true, DomainAlbumListType.Frequent),
			start = false
		)
		libraryRows.forEach { row ->
			when (row) {
				LibraryRowId.QuickPicks -> quickPicksRow()
				LibraryRowId.MostPlayed -> mostPlayedRow()
				LibraryRowId.NewestAlbums -> {
					if (newestAlbumsState.data.orEmpty().isNotEmpty()) {
						albumRow(Res.string.option_sort_newest, DomainAlbumListType.Newest, newestAlbumsState)
					}
				}
				LibraryRowId.StarredAlbums -> {
					if (starredAlbumsState.data.orEmpty().isNotEmpty()) {
						albumRow(Res.string.option_sort_starred, DomainAlbumListType.Starred, starredAlbumsState)
					}
				}
				LibraryRowId.RecentAlbums -> albumRow(
					Res.string.option_sort_recent,
					DomainAlbumListType.Recent,
					albumsState
				)
				LibraryRowId.Stations -> stationsRow()
				LibraryRowId.Playlists -> playlistsRow()
				LibraryRowId.MoodMixes -> moodMixesRow()
				LibraryRowId.GenreMixes -> genreMixesRow()
				LibraryRowId.Artists -> artistsRow()
				LibraryRowId.Genres -> genresRow()
				LibraryRowId.AurralRecentlyAdded -> {
					aurralLoadingPlaceholderRow()
					aurralRows(row)
				}
				LibraryRowId.AurralRecentReleases,
				LibraryRowId.AurralRecommended,
				LibraryRowId.AurralBasedOnLibrary,
				LibraryRowId.AurralGlobalTop,
				LibraryRowId.AurralGenreRows,
				LibraryRowId.AurralTags -> aurralRows(row)
			}
		}
	}
}

private fun LazyGridScope.aurralTagBrickSection(
	title: StringResource,
	destination: Screen,
	tags: List<String>,
	seeAll: Boolean,
	onOpenTag: (String) -> Unit
) {
	if (tags.isEmpty()) return
	header(title, destination = destination, active = seeAll)
	item(span = { GridItemSpan(maxLineSpan) }) {
		AurralDiscoverTagWall(
			tags = tags,
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp)
				.padding(bottom = 4.dp),
			onOpenTag = onOpenTag
		)
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

private fun UiState<ImmutableList<DomainPlaylist>>.filterPlaylists(
	stationsOnly: Boolean
): UiState<List<DomainPlaylist>> {
	fun List<DomainPlaylist>.filtered() =
		if (stationsOnly) stationPlaylists() else regularPlaylists()

	return when (this) {
		is UiState.Error -> UiState.Error(error, data?.filtered())
		is UiState.Loading -> UiState.Loading(data?.filtered())
		is UiState.Success -> UiState.Success(data.filtered())
	}
}

private fun UiState<ImmutableList<DomainPlaylist>>.filterPlaylistKind(
	kind: PlaylistListKind
): UiState<List<DomainPlaylist>> {
	fun List<DomainPlaylist>.filtered() =
		when (kind) {
			PlaylistListKind.User -> regularPlaylists()
			PlaylistListKind.Stations -> stationPlaylists()
			PlaylistListKind.MoodMixes -> moodMixPlaylists()
			PlaylistListKind.GenreMixes -> genreMixPlaylists()
		}

	return when (this) {
		is UiState.Error -> UiState.Error(error, data?.filtered())
		is UiState.Loading -> UiState.Loading(data?.filtered())
		is UiState.Success -> UiState.Success(data.filtered())
	}
}
