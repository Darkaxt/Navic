package paige.navic.ui.screens.reader

import android.os.Build
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Actual gateway/store/registry/executor protocol only, never Native or Foliate startup proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class ReaderNeutralBootstrapReservationHostTest {
    @Test fun closedReservationTransfersWithTheSoleSnapshotAndConsumesFirstAuthenticSequenceOnce() {
        val f = ReservationFixture()
        try {
            val reservation = assertNotNull(f.reserve())
            assertNull(f.reserve())
            assertNull(f.gateway.captureActivatedSemanticEpoch())
            assertTrue(f.gateway.captureSelectedSemanticEpoch() === f.candidate.epoch)
            assertEquals(0, f.store.atomicWriteCount)
            assertEquals(1, f.registry.activeHandleCount)
            assertTrue(f.dispatch(reservation) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertTrue(f.origins.isEmpty())
            assertTrue(f.install(reservation) == ReaderActivationInstallResult.Installed)
            assertEquals(1, f.store.atomicWriteCount)
            assertTrue(f.store.reservedNeutralBootstrap === reservation)
            assertTrue(f.gateway.isCurrentActivatedSemanticEpoch(f.candidate.epoch))
            assertTrue(f.facts.isEmpty() && f.origins.isEmpty())
            assertTrue(f.dispatch(reservation) == ReaderSemanticCommandResult.Accepted)
            val origin = assertNotNull(f.origins.singleOrNull())
            assertTrue(origin.transitionId.sequence == 1L && origin.transitionId.parent == null)
            assertTrue(origin.transitionId.operation == ReaderTransitionOperation.BootstrapNativePage)
            assertTrue(f.dispatch(reservation) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertEquals(1, f.origins.size)
            f.registrations.single().retire()
            assertEquals(0, f.executor.activeSlotCount)
            assertEquals(0, f.registry.activeHandleCount)
        } finally { f.close() }
    }

    @Test fun equalValuedForgedReservationCannotTransferOrConsumeTheOwnedCapability() {
        val f = ReservationFixture()
        try {
            val actual = assertNotNull(f.reserve())
            val copy = actual.copy()
            assertTrue(f.install(copy) is ReaderActivationInstallResult.Rejected)
            assertEquals(0, f.store.atomicWriteCount)
            assertFalse(f.gateway.isCurrentActivatedSemanticEpoch(f.candidate.epoch))
            assertTrue(f.dispatch(actual) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertTrue(f.origins.isEmpty())
        } finally { f.close() }
    }

    @Test fun reservedRequestCannotBeDrainedAsLegacyOrBorrowedByAnotherStore() {
        val f = ReservationFixture(legacyRequestCount = 1)
        try {
            val actual = assertNotNull(f.reserve())
            val legacyRows = f.registry.snapshotFrozenOwnership()
            assertEquals(1, legacyRows.size)
            assertEquals(1, f.executor.snapshotFrozenOwnership().size)
            assertEquals(2, f.registry.activeHandleCount)
            assertTrue(legacyRows.single().origin == ReaderLegacyResourceOrigin.Pending)
            val other = ReaderActivatedSessionSnapshotStore()
            val otherBarrier = ReaderActivatedSessionInstallationBarrier(other)
            assertTrue(otherBarrier.installTestActivatedSession(f.ports(), f.decision(actual)) is ReaderActivationInstallResult.Rejected)
            assertEquals(0, other.atomicWriteCount)
            assertTrue(f.dispatch(actual) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertTrue(f.origins.isEmpty())
        } finally { f.close() }
    }

    @Test fun rejectionDiscardRetiresOnlyTheExactReservationOnce() {
        val f = ReservationFixture()
        try {
            val actual = assertNotNull(f.reserve())
            assertFalse(f.registry.discardNeutralBootstrap(actual.copy()))
            assertTrue(f.registry.discardNeutralBootstrap(actual))
            assertFalse(f.registry.discardNeutralBootstrap(actual))
            assertEquals(0, f.registry.activeHandleCount)
            assertTrue(f.install(actual) is ReaderActivationInstallResult.Rejected)
            assertTrue(f.origins.isEmpty())
        } finally { f.close() }
    }

    @Test fun ordinaryAdmissionAndWrongFirstSequenceRemainClosed() {
        val f = ReservationFixture()
        try {
            val actual = assertNotNull(f.reserve())
            assertFailsWith<IllegalStateException> { f.registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted } }
            assertTrue(f.install(actual) == ReaderActivationInstallResult.Installed)
            val id = f.firstId().copy(sequence = 2L, parent = f.firstId().parentIdentity())
            assertTrue(f.dispatch(actual, id) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertTrue(f.origins.isEmpty())
            assertTrue(f.dispatch(actual) == ReaderSemanticCommandResult.Accepted)
        } finally { f.close() }
    }

    @Test fun preinstallCloseDiscardsOnlyActivationCustodyAndPreservesActualLegacyRestoration() {
        val f = ReservationFixture(legacyRequestCount = 1)
        try {
            val actual = assertNotNull(f.reserve())
            assertEquals(2, f.registry.activeHandleCount)
            f.candidate.close()
            assertNull(f.gateway.captureSelectedSemanticEpoch())
            assertEquals(1, f.registry.activeHandleCount)
            assertEquals(1, f.executor.snapshotFrozenOwnership().size)
            assertTrue(f.dispatch(actual) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertTrue(f.facts.isEmpty() && f.origins.isEmpty())
            assertEquals(ReaderPortCommandResult.Accepted, f.executor.restoreAfterTransitionActivation(f.domain))
            assertEquals(1, f.registry.activeHandleCount)
            assertEquals(0, f.store.atomicWriteCount)
        } finally { f.close() }
    }

    @Test fun authenticLegacyHandleCapacityRejectsReservationWithoutInstallingOrBorrowing() {
        val f = ReservationFixture(legacyRequestCount = ReaderMaximumPendingSemanticRequestHandles)
        try {
            assertNotNull(f.gateway.captureSelectedSemanticEpoch())
            repeat(2) { assertNull(f.reserve()) }
            assertEquals(ReaderMaximumPendingSemanticRequestHandles, f.registry.activeHandleCount)
            assertEquals(ReaderMaximumPendingSemanticRequestHandles, f.executor.snapshotFrozenOwnership().size)
            assertNull(f.gateway.captureActivatedSemanticEpoch())
            assertEquals(0, f.store.atomicWriteCount)
            assertTrue(f.facts.isEmpty() && f.origins.isEmpty())
        } finally { f.close() }
    }

    @Test fun wrongCandidateEpochCannotInstallAndReadOnlyValidationPreservesTheExactReservation() {
        val f = ReservationFixture()
        try {
            val actual = assertNotNull(f.reserve())
            val wrongDecision = f.decision(actual).copy(origin = ReaderInitialCommittedPresentationOrigin.Neutral(
                3L, 6L, ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.ChromeOnly
            ))
            val barrier = ReaderActivatedSessionInstallationBarrier(f.store)
            assertTrue(barrier.installTestActivatedSession(f.ports(), wrongDecision) is ReaderActivationInstallResult.Rejected)
            assertEquals(0, f.store.atomicWriteCount)
            assertEquals(1, f.registry.activeHandleCount)
            assertTrue(f.install(actual) == ReaderActivationInstallResult.Installed)
            assertTrue(f.dispatch(actual) == ReaderSemanticCommandResult.Accepted)
            assertEquals(1, f.origins.size)
        } finally { f.close() }
    }

    @Test fun closingCandidatePreventsLateConsumptionAndCannotBorrowReplacementAttachment() {
        val f = ReservationFixture()
        val reservation = assertNotNull(f.reserve())
        assertTrue(f.install(reservation) == ReaderActivationInstallResult.Installed)
        f.close()
        assertFalse(f.gateway.isCurrentActivatedSemanticEpoch(f.candidate.epoch))
        val replacementRegistry = ReaderSemanticExecutableRequestRegistry(3L)
        val replacement = f.gateway.attachActivated(enqueue = {},
            retireSemanticRequests = replacementRegistry::clear, registerSemanticRequest = replacementRegistry::register)
        try {
            val nextEpoch = assertNotNull(f.gateway.captureActivatedSemanticEpoch())
            assertFalse(nextEpoch.attachmentToken == f.candidate.epoch.attachmentToken)
            replacementRegistry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted }
            assertTrue(f.dispatch(reservation) is ReaderSemanticCommandResult.RejectedBeforeMutation)
            assertTrue(f.origins.isEmpty())
            assertEquals(1, replacementRegistry.activeHandleCount)
            assertEquals(0, f.registry.activeHandleCount)
            assertEquals(0, f.executor.activeSlotCount)
        } finally { replacement.close() }
    }
}

private class ReservationFixture(legacyRequestCount: Int = 0) {
    val store = ReaderActivatedSessionSnapshotStore()
    val registry = ReaderSemanticExecutableRequestRegistry(3L, activationStore = store)
    val executor = ReaderSemanticCommandExecutor(registry)
    val gateway = ReaderTransitionGateway()
    val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(7L))
    val facts = mutableListOf<ReaderTransitionFact>()
    val origins = mutableListOf<ReaderPresentationEventOrigin.SemanticCommand>()
    val registrations = mutableListOf<ReaderSemanticCommandRegistration>()
    private val reservedCandidate: ReaderTransitionGatewayCandidate?
    val candidate get() = checkNotNull(reservedCandidate)
    private var closed = false
    init {
        repeat(legacyRequestCount) { registry.register { _, _, _ -> ReaderSemanticExecutableResult.Accepted } }
        check(executor.freezeForTransitionActivation(domain) == ReaderPortCommandResult.Accepted)
        reservedCandidate = gateway.reserveActivatedCandidate(
            readerSessionGeneration = 3L, coordinatorEpoch = 5L, installationSource = store,
            enqueue = facts::add, enqueueReceipt = {}, registerSemanticRequest = registry::register,
            discardSemanticReservation = { registry.discardCandidateReservations() },
            retireSemanticRequests = executor::clear
        )
    }
    fun reserve() = reservedCandidate?.let { selected ->
        registry.reserveNeutralBootstrap(domain, selected) { origin, _, _ ->
            origins += origin
            ReaderSemanticExecutableResult.Accepted
        }
    }
    fun ports() = completeActivatedSessionPorts().copy(
        gateway = ReaderSnapshotBackedActivatedGatewayPort(candidate), semantic = executor
    )
    fun decision(reservation: ReaderReservedNeutralBootstrapRequest) = ReaderInitialActivationDecision(
        origin = ReaderInitialCommittedPresentationOrigin.Neutral(3L, 5L,
            ReaderInitialPresentationInputLease.ChromeOnly, ReaderInitialPresentationInputLease.ChromeOnly),
        adoptedSeed = null, adoptedResource = null, neutralBootstrapReservation = reservation
    )
    fun install(reservation: ReaderReservedNeutralBootstrapRequest) =
        ReaderActivatedSessionInstallationBarrier(store).installTestActivatedSession(ports(), decision(reservation))
    fun firstId(): ReaderTransitionId = checkNotNull(store.journal.reduce(
        ReaderTransitionFact.Intent(null, ReaderBootstrapNativePageIntent(checkNotNull(store.reservedNeutralBootstrap).handle))
    ).state.active).id
    fun dispatch(reservation: ReaderReservedNeutralBootstrapRequest, id: ReaderTransitionId? = null): ReaderSemanticCommandResult {
        val commandId = id ?: store.snapshot?.let { firstId() } ?: ReaderTransitionId(
            3L, 5L, 1L, ReaderTransitionOperation.BootstrapNativePage, ReaderExpectedPresentationBinding.FoliateAuthoritativeInitial(1L)
        )
        return executor.synchronize(ReaderTransitionCommand.RequestSemanticSynchronization(
            commandId, ReaderBootstrapNativePageIntent(reservation.handle), reservation.handle
        ), registrations::add, {})
    }
    fun close() {
        if (closed) return
        closed = true
        reservedCandidate?.close()
        executor.clear()
    }
}
