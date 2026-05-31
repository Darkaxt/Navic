package paige.navic.domain.models

import androidx.compose.runtime.Immutable

@Immutable
data class AurralArtistEnrichment(
	val artistMbid: String,
	val artistName: String,
	val releaseGroups: List<AurralReleaseGroup> = emptyList(),
	val previewTracks: List<AurralPreviewTrack> = emptyList(),
	val similarArtists: List<AurralSimilarArtist> = emptyList(),
	val requests: List<AurralAlbumRequest> = emptyList()
)

@Immutable
data class AurralReleaseGroup(
	val id: String,
	val title: String,
	val firstReleaseDate: String? = null,
	val primaryType: String? = null,
	val secondaryTypes: List<String> = emptyList(),
	val coverUrl: String? = null
)

@Immutable
data class AurralPreviewTrack(
	val id: String,
	val title: String,
	val artist: String? = null,
	val album: String? = null,
	val previewUrl: String? = null,
	val durationMs: Long? = null
)

@Immutable
data class AurralSimilarArtist(
	val id: String,
	val name: String,
	val imageUrl: String? = null,
	val matchPercent: Int? = null
)

@Immutable
data class AurralAlbumRequest(
	val albumMbid: String? = null,
	val albumName: String? = null,
	val artistMbid: String? = null,
	val artistName: String? = null,
	val status: String? = null
)

@Immutable
data class AurralMissingAlbumRow(
	val releaseGroup: AurralReleaseGroup,
	val title: String,
	val year: String?,
	val coverUrl: String?,
	val requestStatus: String?,
	val requestable: Boolean,
	val acquisitionProgress: AurralAcquisitionProgress? = null
)

@Immutable
data class AurralSimilarArtistRow(
	val artist: AurralSimilarArtist,
	val localArtistId: String?,
	val inLibrary: Boolean,
	val matchPercent: Int?
)

@Immutable
data class AurralAcquisitionProgress(
	val status: String,
	val active: Boolean,
	val completed: Boolean,
	val failed: Boolean
)

fun aurralMissingAlbumRows(
	enrichment: AurralArtistEnrichment,
	localAlbums: List<DomainAlbum>
): List<AurralMissingAlbumRow> {
	val localMusicBrainzIds = localAlbums
		.mapNotNull { it.musicBrainzId.normalizedAurralIdOrNull() }
		.toSet()
	val localTitles = localAlbums
		.mapNotNull { it.name.normalizedAurralNameOrNull() }
		.toSet()
	val requestsByMusicBrainzId = enrichment.requests
		.mapNotNull { request ->
			request.albumMbid.normalizedAurralIdOrNull()?.let { it to request }
		}
		.toMap()
	val requestsByTitle = enrichment.requests
		.mapNotNull { request ->
			request.albumName.normalizedAurralNameOrNull()?.let { it to request }
		}
		.toMap()

	return enrichment.releaseGroups
		.filter { releaseGroup ->
			val musicBrainzId = releaseGroup.id.normalizedAurralIdOrNull()
			val title = releaseGroup.title.normalizedAurralNameOrNull()
			(musicBrainzId == null || musicBrainzId !in localMusicBrainzIds) &&
				(title == null || title !in localTitles)
		}
		.map { releaseGroup ->
			val request = releaseGroup.id.normalizedAurralIdOrNull()?.let(requestsByMusicBrainzId::get)
				?: releaseGroup.title.normalizedAurralNameOrNull()?.let(requestsByTitle::get)
			val status = request?.status?.trim()?.takeIf { it.isNotEmpty() }
			AurralMissingAlbumRow(
				releaseGroup = releaseGroup,
				title = releaseGroup.title,
				year = releaseGroup.firstReleaseDate?.take(4)?.takeIf { value ->
					value.length == 4 && value.all { it.isDigit() }
				},
				coverUrl = releaseGroup.coverUrl,
				requestStatus = status,
				requestable = status == null,
				acquisitionProgress = status?.let(::aurralAcquisitionProgress)
			)
		}
}

fun aurralAcquisitionProgress(status: String): AurralAcquisitionProgress {
	val normalized = status.trim().lowercase()
	val failed = normalized == "failed" || normalized.contains("fail") || normalized.contains("error")
	val completed = normalized == "available" || normalized == "added" || normalized == "completed"
	val active = !failed && !completed
	return AurralAcquisitionProgress(
		status = status,
		active = active,
		completed = completed,
		failed = failed
	)
}

fun aurralAlbumAcquisitionProgress(
	album: DomainAlbum,
	requests: List<AurralAlbumRequest>
): AurralAcquisitionProgress? =
	aurralAlbumAcquisitionProgress(
		albumMusicBrainzId = album.musicBrainzId,
		albumName = album.name,
		artistName = album.artistName,
		requests = requests
	)

fun aurralAlbumAcquisitionProgress(
	albumMusicBrainzId: String?,
	albumName: String,
	artistName: String,
	requests: List<AurralAlbumRequest>
): AurralAcquisitionProgress? {
	val normalizedAlbumMbid = albumMusicBrainzId.normalizedAurralIdOrNull()
	val normalizedAlbumName = albumName.normalizedAurralNameOrNull()
	val normalizedArtistName = artistName.normalizedAurralNameOrNull()
	return requests
		.mapNotNull { request ->
			val status = request.status?.trim()?.takeIf { it.isNotEmpty() }
				?: return@mapNotNull null
			val matchesMbid = normalizedAlbumMbid != null &&
				request.albumMbid.normalizedAurralIdOrNull() == normalizedAlbumMbid
			val matchesName = normalizedAlbumName != null &&
				normalizedArtistName != null &&
				request.albumName.normalizedAurralNameOrNull() == normalizedAlbumName &&
				request.artistName.normalizedAurralNameOrNull() == normalizedArtistName
			if (matchesMbid || matchesName) {
				aurralAcquisitionProgress(status)
			} else {
				null
			}
		}
		.sortedBy { progress ->
			when {
				progress.active -> 0
				progress.failed -> 1
				else -> 2
			}
		}
		.firstOrNull()
}

fun aurralSimilarArtistRows(
	enrichment: AurralArtistEnrichment,
	localArtists: List<DomainArtist>
): List<AurralSimilarArtistRow> {
	val localByMusicBrainzId = localArtists
		.mapNotNull { artist ->
			artist.musicBrainzId.normalizedAurralIdOrNull()?.let { it to artist.id }
		}
		.toMap()
	val localByName = localArtists
		.mapNotNull { artist ->
			artist.name.normalizedAurralNameOrNull()?.let { it to artist.id }
		}
		.toMap()

	return enrichment.similarArtists.map { artist ->
		val localArtistId = artist.id.normalizedAurralIdOrNull()?.let(localByMusicBrainzId::get)
			?: artist.name.normalizedAurralNameOrNull()?.let(localByName::get)
		AurralSimilarArtistRow(
			artist = artist,
			localArtistId = localArtistId,
			inLibrary = localArtistId != null,
			matchPercent = artist.matchPercent
		)
	}
}

private fun String?.normalizedAurralIdOrNull(): String? =
	this?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

private fun String?.normalizedAurralNameOrNull(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }
