package paige.navic.domain.models

const val QuickPicksDefaultSize = 20

fun quickPickSongs(
	songs: List<DomainSong>,
	albums: List<DomainAlbum>,
	enabled: Boolean = true,
	limit: Int = QuickPicksDefaultSize,
	minDurationSeconds: Int = 0
): List<DomainSong> {
	if (!enabled) return emptyList()
	val resultLimit = limit.coerceAtLeast(0)
	if (resultLimit == 0) return emptyList()

	val originalIndexes = songs.withIndex().associate { it.value.id to it.index }
	val albumCreatedAt = albums.associate { it.id to it.createdAt }
	val minimumDurationSeconds = minDurationSeconds.coerceAtLeast(0)
	val candidates = songs.filter {
		hasStableNavidromeSongId(it.id) &&
			it.duration.inWholeSeconds >= minimumDurationSeconds
	}

	val buckets = listOf(
		candidates
			.filter { it.playCount > 0 }
			.sortedWith(
				compareByDescending<DomainSong> { it.playCount }
					.thenBy { originalIndexes[it.id] ?: Int.MAX_VALUE }
			),
		candidates
			.filter { (it.userRating ?: 0) > 0 }
			.sortedWith(
				compareByDescending<DomainSong> { it.userRating ?: 0 }
					.thenBy { originalIndexes[it.id] ?: Int.MAX_VALUE }
			),
		candidates
			.filter { albumCreatedAt[it.albumId] != null }
			.sortedWith(
				compareByDescending<DomainSong> { albumCreatedAt[it.albumId] }
					.thenBy { originalIndexes[it.id] ?: Int.MAX_VALUE }
			),
		candidates
	)

	val seen = mutableSetOf<String>()
	return buckets
		.asSequence()
		.flatten()
		.filter { seen.add(it.id) }
		.take(resultLimit)
		.toList()
}
