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

	/** Absolute path of a stored living-plate file (`mp4` loop or `png` reveal map), or null when absent (#77). */
	fun livingFilePath(sha256: String, extension: String): String? = null

	/** Writes a verified living-plate file atomically. */
	suspend fun writeLiving(sha256: String, extension: String, bytes: ByteArray) {}

	/** Usable free space for living plates; [Long.MAX_VALUE] when unknown. */
	fun freeSpaceBytes(): Long = Long.MAX_VALUE
}

/** Living plates (~12 MB each) are skipped when they would leave less than this free. */
internal const val ReaderIllustrationLivingFreeSpaceReserveBytes = 1_024L * 1_024L * 1_024L

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
	private val livingDownloads = mutableMapOf<String, Deferred<Int>>()

	/**
	 * @param bookId the Bindery book id (`Screen.Reader.bookId`).
	 * @param ebookBookFileId the opened ebook file; null uses the book's only ready file.
	 * @param enabled the effective "Show illustrations" setting (`ReaderSettings.illustrationsEnabled != false`).
	 */
	suspend fun resolve(
		bookId: String,
		ebookBookFileId: Long?,
		enabled: Boolean,
		fetchMissing: Boolean = false,
		/** The animation level allows living plates (#79 Moderate or Magic): download their loops after the plates. */
		living: Boolean = false,
		/** The Magic level (#79): also download the video-model loops. */
		advanced: Boolean = false
	): ReaderIllustrationSet {
		if (!enabled) return ReaderIllustrationSet.None
		val refresh = refresh(bookId, living, advanced)
		val stored = if (fetchMissing) refresh?.await() else withTimeoutOrNull(openRefreshWaitMillis) { refresh?.await() }
		val resolved = resolveCached(bookId, ebookBookFileId, living, advanced)
		Logger.i(
			ReaderIllustrationResolverTag,
			"Illustrations resolved book=$bookId file=$ebookBookFileId placements=${resolved.placements.size} " +
				"fetchMissing=$fetchMissing refreshed=${stored != null} stored=${stored ?: 0}"
		)
		return resolved
	}

	/** One refresh per book at a time; it outlives the open that started it, so a slow one still lands. */
	private suspend fun refresh(bookId: String, living: Boolean, advanced: Boolean): Deferred<Int>? {
		val store = storage ?: return null
		val key = bookId.trim().takeIf { it.isNotEmpty() } ?: return null
		return refreshLock.withLock {
			val refresh = refreshes[key]?.takeIf { it.isActive } ?: scope.async {
				prefetchReaderIllustrations(repository, store, key, forceRefresh = true)
			}.also { refreshes[key] = it }
			// Living loops are large: they follow the plates in the background and never hold an open.
			val livingKey = if (advanced) "$key:advanced" else key
			if (living && livingDownloads[livingKey]?.isActive != true) {
				livingDownloads[livingKey] = scope.async {
					runCatching { refresh.await() }
					prefetchReaderIllustrationLiving(repository, store, key, advanced)
				}
			}
			refresh
		}
	}

	private suspend fun resolveCached(
		bookId: String,
		ebookBookFileId: Long?,
		living: Boolean,
		advanced: Boolean
	): ReaderIllustrationSet {
		val store = storage ?: return ReaderIllustrationSet.None
		val numericBookId = bookId.trim().toLongOrNull()?.takeIf { it > 0L } ?: return ReaderIllustrationSet.None
		return try {
			val index = store.readFixtureIndexJson(numericBookId)
				?.let { json -> decodeReaderIllustrationIndex(json, numericBookId, expectedEbookBookFileId = null) }
				?: repository.getCachedIllustrationIndex(numericBookId, ebookBookFileId).getOrNull()
				?: return ReaderIllustrationSet.None
			readerIllustrationSet(index, store, living, advanced)
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
	storage: ReaderIllustrationStorage,
	living: Boolean = false,
	advanced: Boolean = false
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
				sha256 = placement.sha256,
				living = placement.living,
				livingAdvanced = placement.livingAdvanced,
				livingPlayable = livingPlayable(placement, storage, living, advanced)
			)
		}
	return if (placements.isEmpty()) ReaderIllustrationSet.None else ReaderIllustrationSet(index.digest, placements)
}

/** The native overlay's own rule: an advanced loop at Magic, else the warp loop with its reveal map. */
private fun livingPlayable(
	placement: ReaderIllustrationIndexPlacement,
	storage: ReaderIllustrationStorage,
	living: Boolean,
	advanced: Boolean
): Boolean {
	if (!living) return false
	val advancedLoop = placement.livingAdvanced?.takeIf { advanced }
	if (advancedLoop != null && storage.livingFilePath(advancedLoop.loopSha256, "mp4") != null) return true
	val warp = placement.living ?: return false
	return storage.livingFilePath(warp.loopSha256, "mp4") != null &&
		storage.livingFilePath(warp.revealSha256, "png") != null
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

/**
 * Downloads the living-plate loops and reveal maps (#77) of every ready file of a book that are not
 * on disk yet, verifying each sha256. Stops when the next file would leave less than
 * [ReaderIllustrationLivingFreeSpaceReserveBytes] free. Returns the number of files newly stored.
 */
suspend fun prefetchReaderIllustrationLiving(
	repository: BinderyRepository,
	storage: ReaderIllustrationStorage,
	bookId: String,
	/** Also the Magic-level `livingAdvanced` loops (#79). */
	advanced: Boolean = false
): Int {
	val numericBookId = bookId.trim().toLongOrNull()?.takeIf { it > 0L } ?: return 0
	var stored = 0
	try {
		val readiness = repository.getIllustrationsReadiness(numericBookId, false).getOrElse { return 0 }
		val wanted = linkedMapOf<String, Pair<String, Long?>>()
		for (file in readiness.readyFiles) {
			val index = repository.getIllustrationIndex(numericBookId, file).getOrNull() ?: continue
			for (living in index.placements.mapNotNull { it.living }) {
				wanted.getOrPut("${living.revealSha256}.png") { living.revealSha256 to living.revealBytes }
				wanted.getOrPut("${living.loopSha256}.mp4") { living.loopSha256 to living.loopBytes }
			}
			if (advanced) for (loop in index.placements.mapNotNull { it.livingAdvanced }) {
				wanted.getOrPut("${loop.loopSha256}.mp4") { loop.loopSha256 to loop.loopBytes }
			}
		}
		for ((name, entry) in wanted) {
			val (sha256, expectedBytes) = entry
			val extension = name.substringAfterLast('.')
			if (storage.livingFilePath(sha256, extension) != null) continue
			if (storage.freeSpaceBytes() - (expectedBytes ?: 0L) < ReaderIllustrationLivingFreeSpaceReserveBytes) {
				Logger.w(ReaderIllustrationResolverTag, "Living plates stopped for low storage book=$numericBookId")
				break
			}
			val bytes = repository.getIllustrationLivingBytes(numericBookId, sha256, extension).getOrNull() ?: continue
			if (bytes.isEmpty() || bytes.readerIllustrationSha256() != sha256) {
				Logger.w(ReaderIllustrationResolverTag, "Living plate hash mismatch book=$numericBookId")
				continue
			}
			storage.writeLiving(sha256, extension, bytes)
			stored += 1
		}
		Logger.i(ReaderIllustrationResolverTag, "Living plates book=$numericBookId wanted=${wanted.size} stored=$stored")
	} catch (cancellation: CancellationException) {
		throw cancellation
	} catch (error: Throwable) {
		Logger.w(ReaderIllustrationResolverTag, "Living plates prefetch failed book=$numericBookId", error)
	}
	return stored
}
