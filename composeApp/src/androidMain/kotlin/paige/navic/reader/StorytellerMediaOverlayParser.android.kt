package paige.navic.reader

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

object StorytellerMediaOverlayParser {
	fun parse(epubBytes: ByteArray): MediaOverlayTimeline =
		parsePackage(epubBytes).timeline

	fun parsePackage(epubBytes: ByteArray): StorytellerReadaloudPackage {
		val entries = epubEntries(epubBytes)
		val opfPath = opfPath(entries)
		val opf = entries[opfPath]?.parseXml()
			?: return StorytellerReadaloudPackage(MediaOverlayTimeline(emptyList()))
		val manifestItems = opf.elements("item")
			.mapNotNull { item ->
				val id = item.attr("id") ?: return@mapNotNull null
				val href = item.attr("href") ?: return@mapNotNull null
				OpfManifestItem(
					id = id,
					href = resolveRelativePath(opfPath, href),
					mediaType = item.attr("media-type"),
					mediaOverlay = item.attr("media-overlay")
				)
			}
		val manifestById = manifestItems.associateBy(OpfManifestItem::id)
		val durationMsByRef = opf.mediaDurationMsByRef()
		val referencedSmilIds = manifestItems.mapNotNull(OpfManifestItem::mediaOverlay).toSet()
		val smilItems = if (referencedSmilIds.isNotEmpty()) {
			referencedSmilIds.mapNotNull(manifestById::get)
		} else {
			manifestItems.filter { item -> item.mediaType.equals("application/smil+xml", ignoreCase = true) }
		}
		val clips = smilItems.flatMap { item ->
			parseSmilClips(
				smilPath = item.href,
				smil = entries[item.href]?.parseXml() ?: return@flatMap emptyList()
			)
		}.sortedWith(
			compareBy<MediaOverlayClip> { clip -> clip.audioResource }
				.thenBy { clip -> clip.startSeconds }
		)
		val opfDuration = opf.elements("meta")
			.firstOrNull { meta ->
				meta.attr("property").equals("media:duration", ignoreCase = true) &&
					meta.attr("refines") == null
			}
			?.textContent
			?.let(::parseClockSeconds)
		val timeline = MediaOverlayTimeline(
			clips = clips,
			durationSeconds = opfDuration ?: clips.maxOfOrNull(MediaOverlayClip::endSeconds)
		)
		val audioResources = manifestItems
			.filter { item -> item.mediaType?.startsWith("audio/", ignoreCase = true) == true }
			.map { item ->
				StorytellerAudioResource(
					id = item.id,
					href = item.href,
					mediaType = item.mediaType,
					durationMs = durationMsByRef[item.id] ?: clips.durationMsForAudioResource(item.href)
				)
			}
		return StorytellerReadaloudPackage(
			timeline = timeline,
			audioResources = audioResources
		)
	}

	private fun parseSmilClips(
		smilPath: String,
		smil: Document
	): List<MediaOverlayClip> =
		smil.elements("par").mapNotNull { par ->
			val text = par.childElement("text") ?: return@mapNotNull null
			val audio = par.childElement("audio") ?: return@mapNotNull null
			val textSrc = text.attr("src") ?: return@mapNotNull null
			val audioSrc = audio.attr("src") ?: return@mapNotNull null
			val (textResource, fragmentId) = splitResourceFragment(resolveRelativePath(smilPath, textSrc))
			val startSeconds = audio.attr("clipBegin")?.let(::parseClockSeconds) ?: return@mapNotNull null
			val endSeconds = audio.attr("clipEnd")?.let(::parseClockSeconds) ?: return@mapNotNull null
			MediaOverlayClip(
				audioResource = resolveRelativePath(smilPath, audioSrc),
				textResource = textResource,
				fragmentId = fragmentId,
				startSeconds = startSeconds,
				endSeconds = endSeconds,
				label = par.attr("id") ?: fragmentId
			)
		}

	private fun epubEntries(epubBytes: ByteArray): Map<String, ByteArray> =
		buildMap {
			ZipInputStream(ByteArrayInputStream(epubBytes)).use { zip ->
				while (true) {
					val entry = zip.nextEntry ?: break
					if (!entry.isDirectory) {
						val output = ByteArrayOutputStream()
						zip.copyTo(output)
						put(normalizedMediaOverlayResource(entry.name), output.toByteArray())
					}
					zip.closeEntry()
				}
			}
		}

	private fun opfPath(entries: Map<String, ByteArray>): String {
		val container = entries["META-INF/container.xml"]?.parseXml()
		val rootFilePath = container
			?.elements("rootfile")
			?.firstOrNull()
			?.attr("full-path")
			?.let(::normalizedMediaOverlayResource)
		return rootFilePath
			?: entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
			.orEmpty()
	}

	private fun ByteArray.parseXml(): Document =
		DocumentBuilderFactory.newInstance()
			.apply {
				isNamespaceAware = true
				runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
				runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
				runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
				runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
			}
			.newDocumentBuilder()
			.parse(ByteArrayInputStream(this))

	private fun Document.elements(localName: String): List<Element> =
		documentElement?.descendantElements(localName).orEmpty()

	private fun Element.childElement(localName: String): Element? =
		childNodes.asElements().firstOrNull { element -> element.matchesLocalName(localName) }

	private fun Element.descendantElements(localName: String): List<Element> =
		getElementsByTagName("*").asElements().filter { element -> element.matchesLocalName(localName) }

	private fun NodeList.asElements(): List<Element> =
		(0 until length).mapNotNull { index ->
			item(index).takeIf { it.nodeType == Node.ELEMENT_NODE } as? Element
		}

	private fun Element.matchesLocalName(expected: String): Boolean =
		localName.equals(expected, ignoreCase = true) ||
			tagName.substringAfter(':').equals(expected, ignoreCase = true)

	private fun Element.attr(name: String): String? =
		getAttribute(name).trim().takeIf { it.isNotEmpty() }

	private fun Document.mediaDurationMsByRef(): Map<String, Long> =
		elements("meta")
			.mapNotNull { meta ->
				val ref = meta.attr("refines")
					?.removePrefix("#")
					?.trim()
					?.takeIf { it.isNotEmpty() }
					?: return@mapNotNull null
				if (!meta.attr("property").equals("media:duration", ignoreCase = true)) {
					return@mapNotNull null
				}
				val durationMs = parseClockSeconds(meta.textContent)
					?.let { seconds -> (seconds * 1000.0).roundToLong() }
					?: return@mapNotNull null
				ref to durationMs
			}
			.toMap()

	private fun List<MediaOverlayClip>.durationMsForAudioResource(audioResource: String): Long? {
		val normalizedAudioResource = normalizedMediaOverlayResource(audioResource)
		return filter { clip -> normalizedMediaOverlayResource(clip.audioResource) == normalizedAudioResource }
			.maxOfOrNull(MediaOverlayClip::endSeconds)
			?.let { seconds -> (seconds * 1000.0).roundToLong() }
	}

	private fun resolveRelativePath(baseFilePath: String, href: String): String {
		val trimmedHref = href.trim()
		if (trimmedHref.startsWith("http://", ignoreCase = true) ||
			trimmedHref.startsWith("https://", ignoreCase = true)
		) {
			return trimmedHref
		}
		val fragment = trimmedHref.substringAfter('#', "")
			.trim()
			.takeIf { it.isNotEmpty() }
		val resourceHref = trimmedHref.substringBefore('#')
		val baseDirectory = baseFilePath.substringBeforeLast('/', "")
		val resolved = normalizedMediaOverlayResource(
			if (baseDirectory.isBlank()) resourceHref else "$baseDirectory/$resourceHref"
		)
		return if (fragment == null) resolved else "$resolved#$fragment"
	}

	private fun splitResourceFragment(href: String): Pair<String, String?> =
		normalizedMediaOverlayResource(href.substringBefore('#')) to
			href.substringAfter('#', "").trim().takeIf { it.isNotEmpty() }

	private data class OpfManifestItem(
		val id: String,
		val href: String,
		val mediaType: String?,
		val mediaOverlay: String?
	)
}

internal fun parseClockSeconds(raw: String): Double? {
	val value = raw.trim()
		.removePrefix("npt=")
		.trim()
	if (value.isEmpty()) return null
	return when {
		value.endsWith("ms", ignoreCase = true) ->
			value.dropLast(2).trim().toDoubleOrNull()?.div(1000.0)
		value.endsWith("s", ignoreCase = true) ->
			value.dropLast(1).trim().toDoubleOrNull()
		':' in value -> {
			val parts = value.split(':').map { it.trim() }
			when (parts.size) {
				2 -> {
					val minutes = parts[0].toDoubleOrNull() ?: return null
					val seconds = parts[1].toDoubleOrNull() ?: return null
					minutes * 60.0 + seconds
				}
				3 -> {
					val hours = parts[0].toDoubleOrNull() ?: return null
					val minutes = parts[1].toDoubleOrNull() ?: return null
					val seconds = parts[2].toDoubleOrNull() ?: return null
					hours * 3600.0 + minutes * 60.0 + seconds
				}
				else -> null
			}
		}
		else -> value.toDoubleOrNull()
	}
}
