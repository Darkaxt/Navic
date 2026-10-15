package paige.navic.ui.screens.reader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import karacken.curl.PageSurfaceView
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderPagePreparationFacts
import paige.navic.reader.ReaderPagePreparationPhase
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationEvent
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationReceiptVersion
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderResourceRetirementOrder
import paige.navic.reader.ReaderRequiredTransition
import paige.navic.reader.ReaderMaterialGenerationAllocation
import paige.navic.reader.ReaderLiveEngineHandoffDirection
import paige.navic.reader.ReaderNativePageHostTokenState
import paige.navic.reader.ReaderPlayLikeCurlDeckTargetIdentity
import paige.navic.reader.ReaderTransitionCommand
import paige.navic.reader.ReaderTransitionDeckRole
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionFrameGeometry
import paige.navic.reader.ReaderTransitionFrameTarget
import paige.navic.reader.ReaderTransitionFrameTargetSpecification
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind
import paige.navic.reader.ReaderTransitionResourceOwnerId
import paige.navic.reader.ReaderTransitionResourceRegistration
import paige.navic.reader.publicationIdentity
import paige.navic.reader.parentIdentity
import paige.navic.reader.readerPresentationEventTransition

class ReaderNativePagePresentationPublisherTest {
	@Test
	fun handoffTimeoutAndFrameRegistrationDrainAndRestoreWithRemainingDeadline() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 3L).copy(
			handoffDirection = ReaderLiveEngineHandoffDirection.LiveEngineToNative
		)
		val transition = ReaderRequiredTransition.PresentNativePage(
			checkNotNull(candidate.transitionToken),
			candidate.binding,
			ReaderLiveEngineHandoffDirection.LiveEngineToNative
		)
		var now = 100L
		val scheduled = mutableListOf<Pair<Runnable, Long>>()
		val removed = mutableListOf<Runnable>()
		val scheduler = object : ReaderPageRelocationDispatchTimeoutScheduler {
			override fun postDelayed(action: Runnable, delayMillis: Long): Boolean {
				scheduled += action to delayMillis
				return true
			}

			override fun removeCallbacks(action: Runnable) {
				removed += action
			}
		}
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			currentHandoffTransition = { transition },
			handoffTimeoutScheduler = scheduler,
			handoffTimeoutMillis = 1_000L,
			handoffNowMillis = { now },
			onEvent = { null }
		)
		publisher.update()
		val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(43L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.freezeForTransitionActivation(domain)
		)
		val rows = publisher.snapshotFrozenOwnership()
		assertEquals(2, rows.size)
		now = 400L
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.forEach { row ->
			assertEquals(
				ReaderPortCommandResult.Accepted,
				publisher.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
			)
		}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertEquals(1, removed.size)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.restoreAfterTransitionActivation(domain)
		)
		assertEquals(listOf(1_000L, 700L), scheduled.map { it.second })
	}

	@Test
	fun legacyPublisherFreezesDrainsAndRestoresPendingFrameCallback() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		val events = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = { event ->
				events += event
				readerTestPresentationReceipt(
					event,
					ReaderPresentationState(binding = candidate.binding)
				)
			}
		)
		publisher.update()
		val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(41L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.freezeForTransitionActivation(domain)
		)
		val row = publisher.snapshotFrozenOwnership().single()
		assertEquals(ReaderTransitionResourceKind.CallbackRegistration, row.kind)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
		)
		assertEquals(listOf(row.physicalIdentity), confirmations)
		assertEquals(listOf(1L), source.cancelledIds)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.restoreAfterTransitionActivation(domain)
		)
		assertEquals(listOf(1L, 2L), source.requestedIds)
		source.present(1L)
		assertTrue(events.isEmpty())
		source.present(2L)
		assertEquals(1, events.size)
	}

	@Test
	fun commandPublisherFreezesExactPreparedTargetAndPendingFrameOwnership() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = { error("command publication must not emit legacy events") }
		)
		val firstId = ReaderTransitionId(
			3L, 5L, 7L,
			ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L)
		)
		val secondId = firstId.copy(sequence = 8L, parent = firstId.parentIdentity())
		publisher.activateCommandOnly { candidate }
		val facts = mutableListOf<ReaderTransitionFact>()
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.prepareTarget(nativePrepareCommand(firstId, candidate), facts::add)
		)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.prepareTarget(nativePrepareCommand(secondId, candidate), facts::add)
		)
		val targets = facts.map { assertIs<ReaderTransitionFact.FrameTargetPrepared>(it).target }
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.present(
				ReaderTransitionCommand.RequestFramePresentation(firstId, targets.first())
			) { }
		)
		val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(37L))
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.freezeForTransitionActivation(domain)
		)
		val rows = publisher.snapshotFrozenOwnership()
		assertEquals(2, rows.size)
		assertEquals(rows.size, rows.map { it.physicalIdentity }.toSet().size)
		assertTrue(rows.all {
			it.physicalIdentity.source == ReaderLegacyInventorySource.FrameOrHandoff
		})
		assertIs<ReaderPortCommandResult.Rejected>(
			publisher.prepareTarget(nativePrepareCommand(firstId, candidate)) { }
		)
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		rows.sortedBy { it.kind != ReaderTransitionResourceKind.CallbackRegistration }
			.forEach { row ->
				assertEquals(
					ReaderPortCommandResult.Accepted,
					publisher.drainFrozenOwnership(row.physicalIdentity, confirmations::add)
				)
			}
		assertEquals(rows.map { it.physicalIdentity }.toSet(), confirmations.toSet())
		assertEquals(listOf(1L), source.cancelledIds)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.restoreAfterTransitionActivation(domain)
		)
		assertEquals(listOf(1L, 2L), source.requestedIds)
		source.present(2L)
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.present(
				ReaderTransitionCommand.RequestFramePresentation(secondId, targets.last())
			) { }
		)
	}

	@Test
	fun finalizedFrozenRetirementAdmitsInstalledFrameCommandsWithoutRestoringLegacy() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		var legacyReads = 0
		val legacyEvents = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { legacyReads += 1; candidate },
			onEvent = { event -> legacyEvents += event; null }
		)
		// Start and drain the original publisher's real callback registration, not an
		// invented activation inventory or a pre-cleared publisher.
		publisher.update()
		assertEquals(listOf(1L), source.requestedIds)
		val domain = ReaderLegacyPhysicalDomain(3L, ReaderLegacyFreezeToken(53L))
		assertEquals(ReaderPortCommandResult.Accepted,
			publisher.freezeForTransitionActivation(domain))
		val row = publisher.snapshotFrozenOwnership().single()
		val confirmations = mutableListOf<ReaderLegacyPhysicalIdentity>()
		assertEquals(ReaderPortCommandResult.Accepted,
			publisher.drainFrozenOwnership(row.physicalIdentity, confirmations::add))
		assertEquals(listOf(row.physicalIdentity), confirmations)
		assertEquals(listOf(1L), source.cancelledIds)
		assertTrue(publisher.frozenFrameRetirementComplete(domain))
		publisher.completeFrozenFrameRetirement(domain)
		val readsAtRetirement = legacyReads
		assertIs<ReaderPortCommandResult.Rejected>(
			publisher.restoreAfterTransitionActivation(domain))
		publisher.update()
		source.present(1L)
		assertEquals(readsAtRetirement, legacyReads)
		assertTrue(legacyEvents.isEmpty())
		assertFalse(publisher.originalLegacyFrameAdmissionRestored)

		// A behavioral assertion on the existing entry point: finalization is not
		// an unfinished freeze, and must not throw when installing its real resolver.
		val activation = runCatching { publisher.activateCommandOnly { candidate } }
		assertTrue(activation.isSuccess,
			"Finalized legacy callback custody must permit installed command activation")
		val id = ReaderTransitionId(3L, 5L, 7L,
			ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
		val prepared = mutableListOf<ReaderTransitionFact>()
		assertEquals(ReaderPortCommandResult.Accepted,
			publisher.prepareTarget(nativePrepareCommand(id, candidate), prepared::add))
		val target = assertIs<ReaderTransitionFact.FrameTargetPrepared>(prepared.single()).target
		val frames = mutableListOf<ReaderTransitionFact>()
		assertEquals(ReaderPortCommandResult.Accepted, publisher.present(
			ReaderTransitionCommand.RequestFramePresentation(id, target), frames::add))
		assertEquals(listOf(1L, 2L), source.requestedIds)
		source.present(1L)
		assertTrue(frames.isEmpty())
		source.present(2L)
		source.present(2L)
		val frame = assertIs<ReaderTransitionFact.PreparedFrame>(frames.single())
		assertEquals(id, frame.transitionId)
		assertEquals(target, frame.target)
		assertEquals(target.resource, frame.resource)
		publisher.update()
		assertEquals(readsAtRetirement, legacyReads)
		assertTrue(legacyEvents.isEmpty())
		assertFalse(publisher.originalLegacyFrameAdmissionRestored)
		assertIs<ReaderPortCommandResult.Rejected>(
			publisher.restoreAfterTransitionActivation(domain))
	}

	@Test
	fun activatedPublisherStartsOnlyFromExactFrameCommandAndEmitsPreparedFact() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		var candidateReads = 0
		val facts = mutableListOf<ReaderTransitionFact>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidateReads += 1; candidate },
			onEvent = { error("Activated publication must not emit legacy events") }
		)
		val id = ReaderTransitionId(
			readerSessionGeneration = 3L,
			coordinatorEpoch = 5L,
			sequence = 7L,
			operation = ReaderTransitionOperation.BootstrapNativePage,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(candidate.binding),
			parent = paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L)
		)
		publisher.activateCommandOnly { specification ->
			candidateReads += 1
			candidate.takeIf { it.binding == specification.binding }
		}
		val preparationFacts = mutableListOf<ReaderTransitionFact>()
		val prepareCommand = nativePrepareCommand(id, candidate)

		publisher.update()
		assertEquals(0, candidateReads)
		assertTrue(source.requestedIds.isEmpty())
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.prepareTarget(prepareCommand, preparationFacts::add)
		)
		assertEquals(1, candidateReads)
		val preparedTarget = assertIs<ReaderTransitionFact.FrameTargetPrepared>(
			preparationFacts.single()
		).target
		assertEquals(
			ReaderPortCommandResult.Accepted,
			publisher.present(
				ReaderTransitionCommand.RequestFramePresentation(id, preparedTarget),
				facts::add
			)
		)
		assertEquals(2, candidateReads)
		assertEquals(listOf(1L), source.requestedIds)
		assertTrue(facts.isEmpty())

		source.present(1L)
		assertEquals(3, candidateReads)

		val fact = assertIs<ReaderTransitionFact.PreparedFrame>(facts.single())
		assertEquals(id, fact.transitionId)
		assertEquals(preparedTarget, fact.target)
		assertEquals(prepareCommand.registration, fact.resource)
		assertEquals(
			ReaderTransitionResourceOwnerId.TransitionOwned(id),
			fact.resource.key.ownerId
		)
		assertEquals(ReaderTransitionResourceKind.Deck, fact.resource.key.kind)
	}

	@Test
	fun activatedPublisherRejectsRetiredTargetBeforePhysicalFrameRequest() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		var current: ReaderNativePagePresentationCandidate? = candidate
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { error("Installed work must not use the legacy resolver") },
			onEvent = { error("Installed work must not publish legacy events") }
		)
		publisher.activateCommandOnly { current }
		val id = ReaderTransitionId(3L, 5L, 7L, ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
		val facts = mutableListOf<ReaderTransitionFact>()
		assertEquals(ReaderPortCommandResult.Accepted,
			publisher.prepareTarget(nativePrepareCommand(id, candidate), facts::add))
		val target = assertIs<ReaderTransitionFact.FrameTargetPrepared>(facts.single()).target
		current = null

		assertIs<ReaderPortCommandResult.Rejected>(publisher.present(
			ReaderTransitionCommand.RequestFramePresentation(id, target)) {
			error("A retired target cannot publish a frame")
		})
		assertTrue(source.requestedIds.isEmpty())
	}

	@Test
	fun activatedPublisherRevalidatesExactCandidateAtPhysicalFrameReturn() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		var current: ReaderNativePagePresentationCandidate? = candidate
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { error("Installed work must not use the legacy resolver") },
			onEvent = { error("Installed work must not publish legacy events") }
		)
		publisher.activateCommandOnly { current }
		val id = ReaderTransitionId(3L, 5L, 7L, ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
		val prepared = mutableListOf<ReaderTransitionFact>()
		assertEquals(ReaderPortCommandResult.Accepted,
			publisher.prepareTarget(nativePrepareCommand(id, candidate), prepared::add))
		val target = assertIs<ReaderTransitionFact.FrameTargetPrepared>(prepared.single()).target
		val returned = mutableListOf<ReaderTransitionFact>()
		assertEquals(ReaderPortCommandResult.Accepted, publisher.present(
			ReaderTransitionCommand.RequestFramePresentation(id, target), returned::add))
		current = candidate.copy(viewportWidth = candidate.viewportWidth + 1)

		source.present(1L)
		source.present(1L)

		assertTrue(returned.none { it is ReaderTransitionFact.PreparedFrame },
			"A real frame for an obsolete candidate is return custody, not display proof")
		val rejected = assertIs<ReaderTransitionFact.CommandRejected>(returned.single())
		assertEquals(id, rejected.transitionId)
		assertEquals(paige.navic.reader.ReaderTransitionCommandStage.FramePresentation, rejected.stage)
		assertEquals(paige.navic.reader.ReaderTransitionCommandRejectionReason.FramePresentationRejected, rejected.reason)
		assertEquals(listOf(1L), source.cancelledIds)
	}

	@Test
	fun keyedInstalledCancellationRetiresOnlyItsOriginalRequestAndPreparedHandles() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		val publisher = ReaderNativePagePresentationPublisher(source, { error("No legacy resolver") },
			onEvent = { error("No legacy event") })
		publisher.activateCommandOnly { candidate }
		val id = ReaderTransitionId(3L, 5L, 7L, ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
		val other = id.copy(sequence = 8L, parent = id.parentIdentity())
		val prepared = mutableListOf<ReaderTransitionFact>()
		publisher.prepareTarget(nativePrepareCommand(id, candidate), prepared::add)
		publisher.prepareTarget(nativePrepareCommand(id, candidate), prepared::add)
		publisher.prepareTarget(nativePrepareCommand(other, candidate), prepared::add)
		val targets = prepared.map { assertIs<ReaderTransitionFact.FrameTargetPrepared>(it).target }
		val returned = mutableListOf<ReaderTransitionFact>()
		assertEquals(ReaderPortCommandResult.Accepted, publisher.present(
			ReaderTransitionCommand.RequestFramePresentation(id, targets[0]), returned::add))
		val unrelated = id.copy(sequence = 9L, parent = other.parentIdentity())
		assertEquals(ReaderPortCommandResult.Accepted, publisher.cancelOwnedWork(unrelated))
		assertTrue(source.cancelledIds.isEmpty())
		assertEquals(ReaderPortCommandResult.Accepted, publisher.cancelOwnedWork(id))
		assertEquals(ReaderPortCommandResult.Accepted, publisher.cancelOwnedWork(id))
		assertEquals(listOf(1L), source.cancelledIds)
		source.present(1L); source.present(1L)
		assertTrue(returned.isEmpty())
		assertIs<ReaderPortCommandResult.Rejected>(publisher.present(
			ReaderTransitionCommand.RequestFramePresentation(id, targets[1]), returned::add))
		assertEquals(ReaderPortCommandResult.Accepted, publisher.present(
			ReaderTransitionCommand.RequestFramePresentation(other, targets[2]), returned::add))
		source.present(2L)
		assertEquals(other, assertIs<ReaderTransitionFact.PreparedFrame>(returned.single()).transitionId)
		publisher.dispose(); publisher.dispose()
		assertEquals(listOf(1L), source.cancelledIds)
	}

	@Test
	fun keyedCancellationDuringOriginalFrameIssuanceRetiresTheReturnedRequestOnce() {
		val candidate = candidate(sequence = 1L)
		val id = ReaderTransitionId(3L, 5L, 7L, ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
		val cancelled = mutableListOf<Long>()
		lateinit var publisher: ReaderNativePagePresentationPublisher
		var lateCallback: ((Long) -> Unit)? = null
		val source = object : ReaderNativePagePresentedFrameSource {
			override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
				lateCallback = onPresented
				onPresented(1L)
				assertIs<ReaderPortCommandResult.Rejected>(publisher.cancelOwnedWork(id),
					"A still-executing request has return custody, not a completed cancellation ACK")
				return 1L
			}
			override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
				cancelled += requestId
				lateCallback?.invoke(requestId)
				return true
			}
		}
		publisher = ReaderNativePagePresentationPublisher(source, { error("No legacy resolver") },
			onEvent = { error("No legacy event") })
		publisher.activateCommandOnly { candidate }
		val prepared = mutableListOf<ReaderTransitionFact>()
		publisher.prepareTarget(nativePrepareCommand(id, candidate), prepared::add)
		val target = assertIs<ReaderTransitionFact.FrameTargetPrepared>(prepared.single()).target
		val returned = mutableListOf<ReaderTransitionFact>()
		assertIs<ReaderPortCommandResult.Rejected>(publisher.present(
			ReaderTransitionCommand.RequestFramePresentation(id, target), returned::add))
		lateCallback?.invoke(1L)
		publisher.dispose()
		assertEquals(listOf(1L), cancelled)
		assertTrue(returned.isEmpty())
	}

	@Test
	fun keyedCancellationInsideOriginalPreparationResolverCannotRetainAHandle() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		val id = ReaderTransitionId(3L, 5L, 7L, ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
		val publisher = ReaderNativePagePresentationPublisher(source, { error("No legacy resolver") },
			onEvent = { error("No legacy event") })
		publisher.activateCommandOnly {
			assertIs<ReaderPortCommandResult.Rejected>(publisher.cancelOwnedWork(id))
			candidate
		}
		val returned = mutableListOf<ReaderTransitionFact>()
		assertIs<ReaderPortCommandResult.Rejected>(publisher.prepareTarget(
			nativePrepareCommand(id, candidate), returned::add))
		assertTrue(returned.isEmpty())
		assertTrue(source.requestedIds.isEmpty())
		assertTrue(source.cancelledIds.isEmpty())
	}

	@Test
	fun keyedCancellationInsidePhysicalReturnResolverCannotEmitPreparedFrame() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		val id = ReaderTransitionId(3L, 5L, 7L, ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
		val publisher = ReaderNativePagePresentationPublisher(source, { error("No legacy resolver") },
			onEvent = { error("No legacy event") })
		var resolutions = 0
		publisher.activateCommandOnly {
			if (++resolutions == 3) {
				assertEquals(ReaderPortCommandResult.Accepted, publisher.cancelOwnedWork(id))
			}
			candidate
		}
		val prepared = mutableListOf<ReaderTransitionFact>()
		publisher.prepareTarget(nativePrepareCommand(id, candidate), prepared::add)
		val target = assertIs<ReaderTransitionFact.FrameTargetPrepared>(prepared.single()).target
		val returned = mutableListOf<ReaderTransitionFact>()
		publisher.present(ReaderTransitionCommand.RequestFramePresentation(id, target), returned::add)
		source.present(1L); source.present(1L)
		assertEquals(listOf(1L), source.cancelledIds)
		assertTrue(returned.isEmpty())
	}

	@Test
	fun failedOriginalFrameCancellationCannotBeRelabeledAppliedOrRepeatedByDispose() {
		for (throws in listOf(false, true)) {
			val candidate = candidate(sequence = 1L)
			val id = ReaderTransitionId(3L, 5L, 7L, ReaderTransitionOperation.BootstrapNativePage,
				ReaderExpectedPresentationBinding.Exact(candidate.binding),
				paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
			var callback: ((Long) -> Unit)? = null
			var cancellations = 0
			val source = object : ReaderNativePagePresentedFrameSource {
				override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
					callback = onPresented
					return 1L
				}
				override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
					cancellations++
					if (throws) error("public-fixture cancellation failed")
					return false
				}
			}
			val publisher = ReaderNativePagePresentationPublisher(source, { error("No legacy resolver") },
				onEvent = { error("No legacy event") })
			publisher.activateCommandOnly { candidate }
			val prepared = mutableListOf<ReaderTransitionFact>()
			publisher.prepareTarget(nativePrepareCommand(id, candidate), prepared::add)
			val target = assertIs<ReaderTransitionFact.FrameTargetPrepared>(prepared.single()).target
			val returned = mutableListOf<ReaderTransitionFact>()
			publisher.present(ReaderTransitionCommand.RequestFramePresentation(id, target), returned::add)
			assertIs<ReaderPortCommandResult.Rejected>(publisher.cancelOwnedWork(id))
			assertIs<ReaderPortCommandResult.Rejected>(publisher.cancelOwnedWork(id))
			callback?.invoke(1L)
			publisher.dispose(); publisher.dispose()
			assertIs<ReaderPortCommandResult.Rejected>(publisher.cancelOwnedWork(id))
			assertEquals(1, cancellations)
			assertTrue(returned.isEmpty())
		}
	}

	@Test
	fun ordinaryNativePublisherCannotConsumeACurlTerminalCommand() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		val publisher = ReaderNativePagePresentationPublisher(source, { error("No legacy resolver") },
			onEvent = { error("No legacy event") })
		publisher.activateCommandOnly { candidate }
		val id = ReaderTransitionId(3L, 5L, 7L, ReaderTransitionOperation.CurlClaimAndSettlement,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
		val ordinary = nativePrepareCommand(id, candidate)
		assertIs<ReaderPortCommandResult.Rejected>(publisher.prepareTarget(ordinary) {
			error("A Native specification cannot stand for Curl terminal custody")
		})
		val native = assertIs<ReaderTransitionFrameTargetSpecification.NativePage>(ordinary.specification)
		val terminal = ReaderTransitionFrameTargetSpecification.CurlSettlementTerminalFrame(
			id, id.readerSessionGeneration, native.publicationGeneration, native.binding, native.allocation,
			paige.navic.reader.ReaderTransitionGestureId(31L),
			paige.navic.reader.ReaderPageTurnSettlementAck("public-terminal", 1, candidate.binding.foliateSessionId,
				requireNotNull(candidate.binding.rasterGeneration), requireNotNull(candidate.binding.textureGeneration)),
			native.deckTarget.copy(role = ReaderTransitionDeckRole.Settlement), native.geometry, 1L)
		assertIs<ReaderPortCommandResult.Rejected>(publisher.prepareTarget(
			ordinary.copy(specification = terminal)) {
			error("Terminal presentation requires the original Curl terminal consumer")
		})
		assertTrue(source.requestedIds.isEmpty())
	}

	@Test
	fun installedFrameIssuanceCannotLoseCustodyOnSynchronousDisposal() {
		val candidate = candidate(sequence = 1L)
		val cancelled = mutableListOf<Long>()
		lateinit var publisher: ReaderNativePagePresentationPublisher
		val source = object : ReaderNativePagePresentedFrameSource {
			override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
				onPresented(1L)
				publisher.dispose()
				return 1L
			}
			override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
				cancelled += requestId
				return true
			}
		}
		publisher = ReaderNativePagePresentationPublisher(source, { error("Legacy resolver must stay fenced") },
			onEvent = { error("No legacy publication") })
		publisher.activateCommandOnly { candidate }
		val id = ReaderTransitionId(3L, 5L, 7L, ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(candidate.binding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L))
		val prepared = mutableListOf<ReaderTransitionFact>()
		assertEquals(ReaderPortCommandResult.Accepted,
			publisher.prepareTarget(nativePrepareCommand(id, candidate), prepared::add))
		val target = assertIs<ReaderTransitionFact.FrameTargetPrepared>(prepared.single()).target
		val returned = mutableListOf<ReaderTransitionFact>()
		assertIs<ReaderPortCommandResult.Rejected>(publisher.present(
			ReaderTransitionCommand.RequestFramePresentation(id, target), returned::add))
		assertEquals(listOf(1L), cancelled)
		assertTrue(returned.isEmpty())
	}

	@Test
	fun activatedPublisherRejectsMismatchedCommandBeforePhysicalFrameWork() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = { error("Activated publication must not emit legacy events") }
		)
		val wrongBinding = candidate.binding.copy(publicationGeneration = 99L)
		val id = ReaderTransitionId(
			3L,
			5L,
			7L,
			ReaderTransitionOperation.BootstrapNativePage,
			ReaderExpectedPresentationBinding.Exact(wrongBinding),
			paige.navic.reader.ReaderTransitionParentIdentity(3L, 5L, 6L)
		)
		publisher.activateCommandOnly { candidate }

		val result = publisher.prepareTarget(
			nativePrepareCommand(id, candidate.copy(binding = wrongBinding))
		) { error("A rejected command cannot emit a fact") }

		assertIs<ReaderPortCommandResult.Rejected>(result)
		assertTrue(source.requestedIds.isEmpty())
	}

	@Test
	fun deckReadyOnlyArmsAndExactPresentedFramePublishesProof() {
		val source = ControllablePresentedFrameSource()
		var candidate: ReaderNativePagePresentationCandidate? = candidate(sequence = 1L)
		val events = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = { event ->
				events += event
				readerTestPresentationReceipt(
					event,
					ReaderPresentationState(binding = requireNotNull(candidate).binding)
				)
			}
		)

		publisher.update()
		publisher.update()

		assertTrue(events.isEmpty())
		assertEquals(listOf(1L), source.requestedIds)
		source.present(1L)

		val event = assertIs<ReaderPresentationEvent.NativePagePresented>(events.single())
		assertEquals(candidate?.binding, event.proof.binding)
		assertEquals(1L, event.proof.presentedFrame)
		assertEquals(candidate?.transitionToken, event.proof.transitionToken)
		publisher.update()
		assertEquals(listOf(1L), source.requestedIds)
	}

	@Test
	fun replacementLossAndDisposalCancelOrFenceLateFrames() {
		val source = ControllablePresentedFrameSource()
		var candidate: ReaderNativePagePresentationCandidate? = candidate(sequence = 1L)
		val events = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = { event ->
				events += event
				readerTestPresentationReceipt(
					event,
					ReaderPresentationState(binding = requireNotNull(candidate).binding)
				)
			}
		)
		publisher.update()

		candidate = candidate(sequence = 2L)
		publisher.update()
		assertEquals(listOf(1L), source.cancelledIds)
		source.present(1L)
		assertTrue(events.isEmpty())
		source.present(2L)
		assertEquals(candidate.binding, assertIs<ReaderPresentationEvent.NativePagePresented>(events.single()).proof.binding)

		candidate = candidate(sequence = 3L)
		publisher.update()
		candidate = null
		publisher.update()
		assertEquals(listOf(1L, 3L), source.cancelledIds)
		source.present(3L)
		assertEquals(1, events.size)

		candidate = candidate(sequence = 4L)
		publisher.update()
		publisher.dispose()
		assertEquals(listOf(1L, 3L, 4L), source.cancelledIds)
		source.present(4L)
		assertEquals(1, events.size)
	}

	@Test
	fun callbackRevalidatesAllCandidateFactsAndSubsequentCurrentFrameCanPublish() {
		val source = ControllablePresentedFrameSource()
		var candidate: ReaderNativePagePresentationCandidate? = candidate(sequence = 1L)
		val events = mutableListOf<ReaderPresentationEvent>()
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = { event ->
				events += event
				readerTestPresentationReceipt(
					event,
					ReaderPresentationState(binding = requireNotNull(candidate).binding)
				)
			}
		)
		publisher.update()

		candidate = candidate?.copy(viewportWidth = 1199)
		source.present(1L)
		assertTrue(events.isEmpty())

		publisher.update()
		assertEquals(listOf(1L, 2L), source.requestedIds)
		source.present(2L)
		assertEquals(1199, assertIs<ReaderPresentationEvent.NativePagePresented>(events.single()).proof.viewportWidth)
	}

	@Test
	fun rejectedAndThrowingCallbacksRearmTheExactNativeCandidate() {
		val source = ControllablePresentedFrameSource()
		val candidate = candidate(sequence = 1L)
		val events = mutableListOf<ReaderPresentationEvent>()
		var attempt = 0
		val publisher = ReaderNativePagePresentationPublisher(
			frameSource = source,
			currentCandidate = { candidate },
			onEvent = { event ->
				events += event
				attempt += 1
				when (attempt) {
					1 -> null
					2 -> error("callback failed")
					else -> readerTestPresentationReceipt(
						event,
						ReaderPresentationState(binding = candidate.binding)
					)
				}
			}
		)

		publisher.update()
		source.present(1L)
		assertEquals(listOf(1L, 2L), source.requestedIds)

		assertFailsWith<IllegalStateException> {
			source.present(2L)
		}
		assertEquals(listOf(1L, 2L, 3L), source.requestedIds)

		source.present(3L)
		publisher.update()

		assertEquals(3, events.size)
		val proofs = events.map {
			assertIs<ReaderPresentationEvent.NativePagePresented>(it).proof
		}
		assertTrue(proofs.all { it.binding == candidate.binding })
		assertTrue(proofs.all { it.transitionToken == candidate.transitionToken })
		assertEquals(listOf(1L, 2L, 3L), proofs.map { it.presentedFrame })
		assertEquals(listOf(1L, 2L, 3L), source.requestedIds)
	}

	@Test
	fun acceptedNativePublicationIgnoresOnlyPreparationProgressCounts() {
		for (retireToken in listOf(false, true)) {
			assertRealAcceptedCandidateChange(retireToken = retireToken, expectAnotherFrame = false) {
				it.copy(preparationFacts = it.preparationFacts.copy(completedCount = 0, requiredCount = 0))
			}
		}
	}

	@Test
	fun acceptedNativePublicationCancelsReentrantProgressOnlyDuplicate() {
		assertRealAcceptedCandidateChange(reentrant = true, expectAnotherFrame = false) {
			it.copy(preparationFacts = it.preparationFacts.copy(completedCount = 0, requiredCount = 0))
		}
	}

	@Test
	fun acceptedNativePublicationStillRequestsChangedIdentityAndNonCounterFacts() {
		for (change in meaningfulCandidateChanges()) {
			assertRealAcceptedCandidateChange(expectAnotherFrame = true, change = change)
		}
	}

	@Test
	fun initialNativeCallbackStillRevalidatesProgressAndAllOtherCandidateFacts() {
		val changes = meaningfulCandidateChanges() + listOf<(ReaderNativePagePresentationCandidate) -> ReaderNativePagePresentationCandidate>(
			{ it.copy(preparationFacts = it.preparationFacts.copy(completedCount = 0)) },
			{ it.copy(preparationFacts = it.preparationFacts.copy(requiredCount = 3)) }
		)
		for (change in changes) {
			val source = ControllablePresentedFrameSource()
			var current = candidate(1L).copy(preparationFacts = candidate(1L).preparationFacts.copy(completedCount = 2, requiredCount = 2))
			var publications = 0
			val publisher = ReaderNativePagePresentationPublisher(source, { current }, onEvent = {
				publications++
				error("Changed initial candidate must not reach the producer")
			})
			publisher.update()
			current = change(current)
			source.present(1L)
			assertEquals(0, publications)
			publisher.dispose()
		}
	}

	private fun meaningfulCandidateChanges(): List<(ReaderNativePagePresentationCandidate) -> ReaderNativePagePresentationCandidate> = listOf(
		{ it.copy(transitionToken = ReaderPresentationToken(99L)) },
		{ it.copy(binding = it.binding.copy(foliateSessionId = "replacement-session",
			destinationCommitIdentity = it.binding.destinationCommitIdentity?.copy(foliateSessionId = "replacement-session"))) },
		{ it.copy(binding = it.binding.copy(publicationGeneration = it.binding.publicationGeneration + 1L)) },
		{ it.copy(binding = it.binding.copy(viewportGeneration = it.binding.viewportGeneration + 1L)) },
		{ it.copy(binding = it.binding.copy(profileGeneration = it.binding.profileGeneration + 1L)) },
		{ it.copy(binding = it.binding.copy(destinationCommitIdentity = it.binding.destinationCommitIdentity?.copy(commitSequence = 9L))) },
		{ it.copy(binding = it.binding.copy(preparationGeneration = 99L)) },
		{ it.copy(binding = it.binding.copy(rasterGeneration = 99L)) },
		{ it.copy(binding = it.binding.copy(textureGeneration = 99L)) },
		{ it.copy(visualPageIndex = it.visualPageIndex + 1) },
		{ it.copy(viewportWidth = it.viewportWidth + 1) },
		{ it.copy(viewportHeight = it.viewportHeight + 1) },
		{ it.copy(handoffDirection = paige.navic.reader.ReaderLiveEngineHandoffDirection.LiveEngineToNative) },
		{ it.copy(preparationFacts = it.preparationFacts.copy(phase = ReaderPagePreparationPhase.Preparing)) },
		{ it.copy(preparationFacts = it.preparationFacts.copy(generation = 99L)) },
		{ it.copy(preparationFacts = it.preparationFacts.copy(readiness = it.preparationFacts.readiness.copy(textureDeck = paige.navic.reader.ReaderTextureDeckState.Ready))) },
		{ it.copy(preparationFacts = it.preparationFacts.copy(failure = paige.navic.reader.ReaderPresentationFailureReason.TimedOut)) },
		{ it.copy(preparationFacts = it.preparationFacts.copy(retryable = true)) }
	)

	private fun assertRealAcceptedCandidateChange(
		retireToken: Boolean = true,
		reentrant: Boolean = false,
		expectAnotherFrame: Boolean,
		change: (ReaderNativePagePresentationCandidate) -> ReaderNativePagePresentationCandidate
	) {
		val source = ControllablePresentedFrameSource()
		val initial = candidate(1L).let { it.copy(preparationFacts = it.preparationFacts.copy(completedCount = 2, requiredCount = 2)) }
		var state = ReaderPresentationState(binding = initial.binding, preparationFacts = initial.preparationFacts)
		var version = ReaderPresentationReceiptVersion(1L, initial.binding.publicationIdentity, 0L)
		val requested = readerPresentationEventTransition(state, version, false, ReaderPresentationEvent.NativePageRequested).receipt
		assertTrue(requested.authorizes(ReaderPresentationEvent.NativePageRequested))
		state = requested.postState
		version = requested.version
		val request = requireNotNull(assertIs<ReaderPresentationAuthority.BlockingPreparation>(state.authority).nativePresentationRequest)
		var current = initial.copy(transitionToken = request.token)
		var publications = 0
		lateinit var publisher: ReaderNativePagePresentationPublisher
		publisher = ReaderNativePagePresentationPublisher(source, { current }, onEvent = { event ->
			publications++
			val receipt = readerPresentationEventTransition(state, version, false, event).receipt
			assertTrue(receipt.authorizes(event), "The first native receipt must be genuinely authorizing")
			state = receipt.postState
			version = receipt.version
			if (reentrant) {
				current = change(current.copy(transitionToken = if (retireToken) null else current.transitionToken))
				publisher.update()
			}
			receipt
		})
		try {
			publisher.update()
			source.present(1L)
			assertEquals(1, publications)
			assertIs<ReaderPresentationAuthority.SettledNativePage>(state.authority)
			if (!reentrant) current = change(current.copy(transitionToken = if (retireToken) null else current.transitionToken))
			publisher.update()
			if (reentrant) {
				assertEquals(listOf(1L, 2L), source.requestedIds)
				assertEquals(listOf(2L), source.cancelledIds, "Retire the real pending progress-only duplicate")
				source.present(2L) // The original late callback must remain fenced after cancellation.
				assertEquals(1, publications)
			} else {
				assertEquals(if (expectAnotherFrame) listOf(1L, 2L) else listOf(1L), source.requestedIds)
			}
		} finally { publisher.dispose() }
	}

	private fun nativePrepareCommand(
		id: ReaderTransitionId,
		candidate: ReaderNativePagePresentationCandidate
	): ReaderTransitionCommand.PrepareFrameTarget {
		val binding = candidate.binding
		val allocation = ReaderMaterialGenerationAllocation(
			id,
			binding,
			requireNotNull(binding.preparationGeneration),
			requireNotNull(binding.rasterGeneration),
			requireNotNull(binding.textureGeneration)
		)
		val specification = ReaderTransitionFrameTargetSpecification.NativePage(
			transitionId = id,
			readerSessionGeneration = id.readerSessionGeneration,
			publicationGeneration = binding.publicationGeneration,
			binding = binding,
			allocation = allocation,
			hostToken = candidate.transitionToken?.let {
				ReaderNativePageHostTokenState.Present(
					paige.navic.reader.ReaderNativePageHostToken(it.value)
				)
			} ?: ReaderNativePageHostTokenState.AuthoritativeAbsent,
			deckTarget = ReaderPlayLikeCurlDeckTargetIdentity(
				rendererGeneration = 1L,
				deckGeneration = requireNotNull(binding.textureGeneration),
				role = ReaderTransitionDeckRole.Initial
			),
			geometry = ReaderTransitionFrameGeometry(
				binding.viewportGeneration,
				binding.profileGeneration,
				0,
				0,
				candidate.viewportWidth,
				candidate.viewportHeight
			),
			requestSequence = 1L
		)
		return ReaderTransitionCommand.PrepareFrameTarget(
			id,
			specification,
			ReaderTransitionResourceRegistration(
				ReaderTransitionResourceKey(
					ReaderTransitionResourceOwnerId.TransitionOwned(id),
					ReaderTransitionResourceKind.Deck,
					requireNotNull(binding.textureGeneration)
				),
				ReaderResourceRetirementOrder(
					id.readerSessionGeneration,
					id.coordinatorEpoch,
					1L
				)
			)
		)
	}

	private fun candidate(sequence: Long) = ReaderNativePagePresentationCandidate(
		binding = ReaderPresentationBinding(
			foliateSessionId = "fixture-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 3L,
			destinationCommitIdentity = ReaderDestinationCommitIdentity("fixture-session", sequence),
			rasterGeneration = 4L + sequence,
			textureGeneration = 5L + sequence,
			preparationGeneration = 6L + sequence
		),
		transitionToken = ReaderPresentationToken(20L + sequence).takeIf { sequence > 2L },
		visualPageIndex = sequence.toInt(),
		viewportWidth = 1200,
		viewportHeight = 800,
		preparationFacts = ReaderPagePreparationFacts(
			phase = ReaderPagePreparationPhase.Ready,
			generation = 6L + sequence
		)
	)
}

class ReaderPresentedFrameCallerSourceTest {
	@Test
	fun productionSurfaceFrameConsumersAreExplicitAndShareThePageSurfaceBroker() {
		val sourceRoot = File("src/androidMain/kotlin")
		val callers = sourceRoot.walkTopDown()
			.filter { file ->
				file.isFile &&
					file.extension == "kt" &&
					"requestNextPresentedFrame" in file.readText()
			}
			.map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
			.toSet()

		assertEquals(
			setOf(
				"paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt",
				"paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt",
				"paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt",
				"paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt"
			),
			callers
		)
		assertTrue(
			File(sourceRoot, "paige/navic/ui/screens/reader/ReaderPageTurnBitmapSource.android.kt")
				.readText()
				.contains("rendererSurface.requestNextPresentedFrame")
		)
		assertTrue(
			File(sourceRoot, "paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt")
				.readText()
				.contains("surfaceView.requestNextPresentedFrame")
		)
		assertTrue(
			File(sourceRoot, "paige/navic/ui/screens/reader/ReaderPresentationHostBridge.android.kt")
				.readText()
				.contains("surface.requestNextPresentedFrame")
		)
		val host = File(sourceRoot, "paige/navic/ui/screens/reader/KomikkuReaderNativeFrameHost.android.kt").readText()
		val forwarding = host
			.substringAfter("private val nativePagePresentationPublisher by lazy {", missingDelimiterValue = "")
			.substringBefore("tokenAllocator = frameTokenAllocator", missingDelimiterValue = "")
		assertContains(forwarding, "frameSource = object : ReaderNativePagePresentedFrameSource")
		assertContains(forwarding, "playLikeCurlController.presentedFrameSource.requestNextPresentedFrame(onPresented)")
		assertContains(forwarding, "playLikeCurlController.presentedFrameSource.cancelPresentedFrameRequest(requestId)")
		val controller = File(sourceRoot, "paige/navic/ui/screens/reader/ReaderPlayLikeCurlFoliateController.android.kt").readText()
		val sameSurface = controller
			.substringAfter("val presentedFrameSource: ReaderNativePagePresentedFrameSource by lazy {", missingDelimiterValue = "")
			.substringBefore("\n\t}", missingDelimiterValue = "")
		assertContains(sameSurface, "ReaderPageSurfacePresentedFrameSource(surfaceView)")
	}
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ReaderPageSurfacePresentedFrameSourceTest {
	@Test
	fun productionAdapterBindsCallbackAndCancellationToPageSurfaceRequestId() {
		val surface = RecordingPageSurfaceView(
			ApplicationProvider.getApplicationContext()
		)
		val source = ReaderPageSurfacePresentedFrameSource(surface)
		val frames = mutableListOf<Long>()

		val requestId = source.requestNextPresentedFrame(frames::add)
		assertEquals(71L, requestId)
		assertTrue(frames.isEmpty())
		surface.present()
		assertEquals(listOf(71L), frames)
		assertTrue(source.cancelPresentedFrameRequest(71L))
		assertEquals(listOf(71L), surface.cancelledIds)
		assertFalse(source.cancelPresentedFrameRequest(PageSurfaceView.NO_PRESENTED_FRAME_REQUEST_ID))
	}

	private class RecordingPageSurfaceView(context: Context) : PageSurfaceView(context) {
		private var callback: Runnable? = null
		val cancelledIds = mutableListOf<Long>()

		override fun requestNextPresentedFrame(callback: Runnable): Long {
			this.callback = callback
			return 71L
		}

		override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
			if (requestId == NO_PRESENTED_FRAME_REQUEST_ID) return false
			cancelledIds += requestId
			return true
		}

		fun present() {
			callback?.run()
		}
	}
}

private class ControllablePresentedFrameSource : ReaderNativePagePresentedFrameSource {
	private var nextId = 1L
	private val callbacks = mutableMapOf<Long, (Long) -> Unit>()
	val requestedIds = mutableListOf<Long>()
	val cancelledIds = mutableListOf<Long>()

	override fun requestNextPresentedFrame(onPresented: (Long) -> Unit): Long {
		val id = nextId++
		requestedIds += id
		callbacks[id] = onPresented
		return id
	}

	override fun cancelPresentedFrameRequest(requestId: Long): Boolean {
		cancelledIds += requestId
		return true
	}

	fun present(requestId: Long) {
		callbacks[requestId]?.invoke(requestId)
	}
}
