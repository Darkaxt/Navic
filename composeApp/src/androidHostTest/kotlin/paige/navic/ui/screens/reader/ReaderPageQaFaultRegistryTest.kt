package paige.navic.ui.screens.reader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderPageQaFaultRegistryTest {
	private lateinit var registry: ReaderPageQaFaultRegistry
	private lateinit var events: MutableList<ReaderPageQaFaultEvent>

	@BeforeTest
	fun setUp() {
		events = mutableListOf()
		registry = ReaderPageQaFaultRegistry(
			ReaderPageQaFaultEventSink(events::add)
		)
	}

	@AfterTest
	fun tearDown() {
		registry.closeAndDrain()
		assertEquals(0, registry.queuedFaultCount())
		assertEquals(0, registry.pendingCallbackCount())
	}

	private fun delayVisualState(
		relocationToken: String,
		handoffAttemptId: Long,
		action: () -> Unit
	): Boolean = registry.delayVisualState(
		relocationToken = relocationToken,
		handoffAttemptId = handoffAttemptId,
		registration = ReaderWebViewVisualDeliveryCell(
			action = action,
			onPhysicalOwnershipReleased = { }
		),
		postPhysical = { registration ->
			check(registration.deliver())
		}
	) != null

	@Test
	fun everyQueuedFaultEmitsOneStrictAppliedChain() {
		ReaderPageQaFault.entries.forEachIndexed { index, fault ->
			assertTrue(
				registry.enqueue("fault-$index", fault),
				fault.name
			)
		}

		ReaderPageQaFault.entries.forEachIndexed { index, fault ->
			val context = contextForTest(fault, index.toLong())
			assertNotNull(
				registry.consumeAndApply(fault, context),
				fault.name
			)
			assertNull(registry.consumeAndApply(fault, context), fault.name)
			val chain = events.filter { it.ticket.requestId == "fault-$index" }
			assertEquals(
				listOf(
					ReaderPageQaFaultState.Enqueued,
					ReaderPageQaFaultState.Consumed,
					ReaderPageQaFaultState.Applied
				),
				chain.map(ReaderPageQaFaultEvent::state),
				fault.name
			)
			assertEquals(
				listOf("queue", seamForTest(fault), seamForTest(fault)),
				chain.map(ReaderPageQaFaultEvent::seam),
				fault.name
			)
			assertTrue(chain.all { it.ticket.fault == fault }, fault.name)
			assertTrue(chain.all { it.releaseRequestId == null }, fault.name)
			assertNull(chain[0].operation)
			assertNull(chain[1].operation)
			assertEquals(context, chain[2].operation)
		}
	}

	@Test
	fun queuedFaultLookupTracksOnlyMatchingUnconsumedTickets() {
		assertFalse(registry.hasQueued(ReaderPageQaFault.MissNextRasterLoad))
		assertTrue(
			registry.enqueue(
				"queued-raster-miss",
				ReaderPageQaFault.MissNextRasterLoad
			)
		)
		assertTrue(
			registry.enqueue(
				"queued-publication-pause",
				ReaderPageQaFault.PauseNextPublication
			)
		)

		assertTrue(registry.hasQueued(ReaderPageQaFault.MissNextRasterLoad))
		assertTrue(registry.hasQueued(ReaderPageQaFault.PauseNextPublication))
		assertNotNull(
			registry.consumeAndApply(
				ReaderPageQaFault.MissNextRasterLoad,
				ReaderPageQaFaultOperationContext(rasterRequestEpoch = 1L)
			)
		)
		assertFalse(registry.hasQueued(ReaderPageQaFault.MissNextRasterLoad))
		assertTrue(registry.hasQueued(ReaderPageQaFault.PauseNextPublication))
		registry.clear("clear-queued-lookup")
		assertFalse(registry.hasQueued(ReaderPageQaFault.PauseNextPublication))
	}

	@Test
	fun nonCanonicalOperationContextIsRejectedWithoutConsumption() {
		assertTrue(registry.enqueue(ReaderPageQaFault.MissNextRasterLoad))

		assertFailsWith<IllegalStateException> {
			registry.consumeAndApply(
				ReaderPageQaFault.MissNextRasterLoad,
				ReaderPageQaFaultOperationContext(
					rasterRequestEpoch = 1L,
					repairAttemptId = 2L
				)
			)
		}

		assertEquals(1, registry.queuedFaultCount())
		registry.clear("clear-non-canonical")
	}

	@Test
	fun reservedNoneRequestIdCannotBecomeAStoredOrReleaseIdentity() {
		listOf("none", "NONE", "NoNe").forEach { requestId ->
			assertFailsWith<IllegalArgumentException> {
				ReaderPageQaFaultTicket(
					requestId = requestId,
					fault = ReaderPageQaFault.FailNextPersistence
				)
			}
			assertFailsWith<IllegalArgumentException> {
				ReaderPageQaFaultCorrelation(
					requestId = requestId,
					appliedOperation = ReaderPageQaFaultOperationContext(
						publicationEpoch = 1L
					),
					relation = ReaderPageQaFaultRelation.AppliedOperation
				)
			}
			assertFailsWith<IllegalArgumentException> {
				registry.enqueue(
					requestId,
					ReaderPageQaFault.FailNextPersistence
				)
			}
			assertFailsWith<IllegalArgumentException> {
				registry.releasePublication(requestId)
			}
			assertFailsWith<IllegalArgumentException> {
				registry.releaseRelocationAck(requestId)
			}
			assertFailsWith<IllegalArgumentException> {
				registry.releaseVisualState(requestId)
			}
			assertFailsWith<IllegalArgumentException> {
				registry.clear(requestId)
			}
		}
	}

	private fun contextForTest(
		fault: ReaderPageQaFault,
		value: Long
	): ReaderPageQaFaultOperationContext = when (fault) {
		ReaderPageQaFault.FailNextPersistence ->
			ReaderPageQaFaultOperationContext(
				publicationEpoch = value,
				persistenceAttemptId = value
			)
		ReaderPageQaFault.PauseNextPublication ->
			ReaderPageQaFaultOperationContext(publicationEpoch = value)
		ReaderPageQaFault.MissNextRasterLoad ->
			ReaderPageQaFaultOperationContext(rasterRequestEpoch = value)
		ReaderPageQaFault.ForceRepairWithoutPreparedDeck ->
			ReaderPageQaFaultOperationContext(repairAttemptId = value)
		ReaderPageQaFault.DeferContentNotReady,
		ReaderPageQaFault.DeferLayoutUnstable,
		ReaderPageQaFault.DeferPaginationNotReady,
		ReaderPageQaFault.DeferWebViewDetached,
		ReaderPageQaFault.DeferReaderPaused ->
			ReaderPageQaFaultOperationContext(preparationAttemptId = value)
		ReaderPageQaFault.DelayNextVisualStateCallback ->
			ReaderPageQaFaultOperationContext(
				relocationToken = "relocation-$value",
				handoffAttemptId = value
			)
		ReaderPageQaFault.DelayNextRelocationAcknowledgement ->
			ReaderPageQaFaultOperationContext(
				relocationToken = "relocation-$value"
			)
	}

	private fun seamForTest(fault: ReaderPageQaFault): String = when (fault) {
		ReaderPageQaFault.FailNextPersistence -> "persistence"
		ReaderPageQaFault.PauseNextPublication -> "publication-worker"
		ReaderPageQaFault.MissNextRasterLoad -> "raster-resolver"
		ReaderPageQaFault.ForceRepairWithoutPreparedDeck -> "repair-role"
		ReaderPageQaFault.DeferContentNotReady,
		ReaderPageQaFault.DeferLayoutUnstable,
		ReaderPageQaFault.DeferPaginationNotReady,
		ReaderPageQaFault.DeferWebViewDetached,
		ReaderPageQaFault.DeferReaderPaused -> "deferred-retry"
		ReaderPageQaFault.DelayNextVisualStateCallback -> "visual-state"
		ReaderPageQaFault.DelayNextRelocationAcknowledgement -> "relocation-ack"
	}

	private fun assertReleasedChain(
		requestId: String,
		releaseRequestId: String
	) {
		val chain = events.filter { it.ticket.requestId == requestId }
		assertEquals(
			listOf(
				ReaderPageQaFaultState.Enqueued,
				ReaderPageQaFaultState.Consumed,
				ReaderPageQaFaultState.Applied,
				ReaderPageQaFaultState.Released
			),
			chain.map(ReaderPageQaFaultEvent::state)
		)
		assertEquals(
			listOf(null, null, null, releaseRequestId),
			chain.map(ReaderPageQaFaultEvent::releaseRequestId)
		)
		assertEquals("command-release", chain.last().result)
	}

	@Test
	fun queuedFaultCapacityRejectsWithoutGrowthAndReturnsAfterConsumption() {
		repeat(registry.queuedFaultLimit) {
			assertTrue(
				registry.enqueue(
					ReaderPageQaFault.FailNextPersistence
				)
			)
		}
		assertEquals(
			registry.queuedFaultLimit,
			registry.queuedFaultCount()
		)

		val eventCountBeforeReject = events.size
		assertFalse(
			registry.enqueue(
				ReaderPageQaFault.MissNextRasterLoad
			)
		)
		assertEquals(eventCountBeforeReject, events.size)
		assertEquals(
			registry.queuedFaultLimit,
			registry.queuedFaultCount()
		)

		assertNotNull(
			registry.consumeAndApply(
				ReaderPageQaFault.FailNextPersistence,
				ReaderPageQaFaultOperationContext(
					publicationEpoch = 1L,
					persistenceAttemptId = 1L
				)
			)
		)
		assertTrue(
			registry.enqueue(
				ReaderPageQaFault.MissNextRasterLoad
			)
		)
		assertEquals(
			registry.queuedFaultLimit,
			registry.queuedFaultCount()
		)
		registry.clear("clear-test")
		assertEquals(0, registry.queuedFaultCount())
	}

	@Test
	fun occupiedSlotsDoNotConsumeTheNextMatchingFault() = runTest {
		val publicationCompletions = mutableListOf<String>()
		var firstRelocation = 0
		var secondRelocation = 0
		var rejectedRelocation = 0
		var firstVisual = 0
		var secondVisual = 0
		var rejectedVisual = 0
		repeat(2) {
			registry.enqueue(ReaderPageQaFault.PauseNextPublication)
			registry.enqueue(
				ReaderPageQaFault.DelayNextRelocationAcknowledgement
			)
			registry.enqueue(ReaderPageQaFault.DelayNextVisualStateCallback)
		}

		val firstPublication = async {
			registry.pausePublicationWithinWorker(publicationEpoch = 7L)
			publicationCompletions += "first"
		}
		runCurrent()
		val bypassedPublication = async {
			registry.pausePublicationWithinWorker(publicationEpoch = 7L)
			publicationCompletions += "bypassed"
		}
		runCurrent()
		assertTrue(bypassedPublication.isCompleted)
		assertEquals(listOf("bypassed"), publicationCompletions)

		assertTrue(
			registry.pauseRelocationAck("relocation-test") { firstRelocation += 1 }
		)
		assertFalse(
			registry.pauseRelocationAck("relocation-test") { rejectedRelocation += 1 }
		)
		assertTrue(
			delayVisualState("relocation-test", 9L) { firstVisual += 1 }
		)
		assertFalse(
			delayVisualState("relocation-test", 9L) { rejectedVisual += 1 }
		)
		assertEquals(
			registry.pendingCallbackLimit,
			registry.pendingCallbackCount()
		)
		assertTrue(registry.releasePublication("release-publication-test"))
		assertTrue(registry.releaseRelocationAck("release-relocation-test"))
		assertTrue(registry.releaseVisualState("release-visual-test"))
		firstPublication.await()
		val secondPublication = async {
			registry.pausePublicationWithinWorker(publicationEpoch = 7L)
			publicationCompletions += "second"
		}
		runCurrent()
		assertFalse(secondPublication.isCompleted)
		assertTrue(
			registry.pauseRelocationAck("relocation-test") { secondRelocation += 1 }
		)
		assertTrue(
			delayVisualState("relocation-test", 10L) { secondVisual += 1 }
		)
		assertTrue(registry.releasePublication("release-publication-test"))
		assertTrue(registry.releaseRelocationAck("release-relocation-test"))
		assertTrue(registry.releaseVisualState("release-visual-test"))
		secondPublication.await()

		assertEquals(
			listOf("bypassed", "first", "second"),
			publicationCompletions
		)
		assertEquals(1, firstRelocation)
		assertEquals(1, secondRelocation)
		assertEquals(0, rejectedRelocation)
		assertEquals(1, firstVisual)
		assertEquals(1, secondVisual)
		assertEquals(0, rejectedVisual)
		assertEquals(0, registry.pendingCallbackCount())
	}

	@Test
	fun publicationGateSurvivesInvalidationCancellationUntilExplicitRelease() =
		runTest {
			var resumedIntoFinally = 0
			registry.enqueue(ReaderPageQaFault.PauseNextPublication)
			val worker = launch {
				try {
					registry.pausePublicationWithinWorker(publicationEpoch = 7L)
				} finally {
					resumedIntoFinally += 1
				}
			}
			runCurrent()

			worker.cancel()
			runCurrent()
			assertFalse(worker.isCompleted)
			assertEquals(1, registry.pendingCallbackCount())
			assertTrue(registry.releasePublication("release-publication-test"))
			worker.join()

			assertEquals(1, resumedIntoFinally)
			assertEquals(0, registry.pendingCallbackCount())
		}

	@Test
	fun physicalAbandonCannotStealTransferredVisualStateOwner() {
		var delivered = 0
		val registration = ReaderWebViewVisualDeliveryCell(
			action = { delivered += 1 },
			onPhysicalOwnershipReleased = { }
		)
		assertTrue(
			registry.enqueue(
				"visual-delay",
				ReaderPageQaFault.DelayNextVisualStateCallback
			)
		)
		assertNotNull(
			registry.delayVisualState(
				relocationToken = "relocation-test",
				handoffAttemptId = 9L,
				registration = registration,
				postPhysical = { returned -> assertTrue(returned.deliver()) }
			)
		)

		assertFalse(registration.abandonPhysicalOwnership())
		assertTrue(registry.releaseVisualState("release-visual-test"))
		assertEquals(1, delivered)
		assertEquals(0, registry.pendingCallbackCount())
	}

	@Test
	fun explicitReleaseResumesWorkerAndIsolatesHostileCallback() = runTest {
		var publicationCompletions = 0
		var relocationCompletions = 0
		var visualCompletions = 0
		var publicationApplied: ReaderPageQaAppliedFault? = null
		var relocationApplied: ReaderPageQaAppliedFault? = null
		registry.enqueue(
			"publication-delay",
			ReaderPageQaFault.PauseNextPublication
		)
		registry.enqueue(
			"relocation-delay",
			ReaderPageQaFault.DelayNextRelocationAcknowledgement
		)
		registry.enqueue(
			"visual-delay",
			ReaderPageQaFault.DelayNextVisualStateCallback
		)
		val publication = async {
			publicationApplied = registry.pausePublicationWithinWorker(
				publicationEpoch = 7L
			)
			publicationCompletions += 1
		}
		runCurrent()
		assertTrue(
			registry.pauseRelocationAck("relocation-test") { applied ->
				relocationApplied = applied
				relocationCompletions += 1
				error("PRIVATE_EPUB_TEXT SECRET_TOKEN")
			}
		)
		assertTrue(
			delayVisualState("relocation-test", 9L) {
				visualCompletions += 1
				error("PRIVATE_EPUB_TEXT SECRET_TOKEN")
			}
		)

		assertTrue(registry.releasePublication("release-publication-test"))
		assertFalse(registry.releasePublication("release-publication-test"))
		assertTrue(registry.releaseRelocationAck("release-relocation-test"))
		assertFalse(registry.releaseRelocationAck("release-relocation-test"))
		assertTrue(registry.releaseVisualState("release-visual-test"))
		assertFalse(registry.releaseVisualState("release-visual-test"))
		publication.await()

		assertEquals(1, publicationCompletions)
		assertEquals(1, relocationCompletions)
		assertEquals(1, visualCompletions)
		assertEquals("publication-delay", publicationApplied?.ticket?.requestId)
		assertEquals(7L, publicationApplied?.context?.publicationEpoch)
		assertEquals("relocation-delay", relocationApplied?.ticket?.requestId)
		assertEquals(
			"relocation-test",
			relocationApplied?.context?.relocationToken
		)
		assertEquals(0, registry.pendingCallbackCount())
		assertReleasedChain(
			"publication-delay",
			"release-publication-test"
		)
		assertReleasedChain(
			"relocation-delay",
			"release-relocation-test"
		)
		assertReleasedChain("visual-delay", "release-visual-test")
	}

	@Test
	fun clearResumesWorkerAndReleasesEveryOwnedCallbackOnce() = runTest {
		var publicationCompletions = 0
		var relocationCompletions = 0
		var visualCompletions = 0
		registry.enqueue(ReaderPageQaFault.FailNextPersistence)
		registry.enqueue(ReaderPageQaFault.MissNextRasterLoad)
		registry.enqueue(ReaderPageQaFault.PauseNextPublication)
		registry.enqueue(
			ReaderPageQaFault.DelayNextRelocationAcknowledgement
		)
		registry.enqueue(ReaderPageQaFault.DelayNextVisualStateCallback)
		val publication = async {
			registry.pausePublicationWithinWorker(publicationEpoch = 7L)
			publicationCompletions += 1
		}
		runCurrent()
		assertTrue(
			registry.pauseRelocationAck("relocation-test") {
				relocationCompletions += 1
				error("PRIVATE_EPUB_TEXT SECRET_TOKEN")
			}
		)
		assertTrue(
			delayVisualState("relocation-test", 9L) {
				visualCompletions += 1
				error("PRIVATE_EPUB_TEXT SECRET_TOKEN")
			}
		)

		registry.clear("clear-test")
		registry.clear("clear-test")
		publication.await()

		assertNull(
			registry.consumeAndApply(
				ReaderPageQaFault.FailNextPersistence,
				ReaderPageQaFaultOperationContext(
					publicationEpoch = 1L,
					persistenceAttemptId = 1L
				)
			)
		)
		assertNull(
			registry.consumeAndApply(
				ReaderPageQaFault.MissNextRasterLoad,
				ReaderPageQaFaultOperationContext(rasterRequestEpoch = 1L)
			)
		)
		assertFalse(registry.releasePublication("release-publication-test"))
		assertFalse(registry.releaseRelocationAck("release-relocation-test"))
		assertFalse(registry.releaseVisualState("release-visual-test"))
		assertEquals(1, publicationCompletions)
		assertEquals(1, relocationCompletions)
		assertEquals(0, visualCompletions)
		assertEquals(0, registry.queuedFaultCount())
		assertEquals(0, registry.pendingCallbackCount())
	}

	@Test
	fun closeResumesPausedWorkerDiscardsRelocationAndRejectsNewFaults() = runTest {
		var publicationCompletions = 0
		var relocationCompletions = 0
		var visualCompletions = 0
		registry.enqueue(ReaderPageQaFault.PauseNextPublication)
		registry.enqueue(
			ReaderPageQaFault.DelayNextRelocationAcknowledgement
		)
		registry.enqueue(ReaderPageQaFault.DelayNextVisualStateCallback)
		val publication = async {
			registry.pausePublicationWithinWorker(publicationEpoch = 7L)
			publicationCompletions += 1
		}
		runCurrent()
		assertTrue(
			registry.pauseRelocationAck("relocation-test") {
				relocationCompletions += 1
				error("PRIVATE_EPUB_TEXT SECRET_TOKEN")
			}
		)
		assertTrue(
			delayVisualState("relocation-test", 9L) {
				visualCompletions += 1
				error("PRIVATE_EPUB_TEXT SECRET_TOKEN")
			}
		)

		registry.closeAndDrain()
		registry.closeAndDrain()
		publication.await()

		assertTrue(registry.isClosed())
		assertEquals(1, publicationCompletions)
		assertEquals(0, relocationCompletions)
		assertEquals(0, visualCompletions)
		assertEquals(0, registry.queuedFaultCount())
		assertEquals(0, registry.pendingCallbackCount())
		assertFalse(registry.enqueue(ReaderPageQaFault.FailNextPersistence))
		assertFalse(registry.releasePublication("release-publication-test"))
		assertFalse(registry.releaseRelocationAck("release-relocation-test"))
		assertFalse(registry.releaseVisualState("release-visual-test"))
	}

	@Test
	fun qaCallbackAdmissionInvalidatesConcurrentApplicationSnapshot() {
		val ownershipEpoch = ReaderPageApplicationOwnershipEpoch()
		val localRegistry = ReaderPageQaFaultRegistry(
			onOwnershipMutated = ownershipEpoch::ownerMutationCommitted
		)
		assertTrue(
			localRegistry.enqueue(
				"relocation-delay",
				ReaderPageQaFault.DelayNextRelocationAcknowledgement
			)
		)

		val captured = ownershipEpoch.captureStable { before ->
			assertTrue(localRegistry.pauseRelocationAck("relocation-test") {})
			ReaderPageApplicationOwnershipSnapshot(
				ownershipEpoch = before,
				adapterResidents = 0,
				adapterResidentLimit = 0,
				adapterDecodedBitmaps = 0,
				adapterDecodedBitmapLimit = 0,
				cacheDecodedBitmaps = 0,
				cacheDecodedBitmapLimit = 0,
				stagedPublications = 0,
				stagedPublicationLimit = 0,
				pendingCallbacks = 0,
				pendingCallbackLimit = localRegistry.pendingCallbackLimit,
				relocationReservations = 0,
				queuedRelocations = 0,
				relocationTokens = 0,
				relocationTokenLimit = 0
			)
		}

		assertNull(captured)
		assertEquals(1L, ownershipEpoch.current())
		localRegistry.clear("clear-relocation")
		assertEquals(2L, ownershipEpoch.current())
		assertEquals(0, localRegistry.pendingCallbackCount())
		localRegistry.closeAndDrain()
		assertEquals(2L, ownershipEpoch.current())
	}

	@Test
	fun prearmedFaultTransfersToFirstAttachedRegistry() {
		val transferredEvents = mutableListOf<ReaderPageQaFaultEvent>()
		val attachedRegistry = ReaderPageQaFaultRegistry(
			ReaderPageQaFaultEventSink(transferredEvents::add)
		)
		assertTrue(
			ReaderPageQaFaultControl.enqueue(
				"prearmed-persistence",
				ReaderPageQaFault.FailNextPersistence
			)
		)
		val registration = ReaderPageQaFaultControl.attach(attachedRegistry)
		try {
			assertNotNull(
				attachedRegistry.consumeAndApply(
					ReaderPageQaFault.FailNextPersistence,
					ReaderPageQaFaultOperationContext(
						publicationEpoch = 1L,
						persistenceAttemptId = 1L
					)
				)
			)
			assertEquals(
				listOf(
					ReaderPageQaFaultState.Enqueued,
					ReaderPageQaFaultState.Consumed,
					ReaderPageQaFaultState.Applied
				),
				transferredEvents.map(ReaderPageQaFaultEvent::state)
			)
			assertFalse(
				ReaderPageQaFaultControl.enqueue(
					"prearmed-persistence",
					ReaderPageQaFault.FailNextPersistence
				),
				"Activity recreation must not replay a consumed launch fault."
			)
		} finally {
			ReaderPageQaFaultControl.detach(registration)
			attachedRegistry.closeAndDrain()
			ReaderPageQaFaultControl.clear("clear-prearmed-persistence")
		}
	}

	@Test
	fun prearmedFaultSurvivesRegistryReplacementBeforeConsumption() {
		val oldEvents = mutableListOf<ReaderPageQaFaultEvent>()
		val replacementEvents = mutableListOf<ReaderPageQaFaultEvent>()
		val oldRegistry = ReaderPageQaFaultRegistry(
			ReaderPageQaFaultEventSink(oldEvents::add)
		)
		val replacementRegistry = ReaderPageQaFaultRegistry(
			ReaderPageQaFaultEventSink(replacementEvents::add)
		)
		assertTrue(
			ReaderPageQaFaultControl.enqueue(
				"recreated-persistence",
				ReaderPageQaFault.FailNextPersistence
			)
		)
		val oldRegistration = ReaderPageQaFaultControl.attach(oldRegistry)
		ReaderPageQaFaultControl.detach(oldRegistration)
		oldRegistry.closeAndDrain()
		val replacementRegistration =
			ReaderPageQaFaultControl.attach(replacementRegistry)
		try {
			assertNotNull(
				replacementRegistry.consumeAndApply(
					ReaderPageQaFault.FailNextPersistence,
					ReaderPageQaFaultOperationContext(
						publicationEpoch = 1L,
						persistenceAttemptId = 1L
					)
				)
			)
			assertEquals(
				listOf(
					ReaderPageQaFaultState.Enqueued,
					ReaderPageQaFaultState.Consumed,
					ReaderPageQaFaultState.Applied
				),
				(oldEvents + replacementEvents)
					.filter { it.ticket.requestId == "recreated-persistence" }
					.map(ReaderPageQaFaultEvent::state),
				"Registry replacement must transfer the unconsumed launch fault without duplicating its diagnostic chain."
			)
			assertFalse(
				ReaderPageQaFaultControl.enqueue(
					"recreated-persistence",
					ReaderPageQaFault.FailNextPersistence
				),
				"The transferred launch fault must remain process-idempotent after consumption."
			)
		} finally {
			ReaderPageQaFaultControl.detach(replacementRegistration)
			replacementRegistry.closeAndDrain()
			ReaderPageQaFaultControl.clear("clear-recreated-persistence")
		}
	}

	@Test
	fun replacementRegistrationSurvivesLateDetachFromOldHost() {
		val oldRegistry = ReaderPageQaFaultRegistry()
		val replacementRegistry = ReaderPageQaFaultRegistry()
		val oldRegistration = ReaderPageQaFaultControl.attach(oldRegistry)
		val replacementRegistration =
			ReaderPageQaFaultControl.attach(replacementRegistry)

		ReaderPageQaFaultControl.detach(oldRegistration)
		assertTrue(
			ReaderPageQaFaultControl.enqueue(
				ReaderPageQaFault.FailNextPersistence
			)
		)
		assertNull(
			oldRegistry.consumeAndApply(
				ReaderPageQaFault.FailNextPersistence,
				ReaderPageQaFaultOperationContext(
					publicationEpoch = 1L,
					persistenceAttemptId = 1L
				)
			)
		)
		assertNotNull(
			replacementRegistry.consumeAndApply(
				ReaderPageQaFault.FailNextPersistence,
				ReaderPageQaFaultOperationContext(
					publicationEpoch = 1L,
					persistenceAttemptId = 1L
				)
			)
		)

		ReaderPageQaFaultControl.detach(replacementRegistration)
		oldRegistry.closeAndDrain()
		replacementRegistry.closeAndDrain()
	}
}
