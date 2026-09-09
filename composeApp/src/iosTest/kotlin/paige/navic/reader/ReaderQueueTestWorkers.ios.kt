@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package paige.navic.reader

import platform.Foundation.NSOperationQueue
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import kotlin.test.assertTrue

/** Executes the shared concurrency assertions on Native threads, not a JVM lock substitute. */
internal actual fun runReaderQueueWorkers(workers: Int, block: (Int) -> Unit) {
	val queue = NSOperationQueue().apply { maxConcurrentOperationCount = workers.toLong() }
	val start = dispatch_semaphore_create(0)
	val done = dispatch_semaphore_create(0)
	val failed = BooleanArray(workers)
	repeat(workers) { index ->
		queue.addOperationWithBlock {
			try {
				dispatch_semaphore_wait(start, DISPATCH_TIME_FOREVER)
				block(index)
			} catch (_: Throwable) {
				failed[index] = true
			} finally {
				dispatch_semaphore_signal(done)
			}
		}
	}
	repeat(workers) { dispatch_semaphore_signal(start) }
	val deadline = dispatch_time(DISPATCH_TIME_NOW, 10_000_000_000L)
	repeat(workers) {
		assertTrue(dispatch_semaphore_wait(done, deadline) == 0L, "reader_queue_workers_timed_out")
	}
	assertTrue(failed.none { it }, "reader_queue_worker_assertion_failed")
}
