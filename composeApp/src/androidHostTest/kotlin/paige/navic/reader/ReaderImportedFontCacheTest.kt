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

	@Test
	fun remoteFontManifestDownloadsCachesListsAndDeletesWebViewFonts() {
		val cacheRoot = createTempDirectory("navic-reader-remote-fonts").toFile()
		val cache = ReaderImportedFontCache(cacheRoot)
		val manifest = """
			[
			  {
			    "id": "dyx-typewriter",
			    "name": "Dyx Typewriter",
			    "files": ["fonts/dyx/Dyx-Regular.ttf", "fonts/dyx/Dyx-Bold.otf"],
			    "size": 22,
			    "preview": "preview.png",
			    "desc": "Typewriter face",
			    "official": "https://fonts.example/dyx",
			    "license": { "name": "OFL", "url": "https://license.example/ofl" }
			  }
			]
		""".trimIndent()

		val entries = cache.fetchRemoteFontManifest(fetchText = { url ->
			assertEquals(ReaderRemoteFontManifestUrl, url)
			manifest
		})
		assertEquals(1, entries.size)
		assertEquals("dyx-typewriter", entries.single().id)
		assertEquals("Dyx Typewriter", entries.single().family)

		val downloaded = cache.downloadRemoteFont(entries.single()) { url ->
			when (url) {
				"${ReaderRemoteFontBaseUrl}fonts/dyx/Dyx-Regular.ttf" -> "REGULAR_FONT".encodeToByteArray()
				"${ReaderRemoteFontBaseUrl}fonts/dyx/Dyx-Bold.otf" -> "BOLD_FONT".encodeToByteArray()
				else -> error("Unexpected remote font URL: $url")
			}
		}

		assertEquals("dyx-typewriter", downloaded.id)
		assertEquals("Dyx Typewriter", downloaded.family)
		assertEquals(2, downloaded.fonts.size)
		assertEquals("REGULAR_FONT".length + "BOLD_FONT".length.toLong(), downloaded.byteSize)
		assertTrue(downloaded.fonts.all { font -> font.url.startsWith("https://appassets.androidplatform.net/reader-cache/fonts/remote/dyx-typewriter/") })
		assertEquals(listOf(downloaded), cache.listRemoteFonts())
		assertEquals(1, cache.deleteRemoteFont("dyx-typewriter"))
		assertTrue(cache.listRemoteFonts().isEmpty())
	}
}
