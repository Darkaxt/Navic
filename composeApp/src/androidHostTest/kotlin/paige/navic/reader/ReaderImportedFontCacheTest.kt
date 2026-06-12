package paige.navic.reader

import java.io.ByteArrayInputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReaderImportedFontCacheTest {
	@Test
	fun importFontCopiesAllowedFontIntoReaderCachePath() {
		val cacheRoot = createTempDirectory("navic-reader-fonts").toFile()
		val cache = ReaderImportedFontCache(cacheRoot)

		val imported = cache.importFont(
			input = ByteArrayInputStream("FONT_BYTES".encodeToByteArray()),
			displayName = "Storyteller Serif.ttf",
			mimeType = "font/ttf"
		)

		assertEquals("Storyteller Serif", imported.family)
		assertTrue(
			imported.url.startsWith("https://appassets.androidplatform.net/reader-cache/fonts/imported-"),
			imported.url
		)
		assertTrue(imported.url.endsWith(".ttf"), imported.url)
		val cachedFile = File(cacheRoot, "fonts/${imported.url.substringAfterLast('/')}")
		assertTrue(cachedFile.isFile, cachedFile.absolutePath)
		assertEquals("FONT_BYTES", cachedFile.readText())
	}

	@Test
	fun importFontUsesStableUrlForDuplicateFontBytes() {
		val cacheRoot = createTempDirectory("navic-reader-fonts-duplicate").toFile()
		val cache = ReaderImportedFontCache(cacheRoot)

		val first = cache.importFont(
			input = ByteArrayInputStream("SAME_FONT".encodeToByteArray()),
			displayName = "First Name.otf",
			mimeType = "font/otf"
		)
		val second = cache.importFont(
			input = ByteArrayInputStream("SAME_FONT".encodeToByteArray()),
			displayName = "Second Name.otf",
			mimeType = "font/otf"
		)

		assertEquals(first.url, second.url)
		assertEquals("Second Name", second.family)
	}

	@Test
	fun importFontRejectsUnsupportedAndEmptyFiles() {
		val cacheRoot = createTempDirectory("navic-reader-fonts-invalid").toFile()
		val cache = ReaderImportedFontCache(cacheRoot)

		assertFailsWith<IllegalArgumentException> {
			cache.importFont(
				input = ByteArrayInputStream("EPUB_BYTES".encodeToByteArray()),
				displayName = "book.epub",
				mimeType = "application/epub+zip"
			)
		}
		assertFailsWith<IllegalArgumentException> {
			cache.importFont(
				input = ByteArrayInputStream(ByteArray(0)),
				displayName = "empty.ttf",
				mimeType = "font/ttf"
			)
		}
	}

	@Test
	fun importedFontCacheReportsStorageAndCanBeCleared() {
		val cacheRoot = createTempDirectory("navic-reader-fonts-clear").toFile()
		val cache = ReaderImportedFontCache(cacheRoot)

		val first = cache.importFont(
			input = ByteArrayInputStream("FONT_ONE".encodeToByteArray()),
			displayName = "First.ttf",
			mimeType = "font/ttf"
		)
		val second = cache.importFont(
			input = ByteArrayInputStream("FONT_TWO_LONGER".encodeToByteArray()),
			displayName = "Second.otf",
			mimeType = "font/otf"
		)

		assertEquals(first.byteSize + second.byteSize, cache.cachedFontsByteSize())
		assertEquals(2, cache.clearImportedFonts())
		assertEquals(0L, cache.cachedFontsByteSize())
		assertTrue(cacheRoot.resolve("fonts").listFiles().isNullOrEmpty())
	}
}
