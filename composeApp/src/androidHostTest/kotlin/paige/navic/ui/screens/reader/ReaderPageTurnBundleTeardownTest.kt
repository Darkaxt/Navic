package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageTurnBundleTeardownTest {
	@Test
	fun closeIsAsynchronousIdempotentAndOrderedAfterPublicationDrain() = runTest {
		val events = mutableListOf<String>()
		val releasePublication = CompletableDeferred<Unit>()
		var publicationEntries = 1
		val teardown = ReaderPageTurnBundleTeardown(
			scope = this,
			closePublicationWorkers = {
				events += "publication-start"
				withContext(NonCancellable) {
					releasePublication.await()
				}
				publicationEntries = 0
				events += "publication-end"
			},
			publicationEntryCount = { publicationEntries },
			closeRasterGenerationWorkers = {
				events += "generation"
			},
			closeRasterHydrationWorkers = {
				events += "hydration"
			},
			closePersistentStore = {
				events += "persistent"
			},
			closeRasterCache = {
				events += "cache"
			},
			clearReferences = {
				events += "references"
			}
		)

		val first = teardown.start()
		val second = teardown.start()
		assertSame(first, second)
		runCurrent()
		assertEquals(listOf("publication-start"), events)
		assertFalse(first.isCompleted)

		releasePublication.complete(Unit)
		first.await()
		assertEquals(
			listOf(
				"publication-start",
				"publication-end",
				"generation",
				"hydration",
				"persistent",
				"cache",
				"references"
			),
			events
		)
	}

	@Test
	fun nonZeroPublicationLedgerIsReportedAfterRemainingOwnersClose() = runTest {
		val events = mutableListOf<String>()
		val teardown = ReaderPageTurnBundleTeardown(
			scope = this,
			closePublicationWorkers = {
				events += "publication"
			},
			publicationEntryCount = { 1 },
			closeRasterGenerationWorkers = {
				events += "generation"
			},
			closeRasterHydrationWorkers = {
				events += "hydration"
			},
			closePersistentStore = {
				events += "persistent"
			},
			closeRasterCache = {
				events += "cache"
			},
			clearReferences = {
				events += "references"
			}
		)

		val failure = assertFailsWith<ReaderPageTeardownException> {
			teardown.start().await()
		}
		assertEquals(
			ReaderPageTeardownStage.PublicationLedger,
			failure.stage
		)
		assertEquals(
			listOf(
				"publication",
				"generation",
				"hydration",
				"persistent",
				"cache",
				"references"
			),
			events
		)
	}

	@Test
	fun preCloseInvalidationFailureStillClosesEveryOwner() = runTest {
		val events = mutableListOf<String>()
		val teardown = ReaderPageTurnBundleTeardown(
			scope = this,
			preCloseFailure = { IllegalStateException() },
			closePublicationWorkers = { events += "publication" },
			publicationEntryCount = { 0 },
			publicationDispatchFailure = { null },
			closeRasterGenerationWorkers = { events += "generation" },
			closeRasterHydrationWorkers = { events += "hydration" },
			closePersistentStore = { events += "persistent" },
			closeRasterCache = { events += "cache" },
			clearReferences = { events += "references" }
		)

		val failure = assertFailsWith<ReaderPageTeardownException> {
			teardown.closeAndJoin()
		}

		assertEquals(ReaderPageTeardownStage.RasterInvalidation, failure.stage)
		assertEquals(
			listOf(
				"publication",
				"generation",
				"hydration",
				"persistent",
				"cache",
				"references"
			),
			events
		)
	}

	@Test
	fun repeatedFailureInstanceDoesNotAbortRemainingOwners() = runTest {
		val shared = ReaderPageTeardownException(
			ReaderPageTeardownStage.RasterInvalidation
		)
		val events = mutableListOf<String>()
		val teardown = ReaderPageTurnBundleTeardown(
			scope = this,
			preCloseFailure = { shared },
			closePublicationWorkers = {
				events += "publication"
				throw shared
			},
			publicationEntryCount = { 0 },
			closeRasterGenerationWorkers = { events += "generation" },
			closeRasterHydrationWorkers = { events += "hydration" },
			closePersistentStore = { events += "persistent" },
			closeRasterCache = { events += "cache" },
			clearReferences = { events += "references" }
		)

		val failure = assertFailsWith<ReaderPageTeardownException> {
			teardown.closeAndJoin()
		}

		assertSame(shared, failure)
		assertEquals(
			listOf(
				"publication",
				"generation",
				"hydration",
				"persistent",
				"cache",
				"references"
			),
			events
		)
		assertTrue(failure.suppressed.isEmpty())
	}
}
