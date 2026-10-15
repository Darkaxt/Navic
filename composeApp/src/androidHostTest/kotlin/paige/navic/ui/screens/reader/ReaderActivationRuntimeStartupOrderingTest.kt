package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderInitialPresentationInputLease
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderSemanticRequestHandle
import paige.navic.reader.ReaderTransitionFailureReason
import paige.navic.reader.ReaderTransitionResourceKind
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Activation ordering only; test ports are not production composition or display proof. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ReaderActivationRuntimeStartupOrderingTest {
	@Test
	fun immediateActivationInitializesRuntimeBeforeOriginalFinalizationAndBootstrap() {
		val trace = mutableListOf<String>()
		val legacy = OriginalRegistryFixture(trace)
		val store = ReaderActivatedSessionSnapshotStore()
		lateinit var coordinator: ReaderSessionActivationCoordinator
		coordinator = newCoordinator(legacy, store,
			onInstalled = {
				assertNotNull(store.snapshot)
				assertEquals(1, store.atomicWriteCount)
				assertEquals(emptyList(), trace)
				trace += "runtime"
				ReaderPortCommandResult.Accepted
			},
			onActivated = {
				assertEquals(ReaderSessionActivationState.Activated, coordinator.state)
				assertEquals(listOf("runtime", "finalization"), trace)
				trace += "bootstrap"
			})

		assertEquals(ReaderActivationInstallResult.Installed,
			coordinator.activateForTest(completeActivatedSessionPorts(), ReaderInitialPresentationInputLease.ChromeOnly))
		assertEquals(listOf("runtime", "finalization", "bootstrap"), trace)
		assertEquals(1, store.atomicWriteCount)
	}

	@Test
	fun originalAsynchronousReturnResumesRuntimeFinalizationAndBootstrapOnce() {
		val trace = mutableListOf<String>()
		val legacy = OriginalRegistryFixture(trace, pending = true)
		val store = ReaderActivatedSessionSnapshotStore()
		lateinit var coordinator: ReaderSessionActivationCoordinator
		coordinator = newCoordinator(legacy, store,
			onInstalled = {
				assertNotNull(store.snapshot)
				trace += "runtime"
				ReaderPortCommandResult.Accepted
			},
			onActivated = {
				assertEquals(ReaderSessionActivationState.Activated, coordinator.state)
				trace += "bootstrap"
			})

		assertEquals(ReaderActivationInstallResult.Pending,
			coordinator.activateForTest(completeActivatedSessionPorts(), ReaderInitialPresentationInputLease.ChromeOnly))
		assertEquals(emptyList(), trace)
		assertEquals(0, store.atomicWriteCount)

		assertTrue(legacy.returnOriginalOwner())
		assertFalse(legacy.returnOriginalOwner())

		assertEquals(listOf("runtime", "finalization", "bootstrap"), trace)
		assertEquals(ReaderSessionActivationState.Activated, coordinator.state)
		assertEquals(1, store.atomicWriteCount)
	}

	@Test
	fun runtimeInitializationRejectionClosesInstalledSessionWithoutFinalizationOrBootstrap() {
		assertRejectedRuntimeInitialization(throws = false)
	}

	@Test
	fun runtimeInitializationThrowClosesInstalledSessionWithoutFinalizationOrBootstrap() {
		assertRejectedRuntimeInitialization(throws = true)
	}

	private fun assertRejectedRuntimeInitialization(throws: Boolean) {
		val trace = mutableListOf<String>()
		val legacy = OriginalRegistryFixture(trace)
		val store = ReaderActivatedSessionSnapshotStore()
		var cancellationCount = 0
		var discardCount = 0
		val coordinator = ReaderSessionActivationCoordinator(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			legacy = legacy,
			installationBarrier = ReaderActivatedSessionInstallationBarrier(store),
			narrowInitialLease = { it },
			reserveNeutralBootstrap = { reservation() },
			discardNeutralBootstrap = { discardCount++ },
			cancelPhysicalWork = { cancellationCount++ },
			onInstalledBeforeFinalization = {
				assertNotNull(store.snapshot)
				trace += "runtime"
				if (throws) throw IllegalStateException("fixture startup rejected")
				ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.AtomicPublicationRejected)
			},
			onActivated = { trace += "bootstrap" })

		assertIs<ReaderActivationInstallResult.Rejected>(coordinator.activateForTest(
			completeActivatedSessionPorts(), ReaderInitialPresentationInputLease.ChromeOnly))

		assertEquals(listOf("runtime"), trace)
		assertEquals(ReaderSessionActivationState.ReleaseOnly, coordinator.state)
		assertEquals(ReaderSessionActivationState.ReleaseOnly, store.state)
		assertFalse(store.commandEgressOpen)
		assertEquals(1, cancellationCount)
		assertEquals(1, discardCount)
		assertEquals(0, legacy.restorationCount)
	}

	@Test
	fun originalFinalizationRejectionNeverStartsTheReservedBootstrap() {
		val trace = mutableListOf<String>()
		val legacy = OriginalRegistryFixture(trace, rejectFinalization = true)
		val store = ReaderActivatedSessionSnapshotStore()
		val coordinator = newCoordinator(legacy, store,
			onInstalled = { trace += "runtime"; ReaderPortCommandResult.Accepted },
			onActivated = { trace += "bootstrap" })

		assertIs<ReaderActivationInstallResult.Rejected>(coordinator.activateForTest(
			completeActivatedSessionPorts(), ReaderInitialPresentationInputLease.ChromeOnly))

		assertEquals(listOf("runtime", "finalization"), trace)
		assertEquals(ReaderSessionActivationState.ReleaseOnly, coordinator.state)
		assertFalse(store.commandEgressOpen)
	}

	private fun newCoordinator(
		legacy: OriginalRegistryFixture,
		store: ReaderActivatedSessionSnapshotStore,
		onInstalled: () -> ReaderPortCommandResult,
		onActivated: () -> Unit
	) = ReaderSessionActivationCoordinator(
		readerSessionGeneration = 3L,
		coordinatorEpoch = 5L,
		legacy = legacy,
		installationBarrier = ReaderActivatedSessionInstallationBarrier(store),
		narrowInitialLease = { it },
		reserveNeutralBootstrap = { reservation() },
		onInstalledBeforeFinalization = onInstalled,
		onActivated = onActivated)

	private fun reservation() = ReaderReservedNeutralBootstrapRequest(ReaderSemanticRequestHandle(701L), 3L)

	private class OriginalRegistryFixture(
		private val trace: MutableList<String>,
		pending: Boolean = false,
		private val rejectFinalization: Boolean = false
	) : ReaderLegacyFreezeAndInventoryPort {
		private val token = ReaderLegacyFreezeToken(211L)
		private val domain = ReaderLegacyPhysicalDomain(3L, token)
		private val sources = ReaderLegacyInventorySource.entries.associateWith { ReaderExactPhysicalOwnerRegistry(it) }
		private val pendingSource = requireNotNull(sources[ReaderLegacyInventorySource.RasterPreparation])
		private val owner = if (pending) requireNotNull(pendingSource.admit(
			ReaderExactPhysicalOwnerDescriptor(ReaderTransitionResourceKind.Raster,
				state = ReaderLegacyResourceState.Running), cancelPhysical = { true })) else null
		private var snapshotSequence = 0L
		var restorationCount = 0
			private set

		override fun freeze(): ReaderLegacyFreezeToken {
			sources.values.forEach {
				assertEquals(ReaderPortCommandResult.Accepted, it.freezeForTransitionActivation(domain))
			}
			return token
		}

		override fun checkpointBeforeDrain(token: ReaderLegacyFreezeToken) = ReaderLegacyRestorationCheckpoint(
			id = ReaderLegacyRestorationCheckpointId(1L),
			freezeToken = token,
			routeGeneration = 1L,
			completedSources = sources.keys,
			restartHandles = sources.keys.associateWith { ReaderLegacySourceRestartHandle(it, it.ordinal + 1L) },
			initialOwner = ReaderPresentationFrameOwner.Neutral,
			initialBinding = null,
			initialPhysicalIdentity = null,
			initialResourceKind = null,
			initialProvenance = null,
			requestedLease = ReaderInitialPresentationInputLease.ChromeOnly,
			physicalLease = ReaderInitialPresentationInputLease.ChromeOnly)

		override fun inventory(token: ReaderLegacyFreezeToken): ReaderLegacyResourceInventory =
			ReaderLegacyResourceInventory.Complete(token, ++snapshotSequence, 1L, sources.keys,
				sources.values.flatMap { assertNotNull(it.connectedFrozenOwnership()).resources })

		override fun drain(token: ReaderLegacyFreezeToken, physicalIdentity: ReaderLegacyPhysicalIdentity,
			onConfirmed: (ReaderLegacyPhysicalIdentity) -> Unit): ReaderPortCommandResult =
			requireNotNull(sources[physicalIdentity.source]).drainFrozenOwnership(physicalIdentity, onConfirmed)

		override fun finalizeActivatedHandoff(token: ReaderLegacyFreezeToken,
			selected: ReaderImportedLegacyResourceRegistration?,
			confirmedRetirements: Set<ReaderLegacyPhysicalIdentity>): ReaderPortCommandResult {
			trace += "finalization"
			if (rejectFinalization) return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
			sources.values.forEach { assertEquals(ReaderPortCommandResult.Accepted, it.finalizeActivatedHandoff(domain)) }
			return ReaderPortCommandResult.Accepted
		}

		fun returnOriginalOwner(): Boolean = pendingSource.complete(requireNotNull(owner))

		override fun cancelFreezeBeforeDrain(token: ReaderLegacyFreezeToken) = ReaderPortCommandResult.Accepted
		override fun restoreFromActivationCheckpoint(checkpoint: ReaderLegacyRestorationCheckpoint,
			source: ReaderLegacyInventorySource,
			onConfirmed: (ReaderLegacyInventorySource, ReaderLegacyRestorationResult) -> Unit): ReaderPortCommandResult {
			restorationCount++
			return ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
		}
		override fun commitRestoredLegacy(checkpoint: ReaderLegacyRestorationCheckpoint) =
			ReaderLegacyCommitRestoredResult.Rejected(ReaderTransitionFailureReason.LegacyDrainFailed)
	}
}
