package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyBookResource
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.BinderyManifest
import paige.navic.domain.repositories.BinderyPublication
import paige.navic.domain.repositories.BinderyReadingOrderItem
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.domain.repositories.configuredBinderyOpdsBaseUrl
import paige.navic.domain.models.queueTotalDurationLabel
import paige.navic.ui.navigation.Screen
import paige.navic.util.core.toFileSize
import kotlin.math.roundToLong

private const val BINDERY_BOOK_CATALOG_PAGE_SIZE = 5

enum class BinderyCatalogTab(
	val path: String
) {
	Audiobooks("/opds/formats/audiobook"),
	Books("/opds/books"),
	Collections("/opds/collections"),
	Authors("/opds/authors")
}

fun BinderyCatalogTab.initialCatalogPath(): String =
	binderyInitialCatalogPath(path)

private fun String.withLimit(limit: Int): String =
	if ('?' in this) "$this&limit=$limit" else "$this?limit=$limit"

fun binderyInitialCatalogPath(path: String): String {
	val trimmed = path.trim()
	val normalizedPath = trimmed.substringBefore('?').trimEnd('/').lowercase()
	return if ((normalizedPath == BinderyCatalogTab.Audiobooks.path.lowercase() ||
			normalizedPath == BinderyCatalogTab.Books.path.lowercase()) &&
		!trimmed.substringAfter('?', "").split('&').any { parameter ->
			parameter.substringBefore('=').equals("limit", ignoreCase = true)
		}
	) {
		trimmed.withLimit(BINDERY_BOOK_CATALOG_PAGE_SIZE)
	} else {
		trimmed
	}
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
		val imageUrl: String?
	) : BinderyCatalogCard

	data class Link(
		override val id: String,
		override val title: String,
		override val subtitle: String?,
		val path: String,
		val imageUrl: String? = null,
		val properties: Map<String, String> = emptyMap()
	) : BinderyCatalogCard
}

fun binderyCatalogCards(
	catalog: BinderyCatalog,
	tab: BinderyCatalogTab?
): List<BinderyCatalogCard> =
	if (tab == BinderyCatalogTab.Collections || tab == BinderyCatalogTab.Authors ||
		(catalog.publications.isEmpty() && catalog.navigation.isNotEmpty())
	) {
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
				properties = link.properties
			)
		}
	} else {
		catalog.publications.map { publication ->
			BinderyCatalogCard.Book(
				id = publication.id ?: publication.title,
				title = publication.title,
				subtitle = publication.author,
				imageUrl = publication.images.firstOrNull()?.href
			)
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
	Ebook
}

data class BinderyBookVersionRow(
	val id: String,
	val kind: BinderyBookVersionKind,
	val title: String,
	val subtitle: String?
)

fun binderyBookVersionRows(
	manifest: BinderyManifest?,
	resourceCatalog: BinderyResourceCatalog?
): List<BinderyBookVersionRow> {
	val audioItems = manifest?.readingOrder.orEmpty().ifEmpty {
		resourceCatalog?.resources.orEmpty()
			.filter(BinderyBookResource::isAudioResource)
			.map(BinderyBookResource::toReadingOrderItem)
	}
	val ebookResources = (
		resourceCatalog?.resources.orEmpty().filter(BinderyBookResource::isEbookResource) +
			manifest?.links.orEmpty()
				.filter(BinderyLink::isEbookAcquisition)
				.map(BinderyLink::toBookResource)
		).distinctBy { resource -> resource.href }

	return buildList {
		if (audioItems.isNotEmpty()) {
			add(audioItems.toAudiobookVersionRow())
		}
		ebookResources.forEach { resource ->
			add(resource.toEbookVersionRow())
		}
	}
}

private fun List<BinderyReadingOrderItem>.toAudiobookVersionRow(): BinderyBookVersionRow {
	val totalDuration = sumOf { it.durationSeconds ?: 0.0 }.takeIf { it > 0.0 }
	val totalSize = sumOf { it.sizeBytes ?: 0L }.takeIf { it > 0L }
	val partsText = if (size == 1) "1 part" else "$size parts"
	val subtitle = listOfNotNull(
		partsText,
		totalDuration?.roundToLong()?.let(::queueTotalDurationLabel),
		totalSize?.toFileSize()
	).joinToString(separator = " / ")
	return BinderyBookVersionRow(
		id = "audiobook",
		kind = BinderyBookVersionKind.Audiobook,
		title = "Audiobook",
		subtitle = subtitle
	)
}

private fun BinderyBookResource.toEbookVersionRow(): BinderyBookVersionRow =
	BinderyBookVersionRow(
		id = href,
		kind = BinderyBookVersionKind.Ebook,
		title = title,
		subtitle = listOfNotNull(
			type.toReadableBookFormat(),
			sizeBytes?.toFileSize()
		).joinToString(separator = " / ").takeIf { it.isNotBlank() }
	)

private fun BinderyBookResource.isAudioResource(): Boolean =
	kind.equals("audio", ignoreCase = true) ||
		kind.equals("audiobook", ignoreCase = true) ||
		type?.startsWith("audio/", ignoreCase = true) == true

private fun BinderyBookResource.isEbookResource(): Boolean =
	kind.equals("ebook", ignoreCase = true) ||
		type.isEbookMediaType()

private fun BinderyLink.isEbookAcquisition(): Boolean =
	rel.any { it.equals("http://opds-spec.org/acquisition", ignoreCase = true) } &&
		(properties["kind"]?.equals("ebook", ignoreCase = true) == true || type.isEbookMediaType())

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
		sizeBytes = sizeBytes
	)

private fun String?.isEbookMediaType(): Boolean =
	this?.let { mediaType ->
		"epub" in mediaType.lowercase() ||
			"pdf" in mediaType.lowercase() ||
			"mobi" in mediaType.lowercase() ||
			"ebook" in mediaType.lowercase()
	} == true

private fun String?.toReadableBookFormat(): String? {
	val normalized = this?.lowercase() ?: return null
	return when {
		"epub" in normalized -> "EPUB"
		"pdf" in normalized -> "PDF"
		"mobi" in normalized -> "MOBI"
		"audiobook" in normalized -> "Audiobook"
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
			properties = link.properties
		)
	}

fun List<BinderyPublication>.sortedForBinderyDetail(): List<BinderyPublication> =
	filter { publication -> publication.publicationYearSortValue() != null }
		.sortedWith(
			compareBy<BinderyPublication>(
				{ publication -> publication.publicationYearSortValue() },
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
