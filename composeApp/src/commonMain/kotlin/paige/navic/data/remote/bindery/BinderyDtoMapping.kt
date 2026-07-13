package paige.navic.data.remote.bindery

import paige.navic.domain.repositories.*

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

@Serializable
internal data class BinderyCatalogDto(
	val metadata: BinderyMetadataDto? = null,
	val properties: Map<String, JsonElement>? = null,
	val images: List<BinderyLinkDto>? = null,
	val links: List<BinderyLinkDto>? = null,
	val navigation: List<BinderyLinkDto>? = null,
	val publications: List<BinderyPublicationDto>? = null
)

@Serializable
internal data class BinderyResourceCatalogDto(
	val metadata: BinderyMetadataDto? = null,
	val resources: List<BinderyLinkDto>? = null
)

@Serializable
internal data class BinderyPublicationDto(
	val metadata: BinderyMetadataDto? = null,
	val properties: Map<String, JsonElement>? = null,
	val links: List<BinderyLinkDto>? = null,
	val images: List<BinderyLinkDto>? = null,
	@SerialName("readingOrder") val readingOrder: List<BinderyLinkDto>? = null
)

@Serializable
internal data class BinderyMetadataDto(
	val title: String? = null,
	val identifier: String? = null,
	val sortAs: String? = null,
	val author: List<BinderyContributorDto>? = null,
	val published: String? = null,
	val modified: String? = null,
	val description: String? = null,
	val subject: List<String>? = null,
	val duration: Double? = null,
	val properties: Map<String, JsonElement>? = null
)

@Serializable
internal data class BinderyContributorDto(
	val name: String? = null
)

@Serializable
internal data class BinderyLinkDto(
	val href: String? = null,
	val title: String? = null,
	val type: String? = null,
	val rel: JsonElement? = null,
	val properties: Map<String, JsonElement>? = null,
	val images: List<BinderyLinkDto>? = null,
	val links: List<BinderyLinkDto>? = null,
	val duration: Double? = null
)

internal fun BinderyCatalogDto.toCatalog(): BinderyCatalog {
	val safeMetadata = metadata ?: BinderyMetadataDto()
	val decodedProperties = safeMetadata.properties.orEmpty() + properties.orEmpty()
	return BinderyCatalog(
		title = safeMetadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Bindery",
		identifier = safeMetadata.identifier?.trim()?.takeIf { it.isNotEmpty() },
		description = safeMetadata.description?.trim()?.takeIf { it.isNotEmpty() },
		subjects = safeMetadata.subject.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		availability = decodedProperties.toAvailability(),
		properties = decodedProperties.toStringProperties(),
		propertyValues = decodedProperties.toPropertyBag(),
		images = images.orEmpty().mapNotNull { it.toLink() },
		links = links.orEmpty().mapNotNull { it.toLink() },
		navigation = navigation.orEmpty().mapNotNull { it.toLink() },
		publications = publications.orEmpty().map { it.toPublication() },
		finding = decodedProperties.toFindingMetadata()
	)
}

private fun BinderyPublicationDto.toPublication(): BinderyPublication {
	val safeMetadata = metadata ?: BinderyMetadataDto()
	val decodedProperties = safeMetadata.properties.orEmpty() + properties.orEmpty()
	return BinderyPublication(
		id = safeMetadata.identifier?.trim()?.takeIf { it.isNotEmpty() },
		title = safeMetadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled",
		author = safeMetadata.author.orEmpty().firstNotNullOfOrNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
		published = safeMetadata.published?.trim()?.takeIf { it.isNotEmpty() },
		description = safeMetadata.description?.trim()?.takeIf { it.isNotEmpty() },
		subjects = safeMetadata.subject.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		durationSeconds = safeMetadata.duration?.takeIf { it > 0.0 },
		availability = decodedProperties.toAvailability(),
		properties = decodedProperties.toStringProperties(),
		propertyValues = decodedProperties.toPropertyBag(),
		links = links.orEmpty().mapNotNull { it.toLink() },
		images = images.orEmpty().mapNotNull { it.toLink() },
		readingOrder = readingOrder.orEmpty().mapNotNull { it.toReadingOrderItem() },
		sync = decodedProperties.toBookSync(),
		finding = decodedProperties.toFindingMetadata()
	)
}

internal fun BinderyPublicationDto.toManifest(): BinderyManifest {
	val safeMetadata = metadata ?: BinderyMetadataDto()
	val decodedProperties = safeMetadata.properties.orEmpty() + properties.orEmpty()
	return BinderyManifest(
		id = safeMetadata.identifier?.trim()?.takeIf { it.isNotEmpty() },
		title = safeMetadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled",
		author = safeMetadata.author.orEmpty().firstNotNullOfOrNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
		published = safeMetadata.published?.trim()?.takeIf { it.isNotEmpty() },
		description = safeMetadata.description?.trim()?.takeIf { it.isNotEmpty() },
		subjects = safeMetadata.subject.orEmpty().mapNotNull { it.trim().takeIf(String::isNotEmpty) },
		durationSeconds = safeMetadata.duration?.takeIf { it > 0.0 },
		availability = decodedProperties.toAvailability(),
		properties = decodedProperties.toStringProperties(),
		propertyValues = decodedProperties.toPropertyBag(),
		links = links.orEmpty().mapNotNull { it.toLink() },
		images = images.orEmpty().mapNotNull { it.toLink() },
		readingOrder = readingOrder.orEmpty().mapNotNull { it.toReadingOrderItem() },
		sync = decodedProperties.toBookSync()
	)
}

internal fun BinderyResourceCatalogDto.toResourceCatalog(): BinderyResourceCatalog {
	val safeMetadata = metadata ?: BinderyMetadataDto()
	return BinderyResourceCatalog(
		title = safeMetadata.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Resources",
		resources = resources.orEmpty().mapNotNull { it.toBookResource() }
	)
}

internal fun decodeBinderyCatalogJson(jsonText: String): BinderyCatalog =
	BinderyJson.decodeFromString<BinderyCatalogDto>(jsonText).toCatalog()

internal fun decodeBinderyManifestJson(jsonText: String): BinderyManifest =
	BinderyJson.decodeFromString<BinderyPublicationDto>(jsonText).toManifest()

internal fun decodeBinderyResourceCatalogJson(jsonText: String): BinderyResourceCatalog =
	BinderyJson.decodeFromString<BinderyResourceCatalogDto>(jsonText).toResourceCatalog()

internal fun decodeBinderyBookSyncJson(jsonText: String): BinderyBookSync {
	val element = BinderyJson.parseToJsonElement(jsonText)
	val root = element as? JsonObject ?: return BinderyJson.decodeFromJsonElement(element)
	val properties = root["properties"] as? JsonObject
	val propertiesSync = properties
		?.takeIf { values ->
			values.containsKey("syncPairs") ||
				values.containsKey("syncPairCounts") ||
				values.containsKey("whispersyncStatus")
		}
		?.let { values -> BinderyJson.decodeFromJsonElement<BinderyBookSync>(values) }
	return propertiesSync ?: BinderyJson.decodeFromJsonElement(root)
}

private fun Map<String, JsonElement>.toBookSync(): BinderyBookSync? =
	takeIf { values ->
		values.containsKey("syncPairs") ||
			values.containsKey("syncPairCounts") ||
			values.containsKey("whispersyncStatus")
	}?.let { values ->
		BinderyJson.decodeFromJsonElement<BinderyBookSync>(JsonObject(values))
	}

private fun BinderyLinkDto.toReadingOrderItem(): BinderyReadingOrderItem? {
	val safeHref = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val safeProperties = properties.orEmpty()
	val stringProperties = safeProperties.toStringProperties()
	return BinderyReadingOrderItem(
		href = safeHref,
		title = title?.trim()?.takeIf { it.isNotEmpty() } ?: safeHref.substringAfterLast('/'),
		type = type?.trim()?.takeIf { it.isNotEmpty() },
		durationSeconds = duration?.takeIf { it > 0.0 },
		sizeBytes = stringProperties.firstNonBlankValue("size")?.toLongOrNull(),
		properties = stringProperties,
		propertyValues = safeProperties.toPropertyBag(),
		metadata = safeProperties.toResourceMetadata()
	)
}

private fun BinderyLinkDto.toLink(): BinderyLink? {
	val safeHref = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val safeProperties = properties.orEmpty()
	return BinderyLink(
		href = safeHref,
		title = title?.trim()?.takeIf { it.isNotEmpty() },
		type = type?.trim()?.takeIf { it.isNotEmpty() },
		rel = rel.toRelList(),
		availability = safeProperties.toAvailability(),
		properties = safeProperties.toStringProperties(),
		propertyValues = safeProperties.toPropertyBag(),
		images = images.orEmpty().mapNotNull { it.toLink() },
		links = links.orEmpty().mapNotNull { it.toLink() }
	)
}

private fun BinderyLinkDto.toBookResource(): BinderyBookResource? {
	val safeHref = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
	val safeProperties = properties.orEmpty()
	val stringProperties = safeProperties.toStringProperties()
	return BinderyBookResource(
		href = safeHref,
		title = title?.trim()?.takeIf { it.isNotEmpty() } ?: safeHref.substringAfterLast('/'),
		type = type?.trim()?.takeIf { it.isNotEmpty() },
		kind = stringProperties.firstNonBlankValue("kind"),
		durationSeconds = duration?.takeIf { it > 0.0 },
		sizeBytes = stringProperties.firstNonBlankValue("size")?.toLongOrNull(),
		properties = stringProperties,
		propertyValues = safeProperties.toPropertyBag(),
		metadata = safeProperties.toResourceMetadata()
	)
}

private fun Map<String, JsonElement>.toStringProperties(): Map<String, String> =
	mapNotNull { (key, value) ->
		(value as? JsonPrimitive)
			?.contentOrNull
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.let { key to it }
	}.toMap()

private fun Map<String, JsonElement>.toPropertyBag(): BinderyPropertyBag =
	BinderyPropertyBag(
		mapNotNull { (key, value) ->
			if (!value.shouldKeepPropertyBagEntry(key)) {
				null
			} else {
				value.toBinderyPropertyValue()?.let { key to it }
			}
		}.toMap()
	)

private fun JsonElement.shouldKeepPropertyBagEntry(key: String): Boolean =
	if (key.equals("audio", ignoreCase = true) && this is JsonObject) {
		toAudioMetadata(emptyMap()) != null
	} else {
		true
	}

private fun JsonElement.toBinderyPropertyValue(): BinderyPropertyValue? =
	when (this) {
		is JsonObject -> BinderyPropertyValue.ObjectValue(
			mapNotNull { (key, value) ->
				value.toBinderyPropertyValue()?.let { key to it }
			}.toMap()
		)
		is JsonArray -> BinderyPropertyValue.ArrayValue(mapNotNull { it.toBinderyPropertyValue() })
		is JsonPrimitive -> {
			val content = contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return null
			when {
				this.isString -> BinderyPropertyValue.StringValue(content)
				content.equals("true", ignoreCase = true) || content.equals("false", ignoreCase = true) ->
					BinderyPropertyValue.BooleanValue(content.toBooleanStrict())
				content.toDoubleOrNull() != null -> BinderyPropertyValue.NumberValue(content.toDouble(), content)
				else -> BinderyPropertyValue.StringValue(content)
			}
		}
	}

private fun Map<String, JsonElement>.toResourceMetadata(): BinderyResourceMetadata {
	val audioObject = jsonObject("audio")
	val sourceReleaseObject = jsonObject("sourceRelease")
	return BinderyResourceMetadata(
		kind = stringValue("kind"),
		format = stringValue("format"),
		artifactType = stringValue("artifactType"),
		resourceKey = stringValue("resourceKey"),
		bookFileId = stringValue("bookFileId")?.takeUnless { it == "0" },
		relativePath = stringValue("relativePath"),
		durationMs = longValue("durationMs") ?: longValue("duration_ms"),
		sizeBytes = longValue("sizeBytes") ?: longValue("size"),
		deliveryPolicy = stringValue("deliveryPolicy"),
		origin = stringValue("origin"),
		language = stringValue("language"),
		version = stringValue("version"),
		chapterLabel = stringValue("chapterLabel"),
		sectionLabel = stringValue("sectionLabel"),
		trackNumber = intValue("trackNumber"),
		discNumber = intValue("discNumber"),
		narrator = stringValue("narrator"),
		author = stringValue("author"),
		editionSuffix = stringValue("editionSuffix"),
		sourceProvider = stringValue("sourceProvider") ?: stringValue("provider"),
		sourceUrl = stringValue("sourceUrl"),
		findingId = stringValue("findingId"),
		findingHref = stringValue("findingHref"),
		audio = audioObject.toAudioMetadata(this),
		sourceRelease = sourceReleaseObject?.toSourceReleaseMetadata()
	)
}

private fun JsonObject?.toAudioMetadata(fallback: Map<String, JsonElement>): BinderyAudioMetadata? {
	val audio = BinderyAudioMetadata(
		codec = this?.stringValue("codec") ?: fallback.stringValue("codec"),
		bitrateKbps = bitrateKbpsValue(fallback),
		sampleRateHz = sampleRateHzValue(fallback),
		channels = (this?.intValue("channels") ?: fallback.intValue("channels"))?.takeIf { it > 0 },
		qualityLabel = this?.stringValue("qualityLabel") ?: fallback.stringValue("qualityLabel"),
		qualityScore = this?.doubleValue("qualityScore") ?: fallback.doubleValue("qualityScore")
	)
	return audio.takeIf(BinderyAudioMetadata::hasContent)
}

private fun JsonObject?.bitrateKbpsValue(fallback: Map<String, JsonElement>): Int? {
	val directKbps = this?.intValue("bitrateKbps") ?: fallback.intValue("bitrateKbps")
	if (directKbps != null && directKbps > 0) return directKbps
	val bitrateBps = this?.longValue("bitrate")
		?: this?.longValue("bitrateBps")
		?: fallback.longValue("bitrate")
		?: fallback.longValue("bitrateBps")
	return bitrateBps
		?.takeIf { it > 0L }
		?.let { (it / 1000L).coerceAtLeast(1L).toInt() }
}

private fun JsonObject?.sampleRateHzValue(fallback: Map<String, JsonElement>): Long? {
	val directHz = this?.longValue("sampleRateHz")
		?: this?.longValue("sampleRate")
		?: fallback.longValue("sampleRateHz")
		?: fallback.longValue("sampleRate")
	if (directHz != null && directHz > 0L) return directHz
	val sampleRateKHz = this?.doubleValue("sampleRateKHz") ?: fallback.doubleValue("sampleRateKHz")
	return sampleRateKHz
		?.takeIf { it > 0.0 }
		?.let { (it * 1000.0).roundToLong() }
		?.takeIf { it > 0L }
}

private fun JsonObject.toSourceReleaseMetadata(): BinderySourceReleaseMetadata? {
	val release = BinderySourceReleaseMetadata(
		provider = stringValue("provider"),
		title = stringValue("title"),
		sourceUrl = stringValue("sourceUrl"),
		narrator = stringValue("narrator"),
		readBy = stringValue("readBy"),
		edition = stringValue("edition"),
		editionType = stringValue("editionType"),
		format = stringValue("format"),
		bitrate = stringValue("bitrate"),
		categories = stringList("categories"),
		keywords = stringList("keywords")
	)
	return release.takeIf(BinderySourceReleaseMetadata::hasContent)
}

private fun BinderyAudioMetadata.hasContent(): Boolean =
	codec != null ||
		bitrateKbps != null ||
		sampleRateHz != null ||
		channels != null ||
		qualityLabel != null ||
		qualityScore != null

private fun BinderySourceReleaseMetadata.hasContent(): Boolean =
	listOf(provider, title, sourceUrl, narrator, readBy, edition, editionType, format, bitrate).any { !it.isNullOrBlank() } ||
		categories.isNotEmpty() ||
		keywords.isNotEmpty()

private fun Map<String, JsonElement>.toFindingMetadata(): BinderyFindingMetadata? {
	val files = jsonArray("files").mapNotNull { (it as? JsonObject)?.toFindingFile() }
	val mappings = jsonArray("mappings").mapNotNull { (it as? JsonObject)?.toFindingMapping() }
	val finding = BinderyFindingMetadata(
		findingId = stringValue("findingId") ?: stringValue("id"),
		provider = stringValue("provider"),
		providerKind = stringValue("providerKind"),
		mediaType = stringValue("mediaType") ?: stringValue("kind"),
		format = stringValue("format"),
		language = stringValue("language"),
		author = stringValue("author"),
		bookTitleHint = stringValue("bookTitleHint") ?: stringValue("bookTitle"),
		edition = stringValue("edition") ?: stringValue("version"),
		narrator = stringValue("narrator"),
		publisher = stringValue("publisher"),
		protocol = stringValue("protocol"),
		fileCount = intValue("fileCount"),
		sizeBytes = longValue("sizeBytes") ?: longValue("size") ?: longValue("selectedBytes"),
		bitrateBps = longValue("bitrateBps")?.takeIf { it > 0L },
		sampleRateHz = longValue("sampleRateHz")?.takeIf { it > 0L },
		availabilityStatus = stringValue("availabilityStatus"),
		availabilityReason = stringValue("availabilityReason"),
		sourceUrl = stringValue("sourceUrl") ?: stringValue("downloadUrl"),
		coverUrl = stringValue("coverUrl") ?: stringValue("image") ?: stringValue("cover"),
		publishedDate = stringValue("publishedDate"),
		uploadDate = stringValue("uploadDate"),
		providerComments = stringValue("providerComments") ?: stringValue("providerNotes"),
		files = files,
		mappings = mappings
	)
	return finding.takeIf(BinderyFindingMetadata::hasContent)
}

private fun JsonObject.toFindingFile(): BinderyFindingFile? {
	val file = BinderyFindingFile(
		name = stringValue("name")
			?: stringValue("title")
			?: stringValue("displayName")
			?: stringValue("relativePath")
			?: stringValue("path")?.substringAfterLast('/')?.substringAfterLast('\\'),
		href = stringValue("href") ?: stringValue("url"),
		format = stringValue("format") ?: stringValue("extension"),
		language = stringValue("language"),
		sizeBytes = (longValue("sizeBytes") ?: longValue("size"))?.takeIf { it > 0L },
		durationSeconds = (doubleValue("durationSeconds")
			?: doubleValue("duration")
			?: longValue("durationMs")?.let { it.toDouble() / 1000.0 })?.takeIf { it > 0.0 },
		bitrateBps = longValue("bitrateBps")?.takeIf { it > 0L },
		sampleRateHz = longValue("sampleRateHz")?.takeIf { it > 0L }
	)
	return file.takeIf(BinderyFindingFile::hasContent)
}

private fun BinderyFindingFile.hasContent(): Boolean =
	listOf(name, href, format, language).any { !it.isNullOrBlank() } ||
		sizeBytes != null ||
		durationSeconds != null ||
		bitrateBps != null ||
		sampleRateHz != null

private fun JsonObject.toFindingMapping(): BinderyFindingMapping =
	BinderyFindingMapping(
		id = stringValue("id"),
		bookId = stringValue("bookId"),
		bookTitle = stringValue("bookTitle") ?: stringValue("title"),
		authorName = stringValue("authorName") ?: stringValue("author"),
		confidence = doubleValue("confidence"),
		mediaType = stringValue("mediaType"),
		targetLanguage = stringValue("targetLanguage") ?: stringValue("language"),
		acquisitionStatus = stringValue("acquisitionStatus"),
		acquisitionScope = stringValue("acquisitionScope"),
		selectedBytes = longValue("selectedBytes"),
		bookFileId = stringValue("bookFileId")?.takeUnless { it == "0" },
		bookFileFormat = stringValue("bookFileFormat"),
		bookFileSizeBytes = longValue("bookFileSizeBytes"),
		sourceCatalogCandidateId = stringValue("sourceCatalogCandidateId")?.takeUnless { it == "0" }
	)

private fun BinderyFindingMetadata.hasContent(): Boolean =
	listOf(
		findingId,
		provider,
		providerKind,
		mediaType,
		format,
		language,
		author,
		bookTitleHint,
		edition,
		narrator,
		publisher,
		protocol,
		availabilityStatus,
		availabilityReason,
		sourceUrl,
		coverUrl,
		publishedDate,
		uploadDate,
		providerComments
	).any { !it.isNullOrBlank() } ||
		fileCount != null ||
		sizeBytes != null ||
		bitrateBps != null ||
		sampleRateHz != null ||
		files.isNotEmpty() ||
		mappings.isNotEmpty()

private fun Map<String, JsonElement>.toAvailability(): BinderyAvailability? {
	val value = this["availability"] as? JsonObject ?: return null
	return BinderyAvailability(
		owned = value.booleanValue("owned") ?: false,
		complete = value.booleanValue("complete") ?: false,
		ownedBooks = value.intValue("ownedBooks"),
		missingBooks = value.intValue("missingBooks"),
		totalBooks = value.intValue("totalBooks"),
		formats = value.stringList("formats"),
		ownedFormats = value.stringList("ownedFormats"),
		ownedLanguages = value.stringList("ownedLanguages"),
		ownedCombinations = value.objectList("ownedCombinations")
			.mapNotNull(JsonObject::toAvailabilityCombination),
		languages = value.stringList("languages"),
		mode = value.stringValue("mode")
	)
}
