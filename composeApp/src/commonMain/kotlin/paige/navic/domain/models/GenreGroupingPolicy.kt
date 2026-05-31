package paige.navic.domain.models

fun genreGroupsFromAlbums(albums: List<DomainAlbum>): List<DomainGenre> {
	val albumsByGenre = linkedMapOf<String, MutableList<DomainAlbum>>()
	albums.forEach { album ->
		album.genreNames().forEach { genreName ->
			albumsByGenre.getOrPut(genreName) { mutableListOf() }.add(album)
		}
	}

	return albumsByGenre.map { (genreName, groupedAlbums) ->
		val sortedAlbums = groupedAlbums
			.distinctBy { it.id }
			.sortedByAlbumYearDescending()
		DomainGenre(
			name = genreName,
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

private fun DomainAlbum.genreNames(): Set<String> =
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
