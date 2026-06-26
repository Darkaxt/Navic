package paige.navic.domain.repositories

import paige.navic.data.database.dao.AurralMetadataCacheDao
import paige.navic.data.database.entities.AurralMetadataCacheEntity

internal const val AURRAL_METADATA_CACHE_FRESH_MILLIS = 6L * 60L * 60L * 1000L

internal object AurralMetadataPayloadType {
	const val Discovery = "discovery"
	const val LibraryArtists = "library-artists"
	const val ArtistSearch = "artist-search"
	const val AlbumSearch = "album-search"
	const val AlbumTracks = "album-tracks"
	const val ArtistCoreEnrichment = "artist-core-enrichment"
	const val ArtistEnrichment = "artist-enrichment"
	const val ReleaseGroupCover = "release-group-cover"
	const val ArtistCreditResolution = "artist-credit-resolution"
}

interface AurralMetadataCache {
	suspend fun get(cacheKey: String): AurralMetadataCacheRecord?
	suspend fun put(record: AurralMetadataCacheRecord)
	suspend fun clearBaseUrl(baseUrl: String)
}

data class AurralMetadataCacheRecord(
	val cacheKey: String,
	val baseUrl: String,
	val payloadType: String,
	val path: String,
	val payloadJson: String,
	val updatedAtMillis: Long
)

object NoOpAurralMetadataCache : AurralMetadataCache {
	override suspend fun get(cacheKey: String): AurralMetadataCacheRecord? = null
	override suspend fun put(record: AurralMetadataCacheRecord) = Unit
	override suspend fun clearBaseUrl(baseUrl: String) = Unit
}

class RoomAurralMetadataCache(
	private val dao: AurralMetadataCacheDao
) : AurralMetadataCache {
	override suspend fun get(cacheKey: String): AurralMetadataCacheRecord? =
		dao.get(cacheKey)?.toRecord()

	override suspend fun put(record: AurralMetadataCacheRecord) {
		dao.upsert(record.toEntity())
	}

	override suspend fun clearBaseUrl(baseUrl: String) {
		dao.clearBaseUrl(baseUrl)
	}
}

internal fun aurralMetadataCacheKey(
	baseUrl: String,
	payloadType: String,
	path: String
): String =
	listOf("aurral", payloadType, baseUrl.trim().trimEnd('/'), path.trim())
		.joinToString("|")

private fun AurralMetadataCacheEntity.toRecord(): AurralMetadataCacheRecord =
	AurralMetadataCacheRecord(
		cacheKey = cacheKey,
		baseUrl = baseUrl,
		payloadType = payloadType,
		path = path,
		payloadJson = payloadJson,
		updatedAtMillis = updatedAtMillis
	)

private fun AurralMetadataCacheRecord.toEntity(): AurralMetadataCacheEntity =
	AurralMetadataCacheEntity(
		cacheKey = cacheKey,
		baseUrl = baseUrl,
		payloadType = payloadType,
		path = path,
		payloadJson = payloadJson,
		updatedAtMillis = updatedAtMillis
	)
