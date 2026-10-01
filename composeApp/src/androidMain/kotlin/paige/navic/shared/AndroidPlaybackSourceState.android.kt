package paige.navic.shared

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.shouldReplaceQueuedMediaItemForDownloadAvailability
import paige.navic.ui.core.PlayerUiState
import java.io.File

internal fun playbackSourceUpdate(
	item: MediaItem,
	localPath: String?,
	isCurrentItem: Boolean,
	isRecoveringFromSourceError: Boolean,
	streamUriForSongId: (String) -> Uri
): MediaItem? {
	if (isCurrentItem && !isRecoveringFromSourceError) return null
	if (item.mediaId.startsWith("radio_") || item.mediaId.startsWith(AurralFlowSongIdPrefix)) return null
	val previousUri = item.localConfiguration?.uri
	val desiredUri = localPath?.let { File(it).toUri() } ?: streamUriForSongId(item.mediaId)
	if (!shouldReplaceQueuedMediaItemForDownloadAvailability(
		isCurrentItem = isCurrentItem,
		hasDownloadedFile = localPath != null,
		isCurrentlyLocal = previousUri?.scheme == "file",
		isRecoveringFromSourceError = isRecoveringFromSourceError,
		hasSourceUriChanged = desiredUri != previousUri
	)) return null
	return item.buildUpon().setUri(desiredUri).build()
}

internal fun MediaItem.requestedPlaybackBitrateKbps(): Int? {
	val uri = localConfiguration?.uri ?: return null
	if (uri.scheme !in listOf("http", "https") || uri.getQueryParameter("id") != mediaId ||
		uri.lastPathSegment !in listOf("stream", "stream.view")) return null
	return uri.getQueryParameter("maxBitRate")?.toIntOrNull()?.takeIf { it > 0 }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun PlayerUiState.withPlaybackTracks(tracks: Tracks): PlayerUiState {
	val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
	val format = group?.let { audio ->
		(0 until audio.length).firstOrNull(audio::isTrackSelected)?.let(audio::getTrackFormat)
	}
	return copy(
		playbackMimeType = format?.sampleMimeType,
		playbackBitrate = format?.bitrate?.takeIf { it > 0 },
		playbackSampleRate = format?.sampleRate?.takeIf { it > 0 }
	)
}
