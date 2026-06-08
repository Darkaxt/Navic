package paige.navic.reader

import java.io.File
import java.security.MessageDigest

data class ReaderPublicationResourceRequest(
	val bookId: String,
	val title: String,
	val resourceHref: String,
	val sourceUrl: String,
	val kind: ReaderPublicationKind,
	val mediaOverlayEnabled: Boolean
)

data class ReaderResolvedPublicationResource(
	val publicationUrl: String,
	val publicationFile: File,
	val resourceHref: String,
	val sourceUrl: String,
	val cacheKey: String,
	val requestHeaders: Map<String, String> = emptyMap()
)

class BinderyReaderPublicationResolver(
	private val fetchResourceBytes: suspend (String) -> ByteArray,
	private val cacheRoot: File
) {
	suspend fun resolve(request: ReaderPublicationResourceRequest): ReaderResolvedPublicationResource {
		val resourceHref = request.safeResourceHref()
		val bytes = fetchResourceBytes(resourceHref)
		val cacheKey = request.readerPublicationCacheKey()
		val publicationFile = File(
			File(cacheRoot, "reader-publications/$cacheKey"),
			"publication.${request.publicationExtension()}"
		)
		publicationFile.parentFile?.mkdirs()
		publicationFile.writeBytes(bytes)
		return ReaderResolvedPublicationResource(
			publicationUrl = publicationFile.toURI().toString(),
			publicationFile = publicationFile,
			resourceHref = resourceHref,
			sourceUrl = request.sourceUrl,
			cacheKey = cacheKey,
			requestHeaders = emptyMap()
		)
	}
}

internal fun ReaderPublicationResourceRequest.safeResourceHref(): String =
	resourceHref.trim().takeIf { it.isNotEmpty() }
		?: throw IllegalStateException("Reader publication resource href is required.")

internal fun ReaderPublicationResourceRequest.readerPublicationCacheKey(): String {
	val identity = listOf(
		bookId.trim().takeIf { it.isNotEmpty() } ?: "anonymous",
		kind.name,
		mediaOverlayEnabled.toString(),
		safeResourceHref()
	).joinToString(separator = "|")
	return "reader-${identity.sha256Hex().take(24)}"
}

private fun ReaderPublicationResourceRequest.publicationExtension(): String =
	when (kind) {
		ReaderPublicationKind.Ebook,
		ReaderPublicationKind.Readaloud -> "epub"
	}

private fun String.sha256Hex(): String =
	MessageDigest.getInstance("SHA-256")
		.digest(encodeToByteArray())
		.joinToString(separator = "") { byte -> "%02x".format(byte) }
