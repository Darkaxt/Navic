package paige.navic.domain.models

const val DefaultStreamedSongCacheSizeMb = 2048

val StreamedSongCacheSizeOptionsMb = listOf(512, 1024, 2048, 5120, 10240)

const val DefaultImageCacheSizeMb = 2048

val ImageCacheSizeOptionsMb = listOf(256, 512, 1024, 2048, 4096)

fun cacheSizeMbToBytes(cacheSizeMb: Int): Long =
	cacheSizeMb.coerceAtLeast(0).toLong() * 1024L * 1024L

fun cacheSizeLimitLabel(cacheSizeMb: Int): String =
	lidaClipsVideoCacheSizeLabel(cacheSizeMb)
