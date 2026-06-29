package paige.navic.domain.models

private const val StationPlaylistPrefix = "[A] "
private const val MoodMixSuffix = " Mix"
private val GenreMixMetadataTokens = setOf(
	"low",
	"medium",
	"high",
	"danceable",
	"party",
	"automatic"
)

fun DomainPlaylist.isStationPlaylist(): Boolean =
	name.startsWith(StationPlaylistPrefix)

fun DomainPlaylist.isGeneratedMixPlaylist(): Boolean =
	isMoodMixPlaylist() || isGenreMixPlaylist()

fun DomainPlaylist.isGenreMixPlaylist(): Boolean =
	!isStationPlaylist() && name.contains("_")

fun DomainPlaylist.isMoodMixPlaylist(): Boolean =
	!isStationPlaylist() && !isGenreMixPlaylist() && name.endsWith(MoodMixSuffix)

fun DomainPlaylist.stationDisplayName(): String {
	if (!isStationPlaylist()) return name
	return name.removePrefix(StationPlaylistPrefix)
		.trimStart()
		.ifBlank { name }
}

fun DomainPlaylist.playlistDisplayName(): String =
	when {
		isStationPlaylist() -> stationDisplayName()
		isGenreMixPlaylist() -> genreMixDisplayName()
		else -> name
	}

private fun DomainPlaylist.genreMixDisplayName(): String {
	val parts = name.split("_")
		.map { part -> part.trim() }
		.filter { part -> part.isNotEmpty() }
	val genreParts = parts.takeWhile { part -> !part.isGenreMixMetadataToken() }
		.ifEmpty { parts }
	return genreParts.joinToString(" / ").ifBlank { name }
}

private fun String.isGenreMixMetadataToken(): Boolean =
	all { char -> char.isDigit() } || lowercase() in GenreMixMetadataTokens

fun DomainSongCollection.displayName(): String =
	when (this) {
		is DomainPlaylist -> playlistDisplayName()
		else -> name
	}

fun List<DomainPlaylist>.stationPlaylists(): List<DomainPlaylist> =
	filter { it.isStationPlaylist() }

fun List<DomainPlaylist>.moodMixPlaylists(): List<DomainPlaylist> =
	filter { it.isMoodMixPlaylist() }

fun List<DomainPlaylist>.genreMixPlaylists(): List<DomainPlaylist> =
	filter { it.isGenreMixPlaylist() }

fun List<DomainPlaylist>.userPlaylists(): List<DomainPlaylist> =
	filterNot { it.isStationPlaylist() || it.isGeneratedMixPlaylist() }

fun List<DomainPlaylist>.regularPlaylists(): List<DomainPlaylist> =
	userPlaylists()

fun canDeletePlaylistFromDetail(playlist: DomainPlaylist): Boolean =
	!playlist.isStationPlaylist() && !playlist.isGeneratedMixPlaylist()
