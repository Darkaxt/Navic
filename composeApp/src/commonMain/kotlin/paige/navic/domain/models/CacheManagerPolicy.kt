package paige.navic.domain.models

const val DefaultStreamedSongCacheSizeMb = 2048

val StreamedSongCacheSizeOptionsMb = listOf(512, 1024, 2048, 5120, 10240)

const val DefaultImageCacheSizeMb = 2048

val ImageCacheSizeOptionsMb = listOf(256, 512, 1024, 2048, 4096)

/** Page previews of opened books (the reader's page raster cache). Downloaded books are removed when closed. */
const val DefaultPagePreviewCacheSizeMb = 384

val PagePreviewCacheSizeOptionsMb = listOf(128, 256, 384, 512, 1024)

/** MusicBrainz results are small text records, so the limit is a song count. */
const val DefaultMusicBrainzCacheMaxSongs = 500

val MusicBrainzCacheMaxSongsOptions = listOf(250, 500, 1000, 2000)

fun cacheSizeMbToBytes(cacheSizeMb: Int): Long =
	cacheSizeMb.coerceAtLeast(0).toLong() * 1024L * 1024L

fun cacheSizeLimitLabel(cacheSizeMb: Int): String =
	lidaClipsVideoCacheSizeLabel(cacheSizeMb)
