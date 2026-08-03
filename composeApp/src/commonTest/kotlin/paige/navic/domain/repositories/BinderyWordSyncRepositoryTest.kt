package paige.navic.domain.repositories

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import paige.navic.data.remote.bindery.BinderyApiException
import paige.navic.data.remote.bindery.binderyWordSyncIndexRoute
import paige.navic.reader.WordSyncTestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BinderyWordSyncRepositoryTest {
	@Test
	fun validatesAndCachesExactRawIndexAndChapterByGeneration() = runBlocking {
		val identity = WordSyncTestFixtures.identity()
		val indexJson = WordSyncTestFixtures.indexJson()
		val chapterJson = WordSyncTestFixtures.chapterJson()
		val api = FakeBinderyApiClient(
			wordSyncIndexJson = indexJson,
			wordSyncChapterJson = chapterJson
		)
		val cache = RecordingBinderyMetadataCache()
		val repository = configuredBinderyRepository(api, cache) { 10_000L }

		val index = repository.getWordSyncIndex(identity, discovery()).getOrThrow()
		val chapter = repository.getWordSyncChapter(
			identity = identity,
			chapter = index.chapters.single()
		).getOrThrow()

		assertEquals(identity, index.identity)
		assertEquals("spine-002-chapter", chapter.chapterKey)
		assertEquals(listOf(identity), api.wordSyncIndexIdentities)
		assertEquals(listOf(identity), api.wordSyncChapterIdentities)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), api.wordSyncIndexHeaders)
		assertEquals(listOf(mapOf("X-Api-Key" to "secret")), api.wordSyncChapterHeaders)
		val indexRecord = cache.records.values.single { it.payloadType == BinderyMetadataPayloadType.WordSyncIndex }
		val chapterRecord = cache.records.values.single { it.payloadType == BinderyMetadataPayloadType.WordSyncChapter }
		assertEquals(indexJson, indexRecord.payloadJson)
		assertEquals(chapterJson, chapterRecord.payloadJson)
		assertTrue(indexRecord.path.contains("artifact:17|index"))
		assertTrue(chapterRecord.path.contains("artifact:17|chapter:spine-002-chapter"))

		repository.getWordSyncIndex(
			identity = identity,
			discovery = discovery(indexHref = "/api/v1/sync/artifacts/17/wordsync/index")
		).getOrThrow()
		repository.getWordSyncChapter(identity, index.chapters.single()).getOrThrow()
		assertEquals(1, api.wordSyncIndexIdentities.size)
		assertEquals(1, api.wordSyncChapterIdentities.size)
	}

	@Test
	fun malformedLiveWordSyncIsNeverCached() = runBlocking {
		val api = FakeBinderyApiClient(wordSyncIndexJson = "{}")
		val cache = RecordingBinderyMetadataCache()
		val repository = configuredBinderyRepository(api, cache) { 20_000L }

		val result = repository.getWordSyncIndex(
			identity = WordSyncTestFixtures.identity(),
			discovery = discovery()
		)

		assertTrue(result.isFailure)
		assertTrue(cache.records.isEmpty())
	}

	@Test
	fun validatedLivePayloadSurvivesCacheCommitFailure() = runBlocking {
		val api = FakeBinderyApiClient(
			wordSyncIndexJson = WordSyncTestFixtures.indexJson(17)
		)
		val failingCache = object : BinderyMetadataCache {
			override suspend fun get(cacheKey: String): BinderyMetadataCacheRecord? = null

			override suspend fun put(record: BinderyMetadataCacheRecord) {
				throw IllegalStateException("cache unavailable")
			}

			override suspend fun clearPayload(
				baseUrl: String,
				payloadType: String,
				path: String?,
				pathPrefix: Boolean
			) = Unit

			override suspend fun clearBaseUrl(baseUrl: String) = Unit
		}

		val result = configuredBinderyRepository(api, failingCache) { 20_001L }
			.getWordSyncIndex(
				identity = WordSyncTestFixtures.identity(artifactId = 17),
				discovery = discovery(artifactId = 17)
			)

		assertTrue(result.isSuccess)
		assertEquals(1, api.wordSyncIndexIdentities.size)
	}

	@Test
	fun newerArtifactGenerationRemovesOlderWordSyncPayloads() = runBlocking {
		val cache = RecordingBinderyMetadataCache()
		val firstIdentity = WordSyncTestFixtures.identity(artifactId = 17)
		val firstRepository = configuredBinderyRepository(
			apiClient = FakeBinderyApiClient(
				wordSyncIndexJson = WordSyncTestFixtures.indexJson(17),
				wordSyncChapterJson = WordSyncTestFixtures.chapterJson(17)
			),
			metadataCache = cache,
			currentTimeMillis = { 30_000L }
		)
		val firstIndex = firstRepository.getWordSyncIndex(
			firstIdentity,
			discovery(artifactId = 17)
		).getOrThrow()
		firstRepository.getWordSyncChapter(
			firstIdentity,
			firstIndex.chapters.single()
		).getOrThrow()
		assertTrue(cache.records.values.any { it.path.contains("artifact:17|index") })
		assertTrue(cache.records.values.any { it.path.contains("artifact:17|chapter:") })

		val secondIdentity = WordSyncTestFixtures.identity(artifactId = 18)
		val secondRepository = configuredBinderyRepository(
			apiClient = FakeBinderyApiClient(wordSyncIndexJson = WordSyncTestFixtures.indexJson(18)),
			metadataCache = cache,
			currentTimeMillis = { 31_000L }
		)
		secondRepository.getWordSyncIndex(secondIdentity, discovery(artifactId = 18)).getOrThrow()

		assertFalse(cache.records.values.any { it.path.contains("artifact:17|") })
		assertTrue(cache.records.values.any { it.path.contains("artifact:18|index") })
	}

	@Test
	fun partialNewGenerationCacheCannotBypassMarkerCommit() = runBlocking {
		val cache = RecordingBinderyMetadataCache()
		val oldIdentity = WordSyncTestFixtures.identity(artifactId = 17)
		configuredBinderyRepository(
			apiClient = FakeBinderyApiClient(
				wordSyncIndexJson = WordSyncTestFixtures.indexJson(17)
			),
			metadataCache = cache,
			currentTimeMillis = { 50_000L }
		).getWordSyncIndex(oldIdentity, discovery(artifactId = 17)).getOrThrow()

		val currentIdentity = WordSyncTestFixtures.identity(artifactId = 18)
		val currentRoute = binderyWordSyncIndexRoute(
			baseUrl = "https://bindery.example.com/opds",
			identity = currentIdentity,
			advertisedHref = "/opds/books/7/sync/18/wordsync/index"
		)
		val partialCacheKey = binderyMetadataCacheKey(
			baseUrl = "https://bindery.example.com/opds",
			payloadType = BinderyMetadataPayloadType.WordSyncIndex,
			path = currentRoute.cachePath,
			apiKeyFingerprint = binderyApiKeyFingerprint("secret")
		)
		cache.put(
			BinderyMetadataCacheRecord(
				cacheKey = partialCacheKey,
				baseUrl = "https://bindery.example.com/opds",
				payloadType = BinderyMetadataPayloadType.WordSyncIndex,
				path = currentRoute.cachePath,
				payloadJson = WordSyncTestFixtures.indexJson(18),
				updatedAtMillis = 50_000L
			)
		)
		val api = FakeBinderyApiClient(
			wordSyncIndexJson = WordSyncTestFixtures.indexJson(18)
		)
		configuredBinderyRepository(api, cache) { 50_001L }
			.getWordSyncIndex(currentIdentity, discovery(artifactId = 18))
			.getOrThrow()

		assertEquals(1, api.wordSyncIndexIdentities.size)
		assertFalse(cache.records.values.any { it.path.contains("artifact:17|") })
		assertTrue(cache.records.values.any {
			it.payloadType == BinderyMetadataPayloadType.WordSyncGeneration &&
				it.payloadJson == "18"
		})
	}

	@Test
	fun markerlessInitialGenerationCacheIsRefetchedBeforeUse() = runBlocking {
		val cache = RecordingBinderyMetadataCache()
		val identity = WordSyncTestFixtures.identity(artifactId = 17)
		val route = binderyWordSyncIndexRoute(
			baseUrl = "https://bindery.example.com/opds",
			identity = identity,
			advertisedHref = "/opds/books/7/sync/17/wordsync/index"
		)
		cache.put(
			BinderyMetadataCacheRecord(
				cacheKey = binderyMetadataCacheKey(
					baseUrl = "https://bindery.example.com/opds",
					payloadType = BinderyMetadataPayloadType.WordSyncIndex,
					path = route.cachePath,
					apiKeyFingerprint = binderyApiKeyFingerprint("secret")
				),
				baseUrl = "https://bindery.example.com/opds",
				payloadType = BinderyMetadataPayloadType.WordSyncIndex,
				path = route.cachePath,
				payloadJson = WordSyncTestFixtures.indexJson(17),
				updatedAtMillis = 50_000L
			)
		)
		val api = FakeBinderyApiClient(
			wordSyncIndexJson = WordSyncTestFixtures.indexJson(17)
		)

		configuredBinderyRepository(api, cache) { 50_001L }
			.getWordSyncIndex(identity, discovery(artifactId = 17))
			.getOrThrow()

		assertEquals(1, api.wordSyncIndexIdentities.size)
		assertTrue(cache.records.values.any {
			it.payloadType == BinderyMetadataPayloadType.WordSyncGeneration &&
				it.payloadJson == "17"
		})
	}

	@Test
	fun currentRefreshAndStaleFetchCannotEvictNewerGenerationCaches() = runBlocking {
		val cache = RecordingBinderyMetadataCache()
		val currentIdentity = WordSyncTestFixtures.identity(artifactId = 18)
		val currentApi = FakeBinderyApiClient(
			wordSyncIndexJson = WordSyncTestFixtures.indexJson(18),
			wordSyncChapterJson = WordSyncTestFixtures.chapterJson(18)
		)
		val currentRepository = configuredBinderyRepository(currentApi, cache) { 40_000L }
		val currentIndex = currentRepository.getWordSyncIndex(
			currentIdentity,
			discovery(artifactId = 18)
		).getOrThrow()
		currentRepository.getWordSyncChapter(
			currentIdentity,
			currentIndex.chapters.single()
		).getOrThrow()

		currentRepository.getWordSyncIndex(
			identity = currentIdentity,
			discovery = discovery(artifactId = 18),
			forceRefresh = true
		).getOrThrow()
		assertTrue(cache.records.values.any {
			it.path.contains("artifact:18|chapter:spine-002-chapter")
		})

		val staleIdentity = WordSyncTestFixtures.identity(artifactId = 17)
		configuredBinderyRepository(
			apiClient = FakeBinderyApiClient(
				wordSyncIndexJson = WordSyncTestFixtures.indexJson(17)
			),
			metadataCache = cache,
			currentTimeMillis = { 41_000L }
		).getWordSyncIndex(
			identity = staleIdentity,
			discovery = discovery(artifactId = 17),
			forceRefresh = true
		).getOrThrow()

		assertFalse(cache.records.values.any { it.path.contains("artifact:17|") })
		assertTrue(cache.records.values.any { it.path.contains("artifact:18|index") })
		assertTrue(cache.records.values.any { it.path.contains("artifact:18|chapter:") })
	}

	@Test
	fun failedNewGenerationCannotFallBackToOlderArtifactCache() = runBlocking {
		val cache = RecordingBinderyMetadataCache()
		val firstIdentity = WordSyncTestFixtures.identity(artifactId = 17)
		configuredBinderyRepository(
			apiClient = FakeBinderyApiClient(wordSyncIndexJson = WordSyncTestFixtures.indexJson(17)),
			metadataCache = cache,
			currentTimeMillis = { 1L }
		).getWordSyncIndex(firstIdentity, discovery(artifactId = 17)).getOrThrow()

		val failingApi = FakeBinderyApiClient(
			wordSyncFailure = BinderyApiException(HttpStatusCode.ServiceUnavailable, "unavailable")
		)
		val result = configuredBinderyRepository(
			apiClient = failingApi,
			metadataCache = cache,
			currentTimeMillis = { BINDERY_METADATA_CACHE_FRESH_MILLIS + 10L }
		).getWordSyncIndex(
			identity = WordSyncTestFixtures.identity(artifactId = 18),
			discovery = discovery(artifactId = 18)
		)

		assertTrue(result.isFailure)
		assertEquals(1, failingApi.wordSyncIndexIdentities.size)
	}

	@Test
	fun unusableDiscoveryFailsBeforeFetch() = runBlocking {
		val api = FakeBinderyApiClient(wordSyncIndexJson = WordSyncTestFixtures.indexJson())
		val repository = configuredBinderyRepository(api, RecordingBinderyMetadataCache()) { 1L }

		val result = repository.getWordSyncIndex(
			identity = WordSyncTestFixtures.identity(),
			discovery = discovery().copy(status = "failed")
		)

		assertTrue(result.isFailure)
		assertTrue(api.wordSyncIndexIdentities.isEmpty())
	}

	private fun discovery(
		artifactId: Long = 17,
		indexHref: String = "/opds/books/7/sync/$artifactId/wordsync/index"
	): BinderyWordSyncDiscovery = BinderyWordSyncDiscovery(
		status = "ready",
		schema = "bindery.whispersync.wordsync.index.v1",
		indexHref = indexHref,
		opdsIndexHref = if (indexHref.startsWith("/opds/")) indexHref else null,
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
}
