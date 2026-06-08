package paige.navic.reader

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

data class MaterializedStorytellerReadaloudAudio(
	val publicationFile: File,
	val cachedAudioFiles: List<File>,
	private val uriByResource: Map<String, String>
) {
	val publicationUri: String
		get() = publicationFile.toURI().toString()

	fun audioHrefResolver(href: String): String =
		uriByResource[normalizedMediaOverlayResource(href)] ?: href
}

object StorytellerReadaloudAudioCache {
	fun materialize(
		sessionId: String?,
		epubBytes: ByteArray,
		readaloudPackage: StorytellerReadaloudPackage,
		cacheRoot: File
	): MaterializedStorytellerReadaloudAudio {
		val entries = epubEntries(epubBytes)
		val sessionDirectory = File(
			cacheRoot,
			"storyteller-readaloud/${sanitizeSegment(sessionId ?: "anonymous")}"
		)
		sessionDirectory.mkdirs()
		val publicationFile = File(sessionDirectory, "publication.epub")
		publicationFile.writeBytes(epubBytes)
		val uriByResource = linkedMapOf<String, String>()
		val cachedFiles = readaloudPackage.audioResources.mapNotNull { resource ->
			val normalizedHref = normalizedMediaOverlayResource(resource.href)
			val bytes = entries[normalizedHref] ?: return@mapNotNull null
			val target = File(sessionDirectory, cacheFileName(normalizedHref))
			target.parentFile?.mkdirs()
			target.writeBytes(bytes)
			uriByResource[normalizedHref] = target.toURI().toString()
			target
		}
		return MaterializedStorytellerReadaloudAudio(
			publicationFile = publicationFile,
			cachedAudioFiles = cachedFiles,
			uriByResource = uriByResource
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

	private fun cacheFileName(resourceHref: String): String =
		sanitizeSegment(resourceHref.replace('/', '_'))

	private fun sanitizeSegment(value: String): String =
		value
			.replace(Regex("[^A-Za-z0-9._-]+"), "_")
			.trim('_')
			.takeIf { it.isNotBlank() }
			?: "resource"
}
