package paige.navic.ui.screens.collection.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.title_more_by_artist
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.ui.navigation.Screen
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.aurralAlbumAcquisitionProgress
import paige.navic.domain.models.collectionDownloadStatus
import paige.navic.domain.models.sortedByAlbumYearDescending
import paige.navic.domain.manager.DownloadManager
import paige.navic.ui.components.layouts.ArtCarousel
import paige.navic.ui.components.layouts.ArtCarouselItem
import paige.navic.ui.components.sheets.CollectionSheet
import paige.navic.ui.screens.album.albumDownloadOwnershipStatus
import paige.navic.ui.screens.artist.rememberArtistCreditDestinationResolver
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog

fun LazyListScope.collectionDetailScreenMoreByArtistRow(
	artistName: String,
	artistAlbums: List<DomainAlbum>,
	aurralAlbumRequests: List<AurralAlbumRequest>,
	selectedAlbum: DomainAlbum?,
	onSetShareId: (String) -> Unit,
	onPlayNext: (() -> Unit)?,
	onAddToQueue: (() -> Unit)?,
	selectedAlbumStarred: Boolean,
	selectedAlbumRating: Int,
	onSetAlbumRating: (Int) -> Unit,
	onSetAlbumStarred: (Boolean) -> Unit,
	onSelect: (DomainAlbum) -> Unit,
	onDeselect: () -> Unit,
	tab: String,
) {
	item {
		val backStack = LocalNavStack.current
		val scope = rememberCoroutineScope()
		val resolveArtistCreditDestination = rememberArtistCreditDestinationResolver()

		var albumToAddToPlaylist by remember { mutableStateOf<DomainAlbum?>(null) }

		val downloadManager = koinInject<DownloadManager>()
		val allDownloads by downloadManager.allDownloads.collectAsState(initial = emptyList())

		ArtCarousel(
			title = stringResource(Res.string.title_more_by_artist, artistName),
			items = artistAlbums.sortedByAlbumYearDescending().toImmutableList()
		) { album ->
			val songIds = album.songs.map { it.id }
			val downloadStatus = collectionDownloadStatus(songIds, allDownloads)
			ArtCarouselItem(
				coverArtId = album.coverArtId,
				title = album.name,
				contentDescription = album.name,
				acquisitionProgress = aurralAlbumAcquisitionProgress(
					album = album,
					requests = aurralAlbumRequests
				),
				ownershipStatus = albumDownloadOwnershipStatus(songIds, allDownloads),
				onSelect = { onSelect(album) },
				onClick = dropUnlessResumed {
					backStack.add(Screen.CollectionDetail(album.id, tab))
				}
			)
			if (selectedAlbum != null && selectedAlbum == album) {
				CollectionSheet(
					onDismissRequest = onDeselect,
					collection = album,
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
					downloadStatus = downloadStatus,
					onShare = { onSetShareId(album.id) },
					onPlayNext = onPlayNext,
					onAddToQueue = onAddToQueue,
					onAddAllToPlaylist = { albumToAddToPlaylist = album },
					onViewArtist = dropUnlessResumed {
						scope.launch {
							resolveArtistCreditDestination(
								album.artistId,
								album.artistName,
								true
							)?.let(backStack::add)
						}
					},
					rating = selectedAlbumRating,
					onSetRating = onSetAlbumRating,
					starred = selectedAlbumStarred,
					onSetStarred =  { onSetAlbumStarred(!selectedAlbumStarred) }
				)
			}
		}
		if (albumToAddToPlaylist != null) {
			albumToAddToPlaylist?.let {
				PlaylistUpdateDialog(
					songs = it.songs.toPersistentList(),
					onDismissRequest = { albumToAddToPlaylist = null }
				)
			}
		}
	}
}
