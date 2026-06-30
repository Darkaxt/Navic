package paige.navic.ui.screens.album.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.LocalNavStack
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.ui.navigation.Screen
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.aurralAlbumAcquisitionProgress
import paige.navic.domain.repositories.AurralAlbumSearchItem
import paige.navic.domain.manager.DownloadManager
import paige.navic.ui.components.common.GeneratedArtworkVariant
import paige.navic.ui.components.common.collectionArtworkRenderSpec
import paige.navic.ui.components.layouts.ArtGridItem
import paige.navic.ui.components.sheets.CollectionSheet
import paige.navic.ui.screens.aurral.aurralAlbumCollectionDetailRoute
import paige.navic.ui.screens.artist.rememberArtistCreditDestinationResolver
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.notice_download_started
import navic.composeapp.generated.resources.notice_deleted_download
import paige.navic.domain.manager.SnackBarManager

@Composable
fun AlbumListScreenItem(
	modifier: Modifier = Modifier,
	tab: String,
	album: DomainAlbum,
	aurralAlbumMatch: AurralAlbumSearchItem? = null,
	aurralAlbumRequests: List<AurralAlbumRequest>,
	ownershipStatus: AurralOwnershipStatus? = null,
	selected: Boolean,
	starred: Boolean,
	rating: Int,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onSetStarred: (starred: Boolean) -> Unit,
	onSetShareId: (String) -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onSetRating: (Int) -> Unit
) {
	val platformContext = LocalPlatformContext.current
	val backStack = LocalNavStack.current
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()
	val resolveArtistCreditDestination = rememberArtistCreditDestinationResolver()

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val displayedTitle = aurralAlbumMatch?.title?.cleanAlbumCardText() ?: album.name
	val displayedSubtitle = aurralAlbumCardSubtitle(aurralAlbumMatch, album)
	val displayedImageUrl = aurralAlbumMatch?.coverUrl?.cleanAlbumCardText()
	val displayedImageCacheKey = aurralAlbumMatch?.id?.cleanAlbumCardText()?.let { "aurral-album:$it" }
	val baseArtworkSpec = album.collectionArtworkRenderSpec(
		displayTitle = displayedTitle,
		externalImageUrl = displayedImageUrl,
		variant = GeneratedArtworkVariant.GridCard
	)
	val artworkSpec = baseArtworkSpec.copy(
		imageCacheKey = displayedImageCacheKey ?: baseArtworkSpec.imageCacheKey
	)

	Box(modifier) {
		ArtGridItem(
			onClick = dropUnlessResumed {
				platformContext.clickSound()
				scope.launch {
					backStack.add(aurralAlbumCollectionDetailRoute(album, aurralAlbumMatch, tab))
				}
			},
			onLongClick = onSelect,
			coverArtId = null,
			artworkSpec = artworkSpec,
			title = displayedTitle,
			subtitle = displayedSubtitle,
			acquisitionProgress = aurralAlbumAcquisitionProgress(
				album = album,
				requests = aurralAlbumRequests
			),
			ownershipStatus = ownershipStatus,
			id = album.id,
			tab = tab
		)
		if (selected) {
			val downloadManager = koinInject<DownloadManager>()
			val downloadStatus by downloadManager
				.getCollectionDownloadStatus(album.songs.map { it.id })
				.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)
			CollectionSheet(
				onDismissRequest = onDeselect,
				collection = album,
				onShare = { onSetShareId(album.id) },
				onPlayNext = onPlayNext,
				onAddToQueue = onAddToQueue,
				downloadStatus = downloadStatus,
				onDownloadAll = { 
					scope.launch {
						downloadManager.downloadCollection(album)
						snackBarManager.notify(Res.string.notice_download_started)
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
						snackBarManager.notify(Res.string.notice_deleted_download)
					}
				},
				starred = starred,
				onSetStarred = onSetStarred,
				onAddAllToPlaylist = { playlistDialogShown = true },
				onViewArtist = dropUnlessResumed {
					scope.launch {
						resolveArtistCreditDestination(
							album.artistId,
							album.artistName,
							true
						)?.let(backStack::add)
					}
				},
				rating = rating,
				onSetRating = onSetRating
			)
		}

		if (playlistDialogShown) {
			PlaylistUpdateDialog(
				songs = album.songs.toPersistentList(),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}
}

fun aurralAlbumCollectionDetailRoute(
	album: DomainAlbum,
	aurralAlbumMatch: AurralAlbumSearchItem?,
	tab: String
): Screen.CollectionDetail =
	if (aurralAlbumMatch != null) {
		aurralAlbumCollectionDetailRoute(aurralAlbumMatch, album.id, tab)
	} else {
		Screen.CollectionDetail(album.id, tab)
	}

private fun aurralAlbumCardSubtitle(
	aurralAlbumMatch: AurralAlbumSearchItem?,
	album: DomainAlbum
): String? {
	val artistName = aurralAlbumMatch?.artistName?.cleanAlbumCardText() ?: album.artistName
	val year = aurralAlbumMatch?.releaseDate?.cleanAlbumCardText()?.take(4)?.takeIf { value ->
		value.length == 4 && value.all(Char::isDigit)
	}
	return listOfNotNull(artistName, year).distinct().joinToString(" • ").takeIf { it.isNotBlank() }
}

private fun String.cleanAlbumCardText(): String? = trim().takeIf { it.isNotEmpty() }
