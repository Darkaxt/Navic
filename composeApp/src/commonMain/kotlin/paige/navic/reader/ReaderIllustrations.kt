package paige.navic.reader

/**
 * One Bindery illustration plate (bindery.illustrations.index.v1), resolved to an image already on
 * disk. The reader shows it as a full page before the chapter (Darkaxt/Navic#74) or, for
 * `plate-before-block`, before the block containing [ebookStart] mid-chapter. [ebookStart] is a
 * UTF-8 byte offset in the bindery-epub-text v1 basis (as Whispersync).
 */
data class ReaderIllustrationPlacement(
	val sceneId: String,
	val spineIndex: Int,
	val ebookHref: String,
	val position: String,
	val ebookStart: Int,
	/** Document figures (#82): the first letter of the last block the figure replaces. */
	val endBlock: Int? = null,
	val width: Int,
	val height: Int,
	/** WebView URL of the local image, e.g. https://appassets.androidplatform.net/reader-cache/... */
	val imageUrl: String,
	val sha256: String,
	/** The living loop (#77) when Bindery serves one; its files may still be downloading. */
	val living: ReaderIllustrationLiving? = null,
	/** The Magic-level loop (#79) when Bindery serves one; downloaded only at Magic. */
	val livingAdvanced: ReaderIllustrationLivingAdvanced? = null,
	/**
	 * The animation level plays this plate and its files are on disk: the page draws it greyscale
	 * and the living overlay colours it in, so it arrives grey through the page turn (#77).
	 */
	val livingPlayable: Boolean = false
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
