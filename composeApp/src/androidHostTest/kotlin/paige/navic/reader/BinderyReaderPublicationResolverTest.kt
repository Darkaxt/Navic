package paige.navic.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import paige.navic.domain.repositories.BinderyBookResource
import paige.navic.domain.repositories.BinderyResourceCatalog
import paige.navic.domain.repositories.BinderyResourceMetadata
import paige.navic.ui.navigation.Screen
import paige.navic.ui.screens.bindery.BinderyBookVersionKind
import paige.navic.ui.screens.bindery.binderyBookVersionRows
import paige.navic.ui.screens.bindery.binderyReaderDestinationForVersionRow
import paige.navic.ui.screens.reader.ReaderPageRasterDescriptor

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BinderyReaderPublicationResolverTest {
	@Test
	fun resolvedPublicationOwnsItsSessionDirectory() = runBlocking {
		val cacheRoot = createTempDirectory("navic-reader-publication-lease").toFile()
		val resolved = BinderyReaderPublicationResolver(
			fetchResourceBytes = { "EPUB_BYTES".encodeToByteArray() },
			cacheRoot = cacheRoot
		).resolve(
			ReaderPublicationResourceRequest(
				bookId = "lease-book",
				title = "Lease Book",
				resourceHref = "/opds/books/lease-book/resources/epub",
				sourceUrl = "https://bindery.local/lease-book.epub",
				kind = ReaderPublicationKind.Ebook,
				mediaOverlayEnabled = false
			)
		)

		val sessionDirectory = resolved.publicationFile.parentFile!!
		assertTrue(sessionDirectory.isDirectory)
		assertEquals(1, resolved.sessionLease.release())
		assertTrue(!sessionDirectory.exists())
	}

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
			mediaOverlayEnabled = false,
			contentRevisionHash = "stable-revision"
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
		val coverBytes = embeddedCoverPngBytes()
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
	fun cachesExternalBinderyShellCoverAsLocalAssetUriForNativeCoverSurface() = runBlocking {
		val epubCoverBytes = embeddedCoverPngBytes()
		val externalCoverBytes = externalCoverPngBytes()
		val fetchedPaths = mutableListOf<String>()
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { path ->
				fetchedPaths += path
				when (path) {
					"/opds/books/3816/resources/ebook-epub" -> minimalEpubWithCover(epubCoverBytes)
					"/api/v1/books/3816/generated/fullscreen-cover?aspect=0.72" -> externalCoverBytes
					else -> error("Unexpected resource fetch: $path")
				}
			},
			cacheRoot = createTempDirectory("navic-reader-external-shell-cover").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "3816",
			title = "The Hobbit",
			resourceHref = "/opds/books/3816/resources/ebook-epub",
			sourceUrl = "https://bindery.local/opds/books/3816/resources/ebook-epub",
			kind = ReaderPublicationKind.Ebook,
			format = ReaderPublicationFormat.Epub,
			mediaOverlayEnabled = false,
			externalShellCoverHref = "/api/v1/books/3816/generated/fullscreen-cover?aspect=0.72"
		)

		val resolved = resolver.resolve(request)

		assertEquals(
			listOf(
				"/opds/books/3816/resources/ebook-epub",
				"/api/v1/books/3816/generated/fullscreen-cover?aspect=0.72"
			),
			fetchedPaths
		)
		assertTrue(
			resolved.shellCoverUrl.orEmpty().startsWith(
				"https://appassets.androidplatform.net/reader-cache/reader-publications/${resolved.cacheKey}/shell-cover-"
			)
		)
		assertTrue(resolved.shellCoverUrl.orEmpty().endsWith(".png"))
		val shellCoverFile = resolved.publicationFile.parentFile!!.resolve(
			resolved.shellCoverUrl!!.substringAfterLast('/')
		)
		assertTrue(shellCoverFile.isFile)
		assertEquals(externalCoverBytes.toList(), shellCoverFile.readBytes().toList())
		assertEquals(epubCoverBytes.toList(), resolved.publicationFile.parentFile!!.resolve("cover.png").readBytes().toList())
	}

	@Test
	fun metadataRevisionFlowsThroughRuntimeRequestAndLocalRasterIdentity() = runBlocking {
		fun reader(kind: BinderyBookVersionKind, version: String): Screen.Reader {
			val resourceKind = when (kind) {
				BinderyBookVersionKind.Ebook -> "ebook"
				BinderyBookVersionKind.Readaloud -> "readaloud"
				BinderyBookVersionKind.Audiobook -> error("Unsupported synthetic case")
			}
			val row = binderyBookVersionRows(
				manifest = null,
				resourceCatalog = BinderyResourceCatalog(
					title = "Synthetic",
					resources = listOf(
						BinderyBookResource(
							href = "/synthetic/publication",
							title = "Synthetic",
							type = "application/epub+zip",
							kind = resourceKind,
							metadata = BinderyResourceMetadata(
								version = version,
								resourceKey = "synthetic-resource",
								bookFileId = "synthetic-file",
								sizeBytes = 4096
							)
						)
					)
				)
			).single { candidate -> candidate.kind == kind }
			return checkNotNull(
				binderyReaderDestinationForVersionRow(
					row = row,
					bookId = "synthetic-book",
					bookTitle = "Synthetic",
					opdsBaseUrl = "https://origin.invalid/opds"
				)
			)
		}

		fun rasterDescriptor(publicationUrl: String) = ReaderPageRasterDescriptor(
			publicationUrl = publicationUrl,
			paginationFingerprint = "synthetic-pagination",
			layoutFingerprint = "synthetic-layout",
			decorationFingerprint = "synthetic-decoration",
			viewportWidth = 1200,
			viewportHeight = 800,
			pageCount = 10,
			spineIndex = 0,
			href = "synthetic.xhtml",
			chapterPageIndex = 0,
			chapterPageCount = 10,
			visualPageOrdinal = 0
		)

		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = { "SYNTHETIC_PUBLICATION".encodeToByteArray() },
			cacheRoot = createTempDirectory("navic-reader-metadata-revision-flow").toFile()
		)
		listOf(BinderyBookVersionKind.Ebook, BinderyBookVersionKind.Readaloud).forEach { kind ->
			val firstRequest = reader(kind, "revision-a")
				.readerPublicationResourceRequest(accountScopeHash = "synthetic-account")
			val secondRequest = reader(kind, "revision-b")
				.readerPublicationResourceRequest(accountScopeHash = "synthetic-account")

			assertNotEquals(firstRequest.contentRevisionHash, secondRequest.contentRevisionHash)
			assertTrue(firstRequest.contentRevisionHash.matches(Regex("[0-9a-f]{64}")))
			assertTrue(secondRequest.contentRevisionHash.matches(Regex("[0-9a-f]{64}")))

			val first = resolver.resolve(firstRequest)
			val second = resolver.resolve(secondRequest)

			assertNotEquals(first.publicationUrl, second.publicationUrl)
			assertNotEquals(
				rasterDescriptor(first.publicationUrl).key(ReaderPageBitmapQuality.Balanced).digest,
				rasterDescriptor(second.publicationUrl).key(ReaderPageBitmapQuality.Balanced).digest
			)
		}
	}

	@Test
	fun isolatesPublicationCacheAcrossSourceOrigins() = runBlocking {
		var fetchCount = 0
		val cacheRoot = createTempDirectory("navic-reader-publication-origins").toFile()
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount += 1
				"EPUB_BYTES_$fetchCount".encodeToByteArray()
			},
			cacheRoot = cacheRoot
		)
		val first = resolver.resolve(
			ReaderPublicationResourceRequest(
				bookId = "synthetic-book",
				title = "Synthetic",
				resourceHref = "/synthetic/publication",
				sourceUrl = "https://origin-one.invalid/synthetic/publication",
				kind = ReaderPublicationKind.Readaloud,
				mediaOverlayEnabled = true,
				contentRevisionHash = "synthetic-revision"
			)
		)
		val second = resolver.resolve(
			ReaderPublicationResourceRequest(
				bookId = "synthetic-book",
				title = "Synthetic",
				resourceHref = "/synthetic/publication",
				sourceUrl = "https://origin-two.invalid/synthetic/publication",
				kind = ReaderPublicationKind.Readaloud,
				mediaOverlayEnabled = true,
				contentRevisionHash = "synthetic-revision"
			)
		)

		assertEquals(2, fetchCount)
		assertNotEquals(first.cacheKey, second.cacheKey)
		assertEquals(false, first.fromCache)
		assertEquals(false, second.fromCache)
		assertNotEquals(first.publicationFile.absolutePath, second.publicationFile.absolutePath)
		assertNotEquals(first.publicationUrl, second.publicationUrl)
		assertTrue(first.publicationUrl.startsWith("https://appassets.androidplatform.net/reader-cache/"))
	}

	@Test
	fun isolatesPublicationCacheAcrossAccountScopes() = runBlocking {
		var fetchCount = 0
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount += 1
				"EPUB_ACCOUNT_$fetchCount".encodeToByteArray()
			},
			cacheRoot = createTempDirectory("navic-reader-publication-accounts").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "synthetic-book",
			title = "Synthetic",
			resourceHref = "/synthetic/publication",
			sourceUrl = "https://origin.invalid/synthetic/publication",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false,
			contentRevisionHash = "synthetic-revision"
		)

		val first = resolver.resolve(request.copy(accountScopeHash = "account-one"))
		val second = resolver.resolve(request.copy(accountScopeHash = "account-two"))

		assertEquals(2, fetchCount)
		assertNotEquals(first.cacheKey, second.cacheKey)
		assertEquals(false, second.fromCache)
	}

	@Test
	fun isolatesPublicationCacheAcrossSourceRevisions() = runBlocking {
		var fetchCount = 0
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount += 1
				"EPUB_REVISION_$fetchCount".encodeToByteArray()
			},
			cacheRoot = createTempDirectory("navic-reader-publication-revisions").toFile()
		)
		val first = resolver.resolve(
			ReaderPublicationResourceRequest(
				bookId = "synthetic-book",
				title = "Synthetic",
				resourceHref = "/synthetic/publication",
				sourceUrl = "https://origin.invalid/synthetic/publication",
				kind = ReaderPublicationKind.Ebook,
				mediaOverlayEnabled = false,
				contentRevisionHash = "revision-one"
			)
		)
		val second = resolver.resolve(
			ReaderPublicationResourceRequest(
				bookId = "synthetic-book",
				title = "Synthetic",
				resourceHref = "/synthetic/publication",
				sourceUrl = "https://origin.invalid/synthetic/publication",
				kind = ReaderPublicationKind.Ebook,
				mediaOverlayEnabled = false,
				contentRevisionHash = "revision-two"
			)
		)

		assertEquals(2, fetchCount)
		assertNotEquals(first.cacheKey, second.cacheKey)
		assertEquals(false, second.fromCache)
	}

	@Test
	fun missingRevisionAuthorityFetchesAndContentAddressesChangedBytes() = runBlocking {
		var fetchCount = 0
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount += 1
				"SYNTHETIC_CONTENT_$fetchCount".encodeToByteArray()
			},
			cacheRoot = createTempDirectory("navic-reader-content-addressed-revision").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "synthetic-book",
			title = "Synthetic",
			resourceHref = "/synthetic/publication",
			sourceUrl = "https://origin.invalid/synthetic/publication",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false
		)

		val first = resolver.resolve(request)
		val second = resolver.resolve(request)

		assertEquals(2, fetchCount)
		assertNotEquals(first.cacheKey, second.cacheKey)
		assertNotEquals(first.publicationUrl, second.publicationUrl)
		assertEquals(false, second.fromCache)
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
				mediaOverlayEnabled = false,
				contentRevisionHash = "stable-revision"
			)
		)
		val second = resolver.resolve(
			ReaderPublicationResourceRequest(
				bookId = "3693",
				title = "Alcatraz",
				resourceHref = "/opds/books/3693/resources/ebook-1",
				sourceUrl = "https://bindery.local/opds/books/3693/resources/ebook-1?download=1#ignored",
				kind = ReaderPublicationKind.Ebook,
				mediaOverlayEnabled = false,
				contentRevisionHash = "stable-revision"
			)
		)

		assertEquals(1, fetchCount)
		assertEquals(first.cacheKey, second.cacheKey)
		assertEquals(false, first.fromCache)
		assertEquals(true, second.fromCache)
		assertEquals(first.publicationFile.absolutePath, second.publicationFile.absolutePath)
		assertEquals("EPUB_BYTES_1", second.publicationFile.readText())
	}

	@Test
	fun rejectsAndReplacesTruncatedPublicationCacheMaterial() = runBlocking {
		var fetchCount = 0
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount += 1
				"SYNTHETIC_COMPLETE_$fetchCount".encodeToByteArray()
			},
			cacheRoot = createTempDirectory("navic-reader-publication-partial").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "synthetic-book",
			title = "Synthetic",
			resourceHref = "/synthetic/publication",
			sourceUrl = "https://origin.invalid/synthetic/publication",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false,
			contentRevisionHash = "stable-revision"
		)

		val first = resolver.resolve(request)
		first.publicationFile.writeBytes("SYNTHETIC_PARTIAL".encodeToByteArray())
		val second = resolver.resolve(request)

		assertEquals(2, fetchCount)
		assertEquals(false, second.fromCache)
		assertEquals("SYNTHETIC_COMPLETE_2", second.publicationFile.readText())
	}

	@Test
	fun cancellationDuringPublicationFetchLeavesCleanRepairRetry() = runBlocking {
		var fetchCount = 0
		val fetchStarted = CompletableDeferred<Unit>()
		val cacheRoot = createTempDirectory("navic-reader-publication-cancel-retry").toFile()
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount += 1
				if (fetchCount == 1) {
					fetchStarted.complete(Unit)
					awaitCancellation()
				}
				"SYNTHETIC_REPAIRED".encodeToByteArray()
			},
			cacheRoot = cacheRoot
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "synthetic-book",
			title = "Synthetic",
			resourceHref = "/synthetic/publication",
			sourceUrl = "https://origin.invalid/synthetic/publication",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false,
			contentRevisionHash = "synthetic-revision"
		)

		val cancelled = launch { resolver.resolve(request) }
		fetchStarted.await()
		cancelled.cancelAndJoin()

		val publicationDirectory = cacheRoot.resolve("reader-publications/${request.readerPublicationCacheKey()}")
		assertEquals(1, fetchCount)
		assertTrue(!publicationDirectory.exists())

		val repaired = resolver.resolve(request)

		assertEquals(2, fetchCount)
		assertEquals(false, repaired.fromCache)
		assertEquals("SYNTHETIC_REPAIRED", repaired.publicationFile.readText())
		assertTrue(
			repaired.publicationFile.parentFile!!.listFiles().orEmpty()
				.none { file -> file.name.endsWith(".tmp") }
		)
	}

	@Test
	fun serializesConcurrentSameTargetPublicationWrites() = runBlocking {
		val fetchCount = AtomicInteger()
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount.incrementAndGet()
				delay(100)
				"SYNTHETIC_CONCURRENT".encodeToByteArray()
			},
			cacheRoot = createTempDirectory("navic-reader-publication-concurrent").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "synthetic-book",
			title = "Synthetic",
			resourceHref = "/synthetic/publication",
			sourceUrl = "https://origin.invalid/synthetic/publication",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false,
			contentRevisionHash = "stable-revision"
		)

		val resolved = coroutineScope {
			List(8) { async { resolver.resolve(request) } }.awaitAll()
		}

		assertEquals(1, fetchCount.get())
		assertEquals(1, resolved.count { result -> !result.fromCache })
		assertTrue(resolved.all { result -> result.publicationFile.readText() == "SYNTHETIC_CONCURRENT" })
		assertTrue(
			resolved.first().publicationFile.parentFile!!.listFiles().orEmpty()
				.none { file -> file.name.endsWith(".tmp") }
		)
	}

	@Test
	fun cacheCriticalWorkUsesInjectedDispatcher() = runBlocking {
		val cacheExecutor = Executors.newSingleThreadExecutor { task ->
			Thread(task, "synthetic-reader-cache-io")
		}
		cacheExecutor.asCoroutineDispatcher().use { cacheDispatcher ->
			val cacheWorkThreads = mutableListOf<String>()
			var fetchThread = ""
			val resolver = BinderyReaderPublicationResolver(
				fetchResourceBytes = {
					fetchThread = Thread.currentThread().name
					"SYNTHETIC_DISPATCHED_CACHE".encodeToByteArray()
				},
				cacheRoot = createTempDirectory("navic-reader-publication-dispatcher").toFile(),
				cacheDispatcher = cacheDispatcher,
				cacheWorkObserver = { cacheWorkThreads += Thread.currentThread().name }
			)
			val resolved = resolver.resolve(
				ReaderPublicationResourceRequest(
					bookId = "synthetic-book",
					title = "Synthetic",
					resourceHref = "/synthetic/publication",
					sourceUrl = "https://origin.invalid/synthetic/publication",
					kind = ReaderPublicationKind.Ebook,
					mediaOverlayEnabled = false,
					contentRevisionHash = "stable-revision"
				)
			)

			assertTrue(cacheWorkThreads.isNotEmpty())
			assertTrue(cacheWorkThreads.all { thread -> thread.startsWith("synthetic-reader-cache-io") })
			assertNotEquals("synthetic-reader-cache-io", fetchThread)
			assertEquals(1, resolved.sessionLease.release())
		}
	}

	@Test
	fun sharedTargetLeaseRetainsCacheUntilExactFinalOwnerReleases() = runBlocking {
		var fetchCount = 0
		val resolver = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount += 1
				"SYNTHETIC_SHARED_LEASE".encodeToByteArray()
			},
			cacheRoot = createTempDirectory("navic-reader-publication-shared-lease").toFile()
		)
		val request = ReaderPublicationResourceRequest(
			bookId = "synthetic-book",
			title = "Synthetic",
			resourceHref = "/synthetic/publication",
			sourceUrl = "https://origin.invalid/synthetic/publication",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false,
			contentRevisionHash = "stable-revision"
		)

		val first = resolver.resolve(request)
		val second = resolver.resolve(request)

		assertEquals(1, fetchCount)
		assertEquals(0, first.sessionLease.release())
		assertEquals("SYNTHETIC_SHARED_LEASE", second.publicationFile.readText())

		val stillAdmissible = resolver.resolve(request)
		assertEquals(1, fetchCount)
		assertEquals(true, stillAdmissible.fromCache)
		assertEquals(0, second.sessionLease.release())
		assertTrue(stillAdmissible.publicationFile.isFile)
		assertEquals(1, stillAdmissible.sessionLease.release())
		assertTrue(!stillAdmissible.publicationFile.parentFile!!.exists())
		assertEquals(0, stillAdmissible.sessionLease.release())
	}

	@Test
	fun inFlightReplacementPinsTargetBeforeAsyncCacheValidation() = runBlocking {
		val fetchCount = AtomicInteger()
		val cacheRoot = createTempDirectory("navic-reader-publication-in-flight-lease").toFile()
		val request = ReaderPublicationResourceRequest(
			bookId = "synthetic-book",
			title = "Synthetic",
			resourceHref = "/synthetic/publication",
			sourceUrl = "https://origin.invalid/synthetic/publication",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false,
			contentRevisionHash = "stable-revision"
		)
		fun resolver(cacheWorkObserver: (() -> Unit)? = null) = BinderyReaderPublicationResolver(
			fetchResourceBytes = {
				fetchCount.incrementAndGet()
				"SYNTHETIC_IN_FLIGHT_LEASE".encodeToByteArray()
			},
			cacheRoot = cacheRoot,
			cacheWorkObserver = cacheWorkObserver
		)

		val ownerA = resolver().resolve(request)
		val validationStarted = CountDownLatch(1)
		val completeValidation = CountDownLatch(1)
		val replacement = async(start = CoroutineStart.UNDISPATCHED) {
			resolver(
				cacheWorkObserver = {
					validationStarted.countDown()
					completeValidation.await()
				}
			).resolve(request)
		}
		validationStarted.await()
		try {
			assertEquals(0, ownerA.sessionLease.release())
			assertEquals("SYNTHETIC_IN_FLIGHT_LEASE", ownerA.publicationFile.readText())
		} finally {
			completeValidation.countDown()
		}

		val ownerB = replacement.await()
		assertEquals(1, fetchCount.get())
		assertEquals(true, ownerB.fromCache)
		assertEquals("SYNTHETIC_IN_FLIGHT_LEASE", ownerB.publicationFile.readText())

		val admitted = resolver().resolve(request)
		assertEquals(true, admitted.fromCache)
		assertEquals(0, admitted.sessionLease.release())
		assertTrue(ownerB.publicationFile.isFile)
		assertEquals(1, ownerB.sessionLease.release())
		assertTrue(!ownerB.publicationFile.parentFile!!.exists())
		assertEquals(0, ownerB.sessionLease.release())
	}

	@Test
	fun publicationCacheIdentityPreservesFieldBoundaries() {
		val shared = ReaderPublicationResourceRequest(
			bookId = "a",
			title = "Synthetic",
			resourceHref = "x|Ebook|Epub|false|y",
			sourceUrl = "https://origin.invalid/synthetic/publication",
			kind = ReaderPublicationKind.Ebook,
			mediaOverlayEnabled = false,
			accountScopeHash = "synthetic-account",
			contentRevisionHash = "synthetic-revision"
		)
		val shiftedBoundary = shared.copy(
			bookId = "a|Ebook|Epub|false|x",
			resourceHref = "y"
		)

		assertNotEquals(shared.readerPublicationCacheKey(), shiftedBoundary.readerPublicationCacheKey())
	}
}

private fun embeddedCoverPngBytes(): ByteArray = java.util.Base64.getDecoder().decode(
	"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC"
)

private fun externalCoverPngBytes(): ByteArray = java.util.Base64.getDecoder().decode(
	"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNgYPgPAAEDAQAIicLsAAAAAElFTkSuQmCC"
)

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
