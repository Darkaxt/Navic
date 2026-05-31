package paige.navic.ui.screens.collection.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.ui.navigation.Screen
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainPlaylist
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.QueueDuplicateAction
import paige.navic.domain.models.duplicateQueueActionFor
import paige.navic.domain.models.hasStableNavidromeSongId
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.dialogs.QueueDuplicateDialog
import paige.navic.ui.components.sheets.SongSheet
import paige.navic.ui.components.sheets.lidaClipsMusicVideoAction
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog

@Composable
fun CollectionDetailScreenSongRowDropdown(
	expanded: Boolean,
	onDismissRequest: () -> Unit,
	onRemoveStar: () -> Unit,
	onAddStar: () -> Unit,
	onShare: () -> Unit,
	collection: DomainSongCollection,
	song: DomainSong,
	onRemoveFromPlaylist: () -> Unit,
	starred: Boolean,
	downloadStatus: DownloadStatus?,
	onDownload: () -> Unit,
	onCancelDownload: () -> Unit,
	onDeleteDownload: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	rating: Int,
	onSetRating: (Int) -> Unit
) {
	val player = koinInject<MediaPlayerViewModel>()
	val backStack = LocalNavStack.current
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	var queueDuplicateAction by rememberSaveable { mutableStateOf<QueueDuplicateAction?>(null) }

	fun queueSongOrConfirmDuplicate(
		action: QueueDuplicateAction,
		onQueue: () -> Unit
	) {
		queueDuplicateAction = duplicateQueueActionFor(
			queueSongIds = player.uiState.value.queue.map { it.id },
			songId = song.id,
			action = action
		)
		if (queueDuplicateAction == null) {
			onQueue()
		}
	}

	if (expanded) {
		SongSheet(
			onDismissRequest = onDismissRequest,
			song = song,
			collection = collection,
			starred = starred,
			onSetStarred = { starred ->
				if (starred) onAddStar() else onRemoveStar()
			},
			onShare = onShare,
			onPlayMusicVideo = lidaClipsMusicVideoAction(song),
			onStartSongRadio = if (hasStableNavidromeSongId(song.id)) {
				{ player.startSongRadio(song) }
			} else null,
			onPlayNext = {
				queueSongOrConfirmDuplicate(QueueDuplicateAction.PlayNext) {
					onPlayNext()
				}
			},
			onAddToQueue = {
				queueSongOrConfirmDuplicate(QueueDuplicateAction.AddToQueue) {
					onAddToQueue()
				}
			},
			onTrackInfo = dropUnlessResumed {
				backStack.add(Screen.SongDetail(song.id))
			},
			onViewAlbum = if (collection !is DomainAlbum && song.albumId != null) {
				dropUnlessResumed {
					backStack.add(
						Screen.CollectionDetail(
							collectionId = song.albumId,
							tab = "library"
						)
					)
				}
			} else null,
			onViewArtist = dropUnlessResumed {
				backStack.add(Screen.ArtistDetail(song.artistId))
			},
			onAddToPlaylist = {
				playlistDialogShown = true
			},
			onRemoveFromPlaylist = onRemoveFromPlaylist,
			downloadStatus = downloadStatus,
			onDownload = onDownload,
			onCancelDownload = onCancelDownload,
			onDeleteDownload = onDeleteDownload,
			rating = rating,
			onSetRating = onSetRating
		)
	}

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = persistentListOf(song),
			playlistToExclude = if (collection is DomainPlaylist)
				collection.id
			else null,
			onDismissRequest = { playlistDialogShown = false }
		)
	}

	if (queueDuplicateAction != null) {
		QueueDuplicateDialog(
			onDismissRequest = {
				queueDuplicateAction = null
				onDismissRequest()
			},
			onConfirm = {
				when (queueDuplicateAction) {
					QueueDuplicateAction.PlayNext -> onPlayNext()
					QueueDuplicateAction.AddToQueue,
					null -> onAddToQueue()
				}
			}
		)
	}
}
