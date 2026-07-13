package paige.navic.reader

import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.NodeList

internal const val ReaderPublicationCachePathPrefix = "/reader-cache/"
private const val ReaderPublicationCachePublicationDirectory = "reader-publications"

data class ReaderPublicationResourceRequest(
	val bookId: String,
	val title: String,
	val resourceHref: String,
	val sourceUrl: String,
	val kind: ReaderPublicationKind,
	val format: ReaderPublicationFormat = ReaderPublicationFormat.Epub,
	val mediaOverlayEnabled: Boolean,
	val externalShellCoverHref: String? = null
)

data class ReaderResolvedPublicationResource(
	val publicationUrl: String,
	val publicationFile: File,
	val sessionLease: ReaderSessionLease,
	val resourceHref: String,
	val sourceUrl: String,
	val cacheKey: String,
	val fromCache: Boolean,
	val shellCoverUrl: String? = null,
	val shellCoverTint: String? = null,
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
		val resolved = if (publicationFile.isFile && publicationFile.length() > 0L) {
			request.resolvedPublicationResource(
				publicationFile = publicationFile,
				resourceHref = resourceHref,
				cacheKey = cacheKey,
				fromCache = true,
				publicationExtension = publicationExtension
			)
		} else {
			val bytes = if (request.shouldFetchReaderDevSourceUrl(resourceHref)) {
				request.fetchReaderDevSourceBytes()
			} else {
				fetchResourceBytes(resourceHref)
			}
			publicationFile.parentFile?.mkdirs()
			publicationFile.writeBytes(bytes)
			request.resolvedPublicationResource(
				publicationFile = publicationFile,
				resourceHref = resourceHref,
				cacheKey = cacheKey,
				fromCache = false,
				publicationExtension = publicationExtension
			)
		}
		return resolved
			.withExternalShellCover(request.externalShellCoverHref, fetchResourceBytes)
			.withShellCoverTint()
	}
}

private fun ReaderPublicationResourceRequest.shouldFetchReaderDevSourceUrl(resourceHref: String): Boolean =
	!resourceHref.readerLooksLikeBinderyResourceHref() &&
		sourceUrl.readerLooksLikeReaderDevSourceUrl()

private fun String.readerLooksLikeBinderyResourceHref(): Boolean {
	val path = canonicalReaderResourceHref(this) ?: return false
	return path.startsWith("/opds/", ignoreCase = true) ||
		path.startsWith("/api/", ignoreCase = true)
}

private fun String.readerLooksLikeReaderDevSourceUrl(): Boolean {
	val safeUrl = trim()
	return safeUrl.startsWith("file:", ignoreCase = true) ||
		safeUrl.readerLooksLikeLoopbackHttpSource("127.0.0.1") ||
		safeUrl.readerLooksLikeLoopbackHttpSource("localhost") ||
		safeUrl.readerLooksLikeLoopbackHttpSource("10.0.2.2")
}

private fun String.readerLooksLikeLoopbackHttpSource(host: String): Boolean =
	equals("http://$host", ignoreCase = true) ||
	startsWith("http://$host/", ignoreCase = true) ||
	startsWith("http://$host:", ignoreCase = true)

private suspend fun ReaderPublicationResourceRequest.fetchReaderDevSourceBytes(): ByteArray =
	withContext(Dispatchers.IO) {
		URL(sourceUrl).openStream().use(InputStream::readBytes)
	}

internal fun readerPublicationCacheRoot(context: Context): File =
	readerManagedStorageRoot(context)

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
		sessionLease = ReaderSessionLease.of(publicationFile.parentFile!!),
		resourceHref = resourceHref,
		sourceUrl = sourceUrl,
		cacheKey = cacheKey,
		fromCache = fromCache,
		shellCoverUrl = shellCoverUrl,
		requestHeaders = emptyMap()
	)
}

private suspend fun ReaderResolvedPublicationResource.withExternalShellCover(
	externalShellCoverHref: String?,
	fetchResourceBytes: suspend (String) -> ByteArray
): ReaderResolvedPublicationResource {
	val shellCoverHref = externalShellCoverHref
		?.trim()
		?.takeIf { it.isNotEmpty() }
		?: return this
	val publicationDirectory = publicationFile.parentFile ?: return this
	val cachedCover = publicationDirectory.findCachedExternalShellCover(shellCoverHref)
	if (cachedCover != null) return copy(shellCoverUrl = cachedCover.toReaderShellCoverAssetUrl(cacheKey))
	return runCatching {
		val coverBytes = fetchResourceBytes(shellCoverHref)
		if (coverBytes.isEmpty()) return@runCatching this
		val coverFile = publicationDirectory.resolveExternalShellCoverFile(shellCoverHref, coverBytes)
		coverFile.parentFile?.mkdirs()
		coverFile.writeBytes(coverBytes)
		copy(shellCoverUrl = coverFile.toReaderShellCoverAssetUrl(cacheKey))
	}.getOrElse { this }
}

private suspend fun ReaderResolvedPublicationResource.withShellCoverTint(): ReaderResolvedPublicationResource {
	val coverFile = shellCoverUrl?.readerShellCoverFile(publicationFile.parentFile) ?: return this
	val tint = withContext(Dispatchers.IO) { coverFile.readerCachedDominantTint() } ?: return this
	return copy(shellCoverTint = tint)
}

private fun String.readerShellCoverFile(publicationDirectory: File?): File? {
	val directory = publicationDirectory ?: return null
	val leaf = substringBefore('?')
		.substringBefore('#')
		.substringAfterLast('/')
		.takeIf { it.isNotBlank() }
		?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
		?: return null
	return directory.resolve(leaf).takeIf { it.isFile && it.length() > 0L }
}

private fun File.readerCachedDominantTint(): String? {
	val cacheFile = resolveSibling("$name.dominant-tint")
	if (cacheFile.isFile && cacheFile.lastModified() >= lastModified()) {
		cacheFile.readText().trim().takeIf(String::readerIsHexColor)?.let { return it }
	}
	val tint = readerDominantTint() ?: return null
	cacheFile.writeText(tint)
	return tint
}

private fun File.readerDominantTint(): String? {
	val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
	BitmapFactory.decodeFile(absolutePath, bounds)
	if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
	var sampleSize = 1
	while (bounds.outWidth / sampleSize > 96 || bounds.outHeight / sampleSize > 96) {
		sampleSize *= 2
	}
	val bitmap = BitmapFactory.decodeFile(
		absolutePath,
		BitmapFactory.Options().apply { inSampleSize = sampleSize }
	) ?: return null
	return try {
		data class Bucket(var count: Int = 0, var red: Long = 0, var green: Long = 0, var blue: Long = 0)
		val buckets = mutableMapOf<Int, Bucket>()
		val xStep = maxOf(1, bitmap.width / 32)
		val yStep = maxOf(1, bitmap.height / 32)
		for (y in 0 until bitmap.height step yStep) {
			for (x in 0 until bitmap.width step xStep) {
				val color = bitmap.getPixel(x, y)
				if (Color.alpha(color) < 128) continue
				val red = Color.red(color)
				val green = Color.green(color)
				val blue = Color.blue(color)
				if (red > 245 && green > 245 && blue > 245) continue
				if (red < 12 && green < 12 && blue < 12) continue
				val key = ((red shr 5) shl 6) or ((green shr 5) shl 3) or (blue shr 5)
				buckets.getOrPut(key, ::Bucket).apply {
					count += 1
					this.red += red.toLong()
					this.green += green.toLong()
					this.blue += blue.toLong()
				}
			}
		}
		val dominant = buckets.values.maxByOrNull(Bucket::count)?.takeIf { it.count > 0 } ?: return null
		"#%02x%02x%02x".format(
			(dominant.red / dominant.count).toInt(),
			(dominant.green / dominant.count).toInt(),
			(dominant.blue / dominant.count).toInt()
		)
	} finally {
		bitmap.recycle()
	}
}

private fun String.readerIsHexColor(): Boolean = matches(Regex("^#[0-9a-fA-F]{6}$"))

private fun File.findCachedExternalShellCover(shellCoverHref: String): File? {
	val filePrefix = shellCoverHref.externalShellCoverFilePrefix()
	return (ReaderImageExtensions + "img")
		.map { extension -> resolve("$filePrefix.$extension") }
		.firstOrNull { file -> file.isFile && file.length() > 0L }
}

private fun File.resolveExternalShellCoverFile(shellCoverHref: String, bytes: ByteArray): File =
	resolve("${shellCoverHref.externalShellCoverFilePrefix()}.${bytes.readerImageExtensionFromMagic() ?: shellCoverHref.readerShellCoverImageExtension()}")

private fun File.toReaderShellCoverAssetUrl(cacheKey: String): String =
	readerPublicationAssetUrl("$ReaderPublicationCachePublicationDirectory/$cacheKey/$name")

private fun String.externalShellCoverFilePrefix(): String =
	"shell-cover-${sha256Hex().take(24)}"

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

private fun String.readerShellCoverImageExtension(): String =
	substringBefore('#')
		.substringBefore('?')
		.substringAfterLast('/', missingDelimiterValue = "")
		.substringAfterLast('.', missingDelimiterValue = "")
		.lowercase()
		.takeIf { it in ReaderImageExtensions }
		?: "img"

private fun ByteArray.readerImageExtensionFromMagic(): String? =
	when {
		size >= 8 &&
			this[0] == 0x89.toByte() &&
			this[1] == 0x50.toByte() &&
			this[2] == 0x4e.toByte() &&
			this[3] == 0x47.toByte() &&
			this[4] == 0x0d.toByte() &&
			this[5] == 0x0a.toByte() &&
			this[6] == 0x1a.toByte() &&
			this[7] == 0x0a.toByte() -> "png"
		size >= 3 &&
			this[0] == 0xff.toByte() &&
			this[1] == 0xd8.toByte() &&
			this[2] == 0xff.toByte() -> "jpg"
		size >= 12 &&
			this[0] == 'R'.code.toByte() &&
			this[1] == 'I'.code.toByte() &&
			this[2] == 'F'.code.toByte() &&
			this[3] == 'F'.code.toByte() &&
			this[8] == 'W'.code.toByte() &&
			this[9] == 'E'.code.toByte() &&
			this[10] == 'B'.code.toByte() &&
			this[11] == 'P'.code.toByte() -> "webp"
		size >= 6 &&
			this[0] == 'G'.code.toByte() &&
			this[1] == 'I'.code.toByte() &&
			this[2] == 'F'.code.toByte() -> "gif"
		else -> null
	}

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
