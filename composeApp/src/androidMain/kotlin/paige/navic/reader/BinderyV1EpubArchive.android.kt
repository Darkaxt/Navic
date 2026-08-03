package paige.navic.reader

import java.io.File
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

internal data class BinderyV1EpubVerificationLimits(
	val maxArchiveBytes: Long = 512L * 1024L * 1024L,
	val maxArchiveEntryCount: Int = 10_000,
	val maxArchiveEntryNameBytes: Int = 1024 * 1024,
	val maxXmlEntryBytes: Int = 4 * 1024 * 1024,
	val maxContentEntryBytes: Int = 16 * 1024 * 1024,
	val maxTotalReadableEntryBytes: Int = 128 * 1024 * 1024,
	val maxTotalExtractedTextBytes: Int = 64 * 1024 * 1024,
	val maxTotalTokenCount: Int = 2_000_000,
	val maxManifestItemCount: Int = 50_000,
	val maxSpineItemCount: Int = 20_000
) {
	init {
		require(maxArchiveBytes > 0L)
		require(maxArchiveEntryCount > 0)
		require(maxArchiveEntryNameBytes > 0)
		require(maxXmlEntryBytes > 0)
		require(maxContentEntryBytes > 0)
		require(maxTotalReadableEntryBytes > 0)
		require(maxTotalExtractedTextBytes > 0)
		require(maxTotalTokenCount > 0)
		require(maxManifestItemCount > 0)
		require(maxSpineItemCount > 0)
	}
}

internal data class BinderyV1EpubDocument(
	val href: String,
	val spineIndex: Int,
	val content: BinderyV1PrivateChapterText
)

internal class BinderyV1EpubLoader(
	private val limits: BinderyV1EpubVerificationLimits
) {
	fun load(publicationFile: File): List<BinderyV1EpubDocument> {
		if (!publicationFile.isFile || publicationFile.length() <= 0L) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
		}
		if (publicationFile.length() > limits.maxArchiveBytes) {
			wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ResourceLimit)
		}
		return BinderyV1BoundedZipArchive(publicationFile, limits).use { archive ->
			val container = archive.readFirstExact(
				name = BinderyContainerPath,
				maxBytes = limits.maxXmlEntryBytes
			) ?: wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
			val rootfile = parseBinderyContainerRootfile(container)
				?: wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
			val opf = archive.readFirstExact(rootfile, limits.maxXmlEntryBytes)
				?: wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
			val (items, spine) = parseBinderyOpf(opf, limits)
			val opfDirectory = unixFilepathDir(rootfile).takeUnless { it == "." }.orEmpty()
			val contentByHref = mutableMapOf<String, BinderyV1PrivateChapterText?>()
			val documents = mutableListOf<BinderyV1EpubDocument>()
			var totalExtractedBytes = 0
			var totalTokenCount = 0
			spine.forEachIndexed { spineIndex, itemId ->
				val item = items[itemId]
				if (item == null || !item.mediaType.contains("html")) return@forEachIndexed
				val href = unixFilepathJoin(opfDirectory, item.href)
				val content = if (contentByHref.containsKey(href)) {
					contentByHref[href]
				} else {
					val extracted = archive.readFirstExact(href, limits.maxContentEntryBytes)
						?.let { raw ->
							extractBinderyV1ChapterText(
								raw = raw,
								maxExtractedBytes = limits.maxTotalExtractedTextBytes - totalExtractedBytes,
								maxTokenCount = limits.maxTotalTokenCount - totalTokenCount
							)
						}
						?.takeIf(BinderyV1PrivateChapterText::isReadable)
					contentByHref[href] = extracted
					extracted
				} ?: return@forEachIndexed
				if (
					content.byteLength > limits.maxTotalExtractedTextBytes - totalExtractedBytes ||
					content.tokenCount > limits.maxTotalTokenCount - totalTokenCount
				) {
					wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ResourceLimit)
				}
				totalExtractedBytes += content.byteLength
				totalTokenCount += content.tokenCount
				documents += BinderyV1EpubDocument(
					href = href,
					spineIndex = spineIndex,
					content = content
				)
			}
			documents
		}
	}
}

private data class BinderyOpfItem(
	val href: String,
	val mediaType: String
)

private fun parseBinderyContainerRootfile(raw: ByteArray): String? {
	var rootfile: String? = null
	try {
		parseBinderyXml(raw, object : DefaultHandler() {
			override fun startElement(
				uri: String?,
				localName: String?,
				qualifiedName: String?,
				attributes: Attributes
			) {
				if (rootfile != null || binderyXmlLocalName(localName, qualifiedName) != "rootfile") return
				for (index in 0 until attributes.length) {
					if (
						binderyXmlLocalName(
							attributes.getLocalName(index),
							attributes.getQName(index)
						) == "full-path"
					) {
						val value = attributes.getValue(index)
						if (value.goTrimSpace().isNotEmpty()) {
							rootfile = value
							return
						}
					}
				}
			}
		})
	} catch (error: WordSyncPublicationVerificationException) {
		throw error
	} catch (_: Exception) {
		// Bindery returns the first rootfile token even when a later token is malformed.
	}
	return rootfile
}

private fun parseBinderyOpf(
	raw: ByteArray,
	limits: BinderyV1EpubVerificationLimits
): Pair<Map<String, BinderyOpfItem>, List<String>> {
	val items = mutableMapOf<String, BinderyOpfItem>()
	val spine = mutableListOf<String>()
	var itemCount = 0
	try {
		parseBinderyXml(raw, object : DefaultHandler() {
			override fun startElement(
				uri: String?,
				localName: String?,
				qualifiedName: String?,
				attributes: Attributes
			) {
				when (binderyXmlLocalName(localName, qualifiedName)) {
					"item" -> {
						itemCount += 1
						if (itemCount > limits.maxManifestItemCount) {
							wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ResourceLimit)
						}
						var id = ""
						var href = ""
						var mediaType = ""
						for (index in 0 until attributes.length) {
							when (
								binderyXmlLocalName(
									attributes.getLocalName(index),
									attributes.getQName(index)
								)
							) {
								"id" -> id = attributes.getValue(index)
								"href" -> href = attributes.getValue(index)
								"media-type" -> mediaType = attributes.getValue(index)
							}
						}
						if (id.isNotEmpty()) items[id] = BinderyOpfItem(href, mediaType)
					}
					"itemref" -> {
						for (index in 0 until attributes.length) {
							if (
								binderyXmlLocalName(
									attributes.getLocalName(index),
									attributes.getQName(index)
								) == "idref"
							) {
								val idref = attributes.getValue(index)
								if (idref.isNotEmpty()) {
									if (spine.size >= limits.maxSpineItemCount) {
										wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.ResourceLimit)
									}
									spine += idref
								}
							}
						}
					}
				}
			}
		})
	} catch (error: WordSyncPublicationVerificationException) {
		throw error
	} catch (error: Exception) {
		val verificationError = error.cause as? WordSyncPublicationVerificationException
		if (verificationError != null) throw verificationError
		// Bindery keeps tokens decoded before the first XML error.
	}
	return items to spine
}

private fun parseBinderyXml(raw: ByteArray, handler: DefaultHandler) {
	if (raw.containsXmlDoctypeDeclaration()) {
		wordSyncVerificationFailure(WordSyncPublicationVerificationFailure.InvalidArchive)
	}
	val factory = SAXParserFactory.newInstance().apply {
		isNamespaceAware = false
		runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
		runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
		runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
	}
	factory.newSAXParser().xmlReader.apply {
		contentHandler = handler
		entityResolver = { _, _ -> InputSource(StringReader("")) }
	}.parse(InputSource(StringReader(raw.binderyStrictUtf8())))
}

private fun ByteArray.containsXmlDoctypeDeclaration(): Boolean {
	var index = 0
	while (index < size) {
		if (this[index] != '<'.code.toByte()) {
			index += 1
			continue
		}
		when {
			matchesAscii(index, "<!--") -> {
				index = indexAfterAscii(index + 4, "-->") ?: return false
			}
			matchesAscii(index, "<![CDATA[") -> {
				index = indexAfterAscii(index + 9, "]]>") ?: return false
			}
			matchesAscii(index, "<?") -> {
				index = indexAfterAscii(index + 2, "?>") ?: return false
			}
			matchesAscii(index, "<!DOCTYPE", ignoreCase = true) &&
				getOrNull(index + 9)?.isXmlSpace() == true -> return true
			else -> index = indexAfterXmlMarkup(index + 1)
		}
	}
	return false
}

private fun ByteArray.indexAfterXmlMarkup(start: Int): Int {
	var index = start
	var quote: Byte? = null
	while (index < size) {
		val byte = this[index]
		when {
			quote != null && byte == quote -> quote = null
			quote == null && (byte == '\''.code.toByte() || byte == '"'.code.toByte()) -> quote = byte
			quote == null && byte == '>'.code.toByte() -> return index + 1
		}
		index += 1
	}
	return size
}

private fun ByteArray.indexAfterAscii(start: Int, value: String): Int? {
	var index = start
	while (index <= size - value.length) {
		if (matchesAscii(index, value)) return index + value.length
		index += 1
	}
	return null
}

private fun ByteArray.matchesAscii(
	start: Int,
	value: String,
	ignoreCase: Boolean = false
): Boolean {
	if (start < 0 || start > size - value.length) return false
	return value.indices.all { offset ->
		val actual = this[start + offset]
		val expected = value[offset].code.toByte()
		if (ignoreCase) actual.asciiLowercase() == expected.asciiLowercase() else actual == expected
	}
}

private fun Byte.isXmlSpace(): Boolean = when (toInt() and 0xff) {
	0x09, 0x0a, 0x0d, 0x20 -> true
	else -> false
}

private fun Byte.asciiLowercase(): Byte {
	val value = toInt() and 0xff
	return if (value in 'A'.code..'Z'.code) (value + ('a'.code - 'A'.code)).toByte() else this
}

private fun binderyXmlLocalName(localName: String?, qualifiedName: String?): String =
	localName?.takeIf(String::isNotEmpty) ?: qualifiedName.orEmpty().substringAfter(':')

private fun unixFilepathDir(path: String): String {
	val slash = path.lastIndexOf('/')
	return if (slash < 0) "." else unixFilepathClean(path.substring(0, slash + 1))
}

private fun unixFilepathJoin(directory: String, href: String): String = when {
	directory.isEmpty() && href.isEmpty() -> ""
	directory.isEmpty() -> unixFilepathClean(href)
	else -> unixFilepathClean("$directory/$href")
}

private fun unixFilepathClean(path: String): String {
	if (path.isEmpty()) return "."
	val rooted = path.startsWith('/')
	val segments = mutableListOf<String>()
	path.split('/').forEach { segment ->
		when (segment) {
			"", "." -> Unit
			".." -> when {
				segments.isNotEmpty() && segments.last() != ".." -> segments.removeAt(segments.lastIndex)
				!rooted -> segments += segment
			}
			else -> segments += segment
		}
	}
	val body = segments.joinToString("/")
	return when {
		rooted && body.isEmpty() -> "/"
		rooted -> "/$body"
		body.isEmpty() -> "."
		else -> body
	}
}

private const val BinderyContainerPath = "META-INF/container.xml"
