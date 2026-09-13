package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionDeckRole
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind

@RunWith(RobolectricTestRunner::class)
class ReaderDeckAdmissionCutoverTest {
	@Test
	fun productionDefaultDeckAdmissionPolicyRemainsLegacyOnlyUntilTask4() {
		val legacyHost = UnavailableReaderDeckAdmissionLeaseHost

		assertEquals(
			ReaderDeckAdmissionProductionMode.LegacyOnly,
			ReaderDeckAdmissionProductionPolicy.mode
		)
		assertFalse(ReaderDeckAdmissionProductionPolicy.coordinatorActivationAvailable)
		assertSame(legacyHost, ReaderDeckAdmissionProductionPolicy.select(legacyHost))
	}

	@Test
	fun protocolInventoryBoundaryReturnsEveryRegisteredExactResource() {
		val boundary = ReaderDeckResourceInventoryBoundary()
		val owned = resource(99L, ReaderLegacyDeckResourceOrigin.Owned)
		val pending = resource(100L, ReaderLegacyDeckResourceOrigin.Pending)
		val discovered = resource(101L, ReaderLegacyDeckResourceOrigin.Discovered)
		assertTrue(boundary.register(owned))
		assertTrue(boundary.register(pending))
		assertTrue(boundary.register(discovered))
		assertFalse(boundary.register(owned))

		val inventory = kotlin.test.assertIs<ReaderLegacyDeckInventory.Complete>(
			boundary.freezeAndInventory()
		)
		assertEquals(listOf(owned, pending, discovered), inventory.resources)
	}

	@Test
	fun protocolInventoryBoundaryCanFailClosedAsIncomplete() {
		val boundary = ReaderDeckResourceInventoryBoundary { false }
		boundary.register(resource(102L))

		assertEquals(ReaderLegacyDeckInventory.Incomplete, boundary.freezeAndInventory())
	}

	@Test
	fun incompleteLegacyInventoryKeepsLegacyAsSoleWriter() {
		val cutover = deckCutover(ReaderLegacyDeckInventory.Incomplete)

		assertFalse(cutover.activate())

		assertTrue(cutover.legacyAdmissionOpen)
		assertFalse(cutover.coordinatorAdmissionOpen)
		assertEquals(
			ReaderDeckAdmissionCutoverOutcome.InventoryIncomplete,
			cutover.snapshot().activationOutcome
		)
	}

	@Test
	fun `complete inventory adopts at most one truthful predecessor`() {
		val truthful = resource(101L, predecessor = ReaderLegacyPredecessorEvidence.Truthful)
		val unprovable = resource(103L, predecessor = ReaderLegacyPredecessorEvidence.Unprovable)
		val first = synchronousCutover(ReaderLegacyDeckInventory.Complete(listOf(truthful, unprovable)))

		assertTrue(first.activate())
		assertEquals(1, first.snapshot().adoptedCount)
		assertEquals(1, first.snapshot().releaseOwnedCount)
		assertEquals(1, first.snapshot().releaseCommandCount)

		val conflictingA = resource(107L, predecessor = ReaderLegacyPredecessorEvidence.Truthful)
		val conflictingB = resource(109L, predecessor = ReaderLegacyPredecessorEvidence.Truthful)
		val conflicting = synchronousCutover(
			ReaderLegacyDeckInventory.Complete(listOf(conflictingA, conflictingB))
		)
		assertTrue(conflicting.activate())
		assertEquals(0, conflicting.snapshot().adoptedCount)
		assertEquals(2, conflicting.snapshot().releaseCommandCount)
	}

	@Test
	fun `all non-adopted owned pending and discovered resources drain before activation`() {
		val callbacks = mutableListOf<(ReaderTransitionFact) -> Unit>()
		val commands = mutableListOf<ReaderTransitionCommand.ReleaseResource>()
		val inventory = ReaderLegacyDeckInventory.Complete(
			listOf(
				resource(113L, ReaderLegacyDeckResourceOrigin.Owned),
				resource(127L, ReaderLegacyDeckResourceOrigin.Pending),
				resource(131L, ReaderLegacyDeckResourceOrigin.Discovered)
			)
		)
		val cutover = deckCutover(inventory) { command, onFact ->
			commands += command
			callbacks += onFact
		}

		assertFalse(cutover.activate())
		assertFalse(cutover.legacyAdmissionOpen)
		assertFalse(cutover.coordinatorAdmissionOpen)
		assertEquals(ReaderDeckAdmissionCutoverOutcome.Draining, cutover.snapshot().activationOutcome)
		assertEquals(3, cutover.snapshot().releaseCommandIssuedCount)

		callbacks.take(2).zip(commands.take(2)).forEach { (callback, command) ->
			callback(ReaderTransitionFact.ResourceReleased(command.transitionId, command.key))
		}
		assertFalse(cutover.coordinatorAdmissionOpen)
		assertEquals(1, cutover.snapshot().releaseCommandIssuedCount)

		callbacks.last().invoke(
			ReaderTransitionFact.ResourceReleased(commands.last().transitionId, commands.last().key)
		)
		assertTrue(cutover.coordinatorAdmissionOpen)
		assertFalse(cutover.legacyAdmissionOpen)
		assertEquals(ReaderDeckAdmissionCutoverOutcome.Activated, cutover.snapshot().activationOutcome)
		assertEquals(3, cutover.snapshot().releaseConfirmationCount)
	}

	@Test
	fun `exact duplicate resources and callback facts release once`() {
		val callbacks = mutableListOf<(ReaderTransitionFact) -> Unit>()
		val commands = mutableListOf<ReaderTransitionCommand.ReleaseResource>()
		val duplicate = resource(137L, ReaderLegacyDeckResourceOrigin.Discovered)
		val cutover = deckCutover(
			ReaderLegacyDeckInventory.Complete(listOf(duplicate, duplicate, duplicate.copy(
				origin = ReaderLegacyDeckResourceOrigin.Owned
			)))
		) { command, onFact ->
			commands += command
			callbacks += onFact
		}

		assertFalse(cutover.activate())
		assertEquals(1, commands.size)
		val released = ReaderTransitionFact.ResourceReleased(commands.single().transitionId, commands.single().key)
		callbacks.single()(released)
		callbacks.single()(released)
		cutover.observeLegacyResource(duplicate)

		assertEquals(1, commands.size)
		assertEquals(1, cutover.snapshot().inventoryCount)
		assertEquals(1, cutover.snapshot().releaseConfirmationCount)
		assertTrue(cutover.coordinatorAdmissionOpen)
	}

	@Test
	fun `callback-before-ownership and ownership-before-callback converge correctly`() {
		val callbackFirst = synchronousCutover(ReaderLegacyDeckInventory.Complete(emptyList()))
		assertTrue(callbackFirst.activate())
		val callbackFirstResource = resource(139L, ReaderLegacyDeckResourceOrigin.Discovered)
		callbackFirst.observeLegacyResource(callbackFirstResource)
		callbackFirst.observeLegacyResource(
			callbackFirstResource.copy(origin = ReaderLegacyDeckResourceOrigin.Owned)
		)

		val ownershipFirst = synchronousCutover(ReaderLegacyDeckInventory.Complete(emptyList()))
		assertTrue(ownershipFirst.activate())
		val ownershipFirstResource = resource(149L, ReaderLegacyDeckResourceOrigin.Owned)
		ownershipFirst.observeLegacyResource(ownershipFirstResource)
		ownershipFirst.observeLegacyResource(
			ownershipFirstResource.copy(origin = ReaderLegacyDeckResourceOrigin.Discovered)
		)

		assertEquals(1, callbackFirst.snapshot().releaseCommandCount)
		assertEquals(1, callbackFirst.snapshot().releaseConfirmationCount)
		assertEquals(1, ownershipFirst.snapshot().releaseCommandCount)
		assertEquals(1, ownershipFirst.snapshot().releaseConfirmationCount)
	}

	@Test
	fun `close-timeout retains release-only sink for late facts`() {
		val cutover = synchronousCutover(ReaderLegacyDeckInventory.Complete(emptyList()))
		assertTrue(cutover.activate())

		cutover.retainReleaseOnlySinkAfterCloseTimeout()
		cutover.observeLegacyResource(resource(151L, ReaderLegacyDeckResourceOrigin.Discovered))

		assertFalse(cutover.legacyAdmissionOpen)
		assertFalse(cutover.coordinatorAdmissionOpen)
		assertEquals(ReaderDeckAdmissionCutoverOutcome.ReleaseOnly, cutover.snapshot().activationOutcome)
		assertEquals(1, cutover.snapshot().releaseCommandCount)
		assertEquals(1, cutover.snapshot().releaseConfirmationCount)
	}

	@Test
	fun `no dual-writer interval and no fallback after activation`() {
		val statesDuringCutover = mutableListOf<Pair<Boolean, Boolean>>()
		lateinit var cutover: ReaderDeckAdmissionCutover
		val inventory = ReaderLegacyDeckInventory.Complete(
			listOf(resource(157L, ReaderLegacyDeckResourceOrigin.Pending))
		)
		val ledger = ReaderTransitionReleaseLedger()
		cutover = ReaderDeckAdmissionCutover(
			legacyAdmissionHost = UnavailableReaderDeckAdmissionLeaseHost,
			coordinatorAdmissionHost = UnavailableReaderDeckAdmissionLeaseHost,
			legacyInventory = {
				statesDuringCutover += cutover.legacyAdmissionOpen to cutover.coordinatorAdmissionOpen
				inventory
			},
			releaseLedger = ledger,
			enqueueResourceFact = { fact ->
				statesDuringCutover += cutover.legacyAdmissionOpen to cutover.coordinatorAdmissionOpen
				val key = (fact as ReaderTransitionFact.DeckReserved).key
				ledger.register(key)
				val command = requireNotNull(ledger.requestRelease(key))
				ledger.confirmReleased(command.key)
				cutover.onCoordinatorResourceFactProcessed(
					ReaderTransitionFact.ResourceReleased(command.transitionId, command.key)
				)
			}
		)

		assertTrue(cutover.activate())
		assertTrue(statesDuringCutover.all { (legacy, coordinator) -> !legacy && !coordinator })
		assertFalse(cutover.legacyAdmissionOpen && cutover.coordinatorAdmissionOpen)
		assertFalse(cutover.legacyAdmissionOpen)
		assertTrue(cutover.coordinatorAdmissionOpen)

		assertTrue(cutover.activate())
		assertFalse(cutover.legacyAdmissionOpen)
		assertTrue(cutover.coordinatorAdmissionOpen)
	}

	@Test
	fun `typed deck and release ports execute only in active cutover states`() {
		val issued = mutableListOf<ReaderTransitionCommand>()
		val callbacks = mutableListOf<ReaderTransitionFact>()
		val delegate = object : ReaderResumableTransitionPorts {
			override val clock = object : ReaderTransitionClock {
				override fun nowMillis(): Long = 1L
				override fun schedule(
					atMillis: Long,
					action: () -> Unit
				): ReaderTransitionClockRegistration = ReaderTransitionClockRegistration {}
			}

			override fun issue(
				command: ReaderTransitionCommand,
				onFact: (ReaderTransitionFact) -> Unit
			) {
				issued += command
				if (command is ReaderTransitionCommand.ReleaseResource) {
					onFact(ReaderTransitionFact.ResourceReleased(command.transitionId, command.key))
				}
			}
		}
		val inactive = deckCutover(ReaderLegacyDeckInventory.Incomplete)
		val inactivePorts = ReaderCutoverTransitionPorts(delegate, inactive)
		val key = resource(159L).key
		assertFailsWith<IllegalStateException> {
			inactivePorts.issue(ReaderTransitionCommand.ReleaseResource(key.transitionId, key), callbacks::add)
		}
		assertTrue(issued.isEmpty())

		val active = synchronousCutover(ReaderLegacyDeckInventory.Complete(emptyList()))
		assertTrue(active.activate())
		val activePorts = ReaderCutoverTransitionPorts(delegate, active)
		val binding = (key.transitionId.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		activePorts.issue(
			ReaderTransitionCommand.ReserveDeck(
				transitionId = key.transitionId,
				binding = binding,
				role = ReaderTransitionDeckRole.Initial
			),
			callbacks::add
		)
		activePorts.issue(ReaderTransitionCommand.ReleaseResource(key.transitionId, key), callbacks::add)

		assertEquals(2, issued.size)
		assertEquals(1, callbacks.size)
	}

	@Test
	fun `snapshot contains only bounded counts and outcome`() {
		val privateOpaqueId = 918_273_645L
		val cutover = synchronousCutover(
			ReaderLegacyDeckInventory.Complete(listOf(resource(privateOpaqueId)))
		)
		assertTrue(cutover.activate())

		val snapshot = cutover.snapshot()
		assertFalse(snapshot.toString().contains(privateOpaqueId.toString()))
		assertEquals(1, snapshot.inventoryCount)
		assertEquals(1, snapshot.releaseCommandCount)
	}

	private fun synchronousCutover(inventory: ReaderLegacyDeckInventory): ReaderDeckAdmissionCutover =
		deckCutover(inventory) { command, onFact ->
			onFact(ReaderTransitionFact.ResourceReleased(command.transitionId, command.key))
		}

	private fun deckCutover(
		inventory: ReaderLegacyDeckInventory,
		release: (
			ReaderTransitionCommand.ReleaseResource,
			(ReaderTransitionFact) -> Unit
		) -> Unit = { _, _ -> }
	): ReaderDeckAdmissionCutover {
		val ledger = ReaderTransitionReleaseLedger()
		lateinit var cutover: ReaderDeckAdmissionCutover
		cutover = ReaderDeckAdmissionCutover(
			legacyAdmissionHost = UnavailableReaderDeckAdmissionLeaseHost,
			coordinatorAdmissionHost = UnavailableReaderDeckAdmissionLeaseHost,
			legacyInventory = { inventory },
			releaseLedger = ledger,
			enqueueResourceFact = { fact ->
				val key = when (fact) {
					is ReaderTransitionFact.ResourceObserved -> fact.key
					is ReaderTransitionFact.DeckReserved -> fact.key
					is ReaderTransitionFact.DeckOwned -> fact.key
					else -> error("Unexpected cutover fact")
				}
				ledger.register(key)
				ledger.requestRelease(key)?.let { command ->
					release(command) { callbackFact ->
						if (callbackFact is ReaderTransitionFact.ResourceReleased) {
							ledger.confirmReleased(callbackFact.key)
						}
						cutover.onCoordinatorResourceFactProcessed(callbackFact)
					}
				}
			}
		)
		return cutover
	}

	private fun resource(
		opaqueId: Long,
		origin: ReaderLegacyDeckResourceOrigin = ReaderLegacyDeckResourceOrigin.Owned,
		predecessor: ReaderLegacyPredecessorEvidence = ReaderLegacyPredecessorEvidence.Unprovable
	) = ReaderLegacyDeckResource(
		key = ReaderTransitionResourceKey(
			transitionId = transitionId(),
			kind = ReaderTransitionResourceKind.Deck,
			opaqueId = opaqueId
		),
		origin = origin,
		predecessorEvidence = predecessor
	)

	private fun transitionId() = ReaderTransitionId(
		readerSessionGeneration = 2L,
		coordinatorEpoch = 3L,
		sequence = 5L,
		operation = ReaderTransitionOperation.CoverToPageEntry,
		expectedBinding = ReaderExpectedPresentationBinding.Exact(
			ReaderPresentationBinding(
				foliateSessionId = "synthetic",
				publicationGeneration = 7L,
				viewportGeneration = 11L,
				profileGeneration = 13L,
				destinationCommitIdentity = ReaderDestinationCommitIdentity("synthetic", 17L)
			)
		)
	)
}
