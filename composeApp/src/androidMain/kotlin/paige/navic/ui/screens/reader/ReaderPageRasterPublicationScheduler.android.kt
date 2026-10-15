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
	private data class JobCustody(
		val token: ReaderLegacySourceLocalOpaqueToken,
		val work: ReaderActivatedRasterWork?
	)

	private val jobTokens = IdentityHashMap<Job, JobCustody>()
	private val activeJobs = java.util.Collections.newSetFromMap(IdentityHashMap<Job, Boolean>())
	private val completedJobs = java.util.Collections.newSetFromMap(IdentityHashMap<Job, Boolean>())
	private val drainConfirmations = IdentityHashMap<Job, () -> Unit>()
	private val closedSignal = CompletableDeferred<Unit>()
	private var activeWorkers = 0
	private var closed = false
	private var frozenDomain: ReaderLegacyPhysicalDomain? = null
	private var legacyFinalized = false

	private fun removeJobLocked(job: Job) {
		jobs.entries.firstOrNull { (_, owned) -> owned === job }
			?.key
			?.let(jobs::remove)
		jobTokens.remove(job)
		completedJobs.remove(job)
	}

	init {
		require(maxConcurrentWorkers > 0)
	}

	fun schedule(
		request: ReaderPageRasterPublicationRequest,
		worker: suspend () -> Unit
	): ReaderPortCommandResult = scheduleOwned(request, null, worker)

	fun scheduleInstalled(
		request: ReaderPageRasterPublicationRequest,
		work: ReaderActivatedRasterWork,
		worker: suspend () -> Unit
	): ReaderPortCommandResult = scheduleOwned(request, work, worker)

	private fun scheduleOwned(
		request: ReaderPageRasterPublicationRequest,
		work: ReaderActivatedRasterWork?,
		worker: suspend () -> Unit
	): ReaderPortCommandResult {
		if (work != null && !work.isCurrent()) return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		val job = synchronized(lock) {
			check(!closed) { "Publication scheduler is closed" }
			val admitted = if (work == null) frozenDomain == null else
				legacyFinalized && frozenDomain == work.domain && work.installationIsCurrent()
			if (!admitted) return@synchronized null
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
				jobTokens[created] = JobCustody(tokenAllocator.allocate(), work)
			}
		} ?: return ReaderPortCommandResult.Rejected(
			ReaderTransitionFailureReason.InvalidLegacyResource
		)
		job.invokeOnCompletion {
			val confirmation = synchronized(lock) {
				activeJobs.remove(job)
				val pending = drainConfirmations.remove(job)
				if (pending != null || frozenDomain == null || work != null) {
					removeJobLocked(job)
				} else {
					completedJobs += job
				}
				pending
			}
			confirmation?.invoke()
		}
		job.start()
		return ReaderPortCommandResult.Accepted
	}

	fun releaseInstalled(work: ReaderActivatedRasterWork, onReleased: () -> Unit): ReaderPortCommandResult {
		val selected = synchronized(lock) {
			if (!legacyFinalized || frozenDomain != work.domain) return ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
			jobs.values.filter { jobTokens[it]?.work === work }
		}
		if (selected.isEmpty()) {
			onReleased()
		} else {
			val pending = java.util.concurrent.atomic.AtomicInteger(selected.size)
			selected.forEach { job ->
				job.invokeOnCompletion { if (pending.decrementAndGet() == 0) onReleased() }
			}
			selected.forEach(Job::cancel)
		}
		return ReaderPortCommandResult.Accepted
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

	internal val originalLegacyAdmissionRestored: Boolean
		get() = synchronized(lock) { !closed && frozenDomain == null }

	fun connectedFrozenOwnership(): ReaderLegacyConnectedSourceInventory? = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized null
		ReaderLegacyConnectedSourceInventory(
			ReaderLegacyInventorySource.RasterPublication, domain, snapshotFrozenOwnership()
		)
	}

	fun snapshotFrozenOwnership(): List<ReaderFrozenLegacyResource> = synchronized(lock) {
		val domain = frozenDomain ?: return@synchronized emptyList()
		jobs.values.filter { checkNotNull(jobTokens[it]).work == null }.map { job ->
			ReaderFrozenLegacyResource(
				freezeToken = domain.freezeToken,
				physicalIdentity = ReaderLegacyPhysicalIdentity(
					domain = domain,
					source = ReaderLegacyInventorySource.RasterPublication,
					sourceLocalToken = checkNotNull(jobTokens[job]).token
				),
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
				physicalIdentity.source != ReaderLegacyInventorySource.RasterPublication
			) {
				return@synchronized null
			}
			val matched = jobs.values.firstOrNull { ownedJob ->
				jobTokens[ownedJob]?.let { it.work == null && it.token == physicalIdentity.sourceLocalToken } == true
			} ?: return@synchronized null
			if (matched in drainConfirmations) return@synchronized null
			if (completedJobs.remove(matched)) {
				removeJobLocked(matched)
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

	fun finalizeActivatedHandoff(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult = synchronized(lock) {
		if (closed || legacyFinalized || frozenDomain != domain || jobs.isNotEmpty() || activeWorkers != 0 ||
			drainConfirmations.isNotEmpty())
			return@synchronized ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource)
		legacyFinalized = true
		ReaderPortCommandResult.Accepted
	}

	fun restoreAfterTransitionActivation(
		domain: ReaderLegacyPhysicalDomain
	): ReaderPortCommandResult = synchronized(lock) {
		if (
			legacyFinalized || frozenDomain != domain ||
			closed ||
			drainConfirmations.isNotEmpty()
		) {
			ReaderPortCommandResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			)
		} else {
			completedJobs.toList().forEach(::removeJobLocked)
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
