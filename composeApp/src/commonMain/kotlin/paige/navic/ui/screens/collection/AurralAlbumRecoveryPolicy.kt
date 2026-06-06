package paige.navic.ui.screens.collection

import paige.navic.domain.models.DomainAlbum
import paige.navic.domain.models.DomainSong
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.models.aurralOwnershipStatusForStatus
import paige.navic.domain.repositories.AurralAlbumSearchItem
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.milliseconds

data class AurralAlbumRecoveryTrack(
	val id: String,
	val title: String,
	val recordingMbid: String? = null,
	val discNumber: Int? = null,
	val trackNumber: Int? = null,
	val durationMs: Long? = null,
	val previewUrl: String? = null,
	val status: String? = null,
	val requested: Boolean? = null
)

data class AurralAlbumRecoveryTrackRow(
	val track: AurralAlbumRecoveryTrack,
	val localSong: DomainSong?,
	val ownershipStatus: AurralOwnershipStatus
)

data class AurralAlbumRecoveryCandidateChoice(
	val album: AurralAlbumSearchItem,
	val confidence: Int
)

fun aurralAlbumRecoveryCandidate(
	album: DomainAlbum,
	candidates: List<AurralAlbumSearchItem>
): AurralAlbumSearchItem? =
	aurralAlbumRecoveryCandidateChoices(album, candidates)
		.firstOrNull { it.confidence >= 20 }
		?.album

fun aurralAlbumRecoveryCandidateChoices(
	album: DomainAlbum,
	candidates: List<AurralAlbumSearchItem>
): List<AurralAlbumRecoveryCandidateChoice> {
	val titleKey = album.name.normalizedAurralAlbumRecoveryKey()
	if (titleKey == null) return emptyList()
	val artistCreditKeys = aurralAlbumArtistCreditParts(album.artistName)
		.mapNotNull { it.normalizedAurralAlbumRecoveryKey() }
		.ifEmpty { listOfNotNull(album.artistName.normalizedAurralAlbumRecoveryKey()) }
	val albumYear = album.year
	return candidates
		.mapNotNull { candidate ->
			val candidateTitleKey = candidate.title.normalizedAurralAlbumRecoveryKey()
			if (candidateTitleKey != titleKey) return@mapNotNull null
			val candidateArtistKey = candidate.artistName.normalizedAurralAlbumRecoveryKey()
			val year = candidate.releaseDate.aurralRecoveryYearOrNull()
			val artistScore = when {
				candidateArtistKey != null && candidateArtistKey in artistCreditKeys -> 30
				candidateArtistKey != null && artistCreditKeys.any { it.contains(candidateArtistKey) } -> 20
				candidateArtistKey != null && artistCreditKeys.any { candidateArtistKey.contains(it) } -> 10
				else -> 0
			}
			val yearScore = when {
				albumYear != null && year == albumYear -> 20
				albumYear != null && year != null && (year - albumYear).absoluteValue <= 1 -> 10
				else -> 0
			}
			val typeScore = when {
				candidate.primaryType.equals("album", ignoreCase = true) -> 6
				else -> 0
			}
			val soundtrackScore = when {
				candidate.secondaryTypes.any { it.equals("soundtrack", ignoreCase = true) } -> 4
				else -> 0
			}
			val confidence = artistScore + yearScore + typeScore + soundtrackScore
			val score = 100 + confidence
			AurralAlbumRecoveryCandidateChoice(candidate, confidence) to score
		}
		.sortedWith(
			compareByDescending<Pair<AurralAlbumRecoveryCandidateChoice, Int>> { it.second }
				.thenByDescending { it.first.album.coverUrl?.isNotBlank() == true }
				.thenByDescending { it.first.album.releaseDate.orEmpty() }
		)
		.map { it.first }
}

fun aurralAlbumRecoveryQueries(album: DomainAlbum): List<String> {
	val title = album.name.trim().takeIf { it.isNotEmpty() } ?: return emptyList()
	val artists = aurralAlbumArtistCreditParts(album.artistName)
		.ifEmpty { listOf(album.artistName.trim()) }
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.distinctBy { it.normalizedAurralAlbumRecoveryKey() ?: it.lowercase() }
		.take(4)
	val year = album.year
	val queries = mutableListOf<String>()
	fun add(query: String) {
		val normalized = query.trim().replace(Regex("""\s+"""), " ")
		if (normalized.isNotEmpty()) queries += normalized
	}

	if (year != null) {
		artists.forEach { artist -> add("$title $artist $year") }
	}
	artists.forEach { artist -> add("$title $artist") }
	if (year != null) add("$title $year")
	add(title)

	return queries.distinctBy { it.normalizedAurralAlbumRecoveryKey() ?: it.lowercase() }
}

fun aurralAlbumArtistCreditParts(artistCredit: String?): List<String> {
	val normalized = artistCredit
		?.trim()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }
		?: return emptyList()
	return normalized
		.split(Regex("""\s+(?:&|and|feat\.?|featuring|with|x)\s+|[,;/]""", RegexOption.IGNORE_CASE))
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.distinctBy { it.normalizedAurralAlbumRecoveryKey() ?: it.lowercase() }
		.ifEmpty { listOf(normalized) }
}

fun aurralAlbumRecoveryRows(
	album: DomainAlbum,
	tracks: List<AurralAlbumRecoveryTrack>
): List<AurralAlbumRecoveryTrackRow> {
	val unmatchedSongs = album.songs.toMutableList()
	return tracks.map { track ->
		val localSong = bestLocalSongMatch(track, unmatchedSongs)
		if (localSong != null) unmatchedSongs.remove(localSong)
		AurralAlbumRecoveryTrackRow(
			track = track,
			localSong = localSong,
			ownershipStatus = when {
				localSong != null -> AurralOwnershipStatus.Owned
				track.requested == true -> AurralOwnershipStatus.Partial
				track.status != null -> aurralOwnershipStatusForStatus(track.status)
				else -> AurralOwnershipStatus.Missing
			}
		)
	}
}

private fun bestLocalSongMatch(
	track: AurralAlbumRecoveryTrack,
	songs: List<DomainSong>
): DomainSong? =
	track.recordingMbid.normalizedAurralAlbumRecoveryKey()?.let { trackMbid ->
		songs.firstOrNull { song ->
			song.musicBrainzId.normalizedAurralAlbumRecoveryKey() == trackMbid
		}
	} ?: songs.firstOrNull { song ->
		song.trackNumber == track.trackNumber &&
			(track.discNumber == null || song.discNumber == null || song.discNumber == track.discNumber) &&
			song.title.normalizedAurralAlbumRecoveryKey() == track.title.normalizedAurralAlbumRecoveryKey()
	} ?: songs.firstOrNull { song ->
		val songTitle = song.title.normalizedAurralAlbumRecoveryKey()
		val trackTitle = track.title.normalizedAurralAlbumRecoveryKey()
		songTitle != null &&
			songTitle == trackTitle &&
			track.durationMs?.let { (song.duration - it.milliseconds).absoluteValue <= 4_000.milliseconds } != false
	} ?: songs.firstOrNull { song ->
		val songTitle = song.title.normalizedAurralAlbumRecoveryKey()?.filter(Char::isLetterOrDigit)
		val trackTitle = track.title.normalizedAurralAlbumRecoveryKey()?.filter(Char::isLetterOrDigit)
		songTitle != null &&
			trackTitle != null &&
			songTitle.length >= 6 &&
			trackTitle.length >= 6 &&
			(songTitle.contains(trackTitle) || trackTitle.contains(songTitle))
	}

internal fun String?.normalizedAurralAlbumRecoveryKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""[^\p{L}\p{N}]+"""), " ")
		?.replace(Regex("""\s+"""), " ")
		?.trim()
		?.takeIf { it.isNotEmpty() }

private fun String?.aurralRecoveryYearOrNull(): Int? =
	this
		?.trim()
		?.take(4)
		?.takeIf { value -> value.length == 4 && value.all(Char::isDigit) }
		?.toIntOrNull()
