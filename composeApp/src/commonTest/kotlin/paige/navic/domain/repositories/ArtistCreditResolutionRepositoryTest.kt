package paige.navic.domain.repositories

import kotlinx.coroutines.runBlocking
import paige.navic.domain.models.ArtistCreditContext
import paige.navic.domain.models.ArtistCreditResolutionReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtistCreditResolutionRepositoryTest {
	@Test
	fun positiveResolutionIsPersistedInMetadataCache() = runBlocking {
		val cache = InMemoryArtistCreditMetadataCache()
		val lookup = FakeArtistCreditLookup(
			exactArtists = mapOf(
				"Anyma" to "Anyma",
				"LISA" to "LISA"
			)
		)
		val repository = ArtistCreditResolutionRepository(
			metadataCache = cache,
			lookup = lookup,
			nowMillis = { 1000L }
		)
		val context = ArtistCreditContext(originalCredit = "Anyma & LISA")

		val resolved = repository.resolveAndCache(context)
		val cached = ArtistCreditResolutionRepository(
			metadataCache = cache,
			lookup = FakeArtistCreditLookup(),
			nowMillis = { 2000L }
		).cachedResolution(context)

		assertEquals(listOf("Anyma", "LISA"), resolved?.displayNames)
		assertEquals(ArtistCreditResolutionReason.ValidatedSplit, resolved?.reason)
		assertEquals(listOf("Anyma", "LISA"), cached?.displayNames)
		assertEquals(3, lookup.exactArtistCalls)
	}

	@Test
	fun unresolvedCreditIsNotPersistedAsNegativeCache() = runBlocking {
		val cache = InMemoryArtistCreditMetadataCache()
		val repository = ArtistCreditResolutionRepository(
			metadataCache = cache,
			lookup = FakeArtistCreditLookup(exactArtists = mapOf("Chase" to "Chase")),
			nowMillis = { 1000L }
		)
		val context = ArtistCreditContext(originalCredit = "Chase & Status")

		assertNull(repository.resolveAndCache(context))
		assertNull(repository.cachedResolution(context))
		assertEquals(0, cache.records.size)
	}

	private class FakeArtistCreditLookup(
		private val exactArtists: Map<String, String> = emptyMap(),
		private val albumArtists: Map<String, List<String>> = emptyMap()
	) : ArtistCreditLookup {
		var exactArtistCalls = 0

		override suspend fun exactArtistName(name: String): String? {
			exactArtistCalls++
			return exactArtists[name]
		}

		override suspend fun albumArtistNames(albumTitle: String?): List<String> =
			albumTitle?.let(albumArtists::get).orEmpty()
	}

	private class InMemoryArtistCreditMetadataCache : AurralMetadataCache {
		val records = mutableMapOf<String, AurralMetadataCacheRecord>()

		override suspend fun get(cacheKey: String): AurralMetadataCacheRecord? =
			records[cacheKey]

		override suspend fun put(record: AurralMetadataCacheRecord) {
			records[record.cacheKey] = record
		}

		override suspend fun clearBaseUrl(baseUrl: String) {
			records.entries.removeAll { it.value.baseUrl == baseUrl }
		}
	}
}
