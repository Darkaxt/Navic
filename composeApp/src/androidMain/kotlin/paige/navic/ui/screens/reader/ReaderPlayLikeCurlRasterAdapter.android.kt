package paige.navic.ui.screens.reader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPageBitmapQuality
import paige.navic.reader.ReaderPageMaximumProtectedRasterEntriesPerLease
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
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

internal enum class ReaderPlayLikeCurlMissingRasterPolicy {
	RequestRepair,
	CacheOnly
}

internal data class ReaderPlayLikeCurlRasterKey(
	val profile: ReaderPlayLikeCurlRasterProfile,
	val pageIndex: Int,
	val publicationFence: ReaderPlayLikeCurlRasterPublicationFence =
		AlwaysCurrentReaderPlayLikeCurlRasterPublicationFence,
	val missingRasterPolicy: ReaderPlayLikeCurlMissingRasterPolicy =
		ReaderPlayLikeCurlMissingRasterPolicy.RequestRepair
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

internal data class ReaderPlayLikeCurlRasterResidencyBudgetMetrics(
	val residentEntries: Int,
	val residentEntryLimit: Int,
	val peakResidentEntries: Int
)

internal class ReaderPlayLikeCurlRasterResidencyBudget(
	val residentEntryLimit: Int,
	private val onCapacityAvailable: () -> Boolean = { true }
) {
	internal class Slot internal constructor()

	internal sealed interface Admission {
		data class Acquired(val slot: Slot) : Admission
		data class Replaced(val slot: Slot) : Admission
		data object Unavailable : Admission
	}

	private val lock = Any()
	private val slots = Collections.newSetFromMap(IdentityHashMap<Slot, Boolean>())
	private var capacityDemanded = false
	private var peakResidentEntries = 0

	init {
		require(residentEntryLimit > 0)
	}

	internal fun tryAcquireOrReplace(
		replacement: Slot?,
		allowNew: Boolean
	): Admission = synchronized(lock) {
		if (allowNew && slots.size < residentEntryLimit) {
			val slot = Slot()
			check(slots.add(slot))
			peakResidentEntries = maxOf(peakResidentEntries, slots.size)
			return@synchronized Admission.Acquired(slot)
		}
		if (replacement != null) {
			check(slots.contains(replacement)) {
				"Shared raster replacement slot is not live"
			}
			return@synchronized Admission.Replaced(replacement)
		}
		capacityDemanded = true
		Admission.Unavailable
	}

	internal fun release(slot: Slot) {
		val signal = synchronized(lock) {
			check(slots.remove(slot)) { "Shared raster residency slot released twice" }
			if (capacityDemanded) {
				capacityDemanded = false
				true
			} else {
				false
			}
		}
		if (signal && !onCapacityAvailable()) {
			synchronized(lock) { capacityDemanded = true }
		}
	}

	fun metrics(): ReaderPlayLikeCurlRasterResidencyBudgetMetrics = synchronized(lock) {
		ReaderPlayLikeCurlRasterResidencyBudgetMetrics(
			residentEntries = slots.size,
			residentEntryLimit = residentEntryLimit,
			peakResidentEntries = peakResidentEntries
		)
	}
}

internal data class ReaderPlayLikeCurlRasterResidencyMetrics(
	val residentEntries: Int,
	val uniqueDecodedBitmaps: Int,
	val residentEntryLimit: Int,
	val uniqueDecodedBitmapLimit: Int,
	val peakResidentEntries: Int,
	val peakUniqueDecodedBitmaps: Int,
	val pinnedEntries: Int,
	val activePreparationWorkers: Int,
	val activeMaterializationWorkers: Int,
	val pendingValueReleases: Int,
	val evictedEntries: Long,
	val releasedEntries: Long
)

private const val ReaderPlayLikeCurlUnintegratedRendererDeckLeaseLimit = 4

/**
 * Asynchronously materializes immutable page rasters while keeping ownership out of the GL renderer.
 * A profile change invalidates obsolete work without cancelling the loader that owns its cleanup.
 */
internal class ReaderPlayLikeCurlRasterAdapter<T : Any>(
	private val scope: CoroutineScope,
	private val loader: ReaderPlayLikeCurlRasterLoader<T>,
	private val rendererDeckLeaseLimit: Int,
	private val residentEntryLimit: Int =
		rendererDeckLeaseLimit * ReaderPageMaximumProtectedRasterEntriesPerLease,
	private val onCapacityAvailable: () -> Boolean = { true },
	private val residencyBudget: ReaderPlayLikeCurlRasterResidencyBudget =
		ReaderPlayLikeCurlRasterResidencyBudget(
			residentEntryLimit = residentEntryLimit,
			onCapacityAvailable = onCapacityAvailable
		),
	private val acquisitionInterceptor:
		suspend (ReaderPlayLikeCurlRasterKey) -> Boolean = { false },
	private val publicationDispatcher: CoroutineDispatcher = Dispatchers.Default,
	private val onOwnershipMutated: () -> Unit = {},
	private val release: (T) -> Unit
) : AutoCloseable {
	constructor(
		scope: CoroutineScope,
		loader: ReaderPlayLikeCurlRasterLoader<T>,
		publicationDispatcher: CoroutineDispatcher,
		release: (T) -> Unit
	) : this(
		scope = scope,
		loader = loader,
		rendererDeckLeaseLimit = ReaderPlayLikeCurlUnintegratedRendererDeckLeaseLimit,
		publicationDispatcher = publicationDispatcher,
		release = release
	)

	constructor(
		scope: CoroutineScope,
		loader: ReaderPlayLikeCurlRasterLoader<T>,
		release: (T) -> Unit
	) : this(
		scope = scope,
		loader = loader,
		rendererDeckLeaseLimit = ReaderPlayLikeCurlUnintegratedRendererDeckLeaseLimit,
		release = release
	)

	private enum class DecodedOwnerState {
		Active,
		Releasing,
		Released
	}

	private class DecodedValueOwner<T : Any>(
		val value: T,
		var state: DecodedOwnerState = DecodedOwnerState.Active
	) {
		var entryReferences: Int = 0
	}

	private class ReleasedValueReference<T : Any>(
		value: T,
		queue: ReferenceQueue<T>? = null
	) : WeakReference<T>(value, queue) {
		private val identityHashCode = System.identityHashCode(value)

		override fun hashCode(): Int = identityHashCode

		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other !is ReleasedValueReference<*>) return false
			val value = get() ?: return false
			return value === other.get()
		}
	}

	private class CacheEntry<T : Any>(
		val key: ReaderPlayLikeCurlRasterCacheKey,
		val owner: DecodedValueOwner<T>,
		val residencySlot: ReaderPlayLikeCurlRasterResidencyBudget.Slot,
		var retainCount: Int = 0
	)

	private class InFlight<T : Any>(
		val generation: Long,
		val result: CompletableDeferred<CacheEntry<T>?>
	) {
		lateinit var worker: Job
		val preparationWaiters = mutableSetOf<Job>()
	}

	private enum class CapacityKind {
		PreparationWorker,
		MaterializationWorkerOrIdentity,
		ResidentSlot
	}

	private enum class WorkerKind {
		Preparation,
		Materialization
	}

	private class RasterLoadUnavailable : RuntimeException(null, null, false, false)

	private class WorkerOwnership<T : Any>(
		val kind: WorkerKind,
		var materializationReservationHeld: Boolean,
		val preparationResult: CompletableDeferred<ReaderPlayLikeCurlRasterDeck<T>?>?
	)

	private data class PublicationResult<T : Any>(
		val entry: CacheEntry<T>?,
		val ownersToRelease: List<DecodedValueOwner<T>>,
		val residencySlotsToRelease: List<ReaderPlayLikeCurlRasterResidencyBudget.Slot>
	)

	private val preparationWorkerLimit = rendererDeckLeaseLimit
	private val materializationWorkerLimit = residentEntryLimit
	private val uniqueDecodedBitmapLimit = residentEntryLimit + materializationWorkerLimit
	private val lock = Any()
	private val entries = LinkedHashMap<ReaderPlayLikeCurlRasterCacheKey, CacheEntry<T>>(
		0,
		0.75f,
		true
	)
	private val decodedOwners = IdentityHashMap<T, DecodedValueOwner<T>>()
	private val releasedValueQueue = ReferenceQueue<T>()
	private val releasedValues = mutableSetOf<ReleasedValueReference<T>>()
	private val retiredEntries = Collections.newSetFromMap(
		IdentityHashMap<CacheEntry<T>, Boolean>()
	)
	private val inFlight = mutableMapOf<ReaderPlayLikeCurlRasterKey, InFlight<T>>()
	private val workers = linkedMapOf<Job, WorkerOwnership<T>>()
	private val preparationCancellationPending = mutableSetOf<Job>()
	private val blockedCapacities = linkedSetOf<CapacityKind>()
	private val closedSignal = CompletableDeferred<Unit>()
	private var activeProfile: ReaderPlayLikeCurlRasterProfile? = null
	private var protectedKeys = emptySet<ReaderPlayLikeCurlRasterCacheKey>()
	private var generation = 0L
	private var closed = false
	private var materializationValueReservations = 0
	private var openDeckRetainers = 0
	private var pendingValueReleases = 0
	private var pendingResidencySlotReleases = 0
	private var releaseFailure: Throwable? = null
	private var peakResidentEntries = 0
	private var peakUniqueDecodedBitmaps = 0
	private var evictedEntries = 0L
	private var releasedEntries = 0L

	init {
		require(rendererDeckLeaseLimit > 0)
		require(residentEntryLimit > 0)
	}

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
		missingRasterPolicy: ReaderPlayLikeCurlMissingRasterPolicy =
			ReaderPlayLikeCurlMissingRasterPolicy.RequestRepair,
		onProgress: (ReaderPlayLikeCurlRasterProgress) -> Unit = {}
	): Deferred<ReaderPlayLikeCurlRasterDeck<T>?> {
		val uniquePageIndices = pageIndices.distinct()
		val requestedKeys = uniquePageIndices.map { pageIndex ->
			ReaderPlayLikeCurlRasterKey(
				profile = profile,
				pageIndex = pageIndex,
				publicationFence = publicationFence,
				missingRasterPolicy = missingRasterPolicy
			)
		}
		val requestedCacheKeys = requestedKeys.mapTo(linkedSetOf()) { key -> key.cacheKey }
		val staleWaiters = mutableListOf<CompletableDeferred<CacheEntry<T>?>>()
		val stalePreparations =
			mutableListOf<CompletableDeferred<ReaderPlayLikeCurlRasterDeck<T>?>>()
		val ownersToRelease = mutableListOf<DecodedValueOwner<T>>()
		val residencySlotsToRelease =
			mutableListOf<ReaderPlayLikeCurlRasterResidencyBudget.Slot>()
		var workerToStart: Job? = null

		val result = synchronized(lock) {
			if (closed) return completedNullDeck()
			if (activeProfile != profile) {
				generation += 1L
				activeProfile = profile
				inFlight.values
					.filter { work -> work.generation != generation }
					.mapTo(staleWaiters) { work -> work.result }
				workers.values.mapNotNullTo(stalePreparations) { ownership ->
					ownership.preparationResult
				}
				retireResidentEntriesLocked(ownersToRelease, residencySlotsToRelease)
			}
			protectedKeys = requestedCacheKeys
			if (!canAdmitWorkerLocked(WorkerKind.Preparation)) {
				rejectForCapacityLocked(CapacityKind.PreparationWorker)
				return@synchronized completedNullDeck()
			}

			val admittedGeneration = generation
			val preparationResult =
				CompletableDeferred<ReaderPlayLikeCurlRasterDeck<T>?>()
			lateinit var worker: Job
			worker = scope.launch(start = CoroutineStart.LAZY) {
				try {
					val prepared = prepareDeck(
						profile = profile,
						requestedKeys = requestedKeys,
						requestedCacheKeys = requestedCacheKeys,
						preparationGeneration = admittedGeneration,
						preparationWorker = worker,
						publicationFence = publicationFence,
						onProgress = onProgress
					)
					if (!preparationResult.complete(prepared)) {
						prepared?.close()
					}
				} catch (cancelled: CancellationException) {
					preparationResult.complete(null)
					throw cancelled
				} catch (failure: Throwable) {
					preparationResult.completeExceptionally(failure)
				}
			}
			preparationResult.invokeOnCompletion { completion ->
				if (completion is CancellationException) {
					cancelPreparationWorker(worker, completion)
				}
			}
			registerWorkerLocked(
				worker = worker,
				kind = WorkerKind.Preparation,
				preparationResult = preparationResult
			)
			workerToStart = worker
			preparationResult
		}

		staleWaiters.forEach { waiter -> waiter.complete(null) }
		stalePreparations.forEach { preparation -> preparation.complete(null) }
		releaseScheduledOwners(ownersToRelease)
		releaseResidencySlots(residencySlotsToRelease)
		workerToStart?.start()
		return result
	}

	private suspend fun prepareDeck(
		profile: ReaderPlayLikeCurlRasterProfile,
		requestedKeys: List<ReaderPlayLikeCurlRasterKey>,
		requestedCacheKeys: Set<ReaderPlayLikeCurlRasterCacheKey>,
		preparationGeneration: Long,
		preparationWorker: Job,
		publicationFence: ReaderPlayLikeCurlRasterPublicationFence,
		onProgress: (ReaderPlayLikeCurlRasterProgress) -> Unit
	): ReaderPlayLikeCurlRasterDeck<T>? {
		onProgress(
			ReaderPlayLikeCurlRasterProgress(
				completed = 0,
				total = requestedKeys.size
			)
		)
		val loaded = try {
			coroutineScope {
				val pending = requestedKeys.map { key ->
					async(start = CoroutineStart.UNDISPATCHED) {
						val entry = loadEntry(
							key = key,
							preparationGeneration = preparationGeneration,
							preparationWorker = preparationWorker
						) ?: throw RasterLoadUnavailable()
						key to entry
					}
				}
				val values = LinkedHashMap<ReaderPlayLikeCurlRasterKey, CacheEntry<T>>(
					requestedKeys.size
				)
				for ((position, load) in pending.withIndex()) {
					val (key, entry) = load.await()
					values[key] = entry
					onProgress(
						ReaderPlayLikeCurlRasterProgress(
							completed = position + 1,
							total = requestedKeys.size
						)
					)
				}
				values
			}
		} catch (_: RasterLoadUnavailable) {
			abandonPreparationMaterializations(
				preparationWorker = preparationWorker,
				profile = profile,
				preparationGeneration = preparationGeneration
			)
			return null
		}

		return retainDeck(
			profile = profile,
			preparationGeneration = preparationGeneration,
			publicationFence = publicationFence,
			requestedCacheKeys = requestedCacheKeys,
			loaded = loaded
		)
	}

	private suspend fun retainDeck(
		profile: ReaderPlayLikeCurlRasterProfile,
		preparationGeneration: Long,
		publicationFence: ReaderPlayLikeCurlRasterPublicationFence,
		requestedCacheKeys: Set<ReaderPlayLikeCurlRasterCacheKey>,
		loaded: Map<ReaderPlayLikeCurlRasterKey, CacheEntry<T>>
	): ReaderPlayLikeCurlRasterDeck<T>? = withContext(publicationDispatcher) {
		suspendCancellableCoroutine { continuation ->
			val retainedEntries = synchronized(lock) {
				val current =
					!closed &&
						generation == preparationGeneration &&
						activeProfile == profile &&
						requestedCacheKeys.all { key ->
							entries[key] === loaded.entries
								.firstOrNull { item -> item.key.cacheKey == key }
								?.value
						} &&
						runCatching(publicationFence::isCurrent).getOrDefault(false)
				if (!current) return@synchronized null
				loaded.values.distinct().also { entriesToRetain ->
					entriesToRetain.forEach { entry -> entry.retainCount += 1 }
					openDeckRetainers += 1
				}
			}
			val deck = retainedEntries?.let { retained ->
				ReaderPlayLikeCurlRasterDeck(
					profile = profile,
					values = loaded.mapKeys { (key, _) -> key.pageIndex }
						.mapValues { (_, entry) -> entry.owner.value },
					releaseOwnership = { releaseDeckRetainer(retained) }
				)
			}
			continuation.resume(
				deck,
				onCancellation = { _, undelivered, _ -> undelivered?.close() }
			)
		}
	}

	fun updateProtectedPageIndices(
		profile: ReaderPlayLikeCurlRasterProfile,
		pageIndices: List<Int>
	): Boolean {
		val capacityReturned = synchronized(lock) {
			if (closed || activeProfile != profile) return false
			protectedKeys = pageIndices.mapTo(linkedSetOf()) { pageIndex ->
				ReaderPlayLikeCurlRasterCacheKey(profile, pageIndex)
			}
			collectCapacitySignalsLocked()
		}
		dispatchCapacitySignals(capacityReturned)
		return true
	}

	fun hasDecoded(
		profile: ReaderPlayLikeCurlRasterProfile,
		pageIndex: Int
	): Boolean = synchronized(lock) {
		if (closed || activeProfile != profile) return@synchronized false
		val entry = entries[ReaderPlayLikeCurlRasterCacheKey(profile, pageIndex)]
		entry != null && entry.owner.state == DecodedOwnerState.Active
	}

	fun metrics(): ReaderPlayLikeCurlRasterResidencyMetrics = synchronized(lock) {
		assertDecodedCapacityLocked()
		ReaderPlayLikeCurlRasterResidencyMetrics(
			residentEntries = ownedEntryCountLocked(),
			uniqueDecodedBitmaps = decodedOwners.size,
			residentEntryLimit = residentEntryLimit,
			uniqueDecodedBitmapLimit = uniqueDecodedBitmapLimit,
			peakResidentEntries = peakResidentEntries,
			peakUniqueDecodedBitmaps = peakUniqueDecodedBitmaps,
			pinnedEntries =
				(entries.values.asSequence() + retiredEntries.asSequence())
					.count { entry -> entry.retainCount > 0 },
			activePreparationWorkers = activeWorkerCountLocked(WorkerKind.Preparation),
			activeMaterializationWorkers = activeWorkerCountLocked(WorkerKind.Materialization),
			pendingValueReleases = pendingValueReleases,
			evictedEntries = evictedEntries,
			releasedEntries = releasedEntries
		)
	}

	private fun completedNullDeck(): Deferred<ReaderPlayLikeCurlRasterDeck<T>?> =
		CompletableDeferred<ReaderPlayLikeCurlRasterDeck<T>?>().also { result ->
			result.complete(null)
		}

	private suspend fun loadEntry(
		key: ReaderPlayLikeCurlRasterKey,
		preparationGeneration: Long,
		preparationWorker: Job
	): CacheEntry<T>? {
		val acquisitionIsCurrent = synchronized(lock) {
			preparationWorker.isActive &&
				preparationWorker !in preparationCancellationPending &&
				!closed &&
				generation == preparationGeneration &&
				activeProfile == key.profile
		}
		if (!acquisitionIsCurrent || acquisitionInterceptor(key)) return null

		var workerToStart: Job? = null
		val work = synchronized(lock) {
			if (
				!preparationWorker.isActive ||
				preparationWorker in preparationCancellationPending ||
				closed || generation != preparationGeneration ||
				activeProfile != key.profile
			) return null
			entries[key.cacheKey]?.let { entry -> return entry }
			inFlight[key]
				?.takeIf { active -> active.generation == preparationGeneration }
				?.let { active ->
					active.preparationWaiters += preparationWorker
					return@synchronized active
				}
			if (!canAdmitWorkerLocked(WorkerKind.Materialization)) {
				rejectForCapacityLocked(CapacityKind.MaterializationWorkerOrIdentity)
				return null
			}

			val created = InFlight<T>(
				generation = preparationGeneration,
				result = CompletableDeferred()
			)
			created.preparationWaiters += preparationWorker
			lateinit var worker: Job
			worker = scope.launch(start = CoroutineStart.LAZY) {
				materialize(key, created, worker)
			}
			created.worker = worker
			inFlight[key] = created
			worker.invokeOnCompletion {
				synchronized(lock) {
					if (inFlight[key] === created) {
						inFlight.remove(key)
					}
					completeCloseIfDrainedLocked()
				}
				created.result.complete(null)
			}
			registerWorkerLocked(worker, WorkerKind.Materialization)
			workerToStart = worker
			created
		}
		workerToStart?.start()
		return work.result.await()
	}

	private fun cancelPreparationWorker(
		preparationWorker: Job,
		cancellation: CancellationException
	) {
		val abandonedMaterializations = synchronized(lock) {
			preparationCancellationPending += preparationWorker
			collectAbandonedMaterializationsLocked(preparationWorker)
		}
		try {
			preparationWorker.cancel(cancellation)
			abandonedMaterializations.forEach { worker -> worker.cancel(cancellation) }
		} finally {
			synchronized(lock) {
				preparationCancellationPending -= preparationWorker
			}
		}
	}

	private fun abandonPreparationMaterializations(
		preparationWorker: Job,
		profile: ReaderPlayLikeCurlRasterProfile,
		preparationGeneration: Long
	) {
		val cancellation = CancellationException("Raster preparation became unavailable")
		val abandonedMaterializations = synchronized(lock) {
			if (
				closed ||
				generation != preparationGeneration ||
				activeProfile != profile
			) {
				emptyList()
			} else {
				collectAbandonedMaterializationsLocked(preparationWorker)
			}
		}
		abandonedMaterializations.forEach { worker -> worker.cancel(cancellation) }
	}

	private fun collectAbandonedMaterializationsLocked(
		preparationWorker: Job
	): List<Job> = buildList {
		val iterator = inFlight.entries.iterator()
		while (iterator.hasNext()) {
			val (_, work) = iterator.next()
			if (
				work.preparationWaiters.remove(preparationWorker) &&
				work.preparationWaiters.isEmpty()
			) {
				iterator.remove()
				add(work.worker)
			}
		}
	}

	private suspend fun materialize(
		key: ReaderPlayLikeCurlRasterKey,
		work: InFlight<T>,
		worker: Job
	) {
		var value: T? = null
		var cancellation: CancellationException? = null
		var loaderFailure: Throwable? = null
		try {
			value = loader.load(key)
		} catch (cancelled: CancellationException) {
			cancellation = cancelled
		} catch (failure: Throwable) {
			loaderFailure = failure
		}

		val publication = withContext(NonCancellable + publicationDispatcher) {
			val ownersToRelease = mutableListOf<DecodedValueOwner<T>>()
			val residencySlotsToRelease =
				mutableListOf<ReaderPlayLikeCurlRasterResidencyBudget.Slot>()
			val published = synchronized(lock) {
				val ownsInFlight = inFlight[key] === work
				if (ownsInFlight) inFlight.remove(key)
				val owner = ownerForMaterializedValueLocked(worker, value)
				val current =
					ownsInFlight &&
						cancellation == null &&
						loaderFailure == null &&
						value != null &&
						owner != null &&
						!closed &&
						generation == work.generation &&
						activeProfile == key.profile &&
						runCatching(key.publicationFence::isCurrent).getOrDefault(false)
				if (!current) {
					value?.let { stale ->
						scheduleUnadoptedReleaseLocked(stale, ownersToRelease)
					}
					return@synchronized null
				}

				entries[key.cacheKey]?.let { existing ->
					scheduleUnadoptedReleaseLocked(value, ownersToRelease)
					return@synchronized existing
				}

				val mustReplace = ownedEntryCountLocked() >= residentEntryLimit
				val replacement = replacementCandidateLocked()
				val admission = if (mustReplace && replacement == null) {
					ReaderPlayLikeCurlRasterResidencyBudget.Admission.Unavailable
				} else {
					residencyBudget.tryAcquireOrReplace(
						replacement = replacement?.residencySlot,
						allowNew = !mustReplace
					)
				}
				when (admission) {
					ReaderPlayLikeCurlRasterResidencyBudget.Admission.Unavailable -> {
						rejectForCapacityLocked(CapacityKind.ResidentSlot)
						scheduleUnadoptedReleaseLocked(value, ownersToRelease)
						null
					}
					is ReaderPlayLikeCurlRasterResidencyBudget.Admission.Acquired -> {
						val entry = CacheEntry(
							key = key.cacheKey,
							owner = checkNotNull(owner),
							residencySlot = admission.slot
						)
						attachEntryLocked(entry)
						check(entries.put(key.cacheKey, entry) == null)
						updateResidencyPeaksLocked()
						onOwnershipMutated()
						entry
					}
					is ReaderPlayLikeCurlRasterResidencyBudget.Admission.Replaced -> {
						val candidate = checkNotNull(replacement)
						check(candidate.residencySlot === admission.slot)
						val entry = CacheEntry(
							key = key.cacheKey,
							owner = checkNotNull(owner),
							residencySlot = admission.slot
						)
						attachEntryLocked(entry)
						check(entries.remove(candidate.key) === candidate)
						evictedEntries += 1L
						detachEntryLocked(candidate)?.let { detachedOwner ->
							scheduleReleaseLocked(
								detachedOwner,
								ownersToRelease,
								notifyOwnership = false
							)
						}
						check(entries.put(key.cacheKey, entry) == null)
						updateResidencyPeaksLocked()
						onOwnershipMutated()
						entry
					}
				}
			}
			PublicationResult(
				entry = published,
				ownersToRelease = ownersToRelease,
				residencySlotsToRelease = residencySlotsToRelease
			).also { ownership ->
				releaseScheduledOwners(ownership.ownersToRelease)
				releaseResidencySlots(ownership.residencySlotsToRelease)
			}
		}
		val failure = loaderFailure
		if (failure == null) {
			work.result.complete(publication.entry)
		} else {
			work.result.completeExceptionally(failure)
		}
		cancellation?.let { cancelled -> throw cancelled }
	}

	private fun drainReleasedValuesLocked() {
		while (true) {
			val released = releasedValueQueue.poll() ?: return
			releasedValues.remove(released)
		}
	}

	private fun wasReleasedLocked(value: T): Boolean {
		drainReleasedValuesLocked()
		return ReleasedValueReference(value) in releasedValues
	}

	private fun rememberReleasedLocked(value: T) {
		drainReleasedValuesLocked()
		check(releasedValues.add(ReleasedValueReference(value, releasedValueQueue)))
	}

	private fun ownerForMaterializedValueLocked(
		worker: Job,
		value: T?
	): DecodedValueOwner<T>? {
		val ownership = checkNotNull(workers[worker])
		check(ownership.kind == WorkerKind.Materialization)
		check(ownership.materializationReservationHeld)
		check(materializationValueReservations > 0)
		ownership.materializationReservationHeld = false
		materializationValueReservations -= 1

		if (value == null) {
			assertDecodedCapacityLocked()
			return null
		}
		if (wasReleasedLocked(value)) {
			assertDecodedCapacityLocked()
			return null
		}
		val existing = decodedOwners[value]
		if (existing != null) {
			assertDecodedCapacityLocked()
			return when (existing.state) {
				DecodedOwnerState.Active -> existing
				DecodedOwnerState.Releasing -> null
				DecodedOwnerState.Released -> error("Released decoded owner remained indexed")
			}
		}
		check(decodedOwners.size < uniqueDecodedBitmapLimit)
		return DecodedValueOwner(value).also { owner ->
			decodedOwners[value] = owner
			updateResidencyPeaksLocked()
			assertDecodedCapacityLocked()
			onOwnershipMutated()
		}
	}

	private fun attachEntryLocked(entry: CacheEntry<T>) {
		check(entry.owner.state == DecodedOwnerState.Active)
		entry.owner.entryReferences += 1
	}

	private fun detachEntryLocked(entry: CacheEntry<T>): DecodedValueOwner<T>? {
		val owner = entry.owner
		check(owner.state == DecodedOwnerState.Active)
		check(owner.entryReferences > 0)
		owner.entryReferences -= 1
		if (owner.entryReferences != 0) return null
		owner.state = DecodedOwnerState.Releasing
		releasedEntries += 1L
		return owner
	}

	private fun scheduleUnadoptedReleaseLocked(
		value: T,
		scheduled: MutableList<DecodedValueOwner<T>>
	) {
		if (wasReleasedLocked(value)) return
		val owner = checkNotNull(decodedOwners[value]) {
			"Materialized value was not converted from its reservation"
		}
		when (owner.state) {
			DecodedOwnerState.Active -> {
				if (owner.entryReferences != 0) return
				owner.state = DecodedOwnerState.Releasing
				releasedEntries += 1L
				scheduleReleaseLocked(owner, scheduled)
			}
			DecodedOwnerState.Releasing -> Unit
			DecodedOwnerState.Released -> error("Released decoded owner remained indexed")
		}
	}

	private fun scheduleReleaseLocked(
		owner: DecodedValueOwner<T>,
		scheduled: MutableList<DecodedValueOwner<T>>,
		notifyOwnership: Boolean = true
	) {
		check(owner.state == DecodedOwnerState.Releasing)
		pendingValueReleases += 1
		scheduled += owner
		if (notifyOwnership) onOwnershipMutated()
	}

	private fun releaseScheduledOwners(owners: List<DecodedValueOwner<T>>) {
		owners.forEach { owner ->
			var callbackFailure: Throwable? = null
			try {
				release(owner.value)
			} catch (failure: Throwable) {
				callbackFailure = failure
			} finally {
				val capacityReturned = synchronized(lock) {
					callbackFailure?.let(::recordReleaseFailureLocked)
					check(owner.state == DecodedOwnerState.Releasing)
					check(decodedOwners.remove(owner.value) === owner)
					rememberReleasedLocked(owner.value)
					owner.state = DecodedOwnerState.Released
					if (pendingValueReleases <= 0) {
						recordReleaseFailureLocked(
							IllegalStateException(
								"Decoded release completed without pending ownership"
							)
						)
						pendingValueReleases = 0
					} else {
						pendingValueReleases -= 1
					}
					onOwnershipMutated()
					collectCapacitySignalsLocked().also {
						completeCloseIfDrainedLocked()
					}
				}
				dispatchCapacitySignals(capacityReturned)
			}
		}
	}

	private fun retireResidentEntriesLocked(
		ownersToRelease: MutableList<DecodedValueOwner<T>>,
		residencySlotsToRelease: MutableList<ReaderPlayLikeCurlRasterResidencyBudget.Slot>
	) {
		val previous = entries.values.toList()
		entries.clear()
		var ownershipChanged = false
		previous.forEach { entry ->
			if (entry.retainCount > 0) {
				check(retiredEntries.add(entry))
			} else {
				ownershipChanged = true
				detachResidentEntryLocked(entry, residencySlotsToRelease)?.let { owner ->
					scheduleReleaseLocked(
						owner,
						ownersToRelease,
						notifyOwnership = false
					)
				}
			}
		}
		if (ownershipChanged) onOwnershipMutated()
	}

	private fun replacementCandidateLocked(): CacheEntry<T>? =
		entries.entries.firstOrNull { (_, entry) ->
			entry.retainCount == 0 && entry.key !in protectedKeys
		}?.value

	private fun detachResidentEntryLocked(
		entry: CacheEntry<T>,
		residencySlotsToRelease: MutableList<ReaderPlayLikeCurlRasterResidencyBudget.Slot>
	): DecodedValueOwner<T>? {
		pendingResidencySlotReleases += 1
		residencySlotsToRelease += entry.residencySlot
		return detachEntryLocked(entry)
	}

	private fun detachEntryIfRetiredLocked(
		entry: CacheEntry<T>,
		residencySlotsToRelease: MutableList<ReaderPlayLikeCurlRasterResidencyBudget.Slot>
	): DecodedValueOwner<T>? {
		if (entry.retainCount != 0 || entry !in retiredEntries) return null
		check(retiredEntries.remove(entry))
		return detachResidentEntryLocked(entry, residencySlotsToRelease)
	}

	private fun releaseResidencySlots(
		slots: List<ReaderPlayLikeCurlRasterResidencyBudget.Slot>
	) {
		slots.forEach { slot ->
			var releaseFailure: Throwable? = null
			try {
				residencyBudget.release(slot)
			} catch (failure: Throwable) {
				releaseFailure = failure
			} finally {
				val capacityReturned = synchronized(lock) {
					releaseFailure?.let(::recordReleaseFailureLocked)
					if (pendingResidencySlotReleases <= 0) {
						recordReleaseFailureLocked(
							IllegalStateException(
								"Raster residency slot completed without pending ownership"
							)
						)
						pendingResidencySlotReleases = 0
					} else {
						pendingResidencySlotReleases -= 1
					}
					collectCapacitySignalsLocked().also {
						completeCloseIfDrainedLocked()
					}
				}
				dispatchCapacitySignals(capacityReturned)
			}
		}
	}

	private fun releaseDeckEntries(deckEntries: List<CacheEntry<T>>) {
		val ownersToRelease = mutableListOf<DecodedValueOwner<T>>()
		val residencySlotsToRelease =
			mutableListOf<ReaderPlayLikeCurlRasterResidencyBudget.Slot>()
		synchronized(lock) {
			var ownershipChanged = false
			deckEntries.forEach { entry ->
				if (entry.retainCount <= 0) {
					recordReleaseFailureLocked(
						IllegalStateException(
							"PlayLikeCurl raster deck released without ownership"
						)
					)
				} else {
					val releasesRetiredEntry =
						entry.retainCount == 1 && entry in retiredEntries
					entry.retainCount -= 1
					try {
						val retiredOwner = detachEntryIfRetiredLocked(
							entry,
							residencySlotsToRelease
						)
						if (releasesRetiredEntry) ownershipChanged = true
						retiredOwner?.let { owner ->
							scheduleReleaseLocked(
								owner,
								ownersToRelease,
								notifyOwnership = false
							)
						}
					} catch (failure: Throwable) {
						recordReleaseFailureLocked(failure)
					}
				}
			}
			if (ownershipChanged) onOwnershipMutated()
		}
		releaseScheduledOwners(ownersToRelease)
		releaseResidencySlots(residencySlotsToRelease)
	}

	private fun releaseDeckRetainer(deckEntries: List<CacheEntry<T>>) {
		try {
			releaseDeckEntries(deckEntries)
		} finally {
			val capacityReturned = synchronized(lock) {
				if (openDeckRetainers <= 0) {
					recordReleaseFailureLocked(
						IllegalStateException("Raster deck retainer completed more than once")
					)
					openDeckRetainers = 0
				} else {
					openDeckRetainers -= 1
				}
				collectCapacitySignalsLocked().also {
					completeCloseIfDrainedLocked()
				}
			}
			dispatchCapacitySignals(capacityReturned)
		}
	}

	private fun activeWorkerCountLocked(kind: WorkerKind): Int =
		workers.values.count { ownership -> ownership.kind == kind }

	private fun canAdmitWorkerLocked(kind: WorkerKind): Boolean = when (kind) {
		WorkerKind.Preparation ->
			activeWorkerCountLocked(kind) < preparationWorkerLimit
		WorkerKind.Materialization ->
			activeWorkerCountLocked(kind) < materializationWorkerLimit &&
				decodedOwners.size + materializationValueReservations <
					uniqueDecodedBitmapLimit
	}

	private fun <J : Job> registerWorkerLocked(
		worker: J,
		kind: WorkerKind,
		preparationResult: CompletableDeferred<ReaderPlayLikeCurlRasterDeck<T>?>? = null
	): J {
		check(!closed) { "PlayLikeCurl raster adapter is closed" }
		check((kind == WorkerKind.Preparation) == (preparationResult != null))
		check(canAdmitWorkerLocked(kind)) {
			"PlayLikeCurl raster worker capacity exceeded"
		}
		val ownsReservation = kind == WorkerKind.Materialization
		if (ownsReservation) {
			materializationValueReservations += 1
		}
		workers[worker] = WorkerOwnership(
			kind = kind,
			materializationReservationHeld = ownsReservation,
			preparationResult = preparationResult
		)
		assertDecodedCapacityLocked()
		worker.invokeOnCompletion { workerFailure ->
			var abandonedPreparation:
				CompletableDeferred<ReaderPlayLikeCurlRasterDeck<T>?>? = null
			val capacityReturned = synchronized(lock) {
				val ownership = workers.remove(worker)
				abandonedPreparation = ownership?.preparationResult
				if (ownership != null && ownership.materializationReservationHeld) {
					check(materializationValueReservations > 0)
					ownership.materializationReservationHeld = false
					materializationValueReservations -= 1
				}
				workerFailure?.takeUnless { it is CancellationException }
					?.let(::recordReleaseFailureLocked)
				assertDecodedCapacityLocked()
				collectCapacitySignalsLocked().also {
					completeCloseIfDrainedLocked()
				}
			}
			abandonedPreparation?.complete(null)
			dispatchCapacitySignals(capacityReturned)
		}
		return worker
	}

	private fun ownedEntryCountLocked(): Int = entries.size + retiredEntries.size

	private fun updateResidencyPeaksLocked() {
		peakResidentEntries = maxOf(peakResidentEntries, ownedEntryCountLocked())
		peakUniqueDecodedBitmaps = maxOf(peakUniqueDecodedBitmaps, decodedOwners.size)
	}

	private fun assertDecodedCapacityLocked() {
		check(materializationValueReservations >= 0)
		check(
			decodedOwners.size + materializationValueReservations <= uniqueDecodedBitmapLimit
		) { "PlayLikeCurl decoded identity capacity exceeded" }
	}

	private fun rejectForCapacityLocked(kind: CapacityKind) {
		if (!closed) blockedCapacities += kind
	}

	private fun residentSlotAvailableLocked(): Boolean =
		ownedEntryCountLocked() < residentEntryLimit ||
			entries.values.any { entry ->
				entry.retainCount == 0 && entry.key !in protectedKeys
			}

	private fun capacityAvailableLocked(kind: CapacityKind): Boolean = when (kind) {
		CapacityKind.PreparationWorker -> canAdmitWorkerLocked(WorkerKind.Preparation)
		CapacityKind.MaterializationWorkerOrIdentity ->
			canAdmitWorkerLocked(WorkerKind.Materialization)
		CapacityKind.ResidentSlot -> residentSlotAvailableLocked()
	}

	private fun collectCapacitySignalsLocked(): Set<CapacityKind> {
		if (closed) {
			blockedCapacities.clear()
			return emptySet()
		}
		val available = blockedCapacities
			.filterTo(linkedSetOf(), ::capacityAvailableLocked)
		blockedCapacities.removeAll(available)
		return available
	}

	private fun rearmCapacitySignalsLocked(kinds: Set<CapacityKind>) {
		if (!closed) blockedCapacities += kinds
	}

	private fun dispatchCapacitySignals(returned: Set<CapacityKind>) {
		if (returned.isEmpty()) return
		val accepted = try {
			onCapacityAvailable()
		} catch (failure: Throwable) {
			synchronized(lock) { recordReleaseFailureLocked(failure) }
			false
		}
		if (!accepted) {
			synchronized(lock) { rearmCapacitySignalsLocked(returned) }
		}
	}

	private fun recordReleaseFailureLocked(next: Throwable) {
		val first = releaseFailure
		if (first == null) {
			releaseFailure = next
		} else if (next !== first) {
			first.addSuppressed(next)
		}
	}

	private fun completeCloseIfDrainedLocked() {
		if (
			!closed || workers.isNotEmpty() || inFlight.isNotEmpty() ||
			openDeckRetainers != 0 || materializationValueReservations != 0 ||
			pendingValueReleases != 0 || pendingResidencySlotReleases != 0
		) return

		if (ownedEntryCountLocked() != 0 || decodedOwners.isNotEmpty()) {
			recordReleaseFailureLocked(
				IllegalStateException(
					"PlayLikeCurl adapter retained decoded ownership after drain"
				)
			)
		}
		val failure = releaseFailure
		if (failure == null) {
			closedSignal.complete(Unit)
		} else {
			closedSignal.completeExceptionally(failure)
		}
	}

	override fun close() {
		val staleWaiters = mutableListOf<CompletableDeferred<CacheEntry<T>?>>()
		val stalePreparations =
			mutableListOf<CompletableDeferred<ReaderPlayLikeCurlRasterDeck<T>?>>()
		val ownersToRelease = mutableListOf<DecodedValueOwner<T>>()
		val residencySlotsToRelease =
			mutableListOf<ReaderPlayLikeCurlRasterResidencyBudget.Slot>()
		synchronized(lock) {
			if (closed) return
			closed = true
			generation += 1L
			activeProfile = null
			protectedKeys = emptySet()
			blockedCapacities.clear()
			inFlight.values.mapTo(staleWaiters) { work -> work.result }
			workers.values.mapNotNullTo(stalePreparations) { ownership ->
				ownership.preparationResult
			}
			retireResidentEntriesLocked(ownersToRelease, residencySlotsToRelease)
		}
		staleWaiters.forEach { waiter -> waiter.complete(null) }
		stalePreparations.forEach { preparation -> preparation.complete(null) }
		releaseScheduledOwners(ownersToRelease)
		releaseResidencySlots(residencySlotsToRelease)
		synchronized(lock) { completeCloseIfDrainedLocked() }
	}

	suspend fun closeAndJoin() {
		close()
		withContext(NonCancellable) {
			closedSignal.await()
		}
	}
}
