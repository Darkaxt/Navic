package paige.navic.domain.models

sealed interface OfflinePlaybackFallbackResolution {
	data object KeepCurrent : OfflinePlaybackFallbackResolution
	data object Hold : OfflinePlaybackFallbackResolution
	data class PlayUpcoming(val targetIndex: Int) : OfflinePlaybackFallbackResolution
}

fun resolveOfflinePlaybackFallback(
	currentIndex: Int,
	queueSongIds: List<String>,
	upcomingIndexes: List<Int>,
	availableSongIds: Set<String>,
	currentUsesLocalFile: Boolean
): OfflinePlaybackFallbackResolution {
	val currentSongId = queueSongIds.getOrNull(currentIndex)
	if (currentUsesLocalFile || currentSongId in availableSongIds) {
		return OfflinePlaybackFallbackResolution.KeepCurrent
	}

	return upcomingIndexes
		.asSequence()
		.filter { index -> index != currentIndex && index in queueSongIds.indices }
		.firstOrNull { index -> queueSongIds[index] in availableSongIds }
		?.let(OfflinePlaybackFallbackResolution::PlayUpcoming)
		?: OfflinePlaybackFallbackResolution.Hold
}
