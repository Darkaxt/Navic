package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderRelocationMonitorTest {
	@Test
	fun recursiveAcquisitionPreservesReturnValue() {
		val monitor = ReaderRelocationMonitor()
		runReaderQueueWorkers(1) {
			assertEquals(42, monitor.withLock { monitor.withLock { monitor.withLock { 42 } } })
		}
	}

	@Test
	fun nestedExceptionPreservesIdentityAndUnlocksForOtherThreads() {
		val monitor = ReaderRelocationMonitor()
		val failure = MonitorFailure()
		val caught = runCatching {
			monitor.withLock { monitor.withLock { throw failure } }
		}.exceptionOrNull()
		assertTrue(caught === failure)
		runReaderQueueWorkers { assertEquals(7, monitor.withLock { 7 }) }
	}

	@Test
	fun contendingThreadsCannotLoseUpdates() {
		val monitor = ReaderRelocationMonitor()
		var count = 0
		runReaderQueueWorkers {
			repeat(10_000) { monitor.withLock { count += 1 } }
		}
		assertEquals(40_000, monitor.withLock { count })
	}

	@Test
	fun separateInstancesDoNotShareAGlobalLock() {
		val first = ReaderRelocationMonitor()
		val second = ReaderRelocationMonitor()
		first.withLock {
			runReaderQueueWorkers(1) { assertEquals(9, second.withLock { 9 }) }
		}
	}

	private class MonitorFailure : RuntimeException()
}
