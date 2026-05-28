package paige.navic.ui.screens.song.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_download_failed
import navic.composeapp.generated.resources.info_downloaded
import navic.composeapp.generated.resources.info_in_playlist
import navic.composeapp.generated.resources.info_unknown_album
import navic.composeapp.generated.resources.info_unknown_year
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalNavStack
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.SongSwipeDirection
import paige.navic.domain.models.settings.SongSwipeAction
import paige.navic.domain.models.shouldShowPlaylistIndicator
import paige.navic.domain.models.songSwipeActionForDirection
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Check
import paige.navic.icons.outlined.DownloadOff
import paige.navic.icons.outlined.PlaylistPlay
import paige.navic.icons.outlined.Queue
import paige.navic.icons.outlined.QueuePlayNext
import paige.navic.ui.components.common.CoverArt
import paige.navic.ui.components.common.MarqueeText
import paige.navic.ui.components.sheets.SongSheet
import paige.navic.ui.components.sheets.lidaClipsMusicVideoAction
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import paige.navic.util.core.InlineExplicitIcon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongListScreenItem(
	modifier: Modifier,
	song: DomainSong,
	selected: Boolean,
	starred: Boolean,
	rating: Int,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onSetStarred: (starred: Boolean) -> Unit,
	onSetShareId: (String) -> Unit,
	onStartSongRadio: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onClick: () -> Unit,
	onSetRating: (Int) -> Unit,
	download: DownloadEntity?,
	onDownload: () -> Unit,
	onCancelDownload: () -> Unit,
	onDeleteDownload: () -> Unit,
	inPlaylist: Boolean = false
) {
	val backStack = LocalNavStack.current
	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val preferenceManager = koinInject<PreferenceManager>()
	val startToEndSwipeAction = songSwipeActionForDirection(
		enabled = preferenceManager.songSwipeActionsEnabled,
		startToEndAction = preferenceManager.songSwipeStartToEndAction,
		endToStartAction = preferenceManager.songSwipeEndToStartAction,
		direction = SongSwipeDirection.StartToEnd
	)
	val endToStartSwipeAction = songSwipeActionForDirection(
		enabled = preferenceManager.songSwipeActionsEnabled,
		startToEndAction = preferenceManager.songSwipeStartToEndAction,
		endToStartAction = preferenceManager.songSwipeEndToStartAction,
		direction = SongSwipeDirection.EndToStart
	)
	val showPlaylistIndicator = shouldShowPlaylistIndicator(
		userEnabled = preferenceManager.showPlaylistIndicator,
		isInPlaylist = inPlaylist,
		isPlaylistScreen = false
	)

	SwipeToDismissBox(
		modifier = modifier,
		state = dismissState,
		onDismiss = {
			when (
				if (it == SwipeToDismissBoxValue.StartToEnd) {
					startToEndSwipeAction
				} else {
					endToStartSwipeAction
				}
			) {
				SongSwipeAction.AddToQueue -> onAddToQueue()
				SongSwipeAction.PlayNext -> onPlayNext()
				SongSwipeAction.Disabled -> Unit
			}
			scope.launch { dismissState.reset() }
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(MaterialTheme.shapes.extraSmall)
					.background(MaterialTheme.colorScheme.primaryContainer)
					.padding(horizontal = 20.dp),
				contentAlignment = Alignment.CenterEnd
			) {
				when (dismissState.dismissDirection) {
					SwipeToDismissBoxValue.StartToEnd -> {
						when (startToEndSwipeAction) {
							SongSwipeAction.AddToQueue -> Icon(
								imageVector = Icons.Outlined.Queue,
								contentDescription = stringResource(startToEndSwipeAction.displayName),
								tint = MaterialTheme.colorScheme.onPrimaryContainer,
								modifier = Modifier.align(Alignment.CenterStart)
							)
							SongSwipeAction.PlayNext -> Icon(
								imageVector = Icons.Outlined.QueuePlayNext,
								contentDescription = stringResource(startToEndSwipeAction.displayName),
								tint = MaterialTheme.colorScheme.onPrimaryContainer,
								modifier = Modifier.align(Alignment.CenterStart)
							)
							SongSwipeAction.Disabled -> Unit
						}
					}
					SwipeToDismissBoxValue.EndToStart -> {
						when (endToStartSwipeAction) {
							SongSwipeAction.AddToQueue -> Icon(
								imageVector = Icons.Outlined.Queue,
								contentDescription = stringResource(endToStartSwipeAction.displayName),
								tint = MaterialTheme.colorScheme.onPrimaryContainer,
								modifier = Modifier.align(Alignment.CenterEnd)
							)
							SongSwipeAction.PlayNext -> Icon(
								imageVector = Icons.Outlined.QueuePlayNext,
								contentDescription = stringResource(endToStartSwipeAction.displayName),
								tint = MaterialTheme.colorScheme.onPrimaryContainer,
								modifier = Modifier.align(Alignment.CenterEnd)
							)
							SongSwipeAction.Disabled -> Unit
						}
					}
					else -> {}
				}
			}
		}
	) {
		Box {
			ListItem(
				onClick = onClick,
				onLongClick = onSelect,
				content = {
					MarqueeText(
						text = buildAnnotatedString {
							append(song.title)
							if (song.explicitStatus == DomainExplicitStatus.Explicit) {
								append(" ")
								appendInlineContent("InlineExplicitIcon")
							}
						},
						inlineContent = InlineExplicitIcon,
					)
				},
				supportingContent = {
					Text(
						buildString {
							append(song.albumTitle ?: stringResource(Res.string.info_unknown_album))
							append(" • ")
							append(song.artistName)
							append(" • ")
							append(song.year ?: stringResource(Res.string.info_unknown_year))
						},
						maxLines = 1
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
					Row(verticalAlignment = Alignment.CenterVertically) {
						if (showPlaylistIndicator) {
							Icon(
								Icons.Outlined.PlaylistPlay,
								contentDescription = stringResource(Res.string.info_in_playlist),
								modifier = Modifier.size(18.dp),
								tint = MaterialTheme.colorScheme.primary
							)
							Spacer(Modifier.width(8.dp))
						}
						if (download != null) {
							when (download.status) {
								DownloadStatus.DOWNLOADING -> {
									CircularProgressIndicator(
										progress = { download.progress },
										modifier = Modifier.size(16.dp),
										strokeWidth = 2.dp
									)
								}
								DownloadStatus.QUEUED -> {
									CircularProgressIndicator(
										modifier = Modifier.size(16.dp),
										strokeWidth = 2.dp
									)
								}
								DownloadStatus.DOWNLOADED -> {
									Icon(
										Icons.Outlined.Check,
										contentDescription = stringResource(Res.string.info_downloaded),
										modifier = Modifier.size(16.dp),
										tint = MaterialTheme.colorScheme.primary
									)
								}
								DownloadStatus.FAILED -> {
									Icon(
										Icons.Outlined.DownloadOff,
										contentDescription = stringResource(Res.string.info_download_failed),
										modifier = Modifier.size(16.dp),
										tint = MaterialTheme.colorScheme.error
									)
								}
								else -> {}
							}
						}
					}
				}
			)
			if (selected) {
				SongSheet(
					onDismissRequest = onDeselect,
					song = song,
					starred = starred,
					rating = rating,
					onSetStarred = onSetStarred,
					onShare = { onSetShareId(song.id) },
					onPlayMusicVideo = lidaClipsMusicVideoAction(song.id),
					onStartSongRadio = if (!song.id.startsWith("radio_")) onStartSongRadio else null,
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
					onAddToPlaylist = {
						playlistDialogShown = true
					},
					onSetRating = onSetRating,
					downloadStatus = download?.status ?: DownloadStatus.NOT_DOWNLOADED,
					onDownload = onDownload,
					onCancelDownload = onCancelDownload,
					onDeleteDownload = onDeleteDownload,
				)
			}
		}
	}

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = persistentListOf(song),
			onDismissRequest = { playlistDialogShown = false }
		)
	}
}
