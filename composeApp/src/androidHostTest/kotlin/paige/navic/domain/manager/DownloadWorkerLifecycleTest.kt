package paige.navic.domain.manager

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DownloadWorkerLifecycleTest {

	@Test
	fun reconnectWaitsForUnregisteredWorkerCleanupBeforeRecoveringOrStartingAgain() = runTest {
		val online = MutableStateFlow(true)
		val releaseOldWorker = CompletableDeferred<Unit>()
		val events = mutableListOf<String>()
		var starts = 0
		var liveWorkers = 0
		val job = launch {
			runConnectedDownloadWorkers(online, recoverInterrupted = {
				assertEquals(0, liveWorkers)
				events += "recover"
			}) {
				starts++
				launch {
					liveWorkers++
					events += "start-$starts"
					try {
						awaitCancellation()
					} finally {
						withContext(NonCancellable) {
							if (starts == 1) releaseOldWorker.await()
							liveWorkers--
							events += "stop"
						}
					}
				}
				awaitCancellation()
			}
		}
		runCurrent()
		online.value = false
		runCurrent()
		online.value = true
		runCurrent()
		assertEquals(1, starts)
		assertEquals(listOf("recover", "start-1"), events)
		releaseOldWorker.complete(Unit)
		runCurrent()
		assertEquals(2, starts)
		assertEquals(listOf("recover", "start-1", "stop", "recover", "start-2"), events)
		job.cancel()
		job.join()
		assertEquals(0, liveWorkers)
		assertEquals("recover", events.last())
	}

	@Test
	fun offlineStartupRecoversWithoutAdmittingWorkers() = runTest {
		val online = MutableStateFlow(false)
		var started = false
		var recoveries = 0
		val job = launch {
			runConnectedDownloadWorkers(online, { recoveries++ }) {
				started = true
				awaitCancellation()
			}
		}
		runCurrent()
		assertFalse(started)
		assertEquals(1, recoveries)
		job.cancel()
		job.join()
	}
}
