package paige.navic.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString.Companion.toByteString
import paige.navic.domain.repositories.BinderyRepository
import paige.navic.util.core.Logger
import kotlin.coroutines.cancellation.CancellationException

private const val ReaderIllustrationResolverTag = "ReaderIllustrations"

/** How long a book open waits for the plate refresh before using what is already on disk. */
internal const val ReaderIllustrationOpenRefreshWaitMillis = 2_500L

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
 * Builds the [ReaderIllustrationSet] the reader takes once at open. Each open first refreshes the
 * book's plates from Bindery (readiness is re-read, so plates published later appear), waiting at
 * most [ReaderIllustrationOpenRefreshWaitMillis]; a slower or offline refresh keeps running and its
 * plates apply at the next open. `fetchMissing` (the user turning plates on in an open book) waits
 * for the refresh. Failures resolve to what is on disk, or [ReaderIllustrationSet.None].
 *
 * Fixture: when `reader-illustrations/fixtures/<bookId>.json` exists it replaces the cached index
 * (its own `ebookBookFileId` is accepted); images still come from `reader-illustrations/<sha256>.webp`.
 */
class ReaderIllustrationResolver(
	private val repository: BinderyRepository,
	private val storage: ReaderIllustrationStorage?,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
	private val openRefreshWaitMillis: Long = ReaderIllustrationOpenRefreshWaitMillis
) {
	private val refreshLock = Mutex()
	private val refreshes = mutableMapOf<String, Deferred<Int>>()

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
		val refresh = refresh(bookId)
		val stored = if (fetchMissing) refresh?.await() else withTimeoutOrNull(openRefreshWaitMillis) { refresh?.await() }
		val resolved = resolveCached(bookId, ebookBookFileId)
		Logger.i(
			ReaderIllustrationResolverTag,
			"Illustrations resolved book=$bookId file=$ebookBookFileId placements=${resolved.placements.size} " +
				"fetchMissing=$fetchMissing refreshed=${stored != null} stored=${stored ?: 0}"
		)
		return resolved
	}

	/** One refresh per book at a time; it outlives the open that started it, so a slow one still lands. */
	private suspend fun refresh(bookId: String): Deferred<Int>? {
		val store = storage ?: return null
		val key = bookId.trim().takeIf { it.isNotEmpty() } ?: return null
		return refreshLock.withLock {
			refreshes[key]?.takeIf { it.isActive } ?: scope.async {
				prefetchReaderIllustrations(repository, store, key, forceRefresh = true)
			}.also { refreshes[key] = it }
		}
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
				position = placement.position,
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
	bookId: String,
	/** Re-read readiness from Bindery instead of trusting a fresh cache (plates may have been published since). */
	forceRefresh: Boolean = false
): Int {
	val numericBookId = bookId.trim().toLongOrNull()?.takeIf { it > 0L } ?: return 0
	var stored = 0
	try {
		val readiness = repository.getIllustrationsReadiness(numericBookId, forceRefresh).getOrElse { return 0 }
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
