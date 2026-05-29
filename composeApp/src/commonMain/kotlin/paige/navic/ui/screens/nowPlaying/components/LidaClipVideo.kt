package paige.navic.ui.screens.nowPlaying.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.LidaClipPlaybackState
import paige.navic.domain.models.lidaClipBackgroundVideoFitMode
import paige.navic.domain.models.lidaClipDurationMs
import paige.navic.domain.models.lidaClipForegroundVideoFitMode
import paige.navic.domain.models.lidaClipProgressStartPositionMs
import paige.navic.domain.models.shouldBlurLidaClipBackgroundVideo
import paige.navic.domain.models.shouldPauseMusicForLidaClip
import paige.navic.domain.models.shouldResumeMusicAfterLidaClip
import paige.navic.domain.models.settings.LidaClipsBackgroundVideoMode
import paige.navic.domain.repositories.lidaClipsStreamRequestHeaders
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.ErrorBox
import paige.navic.ui.components.common.KeepScreenOn
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.lidaClips.PlatformLidaClipPlayer

@Composable
fun NowPlayingLidaClipBackground(
	clip: DomainLidaClip,
	backgroundVideoMode: LidaClipsBackgroundVideoMode,
	playerProgress: Float,
	modifier: Modifier = Modifier
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val startProgress = remember(clip.id) { playerProgress.coerceIn(0f, 1f) }
	val startPositionMs = remember(clip.id, startProgress, clip.durationSeconds) {
		lidaClipProgressStartPositionMs(startProgress, lidaClipDurationMs(clip.durationSeconds))
	}
	val requestHeaders = lidaClipsStreamRequestHeaders(
		baseUrl = preferenceManager.lidaClipsBaseUrl,
		streamUrl = clip.streamUrl,
		requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()
	)

	Box(modifier) {
		val videoModifier = if (shouldBlurLidaClipBackgroundVideo(backgroundVideoMode)) {
			Modifier.matchParentSize().blur(28.dp)
		} else {
			Modifier.matchParentSize()
		}
		PlatformLidaClipPlayer(
			clip = clip,
			requestHeaders = requestHeaders,
			pictureInPictureEnabled = false,
			landscapeVideoModeEnabled = false,
			videoFitMode = lidaClipBackgroundVideoFitMode(backgroundVideoMode),
			respectAudioFocus = false,
			startPositionMs = startPositionMs,
			muted = true,
			showControls = false,
			useTextureView = true,
			startProgress = startProgress,
			retryKey = 0,
			onPlaybackReady = {},
			onPlaybackError = {},
			onPlaybackPositionChange = {},
			modifier = videoModifier
		)
		if (shouldBlurLidaClipBackgroundVideo(backgroundVideoMode)) {
			Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
		}
	}
}

@Composable
fun NowPlayingLidaClipArtwork(
	clip: DomainLidaClip,
	playerProgress: Float,
	modifier: Modifier = Modifier
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val durationMs = remember(clip.durationSeconds) {
		lidaClipDurationMs(clip.durationSeconds)
	}
	val startProgress = remember(clip.id) { playerProgress.coerceIn(0f, 1f) }
	val startPositionMs = remember(clip.id, startProgress, durationMs) {
		lidaClipProgressStartPositionMs(startProgress, durationMs)
	}
	val requestHeaders = lidaClipsStreamRequestHeaders(
		baseUrl = preferenceManager.lidaClipsBaseUrl,
		streamUrl = clip.streamUrl,
		requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()
	)
	var playbackState by remember(clip.streamUrl) {
		mutableStateOf(LidaClipPlaybackState())
	}

	DisposableEffect(clip.id, preferenceManager.lidaClipsPauseMusicPlayback, player) {
		val playerState = player.uiState.value
		val pausedSongId = playerState.currentSong?.id?.takeIf {
			shouldPauseMusicForLidaClip(
				pauseMusicPlayback = preferenceManager.lidaClipsPauseMusicPlayback,
				hasCurrentSong = true,
				musicIsPaused = playerState.isPaused
			)
		}

		if (pausedSongId != null) {
			player.pause()
		}

		onDispose {
			val currentState = player.uiState.value
			if (
				shouldResumeMusicAfterLidaClip(
					pauseMusicPlayback = preferenceManager.lidaClipsPauseMusicPlayback,
					pausedSongId = pausedSongId,
					currentSongId = currentState.currentSong?.id,
					musicIsPaused = currentState.isPaused
				)
			) {
				player.resume()
			}
		}
	}

	if (preferenceManager.lidaClipsKeepScreenOn) {
		KeepScreenOn()
	}

	Box(
		modifier = modifier.clip(MaterialTheme.shapes.large),
		contentAlignment = Alignment.Center
	) {
		PlatformLidaClipPlayer(
			clip = clip,
			requestHeaders = requestHeaders,
			pictureInPictureEnabled = false,
			landscapeVideoModeEnabled = false,
			videoFitMode = lidaClipForegroundVideoFitMode(preferenceManager.lidaClipsVideoFitMode),
			respectAudioFocus = preferenceManager.respectAudioFocus,
			startPositionMs = startPositionMs,
			startProgress = startProgress,
			retryKey = playbackState.retryKey,
			onPlaybackReady = {
				playbackState = playbackState.onReady()
			},
			onPlaybackError = { message ->
				playbackState = playbackState.onError(message)
			},
			onPlaybackPositionChange = {},
			modifier = Modifier.matchParentSize()
		)
		playbackState.errorMessage?.let { errorMessage ->
			ErrorBox<Unit>(
				error = UiState.Error(Exception(errorMessage)),
				onRetry = {
					playbackState = playbackState.onRetry()
				}
			)
		}
	}
}
