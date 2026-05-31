package paige.navic.domain.models

const val AurralFlowSongIdPrefix = "aurral_flow_"

fun hasStableNavidromeSongId(songId: String?): Boolean =
	!songId.isNullOrBlank() &&
		!songId.startsWith("radio_") &&
		!songId.startsWith(AurralFlowSongIdPrefix)
