package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageRecoveredDeckBuildOperationTest {
	@Test
	fun cancellingBeforeQueuedPublicationClosesCompletedResultExactlyOnce() = runTest {
		val result = ClosingResult()
		val preparation = CompletableDeferred<ClosingResult?>().apply { complete(result) }
		var delivered = false
		val operation = ReaderPageRecoveredDeckBuildOperation(
			preparation = preparation,
			scope = backgroundScope,
			publicationDispatcher = StandardTestDispatcher(testScheduler, "publication"),
			onResult = { _, resolveOwnership ->
				delivered = true
				resolveOwnership()
			},
			onFailure = { failure -> throw failure }
		)
		operation.start()

		operation.cancel()
		runCurrent()

		assertFalse(delivered)
		assertEquals(1, result.closeCount)
	}

	@Test
	fun cancellingAfterPreparationCompletesConsumesUndeliveredResult() = runTest {
		val result = ClosingResult()
		val preparation = CompletableDeferred<ClosingResult?>()
		var delivered = false
		val operation = ReaderPageRecoveredDeckBuildOperation(
			preparation = preparation,
			scope = backgroundScope,
			publicationDispatcher = StandardTestDispatcher(testScheduler, "publication"),
			onResult = { _, resolveOwnership ->
				delivered = true
				resolveOwnership()
			},
			onFailure = { failure -> throw failure }
		)
		operation.start()
		preparation.complete(result)

		operation.cancel()
		runCurrent()

		assertFalse(delivered)
		assertEquals(1, result.closeCount)
	}

	@Test
	fun resolvedPublicationTransfersOwnershipWithoutClosingResult() = runTest {
		val result = ClosingResult()
		val preparation = CompletableDeferred<ClosingResult?>().apply { complete(result) }
		var delivered = false
		val operation = ReaderPageRecoveredDeckBuildOperation(
			preparation = preparation,
			scope = backgroundScope,
			publicationDispatcher = StandardTestDispatcher(testScheduler, "publication"),
			onResult = { deliveredResult, resolveOwnership ->
				assertTrue(deliveredResult === result)
				resolveOwnership()
				delivered = true
			},
			onFailure = { failure -> throw failure }
		)

		operation.start()
		runCurrent()

		assertTrue(delivered)
		assertEquals(0, result.closeCount)
	}

	private class ClosingResult : AutoCloseable {
		var closeCount = 0
			private set

		override fun close() {
			closeCount += 1
		}
	}
}
