package paige.navic.ui.screens.nowPlaying.components

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.nowPlayingArtworkPaddingDp
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
	val playerState by player.uiState.collectAsState()

	val isRadio = song.id.startsWith("radio_")

	val padding by animateDpAsState(
		targetValue = nowPlayingArtworkPaddingDp(
			size = preferenceManager.nowPlayingArtworkSize,
			isPausedOrInactive = playerState.isPaused || playerState.currentSong?.id != song.id
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
			modifier = Modifier
				.aspectRatio(1f)
				.then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxSize())
				.padding(padding),
			shadowElevation = 8.dp
		)
		if (song.coverArtId.isNullOrEmpty()) {
			Icon(
				imageVector = if (isRadio) Icons.Outlined.Radio else Icons.Filled.Note,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
				modifier = Modifier.size(96.dp)
			)
		}
	}
}
