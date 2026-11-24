package paige.navic.reader

import okio.ByteString.Companion.toByteString
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.util.core.Logger
import kotlin.coroutines.cancellation.CancellationException

private const val ReaderIllustrationResolverTag = "ReaderIllustrations"

/**
 * On-disk illustration images, kept permanently like downloaded EPUBs. On Android the files live in
 * `<reader managed root>/reader-illustrations/<sha256>.webp`, served to the WebView under
 * `/reader-cache/reader-illustrations/`.
 */
interface ReaderIllustrationStorage {
	/** Size of the stored image, or 0 when it is absent. */
	fun imageSizeBytes(sha256: String): Long

	/** Writes the (already verified) image atomically; a torn write never becomes the image. */
	suspend fun writeImage(sha256: String, bytes: ByteArray)

	/**
	 * Test fixture for emulator work before the Bindery route exists:
	 * `<managed root>/reader-illustrations/fixtures/<bindery book id>.json` in the
	 * bindery.illustrations.index.v1 schema. Returns null when absent.
	 */
	fun readFixtureIndexJson(bookId: Long): String?

	/** WebView URL of a stored image. */
	fun imageUrl(sha256: String): String
}

internal fun ByteArray.readerIllustrationSha256(): String = toByteString().sha256().hex()

/**
 * Builds the [ReaderIllustrationSet] the reader takes once at open. It only reads (cached index,
 * fixture, files on disk); only `fetchMissing` (the user turning plates on in an open book) fetches
 * the book's plates first. Failures resolve to [ReaderIllustrationSet.None].
 *
 * Fixture: when `reader-illustrations/fixtures/<bookId>.json` exists it replaces the cached index
 * (its own `ebookBookFileId` is accepted); images still come from `reader-illustrations/<sha256>.webp`.
 */
class ReaderIllustrationResolver(
	private val repository: BinderyRepository,
	private val storage: ReaderIllustrationStorage?
) {
	/**
	 * @param bookId the Bindery book id (`Screen.Reader.bookId`).
	 * @param ebookBookFileId the opened ebook file; null uses the book's only ready file.
	 * @param enabled the effective "Show illustrations" setting (`ReaderSettings.illustrationsEnabled != false`).
	 */
	suspend fun resolve(
		bookId: String,
		ebookBookFileId: Long?,
		enabled: Boolean,
		fetchMissing: Boolean = false
	): ReaderIllustrationSet {
		if (!enabled) return ReaderIllustrationSet.None
		val cached = resolveCached(bookId, ebookBookFileId)
		Logger.i(
			ReaderIllustrationResolverTag,
			"Illustrations resolved book=$bookId file=$ebookBookFileId placements=${cached.placements.size} fetchMissing=$fetchMissing"
		)
		if (cached != ReaderIllustrationSet.None || !fetchMissing) return cached
		// Turned on in an open book whose plates were never fetched: fetch them once, then resolve.
		val store = storage ?: return cached
		val stored = prefetchReaderIllustrations(repository, store, bookId)
		val fetched = resolveCached(bookId, ebookBookFileId)
		Logger.i(
			ReaderIllustrationResolverTag,
			"Illustrations fetched on enable book=$bookId stored=$stored placements=${fetched.placements.size}"
		)
		return fetched
	}

	private suspend fun resolveCached(bookId: String, ebookBookFileId: Long?): ReaderIllustrationSet {
		val store = storage ?: return ReaderIllustrationSet.None
		val numericBookId = bookId.trim().toLongOrNull()?.takeIf { it > 0L } ?: return ReaderIllustrationSet.None
		return try {
			val index = store.readFixtureIndexJson(numericBookId)
				?.let { json -> decodeReaderIllustrationIndex(json, numericBookId, expectedEbookBookFileId = null) }
				?: repository.getCachedIllustrationIndex(numericBookId, ebookBookFileId).getOrNull()
				?: return ReaderIllustrationSet.None
			readerIllustrationSet(index, store)
		} catch (cancellation: CancellationException) {
			throw cancellation
		} catch (error: Throwable) {
			Logger.w(ReaderIllustrationResolverTag, "Illustrations unavailable book=$numericBookId", error)
			ReaderIllustrationSet.None
		}
	}
}

/** Keeps only placements whose image is on disk; no valid placement means [ReaderIllustrationSet.None]. */
internal fun readerIllustrationSet(
	index: ReaderIllustrationIndex,
	storage: ReaderIllustrationStorage
): ReaderIllustrationSet {
	val placements = index.placements
		.filter { placement -> storage.imageSizeBytes(placement.sha256) > 0L }
		.map { placement ->
			ReaderIllustrationPlacement(
				sceneId = placement.sceneId,
				spineIndex = placement.spineIndex,
				ebookHref = placement.ebookHref,
				ebookStart = placement.ebookStart,
				width = placement.width,
				height = placement.height,
				imageUrl = storage.imageUrl(placement.sha256),
				sha256 = placement.sha256
			)
		}
	return if (placements.isEmpty()) ReaderIllustrationSet.None else ReaderIllustrationSet(index.digest, placements)
}

/**
 * Downloads every ready file's index and missing images for a book (best-effort, after WordSync).
 * Returns the number of images newly stored; any failure is logged and stops only this book's plates.
 */
suspend fun prefetchReaderIllustrations(
	repository: BinderyRepository,
	storage: ReaderIllustrationStorage,
	bookId: String
): Int {
	val numericBookId = bookId.trim().toLongOrNull()?.takeIf { it > 0L } ?: return 0
	var stored = 0
	try {
		val readiness = repository.getIllustrationsReadiness(numericBookId).getOrElse { return 0 }
		for (file in readiness.readyFiles) {
			val index = repository.getIllustrationIndex(numericBookId, file).getOrElse { error ->
				Logger.i(ReaderIllustrationResolverTag, "Illustrations index skipped book=$numericBookId error=${error::class.simpleName}")
				null
			} ?: continue
			for (sha256 in index.placements.map { it.sha256 }.distinct()) {
				if (storage.imageSizeBytes(sha256) > 0L) continue
				val bytes = repository.getIllustrationImageBytes(numericBookId, sha256).getOrNull() ?: continue
				if (bytes.isEmpty() || bytes.readerIllustrationSha256() != sha256) {
					Logger.w(ReaderIllustrationResolverTag, "Illustration image hash mismatch book=$numericBookId")
					continue
				}
				storage.writeImage(sha256, bytes)
				stored += 1
			}
		}
	} catch (cancellation: CancellationException) {
		throw cancellation
	} catch (error: Throwable) {
		Logger.w(ReaderIllustrationResolverTag, "Illustrations prefetch failed book=$numericBookId", error)
	}
	return stored
}
