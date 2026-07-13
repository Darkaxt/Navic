package paige.navic.domain.repositories

import paige.navic.data.database.dao.BinderyMetadataCacheDao
import paige.navic.data.database.entities.BinderyMetadataCacheEntity
import okio.ByteString.Companion.encodeUtf8

internal const val BINDERY_METADATA_CACHE_FRESH_MILLIS = 6L * 60L * 60L * 1000L

internal object BinderyMetadataPayloadType {
	const val Catalog = "catalog"
	const val Manifest = "manifest"
	const val Resources = "resources"
	const val AudiobookVersions = "audiobook-versions"
	const val AudiobookDetail = "audiobook-detail"
	const val AudiobookManifest = "audiobook-manifest"
	const val BookSync = "book-sync"
	const val WhispersyncSidecar = "whispersync-sidecar"
	const val BookFindings = "book-findings"
	const val ProviderCover = "provider-cover"
}

interface BinderyMetadataCache {
	suspend fun get(cacheKey: String): BinderyMetadataCacheRecord?
	suspend fun put(record: BinderyMetadataCacheRecord)
	suspend fun clearBaseUrl(baseUrl: String)
}

data class BinderyMetadataCacheRecord(
	val cacheKey: String,
	val baseUrl: String,
	val payloadType: String,
	val path: String,
	val payloadJson: String,
	val updatedAtMillis: Long
)

object NoOpBinderyMetadataCache : BinderyMetadataCache {
	override suspend fun get(cacheKey: String): BinderyMetadataCacheRecord? = null
	override suspend fun put(record: BinderyMetadataCacheRecord) = Unit
	override suspend fun clearBaseUrl(baseUrl: String) = Unit
}

class RoomBinderyMetadataCache(
	private val dao: BinderyMetadataCacheDao
) : BinderyMetadataCache {
	override suspend fun get(cacheKey: String): BinderyMetadataCacheRecord? =
		dao.get(cacheKey)?.toRecord()

	override suspend fun put(record: BinderyMetadataCacheRecord) {
		dao.upsert(record.toEntity())
	}

	override suspend fun clearBaseUrl(baseUrl: String) {
		dao.clearBaseUrl(baseUrl)
	}
}

internal fun binderyMetadataCacheKey(
	baseUrl: String,
	payloadType: String,
	path: String,
	apiKeyFingerprint: String
): String =
	listOf(
		"bindery",
		payloadType,
		baseUrl.trim().trimEnd('/'),
		path.trim(),
		"credential:${apiKeyFingerprint.trim()}"
	)
		.joinToString("|")

internal fun binderyApiKeyFingerprint(apiKey: String): String =
	apiKey.trim().encodeUtf8().sha256().hex().take(24)

private fun BinderyMetadataCacheEntity.toRecord(): BinderyMetadataCacheRecord =
	BinderyMetadataCacheRecord(
		cacheKey = cacheKey,
		baseUrl = baseUrl,
		payloadType = payloadType,
		path = path,
		payloadJson = payloadJson,
		updatedAtMillis = updatedAtMillis
	)

private fun BinderyMetadataCacheRecord.toEntity(): BinderyMetadataCacheEntity =
	BinderyMetadataCacheEntity(
		cacheKey = cacheKey,
		baseUrl = baseUrl,
		payloadType = payloadType,
		path = path,
		payloadJson = payloadJson,
		updatedAtMillis = updatedAtMillis
	)
