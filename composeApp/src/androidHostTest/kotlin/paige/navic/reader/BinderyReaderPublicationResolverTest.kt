package paige.navic.reader

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BinderyReaderPublicationResolverTest {
	@Test
	fun resolvesAuthenticatedBinderyResourceToLocalPublicationUriForWebView() = runBlocking {
		val fetchedPaths = mutableListOf<String>()
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { path ->
				fetchedPaths += path
				"EPUB_BYTES".encodeToByteArray()
			},
			cacheRoot = createTempDirectory("navic-reader-publications").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "3693",
			title = "Alcatraz versus the Evil Librarians",
			resourceHref = "/opds/books/3693/resources/ebook-1",
			sourceUrl = "https://bindery.local/opds/books/3693/resources/ebook-1",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false
		)

		val resolved = resolver.resolve(request)

		assertEquals(listOf("/opds/books/3693/resources/ebook-1"), fetchedPaths)
		assertEquals("/opds/books/3693/resources/ebook-1", resolved.resourceHref)
		assertTrue(
			resolved.publicationUrl.startsWith("https://appassets.androidplatform.net/reader-cache/reader-publications/")
		)
		assertNotEquals(request.sourceUrl, resolved.publicationUrl)
		assertEquals(emptyMap(), resolved.requestHeaders)
		assertEquals(false, resolved.fromCache)
		assertTrue(resolved.publicationFile.exists())
		assertEquals("EPUB_BYTES", resolved.publicationFile.readText())
	}

	@Test
	fun reusesExistingPublicationCacheFileWithoutFetchingAgain() = runBlocking {
		var fetchCount = 0
		val cacheRoot = createTempDirectory("navic-reader-reuse-publications").toFile()
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount += 1
				"EPUB_BYTES_$fetchCount".encodeToByteArray()
			},
			cacheRoot = cacheRoot
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "3693",
			title = "Alcatraz",
			resourceHref = "/opds/books/3693/resources/ebook-1",
			sourceUrl = "https://bindery.local/opds/books/3693/resources/ebook-1",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false
		)

		val first = resolver.resolve(request)
		val second = resolver.resolve(request)

		assertEquals(1, fetchCount)
		assertEquals(false, first.fromCache)
		assertEquals(true, second.fromCache)
		assertEquals(first.publicationUrl, second.publicationUrl)
		assertEquals(first.publicationFile.absolutePath, second.publicationFile.absolutePath)
		assertEquals("EPUB_BYTES_1", second.publicationFile.readText())
	}

	@Test
	fun resolvesPdfResourcesToPdfCacheFiles() = runBlocking {
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { "%PDF-1.7".encodeToByteArray() },
			cacheRoot = createTempDirectory("navic-reader-pdf-publications").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "3816",
			title = "The Hobbit",
			resourceHref = "/opds/books/3816/resources/ebook-abb-pdf",
			sourceUrl = "https://bindery.local/opds/books/3816/resources/ebook-abb-pdf",
			kind = ReaderPublicationKind.Ebook,
			format = ReaderPublicationFormat.Pdf,
			mediaOverlayEnabled = false
		)

		val resolved = resolver.resolve(request)

		assertTrue(resolved.publicationUrl.endsWith("/publication.pdf"))
		assertTrue(resolved.publicationFile.name.endsWith(".pdf"))
		assertEquals("%PDF-1.7", resolved.publicationFile.readText())
		assertNull(resolved.shellCoverUrl)
	}

	@Test
	fun resolvesReaderDevLocalSourceToPdfCacheFileWithoutBinderyResourceFetch() = runBlocking {
		val sourceFile = kotlin.io.path.createTempFile("navic-readerdev-source", ".pdf").toFile()
		sourceFile.writeText("%PDF-1.7-LOCAL")
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { path -> error("Bindery fetch should not run for readerdev source: $path") },
			cacheRoot = createTempDirectory("navic-readerdev-pdf-publications").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "reader-dev",
			title = "PDF Navigation Fixture",
			resourceHref = "/fixtures/local/input.pdf",
			sourceUrl = sourceFile.toURI().toURL().toExternalForm(),
			kind = ReaderPublicationKind.Ebook,
			format = ReaderPublicationFormat.Pdf,
			mediaOverlayEnabled = false
		)

		val resolved = resolver.resolve(request)

		assertEquals("/fixtures/local/input.pdf", resolved.resourceHref)
		assertTrue(resolved.publicationUrl.endsWith("/publication.pdf"))
		assertEquals("%PDF-1.7-LOCAL", resolved.publicationFile.readText())
		assertEquals(false, resolved.fromCache)
		assertNull(resolved.shellCoverUrl)
	}

	@Test
	fun resolvesFoliateFormatsToMatchingCacheFileExtensions() = runBlocking {
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { path -> "BYTES:$path".encodeToByteArray() },
			cacheRoot = createTempDirectory("navic-reader-foliate-format-publications").toFile()
		)
		val cases = listOf(
			ReaderPublicationFormat.Azw3 to "azw3",
			ReaderPublicationFormat.Mobi to "mobi",
			ReaderPublicationFormat.Cbz to "cbz",
			ReaderPublicationFormat.Fb2 to "fb2"
		)

		cases.forEach { (format, extension) ->
			val resolved = resolver.resolve(
				ReaderPublicationResourceRequest(
					bookId = "3816",
					title = "The Hobbit",
					resourceHref = "/opds/books/3816/resources/ebook-$extension",
					sourceUrl = "https://bindery.local/opds/books/3816/resources/ebook-$extension",
					kind = ReaderPublicationKind.Ebook,
					format = format,
					mediaOverlayEnabled = false
				)
			)

			assertTrue(resolved.publicationUrl.endsWith("/publication.$extension"))
			assertEquals("publication.$extension", resolved.publicationFile.name)
			assertEquals("BYTES:/opds/books/3816/resources/ebook-$extension", resolved.publicationFile.readText())
			assertNull(resolved.shellCoverUrl)
		}
	}

	@Test
	fun extractsEpubCoverImageForNativeShellCoverSurface() = runBlocking {
		val coverBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { minimalEpubWithCover(coverBytes) },
			cacheRoot = createTempDirectory("navic-reader-cover-publications").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "3816",
			title = "The Hobbit",
			resourceHref = "/opds/books/3816/resources/ebook-epub",
			sourceUrl = "https://bindery.local/opds/books/3816/resources/ebook-epub",
			kind = ReaderPublicationKind.Ebook,
			format = ReaderPublicationFormat.Epub,
			mediaOverlayEnabled = false
		)

		val resolved = resolver.resolve(request)

		assertEquals(
			"https://appassets.androidplatform.net/reader-cache/reader-publications/${resolved.cacheKey}/cover.png",
			resolved.shellCoverUrl
		)
		val coverFile = resolved.publicationFile.parentFile!!.resolve("cover.png")
		assertTrue(coverFile.isFile)
		assertEquals(coverBytes.toList(), coverFile.readBytes().toList())
	}

	@Test
	fun extractsEpubCoverImageWhenOpfParserRejectsDoctype() = runBlocking {
		val coverBytes = byteArrayOf(0x42, 0x49, 0x4e, 0x44, 0x45, 0x52, 0x59)
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { minimalEpubWithCover(coverBytes, opfDoctype = true) },
			cacheRoot = createTempDirectory("navic-reader-cover-doctype-publications").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "3816",
			title = "The Hobbit",
			resourceHref = "/opds/books/3816/resources/ebook-doctype-cover",
			sourceUrl = "https://bindery.local/opds/books/3816/resources/ebook-doctype-cover",
			kind = ReaderPublicationKind.Ebook,
			format = ReaderPublicationFormat.Epub,
			mediaOverlayEnabled = false
		)

		val resolved = resolver.resolve(request)

		assertEquals(
			"https://appassets.androidplatform.net/reader-cache/reader-publications/${resolved.cacheKey}/cover.jpg",
			resolved.shellCoverUrl
		)
		val coverFile = resolved.publicationFile.parentFile!!.resolve("cover.jpg")
		assertTrue(coverFile.isFile)
		assertEquals(coverBytes.toList(), coverFile.readBytes().toList())
	}

	@Test
	fun localCacheContractIsStableAcrossBinderyBaseUrlChanges() = runBlocking {
		val cacheRoot = createTempDirectory("navic-reader-publications").toFile()
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { "EPUB_BYTES".encodeToByteArray() },
			cacheRoot = cacheRoot
		)
		val first = resolver.resolve(
			ReaderPublicationResourceRequest(
				bookId = "3693",
				title = "Alcatraz",
				resourceHref = "/opds/books/3693/resources/readaloud-1",
				sourceUrl = "https://bindery.local/opds/books/3693/resources/readaloud-1",
				kind = ReaderPublicationKind.Readaloud,
				mediaOverlayEnabled = true
			)
		)
		val second = resolver.resolve(
			ReaderPublicationResourceRequest(
				bookId = "3693",
				title = "Alcatraz",
				resourceHref = "/opds/books/3693/resources/readaloud-1",
				sourceUrl = "https://mirror.local/opds/books/3693/resources/readaloud-1",
				kind = ReaderPublicationKind.Readaloud,
				mediaOverlayEnabled = true
			)
		)

		assertEquals(first.cacheKey, second.cacheKey)
		assertEquals(false, first.fromCache)
		assertEquals(true, second.fromCache)
		assertEquals(first.publicationFile.absolutePath, second.publicationFile.absolutePath)
		assertTrue(first.publicationUrl.startsWith("https://appassets.androidplatform.net/reader-cache/"))
	}

	@Test
	fun localCacheContractIsStableAcrossAbsoluteAndRelativeResourceUrls() = runBlocking {
		var fetchCount = 0
		val cacheRoot = createTempDirectory("navic-reader-publication-url-forms").toFile()
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount += 1
				"EPUB_BYTES_$fetchCount".encodeToByteArray()
			},
			cacheRoot = cacheRoot
		)
		val first = resolver.resolve(
			ReaderPublicationResourceRequest(
				bookId = "3693",
				title = "Alcatraz",
				resourceHref = "https://bindery.local/opds/books/3693/resources/ebook-1?download=1#ignored",
				sourceUrl = "https://bindery.local/opds/books/3693/resources/ebook-1?download=1#ignored",
				kind = ReaderPublicationKind.Ebook,
				mediaOverlayEnabled = false
			)
		)
		val second = resolver.resolve(
			ReaderPublicationResourceRequest(
				bookId = "3693",
				title = "Alcatraz",
				resourceHref = "/opds/books/3693/resources/ebook-1",
				sourceUrl = "https://mirror.local/opds/books/3693/resources/ebook-1",
				kind = ReaderPublicationKind.Ebook,
				mediaOverlayEnabled = false
			)
		)

		assertEquals(1, fetchCount)
		assertEquals(first.cacheKey, second.cacheKey)
		assertEquals(false, first.fromCache)
		assertEquals(true, second.fromCache)
		assertEquals(first.publicationFile.absolutePath, second.publicationFile.absolutePath)
		assertEquals("EPUB_BYTES_1", second.publicationFile.readText())
	}
}

private fun minimalEpubWithCover(
	coverBytes: ByteArray,
	opfDoctype: Boolean = false
): ByteArray {
	val output = java.io.ByteArrayOutputStream()
	ZipOutputStream(output).use { zip ->
		zip.putNextEntry(ZipEntry("META-INF/container.xml"))
		zip.write(
			"""
			<?xml version="1.0" encoding="UTF-8"?>
			<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
				<rootfiles>
					<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
				</rootfiles>
			</container>
			""".trimIndent().encodeToByteArray()
		)
		zip.closeEntry()
		zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
		val opfDoctypeLine = if (opfDoctype) {
			"""<!DOCTYPE package [ <!ENTITY navic "cover"> ]>"""
		} else {
			""
		}
		val coverManifestItem = if (opfDoctype) {
			"""<item id="cover.jpg" href="images/cover.jpg" media-type="image/jpeg"/>"""
		} else {
			"""<item id="cover-image" href="images/cover.png" media-type="image/png" properties="cover-image"/>"""
		}
		zip.write(
			"""
			<?xml version="1.0" encoding="UTF-8"?>
			$opfDoctypeLine
			<package version="3.0" xmlns="http://www.idpf.org/2007/opf">
				<metadata>
					<meta name="cover" content="cover.jpg"/>
				</metadata>
				<manifest>
					$coverManifestItem
					<item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
				</manifest>
				<spine>
					<itemref idref="chapter"/>
				</spine>
			</package>
			""".trimIndent().encodeToByteArray()
		)
		zip.closeEntry()
		val coverPath = if (opfDoctype) "OEBPS/images/cover.jpg" else "OEBPS/images/cover.png"
		zip.putNextEntry(ZipEntry(coverPath))
		zip.write(coverBytes)
		zip.closeEntry()
	}
	return output.toByteArray()
}
