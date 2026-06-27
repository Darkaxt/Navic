package paige.navic.domain.repositories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import paige.navic.domain.models.AurralAlbumRequest

sealed interface AurralConnectionResult {
	data object Connected : AurralConnectionResult
	data object Unauthorized : AurralConnectionResult
	data object Forbidden : AurralConnectionResult
	data class Failed(val message: String) : AurralConnectionResult
}

data class AurralServiceStatus(
	val healthStatus: String = "unknown",
	val appVersion: String? = null,
	val authRequired: Boolean = false,
	val username: String? = null,
	val role: String? = null,
	val accessFlow: Boolean = false,
	val addArtist: Boolean = false,
	val addAlbum: Boolean = false,
	val changeMonitoring: Boolean = false,
	val lidarrConfigured: Boolean = false,
	val discoveryRecommendationsCount: Int = 0,
	val discoveryUpdating: Boolean = false,
	val flowsCount: Int = 0,
	val enabledFlowsCount: Int = 0,
	val sharedPlaylistsCount: Int = 0,
	val requestsCount: Int = 0,
	val flowTracksTotal: Int = 0,
	val flowTracksPending: Int = 0,
	val flowTracksDownloading: Int = 0,
	val flowTracksDone: Int = 0,
	val flowTracksFailed: Int = 0,
	val flowPhase: String? = null,
	val flowMessage: String? = null,
	val flows: List<AurralFlowSummary> = emptyList(),
	val flowCapabilities: AurralFlowCapabilities = AurralFlowCapabilities(),
	val acquisitionQueue: List<AurralAcquisitionQueueItem> = emptyList()
)

sealed class AurralAcquisitionDeleteTarget {
	data class Album(val albumId: String) : AurralAcquisitionDeleteTarget()
	data class Artist(val artistMbid: String) : AurralAcquisitionDeleteTarget()
}

@Serializable
data class AurralDiscoverySummary(
	val recentlyAdded: List<AurralDiscoverArtist> = emptyList(),
	val recommendations: List<AurralDiscoverArtist> = emptyList(),
	val globalTop: List<AurralDiscoverArtist> = emptyList(),
	val basedOn: List<AurralDiscoverArtist> = emptyList(),
	val libraryArtists: List<AurralDiscoverArtist> = emptyList(),
	val recentReleases: List<AurralAlbumSearchItem> = emptyList(),
	val fallbackGenres: List<AurralFallbackGenreSection> = emptyList(),
	val topTags: List<String> = emptyList(),
	val topGenres: List<String> = emptyList(),
	val isUpdating: Boolean = false,
	val stale: Boolean = false,
	val provider: String? = null,
	val discoveryMode: String? = null
)

@Serializable
data class AurralDiscoverArtist(
	val id: String,
	val name: String,
	val imageUrl: String? = null,
	val tags: List<String> = emptyList(),
	val matchedTags: List<String> = emptyList(),
	val reason: String? = null,
	val sourceType: String? = null,
	val discoveryTier: String? = null,
	val monitored: Boolean? = null,
	val recommendedAlbums: List<AurralAlbumSearchItem> = emptyList(),
	val detailsIdVerified: Boolean = false
)

@Serializable
data class AurralFallbackGenreSection(
	val genre: String,
	val artists: List<AurralDiscoverArtist>
)

@Serializable
data class AurralArtistSearchRequest(
	val query: String,
	val limit: Int = 12,
	val offset: Int = 0
)

@Serializable
data class AurralArtistSearchResult(
	val query: String = "",
	val count: Int = 0,
	val offset: Int = 0,
	val artists: List<AurralDiscoverArtist> = emptyList()
)

@Serializable
data class AurralAlbumSearchRequest(
	val query: String,
	val limit: Int = 12,
	val offset: Int = 0
)

@Serializable
data class AurralAlbumSearchResult(
	val query: String = "",
	val count: Int = 0,
	val offset: Int = 0,
	val hasMore: Boolean = false,
	val albums: List<AurralAlbumSearchItem> = emptyList()
)

@Serializable
data class AurralAlbumSearchItem(
	val id: String,
	val title: String,
	val artistName: String,
	val artistMbid: String,
	val releaseDate: String? = null,
	val primaryType: String? = null,
	val secondaryTypes: List<String> = emptyList(),
	val coverUrl: String? = null,
	val inLibrary: Boolean = false,
	val libraryAlbumId: String? = null,
	val libraryArtistId: String? = null,
	val status: String? = null
)

@Serializable
data class AurralAlbumTrackItem(
	val id: String,
	val title: String,
	val artistName: String? = null,
	val recordingMbid: String? = null,
	val discNumber: Int? = null,
	val trackNumber: Int? = null,
	val durationMs: Long? = null,
	val previewUrl: String? = null,
	val status: String? = null,
	val requested: Boolean? = null
)

data class AurralFlowSummary(
	val id: String,
	val name: String,
	val enabled: Boolean,
	val size: Int = 30,
	val nextRunAt: Long? = null,
	val mix: AurralFlowMix = AurralFlowMix(),
	val tags: List<String> = emptyList(),
	val relatedArtists: List<String> = emptyList(),
	val scheduleDays: List<Int> = emptyList(),
	val scheduleTime: String = "00:00",
	val stats: AurralFlowStats = AurralFlowStats()
)

@Serializable
data class AurralFlowMix(
	val discover: Int = 34,
	val mix: Int = 33,
	val trending: Int = 33,
	val focus: Int = 0
)

data class AurralFlowStats(
	val total: Int = 0,
	val pending: Int = 0,
	val downloading: Int = 0,
	val done: Int = 0,
	val failed: Int = 0
)

data class AurralFlowCapabilities(
	val lastfmRequired: Boolean = false,
	val availableSources: List<String> = emptyList(),
	val unavailableSources: Map<String, String> = emptyMap()
)

data class AurralFlowActionResult(
	val success: Boolean = false,
	val flowId: String? = null,
	val enabled: Boolean? = null,
	val tracksQueued: Int = 0,
	val reserveTracks: Int = 0,
	val jobIds: List<String> = emptyList(),
	val message: String? = null,
	val flow: AurralFlowSummary? = null
)

data class AurralAcquisitionQueueItem(
	val id: String,
	val type: String,
	val albumId: String?,
	val albumMbid: String?,
	val albumName: String,
	val artistId: String?,
	val artistMbid: String?,
	val artistName: String,
	val status: String,
	val requestedAt: String?,
	val inQueue: Boolean
)

fun AurralAcquisitionQueueItem.toAlbumRequest() = AurralAlbumRequest(
	albumMbid = albumMbid,
	albumName = albumName,
	artistMbid = artistMbid,
	artistName = artistName,
	status = status
)

@Serializable
data class AurralAlbumRequestPayload(
	val albumMbid: String,
	val albumName: String,
	val artistMbid: String,
	val artistName: String,
	val triggerSearch: Boolean = true
)

@Serializable
data class AurralArtistMonitorPayload(
	@SerialName("foreignArtistId") val foreignArtistId: String,
	val artistName: String,
	val monitorOption: String = "all",
	val monitored: Boolean = true
)

@Serializable
data class AurralFlowCreatePayload(
	val name: String,
	val size: Int,
	val mix: AurralFlowMix = AurralFlowMix(),
	val deepDive: Boolean = false,
	val tags: List<String> = emptyList(),
	@SerialName("relatedArtists") val relatedArtists: List<String> = emptyList(),
	@SerialName("scheduleDays") val scheduleDays: List<Int>,
	@SerialName("scheduleTime") val scheduleTime: String = "00:00"
)

@Serializable
data class AurralAuthSessionDto(
	val token: String? = null,
	@SerialName("expiresAt") val expiresAt: String? = null
)

@Serializable
data class AurralStreamTokenDto(
	val token: String? = null,
	@SerialName("expiresIn") val expiresIn: Int? = null
)

@Serializable
data class AurralFlowJobDto(
	val id: String? = null,
	@SerialName("artistName") val artistName: String? = null,
	@SerialName("trackName") val trackName: String? = null,
	@SerialName("albumName") val albumName: String? = null,
	val status: String? = null,
	@SerialName("playlistType") val playlistType: String? = null,
	@SerialName("artistMbid") val artistMbid: String? = null,
	@SerialName("albumMbid") val albumMbid: String? = null,
	@SerialName("trackMbid") val trackMbid: String? = null,
	@SerialName("releaseYear") val releaseYear: String? = null,
	@SerialName("durationMs") val durationMs: Long? = null,
	@SerialName("finalPath") val finalPath: String? = null
)
