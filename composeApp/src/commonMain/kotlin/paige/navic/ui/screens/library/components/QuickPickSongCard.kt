package paige.navic.ui.screens.library.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.persistentListOf
import paige.navic.LocalNavStack
import paige.navic.LocalPlatformContext
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.hasStableNavidromeSongId
import paige.navic.ui.components.common.rememberPlaybackArtworkUiState
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.sheets.SongSheet
import paige.navic.ui.components.sheets.lidaClipsMusicVideoAction
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog

@Composable
fun QuickPickSongCard(
	modifier: Modifier = Modifier,
	song: DomainSong,
	selected: Boolean,
	starred: Boolean,
	rating: Int,
	download: DownloadEntity?,
	ownershipStatus: AurralOwnershipStatus? = null,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onSetStarred: (Boolean) -> Unit,
	onSetShareId: (String) -> Unit,
	onStartSongRadio: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onClick: () -> Unit,
	onSetRating: (Int) -> Unit,
	onDownload: () -> Unit,
	onCancelDownload: () -> Unit,
	onDeleteDownload: () -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val backStack = LocalNavStack.current
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val playbackArtwork = rememberPlaybackArtworkUiState(
		song = song,
		musicBrainzArtworkUrl = null,
		musicBrainzArtworkCacheKey = null,
		serverCoverLoadFailed = false
	)

	Box(modifier) {
		ArtGridItem(
			onClick = dropUnlessResumed {
				platformContext.clickSound()
				onClick()
			},
			onLongClick = onSelect,
			coverArtId = playbackArtwork.coverArtId,
			imageUrl = playbackArtwork.imageUrl,
			imageCacheKey = playbackArtwork.imageCacheKey,
			imageRequestHeaders = playbackArtwork.imageRequestHeaders,
			title = song.title,
			subtitle = song.artistName,
			ownershipStatus = ownershipStatus,
			id = song.id,
			tab = "quick-picks"
		)
		if (selected) {
			SongSheet(
				onDismissRequest = onDeselect,
				song = song,
				starred = starred,
				rating = rating,
				onSetStarred = onSetStarred,
				onShare = { onSetShareId(song.id) },
				onPlayMusicVideo = lidaClipsMusicVideoAction(song),
				onStartSongRadio = if (hasStableNavidromeSongId(song.id)) onStartSongRadio else null,
				onPlayNext = onPlayNext,
				onAddToQueue = onAddToQueue,
				onTrackInfo = dropUnlessResumed {
					backStack.add(Screen.SongDetail(song.id))
				},
				onViewAlbum = song.albumId?.let { albumId ->
					dropUnlessResumed {
						backStack.add(
							Screen.CollectionDetail(
								collectionId = albumId,
								tab = "library"
							)
						)
					}
				},
				onAddToPlaylist = { playlistDialogShown = true },
				onSetRating = onSetRating,
				downloadStatus = download?.status ?: DownloadStatus.NOT_DOWNLOADED,
				onDownload = onDownload,
				onCancelDownload = onCancelDownload,
				onDeleteDownload = onDeleteDownload
			)
		}
	}

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = persistentListOf(song),
			onDismissRequest = { playlistDialogShown = false }
		)
	}
}
