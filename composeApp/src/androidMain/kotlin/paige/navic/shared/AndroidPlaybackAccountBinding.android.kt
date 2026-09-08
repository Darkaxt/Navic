package paige.navic.shared

import androidx.media3.common.Player
import paige.navic.domain.manager.PlaybackAccountBoundary

internal fun bindPlaybackAccount(
	boundary: PlaybackAccountBoundary,
	player: Player,
	invalidateAutomaticResume: () -> Unit
): () -> Unit = boundary.register {
	invalidateAutomaticResume()
	player.pause()
	player.stop()
	player.clearMediaItems()
}
