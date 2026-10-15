package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReaderInstalledRelocationModelTest {
    private fun original(): ReaderCoordinator {
        val opened = ReaderCoordinator().open(ReaderEngineOpenRequest(
            ReaderPublicationIdentity("synthetic-book", resourceHref = "synthetic.epub", kind = ReaderPublicationKind.Ebook,
                format = ReaderPublicationFormat.Epub), "https://example.invalid/synthetic.epub",
            settings = defaultReaderSettings())).coordinator
        val binding = ReaderPresentationBinding("synthetic-session", 1L, 1L, 1L,
            ReaderDestinationCommitIdentity("synthetic-session", 2L))
        val bound = opened.onPresentationEvent(ReaderPresentationEvent.PublicationOpened(binding)).coordinator
        val ready = bound.onEngineHostEvent(ReaderEngineHostEvent.FoliateBridge(ReaderBridgeEvent.PublicationReady)).coordinator
        return ready.onEngineHostEvent(ReaderEngineHostEvent.FoliateBridge(ReaderBridgeEvent.LocationChanged(
            ReaderLocator(pageIndex = 0, pageCount = 30, progress = 0.0), binding.foliateSessionId,
            destinationCommitIdentity = binding.destinationCommitIdentity))).coordinator
    }
    private fun origin(c: ReaderCoordinator, binding: ReaderPresentationBinding) = ReaderPresentationEventOrigin.SemanticCommand(
        ReaderTransitionId(c.controller.state.readerSessionGeneration, 1L, 1L,
            ReaderTransitionOperation.CurlClaimAndSettlement, ReaderExpectedPresentationBinding.SemanticSuccessor(binding, 1L)),
        ReaderSemanticCommandSlotId(1L))
    private fun event(binding: ReaderPresentationBinding) = ReaderBridgeEvent.LocationChanged(
        ReaderLocator(pageIndex = 7, pageCount = 30, chapterPageIndex = 3, chapterPageCount = 9,
            progress = 0.4, href = "synthetic.xhtml", reason = "page-turn"), binding.foliateSessionId,
        pageTurnSettleToken = "synthetic-turn", pageTurnSettleSessionId = binding.foliateSessionId,
        pageTurnSettleRasterGeneration = 9L, pageTurnSettleTextureGeneration = 12L,
        destinationCommitIdentity = ReaderDestinationCommitIdentity(binding.foliateSessionId,
            (binding.destinationCommitIdentity?.commitSequence ?: 0L) + 1L), commandId = "synthetic-command")

    @Test fun installedRelocationUpdatesOwningDataAndProducesSemanticReceiptWithoutPhysicalEffects() {
        val c = original()
        val binding = assertNotNull(c.controller.state.presentation.binding)
        val location = event(binding)
        val source = origin(c, binding)
        val step = c.onInstalledPageTurnRelocation(location, binding, source)
        assertEquals(7, step.coordinator.controller.state.chrome.currentLocator?.pageIndex,
            "E407.Model.Relocation.OwningPage")
        assertEquals(3, step.coordinator.controller.state.chapterProgress.pageIndex,
            "E407.Model.Relocation.OwningChapterProgress")
        assertEquals(location.destinationCommitIdentity, step.coordinator.controller.state.destinationCommitIdentity,
            "E407.Model.Relocation.OwningDestination")
        val receipt = assertNotNull(step.presentationReceipt, "E407.Model.Relocation.ReceiptProduced")
        assertSame(source, receipt.origin, "E407.Model.Relocation.ExactOrigin")
        assertEquals(location.destinationCommitIdentity, receipt.semanticReceipt?.binding?.destinationCommitIdentity,
            "E407.Model.Relocation.SemanticReceiptAccepted")
        assertTrue(receipt.effects.isEmpty(), "E407.Model.Relocation.NoPhysicalReceiptEffects")
        assertTrue(step.presentationEffects.isEmpty(), "E407.Model.Relocation.NoLegacyPresentationEffects")
        val settlement = assertNotNull(receipt.semanticReceipt as? ReaderPresentationSemanticReceipt.Settlement,
            "E407.Model.Relocation.SettlementShape")
        assertSame(step.coordinator.controller.state.pageTurnSettlementAck, settlement.acknowledgement,
            "E407.Model.Relocation.OwningAcknowledgement")
        assertEquals(source.transitionId, settlement.transitionId, "E407.Model.Relocation.ExactTransition")
        assertNull(settlement.binding.preparationGeneration, "E407.Model.Relocation.NoSuccessorPreparation")
        assertNull(settlement.binding.rasterGeneration, "E407.Model.Relocation.NoSuccessorRaster")
        assertNull(settlement.binding.textureGeneration, "E407.Model.Relocation.NoSuccessorTexture")
        assertSame(c.controller.state.presentation, receipt.postState, "E407.Model.Relocation.RetainedPhysicalState")
        assertEquals(c.controller.presentationVersion, receipt.preVersion, "E407.Model.Relocation.CurrentVersion")
        assertEquals(receipt.preVersion.eventSequence + 1L, receipt.version.eventSequence,
            "E407.Model.Relocation.VersionAdvancedOnce")
        val owning = c.onEngineHostEvent(ReaderEngineHostEvent.FoliateBridge(location))
        assertEquals(owning.coordinator.controller.state, step.coordinator.controller.state,
            "E407.Model.Relocation.OriginalOwningReduction")
        assertEquals(owning.progressToSave, step.progressToSave, "E407.Model.Relocation.OriginalProgressPublication")
        assertEquals(owning.wordSyncEffects, step.wordSyncEffects, "E407.Model.Relocation.OriginalWordSyncEffects")
        assertEquals(owning.coordinator.wordSync, step.coordinator.wordSync,
            "E407.Model.Relocation.OriginalWordSyncCoordinator")
        assertEquals(owning.coordinator.viewState, step.coordinator.viewState,
            "E407.Model.Relocation.OriginalEngineCommands")
    }

    @Test fun wrongSessionCannotUpdateModelOrPublishReceipt() {
        val c = original()
        val binding = assertNotNull(c.controller.state.presentation.binding)
        val step = c.onInstalledPageTurnRelocation(event(binding).copy(foliateSessionId = "wrong"), binding, origin(c, binding))
        assertSame(c, step.coordinator)
        assertNull(step.presentationReceipt)
    }

    @Test fun foreignOrStalePredecessorCannotEnterOwningReduction() {
        val c = original()
        val binding = assertNotNull(c.controller.state.presentation.binding)
        val foreign = binding.copy(publicationGeneration = binding.publicationGeneration + 1L)
        val stale = binding.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity(binding.foliateSessionId, 1L))
        for (predecessor in listOf(foreign, stale)) {
            val step = c.onInstalledPageTurnRelocation(event(binding), predecessor, origin(c, predecessor))
            assertSame(c, step.coordinator, "E407.Model.Relocation.PredecessorRejected")
            assertNull(step.presentationReceipt, "E407.Model.Relocation.PredecessorNoReceipt")
            assertNull(step.progressToSave, "E407.Model.Relocation.PredecessorNoProgress")
        }
    }

    @Test fun foreignCommandCannotEnterOwningReduction() {
        val c = original()
        val binding = assertNotNull(c.controller.state.presentation.binding)
        val command = origin(c, binding)
        val foreignBinding = binding.copy(viewportGeneration = binding.viewportGeneration + 1L)
        val origins = listOf(
            command.copy(transitionId = command.transitionId.copy(readerSessionGeneration =
                c.controller.state.readerSessionGeneration + 1L)),
            command.copy(transitionId = command.transitionId.copy(operation = ReaderTransitionOperation.ExternalSemanticRelocation)),
            origin(c, foreignBinding))
        for (source in origins) {
            val step = c.onInstalledPageTurnRelocation(event(binding), binding, source)
            assertSame(c, step.coordinator, "E407.Model.Relocation.ForeignCommandRejected")
            assertNull(step.presentationReceipt, "E407.Model.Relocation.ForeignCommandNoReceipt")
        }
    }

    @Test fun absentOrForeignSettlementCannotEnterOwningReduction() {
        val c = original()
        val binding = assertNotNull(c.controller.state.presentation.binding)
        val location = event(binding)
        val invalid = listOf(
            location.copy(pageTurnSettleToken = null),
            location.copy(pageTurnSettleSessionId = "foreign-session"),
            location.copy(pageTurnSettleRasterGeneration = null),
            location.copy(pageTurnSettleTextureGeneration = -1L),
            location.copy(locator = location.locator.copy(pageIndex = -1)))
        for (event in invalid) {
            val step = c.onInstalledPageTurnRelocation(event, binding, origin(c, binding))
            assertSame(c, step.coordinator, "E407.Model.Relocation.SourceAcknowledgementRejected")
            assertNull(step.presentationReceipt, "E407.Model.Relocation.SourceAcknowledgementNoReceipt")
        }
    }

    @Test fun olderDestinationCannotRepeatOwningIngress() {
        val c = original()
        val binding = assertNotNull(c.controller.state.presentation.binding)
        val older = event(binding).copy(destinationCommitIdentity =
            ReaderDestinationCommitIdentity(binding.foliateSessionId, 1L))
        val step = c.onInstalledPageTurnRelocation(older, binding, origin(c, binding))
        assertSame(c, step.coordinator, "E407.Model.Relocation.OlderDestinationRejected")
        assertNull(step.presentationReceipt, "E407.Model.Relocation.OlderDestinationNoReceipt")
        assertNull(step.progressToSave, "E407.Model.Relocation.OlderDestinationNoProgress")
    }

    @Test fun duplicateDestinationCannotRepeatOwningIngress() {
        val c = original()
        val binding = assertNotNull(c.controller.state.presentation.binding)
        val location = event(binding)
        val source = origin(c, binding)
        val delivered = c.onInstalledPageTurnRelocation(location, binding, source).coordinator
        val duplicate = delivered.onInstalledPageTurnRelocation(location, binding, source)
        assertSame(delivered, duplicate.coordinator)
        assertNull(duplicate.presentationReceipt)
        assertNull(duplicate.progressToSave)
    }
}
