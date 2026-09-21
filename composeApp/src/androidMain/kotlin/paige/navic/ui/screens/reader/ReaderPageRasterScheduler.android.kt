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
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind

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
	DiskCapacity,
	EncodeIdentityReleasing
}

internal enum class ReaderPageRasterPublicationResult {
	Durable,
	CapacityReached,
	Failed
}

internal data class ReaderPageRasterPublicationCompletion(
	val result: ReaderPageRasterPublicationResult,
	val writeFailureReason: ReaderPageRasterWriteFailureReason? = null
)

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
	fun protectEncodedWindow(
		profile: ReaderPageRasterProfile,
		centerPageOrdinal: Int,
		pinnedPageOrdinals: Set<Int>
	) = Unit
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

	fun contains(
		key: ReaderPageRasterKey,
		expectedMetadata: ReaderPageRasterMetadata
	): Boolean = withOpen(false) {
		cache.contains(key, expectedMetadata)
	}

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

	fun remove(
		key: ReaderPageRasterKey,
		expectedMetadata: ReaderPageRasterMetadata
	): Boolean = withOpen(false) {
		cache.remove(key, expectedMetadata)
	}

	override fun retainProfile(profile: ReaderPageRasterProfile): Int =
		withOpen(0) { cache.retainProfile(profile) }

	override fun protectEncodedWindow(
		profile: ReaderPageRasterProfile,
		centerPageOrdinal: Int,
		pinnedPageOrdinals: Set<Int>
	) {
		withOpen(Unit) {
			cache.protectEncodedWindow(profile, centerPageOrdinal, pinnedPageOrdinals)
		}
	}

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
	private val nanoTime: () -> Long = System::nanoTime,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator()
) {
	private class Work<T : Any>(
		val key: ReaderPageRasterKey,
		var priority: ReaderPageRasterPriority,
		val sequence: Long,
		val profileGeneration: Long,
		val result: CompletableDeferred<ReaderPageRasterScheduleResult>,
		val activationToken: ReaderLegacySourceLocalOpaqueToken,
		var activationDrainConfirmation: (() -> Unit)? = null
	)

	private data class RestartDescriptor(
		val key: ReaderPageRasterKey,
		val priority: ReaderPageRasterPriority
	)

	private val lock = Any()
	private val wakeups = Channel<Unit>(capacity = 1)
	private val queue = PriorityQueue<Work<T>>(
		compareBy<Work<T>> { work -> work.priority.rank }
			.thenBy { work -> work.sequence }
	)
	private val pending = mutableMapOf<String, Work<T>>()
	private var activeProfile: ReaderPageRasterProfile? = null
	private var activeProfileGeneration = 0L
	private var nextSequence = 0L
	private var profilePendingRetention: ReaderPageRasterProfile? = null
	private var retainedWorkerFailure: Throwable? = null
	private var activeWork: Work<T>? = null
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private val restartDescriptors = mutableListOf<RestartDescriptor>()
	private val completedFrozen =
		mutableMapOf<ReaderLegacySourceLocalOpaqueToken, RestartDescriptor>()
	private var closed = false

	private val workerJob = scope.launch {
		try {
			for (signal in wakeups) drain()
		} finally {
			completePendingAfterWorkerExit()
		}
	}

	fun activateProfile(profile: ReaderPageRasterProfile) {
		val stale = synchronized(lock) {
			if (closed || frozenDomain != null) return
			if (activeProfile == profile) return
			activeProfile = profile
			activeProfileGeneration += 1L
			profilePendingRetention = profile
			val obsolete = queue.filter { work -> work.key.profile != profile }
			queue.removeAll(obsolete.toSet())
			obsolete.forEach { work ->
				pending[work.key.digest]
					?.takeIf { current -> current === work }
					?.let { pending.remove(work.key.digest) }
			}
			obsolete
		}
		completeDetached(stale, ReaderPageRasterScheduleStatus.Stale)
		wakeups.trySend(Unit)
	}

	fun request(
		key: ReaderPageRasterKey,
		priority: ReaderPageRasterPriority
	): Deferred<ReaderPageRasterScheduleResult> = synchronized(lock) {
		if (closed || frozenDomain != null) {
			return@synchronized CompletableDeferred(
				ReaderPageRasterScheduleResult(
					key,
					ReaderPageRasterScheduleStatus.Stale
				)
			)
		}
		pending[key.digest]
			?.takeIf { work -> work.key.identity == key.identity }
			?.let { work ->
				if (priority.rank < work.priority.rank && queue.remove(work)) {
					work.priority = priority
					queue.add(work)
				}
				return@synchronized work.result
			}
		if (activeProfile != key.profile) {
			return@synchronized CompletableDeferred(
				ReaderPageRasterScheduleResult(
					key,
					ReaderPageRasterScheduleStatus.Stale
				)
			)
		}
		val work = Work<T>(
			key = key,
			priority = priority,
			sequence = nextSequence++,
			profileGeneration = activeProfileGeneration,
			result = CompletableDeferred(),
			activationToken = tokenAllocator.allocate()
		)
		pending[key.digest] = work
		queue.add(work)
		wakeups.trySend(Unit)
		work.result
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		when {
			closed -> ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
			frozenDomain == null -> {
				frozenDomain = domain
				ReaderPortCommandResult.Accepted
			}
			frozenDomain == domain -> ReaderPortCommandResult.Accepted
			else -> ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		}
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized emptyList()
		pending.values.distinctBy(Work<T>::activationToken).map { work ->
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = ReaderLegacyPhysicalIdentity(
					domain = domain,
					source = ReaderLegacyInventorySource.RasterGenerationAndPersistence,
					sourceLocalToken = work.activationToken
				),
				kind = ReaderTransitionResourceKind.Raster,
				binding = null,
				visibleOwner = null,
				origin = ReaderLegacyResourceOrigin.Owned,
				state = when {
					work.activationDrainConfirmation != null ->
						ReaderLegacyResourceState.ReleaseRequested
					activeWork === work -> ReaderLegacyResourceState.Running
					else -> ReaderLegacyResourceState.Reserved
				},
				mayBeCommittedPredecessor = false
			)
		}
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		var detached: Work<T>? = null
		var completedBeforeDrain = false
		val accepted = synchronized(lock) {
			val domain = frozenDomain
			if (
				domain == null ||
				physicalIdentity.domain != domain ||
				physicalIdentity.source !=
				ReaderLegacyInventorySource.RasterGenerationAndPersistence
			) return@synchronized false
			val token = physicalIdentity.sourceLocalToken
			completedFrozen.remove(token)?.let { descriptor ->
				restartDescriptors += descriptor
				completedBeforeDrain = true
				return@synchronized true
			}
			val work = pending.values.firstOrNull { it.activationToken == token }
				?: return@synchronized false
			if (work.activationDrainConfirmation != null) return@synchronized false
			if (activeWork === work) {
				work.activationDrainConfirmation = { onConfirmed(physicalIdentity) }
			} else {
				queue.remove(work)
				pending[work.key.digest]
					?.takeIf { it === work }
					?.let { pending.remove(work.key.digest) }
				restartDescriptors += RestartDescriptor(work.key, work.priority)
				detached = work
			}
			true
		}
		if (!accepted) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		if (completedBeforeDrain) {
			onConfirmed(physicalIdentity)
		} else {
			detached?.let { work ->
				work.result.complete(
					ReaderPageRasterScheduleResult(
						work.key,
						ReaderPageRasterScheduleStatus.Stale
					)
				)
				onConfirmed(physicalIdentity)
			}
		}
		return ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		val restart = synchronized(lock) {
			if (
				frozenDomain != domain ||
				pending.values.any { it.activationDrainConfirmation != null }
			) return@synchronized null
			frozenDomain = null
			completedFrozen.clear()
			restartDescriptors.toList().also { restartDescriptors.clear() }
		} ?: return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		restart.forEach { descriptor -> request(descriptor.key, descriptor.priority) }
		return ReaderPortCommandResult.Accepted
	}

	fun close() {
		val stale = synchronized(lock) {
			if (closed) return
			closed = true
			activeProfile = null
			activeProfileGeneration += 1L
			profilePendingRetention = null
			queue.toList().also { queued ->
				queue.clear()
				queued.forEach { work ->
					pending[work.key.digest]
						?.takeIf { current -> current === work }
						?.let { pending.remove(work.key.digest) }
				}
			}
		}
		completeDetached(stale, ReaderPageRasterScheduleStatus.Stale)
		wakeups.close()
	}

	suspend fun closeAndJoin() {
		close()
		withContext(NonCancellable) {
			workerJob.join()
		}
		synchronized(lock) { retainedWorkerFailure }?.let { throw it }
	}

	fun dispatchFailure(): Throwable? = synchronized(lock) {
		retainedWorkerFailure
	}

	private fun recordWorkerFailure(failure: Throwable) {
		synchronized(lock) {
			val first = retainedWorkerFailure
			if (first == null) retainedWorkerFailure = failure
			else if (failure !== first) first.addSuppressed(failure)
		}
	}

	suspend fun protectEncodedWindow(
		profile: ReaderPageRasterProfile,
		centerPageOrdinal: Int,
		pinnedPageOrdinals: Set<Int>
	) {
		withContext(ioDispatcher) {
			store.protectEncodedWindow(profile, centerPageOrdinal, pinnedPageOrdinals)
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
			if (retention != null) {
				try {
					withContext(ioDispatcher) { store.retainProfile(retention) }
				} catch (cancelled: CancellationException) {
					throw cancelled
				} catch (failure: Throwable) {
					recordWorkerFailure(failure)
				}
			}
			val work = synchronized(lock) {
				queue.poll()?.also { activeWork = it }
			} ?: return
			process(work)
		}
	}

	private suspend fun process(work: Work<T>) {
		var status = ReaderPageRasterScheduleStatus.Failed
		var cancellation: CancellationException? = null
		try {
			status = processOwned(work)
		} catch (cancelled: CancellationException) {
			cancellation = cancelled
			status = ReaderPageRasterScheduleStatus.Stale
			cancelled.suppressed.forEach(::recordWorkerFailure)
		} catch (failure: Throwable) {
			recordWorkerFailure(failure)
		} finally {
			try {
				complete(work, status)
			} catch (failure: Throwable) {
				recordWorkerFailure(failure)
				synchronized(lock) {
					pending[work.key.digest]
						?.takeIf { current -> current === work }
						?.let { pending.remove(work.key.digest) }
				}
				try {
					work.result.complete(
						ReaderPageRasterScheduleResult(work.key, status)
					)
				} catch (completionFailure: Throwable) {
					recordWorkerFailure(completionFailure)
				}
			}
		}
		cancellation?.let { throw it }
	}

	private suspend fun processOwned(
		work: Work<T>
	): ReaderPageRasterScheduleStatus {
		if (withContext(ioDispatcher) { store.contains(work.key) }) {
			return ReaderPageRasterScheduleStatus.Cached
		}
		if (!isCurrent(work)) {
			return ReaderPageRasterScheduleStatus.Stale
		}
		val generated = generator.generate(work.key)
			?: return ReaderPageRasterScheduleStatus.Failed
		var callerOwnsValue = true
		var status = ReaderPageRasterScheduleStatus.Failed
		var failure: Throwable? = null
		try {
			if (!isCurrent(work)) {
				status = ReaderPageRasterScheduleStatus.Stale
			} else {
				var completedWrite: ReaderPageRasterWriteResult? = null
				try {
					withContext(NonCancellable + ioDispatcher) {
						completedWrite = store.write(
							work.key,
							generated.metadata,
							generated.value
						)
					}
				} catch (cancelled: CancellationException) {
					if (completedWrite == null) throw cancelled
				}
				val write = checkNotNull(completedWrite) {
					"Raster store write completed without an ownership result"
				}
				callerOwnsValue =
					write.ownership == ReaderPageRasterValueOwnership.Caller
				status = if (!write.persisted) {
					ReaderPageRasterScheduleStatus.Failed
				} else if (!isCurrent(work)) {
					write.receipt?.let { receipt ->
						withContext(NonCancellable + ioDispatcher) {
							store.rollbackPublication(receipt)
						}
					}
					ReaderPageRasterScheduleStatus.Stale
				} else {
					ReaderPageRasterScheduleStatus.Published
				}
			}
		} catch (caught: Throwable) {
			failure = caught
		} finally {
			if (callerOwnsValue) {
				try {
					release(generated.value)
				} catch (releaseFailure: Throwable) {
					val currentFailure = failure
					if (currentFailure == null) failure = releaseFailure
					else if (releaseFailure !== currentFailure) {
						currentFailure.addSuppressed(releaseFailure)
					}
				}
			}
		}
		failure?.let { throw it }
		return status
	}

	private fun isCurrent(work: Work<T>): Boolean = synchronized(lock) {
		work.activationDrainConfirmation == null &&
			activeProfileGeneration == work.profileGeneration &&
			activeProfile == work.key.profile
	}

	private fun complete(
		work: Work<T>,
		status: ReaderPageRasterScheduleStatus
	) {
		val drainConfirmation = synchronized(lock) {
			pending[work.key.digest]
				?.takeIf { current -> current === work }
				?.let { pending.remove(work.key.digest) }
			if (activeWork === work) activeWork = null
			if (frozenDomain != null) {
				val descriptor = RestartDescriptor(work.key, work.priority)
				if (work.activationDrainConfirmation != null) {
					restartDescriptors += descriptor
				} else {
					completedFrozen[work.activationToken] = descriptor
				}
			}
			work.activationDrainConfirmation.also {
				work.activationDrainConfirmation = null
			}
		}
		work.result.complete(ReaderPageRasterScheduleResult(work.key, status))
		drainConfirmation?.invoke()
	}

	private fun completePendingAfterWorkerExit() {
		val stale = synchronized(lock) {
			closed = true
			activeProfile = null
			activeProfileGeneration += 1L
			profilePendingRetention = null
			pending.values.toList().also {
				pending.clear()
				queue.clear()
			}
		}
		wakeups.close()
		completeDetached(stale, ReaderPageRasterScheduleStatus.Stale)
	}

	private fun completeDetached(
		work: List<Work<T>>,
		status: ReaderPageRasterScheduleStatus
	) {
		work.forEach { item ->
			try {
				item.result.complete(
					ReaderPageRasterScheduleResult(item.key, status)
				)
			} catch (failure: Throwable) {
				recordWorkerFailure(failure)
			}
		}
	}
}
