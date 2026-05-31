package paige.navic.domain.models

private const val StationPlaylistPrefix = "[A] "

fun DomainPlaylist.isStationPlaylist(): Boolean =
	name.startsWith(StationPlaylistPrefix)

fun DomainPlaylist.stationDisplayName(): String {
	if (!isStationPlaylist()) return name
	return name.removePrefix(StationPlaylistPrefix)
		.trimStart()
		.ifBlank { name }
}

fun DomainSongCollection.displayName(): String =
	when (this) {
		is DomainPlaylist -> stationDisplayName()
		else -> name
	}

fun List<DomainPlaylist>.stationPlaylists(): List<DomainPlaylist> =
	filter { it.isStationPlaylist() }

fun List<DomainPlaylist>.regularPlaylists(): List<DomainPlaylist> =
	filterNot { it.isStationPlaylist() }

fun canDeletePlaylistFromDetail(playlist: DomainPlaylist): Boolean =
	!playlist.isStationPlaylist() && playlist.readOnly != true
