package paige.navic.ui.screens.nowPlaying.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.NowPlayingArtworkRotationDurationMs
import paige.navic.domain.models.nowPlayingArtworkPaddingDp
import paige.navic.domain.models.shouldRotateNowPlayingArtwork
import paige.navic.domain.repositories.MusicBrainzArtworkRepository
import paige.navic.icons.Icons
import paige.navic.icons.filled.Note
import paige.navic.icons.outlined.Radio
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.CoverArt

@Composable
fun NowPlayingArtwork(
	modifier: Modifier = Modifier,
	isLandscape: Boolean,
	song: DomainSong,
	onClick: (() -> Unit)? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val musicBrainzArtworkRepository = koinInject<MusicBrainzArtworkRepository>()
	val playerState by player.uiState.collectAsState()
	val musicBrainzArtworkBySongId by musicBrainzArtworkRepository.artworkBySongId.collectAsState()
	val musicBrainzArtwork = musicBrainzArtworkBySongId[song.id]
	val hasArtwork = !song.coverArtId.isNullOrEmpty() || !musicBrainzArtwork?.imageUrl.isNullOrBlank()

	val isRadio = song.id.startsWith("radio_")
	val isActiveArtwork = playerState.currentSong?.id == song.id
	val rotationDegrees = rememberNowPlayingArtworkRotationDegrees(
		enabled = shouldRotateNowPlayingArtwork(
			enabled = preferenceManager.nowPlayingRotatingArtwork,
			isPaused = playerState.isPaused,
			isActiveArtwork = isActiveArtwork,
			hasCoverArt = hasArtwork
		)
	)

	val padding by animateDpAsState(
		targetValue = nowPlayingArtworkPaddingDp(
			size = preferenceManager.nowPlayingArtworkSize,
			isPausedOrInactive = playerState.isPaused || !isActiveArtwork,
			shrinkWhenPausedOrInactive = preferenceManager.shrinkNowPlayingArtworkOnPause
		).dp
	)
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier.then(
			if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)
		)
	) {
		CoverArt(
			coverArtId = song.coverArtId,
			imageUrl = musicBrainzArtwork?.imageUrl,
			imageCacheKey = musicBrainzArtwork?.sourceMbid?.let { "musicbrainz:$it" },
			modifier = Modifier
				.aspectRatio(1f)
				.then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxSize())
				.padding(padding)
				.then(if (rotationDegrees == 0f) Modifier else Modifier.rotate(rotationDegrees)),
			shadowElevation = 8.dp
		)
		if (!hasArtwork) {
			Icon(
				imageVector = if (isRadio) Icons.Outlined.Radio else Icons.Filled.Note,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
				modifier = Modifier.size(96.dp)
			)
		}
	}
}

@Composable
private fun rememberNowPlayingArtworkRotationDegrees(enabled: Boolean): Float {
	if (!enabled) return 0f

	val transition = rememberInfiniteTransition(label = "nowPlayingArtworkRotation")
	val rotationDegrees by transition.animateFloat(
		initialValue = 0f,
		targetValue = 360f,
		animationSpec = infiniteRepeatable(
			animation = tween(
				durationMillis = NowPlayingArtworkRotationDurationMs,
				easing = LinearEasing
			),
			repeatMode = RepeatMode.Restart
		),
		label = "nowPlayingArtworkRotation"
	)
	return rotationDegrees
}
