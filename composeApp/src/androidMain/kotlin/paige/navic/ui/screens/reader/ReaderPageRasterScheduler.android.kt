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
	private val cache: ReaderPageRasterCache<T>,
	ownershipTokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator()
) : ReaderPageRasterStore<T>, AutoCloseable {
	private val lock = Any()
	private val physicalOwnership = ReaderExactPhysicalOwnerRegistry(
		ReaderLegacyInventorySource.RasterStoreAndCache,
		ownershipTokenAllocator
	)
	private var activeOperations = 0
	private var closed = false

	private inline fun <R> withOpen(
		closedResult: R,
		action: () -> R
	): R {
		val physicalOwner = synchronized(lock) {
			if (closed) null
			else physicalOwnership.admit(
				ReaderExactPhysicalOwnerDescriptor(
					kind = ReaderTransitionResourceKind.Raster,
					origin = ReaderLegacyResourceOrigin.Pending,
					state = ReaderLegacyResourceState.Running
				)
			)?.also { activeOperations += 1 }
		} ?: return closedResult
		return try {
			action()
		} finally {
			synchronized(lock) {
				check(activeOperations > 0)
				activeOperations -= 1
			}
			physicalOwnership.complete(physicalOwner)
		}
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		physicalOwnership.freezeForTransitionActivation(domain)
	}

	fun connectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? =
		physicalOwnership.connectedFrozenOwnership()

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> =
		physicalOwnership.snapshotFrozenOwnership()

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult =
		physicalOwnership.drainFrozenOwnership(physicalIdentity, onConfirmed)

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		physicalOwnership.restoreAfterTransitionActivation(domain)
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
		var activationDrainConfirmation: (() -> Unit)? = null,
		var deliveringResult: Boolean = false
	)

	private sealed interface MaintenanceRequest {
		data class RetainProfile(val profile: ReaderPageRasterProfile) : MaintenanceRequest
		data class ProtectWindow(
			val profile: ReaderPageRasterProfile,
			val center: Int,
			val pins: Set<Int>
		) : MaintenanceRequest
		data class ProtectChapter(val chapter: ReaderPageRasterChapterKey?) : MaintenanceRequest
	}

	private class MaintenanceWork(
		val request: MaintenanceRequest,
		val owner: ReaderExactPhysicalOwnerRegistry.Owner,
		var dispatched: Boolean = false,
		var settled: Boolean = false,
		var drainRequested: Boolean = false
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
	private val maintenanceOwnership = ReaderExactPhysicalOwnerRegistry(
		ReaderLegacyInventorySource.RasterGenerationAndPersistence,
		tokenAllocator
	)
	private val maintenance = linkedSetOf<MaintenanceWork>()
	private val maintenanceQueue = ArrayDeque<MaintenanceWork>()
	private var profilePendingRetention: MaintenanceWork? = null
	private var retainedWorkerFailure: Throwable? = null
	private var activeWork: Work<T>? = null
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private val restartDescriptors = mutableListOf<RestartDescriptor>()
	private val completedFrozen =
		mutableMapOf<ReaderLegacySourceLocalOpaqueToken, RestartDescriptor>()
	private var closed = false

	val isFrozen: Boolean
		get() = synchronized(lock) { frozenDomain != null }

	private val workerJob = scope.launch {
		try {
			for (signal in wakeups) drain()
		} finally {
			completePendingAfterWorkerExit()
		}
	}

	fun activateProfile(profile: ReaderPageRasterProfile) {
		var obsoleteRetention: MaintenanceWork? = null
		val stale = synchronized(lock) {
			if (closed || frozenDomain != null) return
			if (activeProfile == profile) return
			activeProfile = profile
			activeProfileGeneration += 1L
			obsoleteRetention = profilePendingRetention
			profilePendingRetention = admitMaintenanceLocked(MaintenanceRequest.RetainProfile(profile))
			val obsolete = queue.filter { work -> work.key.profile != profile }
			queue.removeAll(obsolete.toSet())
			obsolete
		}
		obsoleteRetention?.let(::completeMaintenance)
		completeDetached(stale, ReaderPageRasterScheduleStatus.Stale)
		wakeups.trySend(Unit)
	}

	fun request(
		key: ReaderPageRasterKey,
		priority: ReaderPageRasterPriority
	): Deferred<ReaderPageRasterScheduleResult> {
		var wakeWorker = false
		val result = synchronized(lock) {
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
			wakeWorker = true
			work.result
		}
		if (wakeWorker) wakeups.trySend(Unit)
		return result
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		// Closing rejects work and restoration, not inventory of unsettled physical tails.
		when {
			frozenDomain == null -> {
				maintenanceOwnership.freezeForTransitionActivation(domain)
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
		val pendingRows = pending.values.distinctBy(Work<T>::activationToken).map { work ->
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
					activeWork === work || work.deliveringResult -> ReaderLegacyResourceState.Running
					else -> ReaderLegacyResourceState.Reserved
				},
				mayBeCommittedPredecessor = false
			)
		}
		val completedRows = completedFrozen.keys.map { token ->
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = ReaderLegacyPhysicalIdentity(
					domain = domain,
					source = ReaderLegacyInventorySource.RasterGenerationAndPersistence,
					sourceLocalToken = token
				),
				kind = ReaderTransitionResourceKind.Raster,
				binding = null,
				visibleOwner = null,
				origin = ReaderLegacyResourceOrigin.Owned,
				state = ReaderLegacyResourceState.Released,
				mayBeCommittedPredecessor = false
			)
		}
		pendingRows + completedRows + maintenanceOwnership.snapshotFrozenOwnership()
	}

	fun drainFrozenOwnership(
		physicalIdentity: ReaderLegacyPhysicalIdentity,
		onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
	): ReaderPortCommandResult {
		val maintenanceResult = maintenanceOwnership.drainFrozenOwnership(physicalIdentity, onConfirmed)
		if (maintenanceResult == ReaderPortCommandResult.Accepted) return maintenanceResult
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
			work.activationDrainConfirmation = { onConfirmed(physicalIdentity) }
			if (activeWork !== work && !work.deliveringResult) {
				queue.remove(work)
				detached = work
			}
			true
		}
		if (!accepted) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		if (completedBeforeDrain) {
			runCatching { onConfirmed(physicalIdentity) }.onFailure(::recordWorkerFailure)
		} else {
			detached?.let { complete(it, ReaderPageRasterScheduleStatus.Stale) }
		}
		return ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult {
		val restart = synchronized(lock) {
			if (
				closed ||
				frozenDomain != domain ||
				pending.values.any { it.activationDrainConfirmation != null || it.deliveringResult } ||
				maintenance.any { it.dispatched && !it.settled }
			) return@synchronized null
			if (maintenanceOwnership.restoreAfterTransitionActivation(domain) != ReaderPortCommandResult.Accepted) {
				return@synchronized null
			}
			val maintenanceRestart = maintenance.filter { it.settled }.map { it.request }.filter { request ->
				request !is MaintenanceRequest.RetainProfile || request.profile == activeProfile
			}
			maintenance.removeAll { it.settled }
			frozenDomain = null
			maintenanceRestart.forEach { request ->
				admitMaintenanceLocked(request)?.let(maintenanceQueue::addLast)
			}
			completedFrozen.values.forEach { restartDescriptors += it }
			completedFrozen.clear()
			restartDescriptors.toList().also { restartDescriptors.clear() }
		} ?: return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		restart.forEach { descriptor -> request(descriptor.key, descriptor.priority) }
		wakeups.trySend(Unit)
		return ReaderPortCommandResult.Accepted
	}

	fun close() {
		var staleMaintenance = emptyList<MaintenanceWork>()
		val stale = synchronized(lock) {
			if (closed) return
			closed = true
			activeProfile = null
			activeProfileGeneration += 1L
			profilePendingRetention = null
			maintenanceQueue.clear()
			staleMaintenance = maintenance.filter { !it.dispatched && !it.settled }
			queue.toList().also { queue.clear() }
		}
		staleMaintenance.forEach(::completeMaintenance)
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
		val work = synchronized(lock) {
			admitMaintenanceLocked(MaintenanceRequest.ProtectWindow(profile, centerPageOrdinal, pinnedPageOrdinals.toSet()))
		} ?: return
		runMaintenance(work)
	}

	suspend fun protectChapter(chapter: ReaderPageRasterChapterKey?) {
		val work = synchronized(lock) {
			admitMaintenanceLocked(MaintenanceRequest.ProtectChapter(chapter))
		} ?: return
		runMaintenance(work)
	}

	private fun admitMaintenanceLocked(request: MaintenanceRequest): MaintenanceWork? {
		if (closed || frozenDomain != null) return null
		lateinit var work: MaintenanceWork
		val owner = maintenanceOwnership.admit(
			ReaderExactPhysicalOwnerDescriptor(
				kind = ReaderTransitionResourceKind.Raster,
				origin = ReaderLegacyResourceOrigin.Pending,
				state = ReaderLegacyResourceState.Reserved
			),
			cancelPhysical = {
				val completeNow = synchronized(lock) {
					work.drainRequested = true
					if (profilePendingRetention === work) profilePendingRetention = null
					maintenanceQueue.remove(work)
					!work.dispatched
				}
				if (completeNow) completeMaintenance(work)
				true
			}
		) ?: return null
		work = MaintenanceWork(request, owner)
		maintenance += work
		return work
	}

	private suspend fun runMaintenance(work: MaintenanceWork) {
		val dispatched = synchronized(lock) {
			if (work.settled || work.dispatched) return
			if (closed || frozenDomain != null || work.drainRequested) false
			else {
				work.dispatched = true
				maintenanceOwnership.updateState(work.owner, ReaderLegacyResourceState.Running)
				true
			}
		}
		if (!dispatched) {
			completeMaintenance(work)
			return
		}
		try {
			withContext(ioDispatcher) {
				val start = synchronized(lock) { !closed && frozenDomain == null && !work.drainRequested }
				if (start) when (val request = work.request) {
					is MaintenanceRequest.RetainProfile -> store.retainProfile(request.profile)
					is MaintenanceRequest.ProtectWindow -> store.protectEncodedWindow(request.profile, request.center, request.pins)
					is MaintenanceRequest.ProtectChapter -> store.protectChapter(request.chapter)
				}
			}
		} catch (cancelled: CancellationException) {
			throw cancelled
		} catch (failure: Throwable) {
			recordWorkerFailure(failure)
		} finally {
			completeMaintenance(work)
		}
	}

	private fun completeMaintenance(work: MaintenanceWork) {
		val complete = synchronized(lock) {
			if (work.settled) false
			else {
				work.settled = true
				if (frozenDomain == null) maintenance.remove(work)
				true
			}
		}
		if (complete) maintenanceOwnership.complete(work.owner)
	}

	private suspend fun drain() {
		while (true) {
			val retention = synchronized(lock) {
				if (closed || frozenDomain != null) return
				profilePendingRetention.also { profilePendingRetention = null }
					?: maintenanceQueue.removeFirstOrNull()
			}
			if (retention != null) {
				runMaintenance(retention)
				continue
			}
			val work = synchronized(lock) {
				if (closed || frozenDomain != null) null
				else queue.poll()?.also { activeWork = it }
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
		if (!isCurrent(work)) return ReaderPageRasterScheduleStatus.Stale
		val cached = withContext(ioDispatcher) { isCurrent(work) && store.contains(work.key) }
		if (!isCurrent(work)) return ReaderPageRasterScheduleStatus.Stale
		if (cached) return ReaderPageRasterScheduleStatus.Cached
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
						completedWrite = if (isCurrent(work)) store.write(
							work.key,
							generated.metadata,
							generated.value
						) else ReaderPageRasterWriteResult(false, ReaderPageRasterValueOwnership.Caller)
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
		!closed && frozenDomain == null && work.activationDrainConfirmation == null &&
			activeProfileGeneration == work.profileGeneration &&
			activeProfile == work.key.profile
	}

	private fun complete(
		work: Work<T>,
		status: ReaderPageRasterScheduleStatus
	) {
		val deliveryStatus = synchronized(lock) {
			if (work.deliveringResult) return
			work.deliveringResult = true
			if (closed || frozenDomain != null) ReaderPageRasterScheduleStatus.Stale else status
		}
		try {
			work.result.complete(ReaderPageRasterScheduleResult(work.key, deliveryStatus))
		} catch (failure: Throwable) {
			recordWorkerFailure(failure)
		} finally {
			val drainConfirmation = synchronized(lock) {
				pending[work.key.digest]
					?.takeIf { current -> current === work }
					?.let { pending.remove(work.key.digest) }
				if (activeWork === work) activeWork = null
				if (frozenDomain != null) {
					val descriptor = RestartDescriptor(work.key, work.priority)
					if (work.activationDrainConfirmation != null) restartDescriptors += descriptor
					else completedFrozen[work.activationToken] = descriptor
				}
				work.activationDrainConfirmation.also { work.activationDrainConfirmation = null }
			}
			runCatching { drainConfirmation?.invoke() }.onFailure(::recordWorkerFailure)
		}
	}

	private fun completePendingAfterWorkerExit() {
		val stale = synchronized(lock) {
			closed = true
			activeProfile = null
			activeProfileGeneration += 1L
			profilePendingRetention = null
			maintenanceQueue.clear()
			queue.clear()
			pending.values.toList()
		}
		val staleMaintenance = synchronized(lock) { maintenance.filter { !it.dispatched && !it.settled } }
		staleMaintenance.forEach(::completeMaintenance)
		wakeups.close()
		completeDetached(stale, ReaderPageRasterScheduleStatus.Stale)
	}

	private fun completeDetached(
		work: List<Work<T>>,
		status: ReaderPageRasterScheduleStatus
	) {
		work.forEach { complete(it, status) }
	}
}
