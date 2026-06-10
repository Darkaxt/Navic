package paige.navic.ui.screens.nowPlaying.components.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject
import paige.navic.domain.manager.PreferenceManager
import paige.navic.shared.MediaPlayerViewModel
import paige.navic.ui.components.common.PlaybackProgressSlider

@Composable
fun NowPlayingProgressBar() {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	PlaybackProgressSlider(
		value = playerState.progress,
		onValueChange = { player.seek(it) },
		isPlaying = !playerState.isPaused,
		enabled = playerState.currentSong != null,
		sliderStyle = preferenceManager.nowPlayingSliderStyle,
		progressWidth = preferenceManager.nowPlayingProgressWidth
	)
}
