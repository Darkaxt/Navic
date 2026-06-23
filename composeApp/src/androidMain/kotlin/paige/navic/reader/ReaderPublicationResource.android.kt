package paige.navic.reader

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.NodeList

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
	val fromCache: Boolean,
	val shellCoverUrl: String? = null,
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
				fromCache = true,
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
			fromCache = false,
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
	val resourceIdentity = canonicalReaderResourceHref(resourceHref) ?: safeResourceHref()
	val identity = listOf(
		bookId.trim().takeIf { it.isNotEmpty() } ?: "anonymous",
		kind.name,
		format.name,
		mediaOverlayEnabled.toString(),
		resourceIdentity
	).joinToString(separator = "|")
	return "reader-${identity.sha256Hex().take(24)}"
}

private fun ReaderPublicationResourceRequest.publicationExtension(): String =
	when {
		kind == ReaderPublicationKind.Readaloud -> "epub"
		else -> when (format) {
			ReaderPublicationFormat.Epub -> "epub"
			ReaderPublicationFormat.Pdf -> "pdf"
			ReaderPublicationFormat.Azw3 -> "azw3"
			ReaderPublicationFormat.Mobi -> "mobi"
			ReaderPublicationFormat.Cbz -> "cbz"
			ReaderPublicationFormat.Fb2 -> "fb2"
		}
	}

private fun ReaderPublicationResourceRequest.resolvedPublicationResource(
	publicationFile: File,
	resourceHref: String,
	cacheKey: String,
	fromCache: Boolean,
	publicationExtension: String
): ReaderResolvedPublicationResource {
	val shellCoverUrl = if (publicationExtension == "epub") {
		publicationFile.extractReaderShellCoverUrl(cacheKey)
	} else {
		null
	}
	return ReaderResolvedPublicationResource(
		publicationUrl = readerPublicationAssetUrl(
			"$ReaderPublicationCachePublicationDirectory/$cacheKey/publication.$publicationExtension"
		),
		publicationFile = publicationFile,
		resourceHref = resourceHref,
		sourceUrl = sourceUrl,
		cacheKey = cacheKey,
		fromCache = fromCache,
		shellCoverUrl = shellCoverUrl,
		requestHeaders = emptyMap()
	)
}

private data class ReaderOpfManifestItem(
	val id: String,
	val href: String,
	val mediaType: String,
	val properties: String
) {
	val isImage: Boolean
		get() = mediaType.lowercase().startsWith("image/") || href.readerLooksLikeImageHref()
}

private fun File.extractReaderShellCoverUrl(cacheKey: String): String? =
	runCatching {
		ZipFile(this).use { zip ->
			val cover = zip.findReaderCoverEntry() ?: return@use null
			val extension = cover.item.readerCoverImageExtension()
			val coverDirectory = parentFile ?: return@use null
			val coverFile = coverDirectory.resolve("cover.$extension")
			if (!coverFile.isFile || coverFile.length() <= 0L) {
				zip.getInputStream(cover.entry).use { input ->
					coverFile.outputStream().use(input::copyTo)
				}
			}
			readerPublicationAssetUrl("$ReaderPublicationCachePublicationDirectory/$cacheKey/${coverFile.name}")
		}
	}.getOrNull() ?: runCatching {
		ZipFile(this).use { zip ->
			val cover = zip.findReaderCoverEntryFromOpfText() ?: return@use null
			val extension = cover.item.readerCoverImageExtension()
			val coverDirectory = parentFile ?: return@use null
			val coverFile = coverDirectory.resolve("cover.$extension")
			if (!coverFile.isFile || coverFile.length() <= 0L) {
				zip.getInputStream(cover.entry).use { input ->
					coverFile.outputStream().use(input::copyTo)
				}
			}
			readerPublicationAssetUrl("$ReaderPublicationCachePublicationDirectory/$cacheKey/${coverFile.name}")
		}
	}.getOrNull()

private data class ReaderCoverZipEntry(
	val item: ReaderOpfManifestItem,
	val entry: java.util.zip.ZipEntry
)

private fun ZipFile.findReaderCoverEntry(): ReaderCoverZipEntry? {
	val containerEntry = getEntry("META-INF/container.xml") ?: return null
	val container = getInputStream(containerEntry).use(::parseReaderXml)
	val opfPath = container
		.getElementsByTagName("rootfile")
		.asElements()
		.firstNotNullOfOrNull { it.getAttribute("full-path").trim().takeIf(String::isNotEmpty) }
		?.readerSafeZipPath()
		?: return null
	val opfEntry = getEntry(opfPath) ?: return null
	val opfDocument = getInputStream(opfEntry).use(::parseReaderXml)
	val manifestItems = opfDocument
		.getElementsByTagName("item")
		.asElements()
		.mapNotNull { element ->
			val href = element.getAttribute("href").trim()
			if (href.isBlank()) return@mapNotNull null
			ReaderOpfManifestItem(
				id = element.getAttribute("id").trim(),
				href = href,
				mediaType = element.getAttribute("media-type").trim(),
				properties = element.getAttribute("properties").trim()
			)
		}
		.filter(ReaderOpfManifestItem::isImage)
	if (manifestItems.isEmpty()) return null
	val coverMetaItemId = opfDocument
		.getElementsByTagName("meta")
		.asElements()
		.firstOrNull { it.getAttribute("name").equals("cover", ignoreCase = true) }
		?.getAttribute("content")
		?.trim()
		?.takeIf(String::isNotEmpty)
	val coverItem = manifestItems.firstOrNull { item ->
		item.properties.splitToSequence(' ', '\t', '\n', '\r')
			.any { it.equals("cover-image", ignoreCase = true) }
	} ?: coverMetaItemId?.let { coverId ->
		manifestItems.firstOrNull { it.id == coverId }
	} ?: manifestItems.firstOrNull { item ->
		item.id.contains("cover", ignoreCase = true) ||
			item.href.substringAfterLast('/').contains("cover", ignoreCase = true)
	} ?: manifestItems.firstOrNull()
		?: return null
	val coverPath = readerResolveZipHref(opfPath, coverItem.href) ?: return null
	val coverEntry = getEntry(coverPath) ?: return null
	return ReaderCoverZipEntry(coverItem, coverEntry)
}

private fun ZipFile.findReaderCoverEntryFromOpfText(): ReaderCoverZipEntry? {
	val containerEntry = getEntry("META-INF/container.xml") ?: return null
	val containerText = getInputStream(containerEntry).readerText()
	val opfPath = ReaderRootfileRegex.find(containerText)
		?.groups
		?.get(1)
		?.value
		?.trim()
		?.readerUrlDecodedPath()
		?.readerSafeZipPath()
		?: return null
	val opfEntry = getEntry(opfPath) ?: return null
	val opfText = getInputStream(opfEntry).readerText()
	val manifestItems = ReaderItemTagRegex.findAll(opfText)
		.mapNotNull { match ->
			val attributes = match.groups[1]?.value?.readerXmlAttributes().orEmpty()
			val href = attributes["href"]?.trim().orEmpty()
			if (href.isBlank()) return@mapNotNull null
			ReaderOpfManifestItem(
				id = attributes["id"]?.trim().orEmpty(),
				href = href,
				mediaType = attributes["media-type"]?.trim().orEmpty(),
				properties = attributes["properties"]?.trim().orEmpty()
			)
		}
		.filter(ReaderOpfManifestItem::isImage)
		.toList()
	if (manifestItems.isEmpty()) return null
	val coverMetaItemId = ReaderMetaTagRegex.findAll(opfText)
		.map { match -> match.groups[1]?.value?.readerXmlAttributes().orEmpty() }
		.firstOrNull { attributes -> attributes["name"].equals("cover", ignoreCase = true) }
		?.get("content")
		?.trim()
		?.takeIf(String::isNotEmpty)
	val coverItem = readerSelectCoverManifestItem(manifestItems, coverMetaItemId) ?: return null
	val coverPath = readerResolveZipHref(opfPath, coverItem.href) ?: return null
	val coverEntry = getEntry(coverPath) ?: return null
	return ReaderCoverZipEntry(coverItem, coverEntry)
}

private fun parseReaderXml(input: InputStream) =
	DocumentBuilderFactory.newInstance()
		.apply {
			isNamespaceAware = false
			setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
			setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
			setFeature("http://xml.org/sax/features/external-general-entities", false)
			setFeature("http://xml.org/sax/features/external-parameter-entities", false)
		}
		.newDocumentBuilder()
		.parse(input)

private fun NodeList.asElements(): List<Element> =
	(0 until length).mapNotNull { index -> item(index) as? Element }

private fun readerResolveZipHref(opfPath: String, href: String): String? {
	val opfDirectory = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
	val cleanHref = href
		.substringBefore('#')
		.substringBefore('?')
		.trim()
		.replace('\\', '/')
		.readerUrlDecodedPath()
	val candidate = if (opfDirectory.isBlank()) cleanHref else "$opfDirectory/$cleanHref"
	return candidate.readerSafeZipPath()
}

private fun String.readerUrlDecodedPath(): String =
	runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrElse { this }

private fun String.readerSafeZipPath(): String? {
	val parts = replace('\\', '/')
		.split('/')
		.filter { it.isNotBlank() && it != "." }
	if (parts.isEmpty() || parts.any { it == ".." }) return null
	return parts.joinToString("/")
}

private fun ReaderOpfManifestItem.readerCoverImageExtension(): String =
	when (mediaType.lowercase()) {
		"image/png" -> "png"
		"image/jpeg", "image/jpg" -> "jpg"
		"image/webp" -> "webp"
		"image/gif" -> "gif"
		"image/svg+xml" -> "svg"
		else -> href.substringAfterLast('.', missingDelimiterValue = "img")
			.substringBefore('?')
			.substringBefore('#')
			.lowercase()
			.takeIf { it.matches(Regex("[a-z0-9]{2,5}")) }
			?: "img"
	}

private fun readerSelectCoverManifestItem(
	manifestItems: List<ReaderOpfManifestItem>,
	coverMetaItemId: String?
): ReaderOpfManifestItem? =
	manifestItems.firstOrNull { item ->
		item.properties.splitToSequence(' ', '\t', '\n', '\r')
			.any { it.equals("cover-image", ignoreCase = true) }
	} ?: coverMetaItemId?.let { coverId ->
		manifestItems.firstOrNull { it.id == coverId }
	} ?: manifestItems.firstOrNull { item ->
		item.id.contains("cover", ignoreCase = true) ||
			item.href.substringAfterLast('/').contains("cover", ignoreCase = true)
	} ?: manifestItems.firstOrNull()

private fun InputStream.readerText(): String =
	bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

private fun String.readerXmlAttributes(): Map<String, String> =
	ReaderAttributeRegex.findAll(this).associate { match ->
		val key = match.groupValues[1].substringAfterLast(':').lowercase()
		val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
		key to value
	}

private fun String.readerLooksLikeImageHref(): Boolean =
	substringBefore('#')
		.substringBefore('?')
		.substringAfterLast('.', missingDelimiterValue = "")
		.lowercase() in ReaderImageExtensions

private val ReaderImageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "svg")
private val ReaderRootfileRegex = Regex(
	"""<\s*(?:[\w.-]+:)?rootfile\b[^>]*\bfull-path\s*=\s*["']([^"']+)["'][^>]*>""",
	RegexOption.IGNORE_CASE
)
private val ReaderItemTagRegex = Regex("""<\s*(?:[\w.-]+:)?item\b([^>]*)>""", RegexOption.IGNORE_CASE)
private val ReaderMetaTagRegex = Regex("""<\s*(?:[\w.-]+:)?meta\b([^>]*)>""", RegexOption.IGNORE_CASE)
private val ReaderAttributeRegex = Regex("""([\w:.-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

private fun String.sha256Hex(): String =
	MessageDigest.getInstance("SHA-256")
		.digest(encodeToByteArray())
		.joinToString(separator = "") { byte -> "%02x".format(byte) }
