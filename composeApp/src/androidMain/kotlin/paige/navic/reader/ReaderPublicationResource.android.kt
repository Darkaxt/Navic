package paige.navic.reader

import android.graphics.BitmapFactory
import android.graphics.Color
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import paige.navic.ui.navigation.Screen

internal const val ReaderPublicationCachePathPrefix = "/reader-cache/"
private const val ReaderPublicationCachePublicationDirectory = "reader-publications"
private const val ReaderPublicationIntegrityFileName = "publication.integrity"
private const val ReaderPublicationIntegritySchemaVersion = 1

data class ReaderPublicationResourceRequest(
	val bookId: String,
	val title: String,
	val resourceHref: String,
	val sourceUrl: String,
	val kind: ReaderPublicationKind,
	val format: ReaderPublicationFormat = ReaderPublicationFormat.Epub,
	val mediaOverlayEnabled: Boolean,
	val accountScopeHash: String = "",
	val contentRevisionHash: String = "",
	val externalShellCoverHref: String? = null
)

internal fun Screen.Reader.readerPublicationResourceRequest(
	accountScopeHash: String,
	externalShellCoverHref: String? = null
): ReaderPublicationResourceRequest = ReaderPublicationResourceRequest(
	bookId = bookId,
	title = title,
	resourceHref = resourceHref,
	sourceUrl = publicationUrl,
	kind = kind,
	format = publicationFormat,
	mediaOverlayEnabled = mediaOverlayEnabled,
	accountScopeHash = accountScopeHash,
	contentRevisionHash = publicationRevisionHash.orEmpty(),
	externalShellCoverHref = externalShellCoverHref
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
	private val cacheRoot: File,
	private val cacheDispatcher: CoroutineDispatcher = Dispatchers.IO,
	private val cacheWorkObserver: (() -> Unit)? = null
) {
	suspend fun resolve(request: ReaderPublicationResourceRequest): ReaderResolvedPublicationResource {
		val resourceHref = request.safeResourceHref()
		val prefetchedBytes = if (request.contentRevisionHash.isBlank()) {
			request.fetchPublicationBytes(resourceHref, fetchResourceBytes).also {
				currentCoroutineContext().ensureActive()
			}
		} else {
			null
		}
		val identifiedRequest = if (prefetchedBytes == null) {
			request
		} else {
			request.copy(
				contentRevisionHash = cacheWork { prefetchedBytes.sha256Hex() }
			)
		}
		val cacheKey = identifiedRequest.readerPublicationCacheKey()
		val publicationExtension = identifiedRequest.publicationExtension()
		val publicationFile = File(
			File(cacheRoot, "$ReaderPublicationCachePublicationDirectory/$cacheKey"),
			"publication.$publicationExtension"
		)
		val resolved = ReaderPublicationTargetLocks.withTarget(publicationFile) {
			val publicationDirectory = publicationFile.parentFile!!
			val sessionLease = ReaderSessionLease.shared(publicationDirectory)
			var transferred = false
			try {
				val integrityFile = publicationDirectory.resolve(ReaderPublicationIntegrityFileName)
				val cached = cacheWork {
					publicationFile.cleanupPublicationTemporaryFiles(integrityFile)
					if (publicationFile.isValidPublicationCacheMaterial(integrityFile)) {
						publicationFile.cacheResult(
							cacheKey = cacheKey,
							fromCache = true,
							publicationExtension = publicationExtension
						)
					} else {
						publicationFile.deleteInvalidPublicationCacheMaterial(integrityFile)
						null
					}
				}
				val cacheResult = cached ?: run {
					val bytes = prefetchedBytes
						?: identifiedRequest.fetchPublicationBytes(resourceHref, fetchResourceBytes)
					currentCoroutineContext().ensureActive()
					cacheWork {
						check(bytes.isNotEmpty()) { "Reader publication source returned no cacheable bytes." }
						check(publicationFile.writePublicationCacheAtomically(integrityFile, bytes)) {
							"Reader publication cache write failed."
						}
						publicationFile.cacheResult(
							cacheKey = cacheKey,
							fromCache = false,
							publicationExtension = publicationExtension
						)
					}
				}
				currentCoroutineContext().ensureActive()
				identifiedRequest.resolvedPublicationResource(
					publicationFile = publicationFile,
					resourceHref = resourceHref,
					cacheKey = cacheKey,
					cacheResult = cacheResult,
					sessionLease = sessionLease
				).also { transferred = true }
			} finally {
				if (!transferred) sessionLease.release()
			}
		}
		var transferred = false
		try {
			currentCoroutineContext().ensureActive()
			val enriched = resolved
				.withExternalShellCover(request.externalShellCoverHref, fetchResourceBytes)
				.withShellCoverTint()
			currentCoroutineContext().ensureActive()
			transferred = true
			return enriched
		} finally {
			if (!transferred) resolved.sessionLease.release()
		}
	}

	private suspend fun <T> cacheWork(action: suspend () -> T): T =
		withContext(cacheDispatcher) {
			currentCoroutineContext().ensureActive()
			cacheWorkObserver?.invoke()
			action()
		}
}

private data class ReaderPublicationCacheResult(
	val fromCache: Boolean,
	val shellCoverUrl: String?
)

private data class ReaderPublicationTargetLock(
	val mutex: Mutex,
	var users: Int
)

private object ReaderPublicationTargetLocks {
	private val targets = mutableMapOf<String, ReaderPublicationTargetLock>()

	suspend fun <T> withTarget(target: File, action: suspend () -> T): T {
		val path = target.absoluteFile.normalize().path
		val targetLock = synchronized(this) {
			targets.getOrPut(path) { ReaderPublicationTargetLock(Mutex(), users = 0) }
				.also { lock -> lock.users += 1 }
		}
		var acquired = false
		return try {
			targetLock.mutex.lock()
			acquired = true
			action()
		} finally {
			if (acquired) targetLock.mutex.unlock()
			synchronized(this) {
				targetLock.users -= 1
				check(targetLock.users >= 0)
				if (targetLock.users == 0) targets.remove(path, targetLock)
			}
		}
	}
}

private data class ReaderPublicationIntegrity(
	val byteSize: Long,
	val contentHash: String
) {
	fun encode(): ByteArray = buildString {
		append(ReaderPublicationIntegritySchemaVersion)
		append('\n')
		append(byteSize)
		append('\n')
		append(contentHash)
		append('\n')
	}.encodeToByteArray()
}

private suspend fun File.isValidPublicationCacheMaterial(integrityFile: File): Boolean {
	currentCoroutineContext().ensureActive()
	if (!isFile || Files.isSymbolicLink(toPath()) || length() <= 0L) return false
	val integrity = integrityFile.readPublicationIntegrity() ?: return false
	return length() == integrity.byteSize && sha256Hex() == integrity.contentHash
}

private fun File.readPublicationIntegrity(): ReaderPublicationIntegrity? {
	if (!isFile || Files.isSymbolicLink(toPath()) || length() !in 1L..256L) return null
	return runCatching {
		val lines = readLines(StandardCharsets.US_ASCII)
		if (lines.size != 3 || lines[0].toInt() != ReaderPublicationIntegritySchemaVersion) return@runCatching null
		val byteSize = lines[1].toLong().takeIf { size -> size > 0L } ?: return@runCatching null
		val contentHash = lines[2].takeIf { hash -> hash.matches(Regex("[0-9a-f]{64}")) }
			?: return@runCatching null
		ReaderPublicationIntegrity(byteSize, contentHash)
	}.getOrNull()
}

private fun File.deleteInvalidPublicationCacheMaterial(integrityFile: File) {
	if (exists()) delete()
	if (integrityFile.exists()) integrityFile.delete()
}

private suspend fun File.cleanupPublicationTemporaryFiles(integrityFile: File) {
	val directory = parentFile ?: return
	directory.listFiles().orEmpty()
		.filter { file ->
			file.name.endsWith(".tmp") &&
				(file.name.startsWith("$name.") || file.name.startsWith("${integrityFile.name}."))
		}
		.forEach { file ->
			currentCoroutineContext().ensureActive()
			file.delete()
		}
}

private suspend fun File.writePublicationCacheAtomically(
	integrityFile: File,
	bytes: ByteArray
): Boolean {
	currentCoroutineContext().ensureActive()
	val directory = parentFile ?: return false
	directory.mkdirs()
	if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath())) return false
	cleanupPublicationTemporaryFiles(integrityFile)
	val suffix = "${System.nanoTime()}.tmp"
	val publicationTemporary = directory.resolve("$name.$suffix")
	val integrityTemporary = directory.resolve("${integrityFile.name}.$suffix")
	val integrity = ReaderPublicationIntegrity(bytes.size.toLong(), bytes.sha256Hex())
	var publicationPromoted = false
	return try {
		publicationTemporary.writeSynced(bytes)
		integrityTemporary.writeSynced(integrity.encode())
		currentCoroutineContext().ensureActive()
		readerPublicationPromote(publicationTemporary, this)
		publicationPromoted = true
		currentCoroutineContext().ensureActive()
		readerPublicationPromote(integrityTemporary, integrityFile)
		currentCoroutineContext().ensureActive()
		if (!isValidPublicationCacheMaterial(integrityFile)) {
			deleteInvalidPublicationCacheMaterial(integrityFile)
			false
		} else {
			true
		}
	} catch (cancelled: CancellationException) {
		if (publicationPromoted) deleteInvalidPublicationCacheMaterial(integrityFile)
		throw cancelled
	} catch (_: Throwable) {
		if (publicationPromoted) deleteInvalidPublicationCacheMaterial(integrityFile)
		false
	} finally {
		publicationTemporary.delete()
		integrityTemporary.delete()
	}
}

private suspend fun File.writeSynced(bytes: ByteArray) {
	FileOutputStream(this).use { output ->
		var offset = 0
		while (offset < bytes.size) {
			currentCoroutineContext().ensureActive()
			val count = minOf(DEFAULT_BUFFER_SIZE, bytes.size - offset)
			output.write(bytes, offset, count)
			offset += count
		}
		currentCoroutineContext().ensureActive()
		output.fd.sync()
	}
}

private fun readerPublicationPromote(source: File, target: File) {
	try {
		Files.move(
			source.toPath(),
			target.toPath(),
			StandardCopyOption.ATOMIC_MOVE,
			StandardCopyOption.REPLACE_EXISTING
		)
	} catch (_: AtomicMoveNotSupportedException) {
		Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
	}
}

private suspend fun File.sha256Hex(): String {
	val digest = MessageDigest.getInstance("SHA-256")
	inputStream().use { input ->
		val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
		while (true) {
			currentCoroutineContext().ensureActive()
			val count = input.read(buffer)
			if (count < 0) break
			digest.update(buffer, 0, count)
		}
	}
	currentCoroutineContext().ensureActive()
	return digest.digest().toHexString()
}

private suspend fun ByteArray.sha256Hex(): String {
	val digest = MessageDigest.getInstance("SHA-256")
	var offset = 0
	while (offset < size) {
		currentCoroutineContext().ensureActive()
		val count = minOf(DEFAULT_BUFFER_SIZE, size - offset)
		digest.update(this, offset, count)
		offset += count
	}
	currentCoroutineContext().ensureActive()
	return digest.digest().toHexString()
}

private fun ByteArray.toHexString(): String =
	joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private suspend fun ReaderPublicationResourceRequest.fetchPublicationBytes(
	resourceHref: String,
	fetchResourceBytes: suspend (String) -> ByteArray
): ByteArray = if (shouldFetchReaderDevSourceUrl(resourceHref)) {
	fetchReaderDevSourceBytes()
} else {
	fetchResourceBytes(resourceHref)
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
	val revisionAuthority = contentRevisionHash.trim().takeIf(String::isNotEmpty)
		?: error("Reader publication revision authority is required for cache identity.")
	val identity = readerPublicationStructuredIdentity(
		"book" to (bookId.trim().takeIf { it.isNotEmpty() } ?: "anonymous"),
		"kind" to kind.name,
		"format" to format.name,
		"media-overlay" to mediaOverlayEnabled.toString(),
		"resource" to resourceIdentity,
		"origin" to sourceUrl.readerPublicationOriginHash(),
		"account" to accountScopeHash.readerPublicationAuthorityHash(),
		"revision" to revisionAuthority.readerPublicationAuthorityHash()
	)
	return "reader-${identity.sha256Hex().take(24)}"
}

private fun readerPublicationStructuredIdentity(vararg fields: Pair<String, String>): String =
	buildString {
		append("navic.reader.publication-cache.v1")
		fields.forEach { (name, value) ->
			append(name.length)
			append(':')
			append(name)
			append(value.length)
			append(':')
			append(value)
		}
	}

private fun String.readerPublicationOriginHash(): String {
	val raw = trim()
	val origin = runCatching {
		val uri = URI(raw)
		val scheme = uri.scheme?.lowercase()?.takeIf { value -> value == "http" || value == "https" }
			?: return@runCatching raw
		val host = uri.host?.lowercase()?.takeIf(String::isNotBlank) ?: return@runCatching raw
		val port = when {
			uri.port >= 0 -> uri.port
			scheme == "https" -> 443
			else -> 80
		}
		"$scheme://$host:$port"
	}.getOrDefault(raw)
	return origin.readerPublicationAuthorityHash()
}

internal fun String.readerPublicationAuthorityHash(): String =
	normalizedPublicationAuthority().sha256Hex()

private fun String.normalizedPublicationAuthority(): String =
	trim().takeIf(String::isNotEmpty) ?: "unspecified"

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

private fun File.cacheResult(
	cacheKey: String,
	fromCache: Boolean,
	publicationExtension: String
): ReaderPublicationCacheResult = ReaderPublicationCacheResult(
	fromCache = fromCache,
	shellCoverUrl = if (publicationExtension == "epub") extractReaderShellCoverUrl(cacheKey) else null
)

private fun ReaderPublicationResourceRequest.resolvedPublicationResource(
	publicationFile: File,
	resourceHref: String,
	cacheKey: String,
	cacheResult: ReaderPublicationCacheResult,
	sessionLease: ReaderSessionLease
): ReaderResolvedPublicationResource =
	ReaderResolvedPublicationResource(
		publicationUrl = readerPublicationAssetUrl(
			"$ReaderPublicationCachePublicationDirectory/$cacheKey/publication.${publicationExtension()}"
		),
		publicationFile = publicationFile,
		sessionLease = sessionLease,
		resourceHref = resourceHref,
		sourceUrl = sourceUrl,
		cacheKey = cacheKey,
		fromCache = cacheResult.fromCache,
		shellCoverUrl = cacheResult.shellCoverUrl,
		requestHeaders = emptyMap()
	)

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
	return try {
		val coverBytes = fetchResourceBytes(shellCoverHref)
		currentCoroutineContext().ensureActive()
		if (coverBytes.isEmpty()) return this
		val coverFile = publicationDirectory.resolveExternalShellCoverFile(shellCoverHref, coverBytes)
		coverFile.parentFile?.mkdirs()
		coverFile.writeBytes(coverBytes)
		currentCoroutineContext().ensureActive()
		copy(shellCoverUrl = coverFile.toReaderShellCoverAssetUrl(cacheKey))
	} catch (cancelled: CancellationException) {
		throw cancelled
	} catch (_: Throwable) {
		this
	}
}

private suspend fun ReaderResolvedPublicationResource.withShellCoverTint(): ReaderResolvedPublicationResource {
	val coverFile = shellCoverUrl?.readerShellCoverFile(publicationFile.parentFile) ?: return this
	val tint = withContext(Dispatchers.IO) { coverFile.readerCachedDominantTint() } ?: return this
	currentCoroutineContext().ensureActive()
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
	MessageDigest.getInstance("SHA-256").digest(encodeToByteArray()).toHexString()
