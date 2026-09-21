package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import paige.navic.reader.ReaderActiveTransition
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionDeckRole
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFactKind
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionLivenessTable
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionPhaseKind
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceProvenance
import paige.navic.reader.ReaderTransitionDeferralReason
import paige.navic.reader.ReaderTransitionNonce
import paige.navic.reader.ReaderTransitionResumeRecord
import paige.navic.reader.ReaderTransitionWakeKind
import paige.navic.reader.parentIdentity

@RunWith(RobolectricTestRunner::class)
class ReaderDeckAdmissionCutoverTest {
	@Test
	fun physicalDeckAndCallbackFreezeDrainAndRestoreByStableExactIdentity() {
		val releaseCallbacks = mutableListOf<() -> Unit>()
		val restored = mutableListOf<ReaderDeckPhysicalRestartDescriptor>()
		val adapter = ReaderDeckPhysicalOwnershipAdapter(
			releasePhysicalDeck = { _, onReleased -> releaseCallbacks.add(onReleased) },
			restorePhysicalDeck = { restart ->
				restored += restart
				restart
			}
		)
		val descriptor = ReaderDeckPhysicalRestartDescriptor(
			binding = binding(textureGeneration = 401L),
			role = ReaderDeckSubmissionRole.Active,
			preparationGeneration = 11L,
			rasterGeneration = 31L,
			textureGeneration = 401L
		)
		val lease = assertNotNull(adapter.register(descriptor))
		assertTrue(adapter.acknowledgeRendererOwnership(lease))
		val domain = ReaderLegacyPhysicalDomain(7L, ReaderLegacyFreezeToken(13L))

		assertEquals(ReaderPortCommandResult.Accepted, adapter.freezeForTransitionActivation(domain))
		assertNull(
			adapter.register(
				descriptor.copy(
					binding = binding(textureGeneration = 402L),
					textureGeneration = 402L
				)
			)
		)
		val frozen = adapter.snapshotFrozenOwnership()
		assertEquals(2, frozen.size)
		val deck = frozen.single { it.kind == ReaderTransitionResourceKind.Deck }
		val callback = frozen.single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}
		assertEquals(ReaderLegacyInventorySource.Deck, deck.physicalIdentity.source)
		assertEquals(ReaderLegacyInventorySource.Deck, callback.physicalIdentity.source)
		assertEquals(ReaderLegacyResourceState.RendererOwned, deck.state)
		assertEquals(ReaderLegacyResourceState.Registered, callback.state)
		assertEquals(2, frozen.map { it.physicalIdentity }.toSet().size)

		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			adapter.drainFrozenOwnership(callback.physicalIdentity, confirmed::add)
		)
		assertEquals(listOf(callback.physicalIdentity), confirmed)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			adapter.drainFrozenOwnership(deck.physicalIdentity, confirmed::add)
		)
		assertEquals(1, releaseCallbacks.size)
		assertEquals(listOf(callback.physicalIdentity), confirmed)

		releaseCallbacks.single().invoke()
		assertEquals(setOf(callback.physicalIdentity, deck.physicalIdentity), confirmed.toSet())
		assertTrue(adapter.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			adapter.restoreAfterTransitionActivation(domain)
		)
		assertEquals(listOf(descriptor), restored)
	}

	@Test
	fun rendererCallbackBeforeDrainIsTombstonedAndRestoredWithoutDispatch() {
		val releaseCallbacks = mutableListOf<() -> Unit>()
		val adapter = ReaderDeckPhysicalOwnershipAdapter(
			releasePhysicalDeck = { _, onReleased -> releaseCallbacks.add(onReleased) },
			restorePhysicalDeck = { it }
		)
		val lease = assertNotNull(
			adapter.register(
				ReaderDeckPhysicalRestartDescriptor(
					binding = binding(textureGeneration = 403L),
					role = ReaderDeckSubmissionRole.Active,
					preparationGeneration = 11L,
					rasterGeneration = 31L,
					textureGeneration = 403L
				)
			)
		)
		assertTrue(adapter.acknowledgeRendererOwnership(lease))
		val domain = ReaderLegacyPhysicalDomain(7L, ReaderLegacyFreezeToken(17L))
		assertEquals(ReaderPortCommandResult.Accepted, adapter.freezeForTransitionActivation(domain))
		val original = adapter.snapshotFrozenOwnership()
		val callback = original.single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}

		assertFalse(adapter.observeRendererCallback(lease))
		val discovered = adapter.snapshotFrozenOwnership().single {
			it.kind == ReaderTransitionResourceKind.CallbackRegistration
		}
		assertEquals(callback.physicalIdentity, discovered.physicalIdentity)
		assertEquals(ReaderLegacyResourceState.ReleaseRequested, discovered.state)

		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			adapter.drainFrozenOwnership(discovered.physicalIdentity, confirmed::add)
		)
		assertEquals(listOf(discovered.physicalIdentity), confirmed)
		val deck = adapter.snapshotFrozenOwnership().single()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			adapter.drainFrozenOwnership(deck.physicalIdentity, confirmed::add)
		)
		releaseCallbacks.single().invoke()
		assertEquals(ReaderPortCommandResult.Accepted, adapter.restoreAfterTransitionActivation(domain))
		val restoredDomain = ReaderLegacyPhysicalDomain(7L, ReaderLegacyFreezeToken(19L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			adapter.freezeForTransitionActivation(restoredDomain)
		)
		assertEquals(
			ReaderLegacyResourceState.Registered,
			adapter.snapshotFrozenOwnership().single {
				it.kind == ReaderTransitionResourceKind.CallbackRegistration
			}.state
		)
	}

	@Test
	fun rejectedPhysicalDeckReleaseRetainsExactFrozenOwnershipForRetry() {
		var releases = 0
		val adapter = ReaderDeckPhysicalOwnershipAdapter(
			releasePhysicalDeck = { _, _ ->
				releases += 1
				false
			},
			restorePhysicalDeck = { it }
		)
		val lease = assertNotNull(
			adapter.register(
				ReaderDeckPhysicalRestartDescriptor(
					binding = binding(textureGeneration = 405L),
					role = ReaderDeckSubmissionRole.Active,
					preparationGeneration = 11L,
					rasterGeneration = 31L,
					textureGeneration = 405L
				)
			)
		)
		assertTrue(adapter.acknowledgeRendererOwnership(lease))
		val domain = ReaderLegacyPhysicalDomain(7L, ReaderLegacyFreezeToken(23L))
		assertEquals(ReaderPortCommandResult.Accepted, adapter.freezeForTransitionActivation(domain))
		val deck = adapter.snapshotFrozenOwnership().single {
			it.kind == ReaderTransitionResourceKind.Deck
		}

		assertIs<ReaderPortCommandResult.Rejected>(
			adapter.drainFrozenOwnership(deck.physicalIdentity) {}
		)
		assertEquals(1, releases)
		assertEquals(deck.physicalIdentity, adapter.snapshotFrozenOwnership().single {
			it.kind == ReaderTransitionResourceKind.Deck
		}.physicalIdentity)
		assertEquals(ReaderLegacyResourceState.RendererOwned, adapter.snapshotFrozenOwnership().single {
			it.kind == ReaderTransitionResourceKind.Deck
		}.state)
	}

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
			inactivePorts.issue(ReaderTransitionCommand.ReleaseResource(requireNotNull(key.owningTransitionIdOrNull), key), callbacks::add)
		}
		assertTrue(issued.isEmpty())

		val active = synchronousCutover(ReaderLegacyDeckInventory.Complete(emptyList()))
		assertTrue(active.activate())
		val activePorts = ReaderCutoverTransitionPorts(delegate, active)
		val binding = (requireNotNull(key.owningTransitionIdOrNull).expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		activePorts.issue(
			ReaderTransitionCommand.ReserveDeck(
				transitionId = requireNotNull(key.owningTransitionIdOrNull),
				binding = binding,
				role = ReaderTransitionDeckRole.Initial
			),
			callbacks::add
		)
		activePorts.issue(ReaderTransitionCommand.ReleaseResource(requireNotNull(key.owningTransitionIdOrNull), key), callbacks::add)

		assertEquals(2, issued.size)
		assertEquals(1, callbacks.size)
	}

	@Test
	fun `coordinator deck lease maps role and preserves exact generations and resource provenance`() {
		val id = transitionId()
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding.copy(
			preparationGeneration = 19L,
			rasterGeneration = 23L,
			textureGeneration = 29L
		)
		val key = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, 29L)

		val lease = assertNotNull(
			readerDeckLeaseOrNull(
				ReaderTransitionCommand.ReserveDeck(id, binding, ReaderTransitionDeckRole.Settlement),
				key
			)
		)

		assertEquals(id, lease.transitionId)
		assertEquals(binding, lease.binding)
		assertEquals(ReaderDeckSubmissionRole.Pending, lease.role)
		assertEquals(19L, lease.preparationGeneration)
		assertEquals(23L, lease.rasterGeneration)
		assertEquals(29L, lease.textureGeneration)
		assertEquals(key, lease.resourceKey)
		assertEquals(ReaderTransitionResourceProvenance.CoordinatorIssued, lease.provenance)
	}

	@Test
	fun `deck lease rejects a binding outside the transition exact identity`() {
		val id = transitionId()
		val mismatchedBinding =
			(id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding.copy(
				textureGeneration = 31L
			)
		val key = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, 31L)

		assertNull(
			readerDeckLeaseOrNull(
				ReaderTransitionCommand.ReserveDeck(
					id,
					mismatchedBinding,
					ReaderTransitionDeckRole.PageEntry
				),
				key
			)
		)
	}

	@Test
	fun `task4 allocation raster and deck accept Foliate and semantic successor bindings`() {
		val predecessor = binding(29L).copy(
			preparationGeneration = null,
			rasterGeneration = null,
			textureGeneration = null
		)
		val cases = listOf(
			ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L) to
				predecessor.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity("physical-deck-session", 31L)),
			ReaderExpectedPresentationBinding.SemanticSuccessor(predecessor, 2L) to
				predecessor.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity("physical-deck-session", 37L))
		)
		val rasterLeases = mutableListOf<ReaderRasterPreparationLease>()
		val deckLeases = mutableListOf<ReaderDeckLease>()
		val ports = ReaderTask4TransitionPorts(
			clock = testTransitionClock(),
			raster = recordingRasterPort(
				onPrepare = { lease, _ -> rasterLeases += lease },
				onRelease = { _, _ -> }
			),
			renderer = object : ReaderRendererDeckCommandPort {
				override fun reserve(lease: ReaderDeckLease, callbacks: ReaderDeckLeaseFactEmitter) {
					deckLeases += lease
				}
				override fun release(lease: ReaderDeckLease, onReleased: () -> Unit) = Unit
				override fun cancelPreparation(transitionId: ReaderTransitionId) = Unit
			}
		)

		cases.forEachIndexed { index, (expected, commandBinding) ->
			val sequence = index + 1L
			val id = ReaderTransitionId(
				readerSessionGeneration = 2L,
				coordinatorEpoch = 3L,
				sequence = sequence,
				operation = if (index == 0) {
					ReaderTransitionOperation.BootstrapNativePage
				} else ReaderTransitionOperation.ExternalSemanticRelocation,
				expectedBinding = expected,
				parent = if (sequence == 1L) null else paige.navic.reader.ReaderTransitionParentIdentity(
					2L,
					3L,
					sequence - 1L
				)
			)
			val facts = mutableListOf<ReaderTransitionFact>()
			ports.issue(ReaderTransitionCommand.AllocateMaterialBinding(id, commandBinding), facts::add)
			val allocation = assertIs<ReaderTransitionFact.MaterialBindingAllocated>(facts.single()).allocation
			val allocated = allocation.allocatedBinding
			ports.issue(
				ReaderTransitionCommand.RequestRasterPreparation(id, allocated, allocation),
				facts::add
			)
			ports.issue(
				ReaderTransitionCommand.ReserveDeck(
					id,
					allocated,
					ReaderTransitionDeckRole.Initial,
					allocation
				),
				facts::add
			)
		}

		assertEquals(2, rasterLeases.size)
		assertEquals(2, deckLeases.size)
	}

	@Test
	fun `renderer callbacks require and preserve the exact coordinator lease`() {
		val id = transitionId()
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding.copy(
			preparationGeneration = 19L,
			rasterGeneration = 23L,
			textureGeneration = 29L
		)
		val key = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, 29L)
		val lease = assertNotNull(
			readerDeckLeaseOrNull(
				ReaderTransitionCommand.ReserveDeck(id, binding, ReaderTransitionDeckRole.PageEntry),
				key
			)
		)
		val facts = mutableListOf<ReaderTransitionFact>()
		val callbacks = ReaderDeckLeaseFactEmitter(lease, facts::add)

		callbacks.onPrepared(lease)
		callbacks.onOwned(lease)

		assertEquals(
			listOf(
				ReaderTransitionFact.DeckPrepared(id, key),
				ReaderTransitionFact.DeckOwned(id, key)
			),
			facts
		)
		val mismatchedId = id.copy(sequence = id.sequence + 1L, parent = id.parentIdentity())
		val mismatched = lease.copy(
			transitionId = mismatchedId,
			resourceKey = key.copy(transitionId = mismatchedId)
		)
		assertFailsWith<IllegalStateException> { callbacks.onPrepared(mismatched) }
	}

	@Test
	fun `capacity rejection defers for bounded renderer wake without retrying automatically`() {
		val id = transitionId()
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding.copy(
			preparationGeneration = 19L,
			rasterGeneration = 23L,
			textureGeneration = 29L
		)
		val key = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, 29L)
		val lease = assertNotNull(
			readerDeckLeaseOrNull(
				ReaderTransitionCommand.ReserveDeck(id, binding, ReaderTransitionDeckRole.Recovery),
				key
			)
		)
		val facts = mutableListOf<ReaderTransitionFact>()
		val callbacks = ReaderDeckLeaseFactEmitter(lease, facts::add)
		val resume = ReaderTransitionResumeRecord(
			operation = id.operation,
			reason = ReaderTransitionDeferralReason.RendererCapacityUnavailable,
			nonce = ReaderTransitionNonce(37L, 41L),
			issuedAtMillis = 100L,
			expiresAtMillis = 900_100L,
			remainingRestorations = 1,
			requiredWake = ReaderTransitionWakeKind.RendererCapacityAvailable
		)

		callbacks.onCapacityRejected(lease, resume)
		callbacks.onCapacityAvailable()

		assertEquals(
			listOf(
				ReaderTransitionFact.RasterDeferred(
					id,
					ReaderTransitionDeferralReason.RendererCapacityUnavailable,
					resume
				),
				ReaderTransitionFact.RendererCapacityAvailable(id)
			),
			facts
		)
		assertEquals(1, facts.count { it is ReaderTransitionFact.RasterDeferred })
	}

	@Test
	fun `physical release command confirms the exact lease once through its mailbox callback`() {
		val id = transitionId()
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding.copy(
			preparationGeneration = 19L,
			rasterGeneration = 23L,
			textureGeneration = 29L
		)
		val key = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, 29L)
		val lease = assertNotNull(
			readerDeckLeaseOrNull(
				ReaderTransitionCommand.ReserveDeck(id, binding, ReaderTransitionDeckRole.Initial),
				key
			)
		)
		val physicalReleases = mutableListOf<ReaderDeckLease>()
		val callbacks = mutableListOf<() -> Unit>()
		val port = ReaderDeckPhysicalReleasePort(
			release = { exactLease, confirmed ->
				physicalReleases += exactLease
				callbacks += confirmed
			}
		)
		val facts = mutableListOf<ReaderTransitionFact>()
		port.register(lease)

		port.release(ReaderTransitionCommand.ReleaseResource(id, key), facts::add)
		port.release(ReaderTransitionCommand.ReleaseResource(id, key), facts::add)
		callbacks.single().invoke()
		callbacks.single().invoke()

		assertEquals(listOf(lease), physicalReleases)
		assertEquals(
			listOf<ReaderTransitionFact>(ReaderTransitionFact.ResourceReleased(id, key)),
			facts
		)
	}

	@Test
	fun `stale renderer callback registers and releases through the shared coordinator ledger`() {
		val staleId = transitionId()
		val staleBinding = (staleId.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding.copy(
			preparationGeneration = 19L,
			rasterGeneration = 23L,
			textureGeneration = 29L
		)
		val staleKey = ReaderTransitionResourceKey(staleId, ReaderTransitionResourceKind.Deck, 29L)
		val staleLease = assertNotNull(
			readerDeckLeaseOrNull(
				ReaderTransitionCommand.ReserveDeck(
					staleId,
					staleBinding,
					ReaderTransitionDeckRole.PageEntry
				),
				staleKey,
				ReaderTransitionResourceProvenance.AdoptedLegacy
			)
		)
		val currentId = staleId.copy(sequence = staleId.sequence + 1L, parent = staleId.parentIdentity())
		val physicalReleases = mutableListOf<ReaderDeckLease>()
		val renderer = object : ReaderRendererDeckCommandPort {
			override fun reserve(lease: ReaderDeckLease, callbacks: ReaderDeckLeaseFactEmitter) = Unit
			override fun release(lease: ReaderDeckLease, onReleased: () -> Unit) {
				physicalReleases += lease
				onReleased()
			}
			override fun cancelPreparation(transitionId: ReaderTransitionId) = Unit
		}
		val ports = ReaderTask4TransitionPorts(
			clock = object : ReaderTransitionClock {
				override fun nowMillis(): Long = 1L
				override fun schedule(
					atMillis: Long,
					action: () -> Unit
				): ReaderTransitionClockRegistration = ReaderTransitionClockRegistration {}
			},
			raster = object : ReaderRasterPreparationCommandPort {
				override fun prepare(
					lease: ReaderRasterPreparationLease,
					callbacks: ReaderRasterLeaseFactEmitter
				) = Unit
				override fun release(
					lease: ReaderRasterPreparationLease,
					onReleased: () -> Unit
				) = Unit
				override fun cancel(transitionId: ReaderTransitionId) = Unit
			},
			renderer = renderer
		)
		ports.registerAdoptedLease(staleLease)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(
					currentId.readerSessionGeneration,
					currentId.coordinatorEpoch
				),
				lastTransitionSequence = currentId.sequence,
				lastIssuedTransitionIdentity = currentId.parentIdentity(),
				active = ReaderActiveTransition(
					id = currentId,
					phase = ReaderTransitionLivenessTable.phase(
						currentId,
						ReaderTransitionPhaseKind.AwaitingPrerequisites,
						ReaderPresentationFrameOwner.Neutral
					)
				)
			)
		)

		coordinator.enqueue(ReaderTransitionFact.DeckPrepared(staleId, staleKey))
		coordinator.enqueue(ReaderTransitionFact.DeckPrepared(staleId, staleKey))

		assertEquals(listOf(staleLease), physicalReleases)
		assertEquals(ReaderTransitionResourceState.Released, coordinator.releaseStateOf(staleKey))
		assertEquals(
			3,
			coordinator.snapshot().factClassifications[
				ReaderTransitionFactClassification.StaleTransition
			]
		)
	}

	@Test
	fun `task4 ports reject semantic frame and input commands`() {
		val issuedId = transitionId()
		val ports = ReaderTask4TransitionPorts(
			clock = object : ReaderTransitionClock {
				override fun nowMillis(): Long = 1L
				override fun schedule(
					atMillis: Long,
					action: () -> Unit
				): ReaderTransitionClockRegistration = ReaderTransitionClockRegistration {}
			},
			raster = object : ReaderRasterPreparationCommandPort {
				override fun prepare(
					lease: ReaderRasterPreparationLease,
					callbacks: ReaderRasterLeaseFactEmitter
				) = Unit
				override fun release(
					lease: ReaderRasterPreparationLease,
					onReleased: () -> Unit
				) = Unit
				override fun cancel(transitionId: ReaderTransitionId) = Unit
			},
			renderer = object : ReaderRendererDeckCommandPort {
				override fun reserve(lease: ReaderDeckLease, callbacks: ReaderDeckLeaseFactEmitter) = Unit
				override fun release(lease: ReaderDeckLease, onReleased: () -> Unit) = Unit
				override fun cancelPreparation(transitionId: ReaderTransitionId) = Unit
			}
		)

		assertFailsWith<IllegalStateException> {
			ports.issue(
				ReaderTransitionCommand.RequestSemanticSynchronization(
					issuedId,
					paige.navic.reader.ReaderCoverEntryIntent(
						paige.navic.reader.ReaderSemanticRequestHandle(1L)
					),
					paige.navic.reader.ReaderSemanticRequestHandle(1L)
				)
			) {}
		}
		val coordinator = ReaderResumableTransitionCoordinator(
			ports,
			ReaderTransitionMode.Active,
			readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(1L, 1L),
				lastTransitionSequence = 0L,
				lastIssuedTransitionIdentity = null
			)
		)
		assertFailsWith<IllegalStateException> {
			coordinator.enqueue(
				ReaderTransitionFact.Intent(null, paige.navic.reader.ReaderRetryIntent)
			)
		}
	}

	@Test
	fun `cutover wrapper preserves task4 fact barrier before reduction`() {
		val task4Ports = ReaderTask4TransitionPorts(
			clock = testTransitionClock(),
			raster = noOpRasterPort(),
			renderer = noOpRendererPort()
		)
		val cutover = synchronousCutover(ReaderLegacyDeckInventory.Complete(emptyList()))
		assertTrue(cutover.activate())
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ReaderCutoverTransitionPorts(task4Ports, cutover),
			mode = ReaderTransitionMode.Active,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(1L, 1L),
				lastTransitionSequence = 0L,
				lastIssuedTransitionIdentity = null
			)
		)
		val before = coordinator.snapshot()

		assertFailsWith<IllegalStateException> {
			coordinator.enqueue(
				ReaderTransitionFact.Intent(null, paige.navic.reader.ReaderRetryIntent)
			)
		}

		assertEquals(before, coordinator.snapshot())
	}

	@Test
	fun `superseded raster resource releases exactly once before late callbacks`() {
		val staleId = transitionId()
		val binding = (staleId.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		val currentId = staleId.copy(sequence = staleId.sequence + 1L, parent = staleId.parentIdentity())
		lateinit var callbacks: ReaderRasterLeaseFactEmitter
		lateinit var submittedLease: ReaderRasterPreparationLease
		val physicalReleases = mutableListOf<ReaderRasterPreparationLease>()
		val raster = recordingRasterPort(
			onPrepare = { lease, emitter ->
				submittedLease = lease
				callbacks = emitter
			},
			onRelease = { lease, released ->
				physicalReleases += lease
				released()
			}
		)
		val ports = ReaderTask4TransitionPorts(
			clock = testTransitionClock(),
			raster = raster,
			renderer = noOpRendererPort()
		)
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(
					currentId.readerSessionGeneration,
					currentId.coordinatorEpoch
				),
				lastTransitionSequence = currentId.sequence,
				lastIssuedTransitionIdentity = currentId.parentIdentity(),
				active = ReaderActiveTransition(
					id = currentId,
					phase = ReaderTransitionLivenessTable.phase(
						currentId,
						ReaderTransitionPhaseKind.AwaitingPrerequisites,
						ReaderPresentationFrameOwner.Neutral
					)
				)
			)
		)

		ports.issue(
			ReaderTransitionCommand.RequestRasterPreparation(staleId, binding),
			coordinator::enqueue
		)
		val resume = ReaderTransitionResumeRecord(
			operation = staleId.operation,
			reason = ReaderTransitionDeferralReason.RendererCapacityUnavailable,
			nonce = ReaderTransitionNonce(61L, 67L),
			issuedAtMillis = 100L,
			expiresAtMillis = 900_100L,
			remainingRestorations = 1,
			requiredWake = ReaderTransitionWakeKind.RendererCapacityAvailable
		)
		callbacks.onProgress(submittedLease)
		callbacks.onProven(submittedLease)
		callbacks.onDeferred(submittedLease, resume)
		callbacks.onFailed(submittedLease, paige.navic.reader.ReaderTransitionFailureReason.PortRejected)
		callbacks.onProgress(submittedLease)

		assertEquals(listOf(submittedLease), physicalReleases)
		assertEquals(
			ReaderTransitionResourceState.Released,
			coordinator.releaseStateOf(submittedLease.resourceKey)
		)
		assertEquals(1, coordinator.snapshot().releasedResourceCount)
	}

	@Test
	fun `raster registration precedes callback facts and terminal release stays exact once`() {
		val id = transitionId()
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		lateinit var callbacks: ReaderRasterLeaseFactEmitter
		lateinit var submittedLease: ReaderRasterPreparationLease
		val physicalReleases = mutableListOf<ReaderRasterPreparationLease>()
		val raster = recordingRasterPort(
			onPrepare = { lease, emitter ->
				submittedLease = lease
				callbacks = emitter
			},
			onRelease = { lease, released ->
				physicalReleases += lease
				released()
			}
		)
		val ports = ReaderTask4TransitionPorts(
			clock = testTransitionClock(),
			raster = raster,
			renderer = noOpRendererPort()
		)
		val processedFacts = mutableListOf<ReaderTransitionFactKind>()
		val coordinator = ReaderResumableTransitionCoordinator(
			ports = ports,
			mode = ReaderTransitionMode.Active,
			journal = readerAndroidHostTestJournal(
				committed = readerAndroidHostTestNeutralInitial(
					id.readerSessionGeneration,
					id.coordinatorEpoch
				),
				lastTransitionSequence = id.sequence,
				lastIssuedTransitionIdentity = id.parentIdentity(),
				active = ReaderActiveTransition(
					id = id,
					phase = ReaderTransitionLivenessTable.phase(
						id,
						ReaderTransitionPhaseKind.AwaitingPrerequisites,
						ReaderPresentationFrameOwner.Neutral
					)
				)
			),
			onObservation = { observation ->
				if (observation.kind == ReaderTransitionCoordinatorObservationKind.FactProcessed) {
					observation.factKind?.let(processedFacts::add)
				}
			}
		)

		ports.issue(
			ReaderTransitionCommand.RequestRasterPreparation(id, binding),
			coordinator::enqueue
		)
		callbacks.onProgress(submittedLease)
		callbacks.onFailed(
			submittedLease,
			paige.navic.reader.ReaderTransitionFailureReason.PortRejected
		)
		callbacks.onFailed(
			submittedLease,
			paige.navic.reader.ReaderTransitionFailureReason.PortRejected
		)

		assertEquals(
			listOf(ReaderTransitionFactKind.ResourceObserved, ReaderTransitionFactKind.RasterProgress),
			processedFacts.take(2)
		)
		assertEquals(listOf(submittedLease), physicalReleases)
		assertEquals(
			ReaderTransitionResourceState.Released,
			coordinator.releaseStateOf(submittedLease.resourceKey)
		)
	}

	@Test
	fun `terminal release bookkeeping stays bounded and evicted identities fail closed`() {
		val releaseCount = 256
		val ledger = ReaderTransitionReleaseLedger()
		var deckPhysicalReleaseCount = 0
		var rasterPhysicalReleaseCount = 0
		val deckPort = ReaderDeckPhysicalReleasePort { _, released ->
			deckPhysicalReleaseCount += 1
			released()
		}
		val rasterPort = ReaderRasterPhysicalReleasePort { _, released ->
			rasterPhysicalReleaseCount += 1
			released()
		}
		lateinit var oldestDeckLease: ReaderDeckLease
		lateinit var oldestDeckCommand: ReaderTransitionCommand.ReleaseResource
		lateinit var newestDeckLease: ReaderDeckLease
		lateinit var newestDeckCommand: ReaderTransitionCommand.ReleaseResource

		repeat(releaseCount) { index ->
			val sequence = index.toLong() + 1L
			val binding = ReaderPresentationBinding(
				foliateSessionId = "stress-fixture",
				publicationGeneration = 1L,
				viewportGeneration = 2L,
				profileGeneration = 3L,
				preparationGeneration = sequence * 3L,
				rasterGeneration = sequence * 3L + 1L,
				textureGeneration = sequence * 3L + 2L
			)
			val id = ReaderTransitionId(
				readerSessionGeneration = 5L,
				coordinatorEpoch = 7L,
				sequence = sequence,
				operation = ReaderTransitionOperation.CoverToPageEntry,
				expectedBinding = ReaderExpectedPresentationBinding.Exact(binding),
				parent = if (sequence == 1L) null else paige.navic.reader.ReaderTransitionParentIdentity(
					5L,
					7L,
					sequence - 1L
				)
			)
			val deckKey = ReaderTransitionResourceKey(
				id,
				ReaderTransitionResourceKind.Deck,
				requireNotNull(binding.textureGeneration)
			)
			val deckLease = assertNotNull(
				readerDeckLeaseOrNull(
					ReaderTransitionCommand.ReserveDeck(
						id,
						binding,
						ReaderTransitionDeckRole.PageEntry
					),
					deckKey
				)
			)
			val rasterLease = ReaderRasterPreparationLease(
				transitionId = id,
				binding = binding,
				preparationGeneration = requireNotNull(binding.preparationGeneration),
				rasterGeneration = requireNotNull(binding.rasterGeneration),
				resourceKey = ReaderTransitionResourceKey(
					id,
					ReaderTransitionResourceKind.Raster,
					requireNotNull(binding.rasterGeneration)
				)
			)

			assertTrue(deckPort.register(deckLease))
			assertTrue(ledger.register(deckLease.resourceKey))
			val deckCommand = assertNotNull(ledger.requestRelease(deckLease.resourceKey))
			assertTrue(deckPort.release(deckCommand) { fact ->
				assertEquals(
					ReaderTransitionFact.ResourceReleased(id, deckLease.resourceKey),
					fact
				)
				assertTrue(ledger.confirmReleased(deckLease.resourceKey))
			})

			assertTrue(rasterPort.register(rasterLease))
			assertTrue(ledger.register(rasterLease.resourceKey))
			val rasterCommand = assertNotNull(ledger.requestRelease(rasterLease.resourceKey))
			assertTrue(rasterPort.release(rasterCommand) { fact ->
				assertEquals(
					ReaderTransitionFact.ResourceReleased(id, rasterLease.resourceKey),
					fact
				)
				assertTrue(ledger.confirmReleased(rasterLease.resourceKey))
			})

			if (index == 0) {
				oldestDeckLease = deckLease
				oldestDeckCommand = deckCommand
			}
			if (index == releaseCount - 1) {
				newestDeckLease = deckLease
				newestDeckCommand = deckCommand
			}
		}

		val ledgerRetention = ledger.retentionSnapshot()
		val deckRetention = deckPort.retentionSnapshot()
		val rasterRetention = rasterPort.retentionSnapshot()
		assertEquals(0, ledgerRetention.activeStateCount)
		assertEquals(0, ledgerRetention.earlyConfirmationCount)
		assertTrue(ledgerRetention.terminalTombstoneCount <= ledgerRetention.terminalTombstoneCapacity)
		assertEquals(ReaderTransitionRetirementFenceState.Active, ledgerRetention.retirementFenceState)
		listOf(deckRetention, rasterRetention).forEach { retention ->
			assertEquals(0, retention.activeLeaseCount)
			assertEquals(0, retention.issuedCount)
			assertEquals(0, retention.confirmingCount)
			assertTrue(retention.terminalTombstoneCount <= retention.terminalTombstoneCapacity)
			assertEquals(ReaderTransitionRetirementFenceState.Active, retention.retirementFenceState)
		}
		assertEquals(releaseCount, deckPhysicalReleaseCount)
		assertEquals(releaseCount, rasterPhysicalReleaseCount)

		listOf(
			oldestDeckLease to oldestDeckCommand,
			newestDeckLease to newestDeckCommand
		).forEach { (lease, command) ->
			assertFalse(ledger.register(lease.resourceKey))
			assertNull(ledger.requestRelease(lease.resourceKey))
			assertFalse(deckPort.register(lease))
			assertFalse(deckPort.release(command) { error("Duplicate release confirmation") })
		}
		assertEquals(releaseCount, deckPhysicalReleaseCount)
	}

	@Test
	fun `active deck lease releases after tombstone eviction and lifecycle advance`() {
		val ledger = ReaderTransitionReleaseLedger()
		val physicalReleases = mutableListOf<ReaderDeckLease>()
		val port = ReaderDeckPhysicalReleasePort { lease, confirmed ->
			physicalReleases += lease
			confirmed()
		}
		val oldId = transitionId().copy(sequence = 1L, parent = null)
		val oldLease = deckLeaseFor(oldId)
		assertTrue(port.register(oldLease))
		assertTrue(ledger.register(oldLease.resourceKey))
		val oldCommand = assertNotNull(ledger.requestRelease(oldLease.resourceKey))

		repeat(33) { index ->
			releaseDeck(port, ledger, deckLeaseFor(oldId.copy(
				sequence = index.toLong() + 2L,
				parent = paige.navic.reader.ReaderTransitionParentIdentity(
					oldId.readerSessionGeneration,
					oldId.coordinatorEpoch,
					index.toLong() + 1L
				)
			)))
		}
		releaseDeck(
			port,
			ledger,
			deckLeaseFor(oldId.copy(readerSessionGeneration = oldId.readerSessionGeneration + 1L))
		)

		assertTrue(port.release(oldCommand) { assertTrue(ledger.confirmReleased(oldLease.resourceKey)) })
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(oldLease.resourceKey))
		assertEquals(35, physicalReleases.size)
		assertEquals(1, port.retentionSnapshot().terminalTombstoneCount)
		assertEquals(1, ledger.retentionSnapshot().terminalTombstoneCount)
	}

	@Test
	fun `active raster lease releases after tombstone eviction and lifecycle advance`() {
		val ledger = ReaderTransitionReleaseLedger()
		val physicalReleases = mutableListOf<ReaderRasterPreparationLease>()
		val port = ReaderRasterPhysicalReleasePort { lease, confirmed ->
			physicalReleases += lease
			confirmed()
		}
		val oldId = transitionId().copy(sequence = 1L, parent = null)
		val oldLease = rasterLeaseFor(oldId)
		assertTrue(port.register(oldLease))
		assertTrue(ledger.register(oldLease.resourceKey))
		val oldCommand = assertNotNull(ledger.requestRelease(oldLease.resourceKey))

		repeat(33) { index ->
			releaseRaster(port, ledger, rasterLeaseFor(oldId.copy(
				sequence = index.toLong() + 2L,
				parent = paige.navic.reader.ReaderTransitionParentIdentity(
					oldId.readerSessionGeneration,
					oldId.coordinatorEpoch,
					index.toLong() + 1L
				)
			)))
		}
		releaseRaster(
			port,
			ledger,
			rasterLeaseFor(oldId.copy(readerSessionGeneration = oldId.readerSessionGeneration + 1L))
		)

		assertTrue(port.release(oldCommand) { assertTrue(ledger.confirmReleased(oldLease.resourceKey)) })
		assertEquals(ReaderTransitionResourceState.Released, ledger.stateOf(oldLease.resourceKey))
		assertEquals(35, physicalReleases.size)
		assertEquals(1, port.retentionSnapshot().terminalTombstoneCount)
		assertEquals(1, ledger.retentionSnapshot().terminalTombstoneCount)
	}

	@Test
	fun `duplicate and terminal deck reservations do not reserve twice`() {
		var reserveCount = 0
		val ports = ReaderTask4TransitionPorts(
			clock = testTransitionClock(),
			raster = noOpRasterPort(),
			renderer = object : ReaderRendererDeckCommandPort {
				override fun reserve(lease: ReaderDeckLease, callbacks: ReaderDeckLeaseFactEmitter) {
					reserveCount += 1
				}
				override fun release(lease: ReaderDeckLease, onReleased: () -> Unit) = onReleased()
				override fun cancelPreparation(transitionId: ReaderTransitionId) = Unit
			}
		)
		val id = transitionId()
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		val command = ReaderTransitionCommand.ReserveDeck(id, binding, ReaderTransitionDeckRole.PageEntry)
		val key = ReaderTransitionResourceKey(
			id,
			ReaderTransitionResourceKind.Deck,
			requireNotNull(binding.textureGeneration)
		)
		val facts = mutableListOf<ReaderTransitionFact>()

		ports.issue(command, facts::add)
		ports.issue(command, facts::add)
		assertEquals(1, reserveCount)
		val factsAfterDuplicate = facts.toList()
		ports.issue(ReaderTransitionCommand.ReleaseResource(id, key), facts::add)
		val factsAfterRelease = facts.toList()
		ports.issue(command, facts::add)

		assertEquals(1, reserveCount)
		assertEquals(1, factsAfterDuplicate.size)
		assertEquals(factsAfterRelease, facts)
	}

	@Test
	fun `duplicate and terminal raster preparations do not prepare twice`() {
		var prepareCount = 0
		val ports = ReaderTask4TransitionPorts(
			clock = testTransitionClock(),
			raster = object : ReaderRasterPreparationCommandPort {
				override fun prepare(
				lease: ReaderRasterPreparationLease,
				callbacks: ReaderRasterLeaseFactEmitter
			) {
					prepareCount += 1
				}
				override fun release(lease: ReaderRasterPreparationLease, onReleased: () -> Unit) =
				onReleased()
				override fun cancel(transitionId: ReaderTransitionId) = Unit
			},
			renderer = noOpRendererPort()
		)
		val id = transitionId()
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		val command = ReaderTransitionCommand.RequestRasterPreparation(id, binding)
		val key = ReaderTransitionResourceKey(
			id,
			ReaderTransitionResourceKind.Raster,
			requireNotNull(binding.rasterGeneration)
		)
		val facts = mutableListOf<ReaderTransitionFact>()

		ports.issue(command, facts::add)
		ports.issue(command, facts::add)
		assertEquals(1, prepareCount)
		val factsAfterDuplicate = facts.toList()
		ports.issue(ReaderTransitionCommand.ReleaseResource(id, key), facts::add)
		val factsAfterRelease = facts.toList()
		ports.issue(command, facts::add)

		assertEquals(1, prepareCount)
		assertEquals(1, factsAfterDuplicate.size)
		assertEquals(factsAfterRelease, facts)
	}

	@Test
	fun `production policy exposes coordinator activation only through a completed cutover`() {
		val legacy = UnavailableReaderDeckAdmissionLeaseHost
		val cutover = synchronousCutover(ReaderLegacyDeckInventory.Complete(emptyList()))

		val selected = ReaderDeckAdmissionProductionPolicy.select(
			legacy,
			cutover,
			ReaderDeckAdmissionActivationPrerequisites.Complete
		)

		assertTrue(cutover.coordinatorAdmissionOpen)
		assertEquals(ReaderDeckAdmissionProductionMode.Coordinator, ReaderDeckAdmissionProductionPolicy.mode(selected))
		assertSame(cutover, selected)
	}

	@Test
	fun `incomplete activation prerequisite remains legacy only`() {
		val legacy = UnavailableReaderDeckAdmissionLeaseHost
		val cutover = deckCutover(ReaderLegacyDeckInventory.Incomplete)

		val selected = ReaderDeckAdmissionProductionPolicy.select(
			legacy,
			cutover,
			ReaderDeckAdmissionActivationPrerequisites(
				exactInventoryAvailable = false,
				callbacksCarryExactLease = true,
				closeDrainConnected = true
			)
		)

		assertSame(legacy, selected)
		assertTrue(cutover.legacyAdmissionOpen)
		assertFalse(cutover.coordinatorAdmissionOpen)
		assertEquals(ReaderDeckAdmissionProductionMode.LegacyOnly, ReaderDeckAdmissionProductionPolicy.mode(selected))
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

	private fun deckLeaseFor(id: ReaderTransitionId): ReaderDeckLease {
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		return assertNotNull(
			readerDeckLeaseOrNull(
				ReaderTransitionCommand.ReserveDeck(id, binding, ReaderTransitionDeckRole.PageEntry),
				ReaderTransitionResourceKey(
					id,
					ReaderTransitionResourceKind.Deck,
					requireNotNull(binding.textureGeneration)
				)
			)
		)
	}

	private fun rasterLeaseFor(id: ReaderTransitionId): ReaderRasterPreparationLease {
		val binding = (id.expectedBinding as ReaderExpectedPresentationBinding.Exact).binding
		return ReaderRasterPreparationLease(
			transitionId = id,
			binding = binding,
			preparationGeneration = requireNotNull(binding.preparationGeneration),
			rasterGeneration = requireNotNull(binding.rasterGeneration),
			resourceKey = ReaderTransitionResourceKey(
				id,
				ReaderTransitionResourceKind.Raster,
				requireNotNull(binding.rasterGeneration)
			)
		)
	}

	private fun releaseDeck(
		port: ReaderDeckPhysicalReleasePort,
		ledger: ReaderTransitionReleaseLedger,
		lease: ReaderDeckLease
	) {
		assertTrue(port.register(lease))
		assertTrue(ledger.register(lease.resourceKey))
		val command = assertNotNull(ledger.requestRelease(lease.resourceKey))
		assertTrue(port.release(command) { assertTrue(ledger.confirmReleased(lease.resourceKey)) })
	}

	private fun releaseRaster(
		port: ReaderRasterPhysicalReleasePort,
		ledger: ReaderTransitionReleaseLedger,
		lease: ReaderRasterPreparationLease
	) {
		assertTrue(port.register(lease))
		assertTrue(ledger.register(lease.resourceKey))
		val command = assertNotNull(ledger.requestRelease(lease.resourceKey))
		assertTrue(port.release(command) { assertTrue(ledger.confirmReleased(lease.resourceKey)) })
	}

	private fun testTransitionClock(): ReaderTransitionClock = object : ReaderTransitionClock {
		override fun nowMillis(): Long = 1L
		override fun schedule(
			atMillis: Long,
			action: () -> Unit
		): ReaderTransitionClockRegistration = ReaderTransitionClockRegistration {}
	}

	private fun noOpRasterPort(): ReaderRasterPreparationCommandPort =
		object : ReaderRasterPreparationCommandPort {
			override fun prepare(
				lease: ReaderRasterPreparationLease,
				callbacks: ReaderRasterLeaseFactEmitter
			) = Unit

			override fun release(
				lease: ReaderRasterPreparationLease,
				onReleased: () -> Unit
			) = Unit
			override fun cancel(transitionId: ReaderTransitionId) = Unit
		}

	private fun noOpRendererPort(): ReaderRendererDeckCommandPort =
		object : ReaderRendererDeckCommandPort {
			override fun reserve(
				lease: ReaderDeckLease,
				callbacks: ReaderDeckLeaseFactEmitter
			) = Unit

			override fun release(lease: ReaderDeckLease, onReleased: () -> Unit) = Unit
			override fun cancelPreparation(transitionId: ReaderTransitionId) = Unit
		}

	private fun recordingRasterPort(
		onPrepare: (ReaderRasterPreparationLease, ReaderRasterLeaseFactEmitter) -> Unit,
		onRelease: (ReaderRasterPreparationLease, () -> Unit) -> Unit
	): ReaderRasterPreparationCommandPort = object : ReaderRasterPreparationCommandPort {
		override fun prepare(
			lease: ReaderRasterPreparationLease,
			callbacks: ReaderRasterLeaseFactEmitter
		) = onPrepare(lease, callbacks)

		override fun release(
			lease: ReaderRasterPreparationLease,
			onReleased: () -> Unit
		) = onRelease(lease, onReleased)

		override fun cancel(transitionId: ReaderTransitionId) = Unit
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

	private fun binding(textureGeneration: Long) = ReaderPresentationBinding(
		foliateSessionId = "physical-deck-session",
		publicationGeneration = 3L,
		viewportGeneration = 5L,
		profileGeneration = 7L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity(
			"physical-deck-session",
			9L
		),
		preparationGeneration = 11L,
		rasterGeneration = 31L,
		textureGeneration = textureGeneration
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
				destinationCommitIdentity = ReaderDestinationCommitIdentity("synthetic", 17L),
				preparationGeneration = 19L,
				rasterGeneration = 23L,
				textureGeneration = 29L
			)
		),
		parent = paige.navic.reader.ReaderTransitionParentIdentity(2L, 3L, 4L)
	)
}
