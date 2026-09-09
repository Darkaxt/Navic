package paige.navic.domain.manager

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import paige.navic.data.database.entities.DownloadEntity

/** Claim and registration share one lock; cancellation can see every admitted job. */
internal class DownloadWorkCoordinator {
	private val mutex = Mutex()
	private val jobs = mutableMapOf<String, Job>()

	suspend fun <T> serialized(block: suspend () -> T): T = mutex.withLock { block() }

	suspend fun runNext(
		claim: suspend (Set<String>) -> DownloadEntity?,
		process: suspend (DownloadEntity) -> Unit
	): Boolean = coroutineScope {
		val work = mutex.withLock {
			val intent = claim(jobs.keys.toSet()) ?: return@withLock null
			val job = launch(start = CoroutineStart.LAZY) { process(intent) }
			jobs[intent.songId] = job
			intent.songId to job
		} ?: return@coroutineScope false
		val (songId, job) = work
		try {
			job.start()
			job.join()
		} finally {
			withContext(NonCancellable) {
				job.cancelAndJoin()
				mutex.withLock { if (jobs[songId] === job) jobs.remove(songId) }
			}
		}
		true
	}

	suspend fun cancel(
		songIds: Collection<String>,
		markCancelled: suspend (List<String>) -> List<DownloadEntity>
	): List<DownloadEntity> {
		val (rows, cancelledJobs) = mutex.withLock {
			val ids = songIds.distinct()
			markCancelled(ids) to ids.mapNotNull(jobs::get)
		}
		cancelledJobs.forEach { it.cancel() }
		cancelledJobs.forEach { it.join() }
		return rows
	}
}
