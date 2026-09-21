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
import paige.navic.reader.ReaderAdoptedPredecessorSeedId
import paige.navic.reader.ReaderCommittedPresentation
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderInitialCommittedPresentationOrigin
import paige.navic.reader.ReaderInitialPresentationInputLease
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderResourceRetirementOrder
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
import paige.navic.reader.ReaderTransitionResourceOwnerId
import paige.navic.reader.ReaderTransitionResourceRegistration
import paige.navic.reader.parentIdentity

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
	fun earlyReleaseConfirmationTombstonesEvictWithoutReopeningOldKeys() {
		val ledger = ReaderTransitionReleaseLedger()
		val oldest = deckKey(transitionId(), opaqueId = 1_000L)
		assertTrue(ledger.confirmReleased(oldest))
		repeat(32) { index ->
			val key = deckKey(
				transitionId(sequence = index.toLong() + 30L),
				opaqueId = 2_000L + index
			)
			assertTrue(ledger.confirmReleased(key))
		}

		assertFalse(ledger.register(oldest))
		assertNull(ledger.requestRelease(oldest))
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
		val ports = TestPorts(onIssue = { _, _ ->
			error("Shadow mode cannot issue commands")
		})
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Shadow,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(1L, 1L),
				lastTransitionSequence = 0L,
				lastIssuedTransitionIdentity = null
			),
			releaseLedger = ledger
		)
		val key = deckKey(transitionId(), opaqueId = 57L)
		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(requireNotNull(key.owningTransitionIdOrNull), key))

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
		ports = TestPorts(onIssue = { command, onFact ->
			commands += command
			if (command is ReaderTransitionCommand.ReleaseResource) {
				onFact(ReaderTransitionFact.ResourceReleased(command.transitionId, command.key))
			}
		})
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(1L, 1L),
				lastTransitionSequence = 0L,
				lastIssuedTransitionIdentity = null
			)
		)
		val key = deckKey(transitionId(), opaqueId = 59L)

		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(requireNotNull(key.owningTransitionIdOrNull), key))
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(requireNotNull(key.owningTransitionIdOrNull), key))

		assertEquals(1, commands.filterIsInstance<ReaderTransitionCommand.ReleaseResource>().size)
		assertEquals(ReaderTransitionResourceState.Released, coordinator.releaseStateOf(key))
		assertEquals(1, coordinator.snapshot().releasedResourceCount)
	}

	@Test
	fun closeTimeoutRetainsReleaseOnlySinkForLateFacts() {
		val commands = mutableListOf<ReaderTransitionCommand>()
		val ports = TestPorts(onIssue = { command, onFact ->
			commands += command
			if (command is ReaderTransitionCommand.ReleaseResource) {
				onFact(ReaderTransitionFact.ResourceReleased(command.transitionId, command.key))
			}
		})
		val id = transitionId(operation = ReaderTransitionOperation.PublicationClose)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = journal(id)
		)

		coordinator.enqueue(ReaderTransitionFact.DeadlineExpired(id))
		assertTrue(coordinator.snapshot().releaseOnlySink)
		val commandCountAtClose = commands.size
		val lateKey = deckKey(id.copy(sequence = id.sequence + 1L, parent = id.parentIdentity()), opaqueId = 61L)
		coordinator.enqueue(ReaderTransitionFact.ResourceObserved(requireNotNull(lateKey.owningTransitionIdOrNull), lateKey))
		coordinator.enqueue(ReaderTransitionFact.DeckOwned(requireNotNull(lateKey.owningTransitionIdOrNull), lateKey))

		assertEquals(
			1,
			commands.drop(commandCountAtClose).filterIsInstance<ReaderTransitionCommand.ReleaseResource>().size
		)
		assertEquals(ReaderTransitionResourceState.Released, coordinator.releaseStateOf(lateKey))
		assertTrue(coordinator.snapshot().releaseOnlySink)
		assertEquals(null, coordinator.snapshot().activePhase)
	}

	@Test
	fun adoptedReleaseConfirmationUsesRegistrationRetirementFenceWithoutTransitionTombstone() {
		val binding = transitionId().let {
			(it.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		}
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(79L),
				binding,
				83L,
				89L,
				1200,
				800
			)
		)
		val seedId = ReaderAdoptedPredecessorSeedId.fromValidatedImport(97L)
		val registration = ReaderTransitionResourceRegistration(
			ReaderTransitionResourceKey(
				ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
				ReaderTransitionResourceKind.FrameHandoff,
				101L
			),
			ReaderResourceRetirementOrder(19L, 23L, 1L)
		)
		val journal = ReaderTransitionJournal(
			committed = ReaderCommittedPresentation.Initial(
				ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
					seedId = seedId,
					readerSessionGeneration = 19L,
					coordinatorEpoch = 23L,
					owner = owner,
					binding = binding,
					resource = registration,
					requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
					physicalLease = ReaderInitialPresentationInputLease.ChromeOnly
				)
			)
		)
		val issued = mutableListOf<ReaderTransitionCommand.ReleaseResource>()
		val ports = TestPorts(onIssue = { command, onFact ->
			if (command is ReaderTransitionCommand.ReleaseResource) {
				issued += command
				onFact(
					ReaderTransitionFact.ResourceReleased(
						command.transitionId,
						command.key,
						command.registration
					)
				)
			}
		})
		val ledger = ReaderTransitionReleaseLedger()
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = journal,
			releaseLedger = ledger
		)

		coordinator.enqueue(ReaderTransitionFact.PublicationClosed(null))

		assertEquals(1, issued.size)
		assertNull(issued.single().transitionId)
		assertEquals(0, ledger.retentionSnapshot().activeStateCount)
		assertEquals(1L, ledger.retentionSnapshot().contiguousReleasedThrough)
	}

	@Test
	fun task4FactGateRejectsMalformedResourceFactsBeforeMailboxAccounting() {
		val commands = mutableListOf<ReaderTransitionCommand>()
		val ports = TestPorts(
			onIssue = { command, _ -> commands += command },
			factAcceptance = ReaderTransitionFact::isTask4CoordinatorFact
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(1L, 1L),
				lastTransitionSequence = 0L,
				lastIssuedTransitionIdentity = null
			)
		)
		val id = transitionId()
		val otherId = id.copy(sequence = id.sequence + 1L, parent = id.parentIdentity())
		val deckKey = deckKey(id, opaqueId = 71L)
		val malformed = listOf(
			ReaderTransitionFact.DeckOwned(id, deckKey.copy(transitionId = otherId)) to deckKey,
			ReaderTransitionFact.DeckPrepared(
				id,
				deckKey.copy(kind = ReaderTransitionResourceKind.Raster)
			) to deckKey,
			ReaderTransitionFact.ResourceObserved(
				id,
				deckKey.copy(kind = ReaderTransitionResourceKind.CallbackRegistration)
			) to deckKey,
			ReaderTransitionFact.ResourceReleased(
				id,
				deckKey.copy(kind = ReaderTransitionResourceKind.FrameHandoff)
			) to deckKey
		)
		val before = coordinator.snapshot()

		malformed.forEach { (fact, key) ->
			assertFalse(fact.isTask4CoordinatorFact())
			assertFailsWith<IllegalStateException> { coordinator.enqueue(fact) }
			assertEquals(before, coordinator.snapshot())
			assertNull(coordinator.releaseStateOf(key))
		}
		assertTrue(commands.isEmpty())
	}

	private class TestPorts(
		private val onIssue: (ReaderTransitionCommand, (ReaderTransitionFact) -> Unit) -> Unit,
		private val factAcceptance: (ReaderTransitionFact) -> Boolean = { true }
	) : ReaderResumableTransitionPorts {
		override val clock: ReaderTransitionClock = object : ReaderTransitionClock {
			override fun nowMillis(): Long = 1_000L
			override fun schedule(
				atMillis: Long,
				action: () -> Unit
			): ReaderTransitionClockRegistration = ReaderTransitionClockRegistration {}
		}

		override fun acceptsFact(fact: ReaderTransitionFact): Boolean = factAcceptance(fact)

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
		return readerAndroidHostTestJournal(
			committed = readerAndroidHostTestAdoptedInitial(
				owner = owner,
				binding = binding,
				readerSessionGeneration = id.readerSessionGeneration,
				coordinatorEpoch = id.coordinatorEpoch
			),
			lastTransitionSequence = id.sequence,
			lastIssuedTransitionIdentity = id.parentIdentity(),
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
		operation: ReaderTransitionOperation = ReaderTransitionOperation.CoverToPageEntry,
		sequence: Long = 1L
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
			sequence = sequence,
			operation = operation,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding),
			parent = if (sequence == 1L) null else paige.navic.reader.ReaderTransitionParentIdentity(
				19L,
				23L,
				sequence - 1L
			)
		)
	}

	private fun deckKey(id: ReaderTransitionId, opaqueId: Long) = ReaderTransitionResourceKey(
		transitionId = id,
		kind = ReaderTransitionResourceKind.Deck,
		opaqueId = opaqueId
	)
}
