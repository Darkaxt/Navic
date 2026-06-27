package paige.navic.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class AurralArtistEnrichment(
	val artistMbid: String,
	val artistName: String,
	val imageUrl: String? = null,
	val bio: String? = null,
	val genres: List<String> = emptyList(),
	val externalLinks: List<AurralArtistExternalLink> = emptyList(),
	val releaseGroups: List<AurralReleaseGroup> = emptyList(),
	val previewTracks: List<AurralPreviewTrack> = emptyList(),
	val similarArtists: List<AurralSimilarArtist> = emptyList(),
	val requests: List<AurralAlbumRequest> = emptyList(),
	val monitored: Boolean? = null
)

@Immutable
@Serializable
data class AurralArtistExternalLink(
	val type: String,
	val url: String
)

@Immutable
@Serializable
data class AurralReleaseGroup(
	val id: String,
	val title: String,
	val firstReleaseDate: String? = null,
	val primaryType: String? = null,
	val secondaryTypes: List<String> = emptyList(),
	val coverUrl: String? = null
)

@Immutable
@Serializable
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
@Serializable
data class AurralSimilarArtist(
	val id: String,
	val name: String,
	val imageUrl: String? = null,
	val matchPercent: Int? = null
)

@Immutable
@Serializable
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
	val acquisitionProgress: AurralAcquisitionProgress? = null,
	val ownershipStatus: AurralOwnershipStatus = aurralOwnershipStatusForProgress(acquisitionProgress)
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
	Requested,
	Processing,
	Failed,
	Missing
}

@Immutable
data class AurralArtistOwnershipAlbumRows(
	val ownedOrPartial: List<AurralArtistOwnershipAlbumRow>,
	val missing: List<AurralArtistOwnershipAlbumRow>
)

@Immutable
data class AurralReleaseGroupTrackEvidence(
	val title: String,
	val recordingMbid: String? = null
)

@Immutable
data class AurralArtistOwnershipAlbumRow(
	val releaseGroup: AurralReleaseGroup?,
	val localAlbum: DomainAlbum?,
	val title: String,
	val year: String?,
	val coverUrl: String?,
	val requestStatus: String?,
	val requestable: Boolean,
	val acquisitionProgress: AurralAcquisitionProgress? = null,
	val ownershipStatus: AurralOwnershipStatus,
	val localSongs: List<DomainSong> = emptyList()
)

fun aurralArtistOwnershipAlbumRows(
	enrichment: AurralArtistEnrichment,
	localAlbums: List<DomainAlbum>,
	releaseGroupTrackEvidence: Map<String, List<AurralReleaseGroupTrackEvidence>> = emptyMap()
): AurralArtistOwnershipAlbumRows {
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
	val matchedReleaseGroupIds = mutableSetOf<String>()
	val ownedOrPartial = localAlbums.map { album ->
		val match = enrichment.releaseGroups
			.mapNotNull { releaseGroup ->
				val directScore = localAlbumReleaseGroupMatchScore(album, releaseGroup)
				val releaseGroupTracks = releaseGroup.id.normalizedAurralIdOrNull()
					?.let(releaseGroupTrackEvidence::get)
					.orEmpty()
				val trackEvidenceScore = localAlbumReleaseGroupTrackEvidenceMatchScore(
					album = album,
					releaseGroup = releaseGroup,
					tracks = releaseGroupTracks
				)
				listOfNotNull(directScore, trackEvidenceScore).maxOrNull()?.let { score -> score to releaseGroup }
			}
			.maxWithOrNull(
				compareBy<Pair<Int, AurralReleaseGroup>> { it.first }
					.thenByDescending { it.second.firstReleaseDate.toAurralYearOrNull() ?: Int.MIN_VALUE }
			)
			?.second
		match?.id?.normalizedAurralIdOrNull()?.let(matchedReleaseGroupIds::add)
		val request = match?.let { releaseGroup ->
			releaseGroup.id.normalizedAurralIdOrNull()?.let(requestsByMusicBrainzId::get)
				?: releaseGroup.title.normalizedAurralNameOrNull()?.let(requestsByTitle::get)
		}
		val progress = request?.status?.trim()?.takeIf { it.isNotEmpty() }?.let(::aurralAcquisitionProgress)
		val hasMultipleDistinctLocalTracks = album.hasMultipleDistinctAurralTracks()
		val ownershipStatus = when {
			progress != null -> aurralOwnershipStatusForProgress(progress)
			match == null -> if (hasMultipleDistinctLocalTracks) {
				AurralOwnershipStatus.Owned
			} else {
				AurralOwnershipStatus.Partial
			}
			localAlbumReleaseGroupMatchScore(album, match).isExactAurralAlbumMatchScore() &&
				hasMultipleDistinctLocalTracks -> AurralOwnershipStatus.Owned
			else -> AurralOwnershipStatus.Partial
		}
		AurralArtistOwnershipAlbumRow(
			releaseGroup = match,
			localAlbum = album,
			title = match?.title ?: album.name,
			year = match?.firstReleaseDate?.take(4)?.takeIf { value ->
				value.length == 4 && value.all { it.isDigit() }
			} ?: album.year?.toString(),
			coverUrl = match?.coverUrl,
			requestStatus = request?.status,
			requestable = false,
			acquisitionProgress = progress,
			ownershipStatus = ownershipStatus,
			localSongs = album.songs
		)
	}.sortedWith(aurralArtistOwnershipRowComparator())

	val missing = enrichment.releaseGroups
		.filter { releaseGroup ->
			releaseGroup.id.normalizedAurralIdOrNull()?.let { it !in matchedReleaseGroupIds } != false
		}
		.map { releaseGroup ->
			val request = releaseGroup.id.normalizedAurralIdOrNull()?.let(requestsByMusicBrainzId::get)
				?: releaseGroup.title.normalizedAurralNameOrNull()?.let(requestsByTitle::get)
			val status = request?.status?.trim()?.takeIf { it.isNotEmpty() }
			val progress = status?.let(::aurralAcquisitionProgress)
			AurralArtistOwnershipAlbumRow(
				releaseGroup = releaseGroup,
				localAlbum = null,
				title = releaseGroup.title,
				year = releaseGroup.firstReleaseDate?.take(4)?.takeIf { value ->
					value.length == 4 && value.all { it.isDigit() }
				},
				coverUrl = releaseGroup.coverUrl,
				requestStatus = status,
				requestable = status == null,
				acquisitionProgress = progress,
				ownershipStatus = aurralOwnershipStatusForProgress(progress),
				localSongs = emptyList()
			)
		}
		.sortedWith(aurralArtistOwnershipRowComparator())

	return AurralArtistOwnershipAlbumRows(
		ownedOrPartial = ownedOrPartial,
		missing = missing
	)
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
				acquisitionProgress = status?.let(::aurralAcquisitionProgress),
				ownershipStatus = aurralOwnershipStatusForStatus(status)
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
	val completed = normalized == "available" ||
		normalized == "added" ||
		normalized == "completed" ||
		normalized == "downloaded" ||
		normalized == "owned" ||
		normalized == "in_library" ||
		normalized == "in library"
	val active = !failed && !completed
	return AurralAcquisitionProgress(
		status = status,
		active = active,
		completed = completed,
		failed = failed
	)
}

fun aurralOwnershipStatusForProgress(progress: AurralAcquisitionProgress?): AurralOwnershipStatus =
	progress?.status?.let(::aurralOwnershipStatusForStatus) ?: AurralOwnershipStatus.Missing

fun aurralOwnershipStatusForStatus(status: String?): AurralOwnershipStatus {
	val normalized = status?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
		?: return AurralOwnershipStatus.Missing
	return when {
		normalized.contains("fail") ||
			normalized.contains("error") -> AurralOwnershipStatus.Failed
		normalized == "available" ||
			normalized == "added" ||
			normalized == "completed" ||
			normalized == "downloaded" ||
			normalized == "owned" ||
			normalized == "in_library" ||
			normalized == "in library" -> AurralOwnershipStatus.Owned
		normalized == "partial" ||
			normalized == "partially_owned" ||
			normalized == "partially owned" -> AurralOwnershipStatus.Partial
		normalized == "requested" ||
			normalized == "queued" ||
			normalized == "pending" -> AurralOwnershipStatus.Requested
		normalized == "processing" ||
			normalized == "downloading" ||
			normalized == "searching" -> AurralOwnershipStatus.Processing
		else -> AurralOwnershipStatus.Missing
	}
}

fun aurralPreviewTrackOwnershipStatus(
	track: AurralPreviewTrack,
	fallbackAlbumStatus: AurralOwnershipStatus? = null
): AurralOwnershipStatus =
	when {
		track.owned == true || track.inLibrary == true -> AurralOwnershipStatus.Owned
		track.requested == true -> AurralOwnershipStatus.Requested
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
	localSimilarArtists: List<DomainArtist>,
	externalArtists: List<AurralSimilarArtist> = emptyList()
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
	val externalByMusicBrainzId = externalArtists
		.mapNotNull { artist ->
			artist.id.normalizedAurralIdOrNull()?.let { it to artist }
		}
		.toMap()
	val externalByName = externalArtists
		.mapNotNull { artist ->
			artist.name.normalizedAurralNameOrNull()?.let { it to artist }
		}
		.toMap()
	val seenKeys = mutableSetOf<String>()

	val aurralRows = enrichment.similarArtists.map { artist ->
		val localArtist = artist.id.normalizedAurralIdOrNull()?.let(localByMusicBrainzId::get)
			?: artist.name.normalizedAurralNameOrNull()?.let(localByName::get)
		val externalArtist = artist.id.normalizedAurralIdOrNull()?.let(externalByMusicBrainzId::get)
			?: artist.name.normalizedAurralNameOrNull()?.let(externalByName::get)
		val rowArtist = artist.copy(
			imageUrl = preferredAurralArtworkUrl(
				primary = artist.imageUrl,
				fallback = externalArtist?.imageUrl
			)
				?: localArtist?.artistImageUrl.externalAurralArtworkUrlOrNull()
		)
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
		val externalArtist = artist.musicBrainzId.normalizedAurralIdOrNull()
			?.let(externalByMusicBrainzId::get)
			?: artist.name.normalizedAurralNameOrNull()?.let(externalByName::get)
		artist.musicBrainzId.normalizedAurralIdOrNull()?.let { seenKeys += "mbid:$it" }
		artist.name.normalizedAurralNameOrNull()?.let { seenKeys += "name:$it" }
		seenKeys += "local:${artist.id}"
		AurralSimilarArtistRow(
			artist = AurralSimilarArtist(
				id = artist.musicBrainzId ?: artist.id,
				name = artist.name,
				imageUrl = preferredAurralArtworkUrl(
					primary = externalArtist?.imageUrl,
					fallback = artist.artistImageUrl
				),
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

private fun preferredAurralArtworkUrl(
	primary: String?,
	fallback: String?
): String? {
	val primaryUrl = primary.externalAurralArtworkUrlOrNull()
	val fallbackUrl = fallback.externalAurralArtworkUrlOrNull()
	return when {
		primaryUrl == null -> fallbackUrl
		fallbackUrl == null -> primaryUrl
		else -> primaryUrl
	}
}

private fun String?.externalAurralArtworkUrlOrNull(): String? {
	val url = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	return url.takeUnless { it.isNavidromeArtworkUrl() }
}

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

private const val ExactMbidMatchScore = 1000
private const val ExactTitleMatchScore = 900

private fun Int?.isExactAurralAlbumMatchScore(): Boolean =
	this != null && this >= ExactTitleMatchScore

private fun DomainAlbum.hasMultipleDistinctAurralTracks(): Boolean {
	val distinctLocalTrackTitles = songs
		.mapNotNull { song -> song.title.normalizedAurralTrackTitleOrNull() }
		.toSet()
	return if (distinctLocalTrackTitles.isNotEmpty()) {
		distinctLocalTrackTitles.size > 1
	} else {
		songCount > 1
	}
}

private fun localAlbumReleaseGroupMatchScore(
	album: DomainAlbum,
	releaseGroup: AurralReleaseGroup
): Int? {
	val localMbid = album.musicBrainzId.normalizedAurralIdOrNull()
	val releaseMbid = releaseGroup.id.normalizedAurralIdOrNull()
	if (localMbid != null && localMbid == releaseMbid) return ExactMbidMatchScore

	val localTitle = album.name.normalizedAurralAlbumTitleOrNull() ?: return null
	val releaseTitle = releaseGroup.title.normalizedAurralAlbumTitleOrNull() ?: return null
	val yearBonus = if (album.year != null && album.year == releaseGroup.firstReleaseDate.toAurralYearOrNull()) 50 else 0
	if (localTitle == releaseTitle) return ExactTitleMatchScore + yearBonus
	val localCompact = localTitle.filter { it.isLetterOrDigit() }
	val releaseCompact = releaseTitle.filter { it.isLetterOrDigit() }
	if (localCompact.isNotEmpty() && releaseCompact.isNotEmpty()) {
		if (localCompact == releaseCompact) return ExactTitleMatchScore - 10 + yearBonus
		if (localCompact.contains(releaseCompact) || releaseCompact.contains(localCompact)) return 800 + yearBonus
	}

	val localTokens = localTitle.normalizedAurralAlbumMatchTokens()
	val releaseTokens = releaseTitle.normalizedAurralAlbumMatchTokens()
	val common = localTokens.intersect(releaseTokens)
	val commonSignificantLength = common.sumOf(String::length)
	return when {
		common.size >= 4 && commonSignificantLength >= 16 -> 700 + commonSignificantLength + yearBonus
		common.size >= 3 && yearBonus > 0 -> 650 + commonSignificantLength + yearBonus
		else -> null
	}
}

private fun localAlbumReleaseGroupTrackEvidenceMatchScore(
	album: DomainAlbum,
	releaseGroup: AurralReleaseGroup,
	tracks: List<AurralReleaseGroupTrackEvidence>
): Int? {
	if (tracks.isEmpty() || !localAlbumLooksLikeReleaseGroupEdition(album, releaseGroup)) return null
	val localTrackTitles = album.songs
		.mapNotNull { song -> song.title.normalizedAurralTrackTitleOrNull() }
		.toSet()
	if (localTrackTitles.isEmpty()) return null
	val releaseTrackTitles = tracks
		.mapNotNull { track -> track.title.normalizedAurralTrackTitleOrNull() }
		.toSet()
	val matchedTitles = localTrackTitles.intersect(releaseTrackTitles)
	if (matchedTitles.isEmpty()) return null
	val yearBonus = if (album.year != null && album.year == releaseGroup.firstReleaseDate.toAurralYearOrNull()) 50 else 0
	val soundtrackBonus = if (releaseGroup.isSoundtrackReleaseGroup()) 40 else 0
	val evidenceStrength = matchedTitles.sumOf { title -> title.length.coerceAtMost(30) }
	return 580 + evidenceStrength + yearBonus + soundtrackBonus
}

private fun localAlbumLooksLikeReleaseGroupEdition(
	album: DomainAlbum,
	releaseGroup: AurralReleaseGroup
): Boolean {
	val localTitle = album.name.normalizedAurralAlbumTitleOrNull() ?: return false
	val releaseTitle = releaseGroup.title.normalizedAurralAlbumTitleOrNull() ?: return false
	val localTokens = localTitle.normalizedAurralAlbumMatchTokens()
	val releaseTokens = releaseTitle.normalizedAurralAlbumMatchTokens()
	if (localTokens.intersect(releaseTokens).isNotEmpty()) return true

	val localEditionTokens = localTitle.normalizedAurralAlbumEditionTokens()
	return localEditionTokens.isNotEmpty() && releaseGroup.isSoundtrackReleaseGroup()
}

private fun String.normalizedAurralAlbumMatchTokens(): Set<String> =
	split(Regex("""[^a-z0-9]+"""))
		.mapNotNull { token ->
			token.trim()
				.takeIf { it.length >= 3 }
				?.takeUnless { it in AurralAlbumMatchStopWords }
		}
		.toSet()

private fun String.normalizedAurralAlbumEditionTokens(): Set<String> =
	normalizedAurralCueTokens()
		.filter { it in AurralAlbumEditionEvidenceWords }
		.toSet()

private fun String?.normalizedAurralTrackTitleOrNull(): String? =
	normalizedAurralNameOrNull()
		?.replace(Regex("""\s*[\(\[](feat\.?|ft\.?|with|live|remaster|remastered).*$"""), "")
		?.trim()
		?.takeIf { it.isNotEmpty() }

private fun AurralReleaseGroup.isSoundtrackReleaseGroup(): Boolean {
	val releaseTypeTokens = (listOfNotNull(primaryType) + secondaryTypes)
		.flatMap { type -> type.normalizedAurralCueTokens() }
		.toSet()
	val releaseTitleTokens = title.normalizedAurralCueTokens()
	return "soundtrack" in releaseTypeTokens ||
		"score" in releaseTypeTokens ||
		"soundtrack" in releaseTitleTokens ||
		"score" in releaseTitleTokens ||
		("motion" in releaseTitleTokens && "picture" in releaseTitleTokens)
}

private fun String.normalizedAurralCueTokens(): Set<String> =
	split(Regex("""[^a-z0-9]+"""))
		.mapNotNull { token ->
			token.trim().takeIf { it.length >= 3 }
		}
		.toSet()

private val AurralAlbumMatchStopWords = setOf(
	"the",
	"and",
	"for",
	"from",
	"with",
	"music",
	"motion",
	"picture",
	"original",
	"score",
	"soundtrack",
	"consideration",
	"album"
)

private val AurralAlbumEditionEvidenceWords = setOf(
	"consideration",
	"score",
	"soundtrack",
	"ost",
	"edition",
	"complete",
	"expanded",
	"disc",
	"bonus"
)

private fun aurralArtistOwnershipRowComparator(): Comparator<AurralArtistOwnershipAlbumRow> =
	compareBy<AurralArtistOwnershipAlbumRow> { it.year.toAurralYearOrNull() == null }
		.thenByDescending { it.year.toAurralYearOrNull() ?: Int.MIN_VALUE }
		.thenBy { it.title.lowercase() }
