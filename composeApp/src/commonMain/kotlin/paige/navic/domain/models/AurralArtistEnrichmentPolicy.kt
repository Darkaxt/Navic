package paige.navic.domain.models

import androidx.compose.runtime.Immutable

@Immutable
data class AurralArtistEnrichment(
	val artistMbid: String,
	val artistName: String,
	val releaseGroups: List<AurralReleaseGroup> = emptyList(),
	val previewTracks: List<AurralPreviewTrack> = emptyList(),
	val similarArtists: List<AurralSimilarArtist> = emptyList(),
	val requests: List<AurralAlbumRequest> = emptyList(),
	val monitored: Boolean? = null
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
	val durationMs: Long? = null,
	val owned: Boolean? = null,
	val requested: Boolean? = null,
	val inLibrary: Boolean? = null,
	val status: String? = null
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
sealed interface AurralArtistAlbumRow {
	val title: String
	val year: Int?

	@Immutable
	data class Local(
		val album: DomainAlbum
	) : AurralArtistAlbumRow {
		override val title: String = album.name
		override val year: Int? = album.year
	}

	@Immutable
	data class Missing(
		val album: AurralMissingAlbumRow
	) : AurralArtistAlbumRow {
		override val title: String = album.title
		override val year: Int? = album.year.toAurralYearOrNull()
	}
}

@Immutable
data class AurralSimilarArtistRow(
	val artist: AurralSimilarArtist,
	val localArtistId: String?,
	val localCoverArtId: String? = null,
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

@Immutable
enum class AurralOwnershipStatus {
	Owned,
	Partial,
	Missing
}

fun aurralMissingAlbumRows(
	enrichment: AurralArtistEnrichment,
	localAlbums: List<DomainAlbum>
): List<AurralMissingAlbumRow> {
	val localMusicBrainzIds = localAlbums
		.mapNotNull { it.musicBrainzId.normalizedAurralIdOrNull() }
		.toSet()
	val localTitles = localAlbums
		.flatMap { it.name.normalizedAurralAlbumDedupeKeys() }
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
			val titleKeys = releaseGroup.title.normalizedAurralAlbumDedupeKeys()
			(musicBrainzId == null || musicBrainzId !in localMusicBrainzIds) &&
				titleKeys.none { it in localTitles }
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
		.sortedWith(
			compareBy<AurralMissingAlbumRow> { it.year.toAurralYearOrNull() == null }
				.thenByDescending { it.year.toAurralYearOrNull() ?: Int.MIN_VALUE }
				.thenBy { it.title.lowercase() }
		)
}

fun aurralArtistAlbumRows(
	localAlbums: List<DomainAlbum>,
	missingAlbums: List<AurralMissingAlbumRow>
): List<AurralArtistAlbumRow> {
	val localMusicBrainzIds = localAlbums
		.mapNotNull { it.musicBrainzId.normalizedAurralIdOrNull() }
		.toSet()
	val localTitleKeys = localAlbums
		.flatMap { it.name.normalizedAurralAlbumDedupeKeys() }
		.toSet()
	val seenMissingKeys = mutableSetOf<String>()
	val localRows = localAlbums.map(AurralArtistAlbumRow::Local)
	val missingRows = missingAlbums.mapNotNull { row ->
		val musicBrainzId = row.releaseGroup.id.normalizedAurralIdOrNull()
		val titleKeys = row.title.normalizedAurralAlbumDedupeKeys()
		val matchesLocal = (musicBrainzId != null && musicBrainzId in localMusicBrainzIds) ||
			titleKeys.any { it in localTitleKeys }
		if (matchesLocal) return@mapNotNull null

		val rowKeys = listOfNotNull(musicBrainzId?.let { "mbid:$it" }) +
			titleKeys.map { "title:$it" }
		if (rowKeys.any { it in seenMissingKeys }) return@mapNotNull null
		seenMissingKeys += rowKeys

		AurralArtistAlbumRow.Missing(row)
	}

	return (localRows + missingRows).sortedWith(
		compareBy<AurralArtistAlbumRow> { it.year == null }
			.thenByDescending { it.year ?: Int.MIN_VALUE }
			.thenBy { it.title.lowercase() }
			.thenBy { row -> if (row is AurralArtistAlbumRow.Local) 0 else 1 }
	)
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

fun aurralOwnershipStatusForProgress(progress: AurralAcquisitionProgress?): AurralOwnershipStatus =
	when {
		progress?.completed == true -> AurralOwnershipStatus.Owned
		progress?.active == true -> AurralOwnershipStatus.Partial
		else -> AurralOwnershipStatus.Missing
	}

fun aurralOwnershipStatusForStatus(status: String?): AurralOwnershipStatus {
	val normalized = status?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
		?: return AurralOwnershipStatus.Missing
	return when {
		normalized == "available" ||
			normalized == "added" ||
			normalized == "completed" ||
			normalized == "downloaded" ||
			normalized == "owned" ||
			normalized == "in_library" ||
			normalized == "in library" -> AurralOwnershipStatus.Owned
		normalized == "partial" ||
			normalized == "partially_owned" ||
			normalized == "partially owned" ||
			normalized == "requested" ||
			normalized == "queued" ||
			normalized == "pending" ||
			normalized == "processing" ||
			normalized == "downloading" ||
			normalized == "searching" -> AurralOwnershipStatus.Partial
		else -> AurralOwnershipStatus.Missing
	}
}

fun aurralPreviewTrackOwnershipStatus(
	track: AurralPreviewTrack,
	fallbackAlbumStatus: AurralOwnershipStatus? = null
): AurralOwnershipStatus =
	when {
		track.owned == true || track.inLibrary == true -> AurralOwnershipStatus.Owned
		track.requested == true -> AurralOwnershipStatus.Partial
		track.status != null -> aurralOwnershipStatusForStatus(track.status)
		fallbackAlbumStatus != null -> fallbackAlbumStatus
		else -> AurralOwnershipStatus.Missing
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
): List<AurralSimilarArtistRow> =
	aurralSimilarArtistRows(
		enrichment = enrichment,
		allLocalArtists = localArtists,
		localSimilarArtists = emptyList()
	)

fun aurralSimilarArtistRows(
	enrichment: AurralArtistEnrichment,
	allLocalArtists: List<DomainArtist>,
	localSimilarArtists: List<DomainArtist>
): List<AurralSimilarArtistRow> {
	val localByMusicBrainzId = allLocalArtists
		.mapNotNull { artist ->
			artist.musicBrainzId.normalizedAurralIdOrNull()?.let { it to artist }
		}
		.toMap()
	val localByName = allLocalArtists
		.mapNotNull { artist ->
			artist.name.normalizedAurralNameOrNull()?.let { it to artist }
		}
		.toMap()
	val seenKeys = mutableSetOf<String>()

	val aurralRows = enrichment.similarArtists.map { artist ->
		val localArtist = artist.id.normalizedAurralIdOrNull()?.let(localByMusicBrainzId::get)
			?: artist.name.normalizedAurralNameOrNull()?.let(localByName::get)
		val rowArtist = artist.copy(imageUrl = artist.imageUrl ?: localArtist?.artistImageUrl)
		localArtist?.id?.let { seenKeys += "local:$it" }
		artist.id.normalizedAurralIdOrNull()?.let { seenKeys += "mbid:$it" }
		artist.name.normalizedAurralNameOrNull()?.let { seenKeys += "name:$it" }
		AurralSimilarArtistRow(
			artist = rowArtist,
			localArtistId = localArtist?.id,
			localCoverArtId = localArtist?.coverArtId,
			inLibrary = localArtist != null,
			matchPercent = artist.matchPercent
		)
	}

	val localRows = localSimilarArtists.mapNotNull { artist ->
		val alreadySeen = listOfNotNull(
			"local:${artist.id}",
			artist.musicBrainzId.normalizedAurralIdOrNull()?.let { "mbid:$it" },
			artist.name.normalizedAurralNameOrNull()?.let { "name:$it" }
		).any { it in seenKeys }
		if (alreadySeen) return@mapNotNull null
		artist.musicBrainzId.normalizedAurralIdOrNull()?.let { seenKeys += "mbid:$it" }
		artist.name.normalizedAurralNameOrNull()?.let { seenKeys += "name:$it" }
		seenKeys += "local:${artist.id}"
		AurralSimilarArtistRow(
			artist = AurralSimilarArtist(
				id = artist.musicBrainzId ?: artist.id,
				name = artist.name,
				imageUrl = artist.artistImageUrl,
				matchPercent = null
			),
			localArtistId = artist.id,
			localCoverArtId = artist.coverArtId,
			inLibrary = true,
			matchPercent = null
		)
	}

	return aurralRows + localRows
}

fun aurralPreviewTracksForReleaseGroup(
	releaseGroup: AurralReleaseGroup,
	tracks: List<AurralPreviewTrack>
): List<AurralPreviewTrack> {
	val releaseTitle = releaseGroup.title.normalizedAurralAlbumTitleOrNull() ?: return emptyList()
	return tracks.filter { track ->
		val albumTitle = track.album.normalizedAurralAlbumTitleOrNull() ?: return@filter false
		albumTitle == releaseTitle
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

private fun String?.normalizedAurralAlbumTitleOrNull(): String? =
	normalizedAurralNameOrNull()
		?.replace(Regex("""\s*[\(\[](deluxe|expanded|bonus|remaster|anniversary|edition).*$"""), "")
		?.trim()
		?.takeIf { it.isNotEmpty() }

private fun String?.normalizedAurralAlbumDedupeKeys(): Set<String> {
	val normalized = normalizedAurralAlbumTitleOrNull() ?: return emptySet()
	val compact = normalized.filter { it.isLetterOrDigit() }
	return setOf(normalized, compact)
		.filter { it.isNotEmpty() }
		.toSet()
}

private fun String?.toAurralYearOrNull(): Int? =
	this
		?.trim()
		?.take(4)
		?.takeIf { value -> value.length == 4 && value.all { it.isDigit() } }
		?.toIntOrNull()
