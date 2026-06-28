package paige.navic.domain.repositories

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


sealed interface BinderyConnectionResult {
	data object Disabled : BinderyConnectionResult
	data object MissingOpdsUrl : BinderyConnectionResult
	data class InvalidOpdsUrl(val message: String) : BinderyConnectionResult
	data object MissingApiKey : BinderyConnectionResult
	data object Unauthorized : BinderyConnectionResult
	data object Forbidden : BinderyConnectionResult
	data class Connected(
		val navigationCount: Int,
		val audiobooksAvailable: Boolean
	) : BinderyConnectionResult
	data class Failed(val message: String) : BinderyConnectionResult
}

data class BinderyServiceStatus(
	val enabled: Boolean = true,
	val opdsUrlConfigured: Boolean,
	val apiKeyConfigured: Boolean,
	val navigationCount: Int = 0,
	val hasSearch: Boolean = false,
	val hasAudiobooks: Boolean = false,
	val hasAuthors: Boolean = false,
	val hasSeries: Boolean = false,
	val hasCollections: Boolean = false,
	val hasFindings: Boolean = false,
	val progressSyncSupported: Boolean = false,
	val paginationSupported: Boolean = false
)

@Serializable
data class BinderyReadingProgress(
	val bookId: String,
	val alias: String? = null,
	val kind: BinderyReadingProgressKind = BinderyReadingProgressKind.Ebook,
	val resourceHref: String? = null,
	val textHref: String? = null,
	val cfi: String? = null,
	val fragmentId: String? = null,
	val positionMs: Long? = null,
	val durationMs: Long? = null,
	val progressFraction: Double? = null,
	val updatedAt: String? = null
)

@Serializable
data class BinderyAudiobookVersion(
	val id: Long? = null,
	val bookId: Long? = null,
	val bookFileId: Long? = null,
	val providerFindingId: Long? = null,
	val providerMappingId: Long? = null,
	val title: String? = null,
	val versionLabel: String? = null,
	val language: String? = null,
	val narrator: String? = null,
	val publisher: String? = null,
	val studio: String? = null,
	val editionType: String? = null,
	val audibleAsin: String? = null,
	val audibleSourceUrl: String? = null,
	val audibleTitle: String? = null,
	val audibleAuthor: String? = null,
	val seriesTitle: String? = null,
	val seriesPosition: String? = null,
	val releaseDate: String? = null,
	val formatLabel: String? = null,
	val description: String? = null,
	val copyright: String? = null,
	val categories: List<String> = emptyList(),
	val sourceUrl: String? = null,
	val coverUrl: String? = null,
	val coverSource: String? = null,
	val metadataStatus: String? = null,
	val durationMs: Long? = null,
	val sizeBytes: Long? = null,
	val resourceCount: Int? = null,
	val codec: String? = null,
	val bitrateBps: Long? = null,
	val sampleRateHz: Long? = null,
	val channels: Int? = null,
	val whispersyncAvailable: Boolean? = null,
	val whispersyncReadyCount: Int? = null,
	val whispersyncStatus: String? = null,
	val whispersync: List<BinderySyncPair> = emptyList(),
	val resources: List<BinderyAudiobookResource> = emptyList(),
	val provenance: BinderyAudiobookProvenance? = null,
	val createdAt: String? = null,
	val updatedAt: String? = null,
	val book: JsonElement? = null
)

@Serializable
data class BinderyAudiobookResource(
	val id: Long? = null,
	val resourceKey: String? = null,
	val relativePath: String? = null,
	val displayTitle: String? = null,
	val trackNumber: Int? = null,
	val discNumber: Int? = null,
	val durationMs: Long? = null,
	val sizeBytes: Long? = null,
	val mimeType: String? = null,
	val codec: String? = null,
	val bitrateBps: Long? = null,
	val sampleRateHz: Long? = null,
	val channels: Int? = null,
	val deliveryPolicy: String? = null
)

@Serializable
data class BinderyAudiobookProvenance(
	val source: String? = null,
	val sourceUrl: String? = null,
	val provider: String? = null,
	val providerKind: String? = null,
	val providerTitle: String? = null,
	val providerSourceUrl: String? = null,
	val mappingStatus: String? = null,
	val metadataProvider: String? = null,
	val metadataConfidence: String? = null,
	val metadataConfidenceScore: Int? = null,
	val metadataConfidenceReason: String? = null,
	val coverHref: String? = null
)

@Serializable
data class BinderyBookSync(
	val bookId: Long? = null,
	val whispersyncStatus: String? = null,
	val syncPairCounts: Map<String, Int> = emptyMap(),
	val syncPairs: List<BinderySyncPair> = emptyList()
)

@Serializable
data class BinderySyncPair(
	val bookId: Long? = null,
	val ebookBookFileId: Long? = null,
	val audiobookBookFileId: Long? = null,
	val ebookLanguage: String? = null,
	val audiobookLanguage: String? = null,
	val language: String? = null,
	val whispersync: BinderyWhispersyncArtifact? = null
)

@Serializable
data class BinderyWhispersyncArtifact(
	val status: String? = null,
	val artifactId: Long? = null,
	val artifactHref: String? = null,
	val reportHref: String? = null,
	val score: Double? = null,
	val coverage: Double? = null,
	val audioCoverage: Double? = null,
	val ebookCoverage: Double? = null,
	val lastJob: BinderyWhispersyncJob? = null
)

@Serializable
data class BinderyWhispersyncJob(
	val id: Long? = null,
	val state: String? = null,
	val status: String? = null,
	val phase: String? = null,
	val progressPercent: Double? = null,
	val message: String? = null,
	val updatedAt: String? = null
)

fun BinderySyncPair.hasReadyWhispersyncArtifact(): Boolean {
	val artifact = whispersync ?: return false
	return artifact.status.equals("ready", ignoreCase = true) &&
		!artifact.artifactHref.isNullOrBlank()
}

@Serializable
enum class BinderyReadingProgressKind {
	@SerialName("ebook")
	Ebook,

	@SerialName("audiobook")
	Audiobook,

	@SerialName("readaloud")
	Readaloud
}

@Serializable
data class BinderyCatalog(
	val title: String,
	val identifier: String? = null,
	val description: String? = null,
	val subjects: List<String> = emptyList(),
	val availability: BinderyAvailability? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val images: List<BinderyLink> = emptyList(),
	val links: List<BinderyLink> = emptyList(),
	val navigation: List<BinderyLink> = emptyList(),
	val publications: List<BinderyPublication> = emptyList(),
	val finding: BinderyFindingMetadata? = null
) {
	fun hasRel(rel: String): Boolean =
		links.any { link -> link.rel.any { it.equals(rel, ignoreCase = true) } }

	fun hasNavigationPath(path: String): Boolean {
		val normalizedPath = path.trim().trimEnd('/').lowercase()
		return navigation.any { link ->
			link.href.trim().trimEnd('/').lowercase() == normalizedPath
		}
	}
}

@Serializable
data class BinderyPublication(
	val id: String?,
	val title: String,
	val author: String? = null,
	val published: String? = null,
	val description: String? = null,
	val subjects: List<String> = emptyList(),
	val durationSeconds: Double? = null,
	val availability: BinderyAvailability? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val links: List<BinderyLink> = emptyList(),
	val images: List<BinderyLink> = emptyList(),
	val readingOrder: List<BinderyReadingOrderItem> = emptyList(),
	val sync: BinderyBookSync? = null,
	val finding: BinderyFindingMetadata? = null
)

@Serializable
data class BinderyFindingMetadata(
	val findingId: String? = null,
	val provider: String? = null,
	val providerKind: String? = null,
	val mediaType: String? = null,
	val format: String? = null,
	val language: String? = null,
	val author: String? = null,
	val bookTitleHint: String? = null,
	val edition: String? = null,
	val narrator: String? = null,
	val publisher: String? = null,
	val protocol: String? = null,
	val fileCount: Int? = null,
	val sizeBytes: Long? = null,
	val bitrateBps: Long? = null,
	val sampleRateHz: Long? = null,
	val availabilityStatus: String? = null,
	val availabilityReason: String? = null,
	val sourceUrl: String? = null,
	val coverUrl: String? = null,
	val publishedDate: String? = null,
	val uploadDate: String? = null,
	val providerComments: String? = null,
	val files: List<BinderyFindingFile> = emptyList(),
	val mappings: List<BinderyFindingMapping> = emptyList()
)

@Serializable
data class BinderyFindingFile(
	val name: String? = null,
	val href: String? = null,
	val format: String? = null,
	val language: String? = null,
	val sizeBytes: Long? = null,
	val durationSeconds: Double? = null,
	val bitrateBps: Long? = null,
	val sampleRateHz: Long? = null
)

@Serializable
data class BinderyFindingMapping(
	val id: String? = null,
	val bookId: String? = null,
	val bookTitle: String? = null,
	val authorName: String? = null,
	val confidence: Double? = null,
	val mediaType: String? = null,
	val targetLanguage: String? = null,
	val acquisitionStatus: String? = null,
	val acquisitionScope: String? = null,
	val selectedBytes: Long? = null,
	val bookFileId: String? = null,
	val bookFileFormat: String? = null,
	val bookFileSizeBytes: Long? = null,
	val sourceCatalogCandidateId: String? = null
)

@Serializable
data class BinderyManifest(
	val id: String?,
	val title: String,
	val author: String? = null,
	val published: String? = null,
	val description: String? = null,
	val subjects: List<String> = emptyList(),
	val durationSeconds: Double? = null,
	val availability: BinderyAvailability? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val links: List<BinderyLink> = emptyList(),
	val images: List<BinderyLink> = emptyList(),
	val readingOrder: List<BinderyReadingOrderItem> = emptyList(),
	val sync: BinderyBookSync? = null
)

@Serializable
data class BinderyReadingOrderItem(
	val href: String,
	val title: String,
	val type: String?,
	val durationSeconds: Double? = null,
	val sizeBytes: Long? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val metadata: BinderyResourceMetadata = BinderyResourceMetadata()
)

@Serializable
data class BinderyResourceMetadata(
	val resourceKey: String? = null,
	val relativePath: String? = null,
	val durationMs: Long? = null,
	val language: String? = null,
	val chapterLabel: String? = null,
	val sectionLabel: String? = null,
	val trackNumber: Int? = null,
	val discNumber: Int? = null,
	val narrator: String? = null,
	val author: String? = null,
	val editionSuffix: String? = null,
	val sourceProvider: String? = null,
	val audio: BinderyAudioMetadata? = null,
	val sourceRelease: BinderySourceReleaseMetadata? = null
)

@Serializable
data class BinderyAudioMetadata(
	val codec: String? = null,
	val bitrateKbps: Int? = null,
	val sampleRateHz: Long? = null,
	val channels: Int? = null,
	val qualityLabel: String? = null,
	val qualityScore: Double? = null
)

@Serializable
data class BinderySourceReleaseMetadata(
	val provider: String? = null,
	val sourceUrl: String? = null,
	val narrator: String? = null,
	val readBy: String? = null,
	val edition: String? = null,
	val format: String? = null,
	val categories: List<String> = emptyList(),
	val keywords: List<String> = emptyList()
)

@Serializable
data class BinderyLink(
	val href: String,
	val title: String? = null,
	val type: String? = null,
	val rel: List<String> = emptyList(),
	val availability: BinderyAvailability? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val images: List<BinderyLink> = emptyList(),
	val links: List<BinderyLink> = emptyList()
)

@Serializable
data class BinderyAvailability(
	val owned: Boolean = false,
	val complete: Boolean = false,
	val ownedBooks: Int? = null,
	val missingBooks: Int? = null,
	val totalBooks: Int? = null,
	val formats: List<String> = emptyList(),
	val ownedFormats: List<String> = emptyList(),
	val ownedLanguages: List<String> = emptyList(),
	val ownedCombinations: List<BinderyAvailabilityCombination> = emptyList(),
	val languages: List<String> = emptyList(),
	val mode: String? = null
)

@Serializable
data class BinderyAvailabilityCombination(
	val format: String,
	val language: String
)

@Serializable
data class BinderyResourceCatalog(
	val title: String,
	val resources: List<BinderyBookResource> = emptyList()
)

@Serializable
data class BinderyBookResource(
	val href: String,
	val title: String,
	val type: String? = null,
	val kind: String? = null,
	val durationSeconds: Double? = null,
	val sizeBytes: Long? = null,
	val properties: Map<String, String> = emptyMap(),
	val propertyValues: BinderyPropertyBag = BinderyPropertyBag(),
	val metadata: BinderyResourceMetadata = BinderyResourceMetadata()
)

@Serializable
data class BinderyPropertyBag(
	val values: Map<String, BinderyPropertyValue> = emptyMap()
) {
	operator fun get(key: String): BinderyPropertyValue? =
		values.entries.firstOrNull { (entryKey, _) -> entryKey.equals(key, ignoreCase = true) }?.value

	fun string(key: String): String? =
		(this[key] as? BinderyPropertyValue.StringValue)?.value

	fun number(key: String): Double? =
		(this[key] as? BinderyPropertyValue.NumberValue)?.value

	fun boolean(key: String): Boolean? =
		(this[key] as? BinderyPropertyValue.BooleanValue)?.value

	fun array(key: String): List<BinderyPropertyValue> =
		(this[key] as? BinderyPropertyValue.ArrayValue)?.values.orEmpty()

	fun objectBag(key: String): BinderyPropertyBag? =
		(this[key] as? BinderyPropertyValue.ObjectValue)?.let { BinderyPropertyBag(it.values) }
}

@Serializable
sealed interface BinderyPropertyValue {
	@Serializable
	@SerialName("string")
	data class StringValue(val value: String) : BinderyPropertyValue

	@Serializable
	@SerialName("number")
	data class NumberValue(val value: Double, val raw: String) : BinderyPropertyValue

	@Serializable
	@SerialName("boolean")
	data class BooleanValue(val value: Boolean) : BinderyPropertyValue

	@Serializable
	@SerialName("array")
	data class ArrayValue(val values: List<BinderyPropertyValue>) : BinderyPropertyValue

	@Serializable
	@SerialName("object")
	data class ObjectValue(val values: Map<String, BinderyPropertyValue>) : BinderyPropertyValue
}
