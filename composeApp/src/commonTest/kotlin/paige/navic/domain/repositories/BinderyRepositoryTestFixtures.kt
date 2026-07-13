package paige.navic.domain.repositories

import com.russhwolf.settings.MapSettings
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.domain.manager.PreferenceManager

internal class FakeBinderyApiClient(
	private val rootCatalog: BinderyCatalog = BinderyCatalog(title = "Bindery"),
	private val catalog: BinderyCatalog = rootCatalog,
	private val bookFindings: BinderyCatalog = BinderyCatalog(title = "Findings"),
	private val audiobookVersions: List<BinderyAudiobookVersion> = emptyList(),
	private val audiobookVersion: BinderyAudiobookVersion = BinderyAudiobookVersion(id = 1),
	private val audiobookManifest: BinderyManifest = BinderyManifest(id = "urn:bindery:audiobook:1", title = "Audiobook"),
	private val bookSync: BinderyBookSync = BinderyBookSync(),
	private val whispersyncSidecarJson: String = "{}",
	private val resourceBytes: ByteArray = ByteArray(0),
	private val progress: BinderyReadingProgress = BinderyReadingProgress(
		bookId = "book",
		kind = BinderyReadingProgressKind.Ebook
	),
	private val externalTextByUrl: Map<String, String> = emptyMap(),
	private val rootFailure: Throwable? = null,
	private val catalogFailure: Throwable? = null,
	private val bookFindingsFailure: Throwable? = null
) : BinderyApiClient {
	var rootCalls = 0
	val rootBaseUrls = mutableListOf<String>()
	val rootHeaders = mutableListOf<Map<String, String>>()
	val actionBaseUrls = mutableListOf<String>()
	val actionHeaders = mutableListOf<Map<String, String>>()
	val actionPaths = mutableListOf<String>()
	val catalogBaseUrls = mutableListOf<String>()
	val catalogHeaders = mutableListOf<Map<String, String>>()
	val catalogPaths = mutableListOf<String>()
	val manifestBaseUrls = mutableListOf<String>()
	val manifestHeaders = mutableListOf<Map<String, String>>()
	val manifestBookIds = mutableListOf<String>()
	val resourceCatalogBaseUrls = mutableListOf<String>()
	val resourceCatalogHeaders = mutableListOf<Map<String, String>>()
	val resourceCatalogBookIds = mutableListOf<String>()
	val bookFindingBaseUrls = mutableListOf<String>()
	val bookFindingHeaders = mutableListOf<Map<String, String>>()
	val bookFindingIds = mutableListOf<String>()
	val audiobookVersionBaseUrls = mutableListOf<String>()
	val audiobookVersionHeaders = mutableListOf<Map<String, String>>()
	val audiobookVersionBookIds = mutableListOf<String>()
	val audiobookVersionLimits = mutableListOf<Int>()
	val audiobookDetailBaseUrls = mutableListOf<String>()
	val audiobookDetailHeaders = mutableListOf<Map<String, String>>()
	val audiobookDetailIds = mutableListOf<String>()
	val audiobookManifestBaseUrls = mutableListOf<String>()
	val audiobookManifestHeaders = mutableListOf<Map<String, String>>()
	val audiobookManifestIds = mutableListOf<String>()
	val audiobookManifestPaths = mutableListOf<String>()
	val bookSyncBaseUrls = mutableListOf<String>()
	val bookSyncHeaders = mutableListOf<Map<String, String>>()
	val bookSyncIds = mutableListOf<String>()
	val whispersyncSidecarBaseUrls = mutableListOf<String>()
	val whispersyncSidecarHeaders = mutableListOf<Map<String, String>>()
	val whispersyncSidecarPaths = mutableListOf<String>()
	val resourceBaseUrls = mutableListOf<String>()
	val resourceHeaders = mutableListOf<Map<String, String>>()
	val resourcePaths = mutableListOf<String>()
	val progressFetchBaseUrls = mutableListOf<String>()
	val progressFetchHeaders = mutableListOf<Map<String, String>>()
	val progressFetchBookIds = mutableListOf<String>()
	val progressFetchAliases = mutableListOf<String?>()
	val progressPutBaseUrls = mutableListOf<String>()
	val progressPutHeaders = mutableListOf<Map<String, String>>()
	val progressPutPayloads = mutableListOf<BinderyReadingProgress>()
	val externalTextUrls = mutableListOf<String>()
	val externalTextPurposes = mutableListOf<ExternalTextPurpose>()

	override suspend fun fetchRootCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): BinderyCatalog {
		rootCalls += 1
		rootBaseUrls += baseUrl
		rootHeaders += requestHeaders
		rootFailure?.let { throw it }
		return rootCatalog
	}

	override suspend fun fetchCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): BinderyCatalog {
		catalogBaseUrls += baseUrl
		catalogHeaders += requestHeaders
		catalogPaths += path
		catalogFailure?.let { throw it }
		return catalog
	}

	override suspend fun fetchManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyManifest {
		manifestBaseUrls += baseUrl
		manifestHeaders += requestHeaders
		manifestBookIds += bookId
		return BinderyManifest(
			id = "urn:bindery:book:$bookId",
			title = "Book $bookId"
		)
	}

	override suspend fun fetchBookResources(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyResourceCatalog {
		resourceCatalogBaseUrls += baseUrl
		resourceCatalogHeaders += requestHeaders
		resourceCatalogBookIds += bookId
		return BinderyResourceCatalog(title = "Book $bookId Resources")
	}

	override suspend fun fetchAudiobookVersions(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		limit: Int
	): List<BinderyAudiobookVersion> {
		audiobookVersionBaseUrls += baseUrl
		audiobookVersionHeaders += requestHeaders
		audiobookVersionBookIds += bookId
		audiobookVersionLimits += limit
		return audiobookVersions
	}

	override suspend fun fetchAudiobookVersion(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		audiobookId: String
	): BinderyAudiobookVersion {
		audiobookDetailBaseUrls += baseUrl
		audiobookDetailHeaders += requestHeaders
		audiobookDetailIds += audiobookId
		return audiobookVersion
	}

	override suspend fun fetchAudiobookManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		audiobookId: String
	): BinderyManifest {
		audiobookManifestBaseUrls += baseUrl
		audiobookManifestHeaders += requestHeaders
		audiobookManifestIds += audiobookId
		return audiobookManifest
	}

	override suspend fun fetchAudiobookManifestPath(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): BinderyManifest {
		audiobookManifestBaseUrls += baseUrl
		audiobookManifestHeaders += requestHeaders
		audiobookManifestPaths += path
		return audiobookManifest
	}

	override suspend fun fetchBookSync(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyBookSync {
		bookSyncBaseUrls += baseUrl
		bookSyncHeaders += requestHeaders
		bookSyncIds += bookId
		return bookSync
	}

	override suspend fun fetchWhispersyncSidecarJson(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): String {
		whispersyncSidecarBaseUrls += baseUrl
		whispersyncSidecarHeaders += requestHeaders
		whispersyncSidecarPaths += path
		return whispersyncSidecarJson
	}

	override suspend fun fetchResourceBytes(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): ByteArray {
		resourceBaseUrls += baseUrl
		resourceHeaders += requestHeaders
		resourcePaths += path
		return resourceBytes
	}

	override suspend fun fetchReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		alias: String?
	): BinderyReadingProgress {
		progressFetchBaseUrls += baseUrl
		progressFetchHeaders += requestHeaders
		progressFetchBookIds += bookId
		progressFetchAliases += alias
		return progress
	}

	override suspend fun putReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		progress: BinderyReadingProgress
	) {
		progressPutBaseUrls += baseUrl
		progressPutHeaders += requestHeaders
		progressPutPayloads += progress
	}

	override suspend fun fetchBookFindings(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyCatalog {
		bookFindingBaseUrls += baseUrl
		bookFindingHeaders += requestHeaders
		bookFindingIds += bookId
		bookFindingsFailure?.let { throw it }
		return bookFindings
	}

	override suspend fun fetchExternalText(url: String, purpose: ExternalTextPurpose): String {
		externalTextUrls += url
		externalTextPurposes += purpose
		return externalTextByUrl[url] ?: throw BinderyApiException(
			HttpStatusCode.NotFound,
			"Provider source page returned HTTP 404"
		)
	}

	override suspend fun performAction(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	) {
		actionBaseUrls += baseUrl
		actionHeaders += requestHeaders
		actionPaths += path
	}
}

internal class RecordingBinderyMetadataCache : BinderyMetadataCache {
	val records = linkedMapOf<String, BinderyMetadataCacheRecord>()
	val clearedBaseUrls = mutableListOf<String>()
	val clearedPayloads = mutableListOf<Triple<String, String, String?>>()

	override suspend fun get(cacheKey: String): BinderyMetadataCacheRecord? =
		records[cacheKey]

	override suspend fun put(record: BinderyMetadataCacheRecord) {
		records[record.cacheKey] = record
	}

	override suspend fun clearPayload(
		baseUrl: String,
		payloadType: String,
		path: String?,
		pathPrefix: Boolean
	) {
		clearedPayloads += Triple(baseUrl, payloadType, path)
		records.entries.removeAll { (_, record) ->
			record.baseUrl == baseUrl &&
				record.payloadType == payloadType &&
				(path == null || if (pathPrefix) record.path.startsWith(path) else record.path == path)
		}
	}

	override suspend fun clearBaseUrl(baseUrl: String) {
		clearedBaseUrls += baseUrl
		records.entries.removeAll { (_, record) -> record.baseUrl == baseUrl }
	}
}

internal fun configuredBinderyRepository(
	apiClient: BinderyApiClient,
	metadataCache: BinderyMetadataCache,
	currentTimeMillis: () -> Long
): BinderyRepository {
	val preferences = PreferenceManager(MapSettings()).apply {
		binderyEnabled = true
		binderyOpdsBaseUrl = " https://bindery.example.com/opds/ "
		binderyApiKey = " secret "
	}
	return BinderyRepository(
		preferenceManager = preferences,
		apiClient = apiClient,
		metadataCache = metadataCache,
		currentTimeMillis = currentTimeMillis
	)
}

internal fun binderyRootCatalog(): BinderyCatalog =
	BinderyCatalog(
		title = "Bindery",
		links = listOf(
			BinderyLink(
				href = "/opds/search{?q}",
				rel = listOf("search"),
				type = "application/opds+json"
			)
		),
		navigation = listOf(
			BinderyLink(href = "/opds/books", title = "Books"),
			BinderyLink(href = "/opds/formats/audiobook", title = "Audiobooks"),
			BinderyLink(href = "/opds/authors", title = "Authors"),
			BinderyLink(href = "/opds/series", title = "Series"),
			BinderyLink(href = "/opds/collections", title = "Collections"),
			BinderyLink(href = "/opds/findings", title = "Findings")
		)
	)
