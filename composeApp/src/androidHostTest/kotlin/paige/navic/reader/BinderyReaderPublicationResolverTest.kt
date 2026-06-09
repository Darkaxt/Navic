package paige.navic.reader

import kotlinx.coroutines.runBlocking
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
		assertTrue(resolved.publicationFile.exists())
		assertEquals("EPUB_BYTES", resolved.publicationFile.readText())
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
		assertEquals(first.publicationFile.absolutePath, second.publicationFile.absolutePath)
		assertTrue(first.publicationUrl.startsWith("https://appassets.androidplatform.net/reader-cache/"))
	}
}
