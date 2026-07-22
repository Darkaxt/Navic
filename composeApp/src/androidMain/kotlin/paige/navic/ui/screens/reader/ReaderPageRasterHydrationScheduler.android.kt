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

internal class ReaderPageRasterHydrationScheduler(
	private val scope: CoroutineScope,
	val maxConcurrentWorkers: Int = 2
) {
	private val lock = Any()
	private val permits = Semaphore(maxConcurrentWorkers.also { require(it > 0) })
	private val jobs = linkedSetOf<Job>()
	private val closedSignal = CompletableDeferred<Unit>()
	private var closed = false
	private var activeWorkers = 0

	val activeWorkerCount: Int
		get() = synchronized(lock) { activeWorkers }

	fun schedule(worker: suspend () -> Unit): Job? {
		val job = synchronized(lock) {
			if (closed) {
				null
			} else {
				scope.launch(start = CoroutineStart.LAZY) {
					permits.withPermit {
						synchronized(lock) { activeWorkers += 1 }
						try {
							worker()
						} finally {
							synchronized(lock) { activeWorkers -= 1 }
						}
					}
				}.also(jobs::add)
			}
		} ?: return null
		job.invokeOnCompletion {
			synchronized(lock) { jobs.remove(job) }
		}
		job.start()
		return job
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
