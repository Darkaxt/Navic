package paige.navic.ui.screens.reader

import android.os.Build
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Original lifecycle registry and authentic candidate, with a TEST port installation.
 * This verifies physical return/safety custody, not production composition or Foliate. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class ReaderFinalizedLifecycleCustodyHostTest {
    @Test fun installedSafetyDeliverySurvivesPermanentLegacyFinalization() {
        val f = Fixture()
        try {
            f.finalizeOriginal()
            f.delivery.observe(ReaderPresentationLifecycleEvent.VisibilityLost)
            assertNull(f.delivery.retry { error("Finalized safety cannot dispatch legacy receipts") })
            assertTrue(f.safetyKinds == listOf(ReaderTask6LifecycleSafetyKind.VisibilityLost),
                "The original finalized source must deliver the actual installed safety fact")
            assertFalse(f.delivery.hasPhysicalReturnsInFlight)
            assertTrue(f.delivery.snapshotFrozenOwnership().isEmpty())
            assertIs<ReaderPortCommandResult.Rejected>(f.delivery.restoreAfterTransitionActivation(f.domain))
        } finally { f.close() }
    }

    @Test fun installedPhysicalReturnsRemainCountedWithoutReopeningLegacyRows() {
        val f = Fixture()
        try {
            f.finalizeOriginal()
            val outer = f.delivery.beginRegisteredRootReturn()
            assertTrue(outer != null, "Actual installed source return cannot disappear at legacy finalization")
            assertTrue(f.delivery.hasPhysicalReturnsInFlight)
            val inner = f.delivery.beginRegisteredRootReturn()
            assertTrue(inner != null)
            f.delivery.endPhysicalDelivery(checkNotNull(inner))
            assertTrue(f.delivery.hasPhysicalReturnsInFlight, "Inner return cannot settle its actual outer tail")
            assertTrue(f.delivery.snapshotFrozenOwnership().isEmpty(), "Installed returns are not abandoned legacy rows")
            f.delivery.endPhysicalDelivery(checkNotNull(outer))
            assertFalse(f.delivery.hasPhysicalReturnsInFlight)
        } finally { f.close() }
    }

    @Test fun originalSafetyProducerNeedsExplicitInstalledAdmissionAndRemovesEachInstallationOnce() {
        val f = Fixture()
        try {
            f.finalizeOriginal()
            assertEquals(1, f.registrations)
            assertEquals(1, f.removals)
            assertFalse(f.root.register(), "Ordinary constructor admission must remain permanently fenced")
            assertTrue(f.root.registerInstalledSafetyIngress(), "Original producer needs genuine successor installation custody")
            assertEquals(2, f.registrations)
            var legacyCalls = 0
            var safetyCalls = 0
            f.root.callback(installedSafetyObservation = { safetyCalls++ }) { legacyCalls++ }
            assertEquals(0, legacyCalls)
            assertEquals(1, safetyCalls)
            assertTrue(f.delivery.snapshotFrozenOwnership().isEmpty())
            assertTrue(f.root.remove(permanent = true))
            assertTrue(f.root.remove(permanent = true))
            assertEquals(2, f.removals)
            assertFalse(f.root.registerInstalledSafetyIngress())
        } finally { f.close() }
    }

    private class Fixture {
        val controller = ReaderController().open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity("public-synthetic-finalized-lifecycle", resourceHref = "fixture.epub"),
            url = "https://example.invalid/fixture.epub")).controller
        val store = ReaderActivatedSessionSnapshotStore()
        val delivery = ReaderPresentationLifecycleDelivery(activatedSessionStore = store)
        val domain = ReaderLegacyPhysicalDomain(controller.state.readerSessionGeneration, ReaderLegacyFreezeToken(71L))
        val registry = ReaderSemanticExecutableRequestRegistry(domain.readerSessionGeneration, activationStore = store)
        val executor = ReaderSemanticCommandExecutor(registry)
        val gateway = ReaderTransitionGateway()
        val candidate: ReaderTransitionGatewayCandidate
        var registrations = 0
        var removals = 0
        val root = delivery.registeredRoot(registerPhysical = { registrations++ },
            unregisterPhysical = { removals++ }, isValid = { true })
        val safetyKinds = mutableListOf<ReaderTask6LifecycleSafetyKind>()
        init {
            delivery.reset(controller.presentationVersion, observedWindowVisible = null)
            check(root.register())
            check(delivery.freezeForTransitionActivation(domain) == ReaderPortCommandResult.Accepted)
            repeat(3) {
                delivery.snapshotFrozenOwnership().forEach { row ->
                    check(delivery.drainFrozenOwnership(row.physicalIdentity) {} == ReaderPortCommandResult.Accepted)
                }
            }
            check(delivery.snapshotFrozenOwnership().isEmpty())
            check(executor.freezeForTransitionActivation(domain) == ReaderPortCommandResult.Accepted)
            candidate = checkNotNull(gateway.reserveActivatedCandidate(domain.readerSessionGeneration, 73L, store,
                {}, {}, registry::register, registry::discardCandidateReservations, executor::clear))
            val reservation = checkNotNull(registry.reserveNeutralBootstrap(domain, candidate) { _, _, _ ->
                error("Lifecycle custody cannot execute semantic bootstrap")
            })
            val decision = ReaderInitialActivationDecision(ReaderInitialCommittedPresentationOrigin.Neutral(
                domain.readerSessionGeneration, 73L, ReaderInitialPresentationInputLease.ChromeOnly,
                ReaderInitialPresentationInputLease.ChromeOnly), null, null, reservation)
            val ports = completeActivatedSessionPorts().copy(
                gateway = ReaderSnapshotBackedActivatedGatewayPort(candidate), semantic = executor,
                lifecycleFacts = ReaderTask6LifecycleFactPort { fact ->
                    check(delivery.hasPhysicalReturnsInFlight)
                    safetyKinds += fact.kind
                    ReaderPortCommandResult.Accepted
                })
            check(ReaderActivatedSessionInstallationBarrier(store).installTestActivatedSession(ports, decision) ==
                ReaderActivationInstallResult.Installed)
        }
        fun finalizeOriginal() {
            assertEquals(ReaderPortCommandResult.Accepted, delivery.finalizeActivatedHandoff(domain))
            assertFalse(store.legacyConsequencesOpen)
        }
        fun close() {
            root.remove(permanent = true)
            candidate.close()
            executor.clear()
            registry.clear()
        }
    }
}
