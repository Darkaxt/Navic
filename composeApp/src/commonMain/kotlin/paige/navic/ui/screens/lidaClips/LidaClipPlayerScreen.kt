package paige.navic.ui.screens.lidaClips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.info_no_lida_clip
import navic.composeapp.generated.resources.title_music_video
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.LidaClipPlaybackState
import paige.navic.domain.models.lidaClipStartPositionMs
import paige.navic.domain.models.nextRememberedLidaClipPosition
import paige.navic.domain.models.shouldPauseMusicForLidaClip
import paige.navic.domain.models.shouldResumeMusicAfterLidaClip
import paige.navic.icons.Icons
import paige.navic.icons.filled.Play
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.ContentUnavailable
import paige.navic.ui.components.common.ErrorBox
import paige.navic.ui.components.layouts.NestedTopBar
import paige.navic.ui.core.UiState

@Composable
fun LidaClipPlayerScreen(songId: String) {
	val preferenceManager = koinInject<PreferenceManager>()
	val viewModel = koinViewModel<LidaClipPlayerViewModel>(
		key = songId,
		parameters = { parametersOf(songId) }
	)
	val state by viewModel.clipState.collectAsStateWithLifecycle()

	Scaffold(
		topBar = {
			NestedTopBar({ Text(stringResource(Res.string.title_music_video)) })
		},
		contentWindowInsets = WindowInsets.statusBars
	) { innerPadding ->
		Box(
			modifier = Modifier
				.padding(innerPadding)
				.fillMaxSize()
		) {
			when (val currentState = state) {
				is UiState.Loading -> {
					CircularProgressIndicator(Modifier.align(Alignment.Center))
				}

				is UiState.Error -> {
					ErrorBox(
						error = currentState,
						onRetry = { viewModel.load() },
						modifier = Modifier.align(Alignment.Center)
					)
				}

				is UiState.Success -> {
					val clip = currentState.data
					if (clip == null) {
						ContentUnavailable(
							modifier = Modifier.fillMaxSize(),
							icon = Icons.Filled.Play,
							label = stringResource(Res.string.info_no_lida_clip)
						)
					} else {
						LidaClipPlayerContent(
							clip = clip,
							requestHeaders = preferenceManager.lidaClipsRequestHeadersMap(),
							pictureInPictureEnabled = preferenceManager.lidaClipsPictureInPicture,
							pauseMusicPlayback = preferenceManager.lidaClipsPauseMusicPlayback,
							rememberPlaybackPosition = preferenceManager.lidaClipsRememberPlaybackPosition,
							modifier = Modifier.fillMaxSize()
						)
					}
				}
			}
		}
	}
}

@Composable
private fun LidaClipPlayerContent(
	clip: DomainLidaClip,
	requestHeaders: Map<String, String>,
	pictureInPictureEnabled: Boolean,
	pauseMusicPlayback: Boolean,
	rememberPlaybackPosition: Boolean,
	modifier: Modifier = Modifier
) {
	val player = koinInject<MediaPlayerViewModel>()
	var playbackState by remember(clip.streamUrl) {
		mutableStateOf(LidaClipPlaybackState())
	}
	val durationMs = remember(clip.durationSeconds) {
		clip.durationSeconds?.toLong()?.times(1000L)
	}
	val preferenceManager = koinInject<PreferenceManager>()
	val startPositionMs = remember(clip.id, rememberPlaybackPosition) {
		lidaClipStartPositionMs(
			rememberPosition = rememberPlaybackPosition,
			clipId = clip.id,
			lastClipId = preferenceManager.lidaClipsLastClipId,
			lastPositionMs = preferenceManager.lidaClipsLastPositionMs,
			durationMs = durationMs
		)
	}

	DisposableEffect(clip.id, pauseMusicPlayback, player) {
		val playerState = player.uiState.value
		val pausedSongId = playerState.currentSong?.id?.takeIf {
			shouldPauseMusicForLidaClip(
				pauseMusicPlayback = pauseMusicPlayback,
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
					pauseMusicPlayback = pauseMusicPlayback,
					pausedSongId = pausedSongId,
					currentSongId = currentState.currentSong?.id,
					musicIsPaused = currentState.isPaused
				)
			) {
				player.resume()
			}
		}
	}

	Column(
		modifier = modifier.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		PlatformLidaClipPlayer(
			clip = clip,
			requestHeaders = requestHeaders,
			pictureInPictureEnabled = pictureInPictureEnabled,
			startPositionMs = startPositionMs,
			retryKey = playbackState.retryKey,
			onPlaybackReady = {
				playbackState = playbackState.onReady()
			},
			onPlaybackError = { message ->
				playbackState = playbackState.onError(message)
			},
			onPlaybackPositionChange = { positionMs ->
				nextRememberedLidaClipPosition(
					rememberPosition = rememberPlaybackPosition,
					clipId = clip.id,
					positionMs = positionMs,
					durationMs = durationMs
				)?.let { rememberedPosition ->
					preferenceManager.lidaClipsLastClipId = rememberedPosition.clipId
					preferenceManager.lidaClipsLastPositionMs = rememberedPosition.positionMs
				}
			},
			modifier = Modifier
				.fillMaxWidth()
				.aspectRatio(16f / 9f)
				.clip(MaterialTheme.shapes.medium)
		)
		playbackState.errorMessage?.let { errorMessage ->
			ErrorBox<Unit>(
				error = UiState.Error(Exception(errorMessage)),
				onRetry = {
					playbackState = playbackState.onRetry()
				},
				bottomPadding = 0.dp,
				padding = PaddingValues(0.dp)
			)
		}
		Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
			Text(clip.title, style = MaterialTheme.typography.titleMedium)
			val subtitle = listOfNotNull(clip.artist, clip.album, clip.qualityTier)
				.filter { it.isNotBlank() }
				.joinToString(" - ")
			if (subtitle.isNotEmpty()) {
				Text(
					subtitle,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}
