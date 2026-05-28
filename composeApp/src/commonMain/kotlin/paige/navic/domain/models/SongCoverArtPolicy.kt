package paige.navic.domain.models

fun songCoverArtIdWithAlbumFallback(
	songCoverArtId: String?,
	albumCoverArtId: String?
): String? =
	songCoverArtId?.takeIf { it.isNotBlank() }
		?: albumCoverArtId?.takeIf { it.isNotBlank() }
