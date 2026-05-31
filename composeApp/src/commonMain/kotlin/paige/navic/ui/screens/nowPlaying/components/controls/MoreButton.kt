package paige.navic.ui.screens.nowPlaying.components.controls

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_more
import navic.composeapp.generated.resources.info_discover_queue_no_matches
import navic.composeapp.generated.resources.info_discover_queue_removed
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.LocalSnackbarState
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.DownloadManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.shouldShowNowPlayingAddToPlaylistAction
import paige.navic.domain.models.shouldShowNowPlayingDiscoverQueueAction
import paige.navic.domain.models.shouldShowNowPlayingDownloadAction
import paige.navic.domain.models.shouldShowNowPlayingStartRadioAction
import paige.navic.ui.navigation.Screen
import paige.navic.icons.Icons
import paige.navic.icons.outlined.MoreHoriz
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.sheets.SongSheet
import paige.navic.ui.components.sheets.lidaClipsMusicVideoAction
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import paige.navic.ui.screens.share.dialogs.ShareDialog
import paige.navic.ui.theme.NavicTheme
import kotlin.time.Duration

@Composable
fun NowPlayingMoreButton(
	songRating: Int,
	onSetSongRating: (Int) -> Unit
) {
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val snackbarState = LocalSnackbarState.current
	val scope = rememberCoroutineScope()
	val preferenceManager = koinInject<PreferenceManager>()
	val downloadManager = koinInject<DownloadManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val allDownloads by downloadManager.allDownloads.collectAsState(persistentListOf())
	val song = playerState.currentSong
	val download = allDownloads.find { it.songId == song?.id }
	val hasUpcomingSongs = playerState.currentIndex in playerState.queue.indices &&
		playerState.currentIndex < playerState.queue.lastIndex
	val showDownloadAction = shouldShowNowPlayingDownloadAction(
		userActionEnabled = preferenceManager.showNowPlayingDownloadAction,
		songId = song?.id
	)
	var expanded by remember { mutableStateOf(false) }
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	IconButton(
		onClick = {
			platformContext.clickSound()
			expanded = true
		},
		colors = IconButtonDefaults.filledTonalIconButtonColors(),
		modifier = Modifier.size(32.dp),
		enabled = song != null
	) {
		Icon(
			imageVector = Icons.Outlined.MoreHoriz,
			contentDescription = stringResource(Res.string.action_more)
		)
	}

	if (expanded && song != null) {
		NavicTheme {
			SongSheet(
				onDismissRequest = { expanded = false },
				song = song,
				collection = playerState.currentCollection,
				onViewAlbum = dropUnlessResumed {
					playerState.currentCollection?.let { collection ->
						backStack.remove(Screen.NowPlaying)
						backStack.add(Screen.CollectionDetail(collection.id, ""))
					}
				},
				onViewArtist = dropUnlessResumed {
					backStack.remove(Screen.NowPlaying)
					backStack.add(Screen.ArtistDetail(song.artistId))
				},
				onShare = {
					shareId = song.id
				},
				onPlayMusicVideo = lidaClipsMusicVideoAction(song.id),
				onStartSongRadio = if (shouldShowNowPlayingStartRadioAction(
						userActionEnabled = preferenceManager.showNowPlayingStartRadioAction,
						songId = song.id
					)
				) {
					{ player.startSongRadio(song) }
				} else null,
				onDiscoverQueue = if (shouldShowNowPlayingDiscoverQueueAction(
						userActionEnabled = preferenceManager.showNowPlayingDiscoverQueueAction,
						hasUpcomingSongs = hasUpcomingSongs
					)
				) {
					{
						player.applyDiscoverQueueFilter { removedCount ->
							scope.launch {
								snackbarState.showSnackbar(
									if (removedCount > 0) {
										getString(
											Res.string.info_discover_queue_removed,
											removedCount
										)
									} else {
										getString(Res.string.info_discover_queue_no_matches)
									}
								)
							}
						}
					}
				} else null,
				onAddToPlaylist = if (shouldShowNowPlayingAddToPlaylistAction(
						userActionEnabled = preferenceManager.showNowPlayingAddToPlaylistAction,
						songId = song.id
					)
				) {
					{
						playlistDialogShown = true
					}
				} else null,
				downloadStatus = if (showDownloadAction) {
					download?.status ?: DownloadStatus.NOT_DOWNLOADED
				} else null,
				onDownload = if (showDownloadAction) {
					{ downloadManager.downloadSong(song) }
				} else null,
				onCancelDownload = if (showDownloadAction) {
					{ downloadManager.cancelDownload(song.id) }
				} else null,
				onDeleteDownload = if (showDownloadAction) {
					{ downloadManager.deleteDownload(song.id) }
				} else null,
				onTrackInfo = dropUnlessResumed {
					backStack.add(Screen.MusicBrainzInfo)
				},
				rating = songRating,
				onSetRating = onSetSongRating,
				onOpenSystemEqualizer = if (
					platformContext.name.lowercase().startsWith("android") &&
					preferenceManager.showNowPlayingEqualizerAction
				) {
					{ player.openSystemEqualizer() }
				} else null,
				showSleepTimer = preferenceManager.showNowPlayingSleepTimerAction,
				showPlaybackSpeed = preferenceManager.showNowPlayingPlaybackSpeedAction
			)
		}
	}

	if (playlistDialogShown && song != null) {
		NavicTheme {
			PlaylistUpdateDialog(
				songs = persistentListOf(song),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}

	NavicTheme {
		ShareDialog(
			id = shareId,
			onIdClear = { shareId = null },
			expiry = shareExpiry,
			onExpiryChange = { shareExpiry = it }
		)
	}
}
