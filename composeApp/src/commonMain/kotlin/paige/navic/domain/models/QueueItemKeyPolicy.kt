package paige.navic.domain.models

fun queueItemKeys(queue: List<DomainSong>): List<String> {
	val occurrenceCounts = mutableMapOf<String, Int>()

	return queue.map { song ->
		val occurrence = occurrenceCounts.getOrDefault(song.id, 0)
		occurrenceCounts[song.id] = occurrence + 1
		"${song.id}:$occurrence"
	}
}
