package paige.navic.reader

/**
 * One Bindery chapter-illustration plate (bindery.illustrations.index.v1), resolved to an image
 * already on disk. The reader shows it as a full page before the chapter (Darkaxt/Navic#74).
 * [ebookStart] is a UTF-8 byte offset in the bindery-epub-text v1 basis (as Whispersync); the
 * agreed contract places plates at 0, before the chapter.
 */
data class ReaderIllustrationPlacement(
	val sceneId: String,
	val spineIndex: Int,
	val ebookHref: String,
	val ebookStart: Int,
	val width: Int,
	val height: Int,
	/** WebView URL of the local image, e.g. https://appassets.androidplatform.net/reader-cache/... */
	val imageUrl: String,
	val sha256: String
)

/**
 * The plates the reader shows for one book. [digest] identifies the index plus the user's
 * "Show illustrations" choice; it keys the pagination and raster caches. An empty set has a
 * stable digest so turning illustrations off also invalidates the caches once.
 */
data class ReaderIllustrationSet(
	val digest: String,
	val placements: List<ReaderIllustrationPlacement>
) {
	companion object {
		const val NoneDigest = "none"
		val None = ReaderIllustrationSet(NoneDigest, emptyList())
	}
}
