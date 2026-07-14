package paige.navic.ui.screens.reader

import java.util.PriorityQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPageRasterCalibrationSample
import paige.navic.reader.ReaderPageRasterPreparationMode
import paige.navic.reader.ReaderPageRasterPriority
import paige.navic.reader.readerPageRasterPreparationMode

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

internal interface ReaderPageRasterStore<T : Any> {
	fun contains(key: ReaderPageRasterKey): Boolean
	fun write(key: ReaderPageRasterKey, metadata: ReaderPageRasterMetadata, value: T): Boolean
	fun remove(key: ReaderPageRasterKey): Boolean
	fun retainProfile(profile: ReaderPageRasterProfile): Int
	fun encodedBytes(key: ReaderPageRasterKey): Long
}

internal class ReaderPageRasterCacheStore<T : Any>(
	private val cache: ReaderPageRasterCache<T>
) : ReaderPageRasterStore<T> {
	override fun contains(key: ReaderPageRasterKey): Boolean = cache.contains(key)

	override fun write(key: ReaderPageRasterKey, metadata: ReaderPageRasterMetadata, value: T): Boolean =
		cache.write(key, metadata, value)

	override fun remove(key: ReaderPageRasterKey): Boolean = cache.remove(key)

	override fun retainProfile(profile: ReaderPageRasterProfile): Int = cache.retainProfile(profile)

	override fun encodedBytes(key: ReaderPageRasterKey): Long = cache.pathFor(key).takeIf { file -> file.isFile }?.length() ?: 0L
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
	private val calibrationSamples = mutableListOf<ReaderPageRasterCalibrationSample>()
	private var activeProfile: ReaderPageRasterProfile? = null
	private var activeProfileGeneration = 0L
	private var nextSequence = 0L
	private var profilePendingRetention: ReaderPageRasterProfile? = null
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

	fun preparationMode(chapterPageCount: Int): ReaderPageRasterPreparationMode = synchronized(lock) {
		readerPageRasterPreparationMode(calibrationSamples.toList(), chapterPageCount)
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

		val writeStarted = nanoTime()
		val published = withContext(ioDispatcher) {
			store.write(work.key, generated.metadata, generated.value)
		}
		val writeMillis = ((nanoTime() - writeStarted).coerceAtLeast(0L) / 1_000_000L)
		if (!published) {
			release(generated.value)
			complete(work, ReaderPageRasterScheduleStatus.Failed)
			return
		}
		if (!isCurrent(work)) {
			withContext(ioDispatcher) { store.remove(work.key) }
			complete(work, ReaderPageRasterScheduleStatus.Stale)
			return
		}

		val encodedBytes = withContext(ioDispatcher) { store.encodedBytes(work.key) }
		recordCalibration(
			ReaderPageRasterCalibrationSample(
				captureMillis = generated.captureMillis,
				encodeWriteMillis = writeMillis,
				readDecodeMillis = generated.readDecodeMillis,
				gpuUploadMillis = generated.gpuUploadMillis,
				encodedBytes = encodedBytes
			)
		)
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

	private fun recordCalibration(sample: ReaderPageRasterCalibrationSample) {
		synchronized(lock) {
			if (calibrationSamples.size < 3) calibrationSamples += sample
		}
	}
}
