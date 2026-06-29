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
		isMoodMixPlaylist() -> moodMixDisplayName()
		isGenreMixPlaylist() -> genreMixDisplayName()
		else -> name
	}

fun DomainPlaylist.playlistArtworkLabel(): String =
	when {
		isGenreMixPlaylist() -> genreMixDisplayName()
			.split(" / ")
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.take(3)
			.joinToString("\n")
			.ifBlank { playlistDisplayName() }
		else -> playlistDisplayName()
	}

fun DomainPlaylist.playlistFallbackKind(): String =
	when {
		isGeneratedMixPlaylist() -> "Mix"
		isStationPlaylist() -> "Flow"
		else -> "Playlist"
	}

private fun DomainPlaylist.moodMixDisplayName(): String =
	name.removeSuffix(MoodMixSuffix)
		.trimEnd()
		.ifBlank { name }

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

fun DomainPlaylist.visiblePlaylistCoverArtId(): String? =
	coverArtId.takeUnless { isGeneratedMixPlaylist() }

fun DomainSongCollection.visibleCollectionCoverArtId(): String? =
	when (this) {
		is DomainPlaylist -> visiblePlaylistCoverArtId()
		else -> coverArtId
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
