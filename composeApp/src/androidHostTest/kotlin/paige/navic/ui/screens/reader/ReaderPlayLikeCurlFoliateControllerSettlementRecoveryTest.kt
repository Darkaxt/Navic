package paige.navic.ui.screens.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
import paige.navic.reader.ReaderPresentationFrameOwner
import paige.navic.reader.ReaderPresentationLayer
import paige.navic.reader.ReaderPresentationState
import paige.navic.reader.ReaderPresentationToken
import paige.navic.reader.readerPresentationDecision
import paige.navic.reader.readerPresentationReduce

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
		assertEquals(ReaderPresentationToken(gestureId), claimEvent.frame.token)
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
		assertEquals(ReaderPresentationToken(gestureId), terminalEvent.token)
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
		val staleCallback = ReaderAcceptedDeckCallbackFence(
			presentationToken = token,
			binding = sourceBinding.copy(textureGeneration = 53L)
		)

		val currentCallback = staleCallback.copy(binding = sourceBinding)
		assertTrue(
			readerAcceptedDeckCallbackMatches(
				callback = currentCallback,
				decision = decision,
				currentPreparationGeneration = requireNotNull(sourceBinding.preparationGeneration),
				currentRasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
				currentTextureGeneration = requireNotNull(sourceBinding.textureGeneration)
			)
		)
		assertFalse(
			readerAcceptedDeckCallbackMatches(
				callback = currentCallback.copy(
					binding = sourceBinding.copy(
						destinationCommitIdentity =
							ReaderDestinationCommitIdentity("session-a", 22L)
					)
				),
				decision = decision,
				currentPreparationGeneration = requireNotNull(sourceBinding.preparationGeneration),
				currentRasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
				currentTextureGeneration = requireNotNull(sourceBinding.textureGeneration)
			)
		)

		repeat(2) {
			assertFalse(
				readerAcceptedDeckCallbackMatches(
					callback = staleCallback,
					decision = decision,
					currentPreparationGeneration = requireNotNull(sourceBinding.preparationGeneration),
					currentRasterGeneration = requireNotNull(sourceBinding.rasterGeneration),
					currentTextureGeneration = 54L
				)
			)
			cleanup.request(ReaderRendererCleanupRequest.StaleGeneration(53L))
		}

		assertEquals(1, releaseCount)
		assertEquals(ReaderPresentationLayer.Curl, decision.layer)
		ownerPresent = false
		cleanup.complete(53L)
		assertTrue(cleanup.request(ReaderRendererCleanupRequest.StaleGeneration(53L)))
		assertEquals(1, releaseCount)
	}

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
