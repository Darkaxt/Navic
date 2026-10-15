package paige.navic.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Original ledger custody: these tests are independent of the Android fixture lane. */
class ReaderInstalledNavigationLedgerTest {
    private val command = ReaderBridgeCommand.GoToVisualPage(
        pageIndex = 7, settleToken = "synthetic-turn", settleGestureId = 4L,
        settleSessionId = "synthetic-session", settleRasterGeneration = 9L,
        settleTextureGeneration = 12L, settleForegroundMutationGeneration = 3L,
        sourceDestinationCommitSequence = 5L)
    private val continuation = ReaderInstalledPageTurnContinuation { }
    private fun state(dispatched: Boolean = true) = ReaderWebCommandDispatchState(
        publicationKey = "synthetic-publication", runtimeGeneration = 2,
        foliateSessionId = command.settleSessionId,
        pendingCommands = listOf(ReaderWebPendingCommand(
            ReaderBridgeDispatchCommand("synthetic-command", command),
            lastDispatchedGeneration = if (dispatched) 2 else null,
            installedPageTurnContinuation = continuation)))
    private fun event() = ReaderBridgeEvent.LocationChanged(
        ReaderLocator(pageIndex = command.pageIndex), command.settleSessionId,
        pageTurnSettleToken = command.settleToken,
        pageTurnSettleSessionId = command.settleSessionId,
        pageTurnSettleRasterGeneration = command.settleRasterGeneration,
        pageTurnSettleTextureGeneration = command.settleTextureGeneration,
        destinationCommitIdentity = ReaderDestinationCommitIdentity(command.settleSessionId, 11L),
        commandId = "synthetic-command")

    @Test fun acknowledgementCannotDequeueNavigationBeforeSemanticSettlement() {
        val original = state()
        assertSame(original, original.acknowledge("synthetic-command"))
        assertSame(original.pendingCommands.single(), original.acknowledge("synthetic-command").pendingCommands.single())
    }

    @Test fun exactSettlementConsumesOriginalDispatchedHeadBeforeDelivery() {
        val original = state()
        val relocation = event()
        val consumed = original.consumeInstalledPageTurnResult(relocation, 2)
        assertTrue(consumed.state.pendingCommands.isEmpty())
        assertSame(continuation, consumed.continuation)
        assertSame(relocation, consumed.event)
        assertSame(consumed.state, consumed.state.consumeInstalledPageTurnResult(relocation, 2).state)
        assertEquals(command.pageIndex, consumed.state.lastKnownLocator?.pageIndex)
    }

    @Test fun wrongCorrelationRetainsValidHead() {
        val original = state()
        val right = event()
        val wrong = listOf(right.copy(commandId = "other"), right.copy(pageTurnSettleToken = "other"),
            right.copy(pageTurnSettleSessionId = "other"), right.copy(pageTurnSettleRasterGeneration = 10L),
            right.copy(pageTurnSettleTextureGeneration = 13L),
            right.copy(locator = right.locator.copy(pageIndex = 8)),
            right.copy(destinationCommitIdentity = ReaderDestinationCommitIdentity(command.settleSessionId, 5L)),
            right.copy(destinationCommitIdentity = null), right.copy(foliateSessionId = "other"))
        for (relocation in wrong) {
            val consumed = original.consumeInstalledPageTurnResult(relocation, 2)
            assertSame(original, consumed.state)
            assertNull(consumed.continuation)
        }
    }

    @Test fun undispatchedOrWrongRuntimeHasNoSemanticAuthority() {
        val waiting = state(false)
        assertSame(waiting, waiting.consumeInstalledPageTurnResult(event(), 2).state)
        val dispatched = state()
        assertSame(dispatched, dispatched.consumeInstalledPageTurnResult(event(), 3).state)
    }

    @Test fun cancellationRetiresOnlyExactOriginalToken() {
        val original = state()
        assertSame(original, original.retireInstalledPageTurnExact(ReaderInstalledPageTurnContinuation { }))
        val retired = original.retireInstalledPageTurnExact(continuation)
        assertTrue(retired.pendingCommands.isEmpty())
        assertSame(retired, retired.consumeInstalledPageTurnResult(event(), 2).state)
    }

    @Test fun ordinaryCommandProjectionCannotReplayInstalledNavigation() {
        val original = state().retireInstalledPageTurnExact(continuation)
        val step = original.commandsForReadyReaderRuntime(2, "synthetic-publication",
            ReaderBridgeCommand.OpenPublication("https://example.invalid/synthetic.epub", "synthetic-session"),
            command, 44L)
        assertTrue(step.commands.isEmpty())
        assertTrue(step.state.pendingCommands.isEmpty())
    }
}
