package paige.navic.domain.models

fun Iterable<DomainAlbum>.sortedByAlbumYearDescending(): List<DomainAlbum> =
	sortedWith(
		compareBy<DomainAlbum> { it.year == null }
			.thenByDescending { it.year ?: Int.MIN_VALUE }
			.thenBy { it.name.lowercase() }
	)
