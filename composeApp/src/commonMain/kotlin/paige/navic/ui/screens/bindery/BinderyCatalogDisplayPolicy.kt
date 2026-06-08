package paige.navic.ui.screens.bindery

import io.ktor.http.encodeURLParameter
import paige.navic.domain.models.AurralOwnershipStatus
import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyAvailability
import paige.navic.domain.repositories.BinderyBookResource
import paige.navic.domain.repositories.BinderyFindingFile
import paige.navic.domain.repositories.BinderyFindingMapping
import paige.navic.domain.repositories.BinderyFindingMetadata
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.domain.repositories.binderyEndpoint
import paige.navic.domain.repositories.configuredBinderyOpdsBaseUrl
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.reader.ReaderPublicationKind
import paige.navic.ui.navigation.Screen
import paige.navic.ui.navigation.SearchScope
import paige.navic.util.core.toFileSize
import kotlin.math.roundToLong

private const val BINDERY_BOOK_CATALOG_PAGE_SIZE = 5
internal const val BINDERY_MONITOR_REL = "https://bindery.app/opds/rel/monitor"
internal const val BINDERY_UNMONITOR_REL = "https://bindery.app/opds/rel/unmonitor"
internal const val BINDERY_DOWNLOAD_REQUEST_REL = "https://bindery.app/opds/rel/download-request"
private const val BINDERY_ACQUISITION_REL = "http://opds-spec.org/acquisition"
private val BinderyAvailabilityQueryKeys = setOf("owned", "formats", "languages", "coverage")
private val BinderyRequiredBookFormats = setOf("ebook", "audiobook")
private val BinderyAvailableFindingStatuses = setOf(
	"available",
	"downloadable",
	"downloaded",
	"imported",
	"owned",
	"ready",
	"acquired"
)
private val BinderyUnavailableFindingStatuses = setOf(
	"unknown",
	"missing",
	"unavailable",
	"not_available",
	"not found",
	"not_found",
	"failed",
	"excluded",
	"rejected"
)

enum class BinderyCatalogTab(
	val path: String
) {
	Audiobooks("/opds/formats/audiobook"),
	Books("/opds/books"),
	Collections("/opds/collections"),
	Authors("/opds/authors"),
	Findings("/opds/findings")
}

fun BinderyCatalogTab.initialCatalogPath(): String =
	binderyInitialCatalogPath(path)

private fun String.withLimit(limit: Int): String =
	if ('?' in this) "$this&limit=$limit" else "$this?limit=$limit"

fun binderyInitialCatalogPath(path: String): String {
	val trimmed = path.trim()
	val normalizedPath = trimmed.substringBefore('?').trimEnd('/').lowercase()
	return if ((normalizedPath == BinderyCatalogTab.Audiobooks.path.lowercase() ||
			normalizedPath == BinderyCatalogTab.Books.path.lowercase() ||
			normalizedPath == BinderyCatalogTab.Findings.path.lowercase()) &&
		!trimmed.substringAfter('?', "").split('&').any { parameter ->
			parameter.substringBefore('=').equals("limit", ignoreCase = true)
		}
	) {
		trimmed.withLimit(BINDERY_BOOK_CATALOG_PAGE_SIZE)
	} else {
		trimmed
	}
}

enum class BinderyAvailabilityQueryMode {
	List,
	Detail
}

fun normalizedBinderyLanguageFilter(language: String): String? {
	val normalized = language.trim().lowercase()
	return normalized
		.takeIf { it.isNotEmpty() && it != "all" && it != "any" }
		?.takeIf { value -> value.all { it.isLetterOrDigit() || it == '-' || it == '_' } }
}

private fun String.normalizedBinderyAvailabilityLanguage(): String? {
	val normalized = trim()
		.lowercase()
		.replace('_', '-')
		.takeIf { it.isNotEmpty() } ?: return null
	return when {
		normalized == "english" || normalized == "en" || normalized.startsWith("en-") -> "eng"
		normalized == "spanish" || normalized == "es" || normalized.startsWith("es-") -> "spa"
		normalized == "french" || normalized == "fr" || normalized.startsWith("fr-") -> "fra"
		normalized == "german" || normalized == "de" || normalized.startsWith("de-") -> "deu"
		normalized == "italian" || normalized == "it" || normalized.startsWith("it-") -> "ita"
		normalized == "portuguese" || normalized == "pt" || normalized.startsWith("pt-") -> "por"
		normalized == "japanese" || normalized == "ja" || normalized.startsWith("ja-") -> "jpn"
		normalized == "korean" || normalized == "ko" || normalized.startsWith("ko-") -> "kor"
		normalized == "chinese" || normalized == "zh" || normalized.startsWith("zh-") -> "zho"
		else -> normalized
	}
}

private fun normalizedBinderyAvailabilityLanguageFilter(languageFilter: String?): String? =
	languageFilter
		?.let(::normalizedBinderyLanguageFilter)
		?.normalizedBinderyAvailabilityLanguage()

fun binderyAvailabilityFilteredCatalogPath(
	path: String,
	languageFilter: String?,
	mode: BinderyAvailabilityQueryMode = BinderyAvailabilityQueryMode.List
): String {
	val language = languageFilter?.let(::normalizedBinderyLanguageFilter) ?: return path
	val trimmed = path.trim()
	val pathPart = trimmed.substringBefore('?')
	val existingQuery = trimmed.substringAfter('?', missingDelimiterValue = "")
	val parameters = existingQuery
		.split('&')
		.mapNotNull { parameter -> parameter.trim().takeIf { it.isNotEmpty() } }
		.filterNot { parameter ->
			parameter.substringBefore('=').lowercase() in BinderyAvailabilityQueryKeys
		}
		.toMutableList()
	if (mode == BinderyAvailabilityQueryMode.List) {
		parameters += "owned=1"
		parameters += "formats=ebook,audiobook"
	}
	parameters += "languages=$language"
	parameters += "coverage=any"
	return if (parameters.isEmpty()) pathPart else "$pathPart?${parameters.joinToString("&")}"
}

fun binderySearchCatalogPath(
	path: String,
	languageFilter: String?
): String =
	binderyAvailabilityFilteredCatalogPath(
		path = path,
		languageFilter = languageFilter,
		mode = BinderyAvailabilityQueryMode.Detail
	)

fun binderyDiscoverAuthorsPath(query: String): String =
	"/opds/discover/authors?q=${query.trim().encodeURLParameter()}"

fun binderySubjectSearchDestination(subject: String): Screen.Search? =
	subject.trim()
		.takeIf { it.isNotEmpty() }
		?.let { query ->
			Screen.Search(
				nested = true,
				scope = SearchScope.Audiobooks,
				initialQuery = query
			)
		}

fun shouldLoadBinderyUi(
	binderyEnabled: Boolean,
	opdsBaseUrl: String,
	apiKey: String
): Boolean =
	binderyEnabled &&
		configuredBinderyOpdsBaseUrl(opdsBaseUrl) != null &&
		apiKey.isNotBlank()

enum class BinderyHubRowKind {
	LastRead,
	RecentlyAdded,
	MostPopular,
	Audiobooks,
	Genres,
	Authors,
	Collections,
	Findings,
	Wanted
}

data class BinderyHubRow(
	val kind: BinderyHubRowKind,
	val path: String,
	val title: String
) {
	val catalogPath: String
		get() = if ('?' in path) path else "$path?limit=12"

	val catalogTab: BinderyCatalogTab?
		get() = when (kind) {
			BinderyHubRowKind.Authors -> BinderyCatalogTab.Authors
			BinderyHubRowKind.Collections -> BinderyCatalogTab.Collections
			BinderyHubRowKind.Findings -> BinderyCatalogTab.Findings
			else -> null
		}
}

sealed interface BinderyCatalogCard {
	val id: String
	val title: String
	val subtitle: String?

	data class Book(
		override val id: String,
		override val title: String,
		override val subtitle: String?,
		val imageUrl: String?,
		val availability: BinderyAvailability? = null,
		val links: List<BinderyLink> = emptyList(),
		val readingOrder: List<BinderyReadingOrderItem> = emptyList()
	) : BinderyCatalogCard

	data class Link(
		override val id: String,
		override val title: String,
		override val subtitle: String?,
		val path: String,
		val imageUrl: String? = null,
		val availability: BinderyAvailability? = null,
		val properties: Map<String, String> = emptyMap(),
		val links: List<BinderyLink> = emptyList()
	) : BinderyCatalogCard

	data class Finding(
		override val id: String,
		override val title: String,
		override val subtitle: String?,
		val path: String,
		val imageUrl: String? = null,
		val availability: BinderyAvailability? = null,
		val finding: BinderyFindingMetadata? = null,
		val links: List<BinderyLink> = emptyList()
	) : BinderyCatalogCard
}

fun binderyCatalogCards(
	catalog: BinderyCatalog,
	tab: BinderyCatalogTab?
): List<BinderyCatalogCard> =
	if (tab == BinderyCatalogTab.Findings || catalog.isFindingsCatalog()) {
		catalog.publications.mapNotNull(BinderyPublication::toFindingCardOrNull)
	} else if (tab == BinderyCatalogTab.Collections || tab == BinderyCatalogTab.Authors ||
		(catalog.publications.isEmpty() && catalog.navigation.isNotEmpty())
	) {
		val catalogActionLinks = catalog.links.filter(BinderyLink::isBinderyActionLink)
		catalog.navigation.map { link ->
			BinderyCatalogCard.Link(
				id = link.href,
				title = link.title ?: link.href.substringAfterLast('/').ifBlank { link.href },
				subtitle = when (tab) {
					BinderyCatalogTab.Authors -> "Author"
					BinderyCatalogTab.Collections -> "Collection"
					else -> "Catalog"
				},
				path = link.href,
				imageUrl = link.preferredImageHref(),
				availability = link.availability,
				properties = link.properties,
				links = link.actionLinks().ifEmpty {
					if (catalog.navigation.size == 1) catalogActionLinks else emptyList()
				}
			)
		}
	} else {
		catalog.publications.map { publication ->
			BinderyCatalogCard.Book(
				id = publication.id ?: publication.title,
				title = publication.title,
				subtitle = publication.author,
				imageUrl = publication.images.firstOrNull()?.href,
				availability = publication.availability,
				links = publication.links,
				readingOrder = publication.readingOrder
			)
		}
	}

private fun BinderyCatalog.isFindingsCatalog(): Boolean =
	publications.any(BinderyPublication::hasFindingIdentity) ||
		(publications.isEmpty() && (
			title.equals("Findings", ignoreCase = true) ||
				links.any { link -> link.href.contains("/opds/findings", ignoreCase = true) }
			))

private fun BinderyPublication.hasFindingIdentity(): Boolean =
	findingRoutePath() != null

private fun BinderyPublication.toFindingCardOrNull(): BinderyCatalogCard.Finding? {
	val metadata = finding
	val path = findingRoutePath() ?: return null
	val findingId = metadata?.findingId
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: path.substringAfterLast('/').takeIf { it.isNotBlank() && it != path }
		?: id?.removePrefix("urn:bindery:finding:")?.takeIf { it.isNotBlank() }
		?: title
	return BinderyCatalogCard.Finding(
		id = findingId,
		title = title,
		subtitle = metadata?.displaySubtitle() ?: author,
		path = path,
		imageUrl = images.firstOrNull()?.href ?: metadata?.coverUrl,
		availability = availability,
		finding = metadata,
		links = links
	)
}

private fun BinderyPublication.findingRoutePath(): String? =
	links.firstOrNull { link ->
		link.rel.any { rel -> rel.equals("self", ignoreCase = true) } &&
			link.href.contains("/opds/findings", ignoreCase = true)
	}?.href
		?: links.firstOrNull { link -> link.href.contains("/opds/findings", ignoreCase = true) }?.href
		?: finding?.findingId
			?.trim()
			?.takeIf { findingId -> findingId.isNotEmpty() }
			?.let { findingId -> "/opds/findings/$findingId" }
		?: id?.let(::binderyFindingRoutePathOrNull)

private fun binderyFindingRoutePathOrNull(id: String): String? {
	val trimmed = id.trim()
	return when {
		trimmed.startsWith("/opds/findings/") -> trimmed
		trimmed.startsWith("urn:bindery:finding:") -> "/opds/findings/${trimmed.removePrefix("urn:bindery:finding:")}"
		else -> null
	}
}

fun BinderyFindingMetadata.displaySubtitle(): String? =
	listOfNotNull(
		mediaType?.displayToken(),
		language?.uppercase(),
		format?.uppercase(),
		availabilityStatus?.displayToken()
	).joinToString(separator = " / ")
		.takeIf { it.isNotBlank() }

enum class BinderyOpdsActionType {
	Monitor,
	Unmonitor,
	DownloadRequest
}

data class BinderyOpdsAction(
	val type: BinderyOpdsActionType,
	val link: BinderyLink
)

enum class BinderyOpdsActionPresentation {
	Add,
	Hide,
	Play
}

fun BinderyOpdsActionType.presentation(): BinderyOpdsActionPresentation =
	when (this) {
		BinderyOpdsActionType.Monitor -> BinderyOpdsActionPresentation.Add
		BinderyOpdsActionType.Unmonitor -> BinderyOpdsActionPresentation.Hide
		BinderyOpdsActionType.DownloadRequest -> BinderyOpdsActionPresentation.Play
	}

val BinderyCatalog.monitorAction: BinderyLink?
	get() = actionLinks().actionLink(BINDERY_MONITOR_REL)

val BinderyCatalog.unmonitorAction: BinderyLink?
	get() = actionLinks().actionLink(BINDERY_UNMONITOR_REL)

val BinderyCatalog.downloadRequestAction: BinderyLink?
	get() = actionLinks().actionLink(BINDERY_DOWNLOAD_REQUEST_REL)

val BinderyCatalogCard.Book.monitorAction: BinderyLink?
	get() = links.actionLink(BINDERY_MONITOR_REL)

val BinderyCatalogCard.Book.unmonitorAction: BinderyLink?
	get() = links.actionLink(BINDERY_UNMONITOR_REL)

val BinderyCatalogCard.Book.downloadRequestAction: BinderyLink?
	get() = links.actionLink(BINDERY_DOWNLOAD_REQUEST_REL)

val BinderyCatalogCard.Link.monitorAction: BinderyLink?
	get() = links.actionLink(BINDERY_MONITOR_REL)

val BinderyCatalogCard.Link.unmonitorAction: BinderyLink?
	get() = links.actionLink(BINDERY_UNMONITOR_REL)

val BinderyCatalogCard.Link.downloadRequestAction: BinderyLink?
	get() = links.actionLink(BINDERY_DOWNLOAD_REQUEST_REL)

val BinderyCatalogCard.Finding.monitorAction: BinderyLink?
	get() = links.actionLink(BINDERY_MONITOR_REL)

val BinderyCatalogCard.Finding.unmonitorAction: BinderyLink?
	get() = links.actionLink(BINDERY_UNMONITOR_REL)

val BinderyCatalogCard.Finding.downloadRequestAction: BinderyLink?
	get() = links.actionLink(BINDERY_DOWNLOAD_REQUEST_REL)

fun BinderyCatalog.primaryAction(): BinderyOpdsAction? =
	unmonitorAction?.let { BinderyOpdsAction(BinderyOpdsActionType.Unmonitor, it) }
		?: monitorAction?.let { BinderyOpdsAction(BinderyOpdsActionType.Monitor, it) }
		?: downloadRequestAction
			?.takeIf { finding != null }
			?.let { BinderyOpdsAction(BinderyOpdsActionType.DownloadRequest, it) }

fun BinderyCatalogCard.primaryAction(): BinderyOpdsAction? =
	when (this) {
		is BinderyCatalogCard.Book -> unmonitorAction
			?.let { BinderyOpdsAction(BinderyOpdsActionType.Unmonitor, it) }
			?: monitorAction?.let { BinderyOpdsAction(BinderyOpdsActionType.Monitor, it) }
		is BinderyCatalogCard.Link -> unmonitorAction
			?.let { BinderyOpdsAction(BinderyOpdsActionType.Unmonitor, it) }
			?: monitorAction
			?.let { BinderyOpdsAction(BinderyOpdsActionType.Monitor, it) }
		is BinderyCatalogCard.Finding -> downloadRequestAction
			?.let { BinderyOpdsAction(BinderyOpdsActionType.DownloadRequest, it) }
			?: unmonitorAction?.let { BinderyOpdsAction(BinderyOpdsActionType.Unmonitor, it) }
			?: monitorAction?.let { BinderyOpdsAction(BinderyOpdsActionType.Monitor, it) }
	}

fun BinderyPublication.primaryAction(): BinderyOpdsAction? =
	links.actionLink(BINDERY_UNMONITOR_REL)
		?.let { BinderyOpdsAction(BinderyOpdsActionType.Unmonitor, it) }
		?: links.actionLink(BINDERY_MONITOR_REL)
			?.let { BinderyOpdsAction(BinderyOpdsActionType.Monitor, it) }

fun BinderyManifest.primaryAction(): BinderyOpdsAction? =
	links.actionLink(BINDERY_UNMONITOR_REL)
		?.let { BinderyOpdsAction(BinderyOpdsActionType.Unmonitor, it) }
		?: links.actionLink(BINDERY_MONITOR_REL)
			?.let { BinderyOpdsAction(BinderyOpdsActionType.Monitor, it) }

private fun BinderyLink.actionLinks(): List<BinderyLink> =
	listOf(this).filter(BinderyLink::isBinderyActionLink) +
		links.filter(BinderyLink::isBinderyActionLink)

private fun BinderyCatalog.actionLinks(): List<BinderyLink> =
	links.filter(BinderyLink::isBinderyActionLink) +
		navigation.flatMap(BinderyLink::actionLinks)

private fun BinderyLink.isBinderyActionLink(): Boolean =
	rel.any { item ->
		item.equals(BINDERY_MONITOR_REL, ignoreCase = true) ||
			item.equals(BINDERY_UNMONITOR_REL, ignoreCase = true) ||
			item.equals(BINDERY_DOWNLOAD_REQUEST_REL, ignoreCase = true)
	}

private fun List<BinderyLink>.actionLink(rel: String): BinderyLink? =
	firstOrNull { link -> link.rel.any { item -> item.equals(rel, ignoreCase = true) } }

fun BinderyCatalogCard.availabilityStatus(languageFilter: String? = null): AurralOwnershipStatus? =
	when (this) {
		is BinderyCatalogCard.Book -> availability.toOwnershipStatus(languageFilter)
			.mergeBookMediaStatus(links.toConcreteResourceOwnershipStatus(languageFilter))
			.mergeBookMediaStatus(readingOrder.toReadingOrderResourceOwnershipStatus(languageFilter))
			?: AurralOwnershipStatus.Missing
		is BinderyCatalogCard.Link -> availability.toOwnershipStatus(languageFilter)
			.mergeBookMediaStatus(links.toConcreteResourceOwnershipStatus(languageFilter))
			?: AurralOwnershipStatus.Missing
		is BinderyCatalogCard.Finding -> availability.toOwnershipStatus(languageFilter)
			?: if (isAvailableFindingCandidate(languageFilter)) {
				AurralOwnershipStatus.Owned
			} else {
				AurralOwnershipStatus.Missing
			}
	}

fun BinderyCatalogCard.availabilityAlpha(languageFilter: String? = null): Float =
	when (availabilityStatus(languageFilter)) {
		AurralOwnershipStatus.Missing -> 0.42f
		else -> 1f
	}

fun BinderyCatalogCard.hasAvailableContent(languageFilter: String? = null): Boolean =
	availabilityStatus(languageFilter)
		?.let { status -> status != AurralOwnershipStatus.Missing }
		?: false

fun BinderyAvailability?.availabilityAlpha(languageFilter: String? = null): Float =
	when (toBookOwnershipStatus(languageFilter)) {
		AurralOwnershipStatus.Missing -> 0.42f
		else -> 1f
	}

fun BinderyAvailability?.toOwnershipStatus(languageFilter: String? = null): AurralOwnershipStatus? =
	this?.bookMediaOwnershipStatus(languageFilter)

fun BinderyAvailability?.toBookOwnershipStatus(languageFilter: String? = null): AurralOwnershipStatus =
	this?.bookMediaOwnershipStatus(languageFilter) ?: AurralOwnershipStatus.Missing

private fun BinderyAvailability.bookMediaOwnershipStatus(languageFilter: String? = null): AurralOwnershipStatus? {
	val normalizedLanguage = normalizedBinderyAvailabilityLanguageFilter(languageFilter)
	val combinationFormatsByLanguage = ownedCombinations
		.mapNotNull { combination ->
			val format = combination.format.normalizedBinderyMediaFormat() ?: return@mapNotNull null
			val language = combination.language.normalizedBinderyAvailabilityLanguage() ?: "unknown"
			language to format
		}
		.groupBy(
			keySelector = { (language, _) -> language },
			valueTransform = { (_, format) -> format }
		)
	if (combinationFormatsByLanguage.isNotEmpty()) {
		val strongestOwnedFormats = if (normalizedLanguage != null) {
			combinationFormatsByLanguage[normalizedLanguage].orEmpty().toSet()
		} else {
			combinationFormatsByLanguage.values
				.maxByOrNull { formats -> formats.toSet().count { it in BinderyRequiredBookFormats } }
				.orEmpty()
				.toSet()
		}
		return ownershipStatusForRequiredBookFormats(strongestOwnedFormats)
	}

	val ownedFormatTokens = ownedFormats.mapNotNull(String::normalizedBinderyMediaFormat).toSet()
	if (ownedFormatTokens.isNotEmpty()) {
		return ownershipStatusForAggregateBookFormats(
			formats = ownedFormatTokens,
			languages = ownedLanguages,
			normalizedLanguage = normalizedLanguage
		)
	}

	val availableFormatTokens = formats.mapNotNull(String::normalizedBinderyMediaFormat).toSet()
	if (owned && availableFormatTokens.isNotEmpty()) {
		return ownershipStatusForAggregateBookFormats(
			formats = availableFormatTokens,
			languages = languages,
			normalizedLanguage = normalizedLanguage
		)
	}

	return if (formats.isNotEmpty() || ownedLanguages.isNotEmpty()) {
		AurralOwnershipStatus.Missing
	} else {
		null
	}
}

private fun ownershipStatusForAggregateBookFormats(
	formats: Set<String>,
	languages: List<String>,
	normalizedLanguage: String?
): AurralOwnershipStatus {
	val normalizedLanguages = languages
		.mapNotNull { language -> language.normalizedBinderyAvailabilityLanguage() }
		.toSet()
	if (normalizedLanguage != null) {
		if (normalizedLanguages.isNotEmpty() && normalizedLanguage !in normalizedLanguages) {
			return AurralOwnershipStatus.Missing
		}
		if (normalizedLanguages.size > 1 &&
			BinderyRequiredBookFormats.count(formats::contains) > 1
		) {
			return AurralOwnershipStatus.Partial
		}
	}
	return ownershipStatusForRequiredBookFormats(formats)
}

private fun ownershipStatusForRequiredBookFormats(formats: Set<String>): AurralOwnershipStatus =
	when {
		BinderyRequiredBookFormats.all(formats::contains) -> AurralOwnershipStatus.Owned
		BinderyRequiredBookFormats.any(formats::contains) -> AurralOwnershipStatus.Partial
		else -> AurralOwnershipStatus.Missing
	}

private fun List<BinderyLink>.toConcreteResourceOwnershipStatus(languageFilter: String? = null): AurralOwnershipStatus? {
	val normalizedLanguage = normalizedBinderyAvailabilityLanguageFilter(languageFilter)
	val formats = mapNotNull { link ->
		if (!link.isAcquisition() || !link.matchesLanguage(normalizedLanguage)) return@mapNotNull null
		link.concreteMediaFormat()
	}.toSet()
	return formats.takeIf { it.isNotEmpty() }?.let(::ownershipStatusForRequiredBookFormats)
}

private fun AurralOwnershipStatus?.mergeBookMediaStatus(other: AurralOwnershipStatus?): AurralOwnershipStatus? =
	when {
		this == AurralOwnershipStatus.Owned || other == AurralOwnershipStatus.Owned -> AurralOwnershipStatus.Owned
		this == AurralOwnershipStatus.Partial || other == AurralOwnershipStatus.Partial -> AurralOwnershipStatus.Partial
		this == AurralOwnershipStatus.Missing || other == AurralOwnershipStatus.Missing -> AurralOwnershipStatus.Missing
		else -> null
	}

private fun BinderyLink.matchesLanguage(language: String?): Boolean =
	language == null ||
		properties.firstNonBlankValue("language")
			?.normalizedBinderyAvailabilityLanguage()
			?.let { it == language } != false

private fun BinderyLink.concreteMediaFormat(): String? =
	properties.firstNonBlankValue("kind", "mediaType", "format")
		?.normalizedBinderyMediaFormat()
		?: type?.normalizedBinderyMediaFormat()

private fun List<BinderyReadingOrderItem>.toReadingOrderResourceOwnershipStatus(languageFilter: String? = null): AurralOwnershipStatus? {
	val normalizedLanguage = normalizedBinderyAvailabilityLanguageFilter(languageFilter)
	val formats = mapNotNull { item ->
		if (!item.matchesLanguage(normalizedLanguage)) return@mapNotNull null
		item.concreteMediaFormat()
	}.toSet()
	return formats.takeIf { it.isNotEmpty() }?.let(::ownershipStatusForRequiredBookFormats)
}

private fun BinderyReadingOrderItem.concreteMediaFormat(): String? =
	properties.firstNonBlankValue("kind", "mediaType", "format")
		?.normalizedBinderyMediaFormat()
		?: type?.normalizedBinderyMediaFormat()

private fun String.normalizedBinderyMediaFormat(): String? =
	trim()
		.lowercase()
		.takeIf { it.isNotEmpty() }
		?.let { value ->
			when {
				value == "ebook" || value == "book" || value == "epub" || value == "pdf" ||
					value == "mobi" || value == "azw3" || value.contains("epub") ||
					value.contains("pdf") -> "ebook"
				value == "audiobook" || value == "audio" || value == "mp3" || value == "m4b" ||
					value.startsWith("audio/") -> "audiobook"
				else -> value
			}
		}

data class BinderyCatalogCardVisualPolicy(
	val coverAspectRatio: Float = 1f,
	val imageContentScaleFit: Boolean = false
)

fun binderyCatalogCardVisualPolicy(card: BinderyCatalogCard): BinderyCatalogCardVisualPolicy =
	when (card) {
		is BinderyCatalogCard.Book -> BinderyCatalogCardVisualPolicy(
			coverAspectRatio = 2f / 3f,
			imageContentScaleFit = true
		)
		is BinderyCatalogCard.Finding -> BinderyCatalogCardVisualPolicy(
			coverAspectRatio = 2f / 3f,
			imageContentScaleFit = true
		)
		is BinderyCatalogCard.Link -> if (card.subtitle == "Collection") {
			BinderyCatalogCardVisualPolicy(
				coverAspectRatio = 2f / 3f,
				imageContentScaleFit = true
			)
		} else {
			BinderyCatalogCardVisualPolicy()
		}
	}

fun binderyDestinationForLink(link: BinderyCatalogCard.Link): Screen =
	when (link.subtitle) {
		"Author" -> Screen.BinderyAuthor(link.path, link.title)
		"Collection" -> Screen.BinderyCollection(link.path, link.title)
		else -> Screen.BinderyCatalog(link.path, link.title)
	}

fun binderyDestinationForBook(book: BinderyCatalogCard.Book): Screen =
	Screen.BinderyBook(
		bookId = binderyBookRouteId(book.id),
		title = book.title
	)

fun binderyDestinationForCard(card: BinderyCatalogCard): Screen =
	when (card) {
		is BinderyCatalogCard.Book -> binderyDestinationForBook(card)
		is BinderyCatalogCard.Link -> binderyDestinationForLink(card)
		is BinderyCatalogCard.Finding -> Screen.BinderyFinding(card.path, card.title)
	}

internal fun binderyBookRouteId(id: String): String {
	val trimmed = id.trim()
	val withoutUrn = trimmed.removePrefix("urn:bindery:book:")
	return withoutUrn.substringAfterLast("/opds/books/", withoutUrn)
		.substringBefore('/')
		.takeIf { it.isNotBlank() }
		?: trimmed
}

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
	val readerOpdsAction: BinderyOpdsAction? = card.downloadRequestAction
		?.let { link -> BinderyOpdsAction(BinderyOpdsActionType.DownloadRequest, link) }
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
	languageFilter: String? = null
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
			compareByDescending<BinderyBookFindingRow> { row -> row.card.finding.findingEbookQualityRank() }
				.thenByDescending { row -> row.card.finding?.sizeBytes ?: 0L }
				.thenBy { row -> row.title.lowercase() }
		)
	return BinderyBookFindingGroups(audioRows, ebookRows)
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

data class BinderyBookVersionRow(
	val id: String,
	val kind: BinderyBookVersionKind,
	val title: String,
	val subtitle: String?
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
	opdsBaseUrl: String
): Screen.Reader? =
	when (row.routingAction()) {
		BinderyBookVersionRoutingAction.OpenReadaloud -> Screen.Reader(
			title = bookTitle,
			publicationUrl = binderyEndpoint(opdsBaseUrl, row.id),
			bookId = bookId,
			resourceHref = row.id,
			kind = ReaderPublicationKind.Readaloud,
			mediaOverlayEnabled = true
		)
		BinderyBookVersionRoutingAction.OpenEbook -> Screen.Reader(
			title = bookTitle,
			publicationUrl = binderyEndpoint(opdsBaseUrl, row.id),
			bookId = bookId,
			resourceHref = row.id,
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false
		)
		BinderyBookVersionRoutingAction.OpenAudiobook -> null
	}

fun binderyBookVersionRows(
	manifest: BinderyManifest?,
	resourceCatalog: BinderyResourceCatalog?,
	languageFilter: String? = null
): List<BinderyBookVersionRow> {
	val language = normalizedBinderyAvailabilityLanguageFilter(languageFilter)
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

	val audioRows = audioItems
		.groupBy { item -> item.audioEditionKey() }
		.values
		.sortedWith(
			compareByDescending<List<BinderyReadingOrderItem>> { items -> items.audioFormatQualityRank() }
				.thenByDescending { items -> items.audioBytesPerSecond() }
				.thenByDescending { items -> items.totalSizeBytes() }
		)
		.map { items -> items.toAudiobookVersionRow() }
	val readaloudRows = readaloudResources
		.sortedWith(
			compareByDescending<BinderyBookResource> { resource -> resource.sizeBytes ?: 0L }
				.thenBy { resource -> resource.versionTitle().lowercase() }
		)
		.map { resource -> resource.toReadaloudVersionRow() }
	val ebookRows = ebookResources
		.sortedWith(
			compareByDescending<BinderyBookResource> { resource -> resource.ebookFormatQualityRank() }
				.thenByDescending { resource -> resource.sizeBytes ?: 0L }
				.thenBy { resource -> resource.versionTitle().lowercase() }
		)
		.map { resource -> resource.toEbookVersionRow() }

	return readaloudRows + audioRows + ebookRows
}

private fun List<BinderyReadingOrderItem>.toAudiobookVersionRow(): BinderyBookVersionRow {
	val totalDuration = sumOf { it.durationSeconds ?: 0.0 }.takeIf { it > 0.0 }
	val totalSize = sumOf { it.sizeBytes ?: 0L }.takeIf { it > 0L }
	val format = mostCommonFormat()
	val publisher = firstNotNullOfOrNull { item -> item.versionPublisherLabel() }
	val provider = firstNotNullOfOrNull { item -> item.providerLabel() }
	val narrator = firstNotNullOfOrNull { item -> item.properties.firstNonBlankValue("narrator") }
	val partsText = if (size == 1) "1 part" else "$size parts"
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
		subtitle = subtitle
	)
}

private fun BinderyBookResource.toEbookVersionRow(): BinderyBookVersionRow =
	BinderyBookVersionRow(
		id = href,
		kind = BinderyBookVersionKind.Ebook,
		title = versionTitle(),
		subtitle = listOfNotNull(
			providerLabel(),
			displayFormat().takeUnless { it == versionTitle() },
			sizeBytes?.toFileSize()
		).joinToString(separator = " / ").takeIf { it.isNotBlank() }
	)

private fun BinderyBookResource.toReadaloudVersionRow(): BinderyBookVersionRow =
	BinderyBookVersionRow(
		id = href,
		kind = BinderyBookVersionKind.Readaloud,
		title = "Readaloud",
		subtitle = listOfNotNull(
			providerLabel(),
			displayFormat(),
			sizeBytes?.toFileSize()
		).joinToString(separator = " / ").takeIf { it.isNotBlank() }
	)

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

private fun BinderyLink.isAcquisition(): Boolean =
	rel.any { it.equals(BINDERY_ACQUISITION_REL, ignoreCase = true) }

private fun List<BinderyLink>.hasConcreteAcquisition(): Boolean =
	any(BinderyLink::isAcquisition)

private fun BinderyLink.isEbookAcquisition(): Boolean =
	isAcquisition() &&
		(properties["kind"]?.equals("ebook", ignoreCase = true) == true || type.isEbookMediaType())

private fun BinderyCatalogCard.Finding.isAvailableFindingCandidate(languageFilter: String? = null): Boolean {
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

private fun BinderyReadingOrderItem.matchesLanguage(language: String?): Boolean =
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

private fun String.isGenericBookMediaFormat(): Boolean =
	equals("EBOOK", ignoreCase = true) ||
		equals("BOOK", ignoreCase = true) ||
		equals("AUDIO", ignoreCase = true) ||
		equals("AUDIOBOOK", ignoreCase = true)

private fun String?.ebookFormatQualityRank(): Int =
	when (this?.uppercase()) {
		"EPUB" -> 50
		"PDF" -> 40
		"AZW3" -> 35
		"MOBI" -> 30
		"CBZ" -> 25
		"TXT" -> 10
		else -> 0
	}

private fun String?.audioFormatQualityRank(): Int =
	when (this?.uppercase()) {
		"FLAC" -> 60
		"M4B" -> 55
		"M4A" -> 50
		"AAC" -> 45
		"MP3" -> 40
		"OGG" -> 30
		else -> 0
	}

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

private fun Long.toBitrateLabel(): String =
	"${(this / 1000).coerceAtLeast(1)} kbps"

private fun Long.toSampleRateLabel(): String {
	val khz = this.toDouble() / 1000.0
	return if (khz % 1.0 == 0.0) {
		"${khz.toInt()} kHz"
	} else {
		"${((khz * 10).roundToLong() / 10.0)} kHz"
	}
}

private fun String.fileExtension(): String? {
	val extension = substringAfterLast('.', missingDelimiterValue = "")
		.substringBefore('?')
		.substringBefore('#')
		.trim()
	return extension
		.takeIf { it.length in 2..6 && it.all(Char::isLetterOrDigit) }
		?.uppercase()
}

private fun String.leadingBracketLabel(): String? {
	val trimmed = trim()
	if (trimmed.startsWith("[")) {
		val end = trimmed.indexOf(']')
		if (end > 1) return trimmed.substring(1, end).trim().takeIf { it.isNotEmpty() }
	}
	if (trimmed.startsWith("(")) {
		val end = trimmed.indexOf(')')
		if (end > 1) return trimmed.substring(1, end).trim().takeIf { it.isNotEmpty() }
	}
	return null
}

private fun String?.isEbookMediaType(): Boolean =
	this?.let { mediaType ->
		"epub" in mediaType.lowercase() ||
			"pdf" in mediaType.lowercase() ||
			"azw3" in mediaType.lowercase() ||
			"mobi" in mediaType.lowercase() ||
			"ebook" in mediaType.lowercase()
	} == true

private fun String?.toReadableBookFormat(): String? {
	val normalized = this?.lowercase() ?: return null
	return when {
		"epub" in normalized -> "EPUB"
		"pdf" in normalized -> "PDF"
		"azw3" in normalized -> "AZW3"
		"mobi" in normalized -> "MOBI"
		"audiobook" in normalized -> "Audiobook"
		"mpeg" in normalized -> "MP3"
		"mp4" in normalized -> "M4A"
		"aac" in normalized -> "AAC"
		"flac" in normalized -> "FLAC"
		"ogg" in normalized -> "OGG"
		else -> substringAfter('/').substringBefore(';').uppercase().takeIf { it.isNotBlank() }
	}
}

fun BinderyCatalog.authorCollectionsLink(): BinderyCatalogCard.Link? =
	navigation.firstOrNull { link ->
		val normalizedHref = link.href.trim().trimEnd('/').lowercase()
		normalizedHref.endsWith("/collections") &&
			(link.title?.equals("Collections", ignoreCase = true) == true ||
				normalizedHref.contains("/authors/"))
	}?.let { link ->
		BinderyCatalogCard.Link(
			id = link.href,
			title = link.title ?: "Collections",
			subtitle = "Collection",
			path = link.href,
			imageUrl = link.preferredImageHref(),
			availability = link.availability,
			properties = link.properties
		)
	}

fun List<BinderyPublication>.sortedForBinderyDetail(): List<BinderyPublication> =
	filter { publication -> publication.publicationYearSortValue() != null }
		.sortedWith(
			compareBy<BinderyPublication>(
				{ publication -> publication.publicationYearSortValue() ?: Int.MAX_VALUE },
				{ publication -> publication.title.lowercase() }
			)
		)

fun List<BinderyPublication>.sortedForBinderyCollectionDetail(): List<BinderyPublication> =
	sortedWith(
		compareBy<BinderyPublication>(
			{ publication -> publication.collectionPositionSort() ?: Double.MAX_VALUE },
			{ publication -> publication.publicationYearSortValue() ?: Int.MAX_VALUE },
			{ publication -> publication.title.lowercase() }
		)
	)

private fun BinderyPublication.collectionPositionSort(): Double? =
	properties["collectionPositionSort"]?.toDoubleOrNull()
		?: properties["collectionPosition"]?.toDoubleOrNull()

private fun BinderyPublication.publicationYearSortValue(): Int? =
	published?.trim()?.take(4)?.toIntOrNull()
		?: properties.firstNonBlankValue("published", "publicationYear", "originalPublicationYear")
			?.take(4)
			?.toIntOrNull()

internal fun BinderyCatalog.nextPagePath(): String? =
	links.firstOrNull { link ->
		link.rel.any { rel -> rel.equals("next", ignoreCase = true) }
	}?.href?.trim()?.takeIf { it.isNotEmpty() }

internal fun BinderyCatalog.appendCatalogPage(nextPage: BinderyCatalog): BinderyCatalog =
	copy(
		links = nextPage.links,
		navigation = navigation + nextPage.navigation,
		publications = publications + nextPage.publications
	)

fun binderyHubRows(rootCatalog: BinderyCatalog): List<BinderyHubRow> {
	val candidates = rootCatalog.navigation.mapNotNull { link ->
		val path = link.href.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
		val title = link.title?.trim()?.takeIf { it.isNotEmpty() } ?: path.substringAfterLast('/')
		val kind = binderyHubRowKind(path, title) ?: return@mapNotNull null
		BinderyHubRow(
			kind = kind,
			path = path,
			title = title
		)
	}
	return binderyHubRowKindOrder.mapNotNull { kind ->
		candidates.firstOrNull { it.kind == kind }
	}
}

private val binderyHubRowKindOrder = listOf(
	BinderyHubRowKind.LastRead,
	BinderyHubRowKind.RecentlyAdded,
	BinderyHubRowKind.MostPopular,
	BinderyHubRowKind.Audiobooks,
	BinderyHubRowKind.Genres,
	BinderyHubRowKind.Authors,
	BinderyHubRowKind.Collections,
	BinderyHubRowKind.Wanted
)

private fun binderyHubRowKind(
	path: String,
	title: String
): BinderyHubRowKind? {
	val normalizedPath = path.trim().trimEnd('/').lowercase()
	val normalizedTitle = title.trim().lowercase()
	return when {
		normalizedPath.endsWith("/recent") ||
			"recent" in normalizedTitle -> BinderyHubRowKind.RecentlyAdded

		normalizedPath.endsWith("/formats/audiobook") ||
			normalizedTitle == "audiobooks" -> BinderyHubRowKind.Audiobooks

		normalizedPath.endsWith("/authors") ||
			normalizedTitle == "authors" -> BinderyHubRowKind.Authors

		normalizedPath.endsWith("/collections") ||
			normalizedTitle == "collections" -> BinderyHubRowKind.Collections

		normalizedPath.endsWith("/wanted") ||
			normalizedTitle == "wanted" -> BinderyHubRowKind.Wanted

		"popular" in normalizedPath ||
			"popular" in normalizedTitle -> BinderyHubRowKind.MostPopular

		"genre" in normalizedPath ||
			"genre" in normalizedTitle ||
			"subject" in normalizedPath ||
			"subject" in normalizedTitle -> BinderyHubRowKind.Genres

		"last-read" in normalizedPath ||
			"last read" in normalizedTitle ||
			"continue" in normalizedPath ||
			"continue" in normalizedTitle ||
			"progress" in normalizedPath -> BinderyHubRowKind.LastRead

		else -> null
	}
}

private fun BinderyLink.preferredImageHref(): String? =
	images.firstNotNullOfOrNull { image ->
		image.href.trim().takeIf { it.isNotEmpty() }
	} ?: properties.firstNonBlankValue("image", "cover")

internal fun BinderyCatalogCard.Link.needsDetailArtworkResolution(): Boolean =
	imageUrl.isNullOrBlank() &&
		subtitle == "Collection" &&
		path.isNotBlank()

internal fun BinderyCatalog.firstPublicationImageHref(): String? =
	publications.firstNotNullOfOrNull { publication ->
		publication.images.firstNotNullOfOrNull { image ->
			image.href.trim().takeIf { it.isNotEmpty() }
		}
	}

private fun Map<String, String>.firstNonBlankValue(vararg keys: String): String? =
	keys.firstNotNullOfOrNull { desiredKey ->
		entries.firstOrNull { (key, value) ->
			key.equals(desiredKey, ignoreCase = true) && value.isNotBlank()
		}?.value?.trim()
	}

private fun Map<String, String>.hasTruthyValue(vararg keys: String): Boolean =
	keys.any { desiredKey ->
		val value = firstNonBlankValue(desiredKey)?.lowercase() ?: return@any false
		value !in setOf("false", "0", "no", "off", "none")
	}

private fun String.displayToken(): String =
	trim()
		.replace('-', ' ')
		.replace('_', ' ')
		.split(Regex("\\s+"))
		.filter { it.isNotEmpty() }
		.joinToString(separator = " ") { token ->
			token.replaceFirstChar { char ->
				if (char.isLowerCase()) char.titlecase() else char.toString()
			}
		}
