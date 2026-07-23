package paige.navic.ui.screens.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderMainTerminalActionExecutorTest {
	@Test
	fun boundedActionsRunInFifoOrder() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val events = mutableListOf<Int>()
		val executor = ReaderMainTerminalActionExecutor(
			actionLimit = 2,
			scope = CoroutineScope(dispatcher),
			onActionFailure = { throw AssertionError(it) }
		)

		assertTrue(executor.execute(Runnable { events += 1 }))
		assertTrue(executor.execute(Runnable { events += 2 }))
		assertEquals(2, executor.pendingActionCount())
		assertFalse(executor.execute(Runnable { events += 3 }))
		assertEquals(2, executor.pendingActionCount())

		runCurrent()
		assertEquals(listOf(1, 2), events)
		assertEquals(0, executor.pendingActionCount())
		executor.closeAndJoin()
	}

	@Test
	fun throwingActionCannotSuppressLaterAction() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val failure = IllegalStateException("action-failed")
		val failures = mutableListOf<Throwable>()
		val events = mutableListOf<String>()
		val executor = ReaderMainTerminalActionExecutor(
			actionLimit = 2,
			scope = CoroutineScope(dispatcher),
			onActionFailure = failures::add
		)

		assertTrue(executor.execute(Runnable { throw failure }))
		assertTrue(executor.execute(Runnable { events += "later" }))
		runCurrent()

		assertEquals(listOf("later"), events)
		assertEquals(1, failures.size)
		assertSame(failure, failures.single())
		assertEquals(0, executor.pendingActionCount())
		executor.closeAndJoin()
	}

	@Test
	fun closeDrainsAcceptedActionsAndRejectsLaterSubmission() = runTest {
		val dispatcher = StandardTestDispatcher(testScheduler)
		val events = mutableListOf<String>()
		val executor = ReaderMainTerminalActionExecutor(
			actionLimit = 1,
			scope = CoroutineScope(dispatcher),
			onActionFailure = { throw AssertionError(it) }
		)
		assertTrue(executor.execute(Runnable { events += "accepted" }))

		val closing = async(start = CoroutineStart.UNDISPATCHED) {
			executor.closeAndJoin()
		}
		assertFalse(closing.isCompleted)
		runCurrent()
		closing.await()

		assertEquals(listOf("accepted"), events)
		assertEquals(0, executor.pendingActionCount())
		assertFalse(executor.execute(Runnable { events += "late" }))
		assertEquals(0, executor.pendingActionCount())
	}
}
