package paige.navic.domain.manager

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import paige.navic.domain.repositories.BinderyWordSyncDiscovery
import paige.navic.domain.repositories.BinderyWordSyncReference
import paige.navic.domain.repositories.FakeBinderyApiClient
import paige.navic.domain.repositories.RecordingBinderyMetadataCache
import paige.navic.domain.repositories.configuredBinderyRepository
import paige.navic.reader.ReaderPublicationKind
import paige.navic.reader.WordSyncTestFixtures
import paige.navic.ui.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinderyReaderPrefetcherTest {
	@Test
	fun prefetchFetchesPublicationThenSidecarThenWordSyncIndexThenChapters() = runTest {
		val calls = mutableListOf<String>()
		val prefetcher = prefetcher(calls)

		prefetcher.enqueue(listOf(whispersyncReader()), highIntent = true)
		advanceUntilIdle()

		assertEquals(
			listOf(
				"publication:/opds/books/7/resources/ebook-1",
				"sidecar",
				"audiobook-manifest",
				"wordsync-index",
				"wordsync-chapter"
			),
			calls
		)
	}

	@Test
	fun repeatedEnqueueOfAPrefetchedReaderMakesNoNewCalls() = runTest {
		val calls = mutableListOf<String>()
		val prefetcher = prefetcher(calls)

		prefetcher.enqueue(listOf(whispersyncReader()), highIntent = true)
		advanceUntilIdle()
		val firstPass = calls.toList()
		prefetcher.enqueue(listOf(whispersyncReader(), whispersyncReader()), highIntent = true)
		advanceUntilIdle()

		assertEquals(firstPass, calls)
	}

	@Test
	fun disallowedNetworkMakesNoCalls() = runTest {
		val calls = mutableListOf<String>()
		val prefetcher = prefetcher(calls, canPrefetch = { false })

		prefetcher.enqueue(listOf(whispersyncReader()), highIntent = true)
		prefetcher.enqueueBooks(listOf("7"), "https://bindery.example.com/opds", null, null)
		advanceUntilIdle()

		assertEquals(emptyList<String>(), calls)
	}

	@Test
	fun lowIntentPrefetchRequiresAnUnmeteredNetwork() = runTest {
		val calls = mutableListOf<String>()
		val prefetcher = prefetcher(calls, canPrefetch = { requireUnmetered -> !requireUnmetered })

		prefetcher.enqueue(listOf(plainReader("/opds/books/8/resources/ebook-1")), highIntent = false)
		prefetcher.enqueueBooks(listOf("7"), "https://bindery.example.com/opds", null, null)
		advanceUntilIdle()

		assertEquals(emptyList<String>(), calls)
	}

	@Test
	fun failureStopsTheBatchWithoutThrowingAndAllowsALaterRetry() = runTest {
		val calls = mutableListOf<String>()
		var failPublication = true
		val prefetcher = prefetcher(
			calls,
			publication = ReaderPublicationPrefetch { reader ->
				calls += "publication:${reader.resourceHref}"
				if (failPublication) error("offline")
			}
		)

		prefetcher.enqueue(
			listOf(whispersyncReader(), plainReader("/opds/books/8/resources/ebook-1")),
			highIntent = true
		)
		advanceUntilIdle()

		assertEquals(listOf("publication:/opds/books/7/resources/ebook-1"), calls)

		failPublication = false
		calls.clear()
		prefetcher.enqueue(listOf(plainReader("/opds/books/8/resources/ebook-1")), highIntent = true)
		advanceUntilIdle()

		assertEquals(listOf("publication:/opds/books/8/resources/ebook-1"), calls)
	}

	@Test
	fun highIntentReadersRunBeforeQueuedLowIntentReaders() = runTest {
		val calls = mutableListOf<String>()
		val prefetcher = prefetcher(calls)

		prefetcher.enqueue(listOf(plainReader("/opds/books/8/resources/ebook-1")), highIntent = false)
		prefetcher.enqueue(listOf(plainReader("/opds/books/9/resources/ebook-1")), highIntent = true)
		advanceUntilIdle()

		assertEquals(
			listOf(
				"publication:/opds/books/9/resources/ebook-1",
				"publication:/opds/books/8/resources/ebook-1"
			),
			calls.filter { it.startsWith("publication:") }
		)
		assertTrue(calls.none { it == "sidecar" })
	}

	private fun TestScope.prefetcher(
		calls: MutableList<String>,
		canPrefetch: (Boolean) -> Boolean = { true },
		publication: ReaderPublicationPrefetch = ReaderPublicationPrefetch { reader ->
			calls += "publication:${reader.resourceHref}"
		}
	): BinderyReaderPrefetcher {
		val api = FakeBinderyApiClient(
			whispersyncSidecarJson = SidecarJson,
			wordSyncIndexJson = WordSyncTestFixtures.indexJson(),
			wordSyncChapterJson = WordSyncTestFixtures.chapterJson(),
			callLog = calls
		)
		val repository = configuredBinderyRepository(api, RecordingBinderyMetadataCache()) { 10_000L }
		return BinderyReaderPrefetcher(
			repository = repository,
			publication = publication,
			canPrefetch = canPrefetch,
			// advanceUntilIdle does not wait for backgroundScope work, so the worker runs as a child of the test.
			scope = this
		)
	}

	private fun plainReader(resourceHref: String): Screen.Reader =
		Screen.Reader(
			title = "Book",
			publicationUrl = "https://bindery.example.com$resourceHref",
			bookId = resourceHref.removePrefix("/opds/books/").substringBefore('/'),
			resourceHref = resourceHref,
			kind = ReaderPublicationKind.Ebook
		)

	private fun whispersyncReader(): Screen.Reader =
		plainReader("/opds/books/7/resources/ebook-1").copy(
			whispersyncSidecarUrl = "/api/v1/sync/artifacts/17",
			whispersyncArtifactId = "17",
			whispersyncAudiobookId = "5",
			whispersyncAudiobookBookFileId = "13",
			whispersyncWordSync = BinderyWordSyncReference(
				identity = WordSyncTestFixtures.identity(),
				discovery = BinderyWordSyncDiscovery(
					status = "ready",
					schema = "bindery.whispersync.wordsync.index.v1",
					indexHref = "/opds/books/7/sync/17/wordsync/index",
					opdsIndexHref = "/opds/books/7/sync/17/wordsync/index",
					format = "chapter-sharded-json",
					compression = "http",
					timeScale = 1000,
					shardCount = 1,
					audioWordCount = 3,
					matchedAudioWordCount = 2,
					reviewAudioWordCount = 1,
					unmatchedAudioWordCount = 0,
					unmatchedEbookWordCount = 0,
					coverage = 2.0 / 3.0
				)
			)
		)
}

private val SidecarJson = """
{
  "schema": "bindery.whispersync.sidecar.v1",
  "bookId": 7,
  "ebookBookFileId": 11,
  "audiobookBookFileId": 13,
  "cues": [
    {
      "id": 1,
      "audioHref": "Part 01.mp3",
      "audioStart": 0,
      "audioEnd": 18.04,
      "ebookHref": "OEBPS/Text/chapter.xhtml",
      "ebookStart": 0,
      "ebookEnd": 78,
      "text": "I am not a good person."
    }
  ]
}
""".trimIndent()
