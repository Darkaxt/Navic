package paige.navic.reader

import android.content.Context
import java.io.File
import java.security.MessageDigest

internal const val ReaderPublicationCachePathPrefix = "/reader-cache/"
private const val ReaderPublicationCacheDirectoryName = "reader"
private const val ReaderPublicationCachePublicationDirectory = "reader-publications"

data class ReaderPublicationResourceRequest(
	val bookId: String,
	val title: String,
	val resourceHref: String,
	val sourceUrl: String,
	val kind: ReaderPublicationKind,
	val format: ReaderPublicationFormat = ReaderPublicationFormat.Epub,
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
		val cacheKey = request.readerPublicationCacheKey()
		val publicationExtension = request.publicationExtension()
		val publicationFile = File(
			File(cacheRoot, "$ReaderPublicationCachePublicationDirectory/$cacheKey"),
			"publication.$publicationExtension"
		)
		if (publicationFile.isFile && publicationFile.length() > 0L) {
			return request.resolvedPublicationResource(
				publicationFile = publicationFile,
				resourceHref = resourceHref,
				cacheKey = cacheKey,
				publicationExtension = publicationExtension
			)
		}
		val bytes = fetchResourceBytes(resourceHref)
		publicationFile.parentFile?.mkdirs()
		publicationFile.writeBytes(bytes)
		return request.resolvedPublicationResource(
			publicationFile = publicationFile,
			resourceHref = resourceHref,
			cacheKey = cacheKey,
			publicationExtension = publicationExtension
		)
	}
}

internal fun readerPublicationCacheRoot(context: Context): File =
	File(context.cacheDir, ReaderPublicationCacheDirectoryName)

internal fun readerPublicationAssetUrl(relativePath: String): String =
	ReaderWebRuntime.AssetLoaderOrigin +
		ReaderPublicationCachePathPrefix +
		relativePath.trimStart('/')

internal fun ReaderPublicationResourceRequest.safeResourceHref(): String =
	resourceHref.trim().takeIf { it.isNotEmpty() }
		?: throw IllegalStateException("Reader publication resource href is required.")

internal fun ReaderPublicationResourceRequest.readerPublicationCacheKey(): String {
	val identity = listOf(
		bookId.trim().takeIf { it.isNotEmpty() } ?: "anonymous",
		kind.name,
		format.name,
		mediaOverlayEnabled.toString(),
		safeResourceHref()
	).joinToString(separator = "|")
	return "reader-${identity.sha256Hex().take(24)}"
}

private fun ReaderPublicationResourceRequest.publicationExtension(): String =
	when {
		kind == ReaderPublicationKind.Readaloud -> "epub"
		format == ReaderPublicationFormat.Pdf -> "pdf"
		else -> "epub"
	}

private fun ReaderPublicationResourceRequest.resolvedPublicationResource(
	publicationFile: File,
	resourceHref: String,
	cacheKey: String,
	publicationExtension: String
): ReaderResolvedPublicationResource =
	ReaderResolvedPublicationResource(
		publicationUrl = readerPublicationAssetUrl(
			"$ReaderPublicationCachePublicationDirectory/$cacheKey/publication.$publicationExtension"
		),
		publicationFile = publicationFile,
		resourceHref = resourceHref,
		sourceUrl = sourceUrl,
		cacheKey = cacheKey,
		requestHeaders = emptyMap()
	)

private fun String.sha256Hex(): String =
	MessageDigest.getInstance("SHA-256")
		.digest(encodeToByteArray())
		.joinToString(separator = "") { byte -> "%02x".format(byte) }
