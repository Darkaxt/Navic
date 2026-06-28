package paige.navic.ui.screens.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_play
import navic.composeapp.generated.resources.info_no_songs
import navic.composeapp.generated.resources.notice_aurral_album_requested
import navic.composeapp.generated.resources.notice_aurral_album_request_failed
import navic.composeapp.generated.resources.title_disc_number
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.LocalNavStack
import paige.navic.LocalBottomBarScrollManager
import paige.navic.LocalSnackbarState
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainPlaylistSongSortType
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.canDeletePlaylistFromDetail
import paige.navic.domain.models.sortedForPlaylistDetail
import paige.navic.domain.models.toPlaybackOrigin
import paige.navic.domain.models.settings.BottomBarVisibilityMode
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Album
import paige.navic.icons.outlined.Note
import paige.navic.ui.components.common.AurralOwnershipStatusDot
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.BackToTopScrollHandler
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.ErrorSnackbar
import paige.navic.ui.components.common.IntegrationLoadingIndicatorStrip
import paige.navic.ui.components.common.MusicIntegrationServices
import paige.navic.ui.components.common.integrationFailedIndicators
import paige.navic.ui.components.common.integrationLoadingIndicators
import paige.navic.ui.components.common.rememberResolvedArtworkColorScheme
import paige.navic.ui.components.dialogs.DeletionDialog
import paige.navic.ui.components.dialogs.DeletionEndpoint
import paige.navic.ui.components.layouts.PullToRefreshBox
import paige.navic.ui.components.layouts.RootBottomBar
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.collection.aurralAlbumHeaderProjection
import paige.navic.ui.screens.collection.components.CollectionDetailScreenFooterRow
import paige.navic.ui.screens.collection.components.CollectionDetailScreenHeadingRow
import paige.navic.ui.screens.collection.components.CollectionDetailScreenHeadingRowButtons
import paige.navic.ui.screens.collection.components.CollectionDetailScreenSongRow
import paige.navic.ui.screens.collection.components.CollectionDetailScreenSongRowDropdown
import paige.navic.ui.screens.collection.components.CollectionDetailScreenTopBar
import paige.navic.ui.screens.collection.components.collectionDetailScreenMoreByArtistRow
import paige.navic.ui.screens.collection.viewmodels.CollectionDetailViewModel
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.aurral.aurralAlbumSearchItemOrNull
import paige.navic.ui.screens.aurral.aurralSearchAlbumOwnershipStatus
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.ui.theme.NavicTheme
import paige.navic.util.ui.segmentedShapes
import paige.navic.util.ui.withoutTop
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionDetailScreen(
	route: Screen.CollectionDetail
) {
	val aurralAlbumRouteHint = route.aurralAlbumSearchItemOrNull()
	CollectionDetailScreen(
		collectionId = route.collectionId,
		tab = route.tab,
		aurralAlbumRouteHint = aurralAlbumRouteHint
	)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CollectionDetailScreen(
	collectionId: String,
	tab: String,
	aurralAlbumRouteHint: paige.navic.domain.repositories.AurralAlbumSearchItem?
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val snackbarState = LocalSnackbarState.current
	val albumRequestedMessage = stringResource(Res.string.notice_aurral_album_requested)
	val albumRequestFailedMessage = stringResource(Res.string.notice_aurral_album_request_failed)
	val scope = rememberCoroutineScope()

	val viewModel = koinViewModel<CollectionDetailViewModel>(
		key = collectionId,
		parameters = { parametersOf(collectionId) }
	)
	LaunchedEffect(aurralAlbumRouteHint) {
		viewModel.applyAurralAlbumRouteHint(aurralAlbumRouteHint)
	}

	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val collectionState by viewModel.collectionState.collectAsState()
	val collection = collectionState.data
	var playlistSongSorting by rememberSaveable(collectionId) {
		mutableStateOf(DomainPlaylistSongSortType.ManualOrder)
	}
	var playlistSongReversed by rememberSaveable(collectionId) { mutableStateOf(false) }
	val displayedCollection = remember(collection, playlistSongSorting, playlistSongReversed) {
		(collection as? DomainPlaylist)?.copy(
			songs = collection.songs.sortedForPlaylistDetail(
				sortType = playlistSongSorting,
				reversed = playlistSongReversed
			)
		) ?: collection
	}
	val selection by viewModel.selectedSong.collectAsState()
	val selectedAlbum by viewModel.selectedAlbum.collectAsState()
	val isOnline by viewModel.isOnline.collectAsState()
	val starred by viewModel.starred.collectAsState()

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var deletionId by remember { mutableStateOf<String?>(null) }

	val albumInfoState by viewModel.albumInfoState.collectAsState()
	val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by viewModel.selectedAlbumIsStarred.collectAsStateWithLifecycle()
	val selectedAlbumRating by viewModel.selectedAlbumRating.collectAsStateWithLifecycle()
	val otherAlbums by viewModel.otherAlbums.collectAsState()
	val aurralMoreByArtistRows by viewModel.aurralMoreByArtistRows.collectAsStateWithLifecycle()
	val aurralAlbumRequests by viewModel.aurralAlbumRequests.collectAsState()
	val aurralAlbumRecoveryMatch by viewModel.aurralAlbumRecoveryMatch.collectAsStateWithLifecycle()
	val aurralAlbumRecoveryRows by viewModel.aurralAlbumRecoveryRows.collectAsStateWithLifecycle()
	val aurralAlbumRecoveryLoading by viewModel.aurralAlbumRecoveryLoading.collectAsStateWithLifecycle()
	val aurralAlbumRecoveryCandidates by viewModel.aurralAlbumRecoveryCandidates.collectAsStateWithLifecycle()
	val aurralAlbumPageState by viewModel.aurralAlbumPageState.collectAsStateWithLifecycle()
	val allDownloads by viewModel.allDownloads.collectAsState()
	val playlistSongIds by viewModel.playlistSongIds.collectAsStateWithLifecycle()
	val downloadStatus by viewModel.collectionDownloadStatus()
		.collectAsState(DownloadStatus.NOT_DOWNLOADED)
	val collectionIntegrationIndicators = integrationLoadingIndicators(
		aurralLoading = aurralAlbumRecoveryLoading
	)
	val uriHandler = LocalUriHandler.current

	val rating by viewModel.rating.collectAsStateWithLifecycle()
	BackToTopScrollHandler(viewModel.listState)

	LaunchedEffect(viewModel, albumRequestFailedMessage) {
		viewModel.aurralAlbumRequestFailures.collect {
			snackbarState.currentSnackbarData?.dismiss()
			snackbarState.showSnackbar(albumRequestFailedMessage)
		}
	}

	val titleAlpha by remember {
		derivedStateOf {
			if (viewModel.listState.firstVisibleItemIndex >= 1) return@derivedStateOf 1f
			val height = viewModel.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat() ?: 0f
			if (height > 0f) {
				val threshold = height * 0.4f
				((viewModel.listState.firstVisibleItemScrollOffset.toFloat() - threshold) / (height - threshold)).coerceIn(0f, 1f)
			} else {
				0f
			}
		}
	}

	val headerProjection = remember(displayedCollection, aurralAlbumPageState) {
		(displayedCollection as? DomainAlbum)?.let { album ->
			aurralAlbumHeaderProjection(
				album = album,
				pageState = aurralAlbumPageState
			)
		}
	}
	val collectionColorScheme = rememberResolvedArtworkColorScheme(
		coverArtId = displayedCollection?.coverArtId,
		imageUrl = headerProjection?.coverUrl
	)

	NavicTheme(collectionColorScheme) {
	Scaffold(
		topBar = {
			CollectionDetailScreenTopBar(
				albumInfoState = albumInfoState,
				collection = displayedCollection,
				titleAlpha = titleAlpha,
				onSetShareId = { shareId = it },
				onDownloadAll = { viewModel.downloadAll() },
				onCancelDownloadAll = { viewModel.cancelDownloadAll() },
				onPlayNext = { displayedCollection?.let { player.playNext(it) } },
				onAddToQueue = { displayedCollection?.let { player.addToQueue(it) } },
				downloadStatus = downloadStatus,
				rating = if (collection !is DomainPlaylist) rating else null,
				onSetRating = if (collection !is DomainPlaylist) { { viewModel.rateAlbum(it) } } else null,
				starred = if (collection !is DomainPlaylist) starred else null,
				onSetStarred = if (collection !is DomainPlaylist) { { viewModel.starAlbum(it) } } else null,
				selectedPlaylistSongSorting = playlistSongSorting,
				onSetPlaylistSongSorting = { playlistSongSorting = it },
				selectedPlaylistSongReversed = playlistSongReversed,
				onSetPlaylistSongReversed = { playlistSongReversed = it },
				onDelete = (displayedCollection as? DomainPlaylist)
					?.takeIf { canDeletePlaylistFromDetail(it) }
					?.let { playlist -> { deletionId = playlist.id } },
				refreshCollection = { viewModel.refreshCollection(false) }
			)
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			if (preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { contentPadding ->
		Box(Modifier.fillMaxSize()) {
			PullToRefreshBox(
				modifier = Modifier
					.padding(top = contentPadding.calculateTopPadding())
					.background(MaterialTheme.colorScheme.surface),
				finished = collectionState !is UiState.Loading,
				onRefresh = { viewModel.refreshCollection(true) },
				key = collectionState
			) {
				LazyColumn(
					modifier = Modifier
						.background(MaterialTheme.colorScheme.surface)
						.fillMaxSize(),
					horizontalAlignment = Alignment.CenterHorizontally,
					contentPadding = contentPadding.withoutTop(),
					state = viewModel.listState
				) {
					val contentCollection = displayedCollection ?: return@LazyColumn

					item {
						CollectionDetailScreenHeadingRow(
							collection = contentCollection,
							tab = tab,
							titleAlpha = 1f - titleAlpha,
							displayTitle = headerProjection?.title,
							displaySubtitle = headerProjection?.artistName,
							displayDetail = headerProjection?.detail,
							coverImageUrl = headerProjection?.coverUrl
						)
					}

					item {
						val match = aurralAlbumRecoveryMatch
						val aurralAlbumActionStatus = if (contentCollection is DomainAlbum &&
							match != null
						) {
							aurralAlbumHeaderActionStatus(
								matchStatus = aurralSearchAlbumOwnershipStatus(match),
								recoveryRows = aurralAlbumRecoveryRows
							)
						} else {
							null
						}
						CollectionDetailScreenHeadingRowButtons(
							collection = contentCollection,
							aurralAlbumActionStatus = aurralAlbumActionStatus,
							onAcquireAurralAlbum = if (aurralAlbumActionStatus == AurralOwnershipStatus.Missing ||
								aurralAlbumActionStatus == AurralOwnershipStatus.Failed
							) {
								{
									scope.launch {
										snackbarState.currentSnackbarData?.dismiss()
										snackbarState.showSnackbar(albumRequestedMessage)
									}
									viewModel.requestAurralRecoveryAlbum()
								}
							} else {
								null
							}
						)
					}

					if (contentCollection is DomainAlbum) {
						val album = contentCollection.copy(
							songs = contentCollection.songs.sortedWith(compareBy(
								{ it.discNumber },
								{ it.trackNumber }
							))
						)
						val displayRows = aurralAlbumDisplayRows(
							album = album,
							recoveryRows = aurralAlbumRecoveryRows
						)
						val rowGroups = displayRows.groupBy(::aurralAlbumDisplayDiscKey)
						rowGroups.forEach { group ->
							val multipleDiscs = rowGroups.size > 1
							if (multipleDiscs) {
								item {
									Row(
										modifier = Modifier
											.fillMaxWidth()
											.padding(horizontal = 16.dp)
											.padding(top = if (group.key == 1) 0.dp else 12.dp, bottom = 4.dp)
											.heightIn(min = 32.dp),
										verticalAlignment = Alignment.CenterVertically
									) {
										Icon(
											imageVector = Icons.Outlined.Album,
											contentDescription = null,
											tint = MaterialTheme.colorScheme.onSurfaceVariant,
											modifier = Modifier.size(20.dp)
										)

										Spacer(modifier = Modifier.width(8.dp))

										Text(
											text = stringResource(
												Res.string.title_disc_number,
												group.key
											),
											style = MaterialTheme.typography.titleMediumEmphasized,
											fontWeight = FontWeight(600),
											color = MaterialTheme.colorScheme.onSurfaceVariant
										)
									}
								}
							}
							itemsIndexed(
								items = group.value,
								key = { _, row -> aurralAlbumDisplayRowKey(row) }
							) { index, row ->
								val song = row.localSong
								if (song == null) {
									CollectionDetailScreenAurralTrackRow(
										row = row,
										index = index,
										count = group.value.count(),
										isGroupedByDisc = multipleDiscs,
										onOpenPreview = row.previewUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { previewUrl ->
											{ uriHandler.openUri(previewUrl) }
										}
									)
									return@itemsIndexed
								}
								val download = allDownloads.find { it.songId == song.id }
								Box {
									CollectionDetailScreenSongRow(
										song = song,
										isCurrentTrack = playerState.currentSong?.id == song.id,
										isPlaying = !playerState.isPaused,
										index = index,
										count = group.value.count(),
										isPlaylist = false,
										onClick = {
											if (playerState.currentSong?.id != song.id) {
												player.setPlaybackOrigin(album.toPlaybackOrigin())
												player.playCollection(album, song)
											} else {
												player.togglePlay()
											}
										},
										onLongClick = {
											viewModel.selectSong(song)
										},
										onPlayNext = {
											player.playNextSingle(song)
										},
										onAddToQueue = {
											player.addToQueueSingle(song)
										},
										isStarred = if (selection == song) selectedSongIsStarred else song.starredAt != null,
										download = download,
										isOffline = !isOnline,
										inPlaylist = song.id in playlistSongIds,
										ownershipStatus = row.ownershipStatus
									)
									CollectionDetailScreenSongRowDropdown(
										expanded = selection == song,
										onDismissRequest = { viewModel.clearSelection() },
										onRemoveStar = { viewModel.unstarSelectedSong() },
										onAddStar = { viewModel.starSelectedSong() },
										onShare = { shareId = song.id },
										collection = contentCollection,
										song = song,
										onRemoveFromPlaylist = { viewModel.removeFromPlaylist() },
										starred = selectedSongIsStarred,
										downloadStatus = download?.status,
										onDownload = { viewModel.downloadSong(song) },
										onCancelDownload = { viewModel.cancelDownload(song.id) },
										onDeleteDownload = { viewModel.deleteDownload(song.id) },
										onPlayNext = { player.playNextSingle(song) },
										onAddToQueue = { player.addToQueueSingle(song) },
										rating = selectedSongRating,
										onSetRating = { viewModel.rateSelectedSong(it) }
									)
								}
							}
						}
					} else {
						itemsIndexed(
							items = contentCollection.songs,
							key = { _, song -> song.id }
						) { index, song ->
							val download = allDownloads.find { it.songId == song.id }
							Box {
								CollectionDetailScreenSongRow(
									song = song,
									isCurrentTrack = playerState.currentSong?.id == song.id,
									isPlaying = !playerState.isPaused,
									index = index,
									count = contentCollection.songs.count(),
									isPlaylist = true,
									onClick = {
										if (playerState.currentSong?.id != song.id) {
											player.setPlaybackOrigin(contentCollection.toPlaybackOrigin())
											player.playCollection(contentCollection, song)
										} else {
											player.togglePlay()
										}
									},
									onLongClick = {
										viewModel.selectSong(song)
									},
									onPlayNext = {
										player.playNextSingle(song)
									},
									onAddToQueue = {
										player.addToQueueSingle(song)
									},
									isStarred = if (selection == song) selectedSongIsStarred else song.starredAt != null,
									download = download,
									isOffline = !isOnline,
									inPlaylist = song.id in playlistSongIds
								)
								CollectionDetailScreenSongRowDropdown(
									expanded = selection == song,
									onDismissRequest = { viewModel.clearSelection() },
									onRemoveStar = { viewModel.unstarSelectedSong() },
									onAddStar = { viewModel.starSelectedSong() },
									onShare = { shareId = song.id },
									collection = contentCollection,
									song = song,
									onRemoveFromPlaylist = { viewModel.removeFromPlaylist() },
									starred = selectedSongIsStarred,
									downloadStatus = download?.status,
									onDownload = { viewModel.downloadSong(song) },
									onCancelDownload = { viewModel.cancelDownload(song.id) },
									onDeleteDownload = { viewModel.deleteDownload(song.id) },
									onPlayNext = { player.playNextSingle(song) },
									onAddToQueue = { player.addToQueueSingle(song) },
									rating = selectedSongRating,
									onSetRating = { viewModel.rateSelectedSong(it) }
								)
							}
						}
					}

					if (contentCollection.songs.isEmpty()) {
						item {
							ContentUnavailable(
								icon = Icons.Outlined.Note,
								label = stringResource(Res.string.info_no_songs)
							)
						}
					}

					item { CollectionDetailScreenFooterRow(contentCollection) }

					(contentCollection as? DomainAlbum)?.artistName?.let { artistName ->
						collectionDetailScreenMoreByArtistRow(
							artistName = artistName,
							artistAlbums = otherAlbums,
							aurralArtistAlbums = aurralMoreByArtistRows,
							aurralAlbumRequests = aurralAlbumRequests,
							selectedAlbum = selectedAlbum,
							onSetShareId = { shareId = it },
							onPlayNext = if (selectedAlbum != null) { { player.playNext(selectedAlbum as DomainSongCollection) } } else null,
							onAddToQueue = if (selectedAlbum != null) { { player.addToQueue(selectedAlbum as DomainSongCollection) } } else null,
							selectedAlbumRating = selectedAlbumRating,
							selectedAlbumStarred = selectedAlbumIsStarred,
							onSetAlbumRating = { viewModel.rateSelectedAlbum(it) },
							onSetAlbumStarred = { viewModel.starSelectedAlbum(it) },
							onSelect = { viewModel.selectAlbum(it) },
							onDeselect = { viewModel.clearSelection() },
							tab = tab
						)
					}
				}
			}
			IntegrationLoadingIndicatorStrip(
				indicators = collectionIntegrationIndicators,
				failedIndicators = integrationFailedIndicators(
					preferenceManager = preferenceManager,
					loadingIndicators = collectionIntegrationIndicators,
					relevantServices = MusicIntegrationServices
				),
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 12.dp, top = contentPadding.calculateTopPadding() + 8.dp)
			)
		}
	}

	ErrorSnackbar(
		error = (collectionState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null; viewModel.clearSelection() },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	DeletionDialog(
		endpoint = DeletionEndpoint.PLAYLIST,
		id = deletionId,
		onIdClear = { deletionId = null },
		onRefresh = {
			val effect = collectionDeleteNavigationEffect(
				endpoint = DeletionEndpoint.PLAYLIST,
				collectionId = collectionId,
				tab = tab
			)
			effect.routeToRemove?.let { backStack.remove(it) }
			if (effect.refreshCurrentCollection) {
				viewModel.refreshCollection(false)
			}
		}
	)
	}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CollectionDetailScreenAurralTrackRow(
	row: AurralAlbumDisplayRow,
	index: Int,
	count: Int,
	isGroupedByDisc: Boolean,
	onOpenPreview: (() -> Unit)?
) {
	val number = aurralAlbumDisplayTrackNumberLabel(
		row = row,
		index = index,
		isGroupedByDisc = isGroupedByDisc
	)
	SegmentedListItem(
		modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.5.dp),
		onClick = { onOpenPreview?.invoke() },
		shapes = segmentedShapes(index = index, count = count),
		colors = ListItemDefaults.segmentedColors(
			containerColor = MaterialTheme.colorScheme.surfaceContainer
		),
		contentPadding = PaddingValues(14.dp),
		leadingContent = {
			Column(
				modifier = Modifier.width(collectionDetailAlbumTrackLeadingWidth()),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				row.ownershipStatus?.let { status ->
					AurralOwnershipStatusDot(status = status, size = 11.dp)
				}
				Text(
					text = number,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1
				)
			}
		},
		content = {
			val subtitle = row.displaySubtitleText()
			Column {
				Text(
					text = row.title,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				if (subtitle != null) {
					Text(
						text = subtitle,
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
		},
		trailingContent = {
			if (onOpenPreview != null) {
				IconButton(
					onClick = onOpenPreview
				) {
					Icon(
						imageVector = Icons.Filled.Play,
						contentDescription = stringResource(Res.string.action_play)
					)
				}
			}
		}
	)
}

private fun AurralAlbumDisplayRow.displaySubtitleText(): String? =
	artistName?.takeIf { it.isNotBlank() }
		?: when (ownershipStatus) {
			AurralOwnershipStatus.Owned -> localSong?.let { "Owned locally" } ?: "Owned"
			AurralOwnershipStatus.Partial -> track?.status?.takeIf { it.isNotBlank() } ?: "Partial"
			AurralOwnershipStatus.Requested -> track?.status?.takeIf { it.isNotBlank() } ?: "Requested"
			AurralOwnershipStatus.Processing -> track?.status?.takeIf { it.isNotBlank() } ?: "Processing"
			AurralOwnershipStatus.Failed -> track?.status?.takeIf { it.isNotBlank() } ?: "Failed"
			AurralOwnershipStatus.Missing,
			null -> null
		}
