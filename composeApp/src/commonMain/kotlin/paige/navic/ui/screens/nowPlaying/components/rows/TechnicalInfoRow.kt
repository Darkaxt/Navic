package paige.navic.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import paige.navic.domain.manager.ConnectivityManager
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.NowPlayingTechnicalInfoInput
import paige.navic.domain.models.nowPlayingTechnicalInfo
import paige.navic.shared.MediaPlayerViewModel

@Composable
fun NowPlayingTechnicalInfoRow(
	modifier: Modifier = Modifier
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val connectivityManager = koinInject<ConnectivityManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val song = playerState.currentSong

	val isCellular = connectivityManager.isCellular.value
	val requestedBitrate = if (preferenceManager.isAdvancedTranscodingActive) {
		if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
	} else {
		if (isCellular) preferenceManager.streamingQualityCellular.bitrateAndroid else preferenceManager.streamingQualityWifi.bitrateAndroid
	}
	val info = nowPlayingTechnicalInfo(
		style = preferenceManager.nowPlayingTechnicalInfoStyle,
		input = NowPlayingTechnicalInfoInput(
			playbackMimeType = playerState.playbackMimeType,
			fileExtension = song?.fileExtension,
			playbackSampleRateHz = playerState.playbackSampleRate,
			sourceSampleRateHz = song?.sampleRate,
			playbackBitrateBps = playerState.playbackBitrate,
			sourceBitrateKbps = song?.bitRate,
			requestedTranscodeBitrateKbps = requestedBitrate,
			bitDepth = song?.bitDepth,
			channelCount = song?.audioChannelCount,
			fileSizeBytes = song?.fileSize ?: 0L,
			replayGain = song?.replayGain
		)
	)

	Surface(
		modifier = modifier,
		shape = MaterialTheme.shapes.small,
		color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
		contentColor = MaterialTheme.colorScheme.onSurface
	) {
		Column(
			modifier = Modifier
				.widthIn(max = 340.dp)
				.padding(horizontal = 10.dp, vertical = 4.dp),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(
				text = info.primary,
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			info.secondary?.let { secondary ->
				Text(
					text = secondary,
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
	}
}
