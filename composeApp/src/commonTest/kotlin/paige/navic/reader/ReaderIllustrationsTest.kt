package paige.navic.reader

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import paige.navic.data.remote.bindery.binderyIllustrationImageUrl
import paige.navic.data.remote.bindery.binderyIllustrationsIndexUrl
import paige.navic.data.remote.bindery.binderyIllustrationsReadinessUrl
import paige.navic.domain.manager.BinderyReaderPrefetcher
import paige.navic.domain.manager.ReaderPublicationPrefetch
import paige.navic.domain.repositories.FakeBinderyApiClient
import paige.navic.domain.repositories.RecordingBinderyMetadataCache
import paige.navic.domain.repositories.configuredBinderyRepository
import paige.navic.ui.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ReaderIllustrationsTest {
	private val imageA = "plate-a".encodeToByteArray()
	private val imageB = "plate-b".encodeToByteArray()
	private val shaA = imageA.readerIllustrationSha256()
	private val shaB = imageB.readerIllustrationSha256()

	@Test
	fun routesAreCanonicalAndSameOrigin() {
		val base = "https://bindery.example.com/opds"
		assertEquals("https://bindery.example.com/api/v1/books/7/illustrations", binderyIllustrationsReadinessUrl(base, 7))
		assertEquals(
			"https://bindery.example.com/api/v1/books/7/illustrations/11/index",
			binderyIllustrationsIndexUrl(base, 7, 11, advertisedHref = null)
		)
		assertEquals(
			"https://bindery.example.com/api/v1/books/7/illustrations/11/index",
			binderyIllustrationsIndexUrl(base, 7, 11, "/api/v1/books/7/illustrations/11/index")
		)
		assertFailsWith<IllegalArgumentException> {
			binderyIllustrationsIndexUrl(base, 7, 11, "https://evil.example.com/api/v1/books/7/illustrations/11/index")
		}
		assertFailsWith<IllegalArgumentException> {
			binderyIllustrationsIndexUrl(base, 7, 11, "/api/v1/books/8/illustrations/11/index")
		}
		assertEquals(
			"https://bindery.example.com/api/v1/books/7/illustrations/images/$shaA",
			binderyIllustrationImageUrl(base, 7, shaA)
		)
		assertFailsWith<IllegalArgumentException> { binderyIllustrationImageUrl(base, 7, "../etc") }
	}

	@Test
	fun indexSkipsUnsupportedPositionsAndOffsets() {
		val index = decodeReaderIllustrationIndex(
			indexJson(
				placement("s1", shaA),
				placement("s2", shaB, position = "inline"),
				placement("s3", shaB, ebookStart = 120),
				placement("s4", "not-a-hash"),
				placement("s5", shaB, position = ReaderIllustrationPositionPlateBeforeBlock, ebookStart = 4812),
				placement("s6", shaB, position = ReaderIllustrationPositionPlateBeforeBlock, ebookStart = -1)
			),
			expectedBookId = 7,
			expectedEbookBookFileId = 11
		)

		assertEquals(listOf("s1", "s5"), index.placements.map { it.sceneId })
		assertEquals(4, index.rejectedPlacementCount)
		assertEquals("digest-1", index.digest)
		// A mid-chapter plate keeps its kind and offset; a chapter plate stays at byte 0.
		assertEquals(ReaderIllustrationPositionPlateBeforeChapter, index.placements[0].position)
		assertEquals(ReaderIllustrationPositionPlateBeforeBlock, index.placements[1].position)
		assertEquals(4812, index.placements[1].ebookStart)
	}

	@Test
	fun indexRejectsUnknownSchemaAndOtherFiles() {
		assertFailsWith<IllegalArgumentException> {
			decodeReaderIllustrationIndex(
				indexJson(placement("s1", shaA)).replace(ReaderIllustrationIndexSchema, "bindery.illustrations.index.v2"),
				expectedBookId = 7,
				expectedEbookBookFileId = 11
			)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeReaderIllustrationIndex(indexJson(placement("s1", shaA)), expectedBookId = 7, expectedEbookBookFileId = 12)
		}
		assertFailsWith<IllegalArgumentException> {
			decodeReaderIllustrationIndex(indexJson(placement("s1", shaA)), expectedBookId = 8, expectedEbookBookFileId = 11)
		}
	}

	@Test
	fun prefetchStoresVerifiedImagesAndResolverKeepsOnlyOnDiskPlates() = runTest {
		val storage = MemoryIllustrationStorage()
		val repository = configuredBinderyRepository(
			FakeBinderyApiClient(
				illustrationsReadinessJson = ReadinessJson,
				illustrationsIndexJson = indexJson(placement("s1", shaA), placement("s2", shaB)),
				// s2's bytes do not match its hash, so it must not be stored.
				illustrationImages = mapOf(shaA to imageA, shaB to "tampered".encodeToByteArray())
			),
			RecordingBinderyMetadataCache()
		) { 10_000L }

		assertEquals(1, prefetchReaderIllustrations(repository, storage, "7"))
		assertEquals(setOf(shaA), storage.images.keys)

		val resolver = ReaderIllustrationResolver(repository, storage)
		val set = resolver.resolve("7", ebookBookFileId = null, enabled = true)
		assertEquals("digest-1", set.digest)
		assertEquals(listOf("s1"), set.placements.map { it.sceneId })
		assertEquals("asset://$shaA", set.placements.single().imageUrl)

		assertSame(ReaderIllustrationSet.None, resolver.resolve("7", ebookBookFileId = 11, enabled = false))
		assertSame(ReaderIllustrationSet.None, resolver.resolve("7", ebookBookFileId = 99, enabled = true))
	}

	@Test
	fun turningPlatesOnFetchesThemOnlyWhenAsked() = runTest {
		val storage = MemoryIllustrationStorage()
		val repository = configuredBinderyRepository(
			FakeBinderyApiClient(
				illustrationsReadinessJson = ReadinessJson,
				illustrationsIndexJson = indexJson(placement("s1", shaA)),
				illustrationImages = mapOf(shaA to imageA)
			),
			RecordingBinderyMetadataCache()
		) { 10_000L }
		val resolver = ReaderIllustrationResolver(repository, storage)

		// At open nothing is fetched: an empty cache means no plates.
		assertSame(ReaderIllustrationSet.None, resolver.resolve("7", ebookBookFileId = 11, enabled = true))
		assertEquals(emptySet(), storage.images.keys)

		val set = resolver.resolve("7", ebookBookFileId = 11, enabled = true, fetchMissing = true)
		assertEquals(listOf("s1"), set.placements.map { it.sceneId })
		assertEquals(setOf(shaA), storage.images.keys)
	}

	@Test
	fun fixtureReplacesCachedIndex() = runTest {
		val storage = MemoryIllustrationStorage().apply {
			images[shaB] = imageB
			fixtures[7L] = indexJson(placement("fixture", shaB)).replace("\"digest-1\"", "\"fixture-digest\"")
		}
		val repository = configuredBinderyRepository(FakeBinderyApiClient(), RecordingBinderyMetadataCache()) { 10_000L }

		val set = ReaderIllustrationResolver(repository, storage).resolve("7", ebookBookFileId = null, enabled = true)

		assertEquals("fixture-digest", set.digest)
		assertEquals(listOf("fixture"), set.placements.map { it.sceneId })
	}

	@Test
	fun missingIllustrationsRouteNeverFailsReaderPrefetch() = runTest {
		val calls = mutableListOf<String>()
		val repository = configuredBinderyRepository(
			FakeBinderyApiClient(callLog = calls),
			RecordingBinderyMetadataCache()
		) { 10_000L }
		val prefetcher = BinderyReaderPrefetcher(
			repository = repository,
			publication = ReaderPublicationPrefetch { reader -> calls += "publication:${reader.bookId}" },
			canPrefetch = { true },
			illustrations = MemoryIllustrationStorage(),
			scope = this
		)

		prefetcher.enqueue(listOf(reader("7"), reader("8")), highIntent = true)
		advanceUntilIdle()

		assertEquals(
			listOf("publication:7", "illustrations-readiness", "publication:8", "illustrations-readiness"),
			calls
		)
	}

	private fun reader(bookId: String) = Screen.Reader(
		title = "Book",
		publicationUrl = "https://bindery.example.com/opds/books/$bookId/resources/ebook-1",
		bookId = bookId,
		resourceHref = "/opds/books/$bookId/resources/ebook-1",
		kind = ReaderPublicationKind.Ebook
	)

	private fun placement(
		sceneId: String,
		sha256: String,
		position: String = ReaderIllustrationPositionPlateBeforeChapter,
		ebookStart: Int = 0
	): String =
		"""{"sceneId":"$sceneId","spineIndex":3,"ebookHref":"OEBPS/ch1.xhtml","ebookStart":$ebookStart,""" +
			""""width":1024,"height":1536,"imageHref":"/api/v1/books/7/illustrations/images/$sha256",""" +
			""""sha256":"$sha256","position":"$position"}"""

	private fun indexJson(vararg placements: String): String =
		"""{"schema":"$ReaderIllustrationIndexSchema","bookId":7,"ebookBookFileId":11,""" +
			""""coordinateBasis":{"extractor":"bindery-epub-text","extractorVersion":"1"},""" +
			""""spines":[{"spineIndex":3,"href":"OEBPS/ch1.xhtml","sourceHash":"a","extractedTextHash":"b"}],""" +
			""""placements":[${placements.joinToString(",")}],"digest":"digest-1"}"""
}

private const val ReadinessJson =
	"""{"bookId":7,"illustrationsStatus":"ready","files":[{"bookFileId":11,"status":"ready",""" +
		""""indexHref":"/api/v1/books/7/illustrations/11/index","digest":"digest-1"}]}"""

private class MemoryIllustrationStorage : ReaderIllustrationStorage {
	val images = linkedMapOf<String, ByteArray>()
	val fixtures = mutableMapOf<Long, String>()

	override fun imageSizeBytes(sha256: String): Long = images[sha256]?.size?.toLong() ?: 0L

	override suspend fun writeImage(sha256: String, bytes: ByteArray) {
		images[sha256] = bytes
	}

	override fun readFixtureIndexJson(bookId: Long): String? = fixtures[bookId]

	override fun imageUrl(sha256: String): String = "asset://$sha256"
}
