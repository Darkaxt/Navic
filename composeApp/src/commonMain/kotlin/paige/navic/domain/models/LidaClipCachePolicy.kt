package paige.navic.domain.models

const val DefaultLidaClipsVideoCacheSizeMb = 512

val LidaClipsVideoCacheSizeOptionsMb = listOf(0, 128, 256, 512, 1024, 2048, 4096)

data class LidaClipCacheFileInfo(
	val path: String,
	val sizeBytes: Long,
	val lastModifiedMillis: Long
)

fun lidaClipVideoCacheSizeBytes(cacheSizeMb: Int): Long =
	cacheSizeMb.coerceAtLeast(0).toLong() * 1024L * 1024L

fun shouldUseCachedLidaClipVideo(cacheSizeMb: Int, cacheFileExists: Boolean): Boolean =
	cacheSizeMb > 0 && cacheFileExists

fun lidaClipsVideoCacheSizeLabel(cacheSizeMb: Int): String =
	when {
		cacheSizeMb <= 0 -> "Off"
		cacheSizeMb >= 1024 && cacheSizeMb % 1024 == 0 -> "${cacheSizeMb / 1024} GB"
		else -> "$cacheSizeMb MB"
	}

fun lidaClipCacheFileExtension(
	mimeType: String?,
	fileName: String?,
	streamUrl: String?
): String =
	listOfNotNull(
		fileName?.safeVideoExtensionFromPath(),
		mimeType?.safeVideoExtensionFromMimeType(),
		streamUrl?.safeVideoExtensionFromPath()
	).firstOrNull() ?: "mp4"

fun lidaClipCachePrunePlan(
	files: List<LidaClipCacheFileInfo>,
	maxSizeBytes: Long,
	protectedPath: String
): List<String> {
	if (files.isEmpty()) return emptyList()

	var remainingBytes = files.sumOf { it.sizeBytes.coerceAtLeast(0L) }
	val pathsToDelete = mutableListOf<String>()
	val normalizedProtectedPath = protectedPath.normalizedCachePath()

	for (file in files.sortedWith(compareBy<LidaClipCacheFileInfo> { it.lastModifiedMillis }.thenBy { it.path })) {
		if (remainingBytes <= maxSizeBytes.coerceAtLeast(0L)) break
		if (file.path.normalizedCachePath() == normalizedProtectedPath) continue

		pathsToDelete += file.path
		remainingBytes -= file.sizeBytes.coerceAtLeast(0L)
	}

	return pathsToDelete
}

private fun String.safeVideoExtensionFromMimeType(): String? =
	when (trim().lowercase()) {
		"video/mp4", "application/mp4" -> "mp4"
		"video/webm" -> "webm"
		"video/x-matroska", "video/matroska" -> "mkv"
		"video/quicktime" -> "mov"
		"video/x-m4v" -> "m4v"
		else -> null
	}

private fun String.safeVideoExtensionFromPath(): String? {
	val path = substringBefore('?').substringBefore('#')
	val extension = path.substringAfterLast('.', missingDelimiterValue = "")
		.trim()
		.lowercase()
	return extension.takeIf { it in SafeLidaClipVideoExtensions }
}

private fun String.normalizedCachePath(): String =
	replace('\\', '/')

private val SafeLidaClipVideoExtensions = setOf("mp4", "m4v", "mov", "webm", "mkv")
