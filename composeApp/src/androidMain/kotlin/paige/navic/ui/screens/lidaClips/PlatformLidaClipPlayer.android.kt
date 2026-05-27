package paige.navic.ui.screens.lidaClips

import androidx.annotation.OptIn
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toRect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.lidaClipPlaybackErrorMessage

@OptIn(UnstableApi::class)
@Composable
actual fun PlatformLidaClipPlayer(
	clip: DomainLidaClip,
	requestHeaders: Map<String, String>,
	pictureInPictureEnabled: Boolean,
	landscapeVideoModeEnabled: Boolean,
	startPositionMs: Long,
	retryKey: Int,
	onPlaybackReady: () -> Unit,
	onPlaybackError: (String) -> Unit,
	onPlaybackPositionChange: (Long) -> Unit,
	modifier: Modifier
) {
	val context = LocalContext.current
	val activity = LocalActivity.current
	val player = remember(context, clip.streamUrl, requestHeaders, retryKey) {
		val httpDataSourceFactory = DefaultHttpDataSource.Factory()
			.setDefaultRequestProperties(requestHeaders)
		val dataSourceFactory = DefaultDataSource.Factory(
			context,
			httpDataSourceFactory
		)
		ExoPlayer.Builder(context)
			.setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
			.build()
			.apply {
				setMediaItem(MediaItem.fromUri(clip.streamUrl), startPositionMs.coerceAtLeast(0L))
				prepare()
				playWhenReady = true
		}
	}

	DisposableEffect(player, onPlaybackReady, onPlaybackError) {
		val listener = object : Player.Listener {
			override fun onPlaybackStateChanged(playbackState: Int) {
				if (playbackState == Player.STATE_READY) {
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

			override fun onPlayerError(error: PlaybackException) {
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
		if (activity != null) {
			LidaClipPictureInPictureCoordinator.register(
				activity = activity,
				enabled = pictureInPictureEnabled
			)
		}

		onDispose {
			if (activity != null) {
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
		modifier = if (activity != null) {
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
			PlayerView(context).apply {
				this.player = player
				useController = true
			}
		},
		update = {
			it.player = player
		}
	)
}
