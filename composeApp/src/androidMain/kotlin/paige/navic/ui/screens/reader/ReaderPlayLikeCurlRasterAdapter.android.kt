package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPageBitmapQuality
import kotlin.coroutines.resume

internal data class ReaderPlayLikeCurlRasterProfile(
	val sourceIdentity: String,
	val orientation: ReaderPlayLikeCurlOrientation,
	val quality: ReaderPageBitmapQuality,
	val pageCount: Int = 1,
	val readerDirection: ReaderPlayLikeCurlReaderDirection = ReaderPlayLikeCurlReaderDirection.Ltr,
	val spreadAnchorParity: Int = 0,
	val rasterGeneration: Long = 0L
)

internal fun interface ReaderPlayLikeCurlRasterPublicationFence {
	fun isCurrent(): Boolean
}

private val AlwaysCurrentReaderPlayLikeCurlRasterPublicationFence =
	ReaderPlayLikeCurlRasterPublicationFence { true }

internal data class ReaderPlayLikeCurlRasterKey(
	val profile: ReaderPlayLikeCurlRasterProfile,
	val pageIndex: Int,
	val publicationFence: ReaderPlayLikeCurlRasterPublicationFence =
		AlwaysCurrentReaderPlayLikeCurlRasterPublicationFence
)

private data class ReaderPlayLikeCurlRasterCacheKey(
	val profile: ReaderPlayLikeCurlRasterProfile,
	val pageIndex: Int
)

private val ReaderPlayLikeCurlRasterKey.cacheKey: ReaderPlayLikeCurlRasterCacheKey
	get() = ReaderPlayLikeCurlRasterCacheKey(profile, pageIndex)

internal data class ReaderPlayLikeCurlRasterProgress(
	val completed: Int,
	val total: Int
)

internal fun interface ReaderPlayLikeCurlRasterLoader<T : Any> {
	suspend fun load(key: ReaderPlayLikeCurlRasterKey): T?
}

internal class ReaderPlayLikeCurlRasterDeck<T : Any> internal constructor(
	val profile: ReaderPlayLikeCurlRasterProfile,
	private val values: Map<Int, T>,
	private val releaseOwnership: () -> Unit
) : AutoCloseable {
	private var closed = false

	val pageIndices: Set<Int>
		get() = values.keys

	fun value(pageIndex: Int): T? = values[pageIndex]

	@Synchronized
	override fun close() {
		if (closed) return
		closed = true
		releaseOwnership()
	}
}

/**
 * Asynchronously materializes immutable page rasters while keeping ownership out of the GL renderer.
 * A profile change invalidates obsolete work without cancelling the loader that owns its cleanup.
 */
internal class ReaderPlayLikeCurlRasterAdapter<T : Any>(
	private val scope: CoroutineScope,
	private val loader: ReaderPlayLikeCurlRasterLoader<T>,
	private val publicationDispatcher: CoroutineDispatcher,
	private val release: (T) -> Unit
) : AutoCloseable {
	constructor(
		scope: CoroutineScope,
		loader: ReaderPlayLikeCurlRasterLoader<T>,
		release: (T) -> Unit
	) : this(scope, loader, Dispatchers.Default, release)
	private class CacheEntry<T : Any>(val value: T) {
		var cacheOwned = true
		var retainCount = 0
		var released = false
	}

	private class InFlight<T : Any>(
		val generation: Long,
		val result: CompletableDeferred<CacheEntry<T>?>
	)

	private val lock = Any()
	private val cache = mutableMapOf<ReaderPlayLikeCurlRasterCacheKey, CacheEntry<T>>()
	private val inFlight = mutableMapOf<ReaderPlayLikeCurlRasterKey, InFlight<T>>()
	private var activeProfile: ReaderPlayLikeCurlRasterProfile? = null
	private var generation = 0L
	private var closed = false

	fun prepare(
		profile: ReaderPlayLikeCurlRasterProfile,
		pageIndices: List<Int>,
		onProgress: (ReaderPlayLikeCurlRasterProgress) -> Unit
	): Deferred<ReaderPlayLikeCurlRasterDeck<T>?> = prepare(
		profile = profile,
		pageIndices = pageIndices,
		publicationFence = AlwaysCurrentReaderPlayLikeCurlRasterPublicationFence,
		onProgress = onProgress
	)

	fun prepare(
		profile: ReaderPlayLikeCurlRasterProfile,
		pageIndices: List<Int>,
		publicationFence: ReaderPlayLikeCurlRasterPublicationFence =
			AlwaysCurrentReaderPlayLikeCurlRasterPublicationFence,
		onProgress: (ReaderPlayLikeCurlRasterProgress) -> Unit = {}
	): Deferred<ReaderPlayLikeCurlRasterDeck<T>?> {
		val obsolete = mutableListOf<T>()
		val staleWaiters = mutableListOf<CompletableDeferred<CacheEntry<T>?>>()
		val preparationGeneration = synchronized(lock) {
			if (closed) return completedNullDeck()
			if (activeProfile != profile) {
				generation += 1L
				activeProfile = profile
				inFlight.values
					.filter { work -> work.generation != generation }
					.mapTo(staleWaiters) { work -> work.result }
				releaseCacheOwnershipLocked(obsolete)
				cache.clear()
			}
			generation
		}
		staleWaiters.forEach { waiter -> waiter.complete(null) }
		obsolete.forEach(release)

		val uniquePageIndices = pageIndices.distinct()
		return scope.async {
			onProgress(ReaderPlayLikeCurlRasterProgress(completed = 0, total = uniquePageIndices.size))
			val loaded = LinkedHashMap<ReaderPlayLikeCurlRasterKey, CacheEntry<T>>(uniquePageIndices.size)
			for ((position, pageIndex) in uniquePageIndices.withIndex()) {
				val key = ReaderPlayLikeCurlRasterKey(
					profile = profile,
					pageIndex = pageIndex,
					publicationFence = publicationFence
				)
				val entry = loadEntry(key, preparationGeneration) ?: return@async null
				loaded[key] = entry
				onProgress(
					ReaderPlayLikeCurlRasterProgress(
						completed = position + 1,
						total = uniquePageIndices.size
					)
				)
			}

			return@async retainDeck(
				profile = profile,
				preparationGeneration = preparationGeneration,
				publicationFence = publicationFence,
				loaded = loaded
			)
		}
	}

	private suspend fun retainDeck(
		profile: ReaderPlayLikeCurlRasterProfile,
		preparationGeneration: Long,
		publicationFence: ReaderPlayLikeCurlRasterPublicationFence,
		loaded: Map<ReaderPlayLikeCurlRasterKey, CacheEntry<T>>
	): ReaderPlayLikeCurlRasterDeck<T>? = withContext(publicationDispatcher) {
		suspendCancellableCoroutine { continuation ->
			val retainedEntries = synchronized(lock) {
				if (
					closed ||
					generation != preparationGeneration ||
					activeProfile != profile ||
					!runCatching(publicationFence::isCurrent).getOrDefault(false)
				) {
					return@synchronized null
				}
				loaded.values.distinct().onEach { entry -> entry.retainCount += 1 }
			}
			val deck = retainedEntries?.let { retained ->
				ReaderPlayLikeCurlRasterDeck(
					profile = profile,
					values = loaded.mapKeys { entry -> entry.key.pageIndex }
						.mapValues { entry -> entry.value.value },
					releaseOwnership = { releaseDeckEntries(retained) }
				)
			}
			continuation.resume(
				deck,
				onCancellation = { _, undelivered, _ -> undelivered?.close() }
			)
		}
	}

	fun hasDecoded(
		profile: ReaderPlayLikeCurlRasterProfile,
		pageIndex: Int
	): Boolean = synchronized(lock) {
		if (closed || activeProfile != profile) return@synchronized false
		val entry = cache[ReaderPlayLikeCurlRasterCacheKey(profile, pageIndex)]
		entry != null && !entry.released
	}

	private fun completedNullDeck(): Deferred<ReaderPlayLikeCurlRasterDeck<T>?> =
		CompletableDeferred<ReaderPlayLikeCurlRasterDeck<T>?>().also { result -> result.complete(null) }

	private suspend fun loadEntry(
		key: ReaderPlayLikeCurlRasterKey,
		preparationGeneration: Long
	): CacheEntry<T>? {
		val work = synchronized(lock) {
			if (closed || generation != preparationGeneration || activeProfile != key.profile) return null
			cache[key.cacheKey]?.let { entry -> return entry }
			inFlight[key]?.takeIf { active -> active.generation == preparationGeneration }?.let { active ->
				return@synchronized active
			}
			InFlight<T>(preparationGeneration, CompletableDeferred()).also { created ->
				inFlight[key] = created
				scope.launch { materialize(key, created) }
			}
		}
		return work.result.await()
	}

	private suspend fun materialize(key: ReaderPlayLikeCurlRasterKey, work: InFlight<T>) {
		val value = runCatching { loader.load(key) }.getOrNull()
		var staleValue: T? = null
		val published = withContext(NonCancellable + publicationDispatcher) {
			synchronized(lock) {
				inFlight[key]?.takeIf { active -> active === work }?.let { inFlight.remove(key) }
				if (
					value != null &&
					!closed &&
					generation == work.generation &&
					activeProfile == key.profile &&
					runCatching(key.publicationFence::isCurrent).getOrDefault(false)
				) {
					cache[key.cacheKey]?.also {
						staleValue = value
					} ?: CacheEntry(value).also { entry ->
						cache[key.cacheKey] = entry
					}
				} else {
					staleValue = value
					null
				}
			}
		}
		staleValue?.let(release)
		work.result.complete(published)
	}

	private fun releaseDeckEntries(entries: List<CacheEntry<T>>) {
		val releasedValues = mutableListOf<T>()
		synchronized(lock) {
			entries.forEach { entry ->
				check(entry.retainCount > 0) { "PlayLikeCurl raster deck released without ownership" }
				entry.retainCount -= 1
				entry.releaseIfUnownedLocked(releasedValues)
			}
		}
		releasedValues.forEach(release)
	}

	private fun releaseCacheOwnershipLocked(releasedValues: MutableList<T>) {
		cache.values.distinct().forEach { entry ->
			entry.cacheOwned = false
			entry.releaseIfUnownedLocked(releasedValues)
		}
	}

	private fun CacheEntry<T>.releaseIfUnownedLocked(releasedValues: MutableList<T>) {
		if (cacheOwned || retainCount > 0 || released) return
		released = true
		releasedValues += value
	}

	override fun close() {
		val obsolete = mutableListOf<T>()
		val staleWaiters = mutableListOf<CompletableDeferred<CacheEntry<T>?>>()
		synchronized(lock) {
			if (closed) return
			closed = true
			generation += 1L
			activeProfile = null
			inFlight.values.mapTo(staleWaiters) { work -> work.result }
			releaseCacheOwnershipLocked(obsolete)
			cache.clear()
		}
		staleWaiters.forEach { waiter -> waiter.complete(null) }
		obsolete.forEach(release)
	}
}
