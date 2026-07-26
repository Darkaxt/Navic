package paige.navic.domain.models

import kotlin.math.abs

enum class StalePlaybackMatchStrength {
	MusicBrainz,
	Isrc,
	ExactMetadata
}

sealed interface StalePlaybackSongResolution {
	data object Current : StalePlaybackSongResolution
	data object Missing : StalePlaybackSongResolution
	data object Ambiguous : StalePlaybackSongResolution
	data class Replacement(
		val song: DomainSong,
		val strength: StalePlaybackMatchStrength
	) : StalePlaybackSongResolution
}

fun shouldProbeStalePlaybackSong(
	errorCodeName: String,
	usesLocalFile: Boolean
): Boolean =
	!usesLocalFile && errorCodeName in setOf(
		"ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED",
		"ERROR_CODE_PARSING_CONTAINER_MALFORMED"
	)

fun resolveStalePlaybackSong(
	staleSong: DomainSong,
	currentSongs: List<DomainSong>
): StalePlaybackSongResolution {
	if (currentSongs.any { song -> song.id == staleSong.id }) {
		return StalePlaybackSongResolution.Current
	}

	val candidates = currentSongs.filterNot { song -> song.id == staleSong.id }
	val musicBrainzId = staleSong.musicBrainzId.normalizedIdentity()
	if (musicBrainzId != null) {
		resolveUnique(
			candidates.filter { song -> song.musicBrainzId.normalizedIdentity() == musicBrainzId },
			StalePlaybackMatchStrength.MusicBrainz
		)?.let { return it }
	}

	val staleIsrcs = staleSong.isrc.mapNotNull(String::normalizedIdentity).toSet()
	if (staleIsrcs.isNotEmpty()) {
		resolveUnique(
			candidates.filter { song ->
				song.isrc.mapNotNull(String::normalizedIdentity).any(staleIsrcs::contains)
			},
			StalePlaybackMatchStrength.Isrc
		)?.let { return it }
	}

	val staleTitle = staleSong.title.normalizedIdentity()
	val staleArtist = staleSong.artistName.normalizedIdentity()
	val staleAlbum = staleSong.albumTitle.normalizedIdentity()
	if (
		staleTitle != null &&
		staleArtist != null &&
		staleAlbum != null &&
		staleSong.trackNumber != null
	) {
		resolveUnique(
			candidates.filter { song ->
				song.title.normalizedIdentity() == staleTitle &&
					song.artistName.normalizedIdentity() == staleArtist &&
					song.albumTitle.normalizedIdentity() == staleAlbum &&
					song.trackNumber == staleSong.trackNumber &&
					song.discNumber == staleSong.discNumber &&
					abs(song.duration.inWholeMilliseconds - staleSong.duration.inWholeMilliseconds) <=
						STALE_SONG_DURATION_TOLERANCE_MS
			},
			StalePlaybackMatchStrength.ExactMetadata
		)?.let { return it }
	}

	return StalePlaybackSongResolution.Missing
}

private fun resolveUnique(
	matches: List<DomainSong>,
	strength: StalePlaybackMatchStrength
): StalePlaybackSongResolution? =
	when (matches.size) {
		0 -> null
		1 -> StalePlaybackSongResolution.Replacement(matches.single(), strength)
		else -> StalePlaybackSongResolution.Ambiguous
	}

private fun String?.normalizedIdentity(): String? =
	this
		?.trim()
		?.lowercase()
		?.split(Regex("\\s+"))
		?.filter(String::isNotEmpty)
		?.joinToString(" ")
		?.takeIf(String::isNotEmpty)

private const val STALE_SONG_DURATION_TOLERANCE_MS = 2_000L
