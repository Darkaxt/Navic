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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import paige.navic.domain.models.DomainLidaClip

@OptIn(UnstableApi::class)
@Composable
actual fun PlatformLidaClipPlayer(
	clip: DomainLidaClip,
	requestHeaders: Map<String, String>,
	pictureInPictureEnabled: Boolean,
	modifier: Modifier
) {
	val context = LocalContext.current
	val activity = LocalActivity.current
	val player = remember(context, clip.streamUrl, requestHeaders) {
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
				setMediaItem(MediaItem.fromUri(clip.streamUrl))
				prepare()
				playWhenReady = true
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

	DisposableEffect(player) {
		onDispose {
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
