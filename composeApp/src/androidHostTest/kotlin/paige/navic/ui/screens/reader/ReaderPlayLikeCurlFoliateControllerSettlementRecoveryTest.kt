package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import paige.navic.reader.ReaderCurlPresentationFrame
import paige.navic.reader.ReaderCurlSettlementStage
import paige.navic.reader.ReaderDestinationCommitIdentity
import paige.navic.reader.ReaderNativePagePresentationProof
import paige.navic.reader.ReaderPageGestureTerminalOutcome
import paige.navic.reader.ReaderPageRelocationQueue
import paige.navic.reader.ReaderPageTurnSettlementAck
import paige.navic.reader.ReaderPageRelocationRequest
import paige.navic.reader.ReaderPageRelocationToken
import paige.navic.reader.ReaderPageTurnDirection
import paige.navic.reader.ReaderPresentationAuthority
import paige.navic.reader.ReaderPresentationBinding
import paige.navic.reader.ReaderPresentationDecision
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationInputPolicy
import paige.navic.reader.ReaderPresentationLayer
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.ReaderPresentationTokenDomain
import paige.navic.reader.readerPresentationDecision
import paige.navic.reader.readerPresentationReduce
import paige.navic.reader.publicationIdentity
import paige.navic.reader.ReaderActiveTransition
import paige.navic.reader.ReaderExpectedPresentationBinding
import paige.navic.reader.ReaderTransitionFact
import paige.navic.reader.ReaderTransitionId
import paige.navic.reader.ReaderTransitionJournal
import paige.navic.reader.ReaderTransitionLivenessTable
import paige.navic.reader.ReaderTransitionOperation
import paige.navic.reader.ReaderTransitionPhaseKind
import paige.navic.reader.ReaderTransitionProofKind
import paige.navic.reader.ReaderTransitionResourceKey
import paige.navic.reader.ReaderTransitionResourceKind

class ReaderPlayLikeCurlFoliateControllerSettlementRecoveryTest {
	@Test
	fun rejectedTerminalInvalidatesPromotedRendererBeforeRepreparingSource() {
		val events = mutableListOf<String>()
		var currentOrdinal = 9

		recoverRejectedReaderSettlement(
			sourceOrdinal = 8,
			promotedGeneration = 42L,
			rendererEnabled = true,
			restoreSourceOrdinal = { ordinal ->
				currentOrdinal = ordinal
				events += "restore:$ordinal"
			},
			invalidateRenderer = { reason -> events += "invalidate:$reason" },
			requestPrewarm = { events += "prewarm" }
		)

		assertEquals(8, currentOrdinal)
		assertEquals(
			listOf(
				"restore:8",
				"invalidate:settlement-terminal-rejected:42",
				"prewarm"
			),
			events
		)
	}

	@Test
	fun rejectedTerminalDoesNotReprepareDisabledRenderer() {
		val events = mutableListOf<String>()

		recoverRejectedReaderSettlement(
			sourceOrdinal = 8,
			promotedGeneration = 42L,
			rendererEnabled = false,
			restoreSourceOrdinal = { ordinal -> events += "restore:$ordinal" },
			invalidateRenderer = { reason -> events += "invalidate:$reason" },
			requestPrewarm = { events += "prewarm" }
		)

		assertEquals(
			listOf(
				"restore:8",
				"invalidate:settlement-terminal-rejected:42"
			),
			events
		)
	}

	@Test
	fun controllerFactsBindOnlyThePhysicallyClaimedGestureToItsExactSettlement() {
		val sourceBinding = binding(destinationSequence = 30L, textureGeneration = 60L)
		val settled = settledState(sourceBinding, presentedFrame = 61L)
		val gestureId = 62L
		val claimEvent = requireNotNull(
			readerCurlClaimEvent(
				decision = readerPresentationDecision(settled),
				gestureId = gestureId
			)
		)
		assertEquals(
			ReaderPresentationToken(gestureId, ReaderPresentationTokenDomain.Gesture),
			claimEvent.frame.token
		)
		val claimed = readerPresentationReduce(settled, claimEvent)
		val request = ReaderPageRelocationRequest(
			token = ReaderPageRelocationToken("page-turn-62"),
			gestureId = gestureId,
			rasterGeneration = 4L,
			textureGeneration = 63L,
			sourceOrdinal = 30,
			destinationOrdinal = 31,
			logicalDirection = ReaderPageTurnDirection.Next,
			foliateSessionId = sourceBinding.foliateSessionId
		)

		val terminalEvent = requireNotNull(
			readerCurlTerminalEvent(
				decision = claimed.decision,
				gestureId = gestureId,
				settlementRequest = request
			)
		)
		assertEquals(
			ReaderPresentationToken(gestureId, ReaderPresentationTokenDomain.Gesture),
			terminalEvent.token
		)
		assertEquals(
			acknowledgement(
				request.token.value,
				request.destinationOrdinal,
				sourceBinding.copy(textureGeneration = request.textureGeneration)
			),
			terminalEvent.expectedAcknowledgement
		)
		assertFalse(
			readerCurlTerminalEvent(
				decision = claimed.decision,
				gestureId = gestureId + 1L,
				settlementRequest = request
			) != null
		)
	}

	@Test
	fun rejectedCommonCurlClaimCancelsTheExactPhysicalClaimAndReleasesOwnership() {
		val sourceBinding = binding(destinationSequence = 40L, textureGeneration = 70L)
		val sourceState = settledState(sourceBinding, presentedFrame = 71L)
		val presentationState = sourceState
		val gestureId = 72L
		val cancelledGestures = mutableListOf<Long>()
		var foregroundReleaseCount = 0
		val ownership = ReaderForegroundWebViewOwnership {
			foregroundReleaseCount += 1
		}
		val queue = ReaderPageRelocationQueue(capacity = 1)
		val coordinator = ReaderPageRelocationGestureCoordinator(queue, ownership)

		val result = coordinator.start(
			metadata = ReaderPageRelocationReservationMetadata(
				gestureId = gestureId,
				sourceOrdinal = 40,
				foliateSessionId = sourceBinding.foliateSessionId,
				reservedRasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
				reservedTextureGeneration = requireNotNull(sourceBinding.textureGeneration)
			),
			protocolActionMasked = 0,
			rendererAdmission = {
				admitReaderCurlRendererClaim(
					gestureId = gestureId,
					admitRenderer = { true },
					publishClaim = {
						assertTrue(
							readerCurlClaimEvent(
								readerPresentationDecision(presentationState),
								gestureId
							) != null
						)
						false
					},
					cancelRendererClaim = cancelledGestures::add
				)
			},
			publishTerminal = { outcome, _ ->
				assertEquals(
					ReaderPageGestureTerminalOutcome.RejectedRendererUnavailable,
					outcome
				)
				true
			}
		)

		assertIs<ReaderPageRelocationStartResult.TerminalPublished>(result)
		assertEquals(listOf(gestureId), cancelledGestures)
		assertEquals(0, coordinator.reservationCount())
		assertEquals(0, queue.occupiedCount())
		assertEquals(0, ownership.snapshot().liveClaims)
		assertEquals(1, foregroundReleaseCount)
		assertEquals(sourceState, presentationState)
		assertIs<ReaderPresentationAuthority.SettledNativePage>(presentationState.authority)
		assertIs<ReaderPresentationFrameOwner.NativePage>(
			readerPresentationDecision(presentationState).frameOwner
		)
	}

	@Test
	fun provisionalStartupBindingIssuesImmutableCurrentProfileAdmission() {
		val provisionalBinding = ReaderPresentationBinding(
			foliateSessionId = "startup-session",
			publicationGeneration = 1L,
			viewportGeneration = 2L,
			profileGeneration = 0L,
			preparationGeneration = 5L
		)
		val decision = readerPresentationDecision(
			ReaderPresentationState(binding = provisionalBinding)
		)
		val request = ReaderDeckAdmissionRequest(
			candidateDecision = decision,
			profileGeneration = 3L,
			preparationGeneration = 5L,
			rasterGeneration = 4L,
			textureGeneration = 6L,
			role = ReaderDeckSubmissionRole.Active
		)
		val provisionalAdmission = assertNotNull(
			readerDeckAdmissionCapabilityOrNull(
				authority = admissionAuthority(provisionalBinding),
				admissionId = 1L,
				request = request
			)
		)
		val resolvedAdmission = assertNotNull(
			readerDeckAdmissionCapabilityOrNull(
				authority = admissionAuthority(
					provisionalBinding.copy(profileGeneration = 3L)
				),
				admissionId = 2L,
				request = request
			)
		)

		assertEquals(3L, provisionalAdmission.profileGeneration)
		assertEquals(provisionalAdmission.originBinding, resolvedAdmission.originBinding)
		assertEquals(ReaderDeckAdmissionSlot.Active, provisionalAdmission.slot)
		assertNull(
			readerDeckAdmissionCapabilityOrNull(
				authority = admissionAuthority(
					provisionalBinding.copy(profileGeneration = 7L)
				),
				admissionId = 3L,
				request = request
			)
		)

		val owner = ReaderDeckAdmission(provisionalAdmission)
		assertEquals(
			ReaderDeckAdmissionCallbackDisposition.AwaitingRendererOwnership,
			owner.observeCallback()
		)
		assertEquals(ReaderDeckAdmissionState.Reserved, owner.state)
		assertEquals(
			ReaderDeckAdmissionOwnershipDisposition.ValidateCallback,
			owner.acknowledgeRendererOwnership()
		)
		assertEquals(ReaderDeckAdmissionState.CallbackObserved, owner.state)
	}

	@Test
	fun staleAndPendingAcceptedDeckCallbacksReleaseExactlyOnceWithoutChangingAuthority() {
		val sourceBinding = binding(destinationSequence = 20L, textureGeneration = 50L)
		val token = ReaderPresentationToken(51L)
		val frame = ReaderPresentationFrameOwner.Curl(
			ReaderCurlPresentationFrame(
				token = token,
				binding = sourceBinding,
				presentedFrame = 52L,
				viewportWidth = 1200,
				viewportHeight = 800,
				rasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
				textureGeneration = requireNotNull(sourceBinding.textureGeneration)
			)
		)
		val state = ReaderPresentationState(
			authority = ReaderPresentationAuthority.CurlSettlementPending(
				retainedFrame = frame,
				binding = sourceBinding,
				stage = ReaderCurlSettlementStage.AwaitingFoliate,
				expectedAcknowledgement = acknowledgement(
					"page-turn-51",
					21,
					sourceBinding.copy(
						destinationCommitIdentity = ReaderDestinationCommitIdentity("session-a", 21L),
						textureGeneration = 53L
					)
				)
			),
			binding = sourceBinding
		)
		val decision = readerPresentationDecision(state)
		var ownerPresent = true
		var releaseCount = 0
		val cleanup = ReaderRendererCleanupRetryCoordinator(
			ownerExists = { ownerPresent },
			requestRelease = {
				releaseCount += 1
				true
			},
			onAccepted = {},
			pendingLimit = 2
		)
		val request = ReaderDeckAdmissionRequest(
			candidateDecision = decision,
			profileGeneration = sourceBinding.profileGeneration,
			preparationGeneration = requireNotNull(sourceBinding.preparationGeneration),
			rasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
			textureGeneration = requireNotNull(sourceBinding.textureGeneration),
			role = ReaderDeckSubmissionRole.Active
		)
		val admission = assertNotNull(
			readerDeckAdmissionCapabilityOrNull(
				authority = admissionAuthority(sourceBinding, token, decision),
				admissionId = 1L,
				request = request
			)
		)
		assertTrue(readerDeckAdmissionAuthorityMatches(admissionAuthority(sourceBinding, token, decision), admission))
		assertTrue(
			readerDeckAdmissionAuthorityMatches(
				admissionAuthority(sourceBinding, token, decision).copy(
					authorityVersion = admission.authorityVersion.copy(eventSequence = 2L)
				),
				admission
			),
			"A newer receipt with the identical normalized decision remains compatible"
		)
		assertFalse(
			readerDeckAdmissionAuthorityMatches(
				admissionAuthority(sourceBinding, token, decision).copy(
					authorityVersion = admission.authorityVersion.copy(eventSequence = 0L)
				),
				admission
			),
			"An older authority version must revoke the admission"
		)
		assertFalse(
			readerDeckAdmissionAuthorityMatches(
				admissionAuthority(sourceBinding, token, decision).copy(
					decision = decision.copy(
						inputPolicy = ReaderPresentationInputPolicy.RecoveryOnly
					)
				),
				admission
			),
			"Decision drift at the same authority version must revoke the admission"
		)
		assertFalse(
			readerDeckAdmissionAuthorityMatches(
				admissionAuthority(sourceBinding, token, decision).copy(
					authorityVersion = admission.authorityVersion.copy(eventSequence = 2L),
					decision = decision.copy(
						inputPolicy = ReaderPresentationInputPolicy.RecoveryOnly
					)
				),
				admission
			),
			"A newer authority version cannot conceal incompatible decision drift"
		)
		assertFalse(
			readerDeckAdmissionAuthorityMatches(
				admissionAuthority(
					sourceBinding.copy(
						destinationCommitIdentity =
							ReaderDestinationCommitIdentity("session-a", 22L)
					),
					token
				),
				admission
			)
		)

		repeat(2) {
			cleanup.request(ReaderRendererCleanupRequest.StaleGeneration(53L))
		}

		assertEquals(1, releaseCount)
		assertEquals(ReaderPresentationLayer.Curl, decision.layer)
		ownerPresent = false
		cleanup.complete(53L)
		assertTrue(cleanup.request(ReaderRendererCleanupRequest.StaleGeneration(53L)))
		assertEquals(1, releaseCount)
	}

	@Test
	fun rasterAndDeckProofsStillAwaitPreparedFrameWithoutCommittingReadiness() {
		val exactBinding = binding(destinationSequence = 80L, textureGeneration = 83L)
		val id = ReaderTransitionId(
			readerSessionGeneration = 89L,
			coordinatorEpoch = 97L,
			sequence = 101L,
			operation = ReaderTransitionOperation.CoverToPageEntry,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(exactBinding)
		)
		val retained = ReaderPresentationFrameOwner.NativePage(nativeProof(exactBinding, 103L))
		val key = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, 83L)
		var journal = ReaderTransitionJournal(
			active = ReaderActiveTransition(
				id = id,
				phase = ReaderTransitionLivenessTable.phase(
					id,
					ReaderTransitionPhaseKind.AwaitingPrerequisites,
					retained
				)
			)
		)

		journal = journal.reduce(ReaderTransitionFact.RasterProven(id)).state
		assertTrue(journal.active != null)
		assertTrue(
			ReaderTransitionProofKind.PreparedFrame in
				assertNotNull(journal.active).phase.contract.awaitedProofs
		)
		journal = journal.reduce(ReaderTransitionFact.DeckPrepared(id, key)).state
		assertTrue(journal.active != null)
		journal = journal.reduce(ReaderTransitionFact.DeckOwned(id, key)).state

		assertTrue(journal.active != null)
		assertNull(journal.committed)
		assertTrue(
			ReaderTransitionProofKind.PreparedFrame in
				assertNotNull(journal.active).phase.contract.awaitedProofs
		)
	}

	@Test
	fun callbackAndOwnershipOrderingsConvergeOnTheSameExactLeaseFacts() {
		val exactBinding = binding(destinationSequence = 90L, textureGeneration = 107L)
		val id = ReaderTransitionId(
			readerSessionGeneration = 109L,
			coordinatorEpoch = 113L,
			sequence = 127L,
			operation = ReaderTransitionOperation.RendererRecovery,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(exactBinding)
		)
		val key = ReaderTransitionResourceKey(id, ReaderTransitionResourceKind.Deck, 107L)
		val lease = assertNotNull(
			readerDeckLeaseOrNull(
				paige.navic.reader.ReaderTransitionCommand.ReserveDeck(
					id,
					exactBinding,
					paige.navic.reader.ReaderTransitionDeckRole.Recovery
				),
				key
			)
		)

		fun reduced(order: List<(ReaderDeckLeaseFactEmitter) -> Unit>): ReaderTransitionJournal {
			var journal = ReaderTransitionJournal(
				active = ReaderActiveTransition(
					id,
					ReaderTransitionLivenessTable.phase(
						id,
						ReaderTransitionPhaseKind.AwaitingProof,
						ReaderPresentationFrameOwner.NativePage(nativeProof(exactBinding, 131L)),
						satisfiedProofs = setOf(
							ReaderTransitionProofKind.RendererGeneration
						)
					)
				)
			)
			val emitter = ReaderDeckLeaseFactEmitter(lease) { fact -> journal = journal.reduce(fact).state }
			order.forEach { it(emitter) }
			return journal
		}

		val callbackFirst = reduced(listOf({ it.onPrepared(lease) }, { it.onOwned(lease) }))
		val ownershipFirst = reduced(listOf({ it.onOwned(lease) }, { it.onPrepared(lease) }))

		assertEquals(callbackFirst, ownershipFirst)
	}

	@Test
	fun rendererRecoveryAllocatesFreshTransitionPreparationRasterAndTextureGenerations() {
		val previousBinding = binding(destinationSequence = 100L, textureGeneration = 137L)
		val previousId = ReaderTransitionId(
			readerSessionGeneration = 139L,
			coordinatorEpoch = 149L,
			sequence = 151L,
			operation = ReaderTransitionOperation.CoverToPageEntry,
			expectedBinding = ReaderExpectedPresentationBinding.Exact(previousBinding)
		)
		val allocator = ReaderDeckRecoveryLeaseAllocator()

		val recovered = allocator.next(previousId, previousBinding)
		val retried = allocator.next(previousId, previousBinding)

		assertEquals(ReaderTransitionOperation.RendererRecovery, recovered.transitionId.operation)
		assertTrue(recovered.transitionId.sequence > previousId.sequence)
		assertTrue(recovered.preparationGeneration > requireNotNull(previousBinding.preparationGeneration))
		assertTrue(recovered.rasterGeneration > requireNotNull(previousBinding.rasterGeneration))
		assertTrue(recovered.textureGeneration > requireNotNull(previousBinding.textureGeneration))
		assertEquals(recovered.textureGeneration, recovered.resourceKey.opaqueId)
		assertEquals(recovered.transitionId, recovered.resourceKey.transitionId)
		assertTrue(retried.transitionId.sequence > recovered.transitionId.sequence)
		assertTrue(retried.preparationGeneration > recovered.preparationGeneration)
		assertTrue(retried.rasterGeneration > recovered.rasterGeneration)
		assertTrue(retried.textureGeneration > recovered.textureGeneration)
	}

	private fun admissionAuthority(
		binding: ReaderPresentationBinding,
		token: ReaderPresentationToken? = null,
		decision: ReaderPresentationDecision = readerPresentationDecision(
			ReaderPresentationState(binding = binding)
		)
	) = ReaderDeckAdmissionAuthoritySnapshot(
		hostEpoch = 1L,
		authorityVersion = paige.navic.reader.ReaderPresentationReceiptVersion(
			1L,
			binding.publicationIdentity,
			1L
		),
		viewerGeneration = 1L,
		lifecycle = paige.navic.reader.ReaderPresentationLifecycleState.Foreground,
		bindingSeed = binding,
		presentationToken = token,
		decision = decision
	)

	private fun settledState(
		binding: ReaderPresentationBinding,
		presentedFrame: Long
	) = ReaderPresentationState(
		authority = ReaderPresentationAuthority.SettledNativePage(
			ReaderPresentationFrameOwner.NativePage(nativeProof(binding, presentedFrame))
		),
		binding = binding
	)

	private fun binding(
		destinationSequence: Long,
		textureGeneration: Long
	) = ReaderPresentationBinding(
		foliateSessionId = "session-a",
		publicationGeneration = 1L,
		viewportGeneration = 2L,
		profileGeneration = 3L,
		destinationCommitIdentity = ReaderDestinationCommitIdentity(
			"session-a",
			destinationSequence
		),
		rasterGeneration = 4L,
		textureGeneration = textureGeneration,
		preparationGeneration = 5L
	)

	private fun acknowledgement(
		token: String,
		pageIndex: Int,
		binding: ReaderPresentationBinding
	) = ReaderPageTurnSettlementAck(
		token = token,
		pageIndex = pageIndex,
		foliateSessionId = binding.foliateSessionId,
		rasterGeneration = requireNotNull(binding.rasterGeneration),
		textureGeneration = requireNotNull(binding.textureGeneration)
	)

	private fun nativeProof(
		binding: ReaderPresentationBinding,
		presentedFrame: Long
	) = ReaderNativePagePresentationProof(
		binding = binding,
		transitionToken = null,
		presentedFrame = presentedFrame,
		viewportWidth = 1200,
		viewportHeight = 800,
		rasterGeneration = requireNotNull(binding.rasterGeneration),
		textureGeneration = requireNotNull(binding.textureGeneration)
	)
}
