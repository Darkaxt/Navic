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

internal class ReaderPageRasterPublicationScheduler(
	private val scope: CoroutineScope,
	val maxConcurrentWorkers: Int = 1
) {
	private val lock = Any()
	private val permits = Semaphore(maxConcurrentWorkers)
	private val jobs =
		linkedMapOf<ReaderPageRasterPublicationRequest, Job>()
	private val closedSignal = CompletableDeferred<Unit>()
	private var activeWorkers = 0
	private var closed = false

	init {
		require(maxConcurrentWorkers > 0)
	}

	fun schedule(
		request: ReaderPageRasterPublicationRequest,
		worker: suspend () -> Unit
	) {
		val job = synchronized(lock) {
			check(!closed) { "Publication scheduler is closed" }
			check(request !in jobs) {
				"Publication request already scheduled: $request"
			}
			scope.launch(start = CoroutineStart.LAZY) {
				permits.withPermit {
					synchronized(lock) { activeWorkers += 1 }
					try {
						worker()
					} finally {
						synchronized(lock) { activeWorkers -= 1 }
					}
				}
			}.also { created ->
				jobs[request] = created
			}
		}
		job.invokeOnCompletion {
			synchronized(lock) {
				if (jobs[request] === job) jobs.remove(request)
			}
		}
		job.start()
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
