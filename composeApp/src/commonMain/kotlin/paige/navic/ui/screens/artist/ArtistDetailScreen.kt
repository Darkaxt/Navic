package paige.navic.ui.screens.artist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_see_all
import navic.composeapp.generated.resources.info_aurral_external_artist
import navic.composeapp.generated.resources.info_aurral_loading_catalog
import navic.composeapp.generated.resources.info_aurral_match_percent
import navic.composeapp.generated.resources.info_aurral_monitor_confirmed
import navic.composeapp.generated.resources.info_aurral_monitor_stopped
import navic.composeapp.generated.resources.info_aurral_monitor_waiting
import navic.composeapp.generated.resources.info_aurral_unmonitor_waiting
import navic.composeapp.generated.resources.info_bulk_download_warning
import navic.composeapp.generated.resources.option_sort_frequent
import navic.composeapp.generated.resources.title_aurral_recommendations
import navic.composeapp.generated.resources.title_bulk_download
import navic.composeapp.generated.resources.title_lastfm_top_tracks
import navic.composeapp.generated.resources.title_similar_artists
import navic.composeapp.generated.resources.title_aurral_missing_albums
import navic.composeapp.generated.resources.title_aurral_owned_partial_albums
import navic.composeapp.generated.resources.title_aurral_preview_tracks
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.LocalSnackbarState
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralArtistExternalLink
import paige.navic.domain.models.AurralArtistOwnershipAlbumRow
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.domain.models.aurralAlbumAcquisitionProgress
import paige.navic.domain.models.aurralPreviewTrackOwnershipStatus
import paige.navic.domain.repositories.AurralConfirmationStatus
import paige.navic.domain.repositories.AurralRepository
import paige.navic.domain.repositories.aurralArtistMonitoringConfirmationItem
import paige.navic.domain.repositories.aurralRequestHeadersForUrl
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.AurralAcquisitionProgressBar
import paige.navic.ui.components.common.AurralOwnershipStatusDot
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorBox
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.MusicIntegrationServices
import paige.navic.ui.components.common.SongRow
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.common.rememberAurralFirstArtistArtworkUiState
import paige.navic.ui.components.dialogs.BulkDownloadDialog
import paige.navic.ui.components.layouts.ArtCarousel
import paige.navic.ui.components.layouts.ArtCarouselItem
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.sheets.CollectionSheet
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.aurral.AurralConfirmationQueueSnackbar
import paige.navic.ui.screens.aurral.AurralRecommendedAlbumItem
import paige.navic.ui.screens.aurral.aurralAlbumSearchDestination
import paige.navic.ui.screens.artist.components.ArtistActionButtons
import paige.navic.ui.screens.artist.components.ArtistDetailScreenHeading
import paige.navic.ui.screens.artist.components.ArtistDetailScreenTopBar
import paige.navic.ui.screens.artist.components.AurralPreviewTracks
import paige.navic.ui.screens.artist.viewmodels.AurralArtistActionFeedback
import paige.navic.ui.screens.artist.viewmodels.ArtistDetailViewModel
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.icons.Icons
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
	artistId: String
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val aurralRepository = koinInject<AurralRepository>()

	val viewModel = koinViewModel<ArtistDetailViewModel>(
		key = artistId,
		parameters = { parametersOf(artistId) }
	)
	val platformContext = LocalPlatformContext.current
	val snackbarState = LocalSnackbarState.current
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val selection by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()

	val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by viewModel.selectedAlbumIsStarred.collectAsStateWithLifecycle()
	val selectedAlbumRating by viewModel.selectedAlbumRating.collectAsStateWithLifecycle()
	val monitoringInAurral by viewModel.monitoringInAurral.collectAsStateWithLifecycle()

	val downloadManager = koinInject<DownloadManager>()
	val density = LocalDensity.current
	val backStack = LocalNavStack.current
	val layoutDirection = LocalLayoutDirection.current
	val artistState by viewModel.artistState.collectAsStateWithLifecycle()
	val aurralConfirmationQueue by aurralRepository.confirmationQueue.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsState()
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
	val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
	val playlistSongIds by viewModel.playlistSongIds.collectAsStateWithLifecycle()
	val downloadStatus by viewModel.collectionDownloadStatus()
		.collectAsState(DownloadStatus.NOT_DOWNLOADED)

	val scope = rememberCoroutineScope()

	val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
	val effectSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

	val scrolled by remember {
		derivedStateOf {
			with(density) { viewModel.scrollState.value.toDp() } >= 200.dp
		}
	}

	val frequentGridState = rememberLazyGridState()
	val lastFmGridState = rememberLazyGridState()
	BackToTopScrollHandler(viewModel.scrollState)

	var showDownloadDialog by remember { mutableStateOf(false) }

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }

	val artistData = (artistState as? UiState.Success)?.data
	val artistAurralMonitorPending = artistData?.let { data ->
		aurralArtistMonitoringConfirmationItem(
			queue = aurralConfirmationQueue,
			artistMbid = data.aurralArtistMbid ?: data.artist.musicBrainzId
		)
	}?.status == AurralConfirmationStatus.Pending
	val artistIntegrationIndicators = artistData?.let { data ->
		integrationLoadingIndicators(
			aurralLoading = data.aurralLoading || monitoringInAurral || artistAurralMonitorPending,
			lastFmLoading = data.lastFmLoading
		)
	}.orEmpty()
	val aurralFeedback = artistData?.aurralFeedback
	val aurralFeedbackMessage = when (aurralFeedback) {
		AurralArtistActionFeedback.MonitoringQueued ->
			stringResource(Res.string.info_aurral_monitor_waiting)
		AurralArtistActionFeedback.UnmonitoringQueued ->
			stringResource(Res.string.info_aurral_unmonitor_waiting)
		AurralArtistActionFeedback.MonitoringEnabled ->
			stringResource(Res.string.info_aurral_monitor_confirmed)
		AurralArtistActionFeedback.MonitoringDisabled ->
			stringResource(Res.string.info_aurral_monitor_stopped)
		null -> null
	}
	LaunchedEffect(aurralFeedback) {
		val message = aurralFeedbackMessage ?: return@LaunchedEffect
		snackbarState.showSnackbar(message)
		viewModel.clearAurralFeedback()
	}

	ErrorSnackbar(
		error = artistData?.aurralError?.let(::Exception),
		onClearError = { viewModel.clearAurralError() }
	)
	AurralConfirmationQueueSnackbar(aurralRepository)

	Scaffold(
		topBar = {
			ArtistDetailScreenTopBar(
				scrolled = scrolled,
				artistState = artistState,
				starred = starred,
				onSetStarred = { viewModel.starArtist(it) },
			)
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { contentPadding ->
		val transitionTarget = (artistState as? UiState.Success)
			?.data
			?.let(::artistDetailTransitionKey)
			?: artistState::class.simpleName.orEmpty()
		Box(Modifier.fillMaxSize()) {
			AnimatedContent(
				targetState = transitionTarget,
				transitionSpec = {
					(fadeIn(
						animationSpec = effectSpec
					) + scaleIn(
						initialScale = 0.8f,
						animationSpec = spatialSpec
					)) togetherWith (fadeOut(
						animationSpec = effectSpec
					) + scaleOut(
						animationSpec = spatialSpec
					))
				},
				modifier = Modifier.fillMaxSize()
			) {
				when (val currentArtistState = artistState) {
					is UiState.Error -> Box(Modifier.fillMaxSize().padding(contentPadding)) {
						ErrorBox(currentArtistState)
					}

					is UiState.Loading -> Box(Modifier.fillMaxSize()) {
						ContainedLoadingIndicator(Modifier.size(80.dp).align(Alignment.Center))
					}

					is UiState.Success -> {
						val state = currentArtistState.data
					val ownedOrPartialRows = remember(state.aurralOwnedOrPartialAlbums, state.albums) {
						state.aurralOwnedOrPartialAlbums.ifEmpty {
							state.albums.map { album ->
								AurralArtistOwnershipAlbumRow(
									releaseGroup = null,
									localAlbum = album,
									title = album.name,
									year = album.year?.toString(),
									coverUrl = null,
									requestStatus = null,
									requestable = false,
									ownershipStatus = AurralOwnershipStatus.Owned,
									localSongs = album.songs
								)
							}
						}.toImmutableList()
					}
					val missingReleaseGroupRows = remember(state.aurralMissingReleaseGroups) {
						state.aurralMissingReleaseGroups.toImmutableList()
					}
					val previewTrackOwnershipStatuses = remember(
						state.aurralPreviewTracks,
						ownedOrPartialRows,
						missingReleaseGroupRows
					) {
						state.aurralPreviewTracks.associate { track ->
							track.id to aurralPreviewTrackOwnershipStatus(
								track = track,
								fallbackAlbumStatus = aurralPreviewTrackAlbumOwnershipStatus(
									track = track,
									rows = ownedOrPartialRows + missingReleaseGroupRows
								)
							)
						}.toImmutableMap()
					}
					val displayArtistName = state.aurralArtistName
						?.trim()
						?.takeIf { it.isNotEmpty() }
						?: state.artist.name
					val displayBiography = state.aurralArtistBio
						?.trim()
						?.takeIf { it.isNotEmpty() }
						?: state.artist.biography
					val headingArtwork = rememberAurralFirstArtistArtworkUiState(
						artistId = state.artist.id,
						artistMusicBrainzId = state.artist.musicBrainzId,
						artistName = displayArtistName,
						serverCoverArtId = state.artist.coverArtId
							.takeUnless { preferenceManager.aurralEnabled && state.aurralLoading },
						externalArtistImageUrl = state.aurralArtistImageUrl ?: state.artist.artistImageUrl,
						externalArtistCacheKey = state.aurralArtistImageUrl ?: state.artist.artistImageUrl
					)
					BulkDownloadDialog(
						title = stringResource(Res.string.title_bulk_download),
						message = stringResource(Res.string.info_bulk_download_warning, state.artist.name),
						showDialog = showDownloadDialog,
						onDismissRequest = { showDownloadDialog = false },
						onConfirm = {
							scope.launch {
								state.albums.forEach { album ->
									downloadManager.downloadCollection(album)
								}
							}
						}
					)
					Column(
						modifier = Modifier
							.fillMaxSize()
							.verticalScroll(viewModel.scrollState),
						verticalArrangement = Arrangement.spacedBy(12.dp),
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						ArtistDetailScreenHeading(
							artistName = displayArtistName,
							coverArtId = headingArtwork.coverArtId,
							imageUrl = headingArtwork.imageUrl,
							imageRequestHeaders = headingArtwork.imageRequestHeaders,
							imageDiagnosticLabel = "artist-detail-${state.artist.id}",
							subtitle = displayBiography,
							innerPadding = contentPadding,
							scrolled = scrolled,
							artworkResolving = state.aurralLoading
						)
						AurralArtistProfileMetadata(
							genres = state.aurralArtistGenres,
							externalLinks = state.aurralArtistExternalLinks,
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 20.dp)
						)
						ArtistActionButtons(
							onPlay = { viewModel.playArtistAlbums(player) },
							onShuffle = { viewModel.shuffleArtistAlbums(player) },
							onDownload = {
								showDownloadDialog = true
							},
							onCancelDownload = {
								state.albums.forEach { album ->
									downloadManager.cancelCollectionDownload(album)
								}
							},
							onDeleteDownload = {
								state.albums.forEach { album ->
									downloadManager.deleteDownloadedCollection(album)
								}
							},
							downloadStatus = downloadStatus,
							playEnabled = state.albums.isNotEmpty(),
							onMonitorInAurral = if (
								shouldShowAurralMonitorAction(
									aurralEnabled = preferenceManager.aurralEnabled,
									candidateArtistMbid = state.aurralArtistMbid ?: state.artist.musicBrainzId,
									aurralMonitored = state.aurralMonitored
								)
							) {
								{
									when (state.aurralMonitored) {
										true -> viewModel.setArtistMonitoringInAurral(monitored = false)
										false -> viewModel.monitorArtistInAurral()
										null -> viewModel.monitorArtistInAurral()
									}
								}
							} else {
								null
							},
							monitorInAurralEnabled = true,
							monitoringInAurral = monitoringInAurral,
							monitorPendingInAurral = artistAurralMonitorPending,
							monitoredInAurral = state.aurralMonitored,
							modifier = Modifier.padding(top = 8.dp)
						)
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.padding(
									start = contentPadding.calculateStartPadding(
										layoutDirection
									)
								)
								.padding(
									end = contentPadding.calculateEndPadding(
										layoutDirection
									)
								),
							verticalArrangement = Arrangement.spacedBy(12.dp),
							horizontalAlignment = Alignment.CenterHorizontally
						) {
							state.topSongs.takeIf { state.topSongs.isNotEmpty() }
								?.let { songs ->
									Row(
										modifier = Modifier
											.heightIn(min = 32.dp)
											.padding(top = 8.dp)
											.padding(horizontal = 16.dp)
											.fillMaxWidth(),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Text(
											stringResource(Res.string.option_sort_frequent),
											style = MaterialTheme.typography.titleMediumEmphasized,
											fontWeight = FontWeight(600)
										)
										Text(
											stringResource(Res.string.action_see_all),
											style = MaterialTheme.typography.labelLarge,
											color = MaterialTheme.colorScheme.primary,
											modifier = Modifier.clickable(onClick = dropUnlessResumed {
												platformContext.clickSound()
												backStack.add(
													Screen.SongList(
														nested = true,
														artistId = state.artist.id,
														artistName = state.artist.name
													)
												)
											})
										)
									}
									LazyHorizontalGrid(
										rows = GridCells.Fixed(artistTopSongsGridRows(songs.size)),
										state = frequentGridState,
										flingBehavior = rememberSnapFlingBehavior(lazyGridState = frequentGridState),
										modifier = Modifier
											.fillMaxWidth()
											.height(artistTopSongsGridHeightDp(songs.size).dp)
									) {
										itemsIndexed(songs) { index, song ->
											val download = allDownloads.find { it.songId == song.id }
											SongRow(
												modifier = Modifier.weight(1f),
												song = song,
											isCurrentTrack = playerState.currentSong?.id == song.id,
											isPlaying = !playerState.isPaused,
												selected = selection == song,
												onClick = {
													if (playerState.currentSong?.id != song.id) {
														player.clearQueue()
														songs.forEach { song -> player.addToQueueSingle(song) }
														player.playAt(index)
													} else {
														player.togglePlay()
													}
												},
												onLongClick = {
													viewModel.selectSong(song)
												},
												onDismissRequest = { viewModel.clearSelection() },
												starredState = if (selection == song) selectedSongIsStarred else song.starredAt != null,
												onAddStar = { viewModel.starSelectedSong() },
												onRemoveStar = { viewModel.unstarSelectedSong() },
												download = download,
												onDownload = { viewModel.downloadSong(song) },
												onCancelDownload = { viewModel.cancelDownload(song.id) },
												onDeleteDownload = { viewModel.deleteDownload(song.id) },
												onPlayNext = { player.playNextSingle(song) },
												onAddToQueue = { player.addToQueueSingle(song) },
												onShare = { shareId = song.id },
												isOnline = isOnline,
												rating = selectedSongRating,
												onSetRating = { viewModel.rateSelectedSong(it) },
												inPlaylist = song.id in playlistSongIds
											)
										}
									}
								}
							state.lastFmTopSongs.takeIf { state.lastFmTopSongs.isNotEmpty() }
								?.let { songs ->
									Row(
										modifier = Modifier
											.heightIn(min = 32.dp)
											.padding(top = 8.dp)
											.padding(horizontal = 16.dp)
											.fillMaxWidth(),
										verticalAlignment = Alignment.CenterVertically,
										horizontalArrangement = Arrangement.SpaceBetween
									) {
										Text(
											stringResource(Res.string.title_lastfm_top_tracks),
											style = MaterialTheme.typography.titleMediumEmphasized,
											fontWeight = FontWeight(600)
										)
									}
									LazyHorizontalGrid(
										rows = GridCells.Fixed(artistTopSongsGridRows(songs.size)),
										state = lastFmGridState,
										flingBehavior = rememberSnapFlingBehavior(lazyGridState = lastFmGridState),
										modifier = Modifier
											.fillMaxWidth()
											.height(artistTopSongsGridHeightDp(songs.size).dp)
									) {
										itemsIndexed(songs) { index, song ->
											val download = allDownloads.find { it.songId == song.id }
											SongRow(
												modifier = Modifier.weight(1f),
												song = song,
											isCurrentTrack = playerState.currentSong?.id == song.id,
											isPlaying = !playerState.isPaused,
												selected = selection == song,
												onClick = {
													if (playerState.currentSong?.id != song.id) {
														player.clearQueue()
														songs.forEach { song -> player.addToQueueSingle(song) }
														player.playAt(index)
													} else {
														player.togglePlay()
													}
												},
												onLongClick = {
													viewModel.selectSong(song)
												},
												onDismissRequest = { viewModel.clearSelection() },
												starredState = selectedSongIsStarred,
												onAddStar = { viewModel.starSelectedSong() },
												onRemoveStar = { viewModel.unstarSelectedSong() },
												download = download,
												onDownload = { viewModel.downloadSong(song) },
												onCancelDownload = { viewModel.cancelDownload(song.id) },
												onDeleteDownload = { viewModel.deleteDownload(song.id) },
												onPlayNext = { player.playNextSingle(song) },
												onAddToQueue = { player.addToQueueSingle(song) },
												onShare = { shareId = song.id },
												isOnline = isOnline,
												rating = selectedSongRating,
												onSetRating = { viewModel.rateSelectedSong(it) },
												inPlaylist = song.id in playlistSongIds
											)
										}
									}
								}
							ArtCarousel(
								stringResource(Res.string.title_aurral_recommendations),
								state.aurralRecommendedAlbums.toImmutableList()
							) { album ->
								AurralRecommendedAlbumItem(
									album = album,
									imageRequestHeaders = aurralRequestHeadersForUrl(
										baseUrl = preferenceManager.aurralBaseUrl,
										imageUrl = album.coverUrl,
										requestHeaders = preferenceManager.aurralRequestHeadersMap()
									),
									onClick = {
										aurralAlbumSearchDestination(album)?.let(backStack::add)
									}
								)
							}
							ArtCarousel(
								stringResource(Res.string.title_aurral_owned_partial_albums),
								ownedOrPartialRows
							) { row ->
								val album = row.localAlbum
								if (album != null) {
										val albumDownloadStatus by downloadManager
											.getCollectionDownloadStatus(album.songs.map { it.id })
											.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)
										val coverUrl = row.coverUrl
										val imageRequestHeaders = aurralRequestHeadersForUrl(
											baseUrl = preferenceManager.aurralBaseUrl,
											imageUrl = coverUrl,
											requestHeaders = preferenceManager.aurralRequestHeadersMap()
										)
										ArtCarouselItem(
											coverArtId = album.coverArtId,
											imageUrl = coverUrl,
											imageCacheKey = row.releaseGroup?.id?.let { "aurral-release-group-$it" },
											imageRequestHeaders = imageRequestHeaders,
											title = row.title,
											subtitle = row.year ?: album.year?.toString(),
											acquisitionProgress = row.acquisitionProgress
												?: aurralAlbumAcquisitionProgress(
													album = album,
													requests = state.aurralAlbumRequests
												),
											ownershipStatus = row.ownershipStatus,
											contentDescription = null,
											onSelect = { viewModel.selectAlbum(album) },
											onClick = dropUnlessResumed {
												backStack.add(Screen.CollectionDetail(album.id, "artist"))
											}
										)
										if (selectedAlbum == album) {
											CollectionSheet(
												onDismissRequest = { viewModel.clearAlbumSelection() },
												collection = album,
												starred = selectedAlbumIsStarred,
												onShare = { shareId = album.id },
												onPlayNext = { player.playNext(album) },
												onAddToQueue = { player.addToQueue(album) },
												onSetStarred = { viewModel.starAlbum(!selectedAlbumIsStarred) },
												onAddAllToPlaylist = { playlistDialogShown = true },
												downloadStatus = albumDownloadStatus,
												onDownloadAll = {
													scope.launch {
														downloadManager.downloadCollection(album)
													}
												},
												onCancelDownloadAll = {
													scope.launch {
														downloadManager.cancelCollectionDownload(album)
													}
												},
												onDeleteDownloadAll = {
													scope.launch {
														downloadManager.deleteDownloadedCollection(album)
													}
												},
												rating = selectedAlbumRating,
												onSetRating = { viewModel.rateSelectedAlbum(it) }
											)
										}
								} else {
									AurralOwnershipAlbumItem(
										row = row,
										imageRequestHeaders = aurralRequestHeadersForUrl(
											baseUrl = preferenceManager.aurralBaseUrl,
											imageUrl = row.coverUrl,
											requestHeaders = preferenceManager.aurralRequestHeadersMap()
										),
										grayscale = row.ownershipStatus == AurralOwnershipStatus.Missing,
										onClick = {
											row.releaseGroup?.let { releaseGroup ->
												backStack.add(
													Screen.AurralMissingAlbum(
														artistId = state.artist.id,
														artistName = displayArtistName,
														artistMbid = state.aurralArtistMbid
															?: state.artist.musicBrainzId.orEmpty(),
														releaseGroupId = releaseGroup.id,
														title = row.title,
														year = row.year,
														primaryType = releaseGroup.primaryType,
														coverUrl = row.coverUrl,
														requestStatus = row.requestStatus
													)
												)
											}
										}
									)
									}
							}
							ArtCarousel(
								stringResource(Res.string.title_aurral_missing_albums),
								missingReleaseGroupRows
							) { row ->
								AurralOwnershipAlbumItem(
									row = row,
									imageRequestHeaders = aurralRequestHeadersForUrl(
										baseUrl = preferenceManager.aurralBaseUrl,
										imageUrl = row.coverUrl,
										requestHeaders = preferenceManager.aurralRequestHeadersMap()
									),
									grayscale = true,
									onClick = {
										row.releaseGroup?.let { releaseGroup ->
											backStack.add(
												Screen.AurralMissingAlbum(
													artistId = state.artist.id,
													artistName = displayArtistName,
													artistMbid = state.aurralArtistMbid
														?: state.artist.musicBrainzId.orEmpty(),
													releaseGroupId = releaseGroup.id,
													title = row.title,
													year = row.year,
													primaryType = releaseGroup.primaryType,
													coverUrl = row.coverUrl,
													requestStatus = row.requestStatus
												)
											)
										}
									}
								)
							}
							AurralPreviewTracks(
								title = stringResource(Res.string.title_aurral_preview_tracks),
								tracks = state.aurralPreviewTracks.toImmutableList(),
								modifier = Modifier.fillMaxWidth(),
								ownershipStatuses = previewTrackOwnershipStatuses
							)
							if (state.aurralLoading) {
								Text(
									text = stringResource(Res.string.info_aurral_loading_catalog),
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant,
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 16.dp)
								)
							}
							val similarRows = state.aurralSimilarArtists.ifEmpty {
								state.similarArtists.map { artist ->
									AurralSimilarArtistRow(
										artist = AurralSimilarArtist(
											id = artist.musicBrainzId ?: artist.id,
											name = artist.name,
											imageUrl = null
										),
										localArtistId = artist.id,
										localCoverArtId = artist.coverArtId,
										inLibrary = true,
										matchPercent = null
									)
								}
							}
							ArtCarousel(
								stringResource(Res.string.title_similar_artists),
								similarRows.toImmutableList()
							) { row ->
								AurralSimilarArtistItem(
									row = row,
									onClickLocalArtist = { localArtistId ->
										backStack.add(Screen.ArtistDetail(localArtistId))
									},
									onClickAurralArtist = {
										aurralExternalArtistRoute(row)?.let(backStack::add)
									}
								)
							}
						}
						Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
					}
				}
			}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = artistIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = artistIntegrationIndicators,
					relevantServices = MusicIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(
						start = 12.dp,
						top = contentPadding.calculateTopPadding() + 8.dp
					)
			)
		}
	}

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null; viewModel.clearSelection() },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = selectedAlbum?.songs.orEmpty().toPersistentList(),
			onDismissRequest = { playlistDialogShown = false }
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.AurralOwnershipAlbumItem(
	row: AurralArtistOwnershipAlbumRow,
	imageRequestHeaders: Map<String, String>,
	grayscale: Boolean = false,
	onClick: () -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val colorFilter = remember(grayscale) {
		if (grayscale) {
			ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
		} else {
			null
		}
	}

	Column(
		modifier = Modifier.fillMaxWidth()
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.maskClip(MaterialTheme.shapes.large)
		) {
			CoverArt(
				coverArtId = row.localAlbum?.coverArtId,
				imageUrl = row.coverUrl,
				imageCacheKey = row.releaseGroup?.id?.let { "aurral-release-group-$it" },
				imageRequestHeaders = imageRequestHeaders,
				contentDescription = row.title,
				fallbackKind = row.releaseGroup?.primaryType ?: "Album",
				modifier = Modifier.fillMaxWidth(),
				shape = RectangleShape,
				colorFilter = colorFilter,
				onClick = {
					platformContext.clickSound()
					onClick()
				}
			)
			row.acquisitionProgress?.let { progress ->
				AurralAcquisitionProgressBar(
					progress = progress,
					modifier = Modifier.align(Alignment.BottomCenter)
				)
			}
			AurralOwnershipStatusDot(
				status = row.ownershipStatus,
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(8.dp)
			)
		}
		Text(
			text = row.title,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Medium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
		)
		row.year?.let { year ->
			Text(
				text = year,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				modifier = Modifier.padding(start = 4.dp, end = 4.dp)
			)
		}
	}
}

@Composable
private fun AurralArtistProfileMetadata(
	genres: List<String>,
	externalLinks: List<AurralArtistExternalLink>,
	modifier: Modifier = Modifier
) {
	val uriHandler = LocalUriHandler.current
	val visibleGenres = remember(genres) {
		genres
			.mapNotNull { genre -> genre.trim().takeIf { it.isNotEmpty() } }
			.distinct()
			.take(8)
	}
	val visibleLinks = remember(externalLinks) {
		externalLinks
			.filter { link -> link.url.trim().startsWith("http", ignoreCase = true) }
			.distinctBy { link -> link.type.lowercase() to link.url }
			.take(4)
	}
	if (visibleGenres.isEmpty() && visibleLinks.isEmpty()) return

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(6.dp)
	) {
		if (visibleGenres.isNotEmpty()) {
			Text(
				text = visibleGenres.joinToString(" • "),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
		visibleLinks.forEach { link ->
			Text(
				text = link.type.trim().ifEmpty { link.url },
				style = MaterialTheme.typography.labelLarge,
				color = MaterialTheme.colorScheme.primary,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.clickable {
					uriHandler.openUri(link.url)
				}
			)
		}
	}
}

private fun aurralPreviewTrackAlbumOwnershipStatus(
	track: AurralPreviewTrack,
	rows: List<AurralArtistOwnershipAlbumRow>
): AurralOwnershipStatus? {
	val albumKey = track.album.normalizedAurralAlbumUiKey() ?: return null
	return rows.firstOrNull { row ->
		row.title.normalizedAurralAlbumUiKey() == albumKey ||
			row.localAlbum?.name.normalizedAurralAlbumUiKey() == albumKey ||
			row.releaseGroup?.title.normalizedAurralAlbumUiKey() == albumKey
	}?.ownershipStatus
}

private fun String?.normalizedAurralAlbumUiKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s*[\(\[].*?[\)\]]"""), " ")
		?.replace(Regex("""[^a-z0-9]+"""), " ")
		?.trim()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.AurralSimilarArtistItem(
	row: AurralSimilarArtistRow,
	onClickLocalArtist: (String) -> Unit,
	onClickAurralArtist: () -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val localArtistId = row.localArtistId
	val artistArtwork = rememberAurralFirstArtistArtworkUiState(
		artistId = localArtistId,
		artistMusicBrainzId = row.artist.id,
		artistName = row.artist.name,
		serverCoverArtId = row.localCoverArtId,
		externalArtistImageUrl = row.artist.imageUrl,
		externalArtistCacheKey = "aurral-similar-artist-${row.artist.id}"
	)
	val subtitle = row.matchPercent?.let {
		stringResource(Res.string.info_aurral_match_percent, it)
	} ?: if (row.inLibrary) null else stringResource(Res.string.info_aurral_external_artist)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.alpha(if (row.inLibrary) 1f else .62f)
	) {
		CoverArt(
			coverArtId = artistArtwork.coverArtId,
			imageUrl = artistArtwork.imageUrl,
			imageCacheKey = artistArtwork.imageCacheKey,
			imageRequestHeaders = artistArtwork.imageRequestHeaders,
			contentDescription = row.artist.name,
			fallbackKind = "Artist",
			modifier = Modifier
				.fillMaxWidth()
				.maskClip(MaterialTheme.shapes.large),
			shape = RectangleShape,
			onClick = if (localArtistId != null || !row.inLibrary) {
				{
					platformContext.clickSound()
					if (localArtistId != null) {
						onClickLocalArtist(localArtistId)
					} else {
						onClickAurralArtist()
					}
				}
			} else {
				null
			}
		)
		Text(
			text = row.artist.name,
			style = MaterialTheme.typography.bodyMedium,
			fontWeight = FontWeight.Medium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
		)
		subtitle?.let {
			Text(
				text = it,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(start = 4.dp, end = 4.dp)
			)
		}
	}
}
