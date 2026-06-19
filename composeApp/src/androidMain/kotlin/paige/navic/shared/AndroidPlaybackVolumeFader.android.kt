package paige.navic.shared

import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AndroidPlaybackVolumeFader(
	private val scope: CoroutineScope,
	private val effectiveVolume: (Float) -> Float
) {
	private var fadeJob: Job? = null
	private var restoreVolume: Float? = null

	fun cancel(player: MediaController?) {
		fadeJob?.cancel()
		fadeJob = null
		restoreVolume?.let { volume ->
			player?.volume = effectiveVolume(volume)
		}
		restoreVolume = null
	}

	fun start(
		player: MediaController,
		startVolume: Float,
		targetVolume: Float,
		durationMs: Long,
		restoreVolumeOnCancel: Float,
		onStart: () -> Unit = {},
		onEnd: () -> Unit = {}
	) {
		fadeJob?.cancel()
		restoreVolume = restoreVolumeOnCancel
		fadeJob = scope.launch(Dispatchers.Main.immediate) {
			onStart()
			val steps = (durationMs / 16L).coerceAtLeast(1L).toInt()
			repeat(steps) { step ->
				val progress = (step + 1).toFloat() / steps.toFloat()
				player.volume = effectiveVolume(
					startVolume + ((targetVolume - startVolume) * progress)
				)
				delay(16L)
			}
			player.volume = effectiveVolume(targetVolume)
			fadeJob = null
			restoreVolume = null
			onEnd()
		}
	}
}
