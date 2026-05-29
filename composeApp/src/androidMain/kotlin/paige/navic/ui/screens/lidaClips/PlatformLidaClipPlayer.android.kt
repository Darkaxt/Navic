package paige.navic.ui.screens.lidaClips

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toRect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import paige.navic.R
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.lidaClipStreamTimeoutMs
import paige.navic.domain.models.lidaClipPlaybackErrorMessage
import paige.navic.domain.models.settings.LidaClipsVideoFitMode
import paige.navic.domain.models.shouldCropLidaClipsVideoFrame
import paige.navic.domain.models.shouldHandleLidaClipAudioFocus
import paige.navic.util.core.Logger
import kotlin.math.roundToLong

@OptIn(UnstableApi::class)
@Composable
actual fun PlatformLidaClipPlayer(
	clip: DomainLidaClip,
	requestHeaders: Map<String, String>,
	pictureInPictureEnabled: Boolean,
	landscapeVideoModeEnabled: Boolean,
	videoFitMode: LidaClipsVideoFitMode,
	respectAudioFocus: Boolean,
	startPositionMs: Long,
	muted: Boolean,
	showControls: Boolean,
	useTextureView: Boolean,
	startProgress: Float?,
	playWhenReady: Boolean,
	seekProgress: Float?,
	seekKey: Int,
	retryKey: Int,
	onPlaybackReady: () -> Unit,
	onFirstFrameRendered: () -> Unit,
	onPlaybackError: (String) -> Unit,
	onPlaybackPositionChange: (Long) -> Unit,
	modifier: Modifier
) {
	val context = LocalContext.current
	val activity = LocalActivity.current
	val pendingSeekProgress = remember(clip.id) { mutableStateOf<Float?>(null) }
	val player = remember(context, clip.streamUrl, requestHeaders, retryKey, startPositionMs, startProgress) {
		val httpDataSourceFactory = DefaultHttpDataSource.Factory()
			.setDefaultRequestProperties(requestHeaders)
			.setConnectTimeoutMs(lidaClipStreamTimeoutMs())
			.setReadTimeoutMs(lidaClipStreamTimeoutMs())
		val dataSourceFactory = DefaultDataSource.Factory(
			context,
			httpDataSourceFactory
		)
		ExoPlayer.Builder(context)
			.setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
			.build()
			.apply {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(C.USAGE_MEDIA)
						.setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
						.build(),
					shouldHandleLidaClipAudioFocus(respectAudioFocus)
				)
				volume = if (muted) 0f else 1f
				setMediaItem(MediaItem.fromUri(clip.streamUrl), startPositionMs.coerceAtLeast(0L))
				prepare()
				this.playWhenReady = playWhenReady
		}
	}

	DisposableEffect(player, muted) {
		player.volume = if (muted) 0f else 1f
		onDispose {}
	}

	DisposableEffect(player, playWhenReady) {
		player.playWhenReady = playWhenReady
		onDispose {}
	}

	DisposableEffect(player, seekKey, seekProgress) {
		if (seekKey > 0 && seekProgress != null) {
			pendingSeekProgress.value = if (player.seekToProgress(seekProgress)) {
				null
			} else {
				seekProgress
			}
		}
		onDispose {}
	}

	DisposableEffect(
		player,
		startPositionMs,
		startProgress,
		onPlaybackReady,
		onFirstFrameRendered,
		onPlaybackError
	) {
		var appliedStartProgressSeek = false
		val listener = object : Player.Listener {
			override fun onPlaybackStateChanged(playbackState: Int) {
				if (playbackState == Player.STATE_READY) {
					if (
						!appliedStartProgressSeek &&
						startPositionMs <= 0L &&
						startProgress != null &&
						player.duration > 0L
					) {
						appliedStartProgressSeek = true
						player.seekTo(
							(player.duration * startProgress.coerceIn(0f, 1f))
								.roundToLong()
								.coerceIn(0L, player.duration)
						)
					}
					pendingSeekProgress.value?.let { progress ->
						if (player.seekToProgress(progress)) {
							pendingSeekProgress.value = null
						}
					}
					onPlaybackReady()
				} else if (playbackState == Player.STATE_ENDED) {
					onPlaybackPositionChange(player.currentPosition)
				}
			}

			override fun onIsPlayingChanged(isPlaying: Boolean) {
				if (!isPlaying) {
					onPlaybackPositionChange(player.currentPosition)
				}
			}

			override fun onRenderedFirstFrame() {
				onFirstFrameRendered()
			}

			override fun onPlayerError(error: PlaybackException) {
				Logger.e(
					"LidaClipPlayer",
					"LidaClip playback failed for id=${clip.id} title=${clip.title} " +
						"code=${error.errorCodeName} message=${error.message}",
					error
				)
				onPlaybackError(
					lidaClipPlaybackErrorMessage(
						errorCodeName = error.errorCodeName,
						message = error.message
					)
				)
			}
		}
		player.addListener(listener)
		onDispose {
			player.removeListener(listener)
		}
	}

	DisposableEffect(activity, pictureInPictureEnabled, player) {
		if (activity != null && pictureInPictureEnabled) {
			LidaClipPictureInPictureCoordinator.register(
				activity = activity,
				enabled = pictureInPictureEnabled
			)
		}

		onDispose {
			if (activity != null && pictureInPictureEnabled) {
				LidaClipPictureInPictureCoordinator.unregister(activity)
			}
		}
	}

	DisposableEffect(activity, landscapeVideoModeEnabled, player) {
		if (activity != null) {
			LidaClipVideoModeCoordinator.register(
				activity = activity,
				enabled = landscapeVideoModeEnabled
			)
		}

		onDispose {
			if (activity != null) {
				LidaClipVideoModeCoordinator.unregister(activity)
			}
		}
	}

	DisposableEffect(player) {
		onDispose {
			onPlaybackPositionChange(player.currentPosition)
			player.release()
		}
	}

	AndroidView(
		modifier = if (activity != null && pictureInPictureEnabled) {
			modifier.onGloballyPositioned { layoutCoordinates ->
				LidaClipPictureInPictureCoordinator.updateSourceRect(
					activity = activity,
					sourceRect = layoutCoordinates.boundsInWindow().toAndroidRectF().toRect()
				)
			}
		} else {
			modifier
		},
		factory = { context ->
			val playerView = if (useTextureView) {
				LayoutInflater.from(context)
					.inflate(R.layout.lida_clip_texture_player_view, null) as PlayerView
			} else {
				PlayerView(context)
			}
			playerView.apply {
				this.player = player
				useController = showControls
				resizeMode = lidaClipResizeMode(videoFitMode)
			}
		},
		update = {
			it.player = player
			it.useController = showControls
			it.resizeMode = lidaClipResizeMode(videoFitMode)
		}
	)
}

private fun lidaClipResizeMode(videoFitMode: LidaClipsVideoFitMode): Int =
	if (shouldCropLidaClipsVideoFrame(videoFitMode)) {
		AspectRatioFrameLayout.RESIZE_MODE_ZOOM
	} else {
		AspectRatioFrameLayout.RESIZE_MODE_FIT
	}

private fun Player.seekToProgress(progress: Float): Boolean {
	val durationMs = duration
	if (durationMs <= 0L) return false
	seekTo(
		(durationMs * progress.coerceIn(0f, 1f))
			.roundToLong()
			.coerceIn(0L, durationMs)
	)
	return true
}
