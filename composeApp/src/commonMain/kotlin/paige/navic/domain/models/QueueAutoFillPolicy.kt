package paige.navic.domain.models

import paige.navic.domain.models.settings.AutoFillQueueSource

const val QueueAutoFillRemainingTrigger = 10
const val SongRadioQueueDefaultSize = 50

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
		.filter(::hasStableNavidromeSongId)
		.filter { seen.add(it) }
		.take(limit.coerceAtLeast(0))
		.toList()
}

fun queueAutoFillCandidateSongs(
	candidateSongs: List<DomainSong>,
	queuedIds: Set<String>,
	limit: Int,
	source: AutoFillQueueSource,
	currentSong: DomainSong?,
	preferredSongIds: List<String> = emptyList(),
	recentSongs: List<DomainSong> = emptyList()
): List<DomainSong> {
	val seen = queuedIds.toMutableSet()
	val preferredRanks = preferredSongIds
		.distinct()
		.withIndex()
		.associate { it.value to it.index }
	val filtered = candidateSongs
		.asSequence()
		.filter { hasStableNavidromeSongId(it.id) }
		.filter { seen.add(it.id) }
		.toList()

	val ordered = when (source) {
		AutoFillQueueSource.RandomLibrary -> filtered
		AutoFillQueueSource.SimilarToCurrentSong ->
			filtered
				.mapIndexed { index, song ->
					IndexedValue(index, song) to QueueAutoFillCandidateRank(
						preferredIndex = preferredRanks[song.id],
						similarityScore = queueAutoFillSimilarityScore(currentSong, song)
					)
				}
				.sortedWith(
					compareBy<Pair<IndexedValue<DomainSong>, QueueAutoFillCandidateRank>> {
						it.second.preferredIndex ?: Int.MAX_VALUE
					}
						.thenByDescending { it.second.similarityScore }
						.thenBy { it.first.index }
				)
				.map { it.first.value }
		AutoFillQueueSource.RecentGenres ->
			filtered
				.mapIndexed { index, song ->
					IndexedValue(index, song) to queueAutoFillRecentContextScore(
						recentSongs = recentSongs.ifEmpty { listOfNotNull(currentSong) },
						candidateSong = song
					)
				}
				.let { scoredSongs ->
					scoredSongs.filter { it.second > 0 }
						.ifEmpty { scoredSongs }
				}
				.sortedWith(
					compareByDescending<Pair<IndexedValue<DomainSong>, Int>> { it.second }
						.thenBy { it.first.index }
				)
				.map { it.first.value }
	}

	return ordered.take(limit.coerceAtLeast(0))
}

fun songRadioQueue(
	seedSong: DomainSong,
	candidateSongs: List<DomainSong>,
	limit: Int = SongRadioQueueDefaultSize,
	preferredSongIds: List<String> = emptyList()
): List<DomainSong> {
	val queueLimit = limit.coerceAtLeast(0)
	if (queueLimit == 0 || !hasStableNavidromeSongId(seedSong.id)) return emptyList()

	return listOf(seedSong) + queueAutoFillCandidateSongs(
		candidateSongs = candidateSongs,
		queuedIds = setOf(seedSong.id),
		limit = queueLimit - 1,
		source = AutoFillQueueSource.SimilarToCurrentSong,
		currentSong = seedSong,
		preferredSongIds = preferredSongIds
	)
}

private data class QueueAutoFillCandidateRank(
	val preferredIndex: Int?,
	val similarityScore: Int
)

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

private fun queueAutoFillRecentContextScore(
	recentSongs: List<DomainSong>,
	candidateSong: DomainSong
): Int {
	if (recentSongs.isEmpty()) return 0

	val recentGenres = recentSongs.flatMapTo(mutableSetOf()) { it.normalizedGenres() }
	val candidateGenres = candidateSong.normalizedGenres()
	val recentMoods = recentSongs.flatMapTo(mutableSetOf()) { song ->
		song.moods.normalizedTags()
	}
	val candidateMoods = candidateSong.moods.normalizedTags()

	return recentGenres.intersect(candidateGenres).size * 10 +
		recentMoods.intersect(candidateMoods).size * 5
}

private fun DomainSong.normalizedGenres(): Set<String> =
	(genres + listOfNotNull(genre))
		.normalizedTags()

private fun Iterable<String>.normalizedTags(): Set<String> =
	map { it.trim().lowercase() }
		.filter { it.isNotEmpty() }
		.toSet()
