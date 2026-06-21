package paige.navic.domain.repositories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MusicBrainzArtworkCacheStore(
	val entries: List<MusicBrainzArtworkCacheEntry> = emptyList()
)

@Serializable
data class MusicBrainzArtworkCacheEntry(
	val songId: String,
	val fingerprint: String,
	val status: MusicBrainzArtworkCacheStatus,
	val imageUrl: String?,
	val sourceMbid: String?,
	val sourceType: MusicBrainzArtworkSourceType?,
	val metadata: MusicBrainzTrackMetadata? = null,
	val metadataLookupAttempted: Boolean = false,
	val metadataSchemaVersion: Int = 0,
	val updatedAtMillis: Long
)

@Serializable
data class MusicBrainzTrackMetadata(
	val recordingMbid: String? = null,
	val recordingTitle: String? = null,
	val recordingDisambiguation: String? = null,
	val artistCredit: String? = null,
	val firstReleaseDate: String? = null,
	val releaseMbid: String? = null,
	val releaseTitle: String? = null,
	val releaseDisambiguation: String? = null,
	val releaseGroupMbid: String? = null,
	val releaseGroupTitle: String? = null,
	val releaseGroupDisambiguation: String? = null,
	val releaseGroupType: String? = null,
	val releaseDate: String? = null,
	val country: String? = null,
	val status: String? = null,
	val genres: List<String> = emptyList(),
	val tags: List<String> = emptyList(),
	val isrcs: List<String> = emptyList(),
	val externalLinks: List<MusicBrainzExternalLink> = emptyList(),
	val recordingUrl: String? = null,
	val releaseUrl: String? = null,
	val releaseGroupUrl: String? = null
)

data class MusicBrainzMetadataDisplayField(
	val field: MusicBrainzMetadataField,
	val value: String,
	val url: String? = null
)

@Serializable
data class MusicBrainzExternalLink(
	val label: String,
	val url: String
)

enum class MusicBrainzMetadataField {
	RecordingTitle,
	RecordingDisambiguation,
	ArtistCredit,
	FirstReleaseDate,
	ReleaseTitle,
	ReleaseDisambiguation,
	ReleaseGroupTitle,
	ReleaseGroupDisambiguation,
	ReleaseGroupType,
	ReleaseDate,
	Country,
	Status,
	Genres,
	Tags,
	Isrcs,
	ExternalLink,
	RecordingUrl,
	ReleaseUrl,
	ReleaseGroupUrl
}

@Serializable
enum class MusicBrainzArtworkCacheStatus {
	Found,
	NotFound
}

@Serializable
enum class MusicBrainzArtworkSourceType {
	Release,
	ReleaseGroup
}

@Serializable
internal data class CoverArtArchiveResponseDto(
	val images: List<CoverArtArchiveImageDto> = emptyList()
)

@Serializable
internal data class CoverArtArchiveImageDto(
	val front: Boolean = false,
	val image: String? = null,
	val thumbnails: Map<String, String> = emptyMap()
)

@Serializable
internal data class MusicBrainzRecordingDto(
	val id: String = "",
	val title: String? = null,
	val disambiguation: String? = null,
	@SerialName("first-release-date") val firstReleaseDate: String? = null,
	@SerialName("artist-credit") val artistCredits: List<MusicBrainzArtistCreditDto> = emptyList(),
	val isrcs: List<String> = emptyList(),
	val genres: List<MusicBrainzTagDto> = emptyList(),
	val tags: List<MusicBrainzTagDto> = emptyList(),
	val relations: List<MusicBrainzRelationDto> = emptyList(),
	val releases: List<MusicBrainzReleaseDto> = emptyList()
)

@Serializable
internal data class MusicBrainzRecordingSearchResponseDto(
	val recordings: List<MusicBrainzRecordingSearchResultDto> = emptyList()
)

@Serializable
internal data class MusicBrainzRecordingSearchResultDto(
	val id: String = "",
	val score: String = ""
)

@Serializable
internal data class MusicBrainzArtistCreditDto(
	val name: String = "",
	val joinphrase: String = ""
)

@Serializable
internal data class MusicBrainzTagDto(
	val name: String = "",
	val count: Int? = null
)

@Serializable
internal data class MusicBrainzRelationDto(
	val type: String? = null,
	val ended: Boolean? = null,
	val url: MusicBrainzRelationUrlDto? = null,
	val work: MusicBrainzWorkDto? = null
)

@Serializable
internal data class MusicBrainzRelationUrlDto(
	val resource: String? = null
)

@Serializable
internal data class MusicBrainzWorkDto(
	val relations: List<MusicBrainzRelationDto> = emptyList()
)

@Serializable
internal data class MusicBrainzReleaseDto(
	val id: String = "",
	val title: String? = null,
	val disambiguation: String? = null,
	val date: String? = null,
	val country: String? = null,
	val status: String? = null,
	val relations: List<MusicBrainzRelationDto> = emptyList(),
	@SerialName("release-group") val releaseGroup: MusicBrainzReleaseGroupDto? = null
)

@Serializable
internal data class MusicBrainzReleaseGroupDto(
	val id: String = "",
	val title: String? = null,
	val disambiguation: String? = null,
	@SerialName("primary-type") val primaryType: String? = null,
	@SerialName("secondary-types") val secondaryTypes: List<String> = emptyList(),
	val relations: List<MusicBrainzRelationDto> = emptyList()
)
