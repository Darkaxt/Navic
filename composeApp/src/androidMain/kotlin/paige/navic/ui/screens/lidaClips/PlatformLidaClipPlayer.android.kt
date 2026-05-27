package paige.navic.ui.screens.lidaClips

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
	modifier: Modifier
) {
	val context = LocalContext.current
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

	DisposableEffect(player) {
		onDispose {
			player.release()
		}
	}

	AndroidView(
		modifier = modifier,
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
