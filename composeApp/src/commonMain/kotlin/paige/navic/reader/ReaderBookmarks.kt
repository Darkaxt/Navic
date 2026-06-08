package paige.navic.reader

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val ReaderBookmarkJson = Json {
	ignoreUnknownKeys = true
	encodeDefaults = false
}

@Serializable
data class ReaderBookmark(
	val id: String,
	val bookId: String,
	val bookTitle: String,
	val href: String? = null,
	val cfi: String? = null,
	val progress: Double? = null,
	val sectionTitle: String? = null
) {
	val displayTitle: String
		get() = sectionTitle ?: href ?: cfi ?: bookTitle

	fun toLocator(): ReaderLocator =
		ReaderLocator(
			href = href,
			cfi = cfi,
			progress = progress
		)
}

data class ReaderBookmarkState(
	val bookmarks: List<ReaderBookmark> = emptyList()
) {
	fun bookmarksForBook(bookId: String): List<ReaderBookmark> =
		bookmarks.filter { it.bookId == bookId }

	fun isBookmarked(bookId: String, locator: ReaderLocator?): Boolean =
		bookmarkForLocator(bookId, locator) != null

	fun bookmarkForLocator(bookId: String, locator: ReaderLocator?): ReaderBookmark? {
		val key = locator?.bookmarkTargetKey() ?: return null
		return bookmarks.firstOrNull { bookmark ->
			bookmark.bookId == bookId && bookmark.targetKey == key
		}
	}

	fun toggleBookmark(
		bookId: String,
		bookTitle: String,
		locator: ReaderLocator?,
		sectionTitle: String?
	): ReaderBookmarkState {
		val bookmark = readerBookmarkFromLocator(
			bookId = bookId,
			bookTitle = bookTitle,
			locator = locator,
			sectionTitle = sectionTitle
		) ?: return this
		return if (isBookmarked(bookId, locator)) {
			copy(bookmarks = bookmarks.filterNot { it.id == bookmark.id })
		} else {
			copy(bookmarks = bookmarks.filterNot { it.id == bookmark.id } + bookmark)
		}
	}
}

fun readerBookmarkFromLocator(
	bookId: String,
	bookTitle: String,
	locator: ReaderLocator?,
	sectionTitle: String?
): ReaderBookmark? {
	val key = locator?.bookmarkTargetKey() ?: return null
	return ReaderBookmark(
		id = "$bookId|$key",
		bookId = bookId,
		bookTitle = bookTitle,
		href = locator.href?.trim()?.takeIf { it.isNotEmpty() },
		cfi = locator.cfi?.trim()?.takeIf { it.isNotEmpty() },
		progress = locator.progress?.takeIf(Double::isFinite),
		sectionTitle = sectionTitle?.trim()?.takeIf { it.isNotEmpty() }
	)
}

fun encodeReaderBookmarks(bookmarks: List<ReaderBookmark>): String =
	ReaderBookmarkJson.encodeToString(bookmarks)

fun decodeReaderBookmarks(json: String): List<ReaderBookmark> =
	runCatching {
		ReaderBookmarkJson.decodeFromString<List<ReaderBookmark>>(json)
			.filter { it.id.isNotBlank() && it.bookId.isNotBlank() }
	}.getOrDefault(emptyList())

private val ReaderBookmark.targetKey: String?
	get() = cfi?.trim()?.takeIf { it.isNotEmpty() }
		?: href?.trim()?.takeIf { it.isNotEmpty() }

private fun ReaderLocator.bookmarkTargetKey(): String? =
	cfi?.trim()?.takeIf { it.isNotEmpty() }
		?: href?.trim()?.takeIf { it.isNotEmpty() }
