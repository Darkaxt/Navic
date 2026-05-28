package paige.navic.domain.models

fun discoverQueueRemovalIndexes(
	queueSongIds: List<String>,
	currentIndex: Int,
	knownSongIds: Set<String>
): List<Int> {
	if (currentIndex !in queueSongIds.indices || knownSongIds.isEmpty()) return emptyList()

	return queueSongIds
		.asSequence()
		.withIndex()
		.filter { (index, songId) ->
			index > currentIndex &&
				!songId.startsWith("radio_") &&
				songId in knownSongIds
		}
		.map { it.index }
		.toList()
}
