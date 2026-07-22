package paige.navic.ui.screens.reader

import java.io.File
import java.util.PriorityQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPageRasterPriority

internal enum class ReaderPageRasterScheduleStatus {
	Cached,
	Published,
	Stale,
	Failed
}

internal data class ReaderPageRasterScheduleResult(
	val key: ReaderPageRasterKey,
	val status: ReaderPageRasterScheduleStatus
)

internal data class ReaderPageRasterGeneration<T : Any>(
	val metadata: ReaderPageRasterMetadata,
	val value: T,
	val captureMillis: Long,
	val readDecodeMillis: Long = 0L,
	val gpuUploadMillis: Long = 0L
)

internal fun interface ReaderPageRasterGenerator<T : Any> {
	suspend fun generate(key: ReaderPageRasterKey): ReaderPageRasterGeneration<T>?
}

internal enum class ReaderPageRasterWriteMode {
	AdoptDecoded,
	PersistOnly
}

internal enum class ReaderPageRasterValueOwnership {
	Store,
	Caller
}

internal data class ReaderPageRasterWriteReceipt(
	val key: ReaderPageRasterKey,
	val rasterFileName: String,
	val inProcessRevision: Long
)

internal enum class ReaderPageRasterWriteFailureReason {
	EncodeIdentityReleasing
}

internal data class ReaderPageRasterWriteResult(
	val persisted: Boolean,
	val ownership: ReaderPageRasterValueOwnership,
	val receipt: ReaderPageRasterWriteReceipt? = null,
	val failureReason: ReaderPageRasterWriteFailureReason? = null
)

internal fun interface ReaderPageRasterCommitFence {
	fun commit(
		action: () -> ReaderPageRasterWriteResult
	): ReaderPageRasterWriteResult
}

internal interface ReaderPageRasterStore<T : Any> {
	fun contains(key: ReaderPageRasterKey): Boolean
	fun <R : Any> readCopy(
		key: ReaderPageRasterKey,
		copy: (T) -> R?
	): ReaderPageRaster<R>?
	fun write(
		key: ReaderPageRasterKey,
		metadata: ReaderPageRasterMetadata,
		value: T
	): ReaderPageRasterWriteResult
	fun remove(key: ReaderPageRasterKey): Boolean
	fun rollbackPublication(receipt: ReaderPageRasterWriteReceipt): Boolean
	fun retainProfile(profile: ReaderPageRasterProfile): Int
	fun protectChapter(chapter: ReaderPageRasterChapterKey?)
	fun encodedBytes(key: ReaderPageRasterKey): Long
}

internal class ReaderPageRasterCacheStore<T : Any>(
	private val cache: ReaderPageRasterCache<T>
) : ReaderPageRasterStore<T>, AutoCloseable {
	private val lock = Any()
	private var activeOperations = 0
	private var closed = false

	private inline fun <R> withOpen(
		closedResult: R,
		action: () -> R
	): R {
		val admitted = synchronized(lock) {
			if (closed) false
			else {
				activeOperations += 1
				true
			}
		}
		if (!admitted) return closedResult
		return try {
			action()
		} finally {
			synchronized(lock) {
				check(activeOperations > 0)
				activeOperations -= 1
			}
		}
	}

	override fun contains(key: ReaderPageRasterKey): Boolean =
		withOpen(false) { cache.contains(key) }

	override fun <R : Any> readCopy(
		key: ReaderPageRasterKey,
		copy: (T) -> R?
	): ReaderPageRaster<R>? =
		withOpen<ReaderPageRaster<R>?>(null) {
			cache.readCopy(key, copy)
		}

	override fun write(
		key: ReaderPageRasterKey,
		metadata: ReaderPageRasterMetadata,
		value: T
	): ReaderPageRasterWriteResult = withOpen(
		ReaderPageRasterWriteResult(
			persisted = false,
			ownership = ReaderPageRasterValueOwnership.Caller
		)
	) {
		cache.write(
			key,
			metadata,
			value,
			ReaderPageRasterWriteMode.AdoptDecoded
		)
	}

	fun writePublication(
		key: ReaderPageRasterKey,
		metadata: ReaderPageRasterMetadata,
		value: T,
		commitFence: ReaderPageRasterCommitFence
	): ReaderPageRasterWriteResult = withOpen(
		ReaderPageRasterWriteResult(
			persisted = false,
			ownership = ReaderPageRasterValueOwnership.Caller
		)
	) {
		cache.write(
			key = key,
			metadata = metadata,
			value = value,
			mode = ReaderPageRasterWriteMode.PersistOnly,
			commitFence = commitFence
		)
	}

	override fun rollbackPublication(
		receipt: ReaderPageRasterWriteReceipt
	): Boolean = withOpen(false) {
		cache.rollbackPublication(receipt)
	}

	override fun remove(key: ReaderPageRasterKey): Boolean =
		withOpen(false) { cache.remove(key) }

	override fun retainProfile(profile: ReaderPageRasterProfile): Int =
		withOpen(0) { cache.retainProfile(profile) }

	override fun protectChapter(chapter: ReaderPageRasterChapterKey?) {
		withOpen(Unit) { cache.protectChapter(chapter) }
	}

	override fun encodedBytes(key: ReaderPageRasterKey): Long =
		withOpen(0L) {
			cache.pathFor(key).takeIf(File::isFile)?.length() ?: 0L
		}

	override fun close() {
		synchronized(lock) {
			if (closed) return
			check(activeOperations == 0) {
				"Persistent raster store closed with active operations"
			}
			closed = true
		}
	}
}

internal class ReaderPageRasterScheduler<T : Any>(
	private val scope: CoroutineScope,
	private val store: ReaderPageRasterStore<T>,
	private val generator: ReaderPageRasterGenerator<T>,
	private val release: (T) -> Unit,
	private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
	private val nanoTime: () -> Long = System::nanoTime
) {
	private class Work<T : Any>(
		val key: ReaderPageRasterKey,
		var priority: ReaderPageRasterPriority,
		val sequence: Long,
		val profileGeneration: Long,
		val result: CompletableDeferred<ReaderPageRasterScheduleResult>
	)

	private val lock = Any()
	private val wakeups = Channel<Unit>(capacity = 1)
	private val queue = PriorityQueue<Work<T>>(
		compareBy<Work<T>> { work -> work.priority.rank }.thenBy { work -> work.sequence }
	)
	private val pending = mutableMapOf<String, Work<T>>()
	private var activeProfile: ReaderPageRasterProfile? = null
	private var activeProfileGeneration = 0L
	private var nextSequence = 0L
	private var profilePendingRetention: ReaderPageRasterProfile? = null
	private var retainedDispatchFailure: Throwable? = null
	private var closed = false

	init {
		scope.launch {
			for (signal in wakeups) drain()
		}
	}

	fun activateProfile(profile: ReaderPageRasterProfile) {
		val stale = synchronized(lock) {
			if (closed) return
			if (activeProfile == profile) return
			activeProfile = profile
			activeProfileGeneration += 1L
			profilePendingRetention = profile
			val obsolete = queue.filter { work -> work.key.profile != profile }
			queue.removeAll(obsolete.toSet())
			obsolete.forEach { work -> pending.remove(work.key.digest) }
			obsolete
		}
		stale.forEach { work ->
			work.result.complete(ReaderPageRasterScheduleResult(work.key, ReaderPageRasterScheduleStatus.Stale))
		}
		wakeups.trySend(Unit)
	}

	fun request(
		key: ReaderPageRasterKey,
		priority: ReaderPageRasterPriority
	): Deferred<ReaderPageRasterScheduleResult> = synchronized(lock) {
		if (closed) {
			return@synchronized CompletableDeferred(
				ReaderPageRasterScheduleResult(key, ReaderPageRasterScheduleStatus.Stale)
			)
		}
		pending[key.digest]?.takeIf { work -> work.key.identity == key.identity }?.let { work ->
			if (priority.rank < work.priority.rank && queue.remove(work)) {
				work.priority = priority
				queue.add(work)
			}
			return@synchronized work.result
		}
		if (activeProfile != key.profile) {
			return@synchronized CompletableDeferred(
				ReaderPageRasterScheduleResult(key, ReaderPageRasterScheduleStatus.Stale)
			)
		}
		val work = Work<T>(
			key = key,
			priority = priority,
			sequence = nextSequence++,
			profileGeneration = activeProfileGeneration,
			result = CompletableDeferred()
		)
		pending[key.digest] = work
		queue.add(work)
		wakeups.trySend(Unit)
		work.result
	}

	fun close() {
		val stale = synchronized(lock) {
			if (closed) return
			closed = true
			activeProfile = null
			activeProfileGeneration += 1L
			profilePendingRetention = null
			queue.toList().also {
				queue.clear()
				pending.clear()
			}
		}
		stale.forEach { work ->
			work.result.complete(ReaderPageRasterScheduleResult(work.key, ReaderPageRasterScheduleStatus.Stale))
		}
		wakeups.close()
	}

	fun dispatchFailure(): Throwable? = synchronized(lock) {
		retainedDispatchFailure
	}

	private fun recordFailure(failure: Throwable) {
		synchronized(lock) {
			val first = retainedDispatchFailure
			if (first == null) retainedDispatchFailure = failure
			else if (failure !== first) first.addSuppressed(failure)
		}
	}

	suspend fun protectChapter(chapter: ReaderPageRasterChapterKey?) {
		withContext(ioDispatcher) { store.protectChapter(chapter) }
	}

	private suspend fun drain() {
		while (true) {
			val retention = synchronized(lock) {
				profilePendingRetention.also { profilePendingRetention = null }
			}
			if (retention != null) withContext(ioDispatcher) { store.retainProfile(retention) }
			val work = synchronized(lock) { queue.poll() } ?: return
			process(work)
		}
	}

	private suspend fun process(work: Work<T>) {
		if (withContext(ioDispatcher) { store.contains(work.key) }) {
			complete(work, ReaderPageRasterScheduleStatus.Cached)
			return
		}
		if (!isCurrent(work)) {
			complete(work, ReaderPageRasterScheduleStatus.Stale)
			return
		}

		val generated = runCatching { generator.generate(work.key) }.getOrNull()
		if (generated == null) {
			complete(work, ReaderPageRasterScheduleStatus.Failed)
			return
		}
		if (!isCurrent(work)) {
			release(generated.value)
			complete(work, ReaderPageRasterScheduleStatus.Stale)
			return
		}

		var write = ReaderPageRasterWriteResult(
			persisted = false,
			ownership = ReaderPageRasterValueOwnership.Caller
		)
		var writeFailure: Throwable? = null
		try {
			withContext(NonCancellable + ioDispatcher) {
				try {
					write = store.write(
						work.key,
						generated.metadata,
						generated.value
					)
				} catch (failure: Throwable) {
					writeFailure = failure
				}
			}
		} catch (_: CancellationException) {
			// The non-cancellable worker already captured its write result.
		} catch (failure: Throwable) {
			writeFailure = failure
		}
		if (writeFailure != null) {
			recordFailure(checkNotNull(writeFailure))
			release(generated.value)
			complete(work, ReaderPageRasterScheduleStatus.Failed)
			return
		}
		if (write.ownership == ReaderPageRasterValueOwnership.Caller) {
			release(generated.value)
		}
		if (!write.persisted) {
			complete(work, ReaderPageRasterScheduleStatus.Failed)
			return
		}
		if (!isCurrent(work)) {
			write.receipt?.let { receipt ->
				var rollbackFailure: Throwable? = null
				try {
					withContext(NonCancellable + ioDispatcher) {
						try {
							store.rollbackPublication(receipt)
						} catch (failure: Throwable) {
							rollbackFailure = failure
						}
					}
				} catch (_: CancellationException) {
					// Rollback already completed on the non-cancellable worker.
				} catch (failure: Throwable) {
					rollbackFailure = failure
				}
				rollbackFailure?.let(::recordFailure)
			}
			complete(work, ReaderPageRasterScheduleStatus.Stale)
			return
		}

		complete(work, ReaderPageRasterScheduleStatus.Published)
	}

	private fun isCurrent(work: Work<T>): Boolean = synchronized(lock) {
		activeProfileGeneration == work.profileGeneration && activeProfile == work.key.profile
	}

	private fun complete(work: Work<T>, status: ReaderPageRasterScheduleStatus) {
		synchronized(lock) {
			pending[work.key.digest]?.takeIf { current -> current === work }?.let {
				pending.remove(work.key.digest)
			}
		}
		work.result.complete(ReaderPageRasterScheduleResult(work.key, status))
	}

}
