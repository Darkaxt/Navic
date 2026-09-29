package paige.navic.ui.screens.reader

import java.io.File
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderCurlPresentationFrame
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderInitialCommittedPresentationOrigin
import paige.navic.reader.ReaderInitialOriginKind
import paige.navic.reader.ReaderInitialOriginOwnerKind
import paige.navic.reader.ReaderInitialPresentationInputLease
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderShellCoverCommitProof
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceProvenance
import paige.navic.reader.ReaderAdoptedPredecessorSeedId
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionOperation

class ReaderTransitionActivationTest {
	@Test
	fun retainedFactOnlyTimerOwnsExactDeadlineRegistrationAcrossDrainAndRestore() {
		var now = 100L
		val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(83L)),
			nowMillis = { now },
			schedule = { atMillis, action ->
				scheduled += atMillis to action
				ReaderTransitionClockRegistration { }
			}
		)
		val binding = ReaderPresentationBinding(
			"fixture", 2L, 3L, 5L,
			ReaderDestinationCommitIdentity("fixture", 1L),
			7L, 11L, 13L
		)
		val id = ReaderTransitionId(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			sequence = 1L,
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderTransitionFact.DeadlineExpired>()
		val registration = requireNotNull(timer.bindBeforeWork(id, expired::add))

		assertEquals(1, scheduled.size)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			timer.freezeForTransitionActivation(registration.physicalIdentity.domain)
		)
		val row = timer.snapshotFrozenOwnership().single()
		assertTrue(
			registration.physicalIdentity == row.physicalIdentity,
			"Timer inventory must retain the exact physical identity"
		)
		assertEquals(ReaderLegacyInventorySource.DeadlineRegistration, row.physicalIdentity.source)
		assertEquals(ReaderTransitionResourceKind.CallbackRegistration, row.kind)
		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected),
			timer.matchingProgress(registration, now)
		)
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			timer.drainFrozenOwnership(row.physicalIdentity, confirmed::add)
		)
		assertEquals(1, confirmed.size)
		assertTrue(
			confirmed.single() == row.physicalIdentity,
			"Timer drain must confirm only the exact physical identity"
		)
		assertTrue(timer.snapshotFrozenOwnership().isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			timer.restoreAfterTransitionActivation(registration.physicalIdentity.domain)
		)
		assertEquals(2, scheduled.size)
		now = registration.hardExpiresAtMillis
		scheduled.last().second()
		assertTrue(
			expired == listOf(paige.navic.reader.ReaderTransitionFact.DeadlineExpired(id)),
			"Timer expiry must emit only its bound transition category"
		)
	}

	@Test
	fun progressRearmRejectionExpiresFailClosedWithoutStaleOrDuplicateTimer() {
		var scheduleCount = 0
		var cancellationCount = 0
		val scheduledActions = mutableListOf<() -> Unit>()
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(89L)),
			nowMillis = { 100L },
			schedule = { _, action ->
				scheduleCount += 1
				scheduledActions += action
				if (scheduleCount == 1) {
					ReaderTransitionClockRegistration { cancellationCount += 1 }
				} else null
			}
		)
		val binding = ReaderPresentationBinding(
			"fixture", 2L, 3L, 5L,
			ReaderDestinationCommitIdentity("fixture", 1L),
			7L, 11L, 13L
		)
		val id = ReaderTransitionId(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			sequence = 1L,
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderTransitionFact.DeadlineExpired>()
		val registration = requireNotNull(timer.bindBeforeWork(id, expired::add))

		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected),
			timer.matchingProgress(registration, 200L)
		)
		assertEquals(listOf(paige.navic.reader.ReaderTransitionFact.DeadlineExpired(id)), expired)
		assertEquals(1, cancellationCount)
		assertEquals(2, scheduleCount)
		assertNull(timer.snapshotForTask7Transfer(registration))
		scheduledActions.forEach { it() }
		assertEquals(1, expired.size)
	}

	@Test
	fun retainedFactOnlyTimerKeepsCallbackAuthorityWhenPhysicalCancelThrows() {
		lateinit var scheduledExpiry: () -> Unit
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(97L)),
			nowMillis = { 100L },
			schedule = { _, action ->
				scheduledExpiry = action
				ReaderTransitionClockRegistration { error("private cancel detail") }
			}
		)
		val binding = ReaderPresentationBinding(
			"fixture", 2L, 3L, 5L,
			ReaderDestinationCommitIdentity("fixture", 1L),
			7L, 11L, 13L
		)
		val id = ReaderTransitionId(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			sequence = 1L,
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderTransitionFact.DeadlineExpired>()
		val registration = requireNotNull(timer.bindBeforeWork(id, expired::add))

		assertFailsWith<IllegalStateException> { timer.cancel(registration) }
		assertNotNull(
			timer.snapshotForTask7Transfer(registration),
			"A failed physical cancel must retain the exact callback registration"
		)

		scheduledExpiry()

		assertEquals(
			listOf(paige.navic.reader.ReaderTransitionFact.DeadlineExpired(id)),
			expired
		)
		assertNull(timer.snapshotForTask7Transfer(registration))
		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource),
			timer.cancel(registration)
		)
	}

	@Test
	fun retainedFactOnlyTimerAcceptedCancelSuppressesStaleExpiry() {
		lateinit var scheduledExpiry: () -> Unit
		var physicalCancelCount = 0
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(99L)),
			nowMillis = { 100L },
			schedule = { _, action ->
				scheduledExpiry = action
				ReaderTransitionClockRegistration { physicalCancelCount += 1 }
			}
		)
		val binding = ReaderPresentationBinding(
			"fixture", 2L, 3L, 5L,
			ReaderDestinationCommitIdentity("fixture", 1L),
			7L, 11L, 13L
		)
		val id = ReaderTransitionId(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			sequence = 1L,
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderTransitionFact.DeadlineExpired>()
		val registration = requireNotNull(timer.bindBeforeWork(id, expired::add))

		assertEquals(ReaderPortCommandResult.Accepted, timer.cancel(registration))
		scheduledExpiry()

		assertEquals(1, physicalCancelCount)
		assertTrue(expired.isEmpty())
		assertNull(timer.snapshotForTask7Transfer(registration))
		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource),
			timer.cancel(registration)
		)
	}

	@Test
	fun retainedFactOnlyTimerExpiryDuringPhysicalCancelPublishesExactlyOnce() {
		lateinit var scheduledExpiry: () -> Unit
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(100L)),
			nowMillis = { 100L },
			schedule = { _, action ->
				scheduledExpiry = action
				ReaderTransitionClockRegistration { scheduledExpiry() }
			}
		)
		val binding = ReaderPresentationBinding(
			"fixture", 2L, 3L, 5L,
			ReaderDestinationCommitIdentity("fixture", 1L),
			7L, 11L, 13L
		)
		val id = ReaderTransitionId(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			sequence = 1L,
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderTransitionFact.DeadlineExpired>()
		val registration = requireNotNull(timer.bindBeforeWork(id, expired::add))

		assertEquals(ReaderPortCommandResult.Accepted, timer.cancel(registration))
		scheduledExpiry()

		assertEquals(listOf(paige.navic.reader.ReaderTransitionFact.DeadlineExpired(id)), expired)
		assertNull(timer.snapshotForTask7Transfer(registration))
		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource),
			timer.cancel(registration)
		)
	}

	@Test
	fun retainedFactOnlyTimerOwnsExactTwoSecondReleaseOnlyCloseBudget() {
		var now = 400L
		var scheduledAt = -1L
		lateinit var scheduledExpiry: () -> Unit
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(101L)),
			nowMillis = { now },
			schedule = { atMillis, action ->
				scheduledAt = atMillis
				scheduledExpiry = action
				ReaderTransitionClockRegistration { }
			}
		)
		val cleanupKey = paige.navic.reader.ReaderReleaseOnlyCleanupKey(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			cleanupId = paige.navic.reader.ReaderReleaseOnlyCleanupId(1L),
			generation = paige.navic.reader.ReaderReleaseOnlyCleanupGeneration(1L)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderReleaseOnlyCleanupKey>()

		val registration = requireNotNull(
			timer.bindReleaseOnlyCloseBudget(cleanupKey, expired::add)
		)

		assertEquals(2_400L, registration.expiresAtMillis)
		assertEquals(registration.expiresAtMillis, scheduledAt)
		now = registration.expiresAtMillis
		scheduledExpiry()
		scheduledExpiry()
		assertEquals(listOf(cleanupKey), expired)
		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource),
			timer.cancelReleaseOnlyCloseBudget(registration)
		)
	}

	@Test
	fun retainedReleaseOnlyTimerAcceptedCancelWinsAgainstStaleExpiry() {
		lateinit var scheduledExpiry: () -> Unit
		var physicalCancelCount = 0
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(103L)),
			nowMillis = { 100L },
			schedule = { _, action ->
				scheduledExpiry = action
				ReaderTransitionClockRegistration { physicalCancelCount += 1 }
			}
		)
		val cleanupKey = paige.navic.reader.ReaderReleaseOnlyCleanupKey(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			cleanupId = paige.navic.reader.ReaderReleaseOnlyCleanupId(2L),
			generation = paige.navic.reader.ReaderReleaseOnlyCleanupGeneration(1L)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderReleaseOnlyCleanupKey>()
		val registration = requireNotNull(
			timer.bindReleaseOnlyCloseBudget(cleanupKey, expired::add)
		)

		assertEquals(
			ReaderPortCommandResult.Accepted,
			timer.cancelReleaseOnlyCloseBudget(registration)
		)
		scheduledExpiry()

		assertEquals(1, physicalCancelCount)
		assertTrue(expired.isEmpty())
		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource),
			timer.cancelReleaseOnlyCloseBudget(registration)
		)
	}

	@Test
	fun retainedReleaseOnlyTimerKeepsExpiryAuthorityWhenPhysicalCancelThrows() {
		lateinit var scheduledExpiry: () -> Unit
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(107L)),
			nowMillis = { 100L },
			schedule = { _, action ->
				scheduledExpiry = action
				ReaderTransitionClockRegistration { error("private cancel detail") }
			}
		)
		val cleanupKey = paige.navic.reader.ReaderReleaseOnlyCleanupKey(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			cleanupId = paige.navic.reader.ReaderReleaseOnlyCleanupId(3L),
			generation = paige.navic.reader.ReaderReleaseOnlyCleanupGeneration(1L)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderReleaseOnlyCleanupKey>()
		val registration = requireNotNull(
			timer.bindReleaseOnlyCloseBudget(cleanupKey, expired::add)
		)

		assertFailsWith<IllegalStateException> {
			timer.cancelReleaseOnlyCloseBudget(registration)
		}
		scheduledExpiry()
		scheduledExpiry()

		assertEquals(listOf(cleanupKey), expired)
		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource),
			timer.cancelReleaseOnlyCloseBudget(registration)
		)
	}

	@Test
	fun retainedReleaseOnlyTimerExpiryWinsRaceBeforeCancelWithoutDuplicateCallback() {
		lateinit var scheduledExpiry: () -> Unit
		var physicalCancelCount = 0
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(109L)),
			nowMillis = { 100L },
			schedule = { _, action ->
				scheduledExpiry = action
				ReaderTransitionClockRegistration { physicalCancelCount += 1 }
			}
		)
		val cleanupKey = paige.navic.reader.ReaderReleaseOnlyCleanupKey(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			cleanupId = paige.navic.reader.ReaderReleaseOnlyCleanupId(4L),
			generation = paige.navic.reader.ReaderReleaseOnlyCleanupGeneration(1L)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderReleaseOnlyCleanupKey>()
		val registration = requireNotNull(
			timer.bindReleaseOnlyCloseBudget(cleanupKey, expired::add)
		)

		scheduledExpiry()
		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource),
			timer.cancelReleaseOnlyCloseBudget(registration)
		)
		scheduledExpiry()

		assertEquals(listOf(cleanupKey), expired)
		assertEquals(0, physicalCancelCount)
	}

	@Test
	fun retainedReleaseOnlyTimerRollsBackProvisionalEntryWhenScheduleThrows() {
		var scheduleAttempts = 0
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(113L)),
			nowMillis = { 100L },
			schedule = { _, _ ->
				scheduleAttempts += 1
				if (scheduleAttempts == 1) error("private schedule detail")
				ReaderTransitionClockRegistration {}
			}
		)
		val cleanupKey = paige.navic.reader.ReaderReleaseOnlyCleanupKey(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			cleanupId = paige.navic.reader.ReaderReleaseOnlyCleanupId(5L),
			generation = paige.navic.reader.ReaderReleaseOnlyCleanupGeneration(1L)
		)

		assertFailsWith<IllegalStateException> {
			timer.bindReleaseOnlyCloseBudget(cleanupKey) {}
		}
		val rebound = assertNotNull(timer.bindReleaseOnlyCloseBudget(cleanupKey) {})

		assertEquals(2, scheduleAttempts)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			timer.cancelReleaseOnlyCloseBudget(rebound)
		)
	}

	@Test
	fun retainedReleaseOnlyTimerReportsSynchronousExpiryAsCancelWinner() {
		lateinit var scheduledExpiry: () -> Unit
		val timer = ReaderRetainedFactOnlyTimer(
			domain = ReaderLegacyPhysicalDomain(17L, ReaderLegacyFreezeToken(127L)),
			nowMillis = { 100L },
			schedule = { _, action ->
				scheduledExpiry = action
				ReaderTransitionClockRegistration { scheduledExpiry() }
			}
		)
		val cleanupKey = paige.navic.reader.ReaderReleaseOnlyCleanupKey(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			cleanupId = paige.navic.reader.ReaderReleaseOnlyCleanupId(6L),
			generation = paige.navic.reader.ReaderReleaseOnlyCleanupGeneration(1L)
		)
		val expired = mutableListOf<paige.navic.reader.ReaderReleaseOnlyCleanupKey>()
		val registration = assertNotNull(
			timer.bindReleaseOnlyCloseBudget(cleanupKey, expired::add)
		)

		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected),
			timer.cancelReleaseOnlyCloseBudget(registration)
		)
		scheduledExpiry()

		assertEquals(listOf(cleanupKey), expired)
		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource),
			timer.cancelReleaseOnlyCloseBudget(registration)
		)
	}

	@Test
	fun activatedInstallationIsOneSnapshotWriteAndMissingPortRejectsBeforeWrite() {
		val store = ReaderActivatedSessionSnapshotStore()
		val barrier = ReaderActivatedSessionInstallationBarrier(store)
		val decision = neutralDecision()

		val rejected = barrier.installTestActivatedSession(
			completeActivatedSessionPorts().without(ReaderActivatedPort.Frame),
			decision
		)
		assertEquals(
			ReaderActivationInstallResult.Rejected(
				ReaderTransitionFailureReason.ActivationPrerequisiteMissing
			),
			rejected
		)
		assertEquals(0, store.atomicWriteCount)
		assertEquals(ReaderSessionActivationState.Legacy, store.state)

		assertEquals(
			ReaderActivationInstallResult.Installed,
			barrier.installTestActivatedSession(completeActivatedSessionPorts(), decision)
		)
		assertEquals(1, store.atomicWriteCount)
		val installed = assertNotNull(
			store.snapshot,
			"Test and production installation must publish one typed snapshot authority"
		)
		assertEquals(ReaderSessionActivationState.Activated, store.state)
		assertTrue(store.commandEgressOpen)
		assertTrue(
			installed.initialDecision === store.initialDecision,
			"Observable activation decision must come from the installed snapshot"
		)
		assertTrue(
			installed.journal === store.journal,
			"Observable activation journal must come from the installed snapshot"
		)
		assertTrue(
			store.initialDecision.origin is ReaderInitialCommittedPresentationOrigin.Neutral,
			"Installed test baseline must remain explicitly neutral"
		)
		assertTrue(
			store.journal.committed is paige.navic.reader.ReaderCommittedPresentation.Initial,
			"Atomic installation must include the mandatory initial journal"
		)
		assertTrue(
			store.reservedNeutralBootstrap === decision.neutralBootstrapReservation,
			"Atomic installation must transfer the exact pre-install bootstrap reservation"
		)
	}

	@Test
	fun throwingFirstDrainConfirmationCannotStrandLaterPairedOwner() {
		val registry = ReaderExactPhysicalOwnerRegistry(
			ReaderLegacyInventorySource.RasterCaptureAndVisualState
		)
		val owners = requireNotNull(
			registry.admit(
				listOf(
					ReaderExactPhysicalOwnerDescriptor(
						kind = ReaderTransitionResourceKind.Raster,
						state = ReaderLegacyResourceState.Running
					),
					ReaderExactPhysicalOwnerDescriptor(
						kind = ReaderTransitionResourceKind.CallbackRegistration,
						origin = ReaderLegacyResourceOrigin.Pending,
						state = ReaderLegacyResourceState.Registered
					)
				)
			)
		)
		val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(131L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			registry.freezeForTransitionActivation(domain)
		)
		val rows = registry.snapshotFrozenOwnership()
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			registry.drainFrozenOwnership(rows.first().physicalIdentity) { identity ->
				confirmations += identity
				error("bounded first confirmation failure")
			}
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			registry.drainFrozenOwnership(rows.last().physicalIdentity, confirmations::add)
		)

		val completion = runCatching { registry.complete(owners) }

		assertNull(
			completion.exceptionOrNull(),
			"One failing exact confirmation must not abort paired owner completion"
		)
		assertEquals(rows.map { it.physicalIdentity }, confirmations)
		assertTrue(registry.snapshotFrozenOwnership().isEmpty())
		registry.complete(owners)
		assertEquals(rows.map { it.physicalIdentity }, confirmations)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			registry.restoreAfterTransitionActivation(domain)
		)
	}

	@Test
	fun inventoryHasAllSixteenSourcesAndCompositeIdentityDoesNotCollide() {
		assertEquals(16, ReaderLegacyInventorySource.entries.size)
		val token = ReaderLegacyFreezeToken(1L)
		val first = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(3L, token),
			ReaderLegacyInventorySource.Deck,
			ReaderLegacySourceLocalOpaqueToken(7L)
		)
		val otherSource = first.copy(source = ReaderLegacyInventorySource.RasterPreparation)
		val otherFreeze = first.copy(
			domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(2L))
		)
		val exactDuplicate = first.copy()

		assertTrue(first != otherSource, "Physical identity source must participate in equality")
		assertTrue(first != otherFreeze, "Physical identity freeze domain must participate in equality")
		assertTrue(first == exactDuplicate, "Exact physical identity copy must remain equal")
		assertEquals(3, linkedSetOf(first, otherSource, otherFreeze, exactDuplicate).size)
	}

	@Test
	fun fixedPointRequiresSuccessiveCompleteIdenticalSnapshots() {
		val token = ReaderLegacyFreezeToken(1L)
		val tracker = ReaderLegacyInventoryFixedPointTracker(token)
		val first = ReaderLegacyResourceInventory.Complete(
			token,
			1L,
			4L,
			ReaderLegacyInventorySource.entries.toSet(),
			emptyList()
		)
		assertFalse(tracker.accept(first))
		assertTrue(tracker.accept(first.copy(snapshotSequence = 2L)))
	}

	@Test
	fun selectorRejectsVisibleOwnerBindingMismatchBeforeAuthorityImport() {
		val ownerBinding = activationBinding()
		val inventoryBinding = ownerBinding.copy(
			viewportGeneration = ownerBinding.viewportGeneration + 1L
		)
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(1L),
				ownerBinding,
				2L,
				3L,
				1200,
				800
			)
		)
		val token = ReaderLegacyFreezeToken(5L)
		val row = ReaderFrozenLegacyResource(
			freezeToken = token,
			physicalIdentity = ReaderLegacyPhysicalIdentity(
				ReaderLegacyPhysicalDomain(3L, token),
				ReaderLegacyInventorySource.FrameOrHandoff,
				ReaderLegacySourceLocalOpaqueToken(7L)
			),
			kind = ReaderTransitionResourceKind.FrameHandoff,
			binding = inventoryBinding,
			visibleOwner = owner,
			origin = ReaderLegacyResourceOrigin.Owned,
			state = ReaderLegacyResourceState.Visible,
			mayBeCommittedPredecessor = true
		)

		assertIs<ReaderAdoptedPredecessorSelection.Neutral>(
			ReaderAdoptedPredecessorSelector.select(listOf(row))
		)
	}

	@Test
	fun importedLegacyAuthorityRejectsTransitionOwnedRegistration() {
		val binding = activationBinding()
		val id = ReaderTransitionId(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			sequence = 1L,
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
		)
		val registration = paige.navic.reader.ReaderTransitionResourceRegistration(
			paige.navic.reader.ReaderTransitionResourceKey(
				id,
				ReaderTransitionResourceKind.Deck,
				11L
			),
			paige.navic.reader.ReaderResourceRetirementOrder(3L, 5L, 1L)
		)

		assertFailsWith<IllegalArgumentException> {
			ReaderImportedLegacyResourceRegistration(
				ReaderLegacyPhysicalIdentity(
					ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(13L)),
					ReaderLegacyInventorySource.Deck,
					ReaderLegacySourceLocalOpaqueToken(17L)
				),
				registration
			)
		}
	}

	@Test
	fun ambiguousVisiblePredecessorIsRejected() {
		val binding = paige.navic.reader.ReaderPresentationBinding(
			"fixture", 2L, 3L, 5L,
			paige.navic.reader.ReaderDestinationCommitIdentity("fixture", 1L),
			7L, 11L, 13L
		)
		val owner = paige.navic.reader.ReaderPresentationFrameOwner.ShellCover(
			paige.navic.reader.ReaderShellCoverCommitProof(
				paige.navic.reader.ReaderPresentationToken(17L), binding, 19L, 23L, 1200, 800
			)
		)
		val token = ReaderLegacyFreezeToken(1L)
		fun row(local: Long) = ReaderFrozenLegacyResource(
			token,
			ReaderLegacyPhysicalIdentity(
				ReaderLegacyPhysicalDomain(3L, token),
				ReaderLegacyInventorySource.FrameOrHandoff,
				ReaderLegacySourceLocalOpaqueToken(local)
			),
			paige.navic.reader.ReaderTransitionResourceKind.FrameHandoff,
			binding,
			owner,
			ReaderLegacyResourceOrigin.Owned,
			ReaderLegacyResourceState.Visible,
			true
		)
		assertEquals(
			ReaderAdoptedPredecessorSelection.Ambiguous,
			ReaderAdoptedPredecessorSelector.select(listOf(row(1L), row(2L)))
		)
	}

	@Test
	fun activationFreezesCheckpointsDrainsToFixedPointAndInstallsOnce() {
		val token = ReaderLegacyFreezeToken(11L)
		val binding = activationBinding()
		val visibleOwner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(17L), binding, 19L, 23L, 1200, 800
			)
		)
		fun row(
			source: ReaderLegacyInventorySource,
			local: Long,
			visible: Boolean
		) = ReaderFrozenLegacyResource(
			token,
			ReaderLegacyPhysicalIdentity(
				ReaderLegacyPhysicalDomain(3L, token),
				source,
				ReaderLegacySourceLocalOpaqueToken(local)
			),
			if (visible) ReaderTransitionResourceKind.FrameHandoff else ReaderTransitionResourceKind.Raster,
			binding.takeIf { visible },
			visibleOwner.takeIf { visible },
			ReaderLegacyResourceOrigin.Owned,
			if (visible) ReaderLegacyResourceState.Visible else ReaderLegacyResourceState.Running,
			visible
		)
		val predecessor = row(ReaderLegacyInventorySource.FrameOrHandoff, 1L, true)
		val stale = row(ReaderLegacyInventorySource.RasterPreparation, 1L, false)
		var snapshotSequence = 0L
		val drained = mutableListOf<ReaderLegacyPhysicalIdentity>()
		val finalizedRetirements = mutableListOf<Set<ReaderLegacyPhysicalIdentity>>()
		lateinit var store: ReaderActivatedSessionSnapshotStore
		val checkpoint = activationCheckpoint(token, visibleOwner, binding, predecessor.physicalIdentity)
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = checkpoint
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token,
					++snapshotSequence,
					1L,
					ReaderLegacyInventorySource.entries.toSet(),
					listOf(predecessor, stale)
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			): ReaderPortCommandResult {
				drained += physicalIdentity
				onConfirmed(physicalIdentity)
				return ReaderPortCommandResult.Accepted
			}
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
				ReaderPortCommandResult.Accepted
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			): ReaderPortCommandResult = error("Successful activation cannot restore")
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				ReaderLegacyCommitRestoredResult.Rejected(ReaderTransitionFailureReason.PortRejected)
			override fun finalizeActivatedHandoff(
				token: ReaderLegacyFreezeToken,
				selected: ReaderImportedLegacyResourceRegistration?,
				confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>
			): ReaderPortCommandResult {
				assertEquals(1, store.atomicWriteCount)
				assertEquals(predecessor.physicalIdentity, selected?.physicalIdentity)
				finalizedRetirements += confirmedRetirements
				return ReaderPortCommandResult.Accepted
			}
		}
		val releaseLedger = ReaderTransitionReleaseLedger()
		store = ReaderActivatedSessionSnapshotStore()
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(store),
			narrowInitialLease = { it },
			releaseLedger = releaseLedger
		)

		assertEquals(
			ReaderActivationInstallResult.Installed,
			coordinator.activateForTest(
				completeActivatedSessionPorts(),
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(ReaderSessionActivationState.Activated, coordinator.state)
		assertEquals(1, drained.size)
		assertTrue(
			drained.single() == stale.physicalIdentity,
			"Activation must drain only the exact non-predecessor physical identity"
		)
		val adoptedSeed = requireNotNull(store.initialDecision.adoptedSeed)
		assertTrue(
			adoptedSeed.physicalIdentity == predecessor.physicalIdentity,
			"Adopted seed must retain the selected physical identity"
		)
		assertEquals(ReaderTransitionResourceKind.FrameHandoff, adoptedSeed.resourceKind)
		assertTrue(
			store.journal.committed is paige.navic.reader.ReaderCommittedPresentation.Initial,
			"Adopted install must atomically include the initial journal"
		)
		assertEquals(1, store.atomicWriteCount)
		assertEquals(1, finalizedRetirements.size)
		assertEquals(setOf(stale.physicalIdentity), finalizedRetirements.single())
		val imported = requireNotNull(store.initialDecision.adoptedResource)
		assertEquals(
			predecessor.physicalIdentity,
			imported.physicalIdentity,
			"Selected physical release authority must remain coordinator-owned"
		)
		val installedSnapshot = assertNotNull(store.snapshot)
		val installedLedgerField = installedSnapshot.javaClass.declaredFields.singleOrNull {
			it.name == "releaseLedger"
		}
		assertNotNull(
			installedLedgerField,
			"Atomic activation install must transfer the selected import ledger"
		).isAccessible = true
		assertTrue(
			installedLedgerField.get(installedSnapshot) === releaseLedger,
			"Installed runtime authority must own the exact activation ledger"
		)
		val release = requireNotNull(
			releaseLedger.requestRelease(
				paige.navic.reader.ReaderResourceReleaseIssuer.Session(3L, 5L),
				imported.registration
			)
		)
		assertNull(release.transitionId)
		val confirmation = paige.navic.reader.ReaderTransitionFact.ResourceReleased(
			release.identity
		)
		assertTrue(releaseLedger.confirmLegacyReleased(imported.physicalIdentity, confirmation))
		assertFalse(releaseLedger.confirmLegacyReleased(imported.physicalIdentity, confirmation))
		assertEquals(
			ReaderTransitionResourceState.Released,
			releaseLedger.stateOf(imported.registration.key)
		)
	}

	@Test
	fun selectedPredecessorMustRemainExactThroughFinalInventory() {
		val token = ReaderLegacyFreezeToken(157L)
		val domain = ReaderLegacyPhysicalDomain(3L, token)
		val binding = activationBinding()
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(159L),
				binding,
				161L,
				163L,
				1200,
				800
			)
		)
		val selected = ReaderFrozenLegacyResource(
			freezeToken = token,
			physicalIdentity = ReaderLegacyPhysicalIdentity(
				domain,
				ReaderLegacyInventorySource.FrameOrHandoff,
				ReaderLegacySourceLocalOpaqueToken(165L)
			),
			kind = ReaderTransitionResourceKind.FrameHandoff,
			binding = binding,
			visibleOwner = owner,
			origin = ReaderLegacyResourceOrigin.Owned,
			state = ReaderLegacyResourceState.Visible,
			mayBeCommittedPredecessor = true
		)
		val stale = activationDrainRow(
			token,
			ReaderLegacyInventorySource.RasterPreparation,
			167L
		)
		var inventoryCalls = 0L
		var finalizeCalls = 0
		val checkpoint = activationCheckpoint(
			token,
			owner,
			binding,
			selected.physicalIdentity
		)
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = checkpoint
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token,
					++inventoryCalls,
					1L,
					ReaderLegacyInventorySource.entries.toSet(),
					if (inventoryCalls <= 2L) listOf(selected, stale) else emptyList()
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			): ReaderPortCommandResult {
				assertTrue(physicalIdentity == stale.physicalIdentity)
				onConfirmed(physicalIdentity)
				return ReaderPortCommandResult.Accepted
			}
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
				error("A destructive drain requires restoration")
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			): ReaderPortCommandResult {
				onConfirmed(source, ReaderLegacyRestorationResult.Restored)
				return ReaderPortCommandResult.Accepted
			}
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				ReaderLegacyCommitRestoredResult.Applied
			override fun finalizeActivatedHandoff(
				token: ReaderLegacyFreezeToken,
				selected: ReaderImportedLegacyResourceRegistration?,
				confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>
			): ReaderPortCommandResult {
				finalizeCalls += 1
				return ReaderPortCommandResult.Accepted
			}
		}
		val store = ReaderActivatedSessionSnapshotStore()
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(store),
			narrowInitialLease = { it }
		)

		assertEquals(
			ReaderActivationInstallResult.Rejected(
				ReaderTransitionFailureReason.InvalidLegacyResource
			),
			coordinator.activateForTest(
				completeActivatedSessionPorts(),
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(ReaderSessionActivationState.Legacy, coordinator.state)
		assertEquals(0, store.atomicWriteCount)
		assertEquals(0, finalizeCalls)
	}

	@Test
	fun rejectedAtomicInstallRollsBackUnpublishedSelectedReleaseAuthority() {
		val token = ReaderLegacyFreezeToken(167L)
		val domain = ReaderLegacyPhysicalDomain(3L, token)
		val binding = activationBinding()
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(169L),
				binding,
				171L,
				173L,
				1200,
				800
			)
		)
		val physicalIdentity = ReaderLegacyPhysicalIdentity(
			domain,
			ReaderLegacyInventorySource.FrameOrHandoff,
			ReaderLegacySourceLocalOpaqueToken(175L)
		)
		val selected = ReaderFrozenLegacyResource(
			freezeToken = token,
			physicalIdentity = physicalIdentity,
			kind = ReaderTransitionResourceKind.FrameHandoff,
			binding = binding,
			visibleOwner = owner,
			origin = ReaderLegacyResourceOrigin.Owned,
			state = ReaderLegacyResourceState.Visible,
			mayBeCommittedPredecessor = true
		)
		var snapshotSequence = 0L
		var unfreezeCount = 0
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) =
				activationCheckpoint(token, owner, binding, physicalIdentity)
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token,
					++snapshotSequence,
					1L,
					ReaderLegacyInventorySource.entries.toSet(),
					listOf(selected)
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			) = error("Selected predecessor must not drain")
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken): ReaderPortCommandResult {
				unfreezeCount += 1
				return ReaderPortCommandResult.Accepted
			}
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			) = error("No destructive drain requires restoration")
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				error("No destructive drain requires restoration")
		}
		val store = ReaderActivatedSessionSnapshotStore()
		val installation = ReaderActivatedSessionInstallationBarrier(store)
		assertEquals(
			ReaderActivationInstallResult.Installed,
			installation.installTestActivatedSession(
				completeActivatedSessionPorts(),
				neutralDecision()
			)
		)
		val releaseLedger = ReaderTransitionReleaseLedger()
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = installation,
			narrowInitialLease = { it },
			releaseLedger = releaseLedger
		)

		assertEquals(
			ReaderActivationInstallResult.Rejected(
				ReaderTransitionFailureReason.AtomicPublicationRejected
			),
			coordinator.activateForTest(
				completeActivatedSessionPorts(),
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(ReaderSessionActivationState.Legacy, coordinator.state)
		assertEquals(1, unfreezeCount)
		assertEquals(0, releaseLedger.retentionSnapshot().activeStateCount)
		val replacement = assertNotNull(
			releaseLedger.importLegacy(
				physicalIdentity = physicalIdentity,
				ownerId = paige.navic.reader.ReaderTransitionResourceOwnerId.AdoptedPredecessor(
					ReaderAdoptedPredecessorSeedId.fromValidatedImport(2L)
				),
				kind = ReaderTransitionResourceKind.FrameHandoff,
				coordinatorEpoch = 5L
			)
		)
		assertEquals(1L, replacement.registration.retirementOrder.sequence)
		assertEquals(1L, replacement.registration.key.opaqueId)
	}

	@Test
	fun legacyFinalizationFailureAfterInstallFailsClosedInReleaseOnly() {
		val token = ReaderLegacyFreezeToken(173L)
		val checkpoint = activationCheckpoint(
			token,
			ReaderPresentationFrameOwner.Neutral,
			null,
			null
		)
		var snapshotSequence = 0L
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = checkpoint
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token,
					++snapshotSequence,
					1L,
					ReaderLegacyInventorySource.entries.toSet(),
					emptyList()
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			) = error("Neutral activation has nothing to drain")
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
				ReaderPortCommandResult.Accepted
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			) = error("Installed activation cannot restore")
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				error("Installed activation cannot commit legacy restoration")
			override fun finalizeActivatedHandoff(
				token: ReaderLegacyFreezeToken,
				selected: ReaderImportedLegacyResourceRegistration?,
				confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>
			) = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
		}
		val store = ReaderActivatedSessionSnapshotStore()
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(store),
			narrowInitialLease = { it },
			reserveNeutralBootstrap = {
				ReaderReservedNeutralBootstrapRequest(ReaderSemanticRequestHandle(179L), 3L)
			}
		)

		val activatedPorts = completeActivatedSessionPorts()
		assertEquals(
			ReaderActivationInstallResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed),
			coordinator.activateForTest(
				activatedPorts,
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(2, store.atomicWriteCount)
		assertEquals(ReaderSessionActivationState.ReleaseOnly, coordinator.state)
		assertEquals(ReaderSessionActivationState.ReleaseOnly, store.state)
		assertFalse(store.commandEgressOpen)
		assertNull(store.reservedNeutralBootstrap)
		assertNull(store.snapshot?.portAuthority)
		val releaseOnlyAuthority = assertNotNull(store.snapshot?.releaseOnlyPortAuthority)
		assertTrue(releaseOnlyAuthority.resources === activatedPorts.resources)
		assertTrue(releaseOnlyAuthority.releaseSink === activatedPorts.releaseSink)
		assertTrue(releaseOnlyAuthority.factOnlyTimer === activatedPorts.factOnlyTimer)
		val projection = runCatching { requireNotNull(store.snapshot).sanitizedProjection }
		assertNull(
			projection.exceptionOrNull(),
			"A valid release-only snapshot must retain total authority-free diagnostics"
		)
		assertEquals(
			ReaderSessionActivationState.ReleaseOnly,
			projection.getOrThrow().activationState
		)
	}

	@Test
	fun adoptedFinalizationFailureRetainsExactImportedReleaseAuthority() {
		val token = ReaderLegacyFreezeToken(181L)
		val binding = activationBinding()
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(191L), binding, 193L, 197L, 1200, 800
			)
		)
		val physicalIdentity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(3L, token),
			ReaderLegacyInventorySource.FrameOrHandoff,
			ReaderLegacySourceLocalOpaqueToken(199L)
		)
		val predecessor = ReaderFrozenLegacyResource(
			freezeToken = token,
			physicalIdentity = physicalIdentity,
			kind = ReaderTransitionResourceKind.FrameHandoff,
			binding = binding,
			visibleOwner = owner,
			origin = ReaderLegacyResourceOrigin.Owned,
			state = ReaderLegacyResourceState.Visible,
			mayBeCommittedPredecessor = true
		)
		val checkpoint = activationCheckpoint(token, owner, binding, physicalIdentity)
		var snapshotSequence = 0L
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = checkpoint
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token,
					++snapshotSequence,
					1L,
					ReaderLegacyInventorySource.entries.toSet(),
					listOf(predecessor)
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			) = error("The selected predecessor must remain owned")
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
				ReaderPortCommandResult.Accepted
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			) = error("Installed activation cannot restore")
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				error("Installed activation cannot commit legacy restoration")
			override fun finalizeActivatedHandoff(
				token: ReaderLegacyFreezeToken,
				selected: ReaderImportedLegacyResourceRegistration?,
				confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>
			): ReaderPortCommandResult {
				assertEquals(physicalIdentity, selected?.physicalIdentity)
				return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
			}
		}
		var importedReleaseCount = 0
		var genericReleaseCount = 0
		var closeBudgetBindCount = 0
		var closeBudgetCancelCount = 0
		var confirmImportedRelease: (() -> Unit)? = null
		var expireCloseBudget: (() -> Unit)? = null
		val timer = object : ReaderTask6FactOnlyTimerPort {
			override fun bindBeforeWork(
				transitionId: paige.navic.reader.ReaderTransitionId,
				onExpired: (paige.navic.reader.ReaderTransitionFact.DeadlineExpired) -> Unit
			): ReaderTask6FactOnlyTimerRegistration? = null
			override fun matchingProgress(
				registration: ReaderTask6FactOnlyTimerRegistration,
				nowMillis: Long
			) = ReaderPortCommandResult.Accepted
			override fun snapshotForTask7Transfer(
				registration: ReaderTask6FactOnlyTimerRegistration
			): ReaderTask6FactOnlyTimerTransferSnapshot? = null
			override fun cancel(registration: ReaderTask6FactOnlyTimerRegistration) =
				ReaderPortCommandResult.Accepted
			override fun bindReleaseOnlyCloseBudget(
				cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey,
				onExpired: (paige.navic.reader.ReaderReleaseOnlyCleanupKey) -> Unit
			): ReaderTask6ReleaseOnlyTimerRegistration {
				closeBudgetBindCount += 1
				expireCloseBudget = { onExpired(cleanupKey) }
				return ReaderTask6ReleaseOnlyTimerRegistration(
					ReaderTask6FactOnlyTimerRegistrationId(1L),
					cleanupKey,
					ReaderLegacyPhysicalIdentity(
						ReaderLegacyPhysicalDomain(3L, token),
						ReaderLegacyInventorySource.DeadlineRegistration,
						ReaderLegacySourceLocalOpaqueToken(211L)
					),
					2_000L
				)
			}
			override fun cancelReleaseOnlyCloseBudget(
				registration: ReaderTask6ReleaseOnlyTimerRegistration
			): ReaderPortCommandResult {
				closeBudgetCancelCount += 1
				return ReaderPortCommandResult.Accepted
			}
		}
		val resourcePort = object : ReaderTransitionResourcePort, ReaderReleaseOnlySinkPort {
			override fun release(
				command: ReaderTransitionCommand.ReleaseResource,
				onFact: (paige.navic.reader.ReaderTransitionFact.ResourceReleased) -> Unit
			): ReaderPortCommandResult {
				genericReleaseCount += 1
				return ReaderPortCommandResult.Accepted
			}
			override fun releaseLegacy(
				command: ReaderTransitionCommand.ReleaseResource,
				imported: ReaderImportedLegacyResourceRegistration,
				onConfirmed: (
					ReaderLegacyPhysicalIdentity,
					paige.navic.reader.ReaderTransitionFact.ResourceReleased
				) -> Unit
			): ReaderPortCommandResult {
				importedReleaseCount += 1
				confirmImportedRelease = {
					onConfirmed(
						imported.physicalIdentity,
						paige.navic.reader.ReaderTransitionFact.ResourceReleased(command.identity)
					)
				}
				return ReaderPortCommandResult.Accepted
			}
			override fun cancelOwnedWork(command: ReaderTransitionCommand.CancelOwnedWork) =
				ReaderPortCommandResult.Accepted
			override fun observe(fact: paige.navic.reader.ReaderTransitionFact.ResourceObserved) =
				ReaderPortCommandResult.Accepted
			override fun observeLegacy(imported: ReaderImportedLegacyResourceRegistration) =
				ReaderPortCommandResult.Accepted
			override fun confirm(fact: paige.navic.reader.ReaderTransitionFact.ResourceReleased) =
				ReaderPortCommandResult.Accepted
			override fun confirmLegacy(
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				fact: paige.navic.reader.ReaderTransitionFact.ResourceReleased
			) = ReaderPortCommandResult.Accepted
			override fun release(command: ReaderTransitionCommand.ReleaseResource): ReaderPortCommandResult {
				genericReleaseCount += 1
				return ReaderPortCommandResult.Accepted
			}
		}
		val activatedPorts = completeActivatedSessionPorts().copy(
			resources = resourcePort,
			releaseSink = resourcePort,
			factOnlyTimer = timer
		)
		val releaseLedger = ReaderTransitionReleaseLedger()
		val finalizationCleanupQueue = ArrayDeque<() -> Unit>()
		val store = ReaderActivatedSessionSnapshotStore { action ->
			finalizationCleanupQueue.addLast(action)
			true
		}
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(store),
			narrowInitialLease = { it },
			releaseLedger = releaseLedger
		)

		assertEquals(
			ReaderActivationInstallResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed),
			coordinator.activateForTest(
				activatedPorts,
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		val callbackFailure = AtomicReference<Throwable?>()
		Thread {
			try {
				requireNotNull(confirmImportedRelease).invoke()
				requireNotNull(expireCloseBudget).invoke()
			} catch (throwable: Throwable) {
				callbackFailure.set(throwable)
			}
		}.apply {
			start()
			join()
		}
		assertTrue(
			callbackFailure.get() == null,
			"Background finalization cleanup callbacks must be nonthrowing"
		)
		assertEquals(
			paige.navic.reader.ReaderReleaseOnlyCleanupDeadlineStatus.Armed,
			store.snapshot?.journal?.releaseOnlyCleanup?.deadlineStatus,
			"Cleanup callbacks must serialize through the main queue before mutating state"
		)
		while (finalizationCleanupQueue.isNotEmpty()) {
			finalizationCleanupQueue.removeFirst().invoke()
		}

		val snapshot = assertNotNull(store.snapshot)
		val authority = assertNotNull(snapshot.releaseOnlyPortAuthority)
		assertNull(snapshot.portAuthority)
		assertTrue(snapshot.releaseLedger === releaseLedger)
		assertTrue(authority.resources === resourcePort)
		assertTrue(authority.releaseSink === resourcePort)
		assertTrue(authority.factOnlyTimer === activatedPorts.factOnlyTimer)
		val committed = assertIs<paige.navic.reader.ReaderCommittedPresentation.Initial>(
			snapshot.journal.committed
		)
		val origin = assertIs<paige.navic.reader.ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor>(
			committed.origin
		)
		val registration = origin.resource
		assertNull(
			snapshot.releaseLedger.importedFor(registration),
			"Confirmed cleanup must retire the imported physical authority"
		)
		val cleanup = assertNotNull(snapshot.journal.releaseOnlyCleanup)
		assertEquals(
			paige.navic.reader.ReaderReleaseOnlyCleanupTrigger.PublicationClose,
			cleanup.trigger
		)
		assertEquals(
			paige.navic.reader.ReaderReleaseOnlyCleanupDeadlineStatus.CancelledAfterTerminalAccounting,
			cleanup.deadlineStatus
		)
		assertEquals(1, closeBudgetBindCount)
		assertEquals(1, closeBudgetCancelCount)
		assertEquals(1, importedReleaseCount)
		assertEquals(0, genericReleaseCount, "Finalization failure must not reopen ordinary egress")
		assertEquals(
			ReaderTransitionResourceState.Released,
			snapshot.releaseLedger.stateOf(registration.key)
		)
	}

	@Test
	fun synchronousFinalizationCleanupExpiryPreventsPhysicalReleaseBeforeMainDrain() {
		val decision = adoptedDecisionForPrivacy(373L, 1L)
		val importedDecision = assertNotNull(decision.adoptedResource)
		val ownerId = importedDecision.registration.key.ownerId as
			paige.navic.reader.ReaderTransitionResourceOwnerId.AdoptedPredecessor
		val releaseLedger = ReaderTransitionReleaseLedger()
		val imported = releaseLedger.importLegacy(
			importedDecision.physicalIdentity,
			ownerId,
			importedDecision.registration.key.kind,
			coordinatorEpoch = 5L
		)
		assertTrue(
			imported?.registration == importedDecision.registration,
			"Fixture must install the exact imported registration"
		)
		val finalizationCleanupQueue = ArrayDeque<() -> Unit>()
		val store = ReaderActivatedSessionSnapshotStore { action ->
			finalizationCleanupQueue.addLast(action)
			true
		}
		val basePorts = completeActivatedSessionPorts()
		var importedReleaseCount = 0
		val resources = object : ReaderTransitionResourcePort by requireNotNull(basePorts.resources) {
			override fun releaseLegacy(
				command: ReaderTransitionCommand.ReleaseResource,
				imported: ReaderImportedLegacyResourceRegistration,
				onConfirmed: (
					ReaderLegacyPhysicalIdentity,
					paige.navic.reader.ReaderTransitionFact.ResourceReleased
				) -> Unit
			): ReaderPortCommandResult {
				importedReleaseCount += 1
				return ReaderPortCommandResult.Accepted
			}
		}
		val timer = object : ReaderTask6FactOnlyTimerPort by requireNotNull(basePorts.factOnlyTimer) {
			override fun bindReleaseOnlyCloseBudget(
				cleanupKey: paige.navic.reader.ReaderReleaseOnlyCleanupKey,
				onExpired: (paige.navic.reader.ReaderReleaseOnlyCleanupKey) -> Unit
			): ReaderTask6ReleaseOnlyTimerRegistration {
				onExpired(cleanupKey)
				return ReaderTask6ReleaseOnlyTimerRegistration(
					ReaderTask6FactOnlyTimerRegistrationId(379L),
					cleanupKey,
					ReaderLegacyPhysicalIdentity(
						ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(383L)),
						ReaderLegacyInventorySource.DeadlineRegistration,
						ReaderLegacySourceLocalOpaqueToken(389L)
					),
					2_000L
				)
			}
		}
		val ports = basePorts.copy(resources = resources, factOnlyTimer = timer)
		val barrier = ReaderActivatedSessionInstallationBarrier(store)
		assertEquals(
			ReaderActivationInstallResult.Installed,
			barrier.installTestActivatedSession(ports, decision, releaseLedger)
		)

		assertTrue(barrier.failClosedAfterFinalizationRejection(decision))

		assertEquals(
			0,
			importedReleaseCount,
			"An already observed close-budget expiry must suppress physical release"
		)
		while (finalizationCleanupQueue.isNotEmpty()) {
			finalizationCleanupQueue.removeFirst().invoke()
		}
		assertEquals(
			paige.navic.reader.ReaderReleaseOnlyCleanupDeadlineStatus.Elapsed,
			store.snapshot?.journal?.releaseOnlyCleanup?.deadlineStatus
		)
	}

	@Test
	fun neutralBootstrapRegistrationNullBlocksActivationBeforeAtomicInstall() {
		val token = ReaderLegacyFreezeToken(211L)
		val checkpoint = activationCheckpoint(
			token,
			ReaderPresentationFrameOwner.Neutral,
			null,
			null
		)
		var snapshotSequence = 0L
		var unfreezeCount = 0
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = checkpoint
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token,
					++snapshotSequence,
					1L,
					ReaderLegacyInventorySource.entries.toSet(),
					emptyList()
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			) = error("Neutral activation has nothing to drain")
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken): ReaderPortCommandResult {
				unfreezeCount += 1
				return ReaderPortCommandResult.Accepted
			}
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			) = error("Pre-install neutral registration failure must unfreeze without restoration")
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				error("Pre-install neutral registration failure must not commit restoration")
		}
		val store = ReaderActivatedSessionSnapshotStore()
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(store),
			narrowInitialLease = { it }
		)

		val result = coordinator.activateForTest(
			completeActivatedSessionPorts(),
			ReaderInitialPresentationInputLease.ChromeOnly
		)

		assertEquals(
			ReaderActivationInstallResult.Rejected(
				ReaderTransitionFailureReason.ActivationPrerequisiteMissing
			),
			result
		)
		assertEquals(0, store.atomicWriteCount)
		assertEquals(ReaderSessionActivationState.Legacy, store.state)
		assertFalse(store.commandEgressOpen)
		assertEquals(1, unfreezeCount)
	}

	@Test
	fun curlAdoptionFencesLegacyGestureAndUsesIdentityFreeLease() {
		val token = ReaderLegacyFreezeToken(223L)
		val binding = activationBinding()
		val frame = ReaderCurlPresentationFrame(
			token = ReaderPresentationToken(227L),
			binding = binding,
			presentedFrame = 229L,
			viewportWidth = 1200,
			viewportHeight = 800,
			rasterGeneration = requireNotNull(binding.rasterGeneration),
			textureGeneration = requireNotNull(binding.textureGeneration)
		)
		val owner = ReaderPresentationFrameOwner.Curl(frame)
		val identity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(3L, token),
			ReaderLegacyInventorySource.Deck,
			ReaderLegacySourceLocalOpaqueToken(233L)
		)
		val predecessor = ReaderFrozenLegacyResource(
			token,
			identity,
			ReaderTransitionResourceKind.Deck,
			binding,
			owner,
			ReaderLegacyResourceOrigin.Owned,
			ReaderLegacyResourceState.Visible,
			true
		)
		val checkpoint = activationCheckpoint(token, owner, binding, identity)
		var snapshotSequence = 0L
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = checkpoint
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token,
					++snapshotSequence,
					1L,
					ReaderLegacyInventorySource.entries.toSet(),
					listOf(predecessor)
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			) = error("Selected curl predecessor must not be drained")
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
				ReaderPortCommandResult.Accepted
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			) = error("Successful curl adoption cannot restore")
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				ReaderLegacyCommitRestoredResult.Rejected(ReaderTransitionFailureReason.PortRejected)
			override fun finalizeActivatedHandoff(
				token: ReaderLegacyFreezeToken,
				selected: ReaderImportedLegacyResourceRegistration?,
				confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>
			) = ReaderPortCommandResult.Accepted
		}
		val store = ReaderActivatedSessionSnapshotStore()
		var fencedGestureCount = 0
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(store),
			narrowInitialLease = { lease ->
				assertTrue(
					lease == ReaderInitialPresentationInputLease.None ||
						lease == ReaderInitialPresentationInputLease.ChromeOnly,
					"Curl activation input must be identity-free before narrowing"
				)
				lease
			},
			fenceAdoptedCurlGesture = {
				fencedGestureCount += 1
				true
			}
		)

		assertEquals(
			ReaderActivationInstallResult.Installed,
			coordinator.activateForTest(
				completeActivatedSessionPorts(),
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(1, fencedGestureCount)
		val origin = assertTrueAdoptedOrigin(store.initialDecision)
		assertEquals(
			ReaderInitialPresentationInputLease.ChromeOnly,
			origin.requestedLease,
			"Curl adoption must not retain transition-bearing requested input"
		)
		assertEquals(ReaderInitialPresentationInputLease.ChromeOnly, origin.physicalLease)
		assertTrue(origin.owner is ReaderPresentationFrameOwner.Curl)
	}

	@Test
	fun ownerBindingAndGenerationLeaseMismatchesFailBeforeAnyDrain() {
		val binding = activationBinding()
		val otherBinding = binding.copy(viewportGeneration = binding.viewportGeneration + 1L)
		val nativeOwner = ReaderPresentationFrameOwner.NativePage(
			ReaderNativePagePresentationProof(
				binding = binding,
				transitionToken = null,
				presentedFrame = 239L,
				viewportWidth = 1200,
				viewportHeight = 800,
				rasterGeneration = requireNotNull(binding.rasterGeneration),
				textureGeneration = requireNotNull(binding.textureGeneration)
			)
		)
		val curlOwner = ReaderPresentationFrameOwner.Curl(
			ReaderCurlPresentationFrame(
				token = ReaderPresentationToken(241L),
				binding = binding,
				presentedFrame = 251L,
				viewportWidth = 1200,
				viewportHeight = 800,
				rasterGeneration = requireNotNull(binding.rasterGeneration),
				textureGeneration = requireNotNull(binding.textureGeneration)
			)
		)
		val cases = listOf(
			ActivationLeaseMismatchCase(
				owner = curlOwner,
				checkpointRequestedLease = ReaderInitialPresentationInputLease.CoverActions,
				activationLease = ReaderInitialPresentationInputLease.CoverActions,
				physicalLease = ReaderInitialPresentationInputLease.CoverActions
			),
			ActivationLeaseMismatchCase(
				owner = nativeOwner,
				checkpointRequestedLease = ReaderInitialPresentationInputLease.NativePage(
					otherBinding,
					requireNotNull(binding.textureGeneration)
				),
				activationLease = ReaderInitialPresentationInputLease.NativePage(
					otherBinding,
					requireNotNull(binding.textureGeneration)
				),
				physicalLease = ReaderInitialPresentationInputLease.NativePage(
					otherBinding,
					requireNotNull(binding.textureGeneration)
				)
			),
			ActivationLeaseMismatchCase(
				owner = nativeOwner,
				checkpointRequestedLease = ReaderInitialPresentationInputLease.NativePage(
					binding,
					requireNotNull(binding.textureGeneration) + 1L
				),
				activationLease = ReaderInitialPresentationInputLease.NativePage(
					binding,
					requireNotNull(binding.textureGeneration) + 1L
				),
				physicalLease = ReaderInitialPresentationInputLease.NativePage(
					binding,
					requireNotNull(binding.textureGeneration) + 1L
				)
			)
		)

		cases.forEach { case ->
			val result = activateLeaseMismatch(binding, case)
			assertEquals(
				ReaderActivationInstallResult.Rejected(
					ReaderTransitionFailureReason.ActivationPrerequisiteMissing
				),
				result.installResult
			)
			assertEquals(0, result.drainCount)
			assertEquals(0, result.store.atomicWriteCount)
			assertEquals(ReaderSessionActivationState.Legacy, result.state)
			assertEquals(1, result.unfreezeCount)
		}
	}

	@Test
	fun checkpointAndActivationLeaseDisagreementFailsBeforeAnyDrain() {
		val binding = activationBinding()
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(257L), binding, 263L, 269L, 1200, 800
			)
		)
		val result = activateLeaseMismatch(
			binding,
			ActivationLeaseMismatchCase(
				owner = owner,
				checkpointRequestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
				activationLease = ReaderInitialPresentationInputLease.CoverActions,
				physicalLease = ReaderInitialPresentationInputLease.CoverActions
			)
		)

		assertEquals(
			ReaderActivationInstallResult.Rejected(
				ReaderTransitionFailureReason.ActivationPrerequisiteMissing
			),
			result.installResult
		)
		assertEquals(0, result.drainCount)
		assertEquals(0, result.store.atomicWriteCount)
		assertEquals(ReaderSessionActivationState.Legacy, result.state)
		assertEquals(1, result.unfreezeCount)
	}

	@Test
	fun postDrainFailureRestoresAllSourcesOrBlocksWithoutLegacyFallback() {
		val token = ReaderLegacyFreezeToken(11L)
		val binding = activationBinding()
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(17L), binding, 19L, 23L, 1200, 800
			)
		)
		val identity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(3L, token),
			ReaderLegacyInventorySource.RasterPreparation,
			ReaderLegacySourceLocalOpaqueToken(1L)
		)
		val row = ReaderFrozenLegacyResource(
			token,
			identity,
			ReaderTransitionResourceKind.Raster,
			null,
			null,
			ReaderLegacyResourceOrigin.Owned,
			ReaderLegacyResourceState.Running,
			false
		)
		var snapshotSequence = 0L
		val restored = mutableListOf<ReaderLegacyInventorySource>()
		val checkpoint = activationCheckpoint(
			token,
			ReaderPresentationFrameOwner.Neutral,
			null,
			null
		)
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = checkpoint
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token, ++snapshotSequence, 1L,
					ReaderLegacyInventorySource.entries.toSet(), listOf(row)
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			) = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
				ReaderPortCommandResult.Accepted
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			): ReaderPortCommandResult {
				restored += source
				onConfirmed(
					source,
					if (source == ReaderLegacyInventorySource.Input) {
						ReaderLegacyRestorationResult.Failed(ReaderTransitionFailureReason.PortRejected)
					} else ReaderLegacyRestorationResult.Restored
				)
				return ReaderPortCommandResult.Accepted
			}
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				ReaderLegacyCommitRestoredResult.Applied
		}
		var restorationDelayMillis: Long? = null
		var restorationDeadlineCancellationCount = 0
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(
				ReaderActivatedSessionSnapshotStore()
			),
			narrowInitialLease = { it },
			reserveNeutralBootstrap = {
				ReaderReservedNeutralBootstrapRequest(
					paige.navic.reader.ReaderSemanticRequestHandle(1L),
					3L
				)
			},
			restorationDeadline = object : ReaderActivationRestorationDeadlinePort {
				override fun schedule(
					delayMillis: Long,
					onExpired: () -> Unit
				): ReaderActivationRestorationDeadlineRegistration {
					restorationDelayMillis = delayMillis
					return ReaderActivationRestorationDeadlineRegistration {
						restorationDeadlineCancellationCount += 1
					}
				}
			}
		)

		assertEquals(
			ReaderActivationInstallResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed),
			coordinator.activateForTest(
				completeActivatedSessionPorts(),
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(ReaderSessionActivationState.ActivationBlocked, coordinator.state)
		assertEquals(ReaderLegacyInventorySource.entries.toSet(), restored.toSet())
		assertEquals(3_000L, restorationDelayMillis)
		assertEquals(1, restorationDeadlineCancellationCount)
	}

	@Test
	fun rejectedDrainWaitsForAcceptedDrainQuiescenceAndFencesLatePriorAttemptCallback() {
		var activationNumber = 0L
		var snapshotSequence = 0L
		val drainCalls = mutableListOf<ReaderLegacyPhysicalIdentity>()
		val callbacks = mutableMapOf<ReaderLegacyPhysicalIdentity, (ReaderLegacyPhysicalIdentity) -> Unit>()
		val restored = mutableListOf<ReaderLegacyInventorySource>()
		val firstToken = ReaderLegacyFreezeToken(101L)
		val secondToken = ReaderLegacyFreezeToken(103L)
		val firstRows = listOf(
			activationDrainRow(firstToken, ReaderLegacyInventorySource.RasterPreparation, 1L),
			activationDrainRow(firstToken, ReaderLegacyInventorySource.Deck, 2L),
			activationDrainRow(firstToken, ReaderLegacyInventorySource.FrameOrHandoff, 3L)
		)
		val secondRow = activationDrainRow(
			secondToken,
			ReaderLegacyInventorySource.RasterPreparation,
			4L
		)
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = if (++activationNumber == 1L) firstToken else secondToken
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = activationCheckpoint(
				token,
				ReaderPresentationFrameOwner.Neutral,
				null,
				null
			)
			override fun inventory(token: ReaderLegacyFreezeToken) = ReaderLegacyResourceInventory.Complete(
				token,
				++snapshotSequence,
				1L,
				ReaderLegacyInventorySource.entries.toSet(),
				if (token == firstToken) firstRows else listOf(secondRow)
			)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			): ReaderPortCommandResult {
				drainCalls += physicalIdentity
				callbacks[physicalIdentity] = onConfirmed
				return if (physicalIdentity == firstRows[1].physicalIdentity) {
					ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
				} else ReaderPortCommandResult.Accepted
			}
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
				ReaderPortCommandResult.Accepted
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			): ReaderPortCommandResult {
				restored += source
				onConfirmed(source, ReaderLegacyRestorationResult.Restored)
				return ReaderPortCommandResult.Accepted
			}
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				ReaderLegacyCommitRestoredResult.Applied
			override fun finalizeActivatedHandoff(
				token: ReaderLegacyFreezeToken,
				selected: ReaderImportedLegacyResourceRegistration?,
				confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>
			) = ReaderPortCommandResult.Accepted
		}
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(
				ReaderActivatedSessionSnapshotStore()
			),
			narrowInitialLease = { it },
			reserveNeutralBootstrap = { neutralBootstrapReservation(3L) }
		)

		assertEquals(
			ReaderActivationInstallResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed),
			coordinator.activateForTest(
				completeActivatedSessionPorts(),
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(firstRows.take(2).map { it.physicalIdentity }, drainCalls)
		assertEquals(ReaderSessionActivationState.ActivationBlocked, coordinator.state)
		assertTrue(restored.isEmpty(), "Restoration started before the accepted drain settled")

		val priorCallback = callbacks.getValue(firstRows.first().physicalIdentity)
		priorCallback(firstRows.first().physicalIdentity)
		assertEquals(ReaderSessionActivationState.Legacy, coordinator.state)
		assertEquals(ReaderLegacyInventorySource.entries.toSet(), restored.toSet())

		assertEquals(
			ReaderActivationInstallResult.Pending,
			coordinator.activateForTest(
				completeActivatedSessionPorts(),
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(ReaderSessionActivationState.DrainingLegacy, coordinator.state)
		val restorationCount = restored.size
		priorCallback(firstRows.first().physicalIdentity)
		assertEquals(ReaderSessionActivationState.DrainingLegacy, coordinator.state)
		assertEquals(restorationCount, restored.size)
		callbacks.getValue(secondRow.physicalIdentity)(secondRow.physicalIdentity)
		assertEquals(ReaderSessionActivationState.Activated, coordinator.state)
	}

	@Test
	fun malformedSynchronousDrainConfirmationBlocksWithoutFurtherIssuanceOrRestoration() {
		val token = ReaderLegacyFreezeToken(107L)
		val rows = listOf(
			activationDrainRow(token, ReaderLegacyInventorySource.RasterPreparation, 11L),
			activationDrainRow(token, ReaderLegacyInventorySource.Deck, 13L)
		)
		var snapshotSequence = 0L
		var drainCount = 0
		var restorationCount = 0
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = activationCheckpoint(
				token,
				ReaderPresentationFrameOwner.Neutral,
				null,
				null
			)
			override fun inventory(token: ReaderLegacyFreezeToken) = ReaderLegacyResourceInventory.Complete(
				token,
				++snapshotSequence,
				1L,
				ReaderLegacyInventorySource.entries.toSet(),
				rows
			)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			): ReaderPortCommandResult {
				drainCount += 1
				onConfirmed(rows.last().physicalIdentity)
				return ReaderPortCommandResult.Accepted
			}
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
				ReaderPortCommandResult.Accepted
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			): ReaderPortCommandResult {
				restorationCount += 1
				return ReaderPortCommandResult.Accepted
			}
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				ReaderLegacyCommitRestoredResult.Applied
		}
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(
				ReaderActivatedSessionSnapshotStore()
			),
			narrowInitialLease = { it },
			reserveNeutralBootstrap = { neutralBootstrapReservation(3L) }
		)

		assertEquals(
			ReaderActivationInstallResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed),
			coordinator.activateForTest(
				completeActivatedSessionPorts(),
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(1, drainCount)
		assertEquals(0, restorationCount)
		assertEquals(ReaderSessionActivationState.ActivationBlocked, coordinator.state)
	}

	@Test
	fun restorationDeadlineSurvivesUntilAllAsynchronousConfirmations() {
		val token = ReaderLegacyFreezeToken(71L)
		val identity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(3L, token),
			ReaderLegacyInventorySource.RasterPreparation,
			ReaderLegacySourceLocalOpaqueToken(73L)
		)
		val row = ReaderFrozenLegacyResource(
			token,
			identity,
			ReaderTransitionResourceKind.Raster,
			null,
			null,
			ReaderLegacyResourceOrigin.Owned,
			ReaderLegacyResourceState.Running,
			false
		)
		val checkpoint = activationCheckpoint(
			token,
			ReaderPresentationFrameOwner.Neutral,
			null,
			null
		)
		var snapshotSequence = 0L
		val callbacks = linkedMapOf<ReaderLegacyInventorySource, (
			ReaderLegacyInventorySource,
			ReaderLegacyRestorationResult
		) -> Unit>()
		var commitCount = 0
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = checkpoint
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token,
					++snapshotSequence,
					1L,
					ReaderLegacyInventorySource.entries.toSet(),
					listOf(row)
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			) = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
				ReaderPortCommandResult.Accepted
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			): ReaderPortCommandResult {
				callbacks[source] = onConfirmed
				return ReaderPortCommandResult.Accepted
			}
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint):
				ReaderLegacyCommitRestoredResult {
				commitCount += 1
				return ReaderLegacyCommitRestoredResult.Applied
			}
		}
		var cancellationCount = 0
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(
				ReaderActivatedSessionSnapshotStore()
			),
			narrowInitialLease = { it },
			reserveNeutralBootstrap = {
				ReaderReservedNeutralBootstrapRequest(
					paige.navic.reader.ReaderSemanticRequestHandle(1L),
					3L
				)
			},
			restorationDeadline = object : ReaderActivationRestorationDeadlinePort {
				override fun schedule(
					delayMillis: Long,
					onExpired: () -> Unit
				): ReaderActivationRestorationDeadlineRegistration {
					assertEquals(3_000L, delayMillis)
					return ReaderActivationRestorationDeadlineRegistration { cancellationCount += 1 }
				}
			}
		)

		coordinator.activateForTest(
			completeActivatedSessionPorts(),
			ReaderInitialPresentationInputLease.ChromeOnly
		)

		assertEquals(ReaderSessionActivationState.RestoringLegacy, coordinator.state)
		assertEquals(0, cancellationCount)
		assertEquals(0, commitCount)
		assertEquals(ReaderLegacyInventorySource.entries.toSet(), callbacks.keys)
		ReaderLegacyInventorySource.entries.dropLast(1).forEach { source ->
			callbacks.getValue(source)(source, ReaderLegacyRestorationResult.Restored)
			assertEquals(ReaderSessionActivationState.RestoringLegacy, coordinator.state)
			assertEquals(0, cancellationCount)
			assertEquals(0, commitCount)
		}
		val last = ReaderLegacyInventorySource.entries.last()
		callbacks.getValue(last)(last, ReaderLegacyRestorationResult.Restored)
		assertEquals(ReaderSessionActivationState.Legacy, coordinator.state)
		assertEquals(1, commitCount)
		assertEquals(1, cancellationCount)
	}

	@Test
	fun staleRestorationCallbackCannotAdvanceTheNextActivationAttempt() {
		val harness = TwoAttemptRestorationHarness()
		harness.beginRestoration(0)
		val staleSource = ReaderLegacyInventorySource.RasterPreparation
		val staleCallback = harness.restorationCallbacks[0].getValue(staleSource)
		harness.completeRestoration(0)
		harness.beginRestoration(1)

		staleCallback(staleSource, ReaderLegacyRestorationResult.Restored)
		ReaderLegacyInventorySource.entries.filterNot { it == staleSource }.forEach { source ->
			harness.restorationCallbacks[1].getValue(source)(
				source,
				ReaderLegacyRestorationResult.Restored
			)
		}

		assertEquals(ReaderSessionActivationState.RestoringLegacy, harness.coordinator.state)
		assertEquals(1, harness.commitCount)
		harness.restorationCallbacks[1].getValue(staleSource)(
			staleSource,
			ReaderLegacyRestorationResult.Restored
		)
		assertEquals(ReaderSessionActivationState.Legacy, harness.coordinator.state)
		assertEquals(2, harness.commitCount)
	}

	@Test
	fun staleRestorationDeadlineCannotBlockTheNextActivationAttempt() {
		val harness = TwoAttemptRestorationHarness()
		harness.beginRestoration(0)
		val staleDeadline = harness.deadlineCallbacks.single()
		harness.completeRestoration(0)
		harness.beginRestoration(1)

		staleDeadline()

		assertEquals(ReaderSessionActivationState.RestoringLegacy, harness.coordinator.state)
		harness.completeRestoration(1)
		assertEquals(ReaderSessionActivationState.Legacy, harness.coordinator.state)
		assertEquals(2, harness.commitCount)
	}

	@Test
	fun restorationDeadlineDuringCommitCannotBeOverwrittenByAppliedResult() {
		lateinit var harness: TwoAttemptRestorationHarness
		harness = TwoAttemptRestorationHarness {
			harness.deadlineCallbacks.single().invoke()
		}
		harness.beginRestoration(0)

		harness.completeRestoration(0)

		assertEquals(1, harness.commitCount)
		assertEquals(ReaderSessionActivationState.ActivationBlocked, harness.coordinator.state)
	}

	@Test
	fun releaseOnlyDuringCommitRemainsPermanentAfterAppliedResult() {
		lateinit var harness: TwoAttemptRestorationHarness
		harness = TwoAttemptRestorationHarness {
			harness.coordinator.closeToReleaseOnly()
		}
		harness.beginRestoration(0)

		harness.completeRestoration(0)

		assertEquals(1, harness.commitCount)
		assertEquals(ReaderSessionActivationState.ReleaseOnly, harness.coordinator.state)
		assertIs<ReaderActivationInstallResult.Rejected>(
			harness.coordinator.activateForTest(
				completeActivatedSessionPorts(),
				ReaderInitialPresentationInputLease.ChromeOnly
			)
		)
		assertEquals(ReaderSessionActivationState.ReleaseOnly, harness.coordinator.state)
	}

	@Test
	fun task6TimerExposesTransferSnapshotWithoutEnablingCoordinatorClock() {
		val methodNames = ReaderTask6FactOnlyTimerPort::class.java.methods.map { it.name }.toSet()
		assertTrue("snapshotForTask7Transfer" in methodNames)
		assertTrue(runCatching {
			Class.forName(
				"paige.navic.ui.screens.reader.ReaderTask6FactOnlyTimerTransferSnapshot"
			)
		}.isSuccess)
	}

	@Test
	fun activatedDispatcherPublishesCleanupKeyedCancellationOutcomes() {
		listOf("Applied", "Rejected", "Threw").forEachIndexed { index, expectedOutcome ->
			val testPorts = completeActivatedSessionPorts()
			var routed: ReaderTransitionCommand.CancelOwnedWork? = null
			val resources = object : ReaderTransitionResourcePort by requireNotNull(testPorts.resources) {
				override fun cancelOwnedWork(
					command: ReaderTransitionCommand.CancelOwnedWork
				): ReaderPortCommandResult {
					routed = command
					return when (expectedOutcome) {
						"Applied" -> ReaderPortCommandResult.Accepted
						"Rejected" -> ReaderPortCommandResult.Rejected(
							ReaderTransitionFailureReason.PortRejected
						)
						else -> error("bounded cancellation throw")
					}
				}
			}
			val dispatcher = ReaderActivatedTransitionPorts(
				productionActivatedSessionPorts(testPorts.copy(resources = resources))
			)
			val binding = ReaderPresentationBinding(
				"fixture", 2L, 3L, 5L,
				ReaderDestinationCommitIdentity("fixture", 1L),
				7L, 11L, 13L
			)
			val transitionId = ReaderTransitionId(
				readerSessionGeneration = 17L,
				coordinatorEpoch = 19L,
				sequence = 1L,
				operation = ReaderTransitionOperation.BootstrapNativePage,
				expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
			)
			val cleanupKey = paige.navic.reader.ReaderReleaseOnlyCleanupKey(
				readerSessionGeneration = 17L,
				coordinatorEpoch = 19L,
				cleanupId = paige.navic.reader.ReaderReleaseOnlyCleanupId(23L + index),
				generation = paige.navic.reader.ReaderReleaseOnlyCleanupGeneration(29L + index)
			)
			val command = ReaderTransitionCommand.CancelOwnedWork(transitionId, cleanupKey)
			val callbackFacts = mutableListOf<paige.navic.reader.ReaderTransitionFact>()

			val dispatch = runCatching { dispatcher.issue(command, callbackFacts::add) }

			assertNull(dispatch.exceptionOrNull())
			assertEquals(command, routed)
			val outcomeFact = callbackFacts.single()
			assertEquals("OwnedWorkCancellationCompleted", outcomeFact::class.simpleName)
			val factClass = outcomeFact.javaClass
			assertEquals(cleanupKey, factClass.getMethod("getCleanupKey").invoke(outcomeFact))
			assertEquals(
				expectedOutcome,
				requireNotNull(
					factClass.getMethod("getOutcome").invoke(outcomeFact)
				).toString()
			)
		}
	}

	@Test
	fun activatedDispatcherRoutesEveryPhysicalCommandToExactProductionAdapter() {
		val base = completeActivatedSessionPorts()
		val routed = mutableListOf<ReaderTransitionCommand>()
		val resources = object : ReaderTransitionResourcePort by requireNotNull(base.resources) {
			override fun release(
				command: ReaderTransitionCommand.ReleaseResource,
				onFact: (paige.navic.reader.ReaderTransitionFact.ResourceReleased) -> Unit
			): ReaderPortCommandResult {
				routed += command
				return ReaderPortCommandResult.Accepted
			}
		}
		val frame = object : ReaderFramePresentationPort {
			override fun prepareTarget(
				command: ReaderTransitionCommand.PrepareFrameTarget,
				onFact: (paige.navic.reader.ReaderTransitionFact) -> Unit
			): ReaderPortCommandResult {
				routed += command
				return ReaderPortCommandResult.Accepted
			}

			override fun present(
				command: ReaderTransitionCommand.RequestFramePresentation,
				onFact: (paige.navic.reader.ReaderTransitionFact) -> Unit
			): ReaderPortCommandResult {
				routed += command
				return ReaderPortCommandResult.Accepted
			}
		}
		val testPorts = base.copy(
			materialAllocation = ReaderMaterialGenerationAllocationPort { command, _ ->
				routed += command
				ReaderPortCommandResult.Accepted
			},
			raster = ReaderActivatedRasterPreparationPort { command, _ ->
				routed += command
				ReaderPortCommandResult.Accepted
			},
			deck = ReaderActivatedDeckPort { command, _ ->
				routed += command
				ReaderPortCommandResult.Accepted
			},
			frame = frame,
			resources = resources
		)
		val production = productionActivatedSessionPorts(testPorts)
		val dispatcher = ReaderActivatedTransitionPorts(production)
		val binding = ReaderPresentationBinding(
			"fixture", 2L, 3L, 5L,
			ReaderDestinationCommitIdentity("fixture", 1L),
			7L, 11L, 13L
		)
		val id = ReaderTransitionId(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			sequence = 1L,
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
		)
		val registration = paige.navic.reader.ReaderTransitionResourceRegistration(
			paige.navic.reader.ReaderTransitionResourceKey(
				id,
				ReaderTransitionResourceKind.FrameHandoff,
				41L
			),
			paige.navic.reader.ReaderResourceRetirementOrder(17L, 19L, 43L)
		)
		val specification = paige.navic.reader.ReaderTransitionFrameTargetSpecification.ShellCover(
			transitionId = id,
			readerSessionGeneration = 17L,
			publicationGeneration = binding.publicationGeneration,
			binding = binding,
			hostToken = paige.navic.reader.ReaderShellCoverHostToken(47L),
			coverGeneration = 53L,
			viewportGeneration = 59L,
			geometry = paige.navic.reader.ReaderTransitionFrameGeometry(59L, 61L, 0, 0, 1200, 800),
			requestSequence = 67L
		)
		val target = paige.navic.reader.ReaderTransitionFrameTarget.ShellCover(
			paige.navic.reader.ReaderTransitionFrameTargetHandle(17L, binding.publicationGeneration, 71L),
			specification,
			registration
		)
		val commands = listOf(
			ReaderTransitionCommand.AllocateMaterialBinding(id, binding),
			ReaderTransitionCommand.RequestRasterPreparation(id, binding),
			ReaderTransitionCommand.ReserveDeck(
				id,
				binding,
				paige.navic.reader.ReaderTransitionDeckRole.Initial
			),
			ReaderTransitionCommand.PrepareFrameTarget(id, specification, registration),
			ReaderTransitionCommand.RequestFramePresentation(id, target),
			ReaderTransitionCommand.ReleaseResource(
				issuer = paige.navic.reader.ReaderResourceReleaseIssuer.Transition(id),
				identity = paige.navic.reader.ReaderReleaseCommandIdentity(
					paige.navic.reader.ReaderPhysicalReleaseAttemptId.fromLedger(1L),
					registration
				)
			)
		)

		commands.forEach { dispatcher.issue(it) {} }

		assertEquals(commands, routed)
		assertTrue(dispatcher.semanticCommand === production.semantic)
		assertTrue(dispatcher.ownerAndInputPublication === production.ownerAndInput)
		assertTrue(dispatcher.task6FactOnlyTimer === production.factOnlyTimer)
		assertFalse(
			dispatcher.clock is AndroidReaderTransitionClock,
			"Activated dispatch must not re-enable an independent coordinator clock"
		)
		assertTrue(
			runCatching { dispatcher.clock.schedule(1L) {} }.isFailure,
			"Only the retained Task 6 fact timer may schedule activated deadlines"
		)
	}

	@Test
	fun activatedDispatcherConvertsTargetPreparationRejectionToExactTypedFact() {
		val binding = ReaderPresentationBinding(
			"fixture", 2L, 3L, 5L,
			ReaderDestinationCommitIdentity("fixture", 1L),
			7L, 11L, 13L
		)
		val id = ReaderTransitionId(
			readerSessionGeneration = 17L,
			coordinatorEpoch = 19L,
			sequence = 1L,
			operation = ReaderTransitionOperation.ShellCoverCommit,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
		)
		val registration = paige.navic.reader.ReaderTransitionResourceRegistration(
			paige.navic.reader.ReaderTransitionResourceKey(
				id,
				ReaderTransitionResourceKind.FrameHandoff,
				41L
			),
			paige.navic.reader.ReaderResourceRetirementOrder(17L, 19L, 43L)
		)
		val specification = paige.navic.reader.ReaderTransitionFrameTargetSpecification.ShellCover(
			transitionId = id,
			readerSessionGeneration = 17L,
			publicationGeneration = binding.publicationGeneration,
			binding = binding,
			hostToken = paige.navic.reader.ReaderShellCoverHostToken(47L),
			coverGeneration = 53L,
			viewportGeneration = 59L,
			geometry = paige.navic.reader.ReaderTransitionFrameGeometry(59L, 61L, 0, 0, 1200, 800),
			requestSequence = 67L
		)
		val command = ReaderTransitionCommand.PrepareFrameTarget(id, specification, registration)
		val rejected = ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.PortRejected)
		val base = completeActivatedSessionPorts()
		val frame = object : ReaderFramePresentationPort {
			override fun prepareTarget(
				command: ReaderTransitionCommand.PrepareFrameTarget,
				onFact: (paige.navic.reader.ReaderTransitionFact) -> Unit
			) = rejected

			override fun present(
				command: ReaderTransitionCommand.RequestFramePresentation,
				onFact: (paige.navic.reader.ReaderTransitionFact) -> Unit
			) = ReaderPortCommandResult.Accepted
		}
		val dispatcher = ReaderActivatedTransitionPorts(
			productionActivatedSessionPorts(base.copy(frame = frame))
		)
		val facts = mutableListOf<paige.navic.reader.ReaderTransitionFact>()

		dispatcher.issue(command, facts::add)

		assertTrue(
			facts == listOf<paige.navic.reader.ReaderTransitionFact>(
				paige.navic.reader.ReaderTransitionFact.FrameTargetPreparationRejected(
					id,
					specification,
					registration,
					ReaderTransitionFailureReason.PortRejected
				)
			),
			"Frame allocation rejection must emit one bounded preparation failure"
		)
	}

	@Test
	fun productionCompositionContainsNoShadowOrNoOpActivatedPort() {
		val production = runCatching {
			Class.forName(
				"paige.navic.ui.screens.reader.ReaderProductionActivatedSessionPorts"
			)
		}.getOrElse { throw AssertionError("production capability package is absent", it) }
		assertTrue(production.declaredConstructors.all {
			Modifier.isPrivate(it.modifiers) || it.isSynthetic
		})
		assertEquals(
			setOf(
				"gateway",
				"semantic",
				"materialAllocation",
				"raster",
				"deck",
				"frame",
				"ownerAndInput",
				"inputSafety",
				"resources",
				"releaseSink",
				"lifecycleFacts",
				"factOnlyTimer"
			),
			production.declaredFields.map { it.name }.filterNot {
				it == "Companion" || it == "productionCapability" || it == "\$stable"
			}.toSet()
		)
		assertTrue(runCatching {
			Class.forName("paige.navic.ui.screens.reader.ReaderActivatedNoOpPorts")
		}.isFailure)
	}

	@Test
	fun postInstallLegacyConsequenceWriterIsUnreachable() {
		val production = runCatching {
			Class.forName(
				"paige.navic.ui.screens.reader.ReaderProductionActivatedSessionPorts"
			)
		}.getOrElse { throw AssertionError("production capability package is absent", it) }
		assertTrue(
			production.declaredConstructors.all { constructor ->
				constructor.parameterTypes.none { it == Boolean::class.javaPrimitiveType }
			}
		)
		assertTrue(
			production.declaredMethods.none { it.name == "complete" },
			"production package cannot expose a fixture completeness factory"
		)
		val host = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		assertTrue(host.contains("attachShadow("))
		assertTrue(host.contains("observeLegacyPresentationEvent("))
		assertFalse(host.contains("installActivatedSession("))
		assertFalse(host.contains("attachActivated("))
	}

	@Test
	fun productionCompositionRemainsShadowWithoutActiveCoordinatorInstallation() {
		val host = File(
			"src/androidMain/kotlin/paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
		).readText()
		assertTrue(host.contains("attachShadow("))
		assertFalse(host.contains("ReaderSessionActivationCoordinator("))
		assertFalse(host.contains("ReaderActivatedTransitionPorts("))
		assertFalse(host.contains("ReaderTransitionMode.Active"))
		assertFalse(host.contains("installActivatedSession("))
	}

	@Test
	fun activationBaselineWrappersRenderAndHashOnlyRedactedConstants() {
		val first = adoptedDecisionForPrivacy(307L, 311L)
		val second = adoptedDecisionForPrivacy(313L, 317L)
		val checkpoint = activationCheckpoint(
			ReaderLegacyFreezeToken(331L),
			ReaderPresentationFrameOwner.Neutral,
			null,
			null
		)
		val journal = paige.navic.reader.ReaderTransitionJournal(
			committed = paige.navic.reader.ReaderCommittedPresentation.Initial(first.origin)
		)
		val snapshot = ReaderActivatedSessionSnapshot(
			portAuthority = ReaderActivatedSessionPortAuthority.Production(
				productionActivatedSessionPorts(completeActivatedSessionPorts())
			),
			initialDecision = first,
			reservedNeutralBootstrap = null,
			journal = journal,
			releaseLedger = ReaderTransitionReleaseLedger()
		)

		val firstTimer = ReaderTask6ReleaseOnlyTimerRegistration(
			ReaderTask6FactOnlyTimerRegistrationId(337L),
			paige.navic.reader.ReaderReleaseOnlyCleanupKey(
				3L,
				5L,
				paige.navic.reader.ReaderReleaseOnlyCleanupId(1L),
				paige.navic.reader.ReaderReleaseOnlyCleanupGeneration(1L)
			),
			ReaderLegacyPhysicalIdentity(
				ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(347L)),
				ReaderLegacyInventorySource.DeadlineRegistration,
				ReaderLegacySourceLocalOpaqueToken(349L)
			),
			353L
		)
		val secondTimer = firstTimer.copy(
			id = ReaderTask6FactOnlyTimerRegistrationId(359L),
			expiresAtMillis = 367L
		)

		assertEquals("ReaderLegacyRestorationCheckpoint(<redacted>)", checkpoint.toString())
		assertEquals("ReaderAdoptedPredecessorSeed(<redacted>)", first.adoptedSeed.toString())
		assertEquals(
			"ReaderImportedLegacyResourceRegistration(<redacted>)",
			first.adoptedResource.toString()
		)
		assertEquals("ReaderInitialActivationDecision(<redacted>)", first.toString())
		assertEquals("ReaderActivatedSessionSnapshot(<redacted>)", snapshot.toString())
		assertTrue(
			firstTimer.toString() ==
				"ReaderTask6ReleaseOnlyTimerRegistration(<redacted>)",
			"Release-only timer rendering must be constant and redacted"
		)
		assertTrue(
			firstTimer.hashCode() == secondTimer.hashCode(),
			"Release-only timer hashing must not expose exact timer fields"
		)
		assertEquals(first.adoptedSeed.hashCode(), second.adoptedSeed.hashCode())
		assertEquals(first.adoptedResource.hashCode(), second.adoptedResource.hashCode())
		assertEquals(first.hashCode(), second.hashCode())
		assertEquals(
			ReaderInitialActivationSanitizedProjection(
				originKind = ReaderInitialOriginKind.AdoptedPredecessor,
				ownerKind = ReaderInitialOriginOwnerKind.ShellCover,
				resourceKind = ReaderTransitionResourceKind.FrameHandoff,
				requestedLeaseKind = ReaderInitialActivationLeaseKind.ChromeOnly,
				physicalLeaseKind = ReaderInitialActivationLeaseKind.ChromeOnly,
				hasNeutralBootstrapReservation = false,
				activationState = null
			),
			first.sanitizedProjection,
			"Activation decision diagnostics must expose only finite adopted categories"
		)
		assertEquals(
			first.sanitizedProjection,
			second.sanitizedProjection,
			"Distinct opaque identities must have the same finite activation projection"
		)
		assertEquals(
			ReaderSessionActivationState.Activated,
			snapshot.sanitizedProjection.activationState,
			"Snapshot diagnostics must expose only the finite activation state"
		)
		assertTrue(
			neutralDecision().sanitizedProjection.run {
				originKind == ReaderInitialOriginKind.Neutral &&
					ownerKind == null &&
					resourceKind == null &&
					hasNeutralBootstrapReservation
			},
			"Neutral diagnostics must expose no adopted identity categories"
		)
	}

	@Test
	fun closeFromEveryPhasePublishesPermanentReleaseOnlyBeforeCancellation() {
		ReaderSessionActivationState.entries.forEach { initial ->
			val trace = mutableListOf<String>()
			val stateMachine = ReaderSessionActivationStateMachine(initial) { trace += "cancel" }
			stateMachine.closeToReleaseOnly { trace += "publish" }
			stateMachine.closeToReleaseOnly { trace += "publish-again" }

			assertEquals(ReaderSessionActivationState.ReleaseOnly, stateMachine.state)
			assertEquals(
				if (initial == ReaderSessionActivationState.ReleaseOnly) emptyList() else listOf("publish", "cancel"),
				trace
			)
			assertTrue(stateMachine.accepts(ReaderReleaseOnlyIngress.ResourceObservation))
			assertTrue(stateMachine.accepts(ReaderReleaseOnlyIngress.ReleaseConfirmation))
			assertTrue(stateMachine.accepts(ReaderReleaseOnlyIngress.ReleaseCommand))
			assertFalse(stateMachine.accepts(ReaderReleaseOnlyIngress.SemanticConsequence))
			assertFalse(stateMachine.accepts(ReaderReleaseOnlyIngress.Restoration))
		}
	}

	private inner class TwoAttemptRestorationHarness(
		private val onCommit: () -> Unit = {}
	) {
		private val tokens = listOf(ReaderLegacyFreezeToken(401L), ReaderLegacyFreezeToken(409L))
		private val rows = tokens.mapIndexed { index, token ->
			listOf(
				activationDrainRow(
					token,
					ReaderLegacyInventorySource.RasterPreparation,
					421L + index * 10L
				),
				activationDrainRow(
					token,
					ReaderLegacyInventorySource.Deck,
					423L + index * 10L
				)
			)
		}
		private var currentAttempt = -1
		private var snapshotSequence = 0L
		private val drainCallbacks = arrayOfNulls<(ReaderLegacyPhysicalIdentity) -> Unit>(2)
		val restorationCallbacks = List(2) {
			linkedMapOf<ReaderLegacyInventorySource, (
				ReaderLegacyInventorySource,
				ReaderLegacyRestorationResult
			) -> Unit>()
		}
		val deadlineCallbacks = mutableListOf<() -> Unit>()
		var commitCount = 0
			private set
		val coordinator: ReaderSessionActivationCoordinator

		init {
			val legacy = object : ReaderLegacyFreezeAndInventoryPort {
				override fun freeze(): ReaderLegacyFreezeToken {
					currentAttempt += 1
					return tokens[currentAttempt]
				}

				override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) =
					activationCheckpoint(token, ReaderPresentationFrameOwner.Neutral, null, null)

				override fun inventory(token: ReaderLegacyFreezeToken) =
					ReaderLegacyResourceInventory.Complete(
						token,
						++snapshotSequence,
						1L,
						ReaderLegacyInventorySource.entries.toSet(),
						rows[currentAttempt]
					)

				override fun drain(
					token: ReaderLegacyFreezeToken,
					physicalIdentity: ReaderLegacyPhysicalIdentity,
					onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
				): ReaderPortCommandResult = if (physicalIdentity == rows[currentAttempt].first().physicalIdentity) {
					drainCallbacks[currentAttempt] = onConfirmed
					ReaderPortCommandResult.Accepted
				} else {
					ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
				}

				override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) =
					ReaderPortCommandResult.Accepted

				override fun restoreFromActivationCheckpoint(
					checkpoint: ReaderLegacyRestorationCheckpoint,
					source: ReaderLegacyInventorySource,
					onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
				): ReaderPortCommandResult {
					val attempt = tokens.indexOf(checkpoint.freezeToken)
					restorationCallbacks[attempt][source] = onConfirmed
					return ReaderPortCommandResult.Accepted
				}

				override fun commitRestoredLegacy(
					checkpoint: ReaderLegacyRestorationCheckpoint
				): ReaderLegacyCommitRestoredResult {
					commitCount += 1
					onCommit()
					return ReaderLegacyCommitRestoredResult.Applied
				}
			}
			coordinator = ReaderSessionActivationCoordinator(
				readerSessionGeneration = 3L,
				coordinatorEpoch = 5L,
				legacy = legacy,
				installationBarrier = ReaderActivatedSessionInstallationBarrier(
					ReaderActivatedSessionSnapshotStore()
				),
				narrowInitialLease = { it },
				reserveNeutralBootstrap = {
					ReaderReservedNeutralBootstrapRequest(
						paige.navic.reader.ReaderSemanticRequestHandle((currentAttempt + 1).toLong()),
						3L
					)
				},
				restorationDeadline = object : ReaderActivationRestorationDeadlinePort {
					override fun schedule(
						delayMillis: Long,
						onExpired: () -> Unit
					): ReaderActivationRestorationDeadlineRegistration {
						deadlineCallbacks += onExpired
						return ReaderActivationRestorationDeadlineRegistration {}
					}
				}
			)
		}

		fun beginRestoration(attempt: Int) {
			assertEquals(
				ReaderActivationInstallResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed),
				coordinator.activateForTest(
					completeActivatedSessionPorts(),
					ReaderInitialPresentationInputLease.ChromeOnly
				)
			)
			assertEquals(ReaderSessionActivationState.ActivationBlocked, coordinator.state)
			drainCallbacks[attempt]?.invoke(rows[attempt].first().physicalIdentity)
			assertEquals(ReaderSessionActivationState.RestoringLegacy, coordinator.state)
		}

		fun completeRestoration(attempt: Int) {
			ReaderLegacyInventorySource.entries.forEach { source ->
				restorationCallbacks[attempt].getValue(source)(
					source,
					ReaderLegacyRestorationResult.Restored
				)
			}
		}
	}

	private fun activationDrainRow(
		token: ReaderLegacyFreezeToken,
		source: ReaderLegacyInventorySource,
		opaqueId: Long
	) = ReaderFrozenLegacyResource(
		freezeToken = token,
		physicalIdentity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(3L, token),
			source,
			ReaderLegacySourceLocalOpaqueToken(opaqueId)
		),
		kind = when (source) {
			ReaderLegacyInventorySource.Deck -> ReaderTransitionResourceKind.Deck
			ReaderLegacyInventorySource.FrameOrHandoff -> ReaderTransitionResourceKind.FrameHandoff
			else -> ReaderTransitionResourceKind.Raster
		},
		binding = null,
		visibleOwner = null,
		origin = ReaderLegacyResourceOrigin.Owned,
		state = ReaderLegacyResourceState.Running,
		mayBeCommittedPredecessor = false
	)

	private fun neutralBootstrapReservation(readerSessionGeneration: Long) =
		ReaderReservedNeutralBootstrapRequest(
			paige.navic.reader.ReaderSemanticRequestHandle(readerSessionGeneration),
			readerSessionGeneration
		)

	private data class ActivationLeaseMismatchCase(
		val owner: ReaderPresentationFrameOwner,
		val checkpointRequestedLease: ReaderInitialPresentationInputLease,
		val checkpointPhysicalLease: ReaderInitialPresentationInputLease = checkpointRequestedLease,
		val activationLease: ReaderInitialPresentationInputLease,
		val physicalLease: ReaderInitialPresentationInputLease
	)

	private data class ActivationLeaseMismatchResult(
		val installResult: ReaderActivationInstallResult,
		val drainCount: Int,
		val unfreezeCount: Int,
		val state: ReaderSessionActivationState,
		val store: ReaderActivatedSessionSnapshotStore
	)

	private fun activateLeaseMismatch(
		binding: ReaderPresentationBinding,
		case: ActivationLeaseMismatchCase
	): ActivationLeaseMismatchResult {
		val token = ReaderLegacyFreezeToken(271L)
		val predecessorIdentity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(3L, token),
			ReaderLegacyInventorySource.Deck,
			ReaderLegacySourceLocalOpaqueToken(277L)
		)
		val predecessor = ReaderFrozenLegacyResource(
			freezeToken = token,
			physicalIdentity = predecessorIdentity,
			kind = requireNotNull(readerAdoptedResourceKindFor(case.owner)),
			binding = binding,
			visibleOwner = case.owner,
			origin = ReaderLegacyResourceOrigin.Owned,
			state = ReaderLegacyResourceState.Visible,
			mayBeCommittedPredecessor = true
		)
		val stale = ReaderFrozenLegacyResource(
			freezeToken = token,
			physicalIdentity = ReaderLegacyPhysicalIdentity(
				ReaderLegacyPhysicalDomain(3L, token),
				ReaderLegacyInventorySource.RasterPreparation,
				ReaderLegacySourceLocalOpaqueToken(281L)
			),
			kind = ReaderTransitionResourceKind.Raster,
			binding = null,
			visibleOwner = null,
			origin = ReaderLegacyResourceOrigin.Owned,
			state = ReaderLegacyResourceState.Running,
			mayBeCommittedPredecessor = false
		)
		val checkpoint = activationCheckpoint(
			token,
			case.owner,
			binding,
			predecessorIdentity
		).copy(
			requestedLease = case.checkpointRequestedLease,
			physicalLease = case.checkpointPhysicalLease
		)
		var snapshotSequence = 0L
		var drainCount = 0
		var unfreezeCount = 0
		val legacy = object : ReaderLegacyFreezeAndInventoryPort {
			override fun freeze() = token
			override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = checkpoint
			override fun inventory(token: ReaderLegacyFreezeToken) =
				ReaderLegacyResourceInventory.Complete(
					token,
					++snapshotSequence,
					1L,
					ReaderLegacyInventorySource.entries.toSet(),
					listOf(predecessor, stale)
				)
			override fun drain(
				token: ReaderLegacyFreezeToken,
				physicalIdentity: ReaderLegacyPhysicalIdentity,
				onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit
			): ReaderPortCommandResult {
				drainCount += 1
				onConfirmed(physicalIdentity)
				return ReaderPortCommandResult.Accepted
			}
			override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken): ReaderPortCommandResult {
				unfreezeCount += 1
				return ReaderPortCommandResult.Accepted
			}
			override fun restoreFromActivationCheckpoint(
				checkpoint: ReaderLegacyRestorationCheckpoint,
				source: ReaderLegacyInventorySource,
				onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit
			): ReaderPortCommandResult {
				onConfirmed(source, ReaderLegacyRestorationResult.Restored)
				return ReaderPortCommandResult.Accepted
			}
			override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
				ReaderLegacyCommitRestoredResult.Applied
		}
		val store = ReaderActivatedSessionSnapshotStore()
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(store),
			narrowInitialLease = { case.physicalLease },
			fenceAdoptedCurlGesture = { true }
		)
		val installResult = coordinator.activateForTest(
			completeActivatedSessionPorts(),
			case.activationLease
		)
		return ActivationLeaseMismatchResult(
			installResult = installResult,
			drainCount = drainCount,
			unfreezeCount = unfreezeCount,
			state = coordinator.state,
			store = store
		)
	}

	private fun activationCheckpoint(
		token: ReaderLegacyFreezeToken,
		owner: ReaderPresentationFrameOwner,
		binding: ReaderPresentationBinding?,
		identity: ReaderLegacyPhysicalIdentity?
	) = ReaderLegacyRestorationCheckpoint(
		id = ReaderLegacyRestorationCheckpointId(1L),
		freezeToken = token,
		routeGeneration = 1L,
		completedSources = ReaderLegacyInventorySource.entries.toSet(),
		restartHandles = ReaderLegacyInventorySource.entries.associateWith {
			ReaderLegacySourceRestartHandle(it, it.ordinal.toLong() + 1L)
		},
		initialOwner = owner,
		initialBinding = binding,
		initialPhysicalIdentity = identity,
		initialResourceKind = readerAdoptedResourceKindFor(owner),
		initialProvenance = if (owner == ReaderPresentationFrameOwner.Neutral) {
			null
		} else ReaderTransitionResourceProvenance.AdoptedLegacy,
		requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
		physicalLease = ReaderInitialPresentationInputLease.ChromeOnly
	)

	private fun activationBinding() = ReaderPresentationBinding(
		foliateSessionId = "fixture",
		publicationGeneration = 2L,
		viewportGeneration = 3L,
		profileGeneration = 5L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture", 1L),
		preparationGeneration = 7L,
		rasterGeneration = 11L,
		textureGeneration = 13L
	)

	private fun adoptedDecisionForPrivacy(
		seedValue: Long,
		opaqueResourceId: Long
	): ReaderInitialActivationDecision {
		val binding = activationBinding()
		val owner = ReaderPresentationFrameOwner.ShellCover(
			ReaderShellCoverCommitProof(
				ReaderPresentationToken(seedValue),
				binding,
				seedValue + 1L,
				seedValue + 2L,
				1200,
				800
			)
		)
		val physicalIdentity = ReaderLegacyPhysicalIdentity(
			ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(seedValue + 3L)),
			ReaderLegacyInventorySource.FrameOrHandoff,
			ReaderLegacySourceLocalOpaqueToken(seedValue + 4L)
		)
		val seedId = ReaderAdoptedPredecessorSeedId.fromValidatedImport(seedValue)
		val registration = paige.navic.reader.ReaderTransitionResourceRegistration(
			paige.navic.reader.ReaderTransitionResourceKey(
				paige.navic.reader.ReaderTransitionResourceOwnerId.AdoptedPredecessor(seedId),
				ReaderTransitionResourceKind.FrameHandoff,
				opaqueResourceId
			),
			paige.navic.reader.ReaderResourceRetirementOrder(3L, 5L, 1L)
		)
		val seed = ReaderAdoptedPredecessorSeed(
			id = seedId,
			physicalIdentity = physicalIdentity,
			resourceKind = ReaderTransitionResourceKind.FrameHandoff,
			binding = binding,
			owner = owner,
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L
		)
		return ReaderInitialActivationDecision(
			origin = ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor(
				seedId = seedId,
				readerSessionGeneration = 3L,
				coordinatorEpoch = 5L,
				owner = owner,
				binding = binding,
				resource = registration,
				requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
				physicalLease = ReaderInitialPresentationInputLease.ChromeOnly
			),
			adoptedSeed = seed,
			adoptedResource = ReaderImportedLegacyResourceRegistration(
				physicalIdentity,
				registration
			),
			neutralBootstrapReservation = null
		)
	}

	private fun neutralDecision(): ReaderInitialActivationDecision {
		val origin = ReaderInitialCommittedPresentationOrigin.Neutral(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
			physicalLease = ReaderInitialPresentationInputLease.ChromeOnly
		)
		return ReaderInitialActivationDecision(
			origin = origin,
			adoptedSeed = null,
			adoptedResource = null,
			neutralBootstrapReservation = ReaderReservedNeutralBootstrapRequest(
				paige.navic.reader.ReaderSemanticRequestHandle(1L),
				3L
			)
		)
	}

	private fun assertTrueAdoptedOrigin(
		decision: ReaderInitialActivationDecision
	): ReaderInitialCommittedPresentationOrigin.AdoptedPredecessor = assertIs(decision.origin)
}

private fun productionActivatedSessionPorts(
	testPorts: ReaderTestActivatedSessionPorts
): ReaderProductionActivatedSessionPorts {
	val capabilityClass = Class.forName(
		"paige.navic.ui.screens.reader.ReaderNativeHostProductionActivatedPortCapability"
	)
	val capability = capabilityClass.getDeclaredField("INSTANCE").run {
		isAccessible = true
		get(null) as ReaderProductionActivatedPortCapability
	}
	return ReaderProductionActivatedSessionPorts.mint(
		gateway = requireNotNull(testPorts.gateway),
		semantic = requireNotNull(testPorts.semantic),
		materialAllocation = requireNotNull(testPorts.materialAllocation),
		raster = requireNotNull(testPorts.raster),
		deck = requireNotNull(testPorts.deck),
		frame = requireNotNull(testPorts.frame),
		ownerAndInput = requireNotNull(testPorts.ownerAndInput),
		inputSafety = requireNotNull(testPorts.inputSafety),
		resources = requireNotNull(testPorts.resources),
		releaseSink = requireNotNull(testPorts.releaseSink),
		lifecycleFacts = requireNotNull(testPorts.lifecycleFacts),
		factOnlyTimer = requireNotNull(testPorts.factOnlyTimer),
		productionCapability = capability
	)
}

private fun completeActivatedSessionPorts(): ReaderTestActivatedSessionPorts {
	val accepted = ReaderPortCommandResult.Accepted
	val resources = object : ReaderTransitionResourcePort, ReaderReleaseOnlySinkPort {
		override fun release(
			command: paige.navic.reader.ReaderTransitionCommand.ReleaseResource,
			onFact: (paige.navic.reader.ReaderTransitionFact.ResourceReleased) -> Unit
		) = accepted
		override fun releaseLegacy(
			command: paige.navic.reader.ReaderTransitionCommand.ReleaseResource,
			imported: ReaderImportedLegacyResourceRegistration,
			onConfirmed: (
				ReaderLegacyPhysicalIdentity,
				paige.navic.reader.ReaderTransitionFact.ResourceReleased
			) -> Unit
		) = accepted
		override fun cancelOwnedWork(
			command: paige.navic.reader.ReaderTransitionCommand.CancelOwnedWork
		) = accepted
		override fun observe(fact: paige.navic.reader.ReaderTransitionFact.ResourceObserved) = accepted
		override fun observeLegacy(imported: ReaderImportedLegacyResourceRegistration) = accepted
		override fun confirm(fact: paige.navic.reader.ReaderTransitionFact.ResourceReleased) = accepted
		override fun confirmLegacy(
			physicalIdentity: ReaderLegacyPhysicalIdentity,
			fact: paige.navic.reader.ReaderTransitionFact.ResourceReleased
		) = accepted
		override fun release(command: paige.navic.reader.ReaderTransitionCommand.ReleaseResource) = accepted
	}
	return ReaderTestActivatedSessionPorts(
		gateway = object : ReaderActivatedGatewayPort {
			override fun routeIntent(fact: paige.navic.reader.ReaderTransitionFact.Intent) = accepted
			override fun routeReceipt(receipt: paige.navic.reader.ReaderPresentationEventReceipt) = accepted
			override fun closeToReleaseOnly() = Unit
		},
		semantic = ReaderSemanticCommandPort { _, _, _ -> ReaderSemanticCommandResult.Accepted },
		materialAllocation = ReaderMaterialGenerationAllocationPort { _, _ -> accepted },
		raster = ReaderActivatedRasterPreparationPort { _, _ -> accepted },
		deck = ReaderActivatedDeckPort { _, _ -> accepted },
		frame = object : ReaderFramePresentationPort {
			override fun prepareTarget(
				command: paige.navic.reader.ReaderTransitionCommand.PrepareFrameTarget,
				onFact: (paige.navic.reader.ReaderTransitionFact) -> Unit
			) = accepted
			override fun present(
				command: paige.navic.reader.ReaderTransitionCommand.RequestFramePresentation,
				onFact: (paige.navic.reader.ReaderTransitionFact) -> Unit
			) = accepted
		},
		ownerAndInput = object : ReaderOwnerAndInputPublicationPort {
			override fun publish(command: paige.navic.reader.ReaderTransitionCommand.CommitOwnerAndInputLease) =
				paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied(
					command.transitionId,
					paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Successor(
						command.targetHandle,
						command.preparedFrameResource
					),
					command.owner,
					command.binding,
					command.requestedLease,
					command.publicationIdentity
				)
			override fun publish(command: paige.navic.reader.ReaderTransitionCommand.PublishRetainedOwnerAndInputLease) =
				paige.navic.reader.ReaderOwnerAndInputPublicationResult.Applied(
					command.transitionId,
					paige.navic.reader.ReaderOwnerAndInputPublicationSubject.Retained(
						command.retainedResource
					),
					command.retainedOwner,
					command.retainedBinding,
					command.requestedLease,
					command.publicationIdentity
				)
		},
		inputSafety = ReaderInputLeasePort { it },
		resources = resources,
		releaseSink = resources,
		lifecycleFacts = ReaderTask6LifecycleFactPort { accepted },
		factOnlyTimer = object : ReaderTask6FactOnlyTimerPort {
			override fun bindBeforeWork(
				transitionId: paige.navic.reader.ReaderTransitionId,
				onExpired: (paige.navic.reader.ReaderTransitionFact.DeadlineExpired) -> Unit
			): ReaderTask6FactOnlyTimerRegistration? = null
			override fun matchingProgress(
				registration: ReaderTask6FactOnlyTimerRegistration,
				nowMillis: Long
			) = accepted
			override fun snapshotForTask7Transfer(
				registration: ReaderTask6FactOnlyTimerRegistration
			): ReaderTask6FactOnlyTimerTransferSnapshot? = null
			override fun cancel(registration: ReaderTask6FactOnlyTimerRegistration) = accepted
		}
	)
}
