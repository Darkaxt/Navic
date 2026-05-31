package paige.navic.ui.screens.artist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_pause_preview
import navic.composeapp.generated.resources.action_play_preview
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.LocalPlatformContext
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.aurralPreviewTrackOwnershipStatus
import paige.navic.icons.Icons
import paige.navic.icons.filled.Pause
import paige.navic.icons.filled.Play
import paige.navic.ui.components.common.AurralOwnershipStatusDot

@Composable
actual fun AurralPreviewTracks(
	title: String,
	tracks: ImmutableList<AurralPreviewTrack>,
	modifier: Modifier,
	ownershipStatuses: ImmutableMap<String, AurralOwnershipStatus>
) {
	val previewTracks = remember(tracks) {
		tracks.filter { !it.previewUrl.isNullOrBlank() }
	}
	if (previewTracks.isEmpty()) return

	val context = LocalContext.current
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	var playingTrackId by remember { mutableStateOf<String?>(null) }
	var loadingTrackId by remember { mutableStateOf<String?>(null) }
	var progress by remember { mutableFloatStateOf(0f) }
	val playLabel = stringResource(Res.string.action_play_preview)
	val pauseLabel = stringResource(Res.string.action_pause_preview)
	val previewPlayer = remember {
		ExoPlayer.Builder(context)
			.build()
			.apply {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
						.setUsage(C.USAGE_MEDIA)
						.build(),
					preferenceManager.respectAudioFocus
				)
			}
	}

	DisposableEffect(previewPlayer) {
		val listener = object : Player.Listener {
			override fun onPlaybackStateChanged(playbackState: Int) {
				if (playbackState != Player.STATE_BUFFERING) {
					loadingTrackId = null
				}
				if (playbackState == Player.STATE_ENDED) {
					playingTrackId = null
					progress = 0f
					previewPlayer.seekTo(0)
				}
			}

			override fun onPlayerError(error: PlaybackException) {
				loadingTrackId = null
				playingTrackId = null
				progress = 0f
			}
		}
		previewPlayer.addListener(listener)
		onDispose {
			previewPlayer.removeListener(listener)
			previewPlayer.release()
		}
	}

	LaunchedEffect(playingTrackId) {
		while (isActive && playingTrackId != null) {
			val trackDuration = previewTracks
				.firstOrNull { it.id == playingTrackId }
				?.durationMs
				?.takeIf { it > 0 }
			val duration = previewPlayer.duration.takeIf { it > 0 }
				?: trackDuration
				?: DEFAULT_AURRAL_PREVIEW_DURATION_MS
			progress = (previewPlayer.currentPosition.toFloat() / duration.toFloat())
				.coerceIn(0f, 1f)
			delay(200)
		}
	}

	Column(modifier = modifier.fillMaxWidth()) {
		Text(
			text = title,
			style = MaterialTheme.typography.titleMediumEmphasized,
			fontWeight = FontWeight(600),
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 16.dp)
				.heightIn(min = 32.dp)
				.padding(top = 8.dp)
		)
		LazyRow(
			contentPadding = PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 16.dp, bottom = 16.dp)
		) {
			items(previewTracks, key = { it.id }) { track ->
				val isPlayingTrack = playingTrackId == track.id
				AurralPreviewTrackCard(
					track = track,
					ownershipStatus = ownershipStatuses[track.id]
						?: aurralPreviewTrackOwnershipStatus(track),
					isPlaying = isPlayingTrack,
					isLoading = loadingTrackId == track.id,
					progress = if (isPlayingTrack) progress else 0f,
					playContentDescription = if (isPlayingTrack) pauseLabel else playLabel,
					onClick = {
						platformContext.clickSound()
						val previewUrl = track.previewUrl?.trim()
						if (previewUrl.isNullOrEmpty()) return@AurralPreviewTrackCard
						if (playingTrackId == track.id && previewPlayer.isPlaying) {
							previewPlayer.pause()
							previewPlayer.seekTo(0)
							playingTrackId = null
							loadingTrackId = null
							progress = 0f
							return@AurralPreviewTrackCard
						}
						playingTrackId = track.id
						loadingTrackId = track.id
						progress = 0f
						previewPlayer.setMediaItem(MediaItem.fromUri(previewUrl))
						previewPlayer.prepare()
						previewPlayer.playWhenReady = true
					}
				)
			}
		}
	}
}

@Composable
private fun AurralPreviewTrackCard(
	track: AurralPreviewTrack,
	ownershipStatus: AurralOwnershipStatus,
	isPlaying: Boolean,
	isLoading: Boolean,
	progress: Float,
	playContentDescription: String,
	onClick: () -> Unit
) {
	Surface(
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		tonalElevation = 1.dp,
		modifier = Modifier
			.width(260.dp)
			.heightIn(min = 84.dp)
	) {
		Box {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = 12.dp, top = 12.dp, end = 28.dp, bottom = 12.dp)
			) {
				IconButton(
					onClick = onClick,
					modifier = Modifier
						.size(44.dp)
						.clip(CircleShape)
						.background(MaterialTheme.colorScheme.primaryContainer)
				) {
					Icon(
						imageVector = if (isPlaying && !isLoading) Icons.Filled.Pause else Icons.Filled.Play,
						contentDescription = playContentDescription,
						tint = MaterialTheme.colorScheme.onPrimaryContainer
					)
				}
				Column(
					verticalArrangement = Arrangement.Center,
					modifier = Modifier.weight(1f)
				) {
					Text(
						text = track.title,
						style = MaterialTheme.typography.bodyMedium,
						fontWeight = FontWeight.Medium,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
					Text(
						text = track.album ?: "0:30",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis
					)
				}
			}
			AurralOwnershipStatusDot(
				status = ownershipStatus,
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(10.dp),
				size = 9.dp
			)
			if (isLoading) {
				LinearProgressIndicator(
					modifier = Modifier
						.align(Alignment.BottomStart)
						.fillMaxWidth()
				)
			} else if (isPlaying || progress > 0f) {
				LinearProgressIndicator(
					progress = { progress },
					modifier = Modifier
						.align(Alignment.BottomStart)
						.fillMaxWidth()
				)
			}
		}
	}
}

private const val DEFAULT_AURRAL_PREVIEW_DURATION_MS = 30_000L
