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
	!isStationPlaylist() && isGenreMixPlaylistName(name)

fun DomainPlaylist.isMoodMixPlaylist(): Boolean =
	!isStationPlaylist() && isMoodMixPlaylistName(name)

fun isGeneratedMixPlaylistName(name: String): Boolean =
	isMoodMixPlaylistName(name) || isGenreMixPlaylistName(name)

fun generatedMixPlaylistArtworkLabel(name: String): String =
	if (isGenreMixPlaylistName(name)) {
		genreMixDisplayName(name)
			.split(" / ")
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.take(3)
			.joinToString("\n")
			.ifBlank { name }
	} else {
		moodMixDisplayName(name)
	}

fun DomainPlaylist.stationDisplayName(): String {
	if (!isStationPlaylist()) return name
	return name.removePrefix(StationPlaylistPrefix)
		.trimStart()
		.ifBlank { name }
}

fun DomainPlaylist.playlistDisplayName(): String =
	when {
		isStationPlaylist() -> stationDisplayName()
		isMoodMixPlaylist() -> moodMixDisplayName(name)
		isGenreMixPlaylist() -> genreMixDisplayName(name)
		else -> name
	}

fun DomainPlaylist.playlistArtworkLabel(): String =
	if (isGeneratedMixPlaylist()) generatedMixPlaylistArtworkLabel(name) else playlistDisplayName()

fun DomainPlaylist.playlistFallbackKind(): String =
	when {
		isGeneratedMixPlaylist() -> "Mix"
		isStationPlaylist() -> "Flow"
		else -> "Playlist"
	}

private fun isGenreMixPlaylistName(name: String): Boolean =
	name.contains("_")

private fun isMoodMixPlaylistName(name: String): Boolean =
	!isGenreMixPlaylistName(name) && name.endsWith(MoodMixSuffix)

private fun moodMixDisplayName(name: String): String =
	name.removeSuffix(MoodMixSuffix)
		.trimEnd()
		.ifBlank { name }

private fun genreMixDisplayName(name: String): String {
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
	coverArtId.takeUnless { isStationPlaylist() || isGeneratedMixPlaylist() }

fun DomainSongCollection.visibleCollectionCoverArtId(): String? =
	when (this) {
		is DomainPlaylist -> visiblePlaylistCoverArtId()
		else -> coverArtId
	}

fun DomainPlaylist.hasVisibleEntries(): Boolean =
	songCount > 0 || songs.isNotEmpty()

fun List<DomainPlaylist>.stationPlaylists(): List<DomainPlaylist> =
	filter { it.hasVisibleEntries() && it.isStationPlaylist() }

fun List<DomainPlaylist>.moodMixPlaylists(): List<DomainPlaylist> =
	filter { it.hasVisibleEntries() && it.isMoodMixPlaylist() }

fun List<DomainPlaylist>.genreMixPlaylists(): List<DomainPlaylist> =
	filter { it.hasVisibleEntries() && it.isGenreMixPlaylist() }

fun List<DomainPlaylist>.userPlaylists(): List<DomainPlaylist> =
	filter { it.hasVisibleEntries() && !it.isStationPlaylist() && !it.isGeneratedMixPlaylist() }

fun List<DomainPlaylist>.regularPlaylists(): List<DomainPlaylist> =
	userPlaylists()

fun canDeletePlaylistFromDetail(playlist: DomainPlaylist): Boolean =
	!playlist.isStationPlaylist() && !playlist.isGeneratedMixPlaylist()
