package paige.navic.ui.screens.reader

import androidx.compose.runtime.Composable
import navic.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import paige.navic.reader.ReaderWhispersyncPlaybackControlDescription
import paige.navic.reader.ReaderWhispersyncStatus
import paige.navic.reader.ReaderWhispersyncStatusMessage

@Composable
internal fun ReaderWhispersyncStatus.localizedLabel(): String =
	when (message) {
		ReaderWhispersyncStatusMessage.Ready -> stringResource(Res.string.info_whispersync_ready)
		ReaderWhispersyncStatusMessage.Paused -> stringResource(Res.string.info_whispersync_paused)
		ReaderWhispersyncStatusMessage.SeekingAudio -> stringResource(Res.string.info_whispersync_seeking_audio)
		ReaderWhispersyncStatusMessage.Playing -> stringResource(Res.string.info_whispersync_playing)
		ReaderWhispersyncStatusMessage.NoActiveCue -> stringResource(Res.string.info_whispersync_no_active_cue)
		ReaderWhispersyncStatusMessage.VisiblePageEnded -> stringResource(Res.string.info_whispersync_visible_page_ended)
		ReaderWhispersyncStatusMessage.Mismatch -> stringResource(Res.string.info_whispersync_mismatch)
		ReaderWhispersyncStatusMessage.Unavailable -> stringResource(Res.string.info_whispersync_unavailable)
		ReaderWhispersyncStatusMessage.AudioUnavailable -> stringResource(Res.string.info_whispersync_audio_unavailable)
		null -> stringResource(Res.string.title_whispersync)
	}

@Composable
internal fun ReaderWhispersyncStatus.localizedDetail(): String? =
	detail?.takeIf { it.isNotBlank() }
		?: syncedSegmentCount?.let { count ->
			stringResource(
				if (count == 1) {
					Res.string.info_whispersync_synced_segment
				} else {
					Res.string.info_whispersync_synced_segments
				},
				count
			)
		}

@Composable
internal fun ReaderWhispersyncPlaybackControlDescription.localizedDescription(): String =
	when (this) {
		ReaderWhispersyncPlaybackControlDescription.Audiobook ->
			stringResource(Res.string.title_whispersync_audiobook)
		ReaderWhispersyncPlaybackControlDescription.Loading ->
			stringResource(Res.string.info_whispersync_audiobook_loading)
		ReaderWhispersyncPlaybackControlDescription.Reset ->
			stringResource(Res.string.action_reset_whispersync_audiobook)
		ReaderWhispersyncPlaybackControlDescription.Play ->
			stringResource(Res.string.action_play_whispersync_audiobook)
	}
