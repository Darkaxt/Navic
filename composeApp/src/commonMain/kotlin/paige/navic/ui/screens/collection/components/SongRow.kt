package paige.navic.ui.screens.collection.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_download_failed
import navic.composeapp.generated.resources.info_downloaded
import navic.composeapp.generated.resources.info_in_playlist
import navic.composeapp.generated.resources.info_not_available_offline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.data.database.entities.DownloadEntity
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.SongSwipeDirection
import paige.navic.domain.models.settings.SongSwipeAction
import paige.navic.domain.models.shouldShowNowPlayingIndicator
import paige.navic.domain.models.shouldShowPlaylistIndicator
import paige.navic.domain.models.songSwipeActionForDirection
import paige.navic.icons.Icons
import paige.navic.icons.filled.Star
import paige.navic.icons.outlined.Check
import paige.navic.icons.outlined.DownloadOff
import paige.navic.icons.outlined.Offline
import paige.navic.icons.outlined.PlaylistPlay
import paige.navic.icons.outlined.Queue
import paige.navic.icons.outlined.QueuePlayNext
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.AurralOwnershipStatusDot
import paige.navic.ui.components.common.MarqueeText
import paige.navic.ui.components.common.PlaybackSongCoverArt
import paige.navic.ui.components.common.Waveform
import paige.navic.ui.screens.collection.collectionDetailAlbumTrackLeadingWidth
import paige.navic.util.core.InlineExplicitIcon
import paige.navic.util.ui.segmentedShapes
import paige.navic.util.core.toHoursMinutesSeconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionDetailScreenSongRow(
	song: DomainSong,
	isCurrentTrack: Boolean,
	isPlaying: Boolean,
	index: Int,
	count: Int,
	isPlaylist: Boolean = false,
	onClick: (() -> Unit),
	onLongClick: (() -> Unit),
	onPlayNext: (() -> Unit),
	onAddToQueue: (() -> Unit),
	isStarred: Boolean,
	download: DownloadEntity? = null,
	isOffline: Boolean = false,
	inPlaylist: Boolean = false,
	ownershipStatus: AurralOwnershipStatus? = null
) {
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

	val isDownloaded = download?.status == DownloadStatus.DOWNLOADED
	val showNowPlayingIndicator = shouldShowNowPlayingIndicator(
		userEnabled = preferenceManager.showNowPlayingIndicator,
		isCurrentSong = isCurrentTrack
	)
	val showPlaylistIndicator = shouldShowPlaylistIndicator(
		userEnabled = preferenceManager.showPlaylistIndicator,
		isInPlaylist = inPlaylist,
		isPlaylistScreen = isPlaylist
	)
	val canPlay = !isOffline || isDownloaded

	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()

	val itemShape = segmentedShapes(
		index = index,
		count = count,
		dismissDirection = dismissState.dismissDirection
	)

	SwipeToDismissBox(
		modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.5.dp),
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
					.clip(MaterialTheme.shapes.largeIncreased)
					.background(MaterialTheme.colorScheme.primaryContainer)
					.padding(horizontal = 20.dp)
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
		SegmentedListItem(
			contentPadding = PaddingValues(14.dp),
			onClick = onClick,
			onLongClick = onLongClick,
			shapes = itemShape,
			colors = ListItemDefaults.segmentedColors(
				containerColor = MaterialTheme.colorScheme.surfaceContainer
			),
			leadingContent = {
				if (isPlaylist) {
					PlaybackSongCoverArt(
						song = song,
						modifier = Modifier.size(48.dp),
						shape = MaterialTheme.shapes.small
					)
				} else {
					Column(
						modifier = Modifier.width(collectionDetailAlbumTrackLeadingWidth()),
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						if (ownershipStatus != null) {
							AurralOwnershipStatusDot(
								status = ownershipStatus,
								size = 10.dp
							)
							Spacer(Modifier.size(4.dp))
						}
						Text(
							text = "${index + 1}",
							style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
							fontWeight = FontWeight(400),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1,
							textAlign = TextAlign.Center,
							autoSize = TextAutoSize.StepBased(6.sp, 13.sp)
						)
					}
				}
			},
			content = {
				Column {
					MarqueeText(
						text = buildAnnotatedString {
							append(song.title)
							if (song.explicitStatus == DomainExplicitStatus.Explicit) {
								append(" ")
								appendInlineContent("InlineExplicitIcon")
							}
						},
						inlineContent = InlineExplicitIcon
					)
					Text(
						song.artistName,
						style = MaterialTheme.typography.bodySmall,
						maxLines = 1
					)
				}
			},
			trailingContent = {
				Row(verticalAlignment = Alignment.CenterVertically) {
					if(isStarred) {
						Icon(
							Icons.Filled.Star,
							null,
							modifier = Modifier.size(16.dp)
						)
						Spacer(Modifier.width(6.dp))
					}
					if (!canPlay) {
						Icon(
							Icons.Outlined.Offline,
							stringResource(Res.string.info_not_available_offline),
							modifier = Modifier.size(20.dp)
						)
						Spacer(Modifier.width(6.dp))
					}
					if (showPlaylistIndicator) {
						Icon(
							Icons.Outlined.PlaylistPlay,
							contentDescription = stringResource(Res.string.info_in_playlist),
							modifier = Modifier.size(18.dp),
							tint = MaterialTheme.colorScheme.primary
						)
						Spacer(Modifier.width(8.dp))
					}
					if (download != null && !isCurrentTrack) {
						when (download.status) {
							DownloadStatus.DOWNLOADING -> {
								CircularProgressIndicator(
									progress = { download.progress },
									modifier = Modifier.size(16.dp),
									strokeWidth = 2.dp
								)
								Spacer(Modifier.width(8.dp))
							}

							DownloadStatus.QUEUED -> {
								CircularProgressIndicator(
									modifier = Modifier.size(16.dp),
									strokeWidth = 2.dp
								)
								Spacer(Modifier.width(8.dp))
							}

							DownloadStatus.DOWNLOADED -> {
								Icon(
									Icons.Outlined.Check,
									contentDescription = stringResource(Res.string.info_downloaded),
									modifier = Modifier.size(16.dp),
									tint = MaterialTheme.colorScheme.primary
								)
								Spacer(Modifier.width(8.dp))
							}

							DownloadStatus.FAILED -> {
								Icon(
									Icons.Outlined.DownloadOff,
									contentDescription = stringResource(Res.string.info_download_failed),
									modifier = Modifier.size(16.dp),
									tint = MaterialTheme.colorScheme.error
								)
								Spacer(Modifier.width(8.dp))
							}

							else -> {}
						}
					}
					if (showNowPlayingIndicator) {
						Waveform(
							modifier = Modifier.padding(end = 12.dp),
							isPlaying = isPlaying
						)
					}
					song.duration.toHoursMinutesSeconds().let {
						Text(
							text = it,
							style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
							fontWeight = FontWeight(400),
							fontSize = 13.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1
						)
					}
				}
			}
		)
	}
}
