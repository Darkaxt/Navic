package paige.navic.ui.screens.bindery

import io.ktor.http.encodeURLParameter
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.repositories.BinderyAudiobookVersion
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyAvailability
import paige.navic.domain.repositories.BinderyBookSync
import paige.navic.domain.repositories.BinderyBookResource
import paige.navic.domain.repositories.BinderyFindingFile
import paige.navic.domain.repositories.BinderyFindingMapping
import paige.navic.domain.repositories.BinderyFindingMetadata
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.domain.repositories.BinderySyncPair
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.domain.repositories.configuredBinderyOpdsBaseUrl
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationKind
import paige.navic.ui.navigation.Screen
import paige.navic.ui.navigation.SearchScope
import paige.navic.ui.screens.bindery.versionpolicy.audioFormatQualityRank
import paige.navic.ui.screens.bindery.versionpolicy.displayToken
import paige.navic.ui.screens.bindery.versionpolicy.ebookFormatQualityRank
import paige.navic.ui.screens.bindery.versionpolicy.fileExtension
import paige.navic.ui.screens.bindery.versionpolicy.fileNameStem
import paige.navic.ui.screens.bindery.versionpolicy.firstNonBlankValue
import paige.navic.ui.screens.bindery.versionpolicy.hasTruthyValue
import paige.navic.ui.screens.bindery.versionpolicy.isEbookMediaType
import paige.navic.ui.screens.bindery.versionpolicy.isGenericBookMediaFormat
import paige.navic.ui.screens.bindery.versionpolicy.leadingBracketLabel
import paige.navic.ui.screens.bindery.versionpolicy.toBitrateLabel
import paige.navic.ui.screens.bindery.versionpolicy.toReadableBookFormat
import paige.navic.ui.screens.bindery.versionpolicy.toSampleRateLabel
import paige.navic.util.core.toFileSize
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class BinderyBookVersionKind {
	Audiobook,
	Readaloud,
	Ebook
}

enum class BinderyBookVersionRoutingAction {
	OpenAudiobook,
	OpenReadaloud,
	OpenEbook
}

enum class BinderyWhispersyncAudiobookLaunchAction {
	None,
	OpenDirectly,
	ChooseAudiobook
}

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

private fun BinderyFindingMapping.matchesFindingKind(
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

private fun BinderyFindingMapping.matchesFindingLanguage(
	language: String?,
	metadata: BinderyFindingMetadata
): Boolean {
	if (language == null) return true
	val actual = targetLanguage?.normalizedBinderyAvailabilityLanguage()
		?: metadata.language?.normalizedBinderyAvailabilityLanguage()
	return actual == language
}

internal fun binderyUiStableKey(
	prefix: String,
	index: Int,
	vararg candidates: String?
): String {
	val identity = candidates
		.firstNotNullOfOrNull { candidate -> candidate?.trim()?.takeIf { it.isNotEmpty() } }
		?: "item"
	return "$prefix-$index-$identity"
}

internal fun binderyCatalogCardLazyKey(card: BinderyCatalogCard, index: Int): String =
	binderyUiStableKey("bindery-card", index, card.id, card.title)

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

data class BinderyBookVersionRow(
	val id: String,
	val kind: BinderyBookVersionKind,
	val title: String,
	val subtitle: String?,
	val format: ReaderPublicationFormat = ReaderPublicationFormat.Epub,
	val finding: BinderyCatalogCard.Finding? = null,
	val audiobookId: String? = null,
	val audiobookBookFileId: String? = null,
	val ebookBookFileId: String? = null,
	val syncMatches: List<BinderyWhispersyncMatch> = emptyList()
)

data class BinderyWhispersyncMatch(
	val oppositeTitle: String,
	val oppositeKind: BinderyBookVersionKind,
	val artifactId: String,
	val sidecarHref: String,
	val coveragePercent: Int?,
	val scorePercent: Int?,
	val oppositeAudiobookId: String? = null,
	val oppositeAudiobookBookFileId: String? = null
)

data class BinderyBookVersionGroups(
	val audiobooks: List<BinderyBookVersionRow>,
	val ebooks: List<BinderyBookVersionRow>
) {
	val isEmpty: Boolean
		get() = audiobooks.isEmpty() && ebooks.isEmpty()
}

fun binderyBookVersionGroups(rows: List<BinderyBookVersionRow>): BinderyBookVersionGroups =
	BinderyBookVersionGroups(
		audiobooks = rows.filter { row ->
			row.kind == BinderyBookVersionKind.Audiobook ||
				row.kind == BinderyBookVersionKind.Readaloud
		},
		ebooks = rows.filter { row -> row.kind == BinderyBookVersionKind.Ebook }
	)

internal fun binderyBookVersionRowLazyKey(row: BinderyBookVersionRow, index: Int): String =
	binderyUiStableKey(
		prefix = "bindery-book-version",
		index = index,
		row.id,
		row.title,
		row.subtitle
	)

fun BinderyBookVersionRow.routingAction(): BinderyBookVersionRoutingAction =
	when (kind) {
		BinderyBookVersionKind.Audiobook -> BinderyBookVersionRoutingAction.OpenAudiobook
		BinderyBookVersionKind.Readaloud -> BinderyBookVersionRoutingAction.OpenReadaloud
		BinderyBookVersionKind.Ebook -> BinderyBookVersionRoutingAction.OpenEbook
	}

fun binderyReaderDestinationForVersionRow(
	row: BinderyBookVersionRow,
	bookId: String,
	bookTitle: String,
	opdsBaseUrl: String,
	readaloudMediaOverlayEnabled: Boolean = true
): Screen.Reader? =
	when (row.routingAction()) {
		BinderyBookVersionRoutingAction.OpenReadaloud -> Screen.Reader(
			title = bookTitle,
			publicationUrl = binderyEndpoint(opdsBaseUrl, row.id),
			bookId = bookId,
			resourceHref = row.id,
			kind = ReaderPublicationKind.Readaloud,
			publicationFormat = ReaderPublicationFormat.Epub,
			mediaOverlayEnabled = readaloudMediaOverlayEnabled
		)
		BinderyBookVersionRoutingAction.OpenEbook -> Screen.Reader(
			title = bookTitle,
			publicationUrl = binderyEndpoint(opdsBaseUrl, row.id),
			bookId = bookId,
			resourceHref = row.id,
			kind = ReaderPublicationKind.Ebook,
			publicationFormat = row.format,
			mediaOverlayEnabled = false
		)
		BinderyBookVersionRoutingAction.OpenAudiobook -> null
	}

fun BinderyBookVersionRow.whispersyncAudiobookLaunchMatches(): List<BinderyWhispersyncMatch> =
	if (kind != BinderyBookVersionKind.Ebook) {
		emptyList()
	} else {
		syncMatches.filter { match ->
			match.oppositeKind == BinderyBookVersionKind.Audiobook &&
				match.oppositeAudiobookId.normalizedBookFileId() != null &&
				match.oppositeAudiobookBookFileId.normalizedBookFileId() != null
		}
	}

fun BinderyBookVersionRow.whispersyncAudiobookLaunchAction(): BinderyWhispersyncAudiobookLaunchAction =
	when (whispersyncAudiobookLaunchMatches().size) {
		0 -> BinderyWhispersyncAudiobookLaunchAction.None
		1 -> BinderyWhispersyncAudiobookLaunchAction.OpenDirectly
		else -> BinderyWhispersyncAudiobookLaunchAction.ChooseAudiobook
	}

fun binderyWhispersyncReaderDestinationForMatch(
	ebookRow: BinderyBookVersionRow,
	match: BinderyWhispersyncMatch,
	bookId: String,
	bookTitle: String,
	opdsBaseUrl: String
): Screen.Reader? {
	if (ebookRow.kind != BinderyBookVersionKind.Ebook) return null
	if (match.oppositeKind != BinderyBookVersionKind.Audiobook) return null

	val resourceHref = ebookRow.id.trim().takeIf { it.isNotEmpty() } ?: return null
	val sidecarHref = match.sidecarHref.trim().takeIf { it.isNotEmpty() } ?: return null
	val audiobookId = match.oppositeAudiobookId.normalizedBookFileId() ?: return null
	val audiobookBookFileId = match.oppositeAudiobookBookFileId.normalizedBookFileId() ?: return null

	return Screen.Reader(
		title = bookTitle,
		publicationUrl = binderyEndpoint(opdsBaseUrl, resourceHref),
		bookId = bookId,
		resourceHref = resourceHref,
		kind = ReaderPublicationKind.Ebook,
		publicationFormat = ebookRow.format,
		mediaOverlayEnabled = false,
		whispersyncSidecarUrl = binderyEndpoint(opdsBaseUrl, sidecarHref),
		whispersyncArtifactId = match.artifactId,
		whispersyncAudiobookId = audiobookId,
		whispersyncAudiobookBookFileId = audiobookBookFileId,
		whispersyncAudiobookTitle = match.oppositeTitle.takeIf { it.isNotBlank() }
	)
}

fun binderyBookVersionRows(
	manifest: BinderyManifest?,
	resourceCatalog: BinderyResourceCatalog?,
	languageFilter: String? = null,
	findingsCatalog: BinderyCatalog? = null,
	bookId: String? = manifest?.id,
	audiobookVersions: List<BinderyAudiobookVersion> = emptyList(),
	bookSync: BinderyBookSync? = null
): List<BinderyBookVersionRow> {
	val language = normalizedBinderyAvailabilityLanguageFilter(languageFilter)
	val findingByBookFileId = findingsCatalog.findingCardsByBookFileId(
		currentBookId = bookId ?: manifest?.id,
		language = language
	)
	val manifestReadingOrder = manifest?.readingOrder.orEmpty()
	val audioItems = manifestReadingOrder
		.filter(BinderyReadingOrderItem::isAudioResource)
		.ifEmpty {
			resourceCatalog?.resources.orEmpty()
				.filter(BinderyBookResource::isAudioResource)
				.map(BinderyBookResource::toReadingOrderItem)
		}
		.filter { item -> item.matchesLanguage(language) }
	val resourceBookItems = resourceCatalog?.resources.orEmpty()
	val readaloudResources = resourceBookItems
		.filter(BinderyBookResource::isReadaloudResource)
		.filter { resource -> resource.matchesLanguage(language) }
		.distinctBy { resource -> resource.href }
	val ebookResources = (
		resourceBookItems.filter { resource -> resource.isEbookResource() && !resource.isReadaloudResource() } +
			manifestReadingOrder
				.filter { item -> item.isEbookResource() && !item.isReadaloudResource() }
				.map(BinderyReadingOrderItem::toBookResource) +
			manifest?.links.orEmpty()
				.filter(BinderyLink::isEbookAcquisition)
				.map(BinderyLink::toBookResource)
		)
		.filterNot(BinderyBookResource::isReadaloudResource)
		.filter { resource -> resource.matchesLanguage(language) }
		.distinctBy { resource -> resource.href }

	val audiobookVersionRows = audiobookVersions
		.filter { version -> version.matchesLanguage(language) }
		.sortedWith(
			compareByDescending<BinderyAudiobookVersion> { version -> version.audioFormatQualityRank() }
				.thenByDescending { version -> version.audioBytesPerSecond() }
				.thenByDescending { version -> version.sizeBytes ?: 0L }
				.thenBy { version -> version.audiobookTitleLabel().lowercase() }
		)
		.map(BinderyAudiobookVersion::toAudiobookVersionRow)
	val audioRows = audiobookVersionRows.ifEmpty {
		audioItems
			.groupBy { item -> item.audioEditionKey() }
			.values
			.sortedWith(
				compareByDescending<List<BinderyReadingOrderItem>> { items -> items.audioFormatQualityRank() }
					.thenByDescending { items -> items.audioBytesPerSecond() }
					.thenByDescending { items -> items.totalSizeBytes() }
			)
			.map { items -> items.toAudiobookVersionRow(findingByBookFileId) }
	}
	val readaloudRows = readaloudResources
		.sortedWith(
			compareByDescending<BinderyBookResource> { resource -> resource.sizeBytes ?: 0L }
				.thenBy { resource -> resource.versionTitle().lowercase() }
		)
		.map { resource -> resource.toReadaloudVersionRow(findingByBookFileId) }
	val ebookRows = ebookResources
		.sortedWith(
			compareByDescending<BinderyBookResource> { resource -> resource.sizeBytes ?: 0L }
				.thenByDescending { resource -> resource.ebookFormatQualityRank() }
				.thenBy { resource -> resource.versionTitle().lowercase() }
		)
		.map { resource -> resource.toEbookVersionRow(findingByBookFileId) }

	return (readaloudRows + audioRows + ebookRows)
		.collapsedDuplicateVersionRows()
		.withWhispersyncMatches(bookSync)
}

private fun List<BinderyBookVersionRow>.collapsedDuplicateVersionRows(): List<BinderyBookVersionRow> {
	val rowsByVisibleIdentity = linkedMapOf<String, BinderyBookVersionRow>()
	forEach { row ->
		val key = row.visibleIdentityKey()
		val existing = rowsByVisibleIdentity[key]
		if (existing == null || (existing.finding == null && row.finding != null)) {
			rowsByVisibleIdentity[key] = row
		}
	}
	return rowsByVisibleIdentity.values.toList()
}

private fun List<BinderyBookVersionRow>.withWhispersyncMatches(
	bookSync: BinderyBookSync?
): List<BinderyBookVersionRow> {
	val readyPairs = bookSync?.syncPairs.orEmpty()
		.filter(BinderySyncPair::hasReadyWhispersync)
	if (readyPairs.isEmpty()) return this

	val ebookRowsByBookFileId = filter { row -> row.kind == BinderyBookVersionKind.Ebook }
		.mapNotNull { row -> row.ebookBookFileId?.normalizedDuplicateRowField()?.takeIf { it.isNotEmpty() }?.let { it to row } }
		.toMap()
	val audiobookRowsByBookFileId = filter { row -> row.kind == BinderyBookVersionKind.Audiobook }
		.mapNotNull { row -> row.audiobookBookFileId?.normalizedDuplicateRowField()?.takeIf { it.isNotEmpty() }?.let { it to row } }
		.toMap()

	return map { row ->
		val matches = when (row.kind) {
			BinderyBookVersionKind.Audiobook -> {
				val audioBookFileId = row.audiobookBookFileId.normalizedBookFileId() ?: return@map row
				readyPairs
					.filter { pair -> pair.audiobookBookFileId?.toString() == audioBookFileId }
					.mapNotNull { pair ->
						val ebookBookFileId = pair.ebookBookFileId?.toString()?.normalizedBookFileId() ?: return@mapNotNull null
						pair.toWhispersyncMatch(
							oppositeRow = ebookRowsByBookFileId[ebookBookFileId],
							oppositeKind = BinderyBookVersionKind.Ebook
						)
					}
			}
			BinderyBookVersionKind.Ebook -> {
				val ebookBookFileId = row.ebookBookFileId.normalizedBookFileId() ?: return@map row
				readyPairs
					.filter { pair -> pair.ebookBookFileId?.toString() == ebookBookFileId }
					.mapNotNull { pair ->
						val audioBookFileId = pair.audiobookBookFileId?.toString()?.normalizedBookFileId() ?: return@mapNotNull null
						pair.toWhispersyncMatch(
							oppositeRow = audiobookRowsByBookFileId[audioBookFileId],
							oppositeKind = BinderyBookVersionKind.Audiobook
						)
					}
			}
			BinderyBookVersionKind.Readaloud -> emptyList()
		}
		if (matches.isEmpty()) row else row.copy(syncMatches = matches)
	}
}

private fun BinderySyncPair.hasReadyWhispersync(): Boolean =
	whispersync?.status?.trim()?.equals("ready", ignoreCase = true) == true &&
	whispersync.artifactId != null

private fun BinderySyncPair.toWhispersyncMatch(
	oppositeRow: BinderyBookVersionRow?,
	oppositeKind: BinderyBookVersionKind
): BinderyWhispersyncMatch? {
	val artifact = whispersync ?: return null
	val artifactId = artifact.artifactId?.toString() ?: return null
	return BinderyWhispersyncMatch(
		oppositeTitle = oppositeRow?.title?.takeIf { it.isNotBlank() }
			?: when (oppositeKind) {
				BinderyBookVersionKind.Audiobook -> "Audiobook"
				BinderyBookVersionKind.Ebook -> "Ebook"
				BinderyBookVersionKind.Readaloud -> "Readaloud"
			},
		oppositeKind = oppositeKind,
		artifactId = artifactId,
		sidecarHref = artifact.artifactHref?.trim()?.takeIf { it.isNotEmpty() }
			?: "/opds/books/${bookId ?: ""}/sync/$artifactId",
		coveragePercent = artifact.coverage.toPercent(),
		scorePercent = artifact.score.toPercent(),
		oppositeAudiobookId = if (oppositeKind == BinderyBookVersionKind.Audiobook) {
			oppositeRow?.audiobookId
		} else {
			null
		},
		oppositeAudiobookBookFileId = if (oppositeKind == BinderyBookVersionKind.Audiobook) {
			oppositeRow?.audiobookBookFileId
		} else {
			null
		}
	)
}

private fun Double?.toPercent(): Int? =
	this?.takeIf { it >= 0.0 }?.let { (it.coerceAtMost(1.0) * 100.0).roundToInt() }

private fun String?.normalizedBookFileId(): String? =
	this?.normalizedDuplicateRowField()?.takeIf { it.isNotEmpty() && it != "0" }

private fun BinderyBookVersionRow.visibleIdentityKey(): String =
	listOf(
		kind.name,
		title.normalizedDuplicateRowField(),
		subtitle.normalizedDuplicateRowField()
	).joinToString(separator = "\u001f")

private fun String?.normalizedDuplicateRowField(): String =
	orEmpty()
		.trim()
		.replace(Regex("\\s+"), " ")
		.lowercase()

private fun BinderyCatalog?.findingCardsByBookFileId(
	currentBookId: String?,
	language: String?
): Map<String, BinderyCatalogCard.Finding> {
	val catalog = this ?: return emptyMap()
	val normalizedBookId = currentBookId
		?.let(::binderyBookRouteId)
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }
		?: return emptyMap()
	return binderyCatalogCards(catalog, BinderyCatalogTab.Findings)
		.filterIsInstance<BinderyCatalogCard.Finding>()
		.flatMap { card ->
			val metadata = card.finding ?: return@flatMap emptyList()
			val kind = metadata.findingKind()
			metadata.mappings.mapNotNull { mapping ->
				val bookFileId = mapping.bookFileId
					?.trim()
					?.takeIf { it.isNotEmpty() && it != "0" }
					?: return@mapNotNull null
				val mappingBookId = mapping.bookId
					?.let(::binderyBookRouteId)
					?.trim()
					?.lowercase()
				if (mappingBookId != normalizedBookId) return@mapNotNull null
				if (!mapping.matchesFindingLanguage(language, metadata)) return@mapNotNull null
				if (kind != null && !mapping.matchesFindingKind(kind, metadata)) return@mapNotNull null
				bookFileId to card
			}
		}
		.distinctBy { (bookFileId, _) -> bookFileId }
		.toMap()
}

private fun List<BinderyReadingOrderItem>.toAudiobookVersionRow(
	findingByBookFileId: Map<String, BinderyCatalogCard.Finding>
): BinderyBookVersionRow {
	val totalDuration = sumOf { it.durationSeconds ?: 0.0 }.takeIf { it > 0.0 }
	val totalSize = sumOf { it.sizeBytes ?: 0L }.takeIf { it > 0L }
	val format = mostCommonFormat()
	val publisher = firstNotNullOfOrNull { item -> item.versionPublisherLabel() }
	val provider = firstNotNullOfOrNull { item -> item.providerLabel() }
	val narrator = firstNotNullOfOrNull { item -> item.properties.firstNonBlankValue("narrator") }
	val partsText = if (size == 1) "1 part" else "$size parts"
	val bookFileId = firstNotNullOfOrNull { item -> item.bookFileId() }
	val subtitle = listOfNotNull(
		provider,
		publisher,
		narrator,
		format,
		partsText,
		totalDuration?.roundToLong()?.let(::queueTotalDurationLabel),
		totalSize?.toFileSize()
	).joinToString(separator = " / ")
	return BinderyBookVersionRow(
		id = firstOrNull()?.audioEditionKey()?.takeIf { it != "audiobook" }?.let { "audiobook:$it" } ?: "audiobook",
		kind = BinderyBookVersionKind.Audiobook,
		title = "Audiobook",
		subtitle = subtitle,
		finding = bookFileId?.let(findingByBookFileId::get),
		audiobookBookFileId = bookFileId
	)
}

private fun BinderyAudiobookVersion.toAudiobookVersionRow(): BinderyBookVersionRow {
	val partsText = resourceCount
		?.takeIf { it > 0 }
		?.let { count -> if (count == 1) "1 part" else "$count parts" }
	val subtitle = listOfNotNull(
		editionType?.displayToken(),
		displayCodec(),
		partsText,
		durationMs?.takeIf { it > 0L }?.let(::audiobookDurationLabel),
		sizeBytes?.takeIf { it > 0L }?.toFileSize()
	).joinToString(separator = " / ")
	return BinderyBookVersionRow(
		id = id?.let { "audiobook-version:$it" } ?: bookFileId?.let { "audiobook-book-file:$it" } ?: "audiobook-version",
		kind = BinderyBookVersionKind.Audiobook,
		title = audiobookTitleLabel(),
		subtitle = subtitle.takeIf { it.isNotBlank() },
		audiobookId = id?.toString(),
		audiobookBookFileId = bookFileId?.toString()
	)
}

private fun BinderyBookResource.toEbookVersionRow(
	findingByBookFileId: Map<String, BinderyCatalogCard.Finding>
): BinderyBookVersionRow {
	val title = ebookVersionTitle()
	return BinderyBookVersionRow(
		id = href,
		kind = BinderyBookVersionKind.Ebook,
		title = title,
		subtitle = listOfNotNull(
			providerLabel(),
			displayFormat().takeUnless { it == title },
			sizeBytes?.toFileSize()
		).joinToString(separator = " / ").takeIf { it.isNotBlank() },
		format = readerPublicationFormat(),
		finding = bookFileId()?.let(findingByBookFileId::get),
		ebookBookFileId = bookFileId()
	)
}

private fun BinderyBookResource.toReadaloudVersionRow(
	findingByBookFileId: Map<String, BinderyCatalogCard.Finding>
): BinderyBookVersionRow =
	BinderyBookVersionRow(
		id = href,
		kind = BinderyBookVersionKind.Readaloud,
		title = "Readaloud",
		subtitle = listOfNotNull(
			providerLabel(),
			displayFormat(),
			sizeBytes?.toFileSize()
		).joinToString(separator = " / ").takeIf { it.isNotBlank() },
		finding = bookFileId()?.let(findingByBookFileId::get),
		ebookBookFileId = bookFileId()
	)

private fun BinderyAudiobookVersion.matchesLanguage(language: String?): Boolean =
	language == null || this.language?.normalizedBinderyAvailabilityLanguage() == language

private fun BinderyAudiobookVersion.audiobookTitleLabel(): String =
	narrator
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: versionLabel?.trim()?.takeIf { it.isNotEmpty() }
		?: audibleTitle?.trim()?.takeIf { it.isNotEmpty() }
		?: title?.trim()?.takeIf { it.isNotEmpty() }
		?: "Audiobook"

private fun BinderyAudiobookVersion.displayCodec(): String? =
	codec
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?.uppercase()
		?: formatLabel?.trim()?.takeIf { it.isNotEmpty() }

private fun BinderyAudiobookVersion.audioFormatQualityRank(): Int =
	displayCodec().audioFormatQualityRank()

private fun BinderyAudiobookVersion.audioBytesPerSecond(): Double {
	val durationSeconds = durationMs?.takeIf { it > 0L }?.toDouble()?.div(1000.0) ?: return 0.0
	return (sizeBytes ?: 0L).toDouble() / durationSeconds
}

private fun audiobookDurationLabel(durationMs: Long): String {
	val totalSeconds = (durationMs / 1000.0).roundToLong().coerceAtLeast(0L)
	val hours = totalSeconds / 3600L
	val minutes = (totalSeconds % 3600L) / 60L
	val seconds = totalSeconds % 60L
	return buildString {
		if (hours > 0L) append("${hours}h")
		if (minutes > 0L || hours > 0L) {
			if (isNotEmpty()) append(' ')
			append("${minutes}m")
		}
		if (hours == 0L && minutes == 0L) append("${seconds}s")
	}
}

private fun BinderyBookResource.isAudioResource(): Boolean =
	kind?.normalizedBinderyMediaFormat() == "audiobook" ||
		properties.firstNonBlankValue("kind", "mediaType", "format")
			?.normalizedBinderyMediaFormat() == "audiobook" ||
		type?.startsWith("audio/", ignoreCase = true) == true

private fun BinderyBookResource.isEbookResource(): Boolean =
	kind?.normalizedBinderyMediaFormat() == "ebook" ||
		properties.firstNonBlankValue("kind", "mediaType", "format")
			?.normalizedBinderyMediaFormat() == "ebook" ||
		type.isEbookMediaType()

private fun BinderyReadingOrderItem.isAudioResource(): Boolean =
	properties.firstNonBlankValue("kind", "mediaType", "format")
		?.normalizedBinderyMediaFormat() == "audiobook" ||
		type?.startsWith("audio/", ignoreCase = true) == true

private fun BinderyReadingOrderItem.isEbookResource(): Boolean =
	properties.firstNonBlankValue("kind", "mediaType", "format")
		?.normalizedBinderyMediaFormat() == "ebook" ||
		type.isEbookMediaType()

private fun BinderyBookResource.isReadaloudResource(): Boolean {
	val isEpub = type.equals("application/epub+zip", ignoreCase = true) ||
		displayFormat().equals("EPUB", ignoreCase = true)
	val explicitReadaloudKind = kind.equals("readaloud", ignoreCase = true) ||
		properties.firstNonBlankValue("kind", "mediaType", "format")
			?.equals("readaloud", ignoreCase = true) == true
	val hasMediaOverlay = properties.hasTruthyValue(
		"mediaOverlay",
		"mediaOverlays",
		"media-overlay",
		"epubMediaOverlay",
		"readaloud",
		"storytellerReadaloud"
	)
	return isEpub && (explicitReadaloudKind || hasMediaOverlay)
}

private fun BinderyReadingOrderItem.isReadaloudResource(): Boolean {
	val isEpub = type.equals("application/epub+zip", ignoreCase = true) ||
		displayFormat().equals("EPUB", ignoreCase = true)
	val explicitReadaloudKind = properties.firstNonBlankValue("kind", "mediaType", "format")
		?.equals("readaloud", ignoreCase = true) == true
	val hasMediaOverlay = properties.hasTruthyValue(
		"mediaOverlay",
		"mediaOverlays",
		"media-overlay",
		"epubMediaOverlay",
		"readaloud",
		"storytellerReadaloud"
	)
	return isEpub && (explicitReadaloudKind || hasMediaOverlay)
}

internal fun BinderyLink.isAcquisition(): Boolean =
	rel.any { it.equals(BINDERY_ACQUISITION_REL, ignoreCase = true) }

private fun List<BinderyLink>.hasConcreteAcquisition(): Boolean =
	any(BinderyLink::isAcquisition)

private fun BinderyLink.isEbookAcquisition(): Boolean =
	isAcquisition() &&
		(properties["kind"]?.equals("ebook", ignoreCase = true) == true || type.isEbookMediaType())

internal fun BinderyCatalogCard.Finding.isAvailableFindingCandidate(languageFilter: String? = null): Boolean {
	val status = finding?.availabilityStatus
		?.trim()
		?.lowercase()
	if (status in BinderyUnavailableFindingStatuses) return false
	if (status in BinderyAvailableFindingStatuses) return true
	return availability.toOwnershipStatus(languageFilter) == AurralOwnershipStatus.Owned ||
		links.hasConcreteAcquisition()
}

private fun BinderyLink.toBookResource(): BinderyBookResource =
	BinderyBookResource(
		href = href,
		title = title ?: href.substringAfterLast('/'),
		type = type,
		kind = properties["kind"],
		sizeBytes = properties["size"]?.toLongOrNull(),
		properties = properties
	)

private fun BinderyBookResource.toReadingOrderItem(): BinderyReadingOrderItem =
	BinderyReadingOrderItem(
		href = href,
		title = title,
		type = type,
		durationSeconds = durationSeconds,
		sizeBytes = sizeBytes,
		properties = properties
	)

private fun BinderyReadingOrderItem.toBookResource(): BinderyBookResource =
	BinderyBookResource(
		href = href,
		title = title,
		type = type,
		kind = properties.firstNonBlankValue("kind", "mediaType", "format"),
		durationSeconds = durationSeconds,
		sizeBytes = sizeBytes,
		properties = properties,
		propertyValues = propertyValues,
		metadata = metadata
	)

private fun BinderyReadingOrderItem.audioEditionKey(): String =
	properties.firstNonBlankValue("bookFileId") ?: "audiobook"

private fun BinderyReadingOrderItem.bookFileId(): String? =
	properties.firstNonBlankValue("bookFileId")?.takeIf { it != "0" }

private fun BinderyBookResource.bookFileId(): String? =
	properties.firstNonBlankValue("bookFileId")?.takeIf { it != "0" }

internal fun BinderyReadingOrderItem.matchesLanguage(language: String?): Boolean =
	language == null || properties.firstNonBlankValue("language")
		?.normalizedBinderyAvailabilityLanguage() == language

private fun BinderyBookResource.matchesLanguage(language: String?): Boolean =
	language == null || properties.firstNonBlankValue("language")
		?.normalizedBinderyAvailabilityLanguage() == language

private fun List<BinderyReadingOrderItem>.mostCommonFormat(): String? =
	groupingBy { item -> item.displayFormat() }
		.eachCount()
		.entries
		.filter { (format, _) -> !format.isNullOrBlank() }
		.maxWithOrNull(
			compareBy<Map.Entry<String?, Int>> { (_, count) -> count }
				.thenBy { (format, _) -> format.orEmpty() }
		)
		?.key

private fun List<BinderyReadingOrderItem>.audioFormatQualityRank(): Int =
	maxOfOrNull { item -> item.displayFormat().audioFormatQualityRank() } ?: 0

private fun List<BinderyReadingOrderItem>.audioBytesPerSecond(): Double {
	val duration = sumOf { item -> item.durationSeconds ?: 0.0 }
	return if (duration > 0.0) totalSizeBytes().toDouble() / duration else 0.0
}

private fun List<BinderyReadingOrderItem>.totalSizeBytes(): Long =
	sumOf { item -> item.sizeBytes ?: 0L }

private fun BinderyReadingOrderItem.versionPublisherLabel(): String? =
	properties.firstNonBlankValue("publisher")
		?: displayName().leadingBracketLabel()

private fun BinderyBookResource.versionTitle(): String =
	versionPublisherLabel() ?: displayFormat() ?: "Ebook"

private fun BinderyBookResource.ebookVersionTitle(): String =
	versionPublisherLabel()
		?: identifyingEbookFileLabel()
		?: displayFormat()
		?: "Ebook"

private fun BinderyBookResource.identifyingEbookFileLabel(): String? {
	if (!providerLabel().isAudioBookBayProvider()) return null
	return properties.firstNonBlankValue("relativePath", "fileName", "filename", "name")
		?.fileNameStem()
}

private fun String?.isAudioBookBayProvider(): Boolean {
	val normalized = this?.trim()?.lowercase() ?: return false
	return normalized == "abb" ||
		normalized == "audiobook bay" ||
		normalized == "audio book bay" ||
		"audiobookbay" in normalized.replace(" ", "")
}

private fun BinderyBookResource.versionPublisherLabel(): String? =
	properties.firstNonBlankValue("publisher")
		?: displayName().leadingBracketLabel()

private fun BinderyReadingOrderItem.providerLabel(): String? =
	properties.providerLabel()

private fun BinderyBookResource.providerLabel(): String? =
	properties.providerLabel()

private fun Map<String, String>.providerLabel(): String? =
	firstNonBlankValue("provider")
		?: firstNonBlankValue("providerKind")?.displayToken()

private fun BinderyBookResource.ebookFormatQualityRank(): Int =
	displayFormat().ebookFormatQualityRank()

private fun BinderyBookResource.readerPublicationFormat(): ReaderPublicationFormat =
	if (
		displayFormat().equals("PDF", ignoreCase = true) ||
		type?.contains("pdf", ignoreCase = true) == true ||
		displayName().fileExtension().equals("PDF", ignoreCase = true)
	) {
		ReaderPublicationFormat.Pdf
	} else {
		ReaderPublicationFormat.Epub
	}

private fun BinderyBookResource.displayName(): String =
	properties.firstNonBlankValue("relativePath") ?: title

private fun BinderyReadingOrderItem.displayName(): String =
	properties.firstNonBlankValue("relativePath") ?: title

private fun BinderyBookResource.displayFormat(): String? =
	properties.firstNonBlankValue("format")
		?.uppercase()
		?.takeUnless(String::isGenericBookMediaFormat)
		?: displayName().fileExtension()
		?: type.toReadableBookFormat()
		?: properties.firstNonBlankValue("format")?.uppercase()

private fun BinderyReadingOrderItem.displayFormat(): String? =
	properties.firstNonBlankValue("format")
		?.uppercase()
		?.takeUnless(String::isGenericBookMediaFormat)
		?: displayName().fileExtension()
		?: type.toReadableBookFormat()
		?: properties.firstNonBlankValue("format")?.uppercase()

private fun BinderyFindingMetadata?.findingKind(): BinderyBookFindingKind? {
	val media = this?.mediaType.orEmpty().lowercase()
	val format = this?.format.orEmpty()
	return when {
		"audio" in media || "audiobook" in media || format.audioFormatQualityRank() > 0 ->
			BinderyBookFindingKind.Audiobook
		"ebook" in media || "book" in media || format.ebookFormatQualityRank() > 0 ->
			BinderyBookFindingKind.Ebook
		else -> null
	}
}

private fun BinderyFindingMetadata?.findingTitle(
	kind: BinderyBookFindingKind,
	fallbackTitle: String
): String {
	val metadata = this
	return when (kind) {
		BinderyBookFindingKind.Audiobook -> listOfNotNull(
			metadata?.narrator?.trim()?.takeIf { it.isNotEmpty() },
			metadata?.edition?.trim()?.takeIf { it.isNotEmpty() },
			metadata?.provider?.trim()?.takeIf { it.isNotEmpty() }
		)
		BinderyBookFindingKind.Ebook -> listOfNotNull(
			metadata?.publisher?.trim()?.takeIf { it.isNotEmpty() },
			metadata?.edition?.trim()?.takeIf { it.isNotEmpty() },
			metadata?.provider?.trim()?.takeIf { it.isNotEmpty() },
			metadata?.format?.displayToken()?.takeIf { it.isNotEmpty() }
		)
	}.distinctBy { label -> label.lowercase() }
		.joinToString(" / ")
		.takeIf { it.isNotBlank() }
		?: fallbackTitle.trim().takeIf { it.isNotEmpty() }
		?: when (kind) {
			BinderyBookFindingKind.Audiobook -> "Audiobook"
			BinderyBookFindingKind.Ebook -> "Ebook"
		}
}

private fun BinderyFindingMetadata?.findingSubtitle(
	fallbackSubtitle: String?,
	kind: BinderyBookFindingKind
): String? {
	val metadata = this ?: return fallbackSubtitle
	val labels = buildList {
		metadata.provider?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
		metadata.publisher?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
		metadata.edition?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
		metadata.format?.displayToken()?.let(::add)
		metadata.sizeBytes?.takeIf { it > 0L }?.toFileSize()?.let(::add)
		if (kind == BinderyBookFindingKind.Audiobook) {
			metadata.bitrateBps?.takeIf { it > 0L }?.toBitrateLabel()?.let(::add)
			metadata.sampleRateHz?.takeIf { it > 0L }?.toSampleRateLabel()?.let(::add)
		}
		metadata.fileCount?.takeIf { it > 0 }?.let { count -> if (count == 1) "1 file" else "$count files" }?.let(::add)
		metadata.language?.uppercase()?.let(::add)
		metadata.availabilityStatus?.displayToken()?.let(::add)
	}
	return labels.distinctBy { label -> label.lowercase() }
		.joinToString(" / ")
		.takeIf { it.isNotBlank() }
		?: fallbackSubtitle
}

private fun BinderyFindingMetadata?.findingAudioQualityRank(): Int =
	this?.format.audioFormatQualityRank()

private fun BinderyFindingMetadata?.findingEbookQualityRank(): Int =
	this?.format.ebookFormatQualityRank()
