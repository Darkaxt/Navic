package paige.navic.ui.screens.artist

import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.LastFmTopTrack

fun shouldApplyLastFmTopTrackResult(
	lastFmEnabled: Boolean,
	lastFmApiKey: String,
	currentArtistId: String,
	resultArtistId: String
): Boolean =
	lastFmEnabled &&
		lastFmApiKey.isNotBlank() &&
		currentArtistId == resultArtistId

fun artistLastFmTopTrackSongs(
	tracks: List<LastFmTopTrack>,
	localSongs: List<DomainSong>,
	limit: Int = 12
): List<DomainSong> {
	val songsByTitle = localSongs
		.groupBy { it.title.normalizedTopTrackTitle() }
		.filterKeys { it.isNotEmpty() }
		.mapValues { (_, songs) ->
			songs.maxWithOrNull(
				compareBy<DomainSong> { it.playCount }
					.thenBy { it.albumTitle.orEmpty() }
					.thenBy { it.id }
			)
		}

	val seenTrackNames = mutableSetOf<String>()
	val seenSongIds = mutableSetOf<String>()
	return tracks
		.sortedBy { it.rank }
		.mapNotNull { track ->
			val key = track.name.normalizedTopTrackTitle()
			if (key.isEmpty() || !seenTrackNames.add(key)) return@mapNotNull null
			val song = songsByTitle[key] ?: return@mapNotNull null
			if (!seenSongIds.add(song.id)) return@mapNotNull null
			song
		}
		.take(limit.coerceAtLeast(0))
}

private fun String.normalizedTopTrackTitle(): String =
	lowercase()
		.replace(Regex("""\s+"""), " ")
		.trim()
