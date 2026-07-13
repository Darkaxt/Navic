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
import paige.navic.data.remote.bindery.binderyEndpoint
import paige.navic.data.remote.bindery.configuredBinderyOpdsBaseUrl
import paige.navic.domain.repositories.hasReadyWhispersyncArtifact
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.reader.ReaderPublicationFormat
import paige.navic.reader.ReaderPublicationKind
import paige.navic.ui.navigation.Screen
import paige.navic.ui.navigation.SearchScope
import paige.navic.util.core.toFileSize
import kotlin.math.roundToLong

private const val BINDERY_BOOK_CATALOG_PAGE_SIZE = 5
internal const val BINDERY_MONITOR_REL = "https://bindery.app/opds/rel/monitor"
internal const val BINDERY_UNMONITOR_REL = "https://bindery.app/opds/rel/unmonitor"
internal const val BINDERY_DOWNLOAD_REQUEST_REL = "https://bindery.app/opds/rel/download-request"
internal const val BINDERY_ACQUISITION_REL = "http://opds-spec.org/acquisition"
private val BinderyAvailabilityQueryKeys = setOf("owned", "formats", "languages", "coverage")
private val BinderyRequiredBookFormats = setOf("ebook", "audiobook")
internal val BinderyAvailableFindingStatuses = setOf(
	"available",
	"downloadable",
	"downloaded",
	"imported",
	"owned",
	"ready",
	"acquired"
)
internal val BinderyUnavailableFindingStatuses = setOf(
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

internal fun String.normalizedBinderyAvailabilityLanguage(): String? {
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

internal fun normalizedBinderyAvailabilityLanguageFilter(languageFilter: String?): String? =
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
		val readingOrder: List<BinderyReadingOrderItem> = emptyList(),
		val hasActionableWhispersync: Boolean = false
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
				readingOrder = publication.readingOrder,
				hasActionableWhispersync = publication.hasActionableWhispersync()
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
		is BinderyCatalogCard.Book -> concreteBookMediaStatus(
			languageFilter = languageFilter,
			availability = availability,
			links = links,
			readingOrder = readingOrder
		)
		is BinderyCatalogCard.Link -> concreteBookMediaStatus(
			languageFilter = languageFilter,
			availability = availability,
			links = links
		)
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

fun BinderyPublication.availabilityStatus(languageFilter: String? = null): AurralOwnershipStatus =
	concreteBookMediaStatus(
		languageFilter = languageFilter,
		availability = availability,
		links = links,
		readingOrder = readingOrder
	)

fun BinderyPublication.availabilityAlpha(languageFilter: String? = null): Float =
	when (availabilityStatus(languageFilter)) {
		AurralOwnershipStatus.Missing -> 0.42f
		else -> 1f
	}

fun BinderyPublication.hasAvailableContent(languageFilter: String? = null): Boolean =
	availabilityStatus(languageFilter) != AurralOwnershipStatus.Missing

fun BinderyPublication.hasActionableWhispersync(): Boolean =
	sync?.syncPairs.orEmpty().any { pair -> pair.hasReadyWhispersyncArtifact() }

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

private fun concreteBookMediaStatus(
	languageFilter: String?,
	availability: BinderyAvailability?,
	links: List<BinderyLink>,
	readingOrder: List<BinderyReadingOrderItem> = emptyList()
): AurralOwnershipStatus {
	val formats = availability.ownedMediaFormats(languageFilter) +
		links.concreteLinkMediaFormats(languageFilter) +
		readingOrder.concreteReadingOrderMediaFormats(languageFilter)
	return formats
		.takeIf { it.isNotEmpty() }
		?.let(::ownershipStatusForRequiredBookFormats)
		?: availability.toOwnershipStatus(languageFilter)
		?: AurralOwnershipStatus.Missing
}

private fun BinderyAvailability?.ownedMediaFormats(languageFilter: String? = null): Set<String> {
	if (this == null || owned != true) return emptySet()
	val normalizedLanguage = normalizedBinderyAvailabilityLanguageFilter(languageFilter)
	val combinationFormats = ownedCombinations.mapNotNull { combination ->
		val combinationLanguage = combination.language.normalizedBinderyAvailabilityLanguage()
		if (normalizedLanguage != null && combinationLanguage != normalizedLanguage) return@mapNotNull null
		combination.format.normalizedBinderyMediaFormat()
	}
	if (ownedCombinations.isNotEmpty()) {
		return combinationFormats.toSet()
	}
	val normalizedLanguages = ownedLanguages
		.mapNotNull { language -> language.normalizedBinderyAvailabilityLanguage() }
		.toSet()
	val normalizedFormats = ownedFormats.mapNotNull(String::normalizedBinderyMediaFormat)
	val languagesMatch = normalizedLanguage == null ||
		normalizedLanguages.isEmpty() ||
		normalizedLanguage in normalizedLanguages
	val aggregateFormatsAreLanguageSafe = normalizedLanguage == null ||
		normalizedLanguages.size <= 1 ||
		BinderyRequiredBookFormats.count(normalizedFormats::contains) <= 1
	val directFormats = if (languagesMatch && aggregateFormatsAreLanguageSafe) {
		normalizedFormats
	} else {
		emptyList()
	}
	return directFormats.toSet()
}

private fun List<BinderyLink>.concreteLinkMediaFormats(languageFilter: String? = null): Set<String> {
	val normalizedLanguage = normalizedBinderyAvailabilityLanguageFilter(languageFilter)
	return mapNotNull { link ->
		if (!link.isAcquisition() || !link.matchesLanguage(normalizedLanguage)) return@mapNotNull null
		link.concreteMediaFormat()
	}.toSet()
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

private fun List<BinderyReadingOrderItem>.concreteReadingOrderMediaFormats(languageFilter: String? = null): Set<String> {
	val normalizedLanguage = normalizedBinderyAvailabilityLanguageFilter(languageFilter)
	return mapNotNull { item ->
		if (!item.matchesLanguage(normalizedLanguage)) return@mapNotNull null
		item.concreteMediaFormat()
	}.toSet()
}

private fun BinderyReadingOrderItem.concreteMediaFormat(): String? =
	properties.firstNonBlankValue("kind", "mediaType", "format")
		?.normalizedBinderyMediaFormat()
		?: type?.normalizedBinderyMediaFormat()

internal fun String.normalizedBinderyMediaFormat(): String? =
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
		candidates
			.filter { it.kind == kind }
			.preferredBinderyHubRow(kind)
	}
}

private fun List<BinderyHubRow>.preferredBinderyHubRow(kind: BinderyHubRowKind): BinderyHubRow? =
	when (kind) {
		BinderyHubRowKind.Audiobooks -> firstOrNull { row ->
			row.path.trim().substringBefore('?').trimEnd('/').equals(
				BinderyCatalogTab.Audiobooks.path,
				ignoreCase = true
			)
		} ?: firstOrNull()
		else -> firstOrNull()
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
