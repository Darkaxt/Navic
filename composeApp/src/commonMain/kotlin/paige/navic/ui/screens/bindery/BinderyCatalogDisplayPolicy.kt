package paige.navic.ui.screens.bindery

import paige.navic.domain.repositories.BinderyCatalog
import paige.navic.domain.repositories.BinderyLink
import paige.navic.domain.repositories.configuredBinderyOpdsBaseUrl

enum class BinderyCatalogTab(
	val path: String
) {
	Audiobooks("/opds/formats/audiobook"),
	Books("/opds/books"),
	Collections("/opds/collections"),
	Authors("/opds/authors")
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
		val imageUrl: String? = null
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
				imageUrl = link.preferredImageHref()
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
