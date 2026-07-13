package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import paige.navic.domain.manager.PreferenceManager
import paige.navic.domain.models.OptionalIntegrationFailureKind
import paige.navic.domain.models.OptionalIntegrationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BinderyRepositoryOptionalStateTest {
	private val path = "/opds/books"

	@Test
	fun disabledAndMissingConfigurationRemainDistinctFailures() = runBlocking {
		val disabled = repository(enabled = false).getCatalogOptional(path)
		val misconfigured = repository(apiKey = "").getCatalogOptional(path)

		assertEquals(
			OptionalIntegrationFailureKind.Disabled,
			assertIs<OptionalIntegrationResult.Unavailable>(disabled).failure.kind
		)
		assertEquals(
			OptionalIntegrationFailureKind.Misconfigured,
			assertIs<OptionalIntegrationResult.Unavailable>(misconfigured).failure.kind
		)
	}

	@Test
	fun liveCatalogDistinguishesEmptyAndAvailable() = runBlocking {
		val empty = repository().getCatalogOptional(path)
		val available = repository(
			catalog = BinderyCatalog(
				title = "Books",
				publications = listOf(BinderyPublication(id = "book-1", title = "Book"))
			)
		).getCatalogOptional(path)

		assertIs<OptionalIntegrationResult.Empty>(empty)
		assertEquals(
			"Book",
			assertIs<OptionalIntegrationResult.Available<BinderyCatalog>>(available)
				.data.publications.single().title
		)
	}

	@Test
	fun failedLiveCatalogReturnsStaleCachedDataWithFailure() = runBlocking {
		var nowMillis = 1_000L
		val cache = RecordingBinderyMetadataCache()
		configuredBinderyRepository(
			apiClient = FakeBinderyApiClient(
				catalog = BinderyCatalog(
					title = "Books",
					publications = listOf(BinderyPublication(id = "cached", title = "Cached Book"))
				)
			),
			metadataCache = cache,
			currentTimeMillis = { nowMillis }
		).getCatalogOptional(path)

		nowMillis += BINDERY_METADATA_CACHE_FRESH_MILLIS + 1
		val stale = configuredBinderyRepository(
			apiClient = FakeBinderyApiClient(catalogFailure = IllegalStateException("offline")),
			metadataCache = cache,
			currentTimeMillis = { nowMillis }
		).getCatalogOptional(path)

		val result = assertIs<OptionalIntegrationResult.Stale<BinderyCatalog>>(stale)
		assertEquals("Cached Book", result.data.publications.single().title)
		assertEquals(OptionalIntegrationFailureKind.Unavailable, result.failure.kind)
	}

	@Test
	fun liveCatalogFailuresRemainTyped() = runBlocking {
		val unauthorized = repository(
			failure = BinderyApiException(HttpStatusCode.Forbidden, "Forbidden")
		).getCatalogOptional(path)
		val malformed = repository(
			failure = SerializationException("bad catalog payload")
		).getCatalogOptional(path)
		val unavailable = repository(
			failure = IllegalStateException("offline")
		).getCatalogOptional(path)

		assertEquals(
			OptionalIntegrationFailureKind.Unauthorized,
			assertIs<OptionalIntegrationResult.Unavailable>(unauthorized).failure.kind
		)
		assertEquals(
			OptionalIntegrationFailureKind.Malformed,
			assertIs<OptionalIntegrationResult.Unavailable>(malformed).failure.kind
		)
		assertEquals(
			OptionalIntegrationFailureKind.Unavailable,
			assertIs<OptionalIntegrationResult.Unavailable>(unavailable).failure.kind
		)
	}

	private fun repository(
		enabled: Boolean = true,
		baseUrl: String = "https://bindery.example.com/opds",
		apiKey: String = "secret",
		catalog: BinderyCatalog = BinderyCatalog(title = "Books"),
		failure: Throwable? = null
	): BinderyRepository {
		val preferences = PreferenceManager(MapSettings()).apply {
			binderyEnabled = enabled
			binderyOpdsBaseUrl = baseUrl
			binderyApiKey = apiKey
		}
		return BinderyRepository(
			preferenceManager = preferences,
			apiClient = FakeBinderyApiClient(catalog = catalog, catalogFailure = failure)
		)
	}
}
