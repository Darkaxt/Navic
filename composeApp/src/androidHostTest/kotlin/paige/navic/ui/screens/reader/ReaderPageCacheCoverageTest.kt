package paige.navic.ui.screens.reader

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import paige.navic.reader.ReaderPageBitmapQuality

class ReaderPageCacheCoverageTest {
	@Test
	fun countsDistinctPagesOfTheMostRecentlyUsedPagination() {
		val root = Files.createTempDirectory("coverage").toFile()
		try {
			val book = "book".readerPageRasterHash()
			val other = "other".readerPageRasterHash()
			val oldLayout = "old".readerPageRasterHash()
			val newLayout = "new".readerPageRasterHash()
			val entries = listOf(
				// Old pagination: three pages, used long ago.
				entry(book, oldLayout, ordinal = 0, pageCount = 300, lastAccess = 10L),
				entry(book, oldLayout, ordinal = 1, pageCount = 300, lastAccess = 11L),
				entry(book, oldLayout, ordinal = 2, pageCount = 300, lastAccess = 12L),
				// Current pagination: two pages, one in two qualities.
				entry(book, newLayout, ordinal = 0, pageCount = 200, lastAccess = 50L),
				entry(book, newLayout, ordinal = 0, pageCount = 200, lastAccess = 51L, quality = ReaderPageBitmapQuality.entries.last()),
				entry(book, newLayout, ordinal = 7, pageCount = 200, lastAccess = 52L),
				// Another book never counts.
				entry(other, newLayout, ordinal = 3, pageCount = 200, lastAccess = 99L)
			)
			ReaderPageRasterManifest(root).write(entries)

			assertEquals(ReaderPageCacheCoverage(cachedPages = 2, totalPages = 200), readerPageCacheCoverage(root, setOf(book)))
			assertEquals(1, ReaderPageCacheCoverage(cachedPages = 2, totalPages = 200).percent)
			assertEquals(ReaderPageCacheCoverage(cachedPages = 0, totalPages = 0), readerPageCacheCoverage(root, setOf("none")))
		} finally {
			root.deleteRecursively()
		}
	}

	@Test
	fun twoPageLayoutsCountOnePreviewPerSpread() {
		val root = Files.createTempDirectory("coverage-spread").toFile()
		try {
			val book = "book".readerPageRasterHash()
			val layout = "spread".readerPageRasterHash()
			ReaderPageRasterManifest(root).write(
				listOf(0, 2, 4).map { ordinal -> entry(book, layout, ordinal = ordinal, pageCount = 10, lastAccess = 5L) }
			)
			assertEquals(ReaderPageCacheCoverage(cachedPages = 3, totalPages = 5), readerPageCacheCoverage(root, setOf(book)))
		} finally {
			root.deleteRecursively()
		}
	}

	private fun entry(
		publicationHash: String,
		paginationHash: String,
		ordinal: Int,
		pageCount: Int,
		lastAccess: Long,
		quality: ReaderPageBitmapQuality = ReaderPageBitmapQuality.entries.first()
	): ReaderPageRasterManifestEntry {
		val key = ReaderPageRasterKey(
			publicationHash = publicationHash,
			paginationHash = paginationHash,
			spineIndex = 1,
			hrefHash = "chapter".readerPageRasterHash(),
			chapterPageIndex = ordinal,
			visualPageOrdinal = ordinal,
			viewportWidth = 1000,
			viewportHeight = 800,
			layoutHash = "layout".readerPageRasterHash(),
			decorationHash = "decoration".readerPageRasterHash(),
			quality = quality,
			pageCount = pageCount
		)
		return ReaderPageRasterManifestEntry(
			key = key,
			rasterFileName = "${key.digest}.webp",
			byteSize = 100L,
			lastAccessEpochMillis = lastAccess,
			metadata = ReaderPageRasterMetadata(
				surfaceLeft = 0,
				surfaceTop = 0,
				surfaceRight = 1000,
				surfaceBottom = 800,
				fullLeafRect = null,
				leftLeafRect = null,
				gutterRect = null,
				rightLeafRect = null,
				reverseFaceColor = 0
			)
		)
	}
}
