package paige.navic.ui.screens.reader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageRelocationReservationResult
import paige.navic.reader.ReaderPageRelocationTransferResult
import paige.navic.reader.ReaderPageTurnDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageReaderTeardownTest {
	@Test
	fun readerCloseFencesRasterBeforeRendererAndWaitsBeforeBundleOwners() = runTest {
		val events = mutableListOf<String>()
		val releaseRenderer = CompletableDeferred<Unit>()
		val teardown = ReaderPageReaderTeardown(
			scope = this,
			fenceBundleOwners = { events += "raster-fence" },
			closeRendererAndAdapter = {
				events += "renderer-start"
				withContext(NonCancellable) { releaseRenderer.await() }
				events += "renderer-end"
			},
			closeBundleOwners = {
				events += "bundle"
			}
		)

		val first = teardown.start()
		val second = teardown.start()
		assertSame(first, second)
		runCurrent()
		assertEquals(listOf("raster-fence", "renderer-start"), events)
		assertFalse(first.isCompleted)

		releaseRenderer.complete(Unit)
		first.await()
		assertEquals(
			listOf(
				"raster-fence",
				"renderer-start",
				"renderer-end",
				"bundle"
			),
			events
		)
	}

	@Test
	fun readerCloseStillClosesBundleWhenRendererCloseFails() = runTest {
		val events = mutableListOf<String>()
		val teardown = ReaderPageReaderTeardown(
			scope = this,
			fenceBundleOwners = { events += "raster-fence" },
			closeRendererAndAdapter = {
				events += "renderer"
				error("renderer-close")
			},
			closeBundleOwners = {
				events += "bundle"
			}
		)

		val failure = assertFailsWith<ReaderPageTeardownException> {
			teardown.closeAndJoin()
		}
		assertEquals(
			ReaderPageTeardownStage.RendererDisposal,
			failure.stage
		)
		assertEquals("renderer-close", failure.cause?.message)
		assertEquals(listOf("raster-fence", "renderer", "bundle"), events)
	}

	@Test
	fun readerCloseDrainsQaOwnersBeforeRendererAndBundleOwners() = runTest {
		val events = mutableListOf<String>()
		var workerResumed = 0
		var hostileRelocationCallbacks = 0
		var releasedPublicationValues = 0
		var rendererOwners = 1
		var adapterOwners = 1
		var bundleOwners = 1
		val registry = ReaderPageQaFaultRegistry()
		val publicationLedger = ReaderPageRasterPublicationLedger<String>(
			currentEpochEntryLimit = 1,
			persistenceWorkerLimit = 1,
			callbackLimit = 1,
			release = { releasedPublicationValues += 1 }
		)
		val publicationRequest = assertIs<
			ReaderPageRasterPublicationRegistration.Started
		>(
			publicationLedger.begin("digest", "value") {}
		).request
		assertEquals("value", publicationLedger.acquireForPersistence(publicationRequest))
		val relocationQueue = ReaderPageRelocationQueue()
		val reservation = assertIs<ReaderPageRelocationReservationResult.Reserved>(
			relocationQueue.reserve(1L)
		).reservation
		assertIs<ReaderPageRelocationTransferResult.Enqueued>(
			relocationQueue.enqueueReserved(
				reservation = reservation,
				rasterGeneration = 1L,
				textureGeneration = 2L,
				sourceOrdinal = 3,
				destinationOrdinal = 4,
				logicalDirection = ReaderPageTurnDirection.Next,
				foliateSessionId = "session"
			)
		)
		assertTrue(registry.enqueue(ReaderPageQaFault.PauseNextPublication))
		assertTrue(
			registry.enqueue(
				ReaderPageQaFault.DelayNextRelocationAcknowledgement
			)
		)
		val worker = launch {
			try {
				registry.pausePublicationWithinWorker(publicationRequest.epoch)
				workerResumed += 1
			} finally {
				publicationLedger.complete(publicationRequest, persisted = false)
			}
		}
		runCurrent()
		assertTrue(
			registry.pauseRelocationAck("relocation-token") {
				hostileRelocationCallbacks += 1
				error("PRIVATE_EPUB_TEXT SECRET_TOKEN")
			}
		)

		val teardown = ReaderPageReaderTeardown(
			scope = this,
			fenceCallbacks = {
				events += "CallbackFence"
				registry.closeAndDrain()
			},
			fenceBundleOwners = { events += "BundleFence" },
			closeRendererAndAdapter = {
				events += "RendererDisposal"
				worker.join()
				relocationQueue.cancelAll()
				rendererOwners = 0
				adapterOwners = 0
			},
			closeBundleOwners = {
				events += "BundleOwners"
				bundleOwners = 0
			}
		)

		teardown.closeAndJoin()
		teardown.closeAndJoin()

		assertTrue(events.indexOf("CallbackFence") < events.indexOf("RendererDisposal"))
		assertTrue(events.indexOf("CallbackFence") < events.indexOf("BundleOwners"))
		assertEquals(1, workerResumed)
		assertEquals(0, hostileRelocationCallbacks)
		assertFalse(
			registry.pauseRelocationAck("relocation-token") {
				hostileRelocationCallbacks += 1
			}
		)
		assertNull(
			registry.delayVisualState(
				relocationToken = "relocation-token",
				handoffAttemptId = 1L,
				registration = ReaderWebViewVisualDeliveryCell({}, {}),
				postPhysical = {}
			)
		)
		assertEquals(0, registry.pendingCallbackCount())
		assertEquals(0, publicationLedger.entryCount())
		assertEquals(1, releasedPublicationValues)
		assertEquals(0, relocationQueue.ownershipSnapshot().occupied)
		assertEquals(0, rendererOwners)
		assertEquals(0, adapterOwners)
		assertEquals(0, bundleOwners)
	}

	@Test
	fun repeatedFailureInstanceDoesNotAbortRemainingTeardown() = runTest {
		val shared = ReaderPageTeardownException(
			ReaderPageTeardownStage.RendererDisposal
		)
		val events = mutableListOf<String>()
		val teardown = ReaderPageReaderTeardown(
			scope = this,
			fenceBundleOwners = {},
			closeRendererAndAdapter = { throw shared },
			closeBundleOwners = {
				events += "bundle"
				throw shared
			}
		)

		val failure = assertFailsWith<ReaderPageTeardownException> {
			teardown.closeAndJoin()
		}

		assertSame(shared, failure)
		assertEquals(listOf("bundle"), events)
		assertTrue(failure.suppressed.isEmpty())
	}
}
