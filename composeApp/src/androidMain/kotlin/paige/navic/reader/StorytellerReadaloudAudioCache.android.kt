package paige.navic.reader

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class MaterializedStorytellerReadaloudAudio(
	val publicationFile: File,
	val publicationUrl: String,
	val cachedAudioFiles: List<File>,
	val sessionLease: ReaderSessionLease,
	private val uriByResource: Map<String, String>
) {
	val publicationUri: String
		get() = publicationUrl

	fun audioHrefResolver(href: String): String =
		uriByResource[normalizedMediaOverlayResource(href)] ?: href
}

object StorytellerReadaloudAudioCache {
	internal fun materialize(
		sessionId: String?,
		archive: StorytellerEpubArchive,
		publicationFile: File,
		publicationUrl: String,
		readaloudPackage: StorytellerReadaloudPackage,
		cacheRoot: File
	): MaterializedStorytellerReadaloudAudio {
		val sessionDirectory = File(
			cacheRoot,
			"$ReaderReadaloudSessionDirectoryName/${sanitizeSegment(sessionId ?: "anonymous")}"
		)
		sessionDirectory.mkdirs()
		val resources = readaloudPackage.audioResources.mapIndexedNotNull { index, resource ->
			val normalizedHref = normalizedMediaOverlayResource(resource.href)
			if (!archive.contains(normalizedHref)) return@mapIndexedNotNull null
			CachedAudioResource(
				href = normalizedHref,
				fileName = cacheFileName(index, normalizedHref),
				expectedSize = archive.entrySize(normalizedHref)
			)
		}
		val versionDirectory = File(sessionDirectory, CurrentCacheVersion)
		val cachedFiles = if (versionDirectory.hasCompleteAudio(resources)) {
			resources.map { resource -> File(versionDirectory, resource.fileName) }
		} else {
			materializeVersion(
				archive = archive,
				sessionDirectory = sessionDirectory,
				versionDirectory = versionDirectory,
				resources = resources
			)
		}
		removeLegacyCacheAfterSuccessfulMaterialization(sessionDirectory)
		val uriByResource = resources.zip(cachedFiles).associate { (resource, file) ->
			resource.href to file.toURI().toString()
		}
		return MaterializedStorytellerReadaloudAudio(
			publicationFile = publicationFile,
			publicationUrl = publicationUrl,
			cachedAudioFiles = cachedFiles,
			sessionLease = ReaderSessionLease.of(sessionDirectory),
			uriByResource = uriByResource
		)
	}

	private fun materializeVersion(
		archive: StorytellerEpubArchive,
		sessionDirectory: File,
		versionDirectory: File,
		resources: List<CachedAudioResource>
	): List<File> {
		val pendingDirectory = Files.createTempDirectory(
			sessionDirectory.toPath(),
			"$CurrentCacheVersion.pending-"
		).toFile()
		return try {
			val pendingFiles = resources.map { resource ->
				val target = File(pendingDirectory, resource.fileName)
				check(archive.copyEntryTo(resource.href, target)) {
					"Storyteller audio entry '${resource.href}' disappeared during extraction."
				}
				check(resource.expectedSize == null || target.length() == resource.expectedSize) {
					"Storyteller audio entry '${resource.href}' was not extracted completely."
				}
				target
			}
			if (versionDirectory.exists()) {
				check(versionDirectory.deleteRecursively()) {
					"Could not replace incomplete Storyteller cache '${versionDirectory.path}'."
				}
			}
			promotePendingDirectory(pendingDirectory, versionDirectory)
			pendingFiles.map { pending -> File(versionDirectory, pending.name) }
		} catch (error: Throwable) {
			pendingDirectory.deleteRecursively()
			throw error
		}
	}

	private fun promotePendingDirectory(pendingDirectory: File, versionDirectory: File) {
		versionDirectory.parentFile?.mkdirs()
		try {
			Files.move(
				pendingDirectory.toPath(),
				versionDirectory.toPath(),
				StandardCopyOption.ATOMIC_MOVE
			)
		} catch (_: AtomicMoveNotSupportedException) {
			Files.move(
				pendingDirectory.toPath(),
				versionDirectory.toPath(),
				StandardCopyOption.REPLACE_EXISTING
			)
		}
	}

	private fun File.hasCompleteAudio(resources: List<CachedAudioResource>): Boolean =
		isDirectory && resources.all { resource ->
			val file = resolve(resource.fileName)
			file.isFile && (resource.expectedSize == null || file.length() == resource.expectedSize)
		}

	private fun removeLegacyCacheAfterSuccessfulMaterialization(sessionDirectory: File) {
		sessionDirectory.listFiles()
			.orEmpty()
			.filterNot { child -> child.name == CurrentCacheVersion }
			.forEach { child -> child.deleteRecursively() }
	}

	private fun cacheFileName(index: Int, resourceHref: String): String {
		val leaf = sanitizeSegment(resourceHref.substringAfterLast('/'))
		return "audio-${(index + 1).toString().padStart(4, '0')}-$leaf"
	}

	private fun sanitizeSegment(value: String): String =
		value
			.replace(Regex("[^A-Za-z0-9._-]+"), "_")
			.trim('_')
			.takeIf { it.isNotBlank() }
			?: "resource"

	private data class CachedAudioResource(
		val href: String,
		val fileName: String,
		val expectedSize: Long?
	)

	private const val CurrentCacheVersion = "v2"
}
