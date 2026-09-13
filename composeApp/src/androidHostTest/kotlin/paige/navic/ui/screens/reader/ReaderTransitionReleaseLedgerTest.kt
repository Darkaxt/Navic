package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import paige.navic.reader.ReaderActiveTransition
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionLivenessTable
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionPhaseKind
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind

@RunWith(RobolectricTestRunner::class)
class ReaderTransitionReleaseLedgerTest {
	@Test
	fun staleResourceReceivesOneReleaseCommandAndConfirmation() {
		val ledger = ReaderTransitionReleaseLedger()
		val key = deckKey(transitionId(), opaqueId = 41L)

		assertNull(ledger.stateOf(key))
		assertTrue(ledger.register(key))
		assertNotNull(ledger.requestRelease(key))
		assertNull(ledger.requestRelease(key))
		assertTrue(ledger.confirmReleased(key))
		assertFalse(ledger.confirmReleased(key))
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(key))
	}

	@Test
	fun earlyReleaseCallbackLatchesTerminalStateBeforeLaterRegistration() {
		val ledger = ReaderTransitionReleaseLedger()
		val key = deckKey(transitionId(), opaqueId = 42L)

		assertTrue(ledger.confirmReleased(key))
		assertFalse(ledger.confirmReleased(key))
		assertFalse(ledger.register(key))
		assertNull(ledger.requestRelease(key))
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(key))
	}

	@Test
	fun earlyReleaseConfirmationLatchIsBounded() {
		val ledger = ReaderTransitionReleaseLedger()
		val id = transitionId()
		repeat(32) { index ->
			assertTrue(ledger.confirmReleased(deckKey(id, opaqueId = 1_000L + index)))
		}

		assertFailsWith<IllegalStateException> {
			ledger.confirmReleased(deckKey(id, opaqueId = 2_000L))
		}
		assertEquals(32, ledger.snapshot().releasedCount)
	}

	@Test
	fun exactDuplicateResourceAndCallbackFactsReleaseOnce() {
		val ledger = ReaderTransitionReleaseLedger()
		val key = deckKey(transitionId(), opaqueId = 43L)

		assertTrue(ledger.register(key))
		assertFalse(ledger.register(key))
		assertIs<ReaderTransitionCommand.ReleaseResource>(ledger.requestRelease(key))
		assertNull(ledger.requestRelease(key))
		assertTrue(ledger.confirmReleased(key))
		assertFalse(ledger.confirmReleased(key))
		assertFalse(ledger.register(key))
		assertEquals(
			ReaderTransitionReleaseLedgerSnapshot(ownedCount = 0, issuedCount = 0, releasedCount = 1),
			ledger.snapshot()
		)
	}

	@Test
	fun callbackBeforeOwnershipAndOwnershipBeforeCallbackConvergeCorrectly() {
		val callbackFirst = ReaderTransitionReleaseLedger()
		val callbackFirstKey = deckKey(transitionId(), opaqueId = 47L)
		assertTrue(callbackFirst.confirmReleased(callbackFirstKey))
		assertFalse(callbackFirst.register(callbackFirstKey))
		assertNull(callbackFirst.requestRelease(callbackFirstKey))

		val ownershipFirst = ReaderTransitionReleaseLedger()
		val ownershipFirstKey = deckKey(transitionId(), opaqueId = 53L)
		assertTrue(ownershipFirst.register(ownershipFirstKey))
		assertNotNull(ownershipFirst.requestRelease(ownershipFirstKey))
		assertFalse(ownershipFirst.register(ownershipFirstKey))
		assertTrue(ownershipFirst.confirmReleased(ownershipFirstKey))

		assertEquals(ReaderTransitionResourceState.Released, callbackFirst.stateOf(callbackFirstKey))
		assertEquals(ReaderTransitionResourceState.Released, ownershipFirst.stateOf(ownershipFirstKey))
	}

	@Test
	fun shadowReleasePredictionDoesNotIssueOrPoisonLaterCutoverAdoption() {
		val ledger = ReaderTransitionReleaseLedger()
		val ports = TestPorts { _, _ -> error("Shadow mode cannot issue commands") }
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Shadow,
			journal = ReaderTransitionJournal(),
			releaseLedger = ledger
		)
		val key = deckKey(transitionId(), opaqueId = 57L)
		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(key.transitionId, key))

		val coordinatorSnapshot = coordinator.snapshot()
		assertEquals(
			setOf(ReaderTransitionCommandKind.ReleaseResource),
			coordinatorSnapshot.shadowPredictions.last().commandKinds
		)
		assertEquals(0, coordinatorSnapshot.releaseCommandIssuedCount)
		assertEquals(ReaderTransitionResourceState.Owned, ledger.stateOf(key))

		val cutover = ReaderDeckAdmissionCutover(
			legacyAdmissionHost = UnavailableReaderDeckAdmissionLeaseHost,
			coordinatorAdmissionHost = UnavailableReaderDeckAdmissionLeaseHost,
			legacyInventory = {
				ReaderLegacyDeckInventory.Complete(
					listOf(
						ReaderLegacyDeckResource(
							key = key,
							origin = ReaderLegacyDeckResourceOrigin.Owned,
							predecessorEvidence = ReaderLegacyPredecessorEvidence.Truthful
						)
					)
				)
			},
			releaseLedger = ledger,
			enqueueResourceFact = { error("A truthful predecessor must be adopted") }
		)
		assertTrue(cutover.activate())
		assertEquals(1, cutover.snapshot().adoptedCount)
		assertEquals(1, cutover.snapshot().releaseOwnedCount)
		assertEquals(0, cutover.snapshot().releaseCommandIssuedCount)
	}

	@Test
	fun coordinatorDeduplicatesStaleResourceFactsAndConfirmsThroughItsMailbox() {
		val commands = mutableListOf<ReaderTransitionCommand>()
		lateinit var ports: TestPorts
		ports = TestPorts { command, onFact ->
			commands += command
			if (command is ReaderTransitionCommand.ReleaseResource) {
				onFact(ReaderTransitionFact.ResourceReleased(command.transitionId, command.key))
			}
		}
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = ReaderTransitionJournal()
		)
		val key = deckKey(transitionId(), opaqueId = 59L)

		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(key.transitionId, key))
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(key.transitionId, key))

		assertEquals(1, commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().size)
		assertEquals(ReaderTransitionResourceState.Released, coordinator.releaseStateOf(key))
		assertEquals(1, coordinator.snapshot().releasedResourceCount)
	}

	@Test
	fun closeTimeoutRetainsReleaseOnlySinkForLateFacts() {
		val commands = mutableListOf<ReaderTransitionCommand>()
		val ports = TestPorts { command, onFact ->
			commands += command
			if (command is ReaderTransitionCommand.ReleaseResource) {
				onFact(ReaderTransitionFact.ResourceReleased(command.transitionId, command.key))
			}
		}
		val id = transitionId(operation = ReaderTransitionOperation.PublicationClose)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = journal(id)
		)

		coordinator.enqueue(ReaderTransitionFact.DeadlineExpired(id))
		assertTrue(coordinator.snapshot().releaseOnlySink)
		val commandCountAtClose = commands.size
		val lateKey = deckKey(id.copy(sequence = id.sequence + 1L), opaqueId = 61L)
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(lateKey.transitionId, lateKey))
		coordinator.enqueue(ReaderTransitionFact.DeckOwned(lateKey.transitionId, lateKey))

		assertEquals(
			1,
			commands.drop(commandCountAtClose).filterIsInstance<ReaderTransitionCommand.ReleaseResource>().size
		)
		assertEquals(ReaderTransitionResourceState.Released, coordinator.releaseStateOf(lateKey))
		assertTrue(coordinator.snapshot().releaseOnlySink)
		assertEquals(null, coordinator.snapshot().activePhase)
	}

	private class TestPorts(
		private val onIssue: (ReaderTransitionCommand, (ReaderTransitionFact) -> Unit) -> Unit
	) : ReaderResumableTransitionPorts {
		override val clock: ReaderTransitionClock = object : ReaderTransitionClock {
			override fun nowMillis(): Long = 1_000L
			override fun schedule(
				atMillis: Long,
				action: () -> Unit
			): ReaderTransitionClockRegistration = ReaderTransitionClockRegistration {}
		}

		override fun issue(command: ReaderTransitionCommand, onFact: (ReaderTransitionFact) -> Unit) {
			onIssue(command, onFact)
		}
	}

	private fun journal(id: ReaderTransitionId): ReaderTransitionJournal {
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				token = ReaderPresentationToken(67L),
				binding = binding,
				coverGeneration = 71L,
				presentedFrame = 73L,
				viewportWidth = 1200,
				viewportHeight = 800
			)
		)
		return ReaderTransitionJournal(
			active = ReaderActiveTransition(
				id = id,
				phase = ReaderTransitionLivenessTable.phase(
					id = id,
					kind = ReaderTransitionPhaseKind.AwaitingPrerequisites,
					retainedOwner = owner
				)
			)
		)
	}

	private fun transitionId(
		operation: ReaderTransitionOperation = ReaderTransitionOperation.CoverToPageEntry
	): ReaderTransitionId {
		val binding = ReaderPresentationBinding(
			foliateSessionId = "synthetic",
			publicationGeneration = 2L,
			viewportGeneration = 3L,
			profileGeneration = 5L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity("synthetic", 7L),
			rasterGeneration = 11L,
			textureGeneration = 13L,
			preparationGeneration = 17L
		)
		return ReaderTransitionId(
			readerSessionGeneration = 19L,
			coordinatorEpoch = 23L,
			sequence = 29L,
			operation = operation,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
		)
	}

	private fun deckKey(id: ReaderTransitionId, opaqueId: Long) = ReaderTransitionResourceKey(
		transitionId = id,
		kind = ReaderTransitionResourceKind.Deck,
		opaqueId = opaqueId
	)
}
