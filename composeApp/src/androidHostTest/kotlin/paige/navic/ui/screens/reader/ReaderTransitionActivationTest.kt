package paige.navic.ui.screens.reader

import java.io.File
import java.lang.reflect.Modifier
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
	fun restartablePhysicalAdaptersCoverCaptureValidationAndStoreSourcesExactly() {
		val sources = listOf(
			ReaderLegacyInventorySource.RasterCaptureAndVisualState,
			ReaderLegacyInventorySource.RasterLiveValidation,
			ReaderLegacyInventorySource.RasterStoreAndCache
		)
		sources.forEachIndexed { index, source ->
			val releaseCallbacks = mutableListOf<() -> Unit>()
			val restoredPayloads = mutableListOf<Int>()
			val releasePhysicalOwner = { _: Int, onReleased: () -> Unit ->
				releaseCallbacks.add(onReleased)
				true
			}
			val restorePhysicalOwner = { payload: Int ->
				restoredPayloads += payload
				payload
			}
			val adapter = when (source) {
				ReaderLegacyInventorySource.RasterCaptureAndVisualState ->
					readerRasterCaptureAndVisualStatePhysicalOwnershipAdapter(
						releasePhysicalOwner,
						restorePhysicalOwner
					)
				ReaderLegacyInventorySource.RasterLiveValidation ->
					readerRasterLiveValidationPhysicalOwnershipAdapter(
						releasePhysicalOwner,
						restorePhysicalOwner
					)
				ReaderLegacyInventorySource.RasterStoreAndCache ->
					readerRasterStoreAndCachePhysicalOwnershipAdapter(
						releasePhysicalOwner,
						restorePhysicalOwner
					)
				else -> error("unexpected source")
			}
			val lease = checkNotNull(
				adapter.register(
					ReaderRestartablePhysicalDescriptor(
						restartPayload = index + 1,
						kind = ReaderTransitionResourceKind.Raster,
						binding = activationBinding(),
						state = ReaderLegacyResourceState.Running,
						ownsCallbackRegistration = true
					)
				)
			)
			val domain = ReaderLegacyPhysicalDomain(101L, ReaderLegacyFreezeToken((index + 1).toLong()))
			assertEquals(ReaderPortCommandResult.Accepted, adapter.freezeForTransitionActivation(domain))
			assertEquals(null, adapter.register(lease.descriptor))
			val rows = adapter.snapshotFrozenOwnership()
			assertEquals(2, rows.size)
			assertTrue(rows.all { it.physicalIdentity.source == source })
			assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
			val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
			val owner = rows.single { it.kind == ReaderTransitionResourceKind.Raster }
			val callback = rows.single { it.kind == ReaderTransitionResourceKind.CallbackRegistration }
			assertEquals(ReaderPortCommandResult.Accepted, adapter.drainFrozenOwnership(owner.physicalIdentity, confirmed::add))
			assertEquals(ReaderPortCommandResult.Accepted, adapter.drainFrozenOwnership(callback.physicalIdentity, confirmed::add))
			assertEquals(1, confirmed.size)
			assertTrue(
				confirmed.single() == callback.physicalIdentity,
				"Callback drain must confirm only its exact physical identity"
			)
			releaseCallbacks.single().invoke()
			assertTrue(
				rows.map { it.physicalIdentity }.toSet() == confirmed.toSet(),
				"Restartable source must confirm exactly the frozen physical identities"
			)
			assertEquals(ReaderPortCommandResult.Accepted, adapter.restoreAfterTransitionActivation(domain))
			assertEquals(listOf(index + 1), restoredPayloads)
		}
	}

	@Test
	fun failedRestartablePhysicalRestorationCanRetryWithoutLosingRestoredOwners() {
		val releases = mutableListOf<() -> Unit>()
		var secondRestorationFails = true
		val adapter = ReaderRestartablePhysicalSourceAdapter(
			source = ReaderLegacyInventorySource.RasterLiveValidation,
			releasePhysicalOwner = { _: Int, onReleased ->
				releases.add(onReleased)
				true
			},
			restorePhysicalOwner = { payload: Int ->
				payload.takeUnless { payload == 2 && secondRestorationFails }
			}
		)
		listOf(1, 2).forEach { payload ->
			checkNotNull(
				adapter.register(
					ReaderRestartablePhysicalDescriptor(
						restartPayload = payload,
						kind = ReaderTransitionResourceKind.Raster,
						binding = activationBinding(),
						state = ReaderLegacyResourceState.Running,
						ownsCallbackRegistration = true
					)
				)
			)
		}
		val domain = ReaderLegacyPhysicalDomain(101L, ReaderLegacyFreezeToken(17L))
		assertEquals(ReaderPortCommandResult.Accepted, adapter.freezeForTransitionActivation(domain))
		val initialRows = adapter.snapshotFrozenOwnership()
		val confirmed = mutableListOf<ReaderLegacyPhysicalIdentity>()
		initialRows.filter { it.kind == ReaderTransitionResourceKind.CallbackRegistration }.forEach { row ->
			assertEquals(ReaderPortCommandResult.Accepted, adapter.drainFrozenOwnership(row.physicalIdentity, confirmed::add))
		}
		initialRows.filter { it.kind == ReaderTransitionResourceKind.Raster }.forEach { row ->
			assertEquals(ReaderPortCommandResult.Accepted, adapter.drainFrozenOwnership(row.physicalIdentity, confirmed::add))
		}
		releases.forEach { it() }

		assertEquals(
			ReaderPortCommandResult.Rejected(ReaderTransitionFailureReason.InvalidLegacyResource),
			adapter.restoreAfterTransitionActivation(domain)
		)
		assertTrue(adapter.isFrozen)
		val partiallyRestored = adapter.snapshotFrozenOwnership()
		assertEquals(2, partiallyRestored.size)
		assertTrue(
			initialRows.take(2).map { it.physicalIdentity.sourceLocalToken }.toSet() ==
				partiallyRestored.map { it.physicalIdentity.sourceLocalToken }.toSet(),
			"Partial restoration must retain exactly the first two source-local identities"
		)
		secondRestorationFails = false
		assertEquals(ReaderPortCommandResult.Accepted, adapter.restoreAfterTransitionActivation(domain))
		assertFalse(adapter.isFrozen)
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
	fun activatedDispatcherRoutesCancellationToProductionResourcePort() {
		val testPorts = completeActivatedSessionPorts()
		var routed: ReaderTransitionCommand.CancelOwnedWork? = null
		val resources = object : ReaderTransitionResourcePort by requireNotNull(testPorts.resources) {
			override fun cancelOwnedWork(
				command: ReaderTransitionCommand.CancelOwnedWork
			): ReaderPortCommandResult {
				routed = command
				return ReaderPortCommandResult.Accepted
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
		val command = ReaderTransitionCommand.CancelOwnedWork(
			ReaderTransitionId(
				readerSessionGeneration = 17L,
				coordinatorEpoch = 19L,
				sequence = 1L,
				operation = ReaderTransitionOperation.BootstrapNativePage,
				expectedBinding = ReaderExpectedPresentationBinding.Exact(binding)
			)
		)
		val callbackFacts = mutableListOf<paige.navic.reader.ReaderTransitionFact>()

		dispatcher.issue(command, callbackFacts::add)

		assertEquals(command, routed)
		assertTrue(callbackFacts.isEmpty())
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
			ReaderTransitionCommand.ReleaseResource(registration)
		)

		commands.forEach { dispatcher.issue(it) {} }

		assertEquals(commands, routed)
		assertTrue(dispatcher.semanticCommand === production.semantic)
		assertTrue(dispatcher.ownerAndInputPublication === production.ownerAndInput)
		assertTrue(dispatcher.task6FactOnlyTimer === production.factOnlyTimer)
		assertFailsWith<IllegalStateException> {
			dispatcher.clock.schedule(73L) {}
		}
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
			journal = journal
		)

		assertEquals("ReaderLegacyRestorationCheckpoint(<redacted>)", checkpoint.toString())
		assertEquals("ReaderAdoptedPredecessorSeed(<redacted>)", first.adoptedSeed.toString())
		assertEquals(
			"ReaderImportedLegacyResourceRegistration(<redacted>)",
			first.adoptedResource.toString()
		)
		assertEquals("ReaderInitialActivationDecision(<redacted>)", first.toString())
		assertEquals("ReaderActivatedSessionSnapshot(<redacted>)", snapshot.toString())
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
