package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyFindingFile
import paige.navic.domain.repositories.BinderyFindingMapping
import paige.navic.domain.repositories.BinderyFindingMetadata

enum class BinderyBookFindingKind {
	Audiobook,
	Ebook
}

enum class BinderyBookFindingRowAction {
	Play
}

data class BinderyBookFindingRow(
	val id: String,
	val key: String,
	val kind: BinderyBookFindingKind,
	val title: String,
	val subtitle: String?,
	val card: BinderyCatalogCard.Finding,
	val readerAction: BinderyBookFindingRowAction = BinderyBookFindingRowAction.Play,
	val readerOpdsAction: BinderyOpdsAction? = null
)

data class BinderyBookFindingGroups(
	val audiobooks: List<BinderyBookFindingRow>,
	val ebooks: List<BinderyBookFindingRow>
) {
	val isEmpty: Boolean
		get() = audiobooks.isEmpty() && ebooks.isEmpty()
}

fun binderyBookFindingRows(
	catalog: BinderyCatalog,
	languageFilter: String? = null,
	currentBookId: String? = null,
	concreteVersionRows: List<BinderyBookVersionRow> = emptyList()
): BinderyBookFindingGroups {
	val language = normalizedBinderyAvailabilityLanguageFilter(languageFilter)
	val rows = binderyCatalogCards(catalog, BinderyCatalogTab.Findings)
		.filterIsInstance<BinderyCatalogCard.Finding>()
		.mapIndexedNotNull { index, card ->
			val metadata = card.finding
			if (language != null && metadata?.language?.normalizedBinderyAvailabilityLanguage() != language) {
				return@mapIndexedNotNull null
			}
			if (!card.isAvailableFindingCandidate(languageFilter)) {
				return@mapIndexedNotNull null
			}
			val kind = metadata.findingKind() ?: return@mapIndexedNotNull null
			if (metadata.isImportedCurrentBookFindingAlreadyRepresented(
					currentBookId = currentBookId,
					kind = kind,
					language = language,
					concreteVersionRows = concreteVersionRows
				)
			) {
				return@mapIndexedNotNull null
			}
			BinderyBookFindingRow(
				id = metadata?.findingId?.trim()?.takeIf { it.isNotEmpty() } ?: card.id,
				key = binderyUiStableKey(
					prefix = "bindery-book-finding",
					index = index,
					card.path,
					metadata?.findingId,
					card.id,
					card.title
				),
				kind = kind,
				title = metadata.findingTitle(kind, card.title),
				subtitle = metadata.findingSubtitle(card.subtitle, kind),
				card = card
			)
		}
	val audioRows = rows
		.filter { row -> row.kind == BinderyBookFindingKind.Audiobook }
		.sortedWith(
			compareByDescending<BinderyBookFindingRow> { row -> row.card.finding.findingAudioQualityRank() }
				.thenByDescending { row -> row.card.finding?.bitrateBps ?: 0L }
				.thenByDescending { row -> row.card.finding?.sampleRateHz ?: 0L }
				.thenByDescending { row -> row.card.finding?.sizeBytes ?: 0L }
				.thenBy { row -> row.title.lowercase() }
		)
	val ebookRows = rows
		.filter { row -> row.kind == BinderyBookFindingKind.Ebook }
		.sortedWith(
			compareByDescending<BinderyBookFindingRow> { row -> row.card.finding?.sizeBytes ?: 0L }
				.thenByDescending { row -> row.card.finding.findingEbookQualityRank() }
				.thenBy { row -> row.title.lowercase() }
		)
	return BinderyBookFindingGroups(audioRows, ebookRows)
}

private fun BinderyFindingMetadata?.isImportedCurrentBookFindingAlreadyRepresented(
	currentBookId: String?,
	kind: BinderyBookFindingKind,
	language: String?,
	concreteVersionRows: List<BinderyBookVersionRow>
): Boolean {
	val metadata = this ?: return false
	val status = metadata.availabilityStatus?.trim()?.lowercase()
	if (status != "imported") return false
	if (concreteVersionRows.none { row -> row.kind.representsFindingKind(kind) }) return false
	val normalizedBookId = currentBookId
		?.let(::binderyBookRouteId)
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }
		?: return false
	return metadata.mappings.any { mapping ->
		mapping.bookId
			?.let(::binderyBookRouteId)
			?.trim()
			?.lowercase() == normalizedBookId &&
			mapping.acquisitionStatus?.trim()?.lowercase() == "imported" &&
			mapping.matchesFindingKind(kind, metadata) &&
			mapping.matchesFindingLanguage(language, metadata)
	}
}

private fun BinderyBookVersionKind.representsFindingKind(kind: BinderyBookFindingKind): Boolean =
	when (kind) {
		BinderyBookFindingKind.Audiobook -> this == BinderyBookVersionKind.Audiobook
		BinderyBookFindingKind.Ebook -> this == BinderyBookVersionKind.Ebook ||
			this == BinderyBookVersionKind.Readaloud
	}

internal fun BinderyFindingMapping.matchesFindingKind(
	kind: BinderyBookFindingKind,
	metadata: BinderyFindingMetadata
): Boolean {
	val expected = when (kind) {
		BinderyBookFindingKind.Audiobook -> "audiobook"
		BinderyBookFindingKind.Ebook -> "ebook"
	}
	val actual = mediaType?.normalizedBinderyMediaFormat()
		?: metadata.mediaType?.normalizedBinderyMediaFormat()
		?: metadata.format?.normalizedBinderyMediaFormat()
	return actual == expected
}

internal fun BinderyFindingMapping.matchesFindingLanguage(
	language: String?,
	metadata: BinderyFindingMetadata
): Boolean {
	if (language == null) return true
	val actual = targetLanguage?.normalizedBinderyAvailabilityLanguage()
		?: metadata.language?.normalizedBinderyAvailabilityLanguage()
	return actual == language
}

internal fun binderyFindingFileRowKey(file: BinderyFindingFile, index: Int): String =
	binderyUiStableKey(
		prefix = "bindery-finding-file",
		index = index,
		file.href,
		file.name,
		file.format,
		file.language
	)

internal fun binderyFindingMappingRowKey(mapping: BinderyFindingMapping, index: Int): String =
	binderyUiStableKey(
		prefix = "bindery-finding-mapping",
		index = index,
		mapping.id,
		mapping.bookId,
		mapping.bookTitle,
		mapping.authorName
	)

internal fun List<BinderyFindingMapping>.collapsedForBinderyFindingDetail(): List<BinderyFindingMapping> {
	val mappingsByVisibleIdentity = linkedMapOf<String, BinderyFindingMapping>()
	forEachIndexed { index, mapping ->
		val key = mapping.findingDetailIdentityKey() ?: "mapping-$index"
		val existing = mappingsByVisibleIdentity[key]
		if (existing == null || mapping.isBetterFindingDetailMappingThan(existing)) {
			mappingsByVisibleIdentity[key] = mapping
		}
	}
	return mappingsByVisibleIdentity.values.toList()
}

private fun BinderyFindingMapping.findingDetailIdentityKey(): String? {
	val bookIdentity = bookId
		?.let(::binderyBookRouteId)
		?.normalizedDuplicateRowField()
		?.takeIf { it.isNotEmpty() }
		?: listOfNotNull(
			bookTitle.normalizedDuplicateRowField().takeIf { it.isNotEmpty() },
			authorName.normalizedDuplicateRowField().takeIf { it.isNotEmpty() }
		).joinToString(separator = "\u001f").takeIf { it.isNotEmpty() }
		?: return null
	val media = mediaType
		?.normalizedBinderyMediaFormat()
		?: mediaType.normalizedDuplicateRowField()
	val language = targetLanguage
		?.normalizedBinderyAvailabilityLanguage()
		?: targetLanguage.normalizedDuplicateRowField()
	return listOf(bookIdentity, media, language).joinToString(separator = "\u001f")
}

private fun BinderyFindingMapping.isBetterFindingDetailMappingThan(
	other: BinderyFindingMapping
): Boolean {
	val confidence = confidence ?: -1.0
	val otherConfidence = other.confidence ?: -1.0
	if (confidence != otherConfidence) return confidence > otherConfidence

	val statusRank = acquisitionStatus.findingDetailStatusRank()
	val otherStatusRank = other.acquisitionStatus.findingDetailStatusRank()
	if (statusRank != otherStatusRank) return statusRank > otherStatusRank

	val selectedBytes = selectedBytes?.takeIf { it > 0L } ?: 0L
	val otherSelectedBytes = other.selectedBytes?.takeIf { it > 0L } ?: 0L
	if (selectedBytes != otherSelectedBytes) return selectedBytes > otherSelectedBytes

	val fileSizeBytes = bookFileSizeBytes?.takeIf { it > 0L } ?: 0L
	val otherFileSizeBytes = other.bookFileSizeBytes?.takeIf { it > 0L } ?: 0L
	if (fileSizeBytes != otherFileSizeBytes) return fileSizeBytes > otherFileSizeBytes

	return hasConcreteBookFileId() && !other.hasConcreteBookFileId()
}

private fun String?.findingDetailStatusRank(): Int =
	when (this?.trim()?.lowercase()) {
		"selected" -> 50
		"imported" -> 40
		"downloaded", "downloadable", "available", "ready", "owned", "acquired" -> 30
		"failed", "rejected", "excluded", "unknown" -> 0
		else -> 10
	}

private fun BinderyFindingMapping.hasConcreteBookFileId(): Boolean =
	bookFileId?.trim()?.takeIf { it.isNotEmpty() && it != "0" } != null
