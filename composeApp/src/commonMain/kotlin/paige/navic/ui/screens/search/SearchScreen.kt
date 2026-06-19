package paige.navic.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.insert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_add_to_queue
import navic.composeapp.generated.resources.action_clear_search_history
import navic.composeapp.generated.resources.action_remove_from_history
import navic.composeapp.generated.resources.action_search_history
import navic.composeapp.generated.resources.info_no_search_results
import navic.composeapp.generated.resources.info_not_available_offline
import navic.composeapp.generated.resources.title_albums
import navic.composeapp.generated.resources.title_all
import navic.composeapp.generated.resources.title_aurral_albums
import navic.composeapp.generated.resources.title_aurral_artists
import navic.composeapp.generated.resources.title_artists
import navic.composeapp.generated.resources.title_songs
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainAlbumListType
import paige.navic.domain.models.DomainArtist
import paige.navic.domain.models.DomainArtistListType
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.QueueDuplicateAction
import paige.navic.domain.models.duplicateQueueActionFor
import paige.navic.domain.models.hasStableNavidromeSongId
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Close
import paige.navic.icons.outlined.History
import paige.navic.icons.outlined.NoSearchResults
import paige.navic.icons.outlined.Offline
import paige.navic.icons.outlined.Queue
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorBox
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.MusicIntegrationServices
import paige.navic.ui.components.common.MarqueeText
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.dialogs.QueueDuplicateDialog
import paige.navic.ui.components.layouts.ArtGrid
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.layouts.artGridPlaceholder
import paige.navic.ui.components.layouts.horizontalSection
import paige.navic.ui.components.sheets.SongSheet
import paige.navic.ui.components.sheets.lidaClipsMusicVideoAction
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.album.components.AlbumListScreenItem
import paige.navic.ui.screens.album.viewmodels.AlbumListViewModel
import paige.navic.ui.screens.artist.ArtistsScreenItem
import paige.navic.ui.screens.artist.viewmodels.ArtistListViewModel
import paige.navic.ui.screens.aurral.AurralAlbumSearchCard
import paige.navic.ui.screens.aurral.aurralAlbumSearchDestination
import paige.navic.ui.screens.aurral.aurralArtistRecommendationRoute
import paige.navic.ui.screens.library.components.AurralDiscoverArtistCard
import paige.navic.ui.screens.search.components.SearchScreenChips
import paige.navic.ui.screens.search.components.SearchScreenTopBar
import paige.navic.ui.screens.search.viewmodels.SearchViewModel
import paige.navic.ui.screens.search.viewmodels.visibleSearchHistory
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.aurralRequestHeadersForUrl
import paige.navic.domain.repositories.configuredAurralBaseUrl

enum class SearchCategory(val res: StringResource) {
	ALL(Res.string.title_all),
	SONGS(Res.string.title_songs),
	ALBUMS(Res.string.title_albums),
	ARTISTS(Res.string.title_artists)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
	nested: Boolean,
	initialQuery: String = ""
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val aurralRepository = koinInject<AurralRepository>()

	val viewModel = koinViewModel<SearchViewModel>()
	val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()

	val artistListViewModel = koinViewModel<ArtistListViewModel> {
		parametersOf(DomainArtistListType.AlphabeticalByName)
	}
	val artistListSelection by artistListViewModel.selectedArtist.collectAsState()
	val artistListSelectionAlbums by artistListViewModel.selectedArtistAlbums.collectAsState()
	val artistListStarred by artistListViewModel.starred.collectAsState()
	val allLocalArtistsState by artistListViewModel.artistsState.collectAsStateWithLifecycle()

	val albumListViewModel = koinViewModel<AlbumListViewModel> {
		parametersOf(DomainAlbumListType.AlphabeticalByName)
	}
	val albumListSelection by albumListViewModel.selectedAlbum.collectAsState()
	val albumListStarred by albumListViewModel.starred.collectAsState()
	val selectedAlbumRating by albumListViewModel.rating.collectAsStateWithLifecycle()
	val aurralAlbumRequests by albumListViewModel.aurralAlbumRequests.collectAsStateWithLifecycle()
	val aurralConfirmationQueue by aurralRepository.confirmationQueue.collectAsStateWithLifecycle()

	val query = viewModel.searchQuery
	val state by viewModel.searchState.collectAsState()
	LaunchedEffect(initialQuery) {
		viewModel.setInitialQuery(initialQuery)
	}
	val aurralSearchConfigured = preferenceManager.aurralEnabled &&
		configuredAurralBaseUrl(preferenceManager.aurralBaseUrl) != null
	val searchIntegrationIndicators = integrationLoadingIndicators(
		aurralLoading = query.text.isNotBlank() && aurralSearchConfigured && state is UiState.Loading
	)
	val rawSearchHistory by viewModel.searchHistory.collectAsState(initial = emptyList())
	val searchHistory = visibleSearchHistory(
		history = rawSearchHistory,
		pauseSearchHistory = preferenceManager.pauseSearchHistory
	)
	val isOnline by viewModel.isOnline.collectAsState()
	val downloadedSongs by viewModel.downloadedSongs.collectAsState()

	val platformContext = LocalPlatformContext.current
	val player = koinInject<MediaPlayerViewModel>()
	val backStack = LocalNavStack.current

	var selectedCategory by remember { mutableStateOf(SearchCategory.ALL) }
	var songToQueue by remember { mutableStateOf<DomainSong?>(null) }
	var queueDuplicateAction by remember { mutableStateOf<QueueDuplicateAction?>(null) }
	BackToTopScrollHandler(viewModel.gridState)

	fun queueSongOrConfirmDuplicate(
		song: DomainSong,
		action: QueueDuplicateAction,
		onQueue: () -> Unit
	) {
		val duplicateAction = duplicateQueueActionFor(
			queueSongIds = player.uiState.value.queue.map { it.id },
			songId = song.id,
			action = action
		)
		if (duplicateAction != null) {
			songToQueue = song
			queueDuplicateAction = duplicateAction
		} else {
			onQueue()
		}
	}

	Scaffold(
		topBar = {
			Column(
				modifier = Modifier
					.background(MaterialTheme.colorScheme.surface)
					.padding(
						TopAppBarDefaults.windowInsets.asPaddingValues()
					)
			) {
				SearchScreenTopBar(
					query = query,
					nested = nested,
					onSearch = { submittedQuery ->
						viewModel.addToSearchHistory(submittedQuery)
					}
				)
				SearchScreenChips(
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
		Box(Modifier.fillMaxSize()) {
			AnimatedContent(
				state,
				modifier = Modifier.fillMaxSize()
			) { uiState ->
				when (uiState) {
					is UiState.Loading -> ArtGrid(contentPadding = contentPadding) { artGridPlaceholder() }
					is UiState.Error -> ErrorBox(uiState, padding = contentPadding)
					is UiState.Success -> {
						val results = uiState.data
						val buckets = searchResultBuckets(results, selectedCategory)
						val albums = buckets.albums
						val artists = buckets.artists
						val songs = buckets.songs
						val aurralArtists = buckets.aurralArtists
						val aurralAlbums = buckets.aurralAlbums

					if (query.text.isNotBlank() && buckets.isEmpty) {
						ContentUnavailable(
							icon = Icons.Outlined.NoSearchResults,
							label = stringResource(Res.string.info_no_search_results)
						)
					}

					LazyVerticalGrid(
						modifier = Modifier.fillMaxSize(),
						columns = GridCells.Fixed(2),
						contentPadding = contentPadding,
						state = viewModel.gridState,
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						if (query.text.isNotBlank()) {
							if (songs.isNotEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									Text(
										stringResource(Res.string.title_songs),
										style = MaterialTheme.typography.headlineSmall,
										modifier = Modifier.padding(
											horizontal = 16.dp,
											vertical = 8.dp
										)
									)
								}
								items(
									songs.take(10).size,
									span = { GridItemSpan(maxLineSpan) }) { index ->
									val song = songs[index]
									val isDownloaded = downloadedSongs.containsKey(song.id)
									val canPlay = isOnline || isDownloaded

									val dismissState = rememberSwipeToDismissBoxState()

									LaunchedEffect(dismissState.currentValue) {
										if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
											queueSongOrConfirmDuplicate(song, QueueDuplicateAction.AddToQueue) {
												player.addToQueueSingle(song)
											}
											dismissState.snapTo(SwipeToDismissBoxValue.Settled)
										}
									}

									SwipeToDismissBox(
										state = dismissState,
										enableDismissFromStartToEnd = false,
										enableDismissFromEndToStart = true,
										backgroundContent = {
											val backgroundColor by animateColorAsState(
												targetValue = when (dismissState.targetValue) {
													SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
													else -> Color.Transparent
												}
											)
											val iconColor by animateColorAsState(
												targetValue = when (dismissState.targetValue) {
													SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onPrimaryContainer
													else -> MaterialTheme.colorScheme.onSurfaceVariant
												}
											)

											Box(
												modifier = Modifier
													.fillMaxSize()
													.background(color = backgroundColor)
													.padding(horizontal = 20.dp),
												contentAlignment = Alignment.CenterEnd
											) {
												Icon(
													imageVector = Icons.Outlined.Queue,
													contentDescription = stringResource(Res.string.action_add_to_queue),
													tint = iconColor
												)
											}
										}
									) {
										ListItem(
											modifier = Modifier
												.background(MaterialTheme.colorScheme.surface),
											onClick = {
												platformContext.clickSound()
												player.playNow(song)
											},
											onLongClick = { viewModel.selectSong(song) },
											content = { Text(song.title) },
											supportingContent = {
												MarqueeText(
													"${song.albumTitle ?: ""} • ${song.artistName} • ${song.year ?: ""}"
												)
											},
											leadingContent = {
												CoverArt(
													coverArtId = song.coverArtId,
													modifier = Modifier.size(50.dp),
													shape = preferenceManager.coverArtShape.decreasedShape
												)
											},
											trailingContent = {
												if (!canPlay) {
													Icon(
														Icons.Outlined.Offline,
														stringResource(Res.string.info_not_available_offline),
														modifier = Modifier.size(20.dp)
													)
												}
											}
										)
										if (selectedSong == song) {
											SongSheet(
												onDismissRequest = { viewModel.clearSelectedSong() },
												song = song,
												onStartSongRadio = if (hasStableNavidromeSongId(song.id)) {
													{ player.startSongRadio(song) }
												} else null,
												onPlayMusicVideo = lidaClipsMusicVideoAction(song),
												onPlayNext = {
													queueSongOrConfirmDuplicate(song, QueueDuplicateAction.PlayNext) {
														player.playNextSingle(song)
													}
												},
												onAddToQueue = {
													queueSongOrConfirmDuplicate(song, QueueDuplicateAction.AddToQueue) {
														player.addToQueueSingle(song)
													}
												},
												downloadStatus = if (downloadedSongs.containsKey(
														song.id
													)
												) DownloadStatus.DOWNLOADED else null,
												onTrackInfo = dropUnlessResumed {
													backStack.add(Screen.SongDetail(song.id))
												},
												onViewAlbum = song.albumId?.let { albumId ->
													dropUnlessResumed {
														backStack.add(
															Screen.CollectionDetail(
																collectionId = albumId,
																tab = "search"
															)
														)
													}
												},
												starred = selectedSongIsStarred,
												onSetStarred = { viewModel.starSelectedSong(it) },
												rating = selectedSongRating,
												onSetRating = { viewModel.rateSelectedSong(it) }
											)
										}
									}
								}
							}

							horizontalSection(
								title = Res.string.title_albums,
								destination = Screen.AlbumList(true),
								state = UiState.Success(albums),
								key = { it.id },
								seeAll = false
							) { album ->
								AlbumListScreenItem(
									modifier = Modifier.animateItem(fadeInSpec = null)
										.width(150.dp),
									tab = "search",
									album = album,
									aurralAlbumRequests = aurralAlbumRequests,
									selected = album == albumListSelection,
									starred = albumListStarred,
									onSelect = { albumListViewModel.selectAlbum(album) },
									onDeselect = { albumListViewModel.clearSelection() },
									onSetStarred = { albumListViewModel.starAlbum(it) },
									onSetShareId = { },
									onPlayNext = { player.playNext(album as DomainSongCollection)},
									onAddToQueue = { player.addToQueue(album as DomainSongCollection)},
									rating = selectedAlbumRating,
									onSetRating = { albumListViewModel.setRating(it) }
								)
							}

							horizontalSection(
								title = Res.string.title_aurral_albums,
								destination = Screen.AurralHub,
								state = UiState.Success(aurralAlbums),
								key = { it.id },
								seeAll = false
							) { album ->
								val baseUrl = configuredAurralBaseUrl(preferenceManager.aurralBaseUrl)
								val requestHeaders = preferenceManager.aurralRequestHeadersMap()
								AurralAlbumSearchCard(
									modifier = Modifier.animateItem(fadeInSpec = null)
										.width(150.dp),
									album = album,
									imageRequestHeaders = if (baseUrl != null) {
										aurralRequestHeadersForUrl(
											baseUrl = baseUrl,
											imageUrl = album.coverUrl,
											requestHeaders = requestHeaders
										)
									} else {
										emptyMap()
									},
									onClick = {
										aurralAlbumSearchDestination(album)?.let(backStack::add)
									}
								)
							}

							horizontalSection(
								title = Res.string.title_artists,
								destination = Screen.ArtistList(true),
								state = UiState.Success(artists),
								key = { it.id },
								seeAll = false
							) { artist ->
								ArtistsScreenItem(
									modifier = Modifier.animateItem(fadeInSpec = null)
										.width(150.dp),
									tab = "search",
									artist = artist,
									selected = artist == artistListSelection,
									selectedArtistAlbums = artistListSelectionAlbums,
									starred = artistListStarred,
									onSelect = { artistListViewModel.selectArtist(artist) },
									onDeselect = { artistListViewModel.clearSelection() },
									onSetStarred = { artistListViewModel.starArtist(it) },
									onPlayNext = { artistListViewModel.playArtistAlbumsNext(player) },
									onAddToQueue = { artistListViewModel.addArtistAlbumsToQueue(player) }
								)
							}

							horizontalSection(
								title = Res.string.title_aurral_artists,
								destination = Screen.AurralHub,
								state = UiState.Success(aurralArtists),
								key = { it.id },
								seeAll = false
							) { artist ->
								AurralDiscoverArtistCard(
									modifier = Modifier.animateItem(fadeInSpec = null)
										.width(150.dp),
									artist = artist,
									confirmationQueue = aurralConfirmationQueue,
									onOpenArtist = {
										aurralArtistRecommendationRoute(
											artist = it,
											localArtists = allLocalArtistsState.data.orEmpty()
										)?.let(backStack::add)
									}
								)
							}
						} else {
							if (searchHistory.isNotEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									Row(
										modifier = Modifier
											.fillMaxWidth()
											.padding(
												start = 20.dp,
												top = 4.dp,
												end = 8.dp,
												bottom = 4.dp
											),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Text(
											text = stringResource(Res.string.action_search_history),
											style = MaterialTheme.typography.titleMedium,
											color = MaterialTheme.colorScheme.primary
										)
										IconButton(onClick = {
											platformContext.clickSound()
											viewModel.clearSearchHistory()
										}) {
											Icon(
												imageVector = Icons.Outlined.Close,
												contentDescription = stringResource(Res.string.action_clear_search_history),
												tint = MaterialTheme.colorScheme.onSurfaceVariant
											)
										}
									}
								}
								items(
									searchHistory.size,
									span = { GridItemSpan(maxLineSpan) }) { index ->
									val historyItem = searchHistory[index]
									ListItem(
										modifier = Modifier.clickable {
											platformContext.clickSound()
											query.clearText()
											query.edit { insert(0, historyItem) }
										},
										headlineContent = { Text(historyItem) },
										leadingContent = {
											Icon(
												imageVector = Icons.Outlined.History,
												contentDescription = null,
												tint = MaterialTheme.colorScheme.onSurfaceVariant
											)
										},
										trailingContent = {
											IconButton(onClick = {
												platformContext.clickSound()
												viewModel.removeFromSearchHistory(historyItem)
											}) {
												Icon(
													imageVector = Icons.Outlined.Close,
													contentDescription = stringResource(Res.string.action_remove_from_history),
													tint = MaterialTheme.colorScheme.onSurfaceVariant
												)
											}
										}
									)
								}
							}
						}
					}
					}
				}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = searchIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = searchIntegrationIndicators,
					relevantServices = MusicIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = contentPadding.calculateTopPadding() + 8.dp)
			)
		}
	}

	if (songToQueue != null) {
		QueueDuplicateDialog(
			onDismissRequest = {
				songToQueue = null
				queueDuplicateAction = null
			},
			onConfirm = {
				songToQueue?.let { song ->
					when (queueDuplicateAction) {
						QueueDuplicateAction.PlayNext -> player.playNextSingle(song)
						QueueDuplicateAction.AddToQueue,
						null -> player.addToQueueSingle(song)
					}
				}
			}
		)
	}
}
