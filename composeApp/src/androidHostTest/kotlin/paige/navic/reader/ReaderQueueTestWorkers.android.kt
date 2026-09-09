package paige.navic.reader

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertTrue

internal actual fun runReaderQueueWorkers(workers: Int, block: (Int) -> Unit) {
	val start = CountDownLatch(1)
	val done = CountDownLatch(workers)
	val failed = BooleanArray(workers)
	repeat(workers) { index ->
		thread(isDaemon = true, name = "reader-queue-test-worker") {
			try {
				start.await()
				block(index)
			} catch (_: Throwable) {
				failed[index] = true
			} finally {
				done.countDown()
			}
		}
	}
	start.countDown()
	assertTrue(done.await(10, TimeUnit.SECONDS), "reader_queue_workers_timed_out")
	assertTrue(failed.none { it }, "reader_queue_worker_assertion_failed")
}
