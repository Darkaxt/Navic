package paige.navic.domain.repositories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
	val image: String? = null,
	val imageUrl: String? = null,
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
	val bio: String? = null,
	val genres: List<String> = emptyList(),
	val links: List<AurralExternalLinkDto> = emptyList(),
	val relations: List<AurralRelationDto> = emptyList(),
	@SerialName("_lidarrData") val lidarrData: AurralArtistLidarrDataDto? = null,
	@SerialName("release-groups") val releaseGroups: List<AurralReleaseGroupDto> = emptyList()
)

@Serializable
internal data class AurralExternalLinkDto(
	val type: String? = null,
	val target: String? = null
)

@Serializable
internal data class AurralRelationDto(
	val type: String? = null,
	val url: AurralRelationUrlDto? = null
)

@Serializable
internal data class AurralRelationUrlDto(
	val resource: String? = null
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
