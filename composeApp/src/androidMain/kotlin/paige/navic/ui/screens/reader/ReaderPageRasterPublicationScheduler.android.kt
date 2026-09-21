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

internal class ReaderPageRasterPublicationScheduler(
	private val scope: CoroutineScope,
	val maxConcurrentWorkers: Int = 1,
	private val tokenAllocator: ReaderLegacySourceLocalTokenAllocator =
		ReaderLegacySourceLocalTokenAllocator()
) {
	private val lock = Any()
	private val permits = Semaphore(maxConcurrentWorkers)
	private val jobs =
		linkedMapOf<ReaderPageRasterPublicationRequest, Job>()
	private val jobTokens = IdentityHashMap<Job, ReaderLegacySourceLocalOpaqueToken>()
	private val activeJobs = java.util.Collections.newSetFromMap(IdentityHashMap<Job, Boolean>())
	private val drainConfirmations = IdentityHashMap<Job, () -> Unit>()
	private val closedSignal = CompletableDeferred<Unit>()
	private var activeWorkers = 0
	private var closed = false
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null

	init {
		require(maxConcurrentWorkers > 0)
	}

	fun schedule(
		request: ReaderPageRasterPublicationRequest,
		worker: suspend () -> Unit
	): ReaderPortCommandResult {
		val job = synchronized(lock) {
			check(!closed) { "Publication scheduler is closed" }
			if (frozenDomain != null) return@synchronized null
			check(request !in jobs) {
				"Publication request already scheduled: $request"
			}
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
			}.also { created ->
				jobs[request] = created
				jobTokens[created] = tokenAllocator.allocate()
			}
		} ?: return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		job.invokeOnCompletion {
			val confirmation = synchronized(lock) {
				if (jobs[request] === job) jobs.remove(request)
				jobTokens.remove(job)
				activeJobs.remove(job)
				drainConfirmations.remove(job)
			}
			confirmation?.invoke()
		}
		job.start()
		return ReaderPortCommandResult.Accepted
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
		jobs.values.map { job ->
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = ReaderLegacyPhysicalIdentity(
					domain = domain,
					source = ReaderLegacyInventorySource.RasterPublication,
					sourceLocalToken = checkNotNull(jobTokens[job])
				),
				kind = ReaderTransitionResourceKind.Raster,
				binding = null,
				visibleOwner = null,
				origin = ReaderLegacyResourceOrigin.Owned,
				state = when {
					job in drainConfirmations -> ReaderLegacyResourceState.ReleaseRequested
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
		val job = synchronized(lock) {
			val domain = frozenDomain
			if (
				domain == null ||
				physicalIdentity.domain != domain ||
				physicalIdentity.source != ReaderLegacyInventorySource.RasterPublication
			) {
				return@synchronized null
			}
			val matched = jobs.values.firstOrNull { ownedJob ->
				jobTokens[ownedJob] == physicalIdentity.sourceLocalToken
			} ?: return@synchronized null
			if (matched in drainConfirmations) return@synchronized null
			drainConfirmations[matched] = { onConfirmed(physicalIdentity) }
			matched
		} ?: return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		job.cancel()
		return ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		if (frozenDomain != domain || closed) {
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		} else {
			frozenDomain = null
			ReaderPortCommandResult.Accepted
		}
	}

	fun cancelBeforeEpoch(currentEpoch: Long) {
		val staleJobs = synchronized(lock) {
			jobs.filterKeys { request ->
				request.epoch < currentEpoch
			}.values.toList()
		}
		staleJobs.forEach(Job::cancel)
	}

	fun activeWorkerCount(): Int =
		synchronized(lock) { activeWorkers }

	suspend fun closeAndJoin() {
		val closingJobs = synchronized(lock) {
			if (closed) {
				null
			} else {
				closed = true
				jobs.values.toList()
			}
		}
		if (closingJobs != null) {
			withContext(NonCancellable) {
				try {
					closingJobs.forEach(Job::cancel)
					closingJobs.joinAll()
				} finally {
					closedSignal.complete(Unit)
				}
			}
		}
		closedSignal.await()
	}
}
