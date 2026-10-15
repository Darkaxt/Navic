package paige.navic.ui.screens.reader

import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import java.time.Duration
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Existing actual Root/Native owners, original executor, and real constructor registrations.
 * No complete installation, semantic success, layout, renderer pixels, or new owner is injected. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P], shadows = [
    Task8ImmediateGlSurfaceViewShadow::class, Task398MaterialVisualStateShadow::class,
    Task398CanonicalStoragePathShadow::class])
class ReaderRootPhysicalInventoryCompositionHostTest {
    @Test fun originalRootConstructorSourcesAreConnectedOnlyAfterAllActualOwnersFreeze() = runTest {
        val f = Fixture(testScheduler)
        try {
            assertIncomplete( f.inventory(f.domain))
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            val first = assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            assertEquals(ReaderLegacyInventorySource.entries.toSet(), first.completedSources)
            assertTrue(first.resources.any { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery },
                "Real constructor-created component/listener custody must not disappear into constant zero inventory")
            assertTrue(first.resources.all { it.physicalIdentity.domain == f.domain })
            assertEquals(first.resources.size, first.resources.map { it.physicalIdentity }.distinct().size)
            val tracker = ReaderLegacyInventoryFixedPointTracker(f.domain.freezeToken)
            assertFalse(tracker.accept(first))
            val second = assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            assertEquals(first.snapshotSequence + 1L, second.snapshotSequence)
            assertEquals(first.discoveryVersion, second.discoveryVersion)
            assertTrue(tracker.accept(second))
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun foreignSessionNeverFreezesACompleteConstructorInventory() = runTest {
        val f = Fixture(testScheduler)
        try {
            val foreign = f.domain.copy(readerSessionGeneration = f.domain.readerSessionGeneration + 1L)
            assertIs<ReaderPortCommandResult.Rejected>(f.freeze(foreign))
            assertIncomplete( f.inventory(foreign))
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            assertIncomplete( f.inventory(f.domain.copy(freezeToken = ReaderLegacyFreezeToken(107L))))
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun partialOriginalOwnerFailureAndRootCloseCannotPublishAllSixteenSources() = runTest {
        val f = Fixture(testScheduler)
        try {
            assertEquals(ReaderPortCommandResult.Accepted, f.bridge.freezeForTransitionActivation(f.domain.copy(freezeToken = ReaderLegacyFreezeToken(109L))))
            assertIs<ReaderPortCommandResult.Rejected>(f.freeze(f.domain))
            assertIs<ReaderPortCommandResult.Rejected>(f.freeze(f.domain))
            assertIncomplete( f.inventory(f.domain))
            f.closeRoot()
            assertIncomplete( f.inventory(f.domain))
            assertIs<ReaderPortCommandResult.Rejected>(f.freeze(f.domain))
        } finally { f.dispose() }
    }

    @Test fun everyOriginalOwnerMustStillBeFrozenAtEachCompleteSnapshot() = runTest {
        for (owner in listOf("deck", "preparation", "bundle", "foreground", "semantic", "lifecycle", "input", "frame", "deadline")) {
            val f = Fixture(testScheduler)
            try {
                assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
                assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
                assertEquals(ReaderPortCommandResult.Accepted, f.restoreOwner(owner))
                assertIncomplete( f.inventory(f.domain),
                    "The original owner no longer supplies frozen custody; a remembered successful freeze cannot fabricate a connected source")
            } finally { f.dispose() }
        }
    }

    @Test fun constructorRegistrationDrainUsesExactOriginalOwnerAndConfirmation() = runTest {
        val f = Fixture(testScheduler)
        try {
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            val before = assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            val row = before.resources.first { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery }
            val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
            val foreign = row.physicalIdentity.copy(domain = f.domain.copy(freezeToken = ReaderLegacyFreezeToken(113L)))
            assertIs<ReaderPortCommandResult.Rejected>(f.drain(foreign, confirmations::add))
            assertTrue(confirmations.isEmpty())
            assertTrue(before.resources == assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain)).resources,
                "Rejected foreign drain must preserve the exact original rows")
            assertEquals(ReaderPortCommandResult.Accepted, f.drain(row.physicalIdentity, confirmations::add))
            assertTrue(confirmations == listOf(row.physicalIdentity), "Only the exact original row may be confirmed")
            val after = assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            assertTrue(after.resources.none { it.physicalIdentity == row.physicalIdentity })
            assertTrue(after.discoveryVersion > before.discoveryVersion)
            assertIs<ReaderPortCommandResult.Rejected>(f.drain(row.physicalIdentity, confirmations::add))
            assertTrue(confirmations == listOf(row.physicalIdentity), "Only the exact original row may be confirmed")
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun equalLocalTokenSemanticDrainCannotRetireConstructorLifecycleCustody() = runTest {
        val f = Fixture(testScheduler)
        try {
            f.registerNeverStartedSemanticRequest()
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            val before = assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            val lifecycleRows = before.resources.filter { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery }
            val semantic = before.resources.first { it.physicalIdentity.source == ReaderLegacyInventorySource.SemanticCommandSlot &&
                lifecycleRows.any { row -> row.physicalIdentity.sourceLocalToken == it.physicalIdentity.sourceLocalToken } }
            val lifecycle = lifecycleRows.first { it.physicalIdentity.sourceLocalToken == semantic.physicalIdentity.sourceLocalToken }
            val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
            assertEquals(ReaderPortCommandResult.Accepted, f.drain(semantic.physicalIdentity, confirmations::add))
            assertTrue(confirmations == listOf(semantic.physicalIdentity), "Only the exact semantic row may be confirmed")
            val after = assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            assertTrue(after.resources.none { it.physicalIdentity == semantic.physicalIdentity })
            assertTrue(lifecycle == after.resources.single { it.physicalIdentity == lifecycle.physicalIdentity },
                "Equal-local-token constructor custody must remain unchanged")
            assertEquals(ReaderPortCommandResult.Accepted, f.restoreOwner("semantic"))
            assertIncomplete( f.inventory(f.domain))
            assertEquals(ReaderPortCommandResult.Accepted, f.refreezeSemantic())
            assertTrue(semantic == assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain)).resources
                .single { it.physicalIdentity == semantic.physicalIdentity }, "The original never-started row must restore exactly")
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun everyActualBundleChildMustStillSupplyItsFrozenCustody() = runTest {
        for (child in listOf("bitmapSource", "rasterGenerationAndPersistenceOwnership", "liveValidationOwnership",
            "hydrationOwnership", "hydrationScheduler", "publicationScheduler", "publicationLedger",
            "descriptorRequestOwnership", "pendingDescriptorOwners", "teardownOwnership")) {
            val f = Fixture(testScheduler)
            try {
                assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
                assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
                assertEquals(ReaderPortCommandResult.Accepted, f.restoreBundleChild(child))
                assertIncomplete( f.inventory(f.domain),
                    "A retained bundle domain cannot fabricate frozen custody after an original child reopens")
            } finally { f.dispose() }
        }
    }

    @Test fun originalPortCapturesAllActualRestartHandlesBeforeAnyConstructorDrain() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort()
            assertIncomplete(port.inventory(f.domain.freezeToken))
            assertTrue(port.freeze() == f.domain.freezeToken, "Original freeze token must stay exact")
            val inventory = assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken))
            val checkpoint = assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            assertEquals(ReaderLegacyInventorySource.entries.toSet(), checkpoint.completedSources)
            assertEquals(checkpoint.completedSources, checkpoint.restartHandles.keys)
            assertTrue(checkpoint.restartHandles.all { (source, handle) -> source == handle.source && handle.opaqueId > 0L })
            assertTrue(checkpoint.initialOwner === ReaderPresentationFrameOwner.Neutral, "Actual constructor-only custody has no visible predecessor")
            assertNull(checkpoint.initialBinding); assertNull(checkpoint.initialPhysicalIdentity)
            assertNull(checkpoint.initialResourceKind); assertNull(checkpoint.initialProvenance)
            assertTrue(checkpoint.requestedLease == ReaderInitialPresentationInputLease.ChromeOnly ||
                checkpoint.requestedLease == ReaderInitialPresentationInputLease.None, "Neutral checkpoint cannot fabricate page input")
            assertTrue(checkpoint.physicalLease == ReaderInitialPresentationInputLease.ChromeOnly ||
                checkpoint.physicalLease == ReaderInitialPresentationInputLease.None, "Neutral physical lease must remain identity-free")
            assertTrue(inventory.resources.any { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery })
            assertTrue(port.checkpointBeforeDrain(f.domain.freezeToken) === checkpoint, "The exact pre-drain checkpoint must be retained")
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun originalPortRestoresDrainedConstructorCustodyBeforeExactLegacyCommit() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort(); port.freeze()
            val before = assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken))
            val checkpoint = assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            val rows = before.resources.filter { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery }
            assertTrue(rows.isNotEmpty())
            val originalRegistrations = rows.map { f.constructorRegistrationFor(it.physicalIdentity) }
            val drains = mutableListOf<ReaderLegacyPhysicalIdentity>()
            for (row in rows) assertEquals(ReaderPortCommandResult.Accepted, port.drain(f.domain.freezeToken, row.physicalIdentity, drains::add))
            assertTrue(drains == rows.map { it.physicalIdentity }, "Every original constructor retirement must confirm exactly once before restoration")
            // Exact framework removals themselves produce late return custody in the
            // SAME original source. Mirror the coordinator's exhaustive drain, not an
            // assumption that the pre-drain inventory was forever complete.
            val late = assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken)).resources
                .filter { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery }
            assertTrue(late.isNotEmpty() && late.all { it.state == ReaderLegacyResourceState.Released },
                "Real constructor removal must expose its settled late physical-return rows")
            for (row in late) assertEquals(ReaderPortCommandResult.Accepted, port.drain(f.domain.freezeToken, row.physicalIdentity, drains::add))
            assertTrue(assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken)).resources.none {
                it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery }, "Every original constructor source return must really drain")
            assertIs<ReaderPortCommandResult.Rejected>(port.cancelFreezeBeforeDrain(f.domain.freezeToken))
            assertIs<ReaderLegacyCommitRestoredResult.Rejected>(port.commitRestoredLegacy(checkpoint))
            val restored = linkedSetOf<ReaderLegacyInventorySource>()
            for (source in ReaderLegacyInventorySource.entries) {
                assertEquals(ReaderPortCommandResult.Accepted, port.restoreFromActivationCheckpoint(checkpoint, source) { confirmed, result ->
                    assertEquals(source, confirmed)
                    assertEquals(ReaderLegacyRestorationResult.Restored, result)
                    assertTrue(restored.add(confirmed), "Original source restoration must confirm once")
                }, "Original restoration consumer ${source.name}")
                if (restored.size < ReaderLegacyInventorySource.entries.size)
                    assertIs<ReaderLegacyCommitRestoredResult.Rejected>(port.commitRestoredLegacy(checkpoint))
            }
            assertEquals(ReaderLegacyCommitRestoredResult.Applied, port.commitRestoredLegacy(checkpoint))
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            val after = assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            val restartedIdentities = originalRegistrations.map(f::constructorRegistrationIdentity)
            assertTrue(restartedIdentities.none { restarted -> rows.any { it.physicalIdentity == restarted } },
                "Genuine re-registrations need their new physical installation tokens")
            assertTrue(restartedIdentities.all { restarted -> after.resources.single { it.physicalIdentity == restarted }.state == ReaderLegacyResourceState.Registered },
                "Every same original constructor producer must actually register again")
            assertTrue(before.resources.filter { it.physicalIdentity.source != ReaderLegacyInventorySource.LifecycleDelivery } ==
                after.resources.filter { it.physicalIdentity.source != ReaderLegacyInventorySource.LifecycleDelivery },
                "Untouched original custody must remain exact")
            assertEquals(rows.size, after.resources.count { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery })
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun originalPortRejectsForeignCheckpointAndReallyUnfreezesBeforeDrain() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort(); port.freeze()
            val before = assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken))
            val checkpoint = assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            var called = false
            val foreign = checkpoint.copy(routeGeneration = checkpoint.routeGeneration + 1L)
            assertIs<ReaderPortCommandResult.Rejected>(port.restoreFromActivationCheckpoint(foreign,
                ReaderLegacyInventorySource.LifecycleDelivery) { _, _ -> called = true })
            assertFalse(called)
            assertTrue(before.resources == assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken)).resources,
                "Foreign checkpoint must preserve actual constructor custody")
            assertEquals(ReaderPortCommandResult.Accepted, port.cancelFreezeBeforeDrain(f.domain.freezeToken))
            assertIncomplete(port.inventory(f.domain.freezeToken))
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            assertTrue(before.resources == assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain)).resources,
                "Pre-drain unfreeze must preserve every original registration")
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun originalPortCachedCheckpointStillRequiresEveryActualFence() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort(); port.freeze()
            assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            assertEquals(ReaderPortCommandResult.Accepted, f.restoreBundleChild("bitmapSource"))
            assertIncomplete(port.inventory(f.domain.freezeToken))
            assertNull(port.checkpointBeforeDrain(f.domain.freezeToken), "Remembered checkpoint must not fabricate fresh all-source fencing")
            assertFalse(f.store.legacyConsequencesOpen)
        } finally { f.dispose() }
    }

    @Test fun originalPortDrainCannotBorrowAnUnfencedCheckpoint() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort(); port.freeze()
            val before = assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken))
            assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            val row = before.resources.first { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery }
            val original = f.constructorRegistrationFor(row.physicalIdentity)
            val identity = f.constructorRegistrationIdentity(original)
            assertEquals(ReaderPortCommandResult.Accepted, f.restoreBundleChild("bitmapSource"))
            var called = false
            assertIs<ReaderPortCommandResult.Rejected>(port.drain(f.domain.freezeToken, row.physicalIdentity) { called = true })
            assertFalse(called)
            assertTrue(identity == f.constructorRegistrationIdentity(original), "Unfenced drain must preserve the real constructor registration")
            assertFalse(f.store.legacyConsequencesOpen)
        } finally { f.dispose() }
    }

    @Test fun originalPortRestoredOriginalChildMustStayCurrentUntilCommit() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort(); port.freeze()
            val checkpoint = assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            for (source in ReaderLegacyInventorySource.entries)
                assertEquals(ReaderPortCommandResult.Accepted, port.restoreFromActivationCheckpoint(checkpoint, source) { _, result ->
                    assertEquals(ReaderLegacyRestorationResult.Restored, result)
                    assertFalse(f.store.legacyConsequencesOpen)
                }, "Original restoration consumer ${source.name}")
            assertEquals(ReaderPortCommandResult.Accepted, f.refreezeBundleChild("bitmapSource"))
            assertIs<ReaderLegacyCommitRestoredResult.Rejected>(port.commitRestoredLegacy(checkpoint))
            assertFalse(f.store.legacyConsequencesOpen)
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun originalCheckpointUsesOriginalStoreFrozenRouteGeneration() = runTest {
        val f = Fixture(testScheduler)
        try {
            repeat(2) { f.originalPort() }
            val before = f.legacyRouteGeneration()
            val port = f.originalPort(); port.freeze()
            val checkpoint = assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            assertTrue(checkpoint.routeGeneration == f.legacyRouteGeneration(), "Checkpoint must capture the actual frozen route-table generation")
            assertTrue(f.legacyRouteGeneration() > before, "Original store must advance its route generation when consequences close")
            assertEquals(ReaderPortCommandResult.Accepted, port.cancelFreezeBeforeDrain(f.domain.freezeToken))
            assertTrue(f.legacyRouteGeneration() > checkpoint.routeGeneration, "Complete unfreeze must really publish a newer legacy route table")
            assertTrue(f.store.legacyConsequencesOpen)
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun foreignOriginalPortCannotCloseTheAuthenticLegacyRoutes() = runTest {
        val f = Fixture(testScheduler)
        try {
            val foreign = f.domain.copy(readerSessionGeneration = f.domain.readerSessionGeneration + 1L)
            val port = f.originalPort(foreign)
            assertTrue(f.store.legacyConsequencesOpen)
            port.freeze()
            assertIncomplete(port.inventory(foreign.freezeToken))
            assertTrue(f.store.legacyConsequencesOpen, "Foreign freeze authority must be rejected before any route-table mutation")
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun offMainOriginalPortCannotCloseTheAuthenticLegacyRoutes() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort()
            withContext(Dispatchers.Default) { port.freeze() }
            assertIncomplete(port.inventory(f.domain.freezeToken))
            assertTrue(f.store.legacyConsequencesOpen, "Off-main freeze must not mutate the original consequence-route table")
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            assertNull(f.store.snapshot)
        } finally { f.dispose() }
    }

    @Test fun originalDeckAdapterCannotReopenBehindItsRememberedControllerDomain() = runTest {
        val f = Fixture(testScheduler)
        try {
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            assertIs<ReaderLegacyResourceInventory.Complete>(f.inventory(f.domain))
            assertEquals(ReaderPortCommandResult.Accepted, f.restoreNestedDeckAdapter())
            assertIncomplete(f.inventory(f.domain), "Original deck adapter reopened; controller metadata cannot fabricate connected custody")
        } finally { f.dispose() }
    }

    @Test fun originalDeckAdapterForeignRefreezeCannotSupplyAnEmptyConnectedSource() = runTest {
        val f = Fixture(testScheduler)
        try {
            assertEquals(ReaderPortCommandResult.Accepted, f.freeze(f.domain))
            assertEquals(ReaderPortCommandResult.Accepted, f.restoreNestedDeckAdapter())
            val foreign = f.domain.copy(freezeToken = ReaderLegacyFreezeToken(127L))
            assertEquals(ReaderPortCommandResult.Accepted, f.freezeNestedDeckAdapter(foreign))
            assertIncomplete(f.inventory(f.domain), "Even zero-row inventory requires the exact original adapter domain")
        } finally { f.dispose() }
    }

    @Test fun originalCheckpointCannotBorrowAnUnfencedNestedDeckAdapter() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort(); port.freeze()
            assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            assertEquals(ReaderPortCommandResult.Accepted, f.restoreNestedDeckAdapter())
            assertNull(port.checkpointBeforeDrain(f.domain.freezeToken), "Actual nested deck custody must stay fenced")
        } finally { f.dispose() }
    }

    @Test fun originalDrainCannotRetireConstructorsAfterNestedDeckAdapterReopens() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort(); port.freeze()
            val before = assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken))
            assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            val row = before.resources.first { it.physicalIdentity.source == ReaderLegacyInventorySource.LifecycleDelivery }
            val registration = f.constructorRegistrationFor(row.physicalIdentity)
            val originalIdentity = f.constructorRegistrationIdentity(registration)
            assertEquals(ReaderPortCommandResult.Accepted, f.restoreNestedDeckAdapter())
            var called = false
            assertIs<ReaderPortCommandResult.Rejected>(port.drain(f.domain.freezeToken, row.physicalIdentity) { called = true })
            assertFalse(called)
            assertTrue(originalIdentity == f.constructorRegistrationIdentity(registration), "Unsafe drain must not retire the actual original registration")
        } finally { f.dispose() }
    }

    @Test fun originalAllSourceFinalizationPermanentlyRetiresLegacyRestartCustody() = runTest {
        val f = Fixture(testScheduler)
        try {
            val port = f.originalPort(); port.freeze()
            val checkpoint = assertNotNull(port.checkpointBeforeDrain(f.domain.freezeToken))
            val confirmed = linkedSetOf<ReaderLegacyPhysicalIdentity>()
            repeat(4) {
                val inventory = assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken))
                for (row in inventory.resources) {
                    assertEquals(ReaderPortCommandResult.Accepted,
                        port.drain(f.domain.freezeToken, row.physicalIdentity) { exact ->
                            assertTrue(exact == row.physicalIdentity)
                            assertTrue(confirmed.add(exact), "Each actual original retirement confirms once")
                        })
                }
            }
            assertTrue(assertIs<ReaderLegacyResourceInventory.Complete>(port.inventory(f.domain.freezeToken)).resources.isEmpty())
            assertIs<ReaderPortCommandResult.Rejected>(port.finalizeActivatedHandoff(f.domain.freezeToken, null,
                confirmed + ReaderLegacyPhysicalIdentity(f.domain, ReaderLegacyInventorySource.Input, ReaderLegacySourceLocalOpaqueToken(999L))))
            assertEquals(ReaderPortCommandResult.Accepted, port.finalizeActivatedHandoff(f.domain.freezeToken, null, confirmed),
                "Actual sixteen-source custody must finalize, not inherit the rejecting default")
            assertFalse(f.store.legacyConsequencesOpen)
            for (owner in listOf("deck", "preparation", "bundle", "foreground", "semantic", "lifecycle", "input", "frame", "deadline"))
                assertIs<ReaderPortCommandResult.Rejected>(f.restoreOwner(owner), "Finalized original owner cannot restore legacy admission: $owner")
            assertIs<ReaderPortCommandResult.Rejected>(port.restoreFromActivationCheckpoint(checkpoint,
                ReaderLegacyInventorySource.Input) { _, _ -> fail("Finalized checkpoint must never restart input") })
            assertIs<ReaderLegacyCommitRestoredResult.Rejected>(port.commitRestoredLegacy(checkpoint))
            assertIs<ReaderPortCommandResult.Rejected>(port.finalizeActivatedHandoff(f.domain.freezeToken, null, confirmed))
        } finally { f.dispose() }
    }

    private fun assertIncomplete(inventory: ReaderLegacyResourceInventory,
        message: String = "Original source custody must remain incomplete") {
        assertTrue(inventory === ReaderLegacyResourceInventory.Incomplete, message)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private class Fixture(private val scheduler: TestCoroutineScheduler) {
        // Use the existing host-test Main driver so actual off-main teardown returns
        // resume on this runTest's issuing thread, not an undriven Android Handler.
        init { Dispatchers.setMain(StandardTestDispatcher(scheduler)) }
        private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        private val type = Class.forName("paige.navic.ui.screens.reader.KomikkuReaderNativeFrameRoot")
        val store = ReaderActivatedSessionSnapshotStore()
        private val root = type.getDeclaredConstructor(Context::class.java, ReaderActivatedSessionSnapshotStore::class.java)
            .apply { isAccessible = true }.newInstance(activity.get(), store) as ViewGroup
        private val native = type.getDeclaredField("viewerContainer").apply { isAccessible = true }.get(root)
        private val opened = ReaderController().open(ReaderEngineOpenRequest(
            publication = ReaderPublicationIdentity("public-synthetic-source-composition", resourceHref = "fixture.epub"),
            url = "https://example.invalid/fixture.epub")).controller
        val domain = ReaderLegacyPhysicalDomain(opened.state.readerSessionGeneration, ReaderLegacyFreezeToken(101L))
        private val registry = ReaderSemanticExecutableRequestRegistry(domain.readerSessionGeneration, activationStore = store)
        private val executor = ReaderSemanticCommandExecutor(registry)
        val bridge = type.getDeclaredField("presentationHostBridge").apply { isAccessible = true }.get(root) as ReaderPresentationHostBridge
        init {
            activity.get().setContentView(root); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(48)); scheduler.runCurrent()
            val key = ReaderViewerKey(ReaderViewerKind.WebViewPublication, "public-synthetic-source-composition")
            val set = type.declaredMethods.single { it.name == "setPresentationDecision" }
            set.apply { isAccessible = true }.invoke(root, readerPresentationDecision(opened.state.presentation),
                opened.state.presentation, opened.presentationVersion, false, null, key,
                { _: ReaderPresentationEvent -> null })
            scheduler.runCurrent()
        }
        fun originalPort(domain: ReaderLegacyPhysicalDomain = this.domain): ReaderLegacyFreezeAndInventoryPort {
            val method = type.declaredMethods.singleOrNull { it.name == "createOriginalLegacyFreezeAndInventoryPort" && it.parameterCount == 2 }
            assertNotNull(method, "Actual Root must expose original checkpoint, drain and restoration consumers without minting incomplete production ports")
            return assertIs<ReaderLegacyFreezeAndInventoryPort>(method.apply { isAccessible = true }.invoke(root, domain, executor))
        }
        fun freeze(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
            val method = type.declaredMethods.singleOrNull { it.name == "freezePhysicalSourcesForTransitionActivation" && it.parameterCount == 2 }
            assertNotNull(method, "Actual Root must freeze its original sixteen-source composition, not a synthetic inventory port")
            return assertIs<ReaderPortCommandResult>(method.apply { isAccessible = true }.invoke(root, domain, executor))
        }
        fun inventory(domain: ReaderLegacyPhysicalDomain): ReaderLegacyResourceInventory {
            val method = type.declaredMethods.singleOrNull { it.name == "snapshotPhysicalSourcesForTransitionActivation" && it.parameterCount == 1 }
            assertNotNull(method, "Actual Root must capture all sixteen original sources with truthful constructor effects and unsettled rejection")
            return assertIs<ReaderLegacyResourceInventory>(method.apply { isAccessible = true }.invoke(root, domain))
        }
        fun restoreOwner(owner: String): ReaderPortCommandResult {
            fun nativeField(name: String) = native.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(native)
            val (target, name) = when (owner) {
                "deck" -> nativeField("playLikeCurlController") to "restoreDeckOwnershipAfterTransitionActivation"
                "preparation" -> nativeField("pageRasterPreparationController") to "restoreRasterPreparationOwnershipAfterTransitionActivation"
                "bundle" -> nativeField("pageTurnBundleSource") to "restoreAfterTransitionActivation"
                "foreground" -> nativeField("foregroundWebViewOwnership") to "restoreAfterTransitionActivation"
                "semantic" -> executor to "restoreAfterTransitionActivation"
                "lifecycle" -> nativeField("presentationLifecycleDelivery") to "restoreAfterTransitionActivation"
                "input" -> nativeField("pageInputSettlementHostController") to "restoreAfterTransitionActivation"
                "frame" -> type.getDeclaredMethod("getFrameOrHandoffPhysicalSource").apply { isAccessible = true }.invoke(root) to "restoreAfterTransitionActivation"
                "deadline" -> bridge to "restoreAfterTransitionActivation"
                else -> error("Unknown fixed fixture owner")
            }
            val method = target.javaClass.declaredMethods.single { it.name == name && it.parameterCount == 1 }
            return method.apply { isAccessible = true }.invoke(target, domain) as ReaderPortCommandResult
        }
        fun restoreBundleChild(name: String): ReaderPortCommandResult {
            val bundle = native.javaClass.getDeclaredField("pageTurnBundleSource").apply { isAccessible = true }.get(native)
            val child = bundle.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(bundle)
            val method = child.javaClass.declaredMethods.single { it.name == "restoreAfterTransitionActivation" && it.parameterCount == 1 }
            return method.apply { isAccessible = true }.invoke(child, domain) as ReaderPortCommandResult
        }
        private fun nestedDeckAdapter(): Any {
            val controller = native.javaClass.getDeclaredField("playLikeCurlController").apply { isAccessible = true }.get(native)
            return controller.javaClass.getDeclaredField("physicalDeckOwnership").apply { isAccessible = true }.get(controller)
        }
        fun restoreNestedDeckAdapter(): ReaderPortCommandResult {
            val child = nestedDeckAdapter()
            return child.javaClass.getDeclaredMethod("restoreAfterTransitionActivation", ReaderLegacyPhysicalDomain::class.java)
                .apply { isAccessible = true }.invoke(child, domain) as ReaderPortCommandResult
        }
        fun freezeNestedDeckAdapter(domain: ReaderLegacyPhysicalDomain): ReaderPortCommandResult {
            val child = nestedDeckAdapter()
            return child.javaClass.getDeclaredMethod("freezeForTransitionActivation", ReaderLegacyPhysicalDomain::class.java)
                .apply { isAccessible = true }.invoke(child, domain) as ReaderPortCommandResult
        }
        fun refreezeBundleChild(name: String): ReaderPortCommandResult {
            val bundle = native.javaClass.getDeclaredField("pageTurnBundleSource").apply { isAccessible = true }.get(native)
            val child = bundle.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(bundle)
            val method = child.javaClass.declaredMethods.single { it.name == "freezeForTransitionActivation" && it.parameterCount == 1 }
            return method.apply { isAccessible = true }.invoke(child, domain) as ReaderPortCommandResult
        }
        fun legacyRouteGeneration(): Long {
            val method = store.javaClass.declaredMethods.singleOrNull { it.name.startsWith("getLegacyRouteGeneration") && it.parameterCount == 0 }
            assertNotNull(method, "Original snapshot store must expose its genuine frozen consequence-route generation")
            return method.apply { isAccessible = true }.invoke(store) as Long
        }
        fun drain(identity: ReaderLegacyPhysicalIdentity, onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit): ReaderPortCommandResult {
            val method = type.declaredMethods.singleOrNull { it.name == "drainPhysicalSourceForTransitionActivation" && it.parameterCount == 3 }
            assertNotNull(method, "Actual Root must route exact frozen drain to the original constructor-created owner")
            return assertIs<ReaderPortCommandResult>(method.apply { isAccessible = true }.invoke(root, domain, identity, onConfirmed))
        }
        fun constructorRegistrationFor(identity: ReaderLegacyPhysicalIdentity): Any {
            val lifecycle = native.javaClass.getDeclaredField("presentationLifecycleDelivery").apply { isAccessible = true }.get(native)
            val roots = lifecycle.javaClass.getDeclaredField("registeredRoots").apply { isAccessible = true }.get(lifecycle) as Set<*>
            return assertNotNull(roots.singleOrNull { root ->
                root != null && constructorRegistrationOwner(root)?.token == identity.sourceLocalToken
            }, "The drained row must belong to a real original constructor producer")
        }
        private fun constructorRegistrationOwner(root: Any): ReaderExactPhysicalOwnerRegistry.Owner? =
            root.javaClass.getDeclaredField("physicalOwner").apply { isAccessible = true }.get(root) as ReaderExactPhysicalOwnerRegistry.Owner?
        fun constructorRegistrationIdentity(root: Any): ReaderLegacyPhysicalIdentity {
            val owner = assertNotNull(constructorRegistrationOwner(root), "The same original producer must hold its actual restored installation")
            return ReaderLegacyPhysicalIdentity(domain, ReaderLegacyInventorySource.LifecycleDelivery, owner.token)
        }
        fun registerNeverStartedSemanticRequest() {
            repeat(4) { registry.register { _, _, _ -> error("Frozen pending request must never execute") } }
        }
        fun refreezeSemantic(): ReaderPortCommandResult = executor.freezeForTransitionActivation(domain)
        fun closeRoot() { type.getDeclaredMethod("closeReader").apply { isAccessible = true }.invoke(root) }
        suspend fun dispose() {
            try {
                executor.clear(); registry.clear()
                val controller = native.javaClass.getDeclaredField("pageRasterPreparationController").apply { isAccessible = true }
                    .get(native) as ReaderPageRasterPreparationController
                val closed = controller.destroy()
                scheduler.runCurrent() // Preserve original immediate fence-before-Root-close ordering.
                closeRoot(); shadowOf(Looper.getMainLooper()).idle()
                val failure = runCatching { withContext(Dispatchers.Default) { withTimeout(5_000L) { closed.await() } } }.exceptionOrNull()
                if (failure != null) check(failure is ReaderPageTeardownException && generateSequence(failure as Throwable?) { it.cause }
                    .any { it.message == "GL surface is unavailable for terminal disposal" }) {
                    "Unexpected source fixture terminal disposition: completed=${closed.isCompleted} timedOut=${failure is kotlinx.coroutines.TimeoutCancellationException} teardown=${failure is ReaderPageTeardownException}"
                }
                check(closed.isCompleted)
            } finally {
                try { activity.pause().stop().destroy(); shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent() }
                finally { Dispatchers.resetMain() }
            }
        }
    }
}
