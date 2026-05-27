package paige.navic.domain.models

import paige.navic.domain.models.settings.AutoFillQueueSource

const val QueueAutoFillRemainingTrigger = 10

fun shouldAutoFillQueue(
	autoFillQueue: Boolean,
	isPlaying: Boolean,
	isRadioQueue: Boolean,
	queueSize: Int,
	currentIndex: Int,
	remainingTrigger: Int,
	targetSize: Int
): Boolean {
	if (!autoFillQueue || !isPlaying || isRadioQueue) return false
	if (queueSize <= 0 || currentIndex !in 0..<queueSize) return false
	if (queueSize >= targetSize.coerceAtLeast(0)) return false

	val remainingAfterCurrent = queueSize - currentIndex - 1
	return remainingAfterCurrent <= remainingTrigger.coerceAtLeast(0)
}

fun queueAutoFillAppendCount(
	queueSize: Int,
	targetSize: Int
): Int = (targetSize.coerceAtLeast(0) - queueSize.coerceAtLeast(0)).coerceAtLeast(0)

fun queueAutoFillCandidateIds(
	candidateIds: List<String>,
	queuedIds: Set<String>,
	limit: Int
): List<String> {
	val seen = queuedIds.toMutableSet()
	return candidateIds
		.asSequence()
		.filterNot { it.startsWith("radio_") }
		.filter { seen.add(it) }
		.take(limit.coerceAtLeast(0))
		.toList()
}

fun queueAutoFillCandidateSongs(
	candidateSongs: List<DomainSong>,
	queuedIds: Set<String>,
	limit: Int,
	source: AutoFillQueueSource,
	currentSong: DomainSong?
): List<DomainSong> {
	val seen = queuedIds.toMutableSet()
	val filtered = candidateSongs
		.asSequence()
		.filterNot { it.id.startsWith("radio_") }
		.filter { seen.add(it.id) }
		.toList()

	val ordered = when (source) {
		AutoFillQueueSource.RandomLibrary -> filtered
		AutoFillQueueSource.SimilarToCurrentSong ->
			filtered
				.mapIndexed { index, song ->
					IndexedValue(index, song) to queueAutoFillSimilarityScore(currentSong, song)
				}
				.sortedWith(
					compareByDescending<Pair<IndexedValue<DomainSong>, Int>> { it.second }
						.thenBy { it.first.index }
				)
				.map { it.first.value }
	}

	return ordered.take(limit.coerceAtLeast(0))
}

private fun queueAutoFillSimilarityScore(
	currentSong: DomainSong?,
	candidateSong: DomainSong
): Int {
	if (currentSong == null || currentSong.id == candidateSong.id) return 0

	var score = 0
	if (currentSong.artistId.isNotBlank() && currentSong.artistId == candidateSong.artistId) {
		score += 30
	}
	if (currentSong.albumId != null && currentSong.albumId == candidateSong.albumId) {
		score += 8
	}

	val currentGenres = currentSong.normalizedGenres()
	val candidateGenres = candidateSong.normalizedGenres()
	score += currentGenres.intersect(candidateGenres).size * 10

	val currentMoods = currentSong.moods.mapTo(mutableSetOf()) { it.trim().lowercase() }
	val candidateMoods = candidateSong.moods.mapTo(mutableSetOf()) { it.trim().lowercase() }
	score += currentMoods.intersect(candidateMoods).size * 5

	return score
}

private fun DomainSong.normalizedGenres(): Set<String> =
	(genres + listOfNotNull(genre))
		.mapTo(mutableSetOf()) { it.trim().lowercase() }
		.filterTo(mutableSetOf()) { it.isNotEmpty() }
