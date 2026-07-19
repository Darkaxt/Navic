package paige.navic.domain.models

fun genreGroupsFromAlbums(albums: List<DomainAlbum>): List<DomainGenre> {
	val namesByKey = linkedMapOf<String, String>()
	val albumsByGenre = linkedMapOf<String, MutableList<DomainAlbum>>()
	albums.forEach { album ->
		album.genreNames().forEach { genreName ->
			val key = genreName.lowercase()
			namesByKey.putIfAbsent(key, genreName)
			albumsByGenre.getOrPut(key) { mutableListOf() }.add(album)
		}
	}

	return albumsByGenre.map { (genreKey, groupedAlbums) ->
		val sortedAlbums = groupedAlbums
			.distinctBy { it.id }
			.sortedByAlbumYearDescending()
		DomainGenre(
			name = namesByKey.getValue(genreKey),
			albumCount = sortedAlbums.size,
			songCount = sortedAlbums
				.flatMap { it.songs }
				.distinctBy { it.id }
				.size,
			albums = sortedAlbums
		)
	}.sortedWith(
		compareByDescending<DomainGenre> { it.albums.size }
			.thenBy { it.name.lowercase() }
	)
}

fun genreSummariesFromAlbums(albums: List<GenreAlbumSummaryInput>): List<DomainGenreSummary> {
	val namesByKey = linkedMapOf<String, String>()
	val albumsByGenre = linkedMapOf<String, LinkedHashMap<String, GenreAlbumSummaryInput>>()
	albums.forEach { album ->
		genreNames(album.genre, album.genres).forEach { genreName ->
			val key = genreName.lowercase()
			namesByKey.putIfAbsent(key, genreName)
			albumsByGenre.getOrPut(key) { linkedMapOf() }.putIfAbsent(album.albumId, album)
		}
	}

	return albumsByGenre.map { (genreKey, albumsById) ->
		val groupedAlbums = albumsById.values
		DomainGenreSummary(
			name = namesByKey.getValue(genreKey),
			albumCount = groupedAlbums.size,
			songCount = groupedAlbums.sumOf { it.songCount.coerceAtLeast(0) },
			coverArtIds = groupedAlbums
				.map { it.coverArtId }
				.filter { it.isNotBlank() }
				.distinct()
				.take(2)
		)
	}.sortedWith(
		compareByDescending<DomainGenreSummary> { it.albumCount }
			.thenBy { it.name.lowercase() }
	)
}

fun genreGroupByName(albums: List<DomainAlbum>, genreName: String): DomainGenre? {
	val normalizedTarget = genreName.normalizedGenreDisplayName() ?: return null
	val matchingAlbums = albums
		.filter { album ->
			album.genreNames().any { it.equals(normalizedTarget, ignoreCase = true) }
		}
		.distinctBy { it.id }
		.sortedByAlbumYearDescending()
	if (matchingAlbums.isEmpty()) return null

	return DomainGenre(
		name = normalizedTarget,
		albumCount = matchingAlbums.size,
		songCount = matchingAlbums
			.flatMap { it.songs }
			.distinctBy { it.id }
			.size,
		albums = matchingAlbums
	)
}

private fun DomainAlbum.genreNames(): Set<String> = genreNames(genre, genres)

private fun genreNames(genre: String?, genres: List<String>): Set<String> =
	(listOfNotNull(genre) + genres)
		.flatMap { it.expandedGenreNames() }
		.mapNotNull { it.normalizedGenreDisplayName() }
		.toSet()

private fun String.expandedGenreNames(): List<String> {
	val cleaned = trim().replace(Regex("\\s+"), " ")
	if (cleaned.isEmpty()) return emptyList()

	val parts = cleaned
		.split(Regex("\\s+[-–—]\\s+|\\s*/\\s*|\\s*\\|\\s*"))
		.map { it.trim() }
		.filter { it.isNotEmpty() }
	return parts.takeIf { it.size > 1 } ?: listOf(cleaned)
}

private fun String.normalizedGenreDisplayName(): String? {
	val cleaned = trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() } ?: return null
	if (cleaned.isLowSignalGenreName()) return null
	return when (cleaned.lowercase()) {
		"game",
		"games" -> "Game"
		"soundtrack",
		"soundtracks" -> "Soundtracks"
		"other",
		"others" -> "Other"
		else -> cleaned.replaceFirstChar { char -> char.uppercase() }
	}
}

private fun String.isLowSignalGenreName(): Boolean =
	lowercase() in setOf("other", "unknown", "misc", "miscellaneous")
