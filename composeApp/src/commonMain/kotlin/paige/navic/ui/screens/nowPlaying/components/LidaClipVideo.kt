package paige.navic.ui.screens.nowPlaying.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.action_play_music_video
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import paige.navic.domain.manager.LidaClipCacheManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainLidaClip
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.isCachedLidaClipStreamUrl
import paige.navic.domain.models.LidaClipPlaybackState
import paige.navic.domain.models.NowPlayingVideoArtworkCrossfadeDurationMs
import paige.navic.domain.models.NowPlayingVideoArtworkCrossfadeInitialScale
import paige.navic.domain.models.lidaClipBackgroundVideoFitMode
import paige.navic.domain.models.lidaClipDurationMs
import paige.navic.domain.models.lidaClipForegroundVideoFitMode
import paige.navic.domain.models.lidaClipProgressStartPositionMs
import paige.navic.domain.models.shouldBlurLidaClipBackgroundVideo
import paige.navic.domain.models.shouldMuteMusicForNowPlayingPromotedLidaClip
import paige.navic.domain.models.shouldMuteNowPlayingBackgroundLidaClipVideo
import paige.navic.domain.models.shouldMuteNowPlayingPromotedLidaClipVideo
import paige.navic.domain.models.shouldPlayNowPlayingLidaClipVideo
import paige.navic.domain.models.shouldRecoverLidaClipFromPlaybackError
import paige.navic.domain.models.shouldShowLidaClipsMusicVideoAction
import paige.navic.domain.models.shouldShowLidaClipExtraScreenBackground
import paige.navic.domain.models.shouldShowNowPlayingLidaClipControls
import paige.navic.domain.models.settings.LidaClipsBackgroundVideoMode
import paige.navic.domain.repositories.LidaClipsRepository
import paige.navic.domain.repositories.lidaClipsStreamRequestHeaders
import paige.navic.icons.Icons
import paige.navic.icons.outlined.Movie
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.ErrorBox
import paige.navic.ui.components.common.KeepScreenOn
import paige.navic.ui.core.UiState
import paige.navic.ui.screens.lidaClips.PlatformLidaClipPlayer

@Composable
fun ExtraScreenLidaClipBackground(
	song: DomainSong?,
	enabled: Boolean,
	modifier: Modifier = Modifier
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val lidaClipsRepository = koinInject<LidaClipsRepository>()
	val lidaClipCacheManager = koinInject<LidaClipCacheManager>()
	val playerState by player.uiState.collectAsState()
	var cachedClip by remember(song?.id) { mutableStateOf<DomainLidaClip?>(null) }
	val shouldLoadClip = shouldShowLidaClipsMusicVideoAction(
		lidaClipsEnabled = preferenceManager.lidaClipsEnabled,
		lidaClipsBaseUrl = preferenceManager.lidaClipsBaseUrl,
		userActionEnabled = enabled,
		songId = song?.id
	)

	LaunchedEffect(
		song?.id,
		shouldLoadClip,
		preferenceManager.lidaClipsBaseUrl,
		preferenceManager.lidaClipsApiKey,
		preferenceManager.lidaClipsVideoCacheSizeMb
	) {
		cachedClip = null
		val currentSong = song ?: return@LaunchedEffect
		if (!shouldLoadClip) {
			return@LaunchedEffect
		}
		lidaClipsRepository.findClipForSong(currentSong)
			.onSuccess { clip ->
				cachedClip = clip
					?.let { withContext(Dispatchers.IO) { lidaClipCacheManager.cachedClipFor(currentSong.id, it) } }
					?.takeIf { isCachedLidaClipStreamUrl(it.streamUrl) }
			}
			.onFailure {
				cachedClip = null
			}
	}

	val clip = cachedClip
	if (
		clip != null &&
		shouldShowLidaClipExtraScreenBackground(
			settingEnabled = enabled,
			lidaClipsEnabled = preferenceManager.lidaClipsEnabled,
			hasCachedClip = true,
			musicIsPlaying = !playerState.isPaused
		)
	) {
		NowPlayingLidaClipBackground(
			clip = clip,
			backgroundVideoMode = LidaClipsBackgroundVideoMode.Blurred,
			playerProgress = playerState.progress,
			musicIsPaused = playerState.isPaused,
			modifier = modifier
		)
	}
}

@Composable
fun NowPlayingLidaClipBackground(
	clip: DomainLidaClip,
	backgroundVideoMode: LidaClipsBackgroundVideoMode,
	playerProgress: Float,
	musicIsPaused: Boolean,
	onRecoverablePlaybackError: () -> Unit = {},
	modifier: Modifier = Modifier
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val startProgress = remember(clip.id) { playerProgress.coerceIn(0f, 1f) }
	val startPositionMs = remember(clip.id, startProgress, clip.durationSeconds) {
		lidaClipProgressStartPositionMs(startProgress, lidaClipDurationMs(clip.durationSeconds))
	}
	var hasRenderedFirstFrame by remember(clip.streamUrl) { mutableStateOf(false) }
	val requestHeaders = lidaClipsStreamRequestHeaders(
		baseUrl = preferenceManager.lidaClipsBaseUrl,
		streamUrl = clip.streamUrl,
		requestHeaders = preferenceManager.lidaClipsRequestHeadersMap()
	)
	val videoAlpha by animateFloatAsState(
		targetValue = if (hasRenderedFirstFrame) 1f else 0f,
		animationSpec = tween(NowPlayingVideoArtworkCrossfadeDurationMs),
		label = "nowPlayingLidaClipBackgroundAlpha"
	)

	Box(modifier) {
		val videoModifier = if (shouldBlurLidaClipBackgroundVideo(backgroundVideoMode)) {
			Modifier.matchParentSize().blur(28.dp)
		} else {
			Modifier.matchParentSize()
		}.alpha(videoAlpha)
		PlatformLidaClipPlayer(
			clip = clip,
			requestHeaders = requestHeaders,
			pictureInPictureEnabled = false,
			landscapeVideoModeEnabled = false,
			videoFitMode = lidaClipBackgroundVideoFitMode(backgroundVideoMode),
			respectAudioFocus = false,
			startPositionMs = startPositionMs,
			muted = shouldMuteNowPlayingBackgroundLidaClipVideo(),
			showControls = shouldShowNowPlayingLidaClipControls(),
			useTextureView = true,
			startProgress = startProgress,
			playWhenReady = shouldPlayNowPlayingLidaClipVideo(musicIsPaused),
			retryKey = 0,
			onPlaybackReady = {},
			onFirstFrameRendered = { hasRenderedFirstFrame = true },
			onPlaybackError = { message ->
				if (shouldRecoverLidaClipFromPlaybackError(message)) {
					hasRenderedFirstFrame = false
					onRecoverablePlaybackError()
				}
			},
			onPlaybackPositionChange = {},
			modifier = videoModifier
		)
		if (hasRenderedFirstFrame && shouldBlurLidaClipBackgroundVideo(backgroundVideoMode)) {
			Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
		}
	}
}

@Composable
fun NowPlayingLidaClipArtwork(
	clip: DomainLidaClip,
	playerProgress: Float,
	musicIsPaused: Boolean,
	onRecoverablePlaybackError: () -> Unit = {},
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
	var hasRenderedFirstFrame by remember(clip.streamUrl, playbackState.retryKey) {
		mutableStateOf(false)
	}
	var seekProgress by remember(clip.id) { mutableStateOf<Float?>(null) }
	var seekKey by remember(clip.id) { mutableStateOf(0) }
	val videoAlpha by animateFloatAsState(
		targetValue = if (hasRenderedFirstFrame) 1f else 0f,
		animationSpec = tween(NowPlayingVideoArtworkCrossfadeDurationMs),
		label = "nowPlayingLidaClipArtworkAlpha"
	)
	val videoScale = NowPlayingVideoArtworkCrossfadeInitialScale +
		((1f - NowPlayingVideoArtworkCrossfadeInitialScale) * videoAlpha)

	LaunchedEffect(clip.id, player) {
		player.seekEvents.collect { progress ->
			seekProgress = progress
			seekKey += 1
		}
	}

	DisposableEffect(clip.id, hasRenderedFirstFrame, player) {
		player.setNowPlayingVideoClipAudioActive(
			hasRenderedFirstFrame && shouldMuteMusicForNowPlayingPromotedLidaClip()
		)
		onDispose {
			player.setNowPlayingVideoClipAudioActive(false)
		}
	}

	if (preferenceManager.lidaClipsKeepScreenOn) {
		KeepScreenOn()
	}

	val frameShape = MaterialTheme.shapes.large
	Box(
		modifier = modifier
			.shadow(18.dp, frameShape, clip = false)
			.clip(frameShape)
			.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
			.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f), frameShape)
			.padding(8.dp),
		contentAlignment = Alignment.Center
	) {
		Box(
			modifier = Modifier
				.matchParentSize()
				.clip(frameShape)
				.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
		) {
			PlatformLidaClipPlayer(
				clip = clip,
				requestHeaders = requestHeaders,
				pictureInPictureEnabled = false,
				landscapeVideoModeEnabled = false,
				videoFitMode = lidaClipForegroundVideoFitMode(preferenceManager.lidaClipsVideoFitMode),
				respectAudioFocus = preferenceManager.respectAudioFocus,
				startPositionMs = startPositionMs,
				muted = !hasRenderedFirstFrame || shouldMuteNowPlayingPromotedLidaClipVideo(),
				showControls = shouldShowNowPlayingLidaClipControls(),
				startProgress = startProgress,
				playWhenReady = shouldPlayNowPlayingLidaClipVideo(musicIsPaused),
				seekProgress = seekProgress,
				seekKey = seekKey,
				retryKey = playbackState.retryKey,
				onPlaybackReady = {
					playbackState = playbackState.onReady()
				},
				onFirstFrameRendered = {
					hasRenderedFirstFrame = true
					playbackState = playbackState.onReady()
				},
				onPlaybackError = { message ->
					hasRenderedFirstFrame = false
					if (shouldRecoverLidaClipFromPlaybackError(message)) {
						playbackState = playbackState.onReady()
						onRecoverablePlaybackError()
					} else {
						playbackState = playbackState.onError(message)
					}
				},
				onPlaybackPositionChange = {},
				modifier = Modifier.matchParentSize().graphicsLayer {
					alpha = videoAlpha
					scaleX = videoScale
					scaleY = videoScale
				}
			)
		}
		playbackState.errorMessage?.let { errorMessage ->
			ErrorBox<Unit>(
				error = UiState.Error(Exception(errorMessage)),
				onRetry = {
					playbackState = playbackState.onRetry()
				}
			)
		}
		NowPlayingForegroundClipBadge(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(12.dp)
		)
	}
}

@Composable
private fun NowPlayingForegroundClipBadge(
	modifier: Modifier = Modifier
) {
	Surface(
		shape = MaterialTheme.shapes.small,
		color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
		contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
		tonalElevation = 4.dp,
		shadowElevation = 2.dp,
		modifier = modifier
	) {
		Icon(
			imageVector = Icons.Outlined.Movie,
			contentDescription = stringResource(Res.string.action_play_music_video),
			modifier = Modifier
				.padding(7.dp)
				.size(18.dp)
		)
	}
}
