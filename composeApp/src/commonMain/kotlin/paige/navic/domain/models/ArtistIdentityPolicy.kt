package paige.navic.domain.models

fun resolveArtistId(overrideId: String?, sourceId: String?): String? =
	overrideId ?: sourceId
