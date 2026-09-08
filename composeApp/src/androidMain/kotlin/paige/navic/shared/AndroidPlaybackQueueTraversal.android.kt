package paige.navic.shared

import androidx.media3.common.C
import androidx.media3.common.Player

internal fun Player.upcomingMediaItemIndexes(): List<Int> {
	val itemCount = mediaItemCount
	val currentIndex = currentMediaItemIndex
	if (itemCount <= 0 || currentIndex !in 0 until itemCount) return emptyList()
	if (repeatMode == Player.REPEAT_MODE_ONE) return listOf(currentIndex)
	val timeline = currentTimeline
	if (timeline.isEmpty) return emptyList()
	val indexes = mutableListOf<Int>()
	var index = currentIndex
	repeat(itemCount - 1) {
		val nextIndex = timeline.getNextWindowIndex(index, repeatMode, shuffleModeEnabled)
		if (nextIndex == C.INDEX_UNSET || nextIndex !in 0 until itemCount || nextIndex == currentIndex) return indexes
		indexes += nextIndex
		index = nextIndex
	}
	return indexes
}

internal fun Player.persistableShuffleOrder(): List<Int>? {
	if (!shuffleModeEnabled || currentTimeline.isEmpty) return null
	val timeline = currentTimeline
	val indexes = mutableListOf<Int>()
	var index = timeline.getFirstWindowIndex(true)
	repeat(mediaItemCount) {
		if (index !in 0 until mediaItemCount) return null
		indexes += index
		index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
	}
	return indexes
}
