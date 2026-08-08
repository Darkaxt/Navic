package paige.navic.ui.screens.collection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_acquire_album
import navic.composeapp.generated.resources.action_delete_download
import navic.composeapp.generated.resources.action_play
import navic.composeapp.generated.resources.action_shuffle
import navic.composeapp.generated.resources.info_download_failed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.data.database.entities.DownloadStatus
import paige.navic.domain.models.DomainSongCollection
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.toPlaybackOrigin
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.icons.outlined.Check
import paige.navic.icons.outlined.Close
import paige.navic.icons.outlined.Delete
import paige.navic.icons.outlined.Download
import paige.navic.icons.outlined.DownloadOff
import paige.navic.icons.outlined.Shuffle
import paige.navic.domain.manager.DownloadManager
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.AurralActionIcon
import paige.navic.ui.components.common.AurralActionIconOverlay
import paige.navic.ui.theme.defaultFont

fun aurralAlbumActionIconOverlay(status: AurralOwnershipStatus): AurralActionIconOverlay =
	when (status) {
		AurralOwnershipStatus.Owned -> AurralActionIconOverlay.None
		AurralOwnershipStatus.Partial -> AurralActionIconOverlay.Progress
		AurralOwnershipStatus.Requested -> AurralActionIconOverlay.None
		AurralOwnershipStatus.Processing -> AurralActionIconOverlay.Progress
		AurralOwnershipStatus.Failed,
		AurralOwnershipStatus.Missing -> AurralActionIconOverlay.Crossed
	}

@Composable
fun CollectionDetailScreenHeadingRowButtons(
	collection: DomainSongCollection,
	aurralAlbumActionStatus: AurralOwnershipStatus? = null,
	onAcquireAurralAlbum: (() -> Unit)? = null
) {
	val platformContext = LocalPlatformContext.current
	val player = koinInject<MediaPlayerViewModel>()
	val downloadManager = koinInject<DownloadManager>()
	val scope = rememberCoroutineScope()

	val downloadStatus by downloadManager
		.getCollectionDownloadStatus(collection.songs.map { it.id })
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	Row(
		modifier = Modifier.padding(horizontal = 31.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(
			10.dp,
			alignment = Alignment.CenterHorizontally
		)
	) {
		val buttonShape = ContinuousCapsule
		val buttonHeight = 44.dp
		OutlinedButton(
			modifier = Modifier.size(width = 52.dp, height = buttonHeight),
			onClick = {
				platformContext.clickSound()
				player.setPlaybackOrigin(collection.toPlaybackOrigin())
				player.shufflePlay(collection)
			},
			shape = buttonShape,
			contentPadding = PaddingValues(0.dp),
			enabled = collection.songs.isNotEmpty()
		) {
			Icon(
				Icons.Outlined.Shuffle,
				contentDescription = stringResource(Res.string.action_shuffle),
				modifier = Modifier.size(24.dp)
			)
		}
		Button(
			modifier = Modifier.weight(1f).height(buttonHeight),
			onClick = {
				platformContext.clickSound()
				player.setPlaybackOrigin(collection.toPlaybackOrigin())
				player.playAll(collection.songs)
			},
			shape = buttonShape,
			enabled = collection.songs.isNotEmpty()
		) {
			Icon(
				Icons.Filled.Play,
				null,
				modifier = Modifier.size(25.dp).padding(end = 3.dp)
			)
			Text(
				stringResource(Res.string.action_play),
				maxLines = 1,
				autoSize = TextAutoSize.StepBased(
					minFontSize = 1.sp,
					maxFontSize = 15.sp
				),
				fontWeight = FontWeight.SemiBold,
				fontFamily = defaultFont(round = 100f)
			)
		}
		if (aurralAlbumActionStatus != null) {
			val acquireButtonIsActionable = (aurralAlbumActionStatus == AurralOwnershipStatus.Missing ||
				aurralAlbumActionStatus == AurralOwnershipStatus.Failed) &&
				onAcquireAurralAlbum != null
			OutlinedButton(
				modifier = Modifier.size(width = 52.dp, height = buttonHeight),
				onClick = {
					if (acquireButtonIsActionable) {
						platformContext.clickSound()
						onAcquireAurralAlbum()
					}
				},
				shape = buttonShape,
				contentPadding = PaddingValues(0.dp),
				enabled = true
			) {
				when (aurralAlbumActionStatus) {
					AurralOwnershipStatus.Partial,
					AurralOwnershipStatus.Requested,
					AurralOwnershipStatus.Processing -> {
						AurralActionIcon(
							overlay = aurralAlbumActionIconOverlay(aurralAlbumActionStatus),
							contentDescription = stringResource(Res.string.action_acquire_album),
							progressColor = MaterialTheme.colorScheme.primary
						)
					}
					AurralOwnershipStatus.Owned -> {
						Icon(
							imageVector = Icons.Outlined.Check,
							contentDescription = stringResource(Res.string.action_acquire_album),
							modifier = Modifier.size(24.dp),
							tint = MaterialTheme.colorScheme.onSurface
						)
					}
					AurralOwnershipStatus.Failed,
					AurralOwnershipStatus.Missing -> {
						AurralActionIcon(
							overlay = aurralAlbumActionIconOverlay(aurralAlbumActionStatus),
							contentDescription = stringResource(Res.string.action_acquire_album)
						)
					}
				}
			}
		}
		OutlinedButton(
			modifier = Modifier.size(width = 52.dp, height = buttonHeight),
			onClick = {
				platformContext.clickSound()
				scope.launch {
					when (downloadStatus) {
						DownloadStatus.NOT_DOWNLOADED, DownloadStatus.FAILED -> {
							downloadManager.downloadCollection(collection)
						}
						DownloadStatus.DOWNLOADING,
						DownloadStatus.QUEUED -> {
							downloadManager.cancelCollectionDownload(collection)
						}
						DownloadStatus.DOWNLOADED -> {
							downloadManager.deleteDownloadedCollection(collection)
						}
					}
				}
			},
			shape = buttonShape,
			enabled = collection.songs.isNotEmpty() ||
				(downloadStatus == DownloadStatus.DOWNLOADED ||
					downloadStatus == DownloadStatus.DOWNLOADING ||
					downloadStatus == DownloadStatus.QUEUED),
			contentPadding = PaddingValues(0.dp)
		) {
			when (downloadStatus) {
				DownloadStatus.DOWNLOADING,
				DownloadStatus.QUEUED -> {
					Box(contentAlignment = Alignment.Center) {
						CircularProgressIndicator(
							modifier = Modifier.size(24.dp),
							strokeWidth = 2.dp,
							color = MaterialTheme.colorScheme.primary
						)
						Icon(
							imageVector = Icons.Outlined.Close,
							contentDescription = "Cancel",
							modifier = Modifier.size(12.dp),
							tint = MaterialTheme.colorScheme.primary
						)
					}
				}

				DownloadStatus.DOWNLOADED -> {
					Icon(
						imageVector = Icons.Outlined.Delete,
						contentDescription = stringResource(Res.string.action_delete_download),
						modifier = Modifier.size(24.dp),
						tint = MaterialTheme.colorScheme.primary
					)
				}

				DownloadStatus.FAILED -> {
					Icon(
						imageVector = Icons.Outlined.DownloadOff,
						contentDescription = stringResource(Res.string.info_download_failed),
						modifier = Modifier.size(24.dp),
						tint = MaterialTheme.colorScheme.error
					)
				}

				else -> {
					Icon(
						imageVector = Icons.Outlined.Download,
						contentDescription = null,
						modifier = Modifier.size(24.dp)
					)
				}
			}
		}
	}
}
