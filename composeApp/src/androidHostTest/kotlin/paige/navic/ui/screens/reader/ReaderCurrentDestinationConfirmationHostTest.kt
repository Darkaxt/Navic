package paige.navic.ui.screens.reader

import android.os.Build
import kotlin.test.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import paige.navic.reader.*

/** Common-model/executor behavior only; this fixture does not activate a production Native root. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.P])
class ReaderCurrentDestinationConfirmationHostTest {
    private fun model(f: CarrierSemanticFixture, lineage: Boolean = true): ReaderCoordinator {
        val binding = ReaderPresentationBinding("synthetic-confirmation", 1L, 1L, 1L,
            ReaderDestinationCommitIdentity("synthetic-confirmation", 1L), 2L, 3L, 4L)
        val opened = ReaderCoordinator(controller = f.opened)
            .onPresentationEvent(ReaderPresentationEvent.PublicationOpened(binding)).coordinator
        val request = assertIs<ReaderPresentationAuthority.BlockingPreparation>(opened.controller.state.presentation.authority).nativePresentationRequest
        val native = opened.onPresentationEvent(ReaderPresentationEvent.NativePagePresented(
            ReaderNativePagePresentationProof(binding, assertNotNull(request).token, 1L, 100, 100, 2L, 3L))).coordinator
        assertIs<ReaderPresentationAuthority.SettledNativePage>(native.controller.state.presentation.authority)
        if (!lineage) return native
        val frame = ReaderCurlPresentationFrame(ReaderPresentationToken(8L), binding, 1L, 100, 100, 2L, 3L)
        val claimed = native.onPresentationEvent(ReaderPresentationEvent.CurlClaimed(frame)).coordinator
        val pending = claimed.onPresentationEvent(ReaderPresentationEvent.CurlTerminal(frame.token, binding,
            ReaderPageTurnSettlementAck("synthetic-ack", 0, binding.foliateSessionId, 2L, 3L))).coordinator
        // Genuine reducer-produced synthetic lineage, not a Native/material readiness injection.
        assertNotNull(pending.controller.state.presentation.rendererSuccessorReceipt)
        return pending
    }

    private data class ConfirmationOutput(
        val event: ReaderPresentationEvent.CurrentDestinationConfirmed,
        val origin: ReaderPresentationEventOrigin.SemanticCommand,
        val step: ReaderCoordinatorStep
    )

    private fun assertNoOtherEffects(step: ReaderCoordinatorStep) {
        assertTrue(step.presentationEffects.isEmpty() && step.wordSyncEffects.isEmpty())
        assertTrue(step.progressToSave == null && step.whispersyncAudioSeekTarget == null &&
            step.readaloudPlaybackCommand == null && step.readaloudReaderInteraction == null)
        assertTrue(assertNotNull(step.presentationReceipt).effects.isEmpty())
    }

    @Test fun exactExecutorConfirmationAcceptsWithoutMaterialOrOtherEffects() {
        val f = CarrierSemanticFixture()
        try {
            val before = model(f)
            val binding = assertNotNull(before.controller.state.presentation.binding)
            val version = before.controller.presentationVersion
            var output: ConfirmationOutput? = null
            val call = f.dispatch { call ->
                val event = ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version, call.origin)
                val step = before.reduceSemanticDestination(event, call.origin)
                output = ConfirmationOutput(event, call.origin, step)
                step.presentationReceipt?.let(call.onReceipt)
                ReaderSemanticExecutableResult.Accepted
            }
            // No assertion runs inside the executable or onReceipt: production catches both.
            val captured = assertNotNull(output)
            val step = captured.step
            val receipt = assertNotNull(step.presentationReceipt)
            assertTrue(call.origin === captured.origin)
            assertTrue(captured.event === receipt.event)
            assertEquals(ReaderPresentationEventDisposition.Accepted, receipt.disposition)
            assertTrue(call.origin.transitionId == receipt.semanticReceipt?.transitionId)
            assertTrue(binding == receipt.semanticReceipt?.binding)
            assertTrue(before.controller.state.presentation === step.coordinator.controller.state.presentation)
            assertTrue(before.controller.state.presentation.rendererSuccessorReceipt ===
                step.coordinator.controller.state.presentation.rendererSuccessorReceipt)
            assertTrue(before.viewState === step.coordinator.viewState)
            assertTrue(before.wordSync === step.coordinator.wordSync)
            assertTrue(before.controller.state.destinationCommitIdentity == step.coordinator.controller.state.destinationCommitIdentity)
            assertNoOtherEffects(step)
            assertEquals(1, f.receipts)
            assertEquals(0, f.executor.activeSlotCount)
        } finally { f.close() }
    }

    @Test fun typedIngressCarriesExactConfirmationToGenuineReceiptBoundary() {
        val f = CarrierSemanticFixture()
        try {
            val before = model(f)
            val binding = assertNotNull(before.controller.state.presentation.binding)
            val version = before.controller.presentationVersion
            var output: ConfirmationOutput? = null
            var ingressInvocations = 0
            val ingress: ReaderSemanticReceiptIngress = { event, origin ->
                ingressInvocations += 1
                val step = before.reduceSemanticDestination(event, origin)
                // Keep this observer independent of the ingress's declared event subtype.
                val confirmation = (event as ReaderPresentationEvent) as? ReaderPresentationEvent.CurrentDestinationConfirmed
                if (confirmation != null) output = ConfirmationOutput(confirmation, origin, step)
                step.presentationReceipt
            }
            // Function2's erased boundary reproduces a mismatched typed ingress without
            // adding a broad event capability to production. The old Foliate-only closure
            // rejects the real confirmation before entering the observer.
            @Suppress("UNCHECKED_CAST")
            val transport = ingress as (ReaderPresentationEvent, ReaderPresentationEventOrigin.SemanticCommand) -> ReaderPresentationEventReceipt?
            val call = f.dispatch { call ->
                val event = ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version, call.origin)
                transport(event, call.origin)?.let(call.onReceipt)
                ReaderSemanticExecutableResult.Accepted
            }
            assertEquals(1, ingressInvocations)
            val captured = assertNotNull(output)
            val receipt = assertNotNull(captured.step.presentationReceipt)
            assertTrue(captured.origin === call.origin && receipt.origin === call.origin)
            assertTrue(receipt.event === captured.event)
            assertEquals(ReaderPresentationEventDisposition.Accepted, receipt.disposition)
            assertTrue(receipt.semanticReceipt?.binding == binding)
            assertTrue(before.controller.state.presentation === captured.step.coordinator.controller.state.presentation)
            assertNoOtherEffects(captured.step)
            assertEquals(1, f.receipts)
            assertEquals(0, f.executor.activeSlotCount)
        } finally { f.close() }
    }

    @Test fun duplicateConfirmationBecomesStaleAndNeverChangesMaterialLineage() {
        val f = CarrierSemanticFixture()
        try {
            val before = model(f)
            val binding = assertNotNull(before.controller.state.presentation.binding)
            val version = before.controller.presentationVersion
            var outputs: List<ConfirmationOutput> = emptyList()
            val call = f.dispatch { call ->
                val event = ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version, call.origin)
                val first = before.reduceSemanticDestination(event, call.origin)
                val duplicate = first.coordinator.reduceSemanticDestination(event, call.origin)
                outputs = listOf(ConfirmationOutput(event, call.origin, first), ConfirmationOutput(event, call.origin, duplicate))
                first.presentationReceipt?.let(call.onReceipt)
                ReaderSemanticExecutableResult.Accepted
            }
            assertEquals(2, outputs.size)
            val first = outputs[0].step
            val duplicate = outputs[1].step
            assertTrue(call.origin === outputs[0].origin)
            assertEquals(ReaderPresentationEventDisposition.Accepted, assertNotNull(first.presentationReceipt).disposition)
            assertEquals(ReaderPresentationEventDisposition.Stale, assertNotNull(duplicate.presentationReceipt).disposition)
            assertNull(duplicate.presentationReceipt?.semanticReceipt)
            assertTrue(before.controller.state.presentation === first.coordinator.controller.state.presentation)
            assertTrue(before.controller.state.presentation === duplicate.coordinator.controller.state.presentation)
            assertNoOtherEffects(first); assertNoOtherEffects(duplicate)
            assertEquals(1, f.receipts)
            assertEquals(0, f.executor.activeSlotCount)
        } finally { f.close() }
    }

    @Test fun wrongVersionOriginEpochSessionSlotCommitProfileAndMaterialCannotConfirm() {
        val f = CarrierSemanticFixture()
        try {
            val current = model(f)
            val binding = assertNotNull(current.controller.state.presentation.binding)
            val version = current.controller.presentationVersion
            var outputs: List<ConfirmationOutput> = emptyList()
            f.dispatch { call ->
                val cases = listOf(
                    ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version.copy(eventSequence = version.eventSequence + 1), call.origin),
                    ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version.copy(readerSessionGeneration = version.readerSessionGeneration + 1), call.origin),
                    ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version, call.origin.copy()),
                    ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version, call.origin.copy(transitionId = call.origin.transitionId.copy(coordinatorEpoch = call.origin.transitionId.coordinatorEpoch + 1))),
                    ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version, call.origin.copy(transitionId = call.origin.transitionId.copy(readerSessionGeneration = call.origin.transitionId.readerSessionGeneration + 1))),
                    ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version, call.origin.copy(slotId = ReaderSemanticCommandSlotId(call.origin.slotId.value + 1))),
                    ReaderPresentationEvent.CurrentDestinationConfirmed(binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity(binding.foliateSessionId, 2L)), version, call.origin),
                    ReaderPresentationEvent.CurrentDestinationConfirmed(binding.copy(profileGeneration = 2L), version, call.origin),
                    ReaderPresentationEvent.CurrentDestinationConfirmed(binding.copy(rasterGeneration = 5L, textureGeneration = 6L), version, call.origin)
                )
                outputs = cases.map { event -> ConfirmationOutput(event, call.origin, current.reduceSemanticDestination(event, call.origin)) }
                ReaderSemanticExecutableResult.Rejected
            }
            assertEquals(9, outputs.size)
            outputs.forEach { output ->
                val step = output.step
                val receipt = assertNotNull(step.presentationReceipt)
                assertTrue(receipt.disposition != ReaderPresentationEventDisposition.Accepted)
                assertNull(receipt.semanticReceipt)
                assertTrue(current.controller.state.presentation === step.coordinator.controller.state.presentation)
                assertNoOtherEffects(step)
            }
            assertEquals(0, f.receipts)
            assertEquals(0, f.executor.activeSlotCount)
        } finally { f.close() }
    }

    @Test fun ordinaryEqualRelocationRemainsIdempotentAndUnsolicitedConfirmationIsRejected() {
        val f = CarrierSemanticFixture()
        try {
            val current = model(f, lineage = false)
            val binding = assertNotNull(current.controller.state.presentation.binding)
            val version = current.controller.presentationVersion
            var ordinary: ReaderCoordinatorStep? = null
            var unsolicited: ReaderCoordinatorStep? = null
            var pure: ReaderPresentationReduction? = null
            f.dispatch { call ->
                ordinary = current.reduceSemanticDestination(ReaderPresentationEvent.FoliateRelocated(binding, null), call.origin)
                val event = ReaderPresentationEvent.CurrentDestinationConfirmed(binding, version, call.origin)
                unsolicited = current.onPresentationEvent(event, ReaderPresentationEventOrigin.UnsolicitedFoliate)
                pure = readerPresentationReduce(current.controller.state.presentation, event)
                ReaderSemanticExecutableResult.Rejected
            }
            val ordinaryStep = assertNotNull(ordinary)
            val unsolicitedStep = assertNotNull(unsolicited)
            assertEquals(ReaderPresentationEventDisposition.Idempotent, assertNotNull(ordinaryStep.presentationReceipt).disposition)
            assertNull(ordinaryStep.presentationReceipt?.semanticReceipt)
            assertEquals(ReaderPresentationEventDisposition.Rejected, assertNotNull(unsolicitedStep.presentationReceipt).disposition)
            assertNull(unsolicitedStep.presentationReceipt?.semanticReceipt)
            assertEquals(ReaderPresentationEventDisposition.Rejected, assertNotNull(pure).disposition)
            assertNoOtherEffects(ordinaryStep); assertNoOtherEffects(unsolicitedStep)
            assertEquals(0, f.receipts)
        } finally { f.close() }
    }
}
