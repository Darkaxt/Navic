package paige.navic.domain.repositories

import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLQueryComponent
import paige.navic.data.remote.NetworkClientFactory
import paige.navic.domain.models.OptionalIntegrationHttpFailure


interface BinderyApiClient {
	suspend fun fetchRootCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): BinderyCatalog

	suspend fun fetchCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): BinderyCatalog

	suspend fun fetchManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyManifest

	suspend fun fetchBookResources(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyResourceCatalog

	suspend fun fetchAudiobookVersions(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		limit: Int = 100
	): List<BinderyAudiobookVersion>

	suspend fun fetchAudiobookVersion(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		audiobookId: String
	): BinderyAudiobookVersion

	suspend fun fetchAudiobookManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		audiobookId: String
	): BinderyManifest

	suspend fun fetchAudiobookManifestPath(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): BinderyManifest

	suspend fun fetchBookSync(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyBookSync

	suspend fun fetchWhispersyncSidecarJson(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): String

	suspend fun fetchResourceBytes(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): ByteArray

	suspend fun fetchReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		alias: String? = null
	): BinderyReadingProgress

	suspend fun putReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		progress: BinderyReadingProgress
	)

	suspend fun fetchBookFindings(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyCatalog

	suspend fun fetchExternalText(url: String): String

	suspend fun performAction(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	)
}

internal class KtorBinderyApiClient(
	networkClientFactory: NetworkClientFactory = NetworkClientFactory()
) : BinderyApiClient {
	private val client = networkClientFactory.create(json = BinderyJson) {
		install(HttpTimeout) {
			requestTimeoutMillis = 45_000
			connectTimeoutMillis = 10_000
			socketTimeoutMillis = 45_000
		}
	}

	override suspend fun fetchRootCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>
	): BinderyCatalog =
		fetchCatalog(baseUrl, requestHeaders, "/")

	override suspend fun fetchCatalog(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): BinderyCatalog {
		val response = client.get(binderyEndpoint(baseUrl, path)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType("application", "opds+json"))
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS catalog", response.status))
		}
		return response.body<BinderyCatalogDto>().toCatalog()
	}

	override suspend fun fetchManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyManifest {
		val safeBookId = bookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery book id is required.")
		val response = client.get(binderyEndpoint(baseUrl, "books/${encodeUrlPathSegment(safeBookId)}/manifest")) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType("application", "audiobook+json"))
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS manifest", response.status))
		}
		return response.body<BinderyPublicationDto>().toManifest()
	}

	override suspend fun fetchBookResources(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyResourceCatalog {
		val safeBookId = bookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery book id is required.")
		val response = client.get(binderyEndpoint(baseUrl, "books/${encodeUrlPathSegment(safeBookId)}/resources")) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType("application", "opds+json"))
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS resources", response.status))
		}
		return response.body<BinderyResourceCatalogDto>().toResourceCatalog()
	}

	override suspend fun fetchAudiobookVersions(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		limit: Int
	): List<BinderyAudiobookVersion> {
		val safeBookId = bookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery book id is required.")
		val safeLimit = limit.coerceIn(1, 500)
		val response = client.get(
			binderyApiEndpoint(
				baseUrl,
				"audiobooks?bookId=${safeBookId.encodeURLQueryComponent()}&limit=$safeLimit"
			)
		) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery audiobook versions", response.status))
		}
		return response.body<List<BinderyAudiobookVersion>>()
	}

	override suspend fun fetchAudiobookVersion(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		audiobookId: String
	): BinderyAudiobookVersion {
		val safeAudiobookId = audiobookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery audiobook id is required.")
		val response = client.get(binderyApiEndpoint(baseUrl, "audiobooks/${encodeUrlPathSegment(safeAudiobookId)}")) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery audiobook version", response.status))
		}
		return response.body<BinderyAudiobookVersion>()
	}

	override suspend fun fetchAudiobookManifest(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		audiobookId: String
	): BinderyManifest {
		val safeAudiobookId = audiobookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery audiobook id is required.")
		val response = client.get(binderyEndpoint(baseUrl, "audiobooks/${encodeUrlPathSegment(safeAudiobookId)}")) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType("application", "audiobook+json"))
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery audiobook manifest", response.status))
		}
		return response.body<BinderyPublicationDto>().toManifest()
	}

	override suspend fun fetchAudiobookManifestPath(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): BinderyManifest {
		val safePath = path.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery audiobook manifest path is required.")
		val response = client.get(binderyEndpoint(baseUrl, safePath)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType("application", "audiobook+json"))
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery audiobook manifest", response.status))
		}
		return response.body<BinderyPublicationDto>().toManifest()
	}

	override suspend fun fetchBookSync(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyBookSync {
		val safeBookId = bookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery book id is required.")
		val response = client.get(binderyEndpoint(baseUrl, "books/${encodeUrlPathSegment(safeBookId)}/sync")) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery book sync", response.status))
		}
		return decodeBinderyBookSyncJson(response.bodyAsText())
	}

	override suspend fun fetchWhispersyncSidecarJson(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): String {
		val safePath = path.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery Whispersync sidecar path is required.")
		val response = client.get(binderyEndpoint(baseUrl, safePath)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery Whispersync sidecar", response.status))
		}
		return response.bodyAsText()
	}

	override suspend fun fetchResourceBytes(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	): ByteArray {
		val safePath = path.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery resource path is required.")
		val response = client.get(binderyEndpoint(baseUrl, safePath)) {
			requestHeaders.forEach { (key, value) -> header(key, value) }
			accept(ContentType.Application.OctetStream)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS resource", response.status))
		}
		return response.body<ByteArray>()
	}

	override suspend fun fetchReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String,
		alias: String?
	): BinderyReadingProgress {
		val path = binderyReadingProgressPath(bookId, alias)
		val response = client.get(binderyEndpoint(baseUrl, path)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery reading progress", response.status))
		}
		return response.body<BinderyReadingProgress>()
	}

	override suspend fun putReadingProgress(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		progress: BinderyReadingProgress
	) {
		val path = binderyReadingProgressPath(progress.bookId, progress.alias)
		val response = client.put(binderyEndpoint(baseUrl, path)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
			contentType(ContentType.Application.Json)
			setBody(progress)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery reading progress", response.status))
		}
	}

	override suspend fun fetchBookFindings(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		bookId: String
	): BinderyCatalog {
		val safeBookId = bookId.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery book id is required.")
		return fetchCatalog(baseUrl, requestHeaders, "books/${encodeUrlPathSegment(safeBookId)}/findings")
	}

	override suspend fun fetchExternalText(url: String): String {
		val safeUrl = url.trim().takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
			?: throw IllegalStateException("Provider source URL must be absolute.")
		val response = client.get(safeUrl) {
			header("User-Agent", "Navic/1.0 provider-cover-resolver")
			accept(ContentType.Text.Html)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Provider source page", response.status))
		}
		return response.bodyAsText()
	}

	override suspend fun performAction(
		baseUrl: String,
		requestHeaders: Map<String, String>,
		path: String
	) {
		val safePath = path.trim().takeIf { it.isNotEmpty() }
			?: throw IllegalStateException("Bindery action path is required.")
		val response = client.post(binderyEndpoint(baseUrl, safePath)) {
			binderyJsonRequest(requestHeaders)
			accept(ContentType.Application.Json)
		}
		if (!response.status.isSuccess()) {
			throw BinderyApiException(response.status, binderyHttpErrorMessage("Bindery OPDS action", response.status))
		}
	}
}

class BinderyApiException(
	val status: HttpStatusCode,
	message: String
) : IllegalStateException(message), OptionalIntegrationHttpFailure {
	override val statusCode: Int = status.value
}

internal fun io.ktor.client.request.HttpRequestBuilder.binderyJsonRequest(headers: Map<String, String>) {
	headers.forEach { (key, value) -> header(key, value) }
	contentType(ContentType.Application.Json)
}
