package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderInstalledSemanticReceiptPolicyTest {
    private val predecessor = ReaderPresentationBinding("synthetic-session", 1L, 2L, 3L,
        ReaderDestinationCommitIdentity("synthetic-session", 4L), 5L, 6L, 7L)
    private val successor = predecessor.copy(destinationCommitIdentity =
        ReaderDestinationCommitIdentity(predecessor.foliateSessionId, 5L),
        preparationGeneration = null, rasterGeneration = null, textureGeneration = null)
    // These generations belong to the genuine source settlement, not successor material.
    private val acknowledgement = ReaderPageTurnSettlementAck("synthetic-turn", 8,
        predecessor.foliateSessionId, 5L, 6L)
    private val origin = ReaderPresentationEventOrigin.SemanticCommand(
        ReaderTransitionId(1L, 1L, 1L, ReaderTransitionOperation.CurlClaimAndSettlement,
            ReaderExpectedPresentationBinding.SemanticSuccessor(predecessor, 1L)),
        ReaderSemanticCommandSlotId(1L))
    private val version = ReaderPresentationReceiptVersion(1L, predecessor.publicationIdentity, 10L)
    private val event = ReaderPresentationEvent.FoliateRelocated(successor, acknowledgement)

    private fun nativeState(): ReaderPresentationState {
        val proof = ReaderNativePagePresentationProof(predecessor, null, 1L, 600, 800, 5L, 6L)
        return ReaderPresentationState(
            authority = ReaderPresentationAuthority.SettledNativePage(ReaderPresentationFrameOwner.NativePage(proof)),
            binding = predecessor,
            preparationFacts = ReaderPagePreparationFacts(phase = ReaderPagePreparationPhase.Ready, generation = 7L,
                readiness = ReaderPageReadinessState(ReaderChapterRasterGenerationState.Ready,
                    ReaderDecodedWorkingSetState.Ready, ReaderTextureDeckState.Ready,
                    ReaderTextureDeckState.Empty, ReaderPageInteractionState.Ready)))
    }

    private fun transition(state: ReaderPresentationState,
        sourceEvent: ReaderPresentationEvent.FoliateRelocated = event,
        sourceOrigin: ReaderPresentationEventOrigin = origin,
        sourceVersion: ReaderPresentationReceiptVersion = version) =
        readerPresentationEventTransition(state, sourceVersion, false, sourceEvent, sourceOrigin).receipt

    @Test fun semanticSettlementRetainsReadableNativeOwnerAndPolicy() {
        val state = nativeState()
        val receipt = transition(state)
        assertEquals(ReaderPresentationEventDisposition.Accepted, receipt.disposition,
            "E407.SemanticPolicy.Native.Accepted")
        assertSame(state, receipt.postState, "E407.SemanticPolicy.Native.PhysicalStateRetained")
        assertEquals(readerPresentationDecision(state), readerPresentationDecision(receipt.postState),
            "E407.SemanticPolicy.Native.ReadablePolicyRetained")
        assertSame(event, receipt.event, "E407.SemanticPolicy.Native.ExactEvent")
        assertSame(origin, receipt.origin, "E407.SemanticPolicy.Native.ExactOrigin")
        val settlement = assertNotNull(receipt.semanticReceipt as? ReaderPresentationSemanticReceipt.Settlement,
            "E407.SemanticPolicy.Native.SettlementShape")
        assertSame(acknowledgement, settlement.acknowledgement, "E407.SemanticPolicy.Native.SourceAcknowledgement")
        assertSame(successor, settlement.binding, "E407.SemanticPolicy.Native.SemanticBinding")
        assertEquals(origin.transitionId, settlement.transitionId, "E407.SemanticPolicy.Native.ExactTransition")
        assertTrue(receipt.effects.isEmpty(), "E407.SemanticPolicy.Native.NoPhysicalEffects")
        assertNull(receipt.postState.rendererSuccessorReceipt, "E407.SemanticPolicy.Native.NoRendererLineage")
    }

    @Test fun semanticSettlementDoesNotReplayLegacyCurlTerminalAuthority() {
        val frame = ReaderPresentationFrameOwner.Curl(ReaderCurlPresentationFrame(
            ReaderPresentationToken(2L, ReaderPresentationTokenDomain.Gesture), predecessor, 2L, 600, 800, 5L, 6L))
        val state = nativeState().copy(authority = ReaderPresentationAuthority.CurlGesture(frame))
        val receipt = transition(state)
        assertEquals(ReaderPresentationEventDisposition.Accepted, receipt.disposition,
            "E407.SemanticPolicy.Curl.Accepted")
        assertSame(state, receipt.postState, "E407.SemanticPolicy.Curl.NoLegacySettlementReplay")
        assertEquals(readerPresentationDecision(state), readerPresentationDecision(receipt.postState),
            "E407.SemanticPolicy.Curl.OriginalInputRetained")
        assertTrue(receipt.effects.isEmpty(), "E407.SemanticPolicy.Curl.NoPhysicalEffects")
    }

    @Test fun semanticSettlementDoesNotRebindPendingNativePresentation() {
        val native = nativeState()
        val retained = (native.authority as ReaderPresentationAuthority.SettledNativePage).frame
        val request = ReaderNativePagePresentationRequest(ReaderPresentationToken(3L), predecessor, 7L)
        val state = native.copy(authority = ReaderPresentationAuthority.BlockingPreparation(retained, request),
            rendererCleanupOwnership = listOf(ReaderRendererCleanupOwnership(request.token, predecessor)))
        val receipt = transition(state)
        assertEquals(ReaderPresentationEventDisposition.Accepted, receipt.disposition,
            "E407.SemanticPolicy.Pending.Accepted")
        assertSame(state, receipt.postState, "E407.SemanticPolicy.Pending.RequestAndCleanupRetained")
        assertEquals(readerPresentationDecision(state), readerPresentationDecision(receipt.postState),
            "E407.SemanticPolicy.Pending.NoSuccessorReadiness")
        assertTrue(receipt.effects.isEmpty(), "E407.SemanticPolicy.Pending.NoRelease")
    }

    @Test fun foreignVersionSessionOrPredecessorCannotCertifySemanticSettlement() {
        val state = nativeState()
        // Owning ingress rejects foreign sessions before this boundary. The original
        // immutable receipt also forbids a cross-session origin/version pair.
        assertFailsWith<IllegalArgumentException>("E407.SemanticPolicy.Foreign.SessionReceiptInvariant") {
            transition(state, sourceVersion = version.copy(readerSessionGeneration = 2L))
        }
        val receipts = listOf(
            transition(state, sourceVersion = version.copy(publicationIdentity =
                predecessor.copy(publicationGeneration = 2L).publicationIdentity)),
            transition(state.copy(binding = predecessor.copy(viewportGeneration = 3L))),
            transition(state, sourceEvent = event.copy(acknowledgement = acknowledgement.copy(foliateSessionId = "foreign-session"))),
            transition(state, sourceEvent = event.copy(binding = successor.copy(destinationCommitIdentity =
                predecessor.destinationCommitIdentity))))
        for (receipt in receipts) {
            assertTrue(receipt.disposition != ReaderPresentationEventDisposition.Accepted,
                "E407.SemanticPolicy.Foreign.Rejected")
            assertNull(receipt.semanticReceipt, "E407.SemanticPolicy.Foreign.NoSemanticReceipt")
            assertTrue(receipt.effects.isEmpty(), "E407.SemanticPolicy.Foreign.NoPhysicalEffects")
        }
    }

    @Test fun backgroundDestroyedAndFailedCannotCertifySemanticSettlement() {
        val state = nativeState()
        val states = listOf(state.copy(lifecycle = ReaderPresentationLifecycleState.Background),
            state.copy(lifecycle = ReaderPresentationLifecycleState.Destroyed),
            state.copy(failure = ReaderDiagnosticPresentation.Failure(ReaderPresentationFailureReason.TimedOut, true, true)))
        for (preState in states) {
            val receipt = transition(preState)
            assertTrue(receipt.disposition != ReaderPresentationEventDisposition.Accepted,
                "E407.SemanticPolicy.Unavailable.Rejected")
            assertSame(preState, receipt.postState, "E407.SemanticPolicy.Unavailable.NoPhysicalMutation")
            assertNull(receipt.semanticReceipt, "E407.SemanticPolicy.Unavailable.NoSemanticReceipt")
        }
    }

    @Test fun genuineVersionedSettlementIsConsumedOnce() {
        val receipt = transition(nativeState())
        assertEquals(version, receipt.preVersion, "E407.SemanticPolicy.Version.ExactPreVersion")
        assertEquals(version.copy(eventSequence = 11L), receipt.version, "E407.SemanticPolicy.Version.AdvancedOnce")
        val consumption = ReaderPresentationSemanticReceiptConsumption()
        assertNotNull(consumption.consume(receipt), "E407.SemanticPolicy.Version.FirstConsumption")
        assertNull(consumption.consume(receipt), "E407.SemanticPolicy.Version.DuplicateConsumptionRejected")
        assertNull(consumption.consume(receipt.copy(version = version.copy(eventSequence = 9L))),
            "E407.SemanticPolicy.Version.StaleConsumptionRejected")
    }

    @Test fun unscopedRelocationStillUsesOriginalFailClosedPhysicalReducer() {
        val state = nativeState()
        val original = readerPresentationReduce(state, event)
        val receipt = transition(state, sourceOrigin = ReaderPresentationEventOrigin.UnsolicitedFoliate)
        assertEquals(original.disposition, receipt.disposition, "E407.SemanticPolicy.Unscoped.OriginalDisposition")
        assertEquals(original.state, receipt.postState, "E407.SemanticPolicy.Unscoped.OriginalState")
        assertEquals(original.effects, receipt.effects, "E407.SemanticPolicy.Unscoped.OriginalEffects")
        assertNull(receipt.semanticReceipt, "E407.SemanticPolicy.Unscoped.NoCommandReceipt")
    }
}
