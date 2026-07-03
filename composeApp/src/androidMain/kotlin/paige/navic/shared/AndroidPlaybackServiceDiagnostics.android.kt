package paige.navic.shared

import androidx.media3.common.Player
import paige.navic.domain.models.PlaybackDiagnosticsLogTag
import paige.navic.domain.models.playbackDiagnosticMessage
import paige.navic.util.core.Logger

internal fun logPlaybackServiceDiagnostic(
	event: String,
	player: Player,
	vararg fields: Pair<String, Any?>
) {
	Logger.i(
		PlaybackDiagnosticsLogTag,
		playbackDiagnosticMessage(
			event,
			"mediaId" to player.currentMediaItem?.mediaId,
			"index" to player.currentMediaItemIndex,
			"playWhenReady" to player.playWhenReady,
			"isPlaying" to player.isPlaying,
			*fields
		)
	)
}
