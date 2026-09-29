package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind
import java.util.IdentityHashMap

internal class ReaderPageRasterHydrationScheduler(
	private val scope: CoroutineScope,
	val maxConcurrentWorkers: Int = 2,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator()
) {
	private val lock = Any()
	private val permits = Semaphore(maxConcurrentWorkers.also { require(it > 0) })
	private val jobs = linkedSetOf<Job>()
	private val jobTokens = IdentityHashMap<Job, ReaderLegacySourceLocalOpaqueToken>()
	private val activeJobs = java.util.Collections.newSetFromMap(IdentityHashMap<Job, Boolean>())
	private val completedJobs = java.util.Collections.newSetFromMap(IdentityHashMap<Job, Boolean>())
	private val drainConfirmations = IdentityHashMap<Job, () -> Unit>()
	private val closedSignal = CompletableDeferred<Unit>()
	private var closed = false
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private var activeWorkers = 0

	val activeWorkerCount: Int
		get() = synchronized(lock) { activeWorkers }

	fun schedule(worker: suspend () -> Unit): Job? {
		val job = synchronized(lock) {
			if (closed || frozenDomain != null) {
				null
			} else {
				scope.launch(start = CoroutineStart.LAZY) {
					permits.withPermit {
						synchronized(lock) {
							activeWorkers += 1
							activeJobs += coroutineContext[Job]!!
						}
						try {
							worker()
						} finally {
							synchronized(lock) {
								activeWorkers -= 1
								activeJobs -= coroutineContext[Job]!!
							}
						}
					}
				}.also { ownedJob ->
					jobs += ownedJob
					jobTokens[ownedJob] = tokenAllocator.allocate()
				}
			}
		} ?: return null
		job.invokeOnCompletion {
			val confirmation = synchronized(lock) {
				activeJobs.remove(job)
				val pending = drainConfirmations.remove(job)
				if (pending != null || frozenDomain == null) {
					jobs.remove(job)
					jobTokens.remove(job)
					completedJobs.remove(job)
				} else {
					completedJobs += job
				}
				pending
			}
			confirmation?.invoke()
		}
		job.start()
		return job
	}

	fun freezeForTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		when {
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
		jobs.map { job ->
			val identity = ReaderLegacyPhysicalIdentity(
				domain = domain,
				source = ReaderLegacyInventorySource.RasterHydration,
				sourceLocalToken = checkNotNull(jobTokens[job])
			)
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = identity,
				kind = ReaderTransitionResourceKind.Raster,
				binding = null,
				visibleOwner = null,
				origin = ReaderLegacyResourceOrigin.Owned,
				state = when {
					job in drainConfirmations -> ReaderLegacyResourceState.ReleaseRequested
					job in completedJobs -> ReaderLegacyResourceState.Released
					job in activeJobs -> ReaderLegacyResourceState.Running
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
		var confirmNow = false
		val job = synchronized(lock) {
			val domain = frozenDomain
			if (
				domain == null ||
				physicalIdentity.domain != domain ||
				physicalIdentity.source != ReaderLegacyInventorySource.RasterHydration
			) {
				return@synchronized null
			}
			val matched = jobs.firstOrNull { ownedJob ->
				jobTokens[ownedJob] == physicalIdentity.sourceLocalToken
			} ?: return@synchronized null
			if (matched in drainConfirmations) return@synchronized null
			if (completedJobs.remove(matched)) {
				jobs.remove(matched)
				jobTokens.remove(matched)
				confirmNow = true
			} else {
				drainConfirmations[matched] = { onConfirmed(physicalIdentity) }
			}
			matched
		} ?: return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		if (confirmNow) onConfirmed(physicalIdentity) else job.cancel()
		return ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		if (
			frozenDomain != domain ||
			closed ||
			drainConfirmations.isNotEmpty()
		) {
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		} else {
			jobs.removeAll(completedJobs)
			completedJobs.forEach(jobTokens::remove)
			completedJobs.clear()
			frozenDomain = null
			ReaderPortCommandResult.Accepted
		}
	}

	suspend fun closeAndJoin() {
		val closing = synchronized(lock) {
			if (closed) null
			else {
				closed = true
				jobs.toList()
			}
		}
		if (closing != null) {
			withContext(NonCancellable) {
				try {
					closing.forEach(Job::cancel)
					closing.joinAll()
				} finally {
					closedSignal.complete(Unit)
				}
			}
		}
		closedSignal.await()
	}
}
