package paige.navic.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import paige.navic.domain.manager.PlaybackOriginCredit
import paige.navic.domain.manager.PlaybackOriginTracker
import paige.navic.domain.models.PlaybackOrigin
import paige.navic.domain.repositories.PlaybackOriginRepository
import paige.navic.util.core.Logger
import kotlin.time.Clock

private const val PlaybackOriginCheckpointIntervalMs = 30_000L

internal class AndroidPlaybackOriginRecorder(
	private val scope: CoroutineScope,
	private val repository: PlaybackOriginRepository
) {
	private val tracker = PlaybackOriginTracker()
	private var lastCheckpointMillis = 0L

	fun setOrigin(origin: PlaybackOrigin?) {
		scope.launch {
			setOriginNow(origin)
		}
	}

	fun setOriginNow(origin: PlaybackOrigin?) {
		val nowMillis = Clock.System.now().toEpochMilliseconds()
		record(tracker.setOrigin(origin, nowMillis))
		lastCheckpointMillis = nowMillis
	}

	fun onPlaybackState(
		isPlaying: Boolean,
		nowMillis: Long
	) {
		record(
			tracker.onPlaybackState(
				isPlaying = isPlaying,
				nowMillis = nowMillis
			)
		)
		if (isPlaying) {
			lastCheckpointMillis = nowMillis
		}
	}

	fun checkpointIfNeeded(nowMillis: Long) {
		if (nowMillis - lastCheckpointMillis < PlaybackOriginCheckpointIntervalMs) {
			return
		}
		record(tracker.checkpoint(nowMillis))
		lastCheckpointMillis = nowMillis
	}

	private fun record(credit: PlaybackOriginCredit?) {
		if (credit == null) return
		scope.launch(Dispatchers.IO) {
			runCatching {
				repository.credit(
					origin = credit.origin,
					durationMillis = credit.durationMillis
				)
			}.onFailure { error ->
				Logger.w("MediaPlayer", "Failed to credit playback origin", error)
			}
		}
	}
}
