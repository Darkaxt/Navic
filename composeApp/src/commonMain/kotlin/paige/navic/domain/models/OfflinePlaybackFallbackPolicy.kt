package paige.navic.domain.models

/**
 * An outage report must not interrupt a song that is playing fine: a local or fully cached file, or a
 * stream the player is actively playing (an outage declared by background work such as a timed-out
 * download says nothing about this stream; if it really fails, its own player error starts recovery).
 */
fun shouldKeepLocalPlaybackDuringOutage(
	currentUsesLocalFile: Boolean,
	hasPlayerError: Boolean,
	isIdleOrEnded: Boolean,
	currentIsPlayingOrCached: Boolean = false
): Boolean = (currentUsesLocalFile || currentIsPlayingOrCached) && !hasPlayerError && !isIdleOrEnded

fun playbackRecoveryPositionMs(
	playerPositionMs: Long,
	uiProgressBelongsToCurrentItem: Boolean,
	uiPositionMs: Long
): Long = if (playerPositionMs >= 0L) playerPositionMs
	else if (uiProgressBelongsToCurrentItem) uiPositionMs.coerceAtLeast(0L) else 0L

sealed interface OfflinePlaybackFallbackResolution {
	data object KeepCurrent : OfflinePlaybackFallbackResolution
	data object Hold : OfflinePlaybackFallbackResolution
	data class PlayUpcoming(val targetIndex: Int) : OfflinePlaybackFallbackResolution
}

enum class OfflinePlaybackRecoveryRoute {
	ContinueRecovery,
	HandOffToOfflineFallback
}

fun resolvePlaybackRecoveryConnectivity(
	isEffectivelyOnline: Boolean,
	currentUsesLocalFile: Boolean
): OfflinePlaybackRecoveryRoute =
	if (!isEffectivelyOnline && !currentUsesLocalFile) {
		OfflinePlaybackRecoveryRoute.HandOffToOfflineFallback
	} else {
		OfflinePlaybackRecoveryRoute.ContinueRecovery
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
