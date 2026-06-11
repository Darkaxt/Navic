package paige.navic.domain.repositories

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import paige.navic.domain.models.AurralAlbumRequest
import paige.navic.domain.models.AurralArtistEnrichment
import paige.navic.domain.models.AurralFlowSongIdPrefix
import paige.navic.domain.models.AurralPreviewTrack
import paige.navic.domain.models.AurralReleaseGroup
import paige.navic.domain.models.AurralSimilarArtist
import paige.navic.domain.models.DomainExplicitStatus
import paige.navic.domain.models.DomainSong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

@Serializable
internal data class AurralHealthDto(
	val status: String = "unknown",
	@SerialName("appVersion") val appVersion: String? = null,
	@SerialName("authRequired") val authRequired: Boolean = false,
	@SerialName("lidarrConfigured") val lidarrConfigured: Boolean = false,
	@SerialName("rootFolderConfigured") val rootFolderConfigured: Boolean = false,
	val discovery: AurralDiscoveryDto? = null,
	val user: AurralUserDto? = null
)

@Serializable
internal data class AurralDiscoveryDto(
	@SerialName("recommendationsCount") val recommendationsCount: Int = 0,
	@SerialName("isUpdating") val isUpdating: Boolean = false
)

@Serializable
internal data class AurralDiscoveryResponseDto(
	val recommendations: List<AurralDiscoverArtistDto> = emptyList(),
	@SerialName("globalTop") val globalTop: List<AurralDiscoverArtistDto> = emptyList(),
	@SerialName("basedOn") val basedOn: List<AurralDiscoverArtistDto> = emptyList(),
	@SerialName("fallbackGenres") val fallbackGenres: List<AurralFallbackGenreSectionDto> = emptyList(),
	@SerialName("topTags") val topTags: List<String> = emptyList(),
	@SerialName("topGenres") val topGenres: List<String> = emptyList(),
	@SerialName("isUpdating") val isUpdating: Boolean = false,
	val stale: Boolean = false,
	val provider: String? = null,
	@SerialName("discoveryMode") val discoveryMode: String? = null
)

@Serializable
internal data class AurralFallbackGenreSectionDto(
	val genre: String? = null,
	val name: String? = null,
	val title: String? = null,
	val artists: List<AurralDiscoverArtistDto> = emptyList()
)

@Serializable
internal data class AurralArtistSearchResponseDto(
	val artists: List<AurralDiscoverArtistDto> = emptyList(),
	val count: Int = 0,
	val offset: Int = 0
)

@Serializable
internal data class AurralAlbumSearchResponseDto(
	val query: String? = null,
	val count: Int = 0,
	val offset: Int = 0,
	@SerialName("hasMore") val hasMore: Boolean = false,
	val items: List<AurralAlbumSearchItemDto> = emptyList()
)

@Serializable
internal data class AurralAlbumSearchItemDto(
	val id: String? = null,
	val mbid: String? = null,
	@SerialName("foreignAlbumId") val foreignAlbumId: String? = null,
	val title: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	@SerialName("foreignArtistId") val foreignArtistId: String? = null,
	@SerialName("releaseDate") val releaseDate: String? = null,
	@SerialName("primaryType") val primaryType: String? = null,
	@SerialName("secondaryTypes") val secondaryTypes: List<String> = emptyList(),
	@SerialName("coverUrl") val coverUrl: String? = null,
	@SerialName("inLibrary") val inLibrary: Boolean = false,
	@SerialName("libraryAlbumId") val libraryAlbumId: String? = null,
	@SerialName("libraryArtistId") val libraryArtistId: String? = null,
	val status: String? = null
)

@Serializable
internal data class AurralAlbumTracksResponseDto(
	val tracks: List<AurralAlbumTrackDto> = emptyList()
)

@Serializable
internal data class AurralAlbumTrackDto(
	val id: String? = null,
	val mbid: String? = null,
	@SerialName("recordingMbid") val recordingMbid: String? = null,
	val title: String? = null,
	val artist: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	@SerialName("artistCredit") val artistCredit: String? = null,
	@SerialName("trackName") val trackName: String? = null,
	@SerialName("recordingTitle") val recordingTitle: String? = null,
	@SerialName("discNumber") val discNumber: Int? = null,
	@SerialName("mediumNumber") val mediumNumber: Int? = null,
	@SerialName("trackNumber") val trackNumber: Int? = null,
	val position: Int? = null,
	@SerialName("absoluteTrackNumber") val absoluteTrackNumber: Int? = null,
	val length: Long? = null,
	@SerialName("durationMs") val durationMs: Long? = null,
	@SerialName("duration_ms") val durationMsSnake: Long? = null,
	@SerialName("previewUrl") val previewUrl: String? = null,
	@SerialName("preview_url") val previewUrlSnake: String? = null,
	val requested: Boolean? = null,
	val status: String? = null
)

@Serializable
internal data class AurralDiscoverArtistDto(
	val id: String? = null,
	val mbid: String? = null,
	@SerialName("foreignArtistId") val foreignArtistId: String? = null,
	@SerialName("foreignAlbumId") val foreignAlbumId: String? = null,
	val name: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	val title: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	val type: String? = null,
	val image: String? = null,
	val imageUrl: String? = null,
	@SerialName("coverUrl") val coverUrl: String? = null,
	@SerialName("releaseDate") val releaseDate: String? = null,
	@SerialName("primaryType") val primaryType: String? = null,
	@SerialName("secondaryTypes") val secondaryTypes: List<String> = emptyList(),
	val status: String? = null,
	val tags: List<String> = emptyList(),
	val genres: List<String> = emptyList(),
	@SerialName("matchedTags") val matchedTags: List<String> = emptyList(),
	@SerialName("sourceArtist") val sourceArtist: String? = null,
	@SerialName("sourceArtists") val sourceArtists: List<String> = emptyList(),
	@SerialName("sourceType") val sourceType: String? = null,
	@SerialName("discoveryTier") val discoveryTier: String? = null,
	val monitored: Boolean? = null,
	@SerialName("monitorOption") val monitorOption: String? = null
)

@Serializable
internal data class AurralAuthMeDto(
	val user: AurralUserDto? = null,
	@SerialName("expiresAt") val expiresAt: String? = null
)

@Serializable
internal data class AurralUserDto(
	val id: Int? = null,
	val username: String? = null,
	val role: String? = null,
	val permissions: AurralPermissionsDto = AurralPermissionsDto()
)

@Serializable
internal data class AurralPermissionsDto(
	@SerialName("accessFlow") val accessFlow: Boolean = false,
	@SerialName("addArtist") val addArtist: Boolean = false,
	@SerialName("addAlbum") val addAlbum: Boolean = false,
	@SerialName("changeMonitoring") val changeMonitoring: Boolean = false
)

@Serializable
internal data class AurralWeeklyFlowStatusDto(
	val flows: List<AurralFlowDto> = emptyList(),
	@SerialName("sharedPlaylists") val sharedPlaylists: List<AurralSharedPlaylistDto> = emptyList(),
	@SerialName("flowStats") val flowStats: Map<String, AurralFlowStatsDto> = emptyMap(),
	val stats: AurralFlowStatsDto = AurralFlowStatsDto(),
	val capabilities: AurralFlowCapabilitiesDto = AurralFlowCapabilitiesDto(),
	val hint: AurralFlowHintDto? = null
)

@Serializable
internal data class AurralFlowDto(
	val id: String? = null,
	val name: String? = null,
	val enabled: Boolean = false,
	val size: Int? = null,
	@SerialName("nextRunAt") val nextRunAt: Long? = null,
	val mix: AurralFlowMixDto = AurralFlowMixDto(),
	val tags: List<String> = emptyList(),
	@SerialName("relatedArtists") val relatedArtists: List<String> = emptyList(),
	@SerialName("scheduleDays") val scheduleDays: List<Int> = emptyList(),
	@SerialName("scheduleTime") val scheduleTime: String? = null
)

@Serializable
internal data class AurralSharedPlaylistDto(
	val id: String? = null,
	val name: String? = null
)

@Serializable
internal data class AurralFlowStatsDto(
	val total: Int = 0,
	val pending: Int = 0,
	val downloading: Int = 0,
	val done: Int = 0,
	val failed: Int = 0
)

@Serializable
internal data class AurralFlowMixDto(
	val discover: Int = 34,
	val mix: Int = 33,
	val trending: Int = 33,
	val focus: Int = 0
)

@Serializable
internal data class AurralFlowCapabilitiesDto(
	@SerialName("lastfmRequired") val lastfmRequired: Boolean = false,
	@SerialName("availableSources") val availableSources: List<String> = emptyList(),
	@SerialName("unavailableSources") val unavailableSources: Map<String, String> = emptyMap()
)

@Serializable
internal data class AurralFlowHintDto(
	val phase: String? = null,
	val message: String? = null
)

@Serializable
internal data class AurralRequestDto(
	val id: String? = null,
	val type: String? = null,
	val mbid: String? = null,
	val name: String? = null,
	@SerialName("albumId") val albumId: String? = null,
	@SerialName("albumMbid") val albumMbid: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	@SerialName("artistId") val artistId: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	val status: String? = null,
	@SerialName("requestedAt") val requestedAt: String? = null,
	@SerialName("inQueue") val inQueue: Boolean = false
)

@Serializable
internal data class AurralArtistDetailsDto(
	val id: String? = null,
	val name: String? = null,
	@SerialName("_lidarrData") val lidarrData: AurralArtistLidarrDataDto? = null,
	@SerialName("release-groups") val releaseGroups: List<AurralReleaseGroupDto> = emptyList()
)

@Serializable
internal data class AurralArtistLidarrDataDto(
	val monitored: Boolean? = null
)

@Serializable
internal data class AurralLibraryArtistDto(
	val monitored: Boolean? = null,
	@SerialName("monitorOption") val monitorOption: String? = null
)

@Serializable
internal data class AurralReleaseGroupDto(
	val id: String? = null,
	val title: String? = null,
	@SerialName("first-release-date") val firstReleaseDate: String? = null,
	@SerialName("primary-type") val primaryType: String? = null,
	@SerialName("secondary-types") val secondaryTypes: List<String> = emptyList(),
	@SerialName("_coverUrl") val coverUrl: String? = null
)

@Serializable
internal data class AurralArtistPreviewDto(
	val tracks: List<AurralPreviewTrackDto> = emptyList()
)

@Serializable
internal data class AurralPreviewTrackDto(
	val id: String? = null,
	val title: String? = null,
	val album: String? = null,
	@SerialName("preview_url") val previewUrl: String? = null,
	@SerialName("duration_ms") val durationMs: Long? = null,
	val owned: Boolean? = null,
	val requested: Boolean? = null,
	@SerialName("inLibrary") val inLibrary: Boolean? = null,
	val status: String? = null
)

@Serializable
internal data class AurralSimilarArtistsDto(
	val artists: List<AurralSimilarArtistDto> = emptyList()
)

@Serializable
internal data class AurralReleaseGroupCoverDto(
	val images: List<AurralReleaseGroupCoverImageDto> = emptyList()
)

@Serializable
internal data class AurralReleaseGroupCoverImageDto(
	val image: String? = null
)

@Serializable
internal data class AurralSimilarArtistDto(
	val id: String? = null,
	val name: String? = null,
	val image: String? = null,
	val imageUrl: String? = null,
	val match: Int? = null
)

@Serializable
internal data class AurralFlowActionDto(
	val success: Boolean = false,
	@SerialName("flowId") val flowId: String? = null,
	val enabled: Boolean? = null,
	@SerialName("tracksQueued") val tracksQueued: Int = 0,
	@SerialName("reserveTracks") val reserveTracks: Int = 0,
	@SerialName("jobIds") val jobIds: List<String> = emptyList(),
	val message: String? = null,
	val flow: AurralFlowDto? = null
)

internal fun aurralDiscoverySummary(
	baseUrl: String,
	response: AurralDiscoveryResponseDto,
	recentlyAdded: List<AurralDiscoverArtist> = emptyList(),
	libraryArtists: List<AurralDiscoverArtist> = emptyList(),
	recentReleases: List<AurralAlbumSearchItem> = emptyList()
): AurralDiscoverySummary {
	val safeLibraryArtists = libraryArtists
	return AurralDiscoverySummary(
		recentlyAdded = recentlyAdded.withLibraryArtistMonitoring(safeLibraryArtists),
		recommendations = response.recommendations.mapNotNull { it.toDiscoverArtist(baseUrl) }
			.withLibraryArtistMonitoring(safeLibraryArtists),
		globalTop = response.globalTop.mapNotNull { it.toDiscoverArtist(baseUrl) }
			.withLibraryArtistMonitoring(safeLibraryArtists),
		basedOn = response.basedOn.mapNotNull { it.toDiscoverArtist(baseUrl) }
			.withLibraryArtistMonitoring(safeLibraryArtists),
		libraryArtists = safeLibraryArtists,
		recentReleases = recentReleases,
		fallbackGenres = response.fallbackGenres.mapNotNull { it.toFallbackGenreSection(baseUrl) }
			.map { section ->
				section.copy(artists = section.artists.withLibraryArtistMonitoring(safeLibraryArtists))
			},
		topTags = response.topTags.cleanedAurralStrings(),
		topGenres = response.topGenres.cleanedAurralStrings(),
		isUpdating = response.isUpdating,
		stale = response.stale,
		provider = response.provider?.trim()?.takeIf { it.isNotEmpty() },
		discoveryMode = response.discoveryMode?.trim()?.takeIf { it.isNotEmpty() }
	)
}

internal fun aurralArtistSearchResult(
	baseUrl: String,
	query: String,
	response: AurralArtistSearchResponseDto
): AurralArtistSearchResult =
	AurralArtistSearchResult(
		query = query,
		count = response.count,
		offset = response.offset,
		artists = response.artists.mapNotNull { it.toDiscoverArtist(baseUrl) }
	)

internal fun aurralAlbumSearchResult(
	baseUrl: String,
	query: String,
	response: AurralAlbumSearchResponseDto
): AurralAlbumSearchResult =
	AurralAlbumSearchResult(
		query = response.query?.trim()?.takeIf { it.isNotEmpty() } ?: query,
		count = response.count,
		offset = response.offset,
		hasMore = response.hasMore,
		albums = response.items.mapNotNull { it.toAlbumSearchItem(baseUrl) }
	)

internal fun aurralRecentReleases(
	baseUrl: String,
	response: List<AurralAlbumSearchItemDto>
): List<AurralAlbumSearchItem> =
	response.mapNotNull { it.toAlbumSearchItem(baseUrl) }

internal fun decodeAurralAlbumTracks(responseText: String): List<AurralAlbumTrackDto> =
	runCatching {
		AURRAL_JSON.decodeFromString<List<AurralAlbumTrackDto>>(responseText)
	}.getOrElse {
		AURRAL_JSON.decodeFromString<AurralAlbumTracksResponseDto>(responseText).tracks
	}

internal fun aurralAlbumTrackItems(
	response: List<AurralAlbumTrackDto>
): List<AurralAlbumTrackItem> =
	response.mapNotNull { it.toAlbumTrackItem() }

internal fun aurralRecentlyAddedArtists(
	baseUrl: String,
	response: List<AurralDiscoverArtistDto>
): List<AurralDiscoverArtist> =
	response.mapNotNull { it.toRecentlyAddedArtist(baseUrl) }

internal fun aurralLibraryArtists(
	baseUrl: String,
	response: List<AurralDiscoverArtistDto>
): List<AurralDiscoverArtist> =
	response.mapNotNull { it.toLibraryArtist(baseUrl) }

private fun AurralDiscoverArtistDto.toDiscoverArtist(baseUrl: String): AurralDiscoverArtist? {
	val recommendedAlbum = toRecommendedAlbum(baseUrl)
	if (recommendedAlbum != null) {
		val recommendedArtistId = listOf(artistMbid, foreignArtistId)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
			?: return null
		val recommendedArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() }
			?: return null
		return AurralDiscoverArtist(
			id = recommendedArtistId,
			name = recommendedArtistName,
			imageUrl = null,
			tags = (tags + genres).cleanedAurralStrings(),
			matchedTags = matchedTags.cleanedAurralStrings(),
			reason = "Recommended: ${recommendedAlbum.title}",
			sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
			discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
			monitored = monitored,
			recommendedAlbums = listOf(recommendedAlbum),
			detailsIdVerified = true
		)
	}

	val artistId = verifiedAurralArtistIdCandidate()
		?: return null
	val artistName = listOf(name, artistName)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralDiscoverArtist(
		id = artistId.id,
		name = artistName,
		imageUrl = aurralAbsoluteImageUrl(baseUrl, imageUrl ?: image),
		tags = (tags + genres).cleanedAurralStrings(),
		matchedTags = matchedTags.cleanedAurralStrings(),
		reason = aurralDiscoveryReason(this),
		sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
		discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
		monitored = monitored,
		detailsIdVerified = artistId.verified
	)
}

private fun AurralDiscoverArtistDto.toRecentlyAddedArtist(baseUrl: String): AurralDiscoverArtist? {
	val artistId = verifiedAurralArtistIdCandidate()
		?: return null
	val artistName = listOf(artistName, name, id)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralDiscoverArtist(
		id = artistId.id,
		name = artistName,
		imageUrl = aurralAbsoluteImageUrl(baseUrl, imageUrl ?: image),
		tags = (tags + genres).cleanedAurralStrings(),
		matchedTags = matchedTags.cleanedAurralStrings(),
		sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
		discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
		monitored = monitored,
		detailsIdVerified = artistId.verified
	)
}

private fun AurralDiscoverArtistDto.toLibraryArtist(baseUrl: String): AurralDiscoverArtist? {
	val artistId = verifiedAurralArtistIdCandidate()
		?: return null
	val artistName = listOf(artistName, name, id)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralDiscoverArtist(
		id = artistId.id,
		name = artistName,
		imageUrl = aurralAbsoluteImageUrl(baseUrl, imageUrl ?: image),
		tags = (tags + genres).cleanedAurralStrings(),
		matchedTags = matchedTags.cleanedAurralStrings(),
		sourceType = sourceType?.trim()?.takeIf { it.isNotEmpty() },
		discoveryTier = discoveryTier?.trim()?.takeIf { it.isNotEmpty() },
		monitored = monitored ?: monitorOption?.trim()?.equals("none", ignoreCase = true)?.not(),
		detailsIdVerified = artistId.verified
	)
}

private data class AurralArtistIdCandidate(
	val id: String,
	val verified: Boolean
)

private fun AurralDiscoverArtistDto.verifiedAurralArtistIdCandidate(): AurralArtistIdCandidate? =
	listOf(
		foreignArtistId to true,
		mbid to true,
		artistMbid to true,
		id to false
	).firstNotNullOfOrNull { (candidate, verified) ->
		candidate?.trim()?.takeIf(String::isNotEmpty)?.let { AurralArtistIdCandidate(it, verified) }
	}

private fun AurralFallbackGenreSectionDto.toFallbackGenreSection(baseUrl: String): AurralFallbackGenreSection? {
	val safeGenre = listOf(genre, name, title)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeArtists = artists
		.mapNotNull { it.toDiscoverArtist(baseUrl) }
		.distinctBy { it.id.trim().lowercase() }
	if (safeArtists.isEmpty()) return null
	return AurralFallbackGenreSection(
		genre = safeGenre,
		artists = safeArtists
	)
}

private fun AurralDiscoverArtistDto.toRecommendedAlbum(baseUrl: String): AurralAlbumSearchItem? {
	val kind = type?.trim()?.lowercase()
	val albumTitle = listOf(albumName, title, name.takeIf { kind == "album" })
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val albumId = listOf(foreignAlbumId, mbid, id)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() }
		?: return null
	val safeArtistMbid = listOf(artistMbid, foreignArtistId)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralAlbumSearchItem(
		id = albumId,
		title = albumTitle,
		artistName = safeArtistName,
		artistMbid = safeArtistMbid,
		releaseDate = releaseDate?.trim()?.takeIf { it.isNotEmpty() },
		primaryType = primaryType?.trim()?.takeIf { it.isNotEmpty() },
		secondaryTypes = secondaryTypes.cleanedAurralStrings(),
		coverUrl = aurralAbsoluteImageUrl(baseUrl, coverUrl ?: imageUrl ?: image),
		status = status?.trim()?.takeIf { it.isNotEmpty() }
	)
}

private fun AurralAlbumSearchItemDto.toAlbumSearchItem(baseUrl: String): AurralAlbumSearchItem? {
	val albumId = listOf(id, mbid, foreignAlbumId)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val albumTitle = listOf(title, albumName)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeArtistName = artistName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val safeArtistMbid = listOf(artistMbid, foreignArtistId)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	return AurralAlbumSearchItem(
		id = albumId,
		title = albumTitle,
		artistName = safeArtistName,
		artistMbid = safeArtistMbid,
		releaseDate = releaseDate?.trim()?.takeIf { it.isNotEmpty() },
		primaryType = primaryType?.trim()?.takeIf { it.isNotEmpty() },
		secondaryTypes = secondaryTypes.cleanedAurralStrings(),
		coverUrl = aurralAbsoluteImageUrl(baseUrl, coverUrl),
		inLibrary = inLibrary,
		libraryAlbumId = libraryAlbumId?.trim()?.takeIf { it.isNotEmpty() },
		libraryArtistId = libraryArtistId?.trim()?.takeIf { it.isNotEmpty() },
		status = status?.trim()?.takeIf { it.isNotEmpty() }
	)
}

private fun AurralAlbumTrackDto.toAlbumTrackItem(): AurralAlbumTrackItem? {
	val safeTitle = listOf(title, trackName, recordingTitle)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: return null
	val safeId = listOf(id, mbid, recordingMbid)
		.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
		?: "track-${discNumber ?: mediumNumber ?: 1}-${trackNumber ?: position ?: absoluteTrackNumber ?: safeTitle}"
	return AurralAlbumTrackItem(
		id = safeId,
		title = safeTitle,
		artistName = listOf(artistName, artistCredit, artist)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
		recordingMbid = listOf(recordingMbid, mbid)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
		discNumber = discNumber ?: mediumNumber,
		trackNumber = trackNumber ?: position ?: absoluteTrackNumber,
		durationMs = durationMs ?: durationMsSnake ?: length,
		previewUrl = listOf(previewUrl, previewUrlSnake)
			.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) },
		status = status?.trim()?.takeIf { it.isNotEmpty() },
		requested = requested
	)
}

private fun aurralDiscoveryReason(artist: AurralDiscoverArtistDto): String? {
	val sourceArtist = artist.sourceArtist?.trim()?.takeIf { it.isNotEmpty() }
	if (sourceArtist != null) return "Similar to $sourceArtist"
	val sourceArtists = artist.sourceArtists.cleanedAurralStrings()
	return when {
		sourceArtists.size == 1 -> "Because you listen to ${sourceArtists.single()}"
		sourceArtists.size > 1 -> "Because you listen to ${sourceArtists.take(2).joinToString(", ")}"
		artist.discoveryTier?.trim()?.equals("deeper", ignoreCase = true) == true ->
			"A deeper discovery pick"
		else -> null
	}
}

internal fun List<AurralDiscoverArtist>.withLibraryArtistMonitoring(
	libraryArtists: List<AurralDiscoverArtist>
): List<AurralDiscoverArtist> {
	if (libraryArtists.isEmpty()) return this
	val libraryById = libraryArtists
		.mapNotNull { artist -> artist.id.normalizedAurralArtistKey()?.let { it to artist } }
		.toMap()
	val libraryByName = libraryArtists
		.mapNotNull { artist -> artist.name.normalizedAurralArtistName()?.let { it to artist } }
		.toMap()
	return map { artist ->
		val libraryArtist = artist.id.normalizedAurralArtistKey()?.let(libraryById::get)
			?: artist.name.normalizedAurralArtistName()?.let(libraryByName::get)
		val preferredImageUrl = libraryArtist?.imageUrl?.trim()?.takeIf { it.isNotEmpty() }
			?: artist.imageUrl
		val monitored = artist.monitored ?: libraryArtist?.monitored
		if (preferredImageUrl != artist.imageUrl || monitored != artist.monitored) {
			artist.copy(
				imageUrl = preferredImageUrl,
				monitored = monitored
			)
		} else {
			artist
		}
	}
}

private fun List<String>.cleanedAurralStrings(): List<String> =
	mapNotNull { it.trim().takeIf(String::isNotEmpty) }
		.distinctBy { it.lowercase() }

private fun String?.normalizedAurralArtistKey(): String? =
	this
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }

private fun String?.normalizedAurralArtistName(): String? =
	this
		?.trim()
		?.lowercase()
		?.replace(Regex("""\s+"""), " ")
		?.takeIf { it.isNotEmpty() }

internal fun aurralServiceStatus(
	health: AurralHealthDto,
	authMe: AurralAuthMeDto?,
	weeklyFlow: AurralWeeklyFlowStatusDto?,
	requests: List<AurralRequestDto>
): AurralServiceStatus {
	val user = authMe?.user ?: health.user
	val stats = weeklyFlow?.stats ?: AurralFlowStatsDto()
	val flows = weeklyFlow?.flows.orEmpty().mapNotNull { flow ->
		val id = flow.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		flow.toSummary(weeklyFlow?.flowStats?.get(id))
	}
	return AurralServiceStatus(
		healthStatus = health.status,
		appVersion = health.appVersion,
		authRequired = health.authRequired,
		username = user?.username,
		role = user?.role,
		accessFlow = user?.permissions?.accessFlow == true,
		addArtist = user?.permissions?.addArtist == true,
		addAlbum = user?.permissions?.addAlbum == true,
		changeMonitoring = user?.permissions?.changeMonitoring == true,
		lidarrConfigured = health.lidarrConfigured || health.rootFolderConfigured,
		discoveryRecommendationsCount = health.discovery?.recommendationsCount ?: 0,
		discoveryUpdating = health.discovery?.isUpdating == true,
		flowsCount = weeklyFlow?.flows.orEmpty().size,
		enabledFlowsCount = weeklyFlow?.flows.orEmpty().count { it.enabled },
		sharedPlaylistsCount = weeklyFlow?.sharedPlaylists.orEmpty().size,
		requestsCount = requests.size,
		flowTracksTotal = stats.total,
		flowTracksPending = stats.pending,
		flowTracksDownloading = stats.downloading,
		flowTracksDone = stats.done,
		flowTracksFailed = stats.failed,
		flowPhase = weeklyFlow?.hint?.phase,
		flowMessage = weeklyFlow?.hint?.message,
		flows = flows,
		flowCapabilities = weeklyFlow?.capabilities?.toCapabilities() ?: AurralFlowCapabilities(),
		acquisitionQueue = requests.mapNotNull(::aurralAcquisitionQueueItem)
	)
}

internal fun aurralDefaultFlowCreatePayload(
	name: String,
	size: Int,
	scheduleDay: Int
): AurralFlowCreatePayload {
	val trimmedName = name.trim().takeIf { it.isNotEmpty() }
		?: error("Flow name is required.")
	if (size <= 0) error("Flow size must be positive.")
	if (scheduleDay !in 0..6) error("Flow schedule day must be between 0 and 6.")
	return AurralFlowCreatePayload(
		name = trimmedName,
		size = size,
		mix = AurralFlowMix(discover = 34, mix = 33, trending = 33, focus = 0),
		scheduleDays = listOf(scheduleDay),
		scheduleTime = "00:00"
	)
}

internal fun currentAurralScheduleDay(): Int {
	val isoDay = Clock.System.now()
		.toLocalDateTime(TimeZone.currentSystemDefault())
		.dayOfWeek
		.ordinal + 1
	return if (isoDay == 7) 0 else isoDay
}

private fun AurralFlowDto.toSummary(stats: AurralFlowStatsDto? = null): AurralFlowSummary? {
	val id = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val name = name?.trim()?.takeIf { it.isNotEmpty() } ?: "Flow"
	val safeSize = size?.takeIf { it > 0 } ?: 30
	return AurralFlowSummary(
		id = id,
		name = name,
		enabled = enabled,
		size = safeSize,
		nextRunAt = nextRunAt,
		mix = mix.toMix(),
		tags = tags.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		relatedArtists = relatedArtists.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		scheduleDays = scheduleDays.filter { it in 0..6 },
		scheduleTime = scheduleTime?.trim()?.takeIf { it.isNotEmpty() } ?: "00:00",
		stats = stats.toStats()
	)
}

private fun AurralFlowMixDto.toMix(): AurralFlowMix =
	AurralFlowMix(
		discover = discover,
		mix = mix,
		trending = trending,
		focus = focus
	)

private fun AurralFlowStatsDto?.toStats(): AurralFlowStats =
	AurralFlowStats(
		total = this?.total ?: 0,
		pending = this?.pending ?: 0,
		downloading = this?.downloading ?: 0,
		done = this?.done ?: 0,
		failed = this?.failed ?: 0
	)

private fun AurralFlowCapabilitiesDto.toCapabilities(): AurralFlowCapabilities =
	AurralFlowCapabilities(
		lastfmRequired = lastfmRequired,
		availableSources = availableSources.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		unavailableSources = unavailableSources.mapNotNull { (key, value) ->
			val normalizedKey = key.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
			val normalizedValue = value.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
			normalizedKey to normalizedValue
		}.toMap()
	)

internal fun AurralFlowActionDto.toResult(): AurralFlowActionResult =
	AurralFlowActionResult(
		success = success,
		flowId = flow?.id?.trim()?.takeIf { it.isNotEmpty() }
			?: flowId?.trim()?.takeIf { it.isNotEmpty() },
		enabled = enabled ?: flow?.enabled,
		tracksQueued = tracksQueued,
		reserveTracks = reserveTracks,
		jobIds = jobIds.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		message = message?.trim()?.takeIf { it.isNotEmpty() },
		flow = flow?.toSummary()
	)

internal fun AurralFlowJobDto.toDomainSong(
	baseUrl: String,
	sessionToken: String?,
	streamToken: String?,
	allowUnauthenticatedStream: Boolean
): DomainSong? {
	val jobId = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val title = trackName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val streamUrl = when {
		!sessionToken.isNullOrBlank() -> aurralFlowStreamUrl(baseUrl, jobId, sessionToken)
		!streamToken.isNullOrBlank() -> aurralFlowStreamTokenUrl(baseUrl, jobId, streamToken)
		allowUnauthenticatedStream -> aurralFlowRawStreamUrl(baseUrl, jobId)
		else -> null
	} ?: return null
	val fileExtension = finalPath
		?.substringBefore('?')
		?.substringAfterLast('/')
		?.substringAfterLast('.', "")
		?.lowercase()
		?.takeIf { it.isNotEmpty() }
		?: "mp3"

	return DomainSong(
		id = "$AurralFlowSongIdPrefix$jobId",
		title = title,
		artistName = artistName?.trim()?.takeIf { it.isNotEmpty() } ?: "Aurral",
		artistId = artistMbid?.trim()?.takeIf { it.isNotEmpty() } ?: "",
		albumTitle = albumName?.trim()?.takeIf { it.isNotEmpty() },
		albumId = albumMbid?.trim()?.takeIf { it.isNotEmpty() },
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = releaseYear?.trim()?.take(4)?.toIntOrNull(),
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = durationMs?.takeIf { it > 0 }?.milliseconds ?: 0.milliseconds,
		bpm = null,
		contributors = emptyList(),
		playCount = 0,
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 0,
		fileExtension = fileExtension,
		mimeType = fileExtension.toAurralAudioMimeType(),
		filePath = streamUrl,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = trackMbid?.trim()?.takeIf { it.isNotEmpty() },
		explicitStatus = DomainExplicitStatus.Unknown
	)
}

private fun String.toAurralAudioMimeType(): String =
	when (lowercase()) {
		"flac" -> "audio/flac"
		"m4a", "mp4" -> "audio/mp4"
		"ogg", "oga" -> "audio/ogg"
		"wav" -> "audio/wav"
		"aac" -> "audio/aac"
		else -> "audio/mpeg"
	}

internal fun aurralAcquisitionQueueItem(request: AurralRequestDto): AurralAcquisitionQueueItem? {
	val id = request.id?.trim()?.takeIf { it.isNotEmpty() }
		?: request.albumId?.trim()?.takeIf { it.isNotEmpty() }?.let { "album-$it" }
		?: request.resolvedAlbumMbid()?.let { "album-$it" }
		?: return null
	val albumName = request.resolvedAlbumName() ?: return null
	val artistName = request.artistName?.trim()?.takeIf { it.isNotEmpty() } ?: "Artist"
	val status = request.status?.trim()?.takeIf { it.isNotEmpty() } ?: "requested"
	return AurralAcquisitionQueueItem(
		id = id,
		type = request.type?.trim()?.takeIf { it.isNotEmpty() } ?: "album",
		albumId = request.albumId?.trim()?.takeIf { it.isNotEmpty() },
		albumMbid = request.resolvedAlbumMbid(),
		albumName = albumName,
		artistId = request.artistId?.trim()?.takeIf { it.isNotEmpty() },
		artistMbid = request.artistMbid?.trim()?.takeIf { it.isNotEmpty() },
		artistName = artistName,
		status = status,
		requestedAt = request.requestedAt?.trim()?.takeIf { it.isNotEmpty() },
		inQueue = request.inQueue
	)
}

fun aurralAcquisitionDeleteTarget(item: AurralAcquisitionQueueItem): AurralAcquisitionDeleteTarget? =
	item.albumId?.trim()?.takeIf { it.isNotEmpty() }?.let(AurralAcquisitionDeleteTarget::Album)
		?: item.artistMbid?.trim()?.takeIf { it.isNotEmpty() }?.let(AurralAcquisitionDeleteTarget::Artist)

private fun AurralRequestDto.resolvedAlbumMbid(): String? =
	albumMbid?.trim()?.takeIf { it.isNotEmpty() }
		?: mbid?.trim()?.takeIf { it.isNotEmpty() }

private fun AurralRequestDto.resolvedAlbumName(): String? =
	albumName?.trim()?.takeIf { it.isNotEmpty() }
		?: name?.trim()?.takeIf { it.isNotEmpty() }

internal fun aurralArtistEnrichment(
	baseUrl: String,
	details: AurralArtistDetailsDto,
	preview: AurralArtistPreviewDto,
	similar: AurralSimilarArtistsDto,
	requests: List<AurralRequestDto>
): AurralArtistEnrichment {
	val artistMbid = details.id.orEmpty()
	val artistName = details.name.orEmpty()
	return AurralArtistEnrichment(
		artistMbid = artistMbid,
		artistName = artistName,
		releaseGroups = details.releaseGroups.mapNotNull { releaseGroup ->
			val id = releaseGroup.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val title = releaseGroup.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralReleaseGroup(
				id = id,
				title = title,
				firstReleaseDate = releaseGroup.firstReleaseDate,
				primaryType = releaseGroup.primaryType,
				secondaryTypes = releaseGroup.secondaryTypes,
				coverUrl = releaseGroup.coverUrl
			)
		},
		previewTracks = preview.tracks.mapNotNull { track ->
			val id = track.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val title = track.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralPreviewTrack(
				id = id,
				title = title,
				album = track.album,
				previewUrl = track.previewUrl,
				durationMs = track.durationMs,
				owned = track.owned,
				requested = track.requested,
				inLibrary = track.inLibrary,
				status = track.status
			)
		},
		similarArtists = similar.artists.mapNotNull { artist ->
			val id = artist.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			val name = artist.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
			AurralSimilarArtist(
				id = id,
				name = name,
				imageUrl = aurralAbsoluteImageUrl(baseUrl, artist.imageUrl ?: artist.image),
				matchPercent = artist.match
			)
		},
		requests = requests.map { request ->
			AurralAlbumRequest(
				albumMbid = request.resolvedAlbumMbid(),
				albumName = request.resolvedAlbumName(),
				artistMbid = request.artistMbid,
				artistName = request.artistName,
				status = request.status
			)
		},
		monitored = details.lidarrData?.monitored
	)
}
