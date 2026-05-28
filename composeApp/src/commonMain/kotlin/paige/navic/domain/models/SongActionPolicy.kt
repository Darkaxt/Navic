package paige.navic.domain.models

fun hasStableNavidromeSongId(songId: String?): Boolean =
	!songId.isNullOrBlank() && !songId.startsWith("radio_")
