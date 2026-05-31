package paige.navic.domain.manager

import paige.navic.domain.models.PlaybackOrigin

data class PlaybackOriginCredit(
	val origin: PlaybackOrigin,
	val durationMillis: Long
)

class PlaybackOriginTracker {
	private var currentOrigin: PlaybackOrigin? = null
	private var playingSinceMillis: Long? = null

	fun setOrigin(origin: PlaybackOrigin?, nowMillis: Long): PlaybackOriginCredit? {
		val wasPlaying = playingSinceMillis != null
		val credit = flush(nowMillis)
		currentOrigin = origin
		playingSinceMillis = if (origin != null && wasPlaying) nowMillis else null
		return credit
	}

	fun onPlaybackState(isPlaying: Boolean, nowMillis: Long): PlaybackOriginCredit? {
		if (isPlaying) {
			if (currentOrigin != null && playingSinceMillis == null) {
				playingSinceMillis = nowMillis
			}
			return null
		}

		return flush(nowMillis)
	}

	fun checkpoint(nowMillis: Long): PlaybackOriginCredit? {
		val origin = currentOrigin ?: run {
			playingSinceMillis = null
			return null
		}
		val startedAt = playingSinceMillis ?: return null
		playingSinceMillis = nowMillis

		val durationMillis = (nowMillis - startedAt).coerceAtLeast(0L)
		return if (durationMillis > 0L) {
			PlaybackOriginCredit(
				origin = origin,
				durationMillis = durationMillis
			)
		} else {
			null
		}
	}

	fun flush(nowMillis: Long): PlaybackOriginCredit? {
		val origin = currentOrigin ?: run {
			playingSinceMillis = null
			return null
		}
		val startedAt = playingSinceMillis ?: return null
		playingSinceMillis = null

		val durationMillis = (nowMillis - startedAt).coerceAtLeast(0L)
		return if (durationMillis > 0L) {
			PlaybackOriginCredit(
				origin = origin,
				durationMillis = durationMillis
			)
		} else {
			null
		}
	}
}
