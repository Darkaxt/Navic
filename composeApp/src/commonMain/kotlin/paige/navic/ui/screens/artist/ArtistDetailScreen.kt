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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_cancel
import navic.composeapp.generated.resources.action_see_all
import navic.composeapp.generated.resources.action_stop_monitoring_artist
import navic.composeapp.generated.resources.info_aurral_external_artist
import navic.composeapp.generated.resources.info_aurral_loading_catalog
import navic.composeapp.generated.resources.info_aurral_match_percent
import navic.composeapp.generated.resources.info_bulk_download_warning
import navic.composeapp.generated.resources.info_stop_monitoring_artist_confirmation
import navic.composeapp.generated.resources.option_sort_frequent
import navic.composeapp.generated.resources.title_albums
import navic.composeapp.generated.resources.title_aurral_recommendations
import navic.composeapp.generated.resources.title_bulk_download
import navic.composeapp.generated.resources.title_confirm
import navic.composeapp.generated.resources.title_similar_artists
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralArtistAlbumRow
import paige.navic.domain.models.AurralMissingAlbumRow
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.AurralSimilarArtistRow
import paige.navic.domain.models.aurralAlbumAcquisitionProgress
import paige.navic.domain.models.aurralArtistAlbumRows
import paige.navic.domain.repositories.aurralRequestHeadersForUrl
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.AurralAcquisitionProgressBar
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.ErrorBox
import paige.navic.ui.components.common.FormButton
import paige.navic.ui.components.common.SongRow
import paige.navic.ui.components.dialogs.BulkDownloadDialog
import paige.navic.ui.components.dialogs.FormDialog
import paige.navic.ui.components.layouts.ArtCarousel
import paige.navic.ui.components.layouts.ArtCarouselItem
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.components.sheets.CollectionSheet
import paige.navic.ui.core.UiState
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.aurral.AurralRecommendedAlbumItem
import paige.navic.ui.screens.artist.components.ArtistActionButtons
import paige.navic.ui.screens.artist.components.ArtistDetailScreenHeading
import paige.navic.ui.screens.artist.components.ArtistDetailScreenTopBar
import paige.navic.ui.screens.artist.viewmodels.ArtistDetailViewModel
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.icons.Icons
import paige.navic.icons.outlined.VisibilityOff
import kotlin.time.Duration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistDetailScreen(
	artistId: String
) {
	val preferenceManager = koinInject<PreferenceManager>()

	val viewModel = koinViewModel<ArtistDetailViewModel>(
		key = artistId,
		parameters = { parametersOf(artistId) }
	)
	val platformContext = LocalPlatformContext.current
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

	val gridState = rememberLazyGridState()

	var showDownloadDialog by remember { mutableStateOf(false) }

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	var stopMonitoringDialogShown by rememberSaveable { mutableStateOf(false) }

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
		AnimatedContent(
			targetState = artistState,
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
		) { artistState ->
			when (artistState) {
				is UiState.Error -> Box(Modifier.fillMaxSize().padding(contentPadding)) {
					ErrorBox(artistState)
				}

				is UiState.Loading -> Box(Modifier.fillMaxSize()) {
					ContainedLoadingIndicator(Modifier.size(80.dp).align(Alignment.Center))
				}

				is UiState.Success -> {
					val state = artistState.data
					val albumRows = remember(state.albums, state.aurralMissingAlbums) {
						aurralArtistAlbumRows(
							localAlbums = state.albums,
							missingAlbums = state.aurralMissingAlbums
						).toImmutableList()
					}
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
							artistName = state.artist.name,
							coverArtId = state.artist.coverArtId,
							subtitle = state.artist.biography,
							lastfm = state.artist.lastFmUrl,
							innerPadding = contentPadding,
							scrolled = scrolled
						)
						ArtistActionButtons(
							onPlay = { viewModel.playArtistAlbums(player) },
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
							onMonitorInAurral = if (preferenceManager.aurralEnabled &&
								!(state.aurralArtistMbid ?: state.artist.musicBrainzId).isNullOrBlank()
							) {
								{
									when (state.aurralMonitored) {
										true -> stopMonitoringDialogShown = true
										false -> viewModel.monitorArtistInAurral()
										null -> Unit
									}
								}
							} else {
								null
							},
							monitorInAurralEnabled = isAurralMonitorActionVerified(
								aurralMonitorActionState(state.aurralMonitored)
							),
							monitoringInAurral = monitoringInAurral,
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
										state = gridState,
										flingBehavior = rememberSnapFlingBehavior(lazyGridState = gridState),
										modifier = Modifier
											.fillMaxWidth()
											.height(artistTopSongsGridHeightDp(songs.size).dp)
									) {
										itemsIndexed(songs) { index, song ->
											val download = allDownloads.find { it.songId == song.id }
											SongRow(
												modifier = Modifier.weight(1f),
												song = song,
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
										backStack.add(
											Screen.AurralMissingAlbum(
												artistId = state.artist.id,
												artistName = state.artist.name,
												artistMbid = state.artist.musicBrainzId.orEmpty(),
												releaseGroupId = album.id,
												title = album.title,
												year = album.releaseDate?.trim()?.take(4),
												primaryType = album.primaryType
													?: album.secondaryTypes.firstOrNull(),
												coverUrl = album.coverUrl,
												requestStatus = album.status
											)
										)
									}
								)
							}
							ArtCarousel(
								stringResource(Res.string.title_albums),
								albumRows
							) { row ->
								when (row) {
									is AurralArtistAlbumRow.Local -> {
										val album = row.album
										val albumDownloadStatus by downloadManager
											.getCollectionDownloadStatus(album.songs.map { it.id })
											.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)
										ArtCarouselItem(
											coverArtId = album.coverArtId,
											title = album.name,
											subtitle = album.year?.toString(),
											acquisitionProgress = aurralAlbumAcquisitionProgress(
												album = album,
												requests = state.aurralAlbumRequests
											),
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
									}

									is AurralArtistAlbumRow.Missing -> {
										val missingAlbum = row.album
										val coverUrl = missingAlbum.coverUrl
										val imageRequestHeaders = aurralRequestHeadersForUrl(
											baseUrl = preferenceManager.aurralBaseUrl,
											imageUrl = coverUrl,
											requestHeaders = preferenceManager.aurralRequestHeadersMap()
										)
										AurralMissingAlbumItem(
											row = missingAlbum,
											coverUrl = coverUrl,
											imageRequestHeaders = imageRequestHeaders,
											grayscale = true,
											onClick = {
												backStack.add(
													Screen.AurralMissingAlbum(
														artistId = state.artist.id,
														artistName = state.artist.name,
														artistMbid = state.artist.musicBrainzId.orEmpty(),
														releaseGroupId = missingAlbum.releaseGroup.id,
														title = missingAlbum.title,
														year = missingAlbum.year,
														primaryType = missingAlbum.releaseGroup.primaryType,
														coverUrl = coverUrl,
														requestStatus = missingAlbum.requestStatus
													)
												)
											}
										)
									}
								}
							}
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
											imageUrl = artist.artistImageUrl
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
									imageRequestHeaders = aurralRequestHeadersForUrl(
										baseUrl = preferenceManager.aurralBaseUrl,
										imageUrl = row.artist.imageUrl,
										requestHeaders = preferenceManager.aurralRequestHeadersMap()
									),
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
	}

	if (stopMonitoringDialogShown) {
		val artistName = (artistState as? UiState.Success)?.data?.artist?.name.orEmpty()
		FormDialog(
			onDismissRequest = { stopMonitoringDialogShown = false },
			icon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
			title = { Text(stringResource(Res.string.title_confirm)) },
			content = {
				Text(
					text = stringResource(
						Res.string.info_stop_monitoring_artist_confirmation,
						artistName
					)
				)
			},
			buttons = {
				FormButton(
					onClick = {
						stopMonitoringDialogShown = false
						viewModel.setArtistMonitoringInAurral(monitored = false)
					}
				) {
					Text(stringResource(Res.string.action_stop_monitoring_artist))
				}
				FormButton(
					onClick = { stopMonitoringDialogShown = false }
				) {
					Text(stringResource(Res.string.action_cancel))
				}
			}
		)
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

fun truncateText(text: String, limit: Int): String {
	return if (text.length > limit) {
		text.take(limit) + "..."
	} else {
		text
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.AurralMissingAlbumItem(
	row: AurralMissingAlbumRow,
	coverUrl: String?,
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
				coverArtId = null,
				imageUrl = coverUrl,
				imageCacheKey = "aurral-release-group-${row.releaseGroup.id}",
				imageRequestHeaders = imageRequestHeaders,
				contentDescription = row.title,
				fallbackKind = row.releaseGroup.primaryType ?: "Album",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarouselItemScope.AurralSimilarArtistItem(
	row: AurralSimilarArtistRow,
	imageRequestHeaders: Map<String, String>,
	onClickLocalArtist: (String) -> Unit,
	onClickAurralArtist: () -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val localArtistId = row.localArtistId
	val subtitle = row.matchPercent?.let {
		stringResource(Res.string.info_aurral_match_percent, it)
	} ?: if (row.inLibrary) null else stringResource(Res.string.info_aurral_external_artist)

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.alpha(if (row.inLibrary) 1f else .62f)
	) {
		CoverArt(
			coverArtId = row.localCoverArtId,
			imageUrl = row.artist.imageUrl,
			imageCacheKey = "aurral-similar-artist-${row.artist.id}",
			imageRequestHeaders = imageRequestHeaders,
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
