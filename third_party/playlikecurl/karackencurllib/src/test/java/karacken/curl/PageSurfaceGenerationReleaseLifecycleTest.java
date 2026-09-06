package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class PageSurfaceGenerationReleaseLifecycleTest {
    @Test
    public void recordTransitionTableIsTypedFencedAndIdempotent() {
        PageSurfaceGenerationReleaseRecord<String> record =
                PageSurfaceGenerationReleaseRecord.requested(
                        71,
                        PageDeckCoordinator.Release.rollbackable(
                                portraitDeck(401, "record"),
                                DeckReleaseReason.EXPLICIT,
                                true));

        assertEquals(PageSurfaceGenerationReleaseRecord.State.REQUESTED, record.getState());
        assertEquals(
                PageSurfaceGenerationReleaseRecord.DuplicateDisposition.STATE_CONFLICT,
                record.duplicateDisposition());
        assertTrue(record.matches(71, 401));
        assertFalse(record.matches(72, 401));
        assertFalse(record.matches(71, 402));
        assertFalse(record.rendererDetached());
        assertFalse(record.terminallyAbandon());
        assertFalse(record.complete());
        assertTrue(record.queueAccepted());
        assertFalse(record.queueAccepted());
        assertEquals(
                PageSurfaceGenerationReleaseRecord.DuplicateDisposition.ALREADY_ACCEPTED,
                record.duplicateDisposition());
        assertFalse(record.complete());
        assertTrue(record.rendererDetached());
        assertFalse(record.queueAccepted());
        assertFalse(record.rendererDetached());
        assertFalse(record.terminallyAbandon());
        assertEquals(PageSurfaceGenerationReleaseRecord.State.RENDERER_DETACHED, record.getState());
        assertTrue(record.complete());
        assertFalse(record.complete());
        assertEquals(PageSurfaceGenerationReleaseRecord.State.COMPLETED, record.getState());
        assertEquals(
                PageSurfaceGenerationReleaseRecord.DuplicateDisposition.COMPLETED,
                record.duplicateDisposition());
        assertFalse(record.queueAccepted());
        assertFalse(record.rendererDetached());
        assertFalse(record.terminallyAbandon());
    }

    @Test
    public void terminalAbandonTransitionWinsLateRendererCallbackOnce() {
        PageSurfaceGenerationReleaseRecord<String> record =
                PageSurfaceGenerationReleaseRecord.requested(
                        81,
                        PageDeckCoordinator.Release.rollbackable(
                                portraitDeck(402, "terminal"),
                                DeckReleaseReason.SESSION_DETACHED,
                                false));
        assertTrue(record.queueAccepted());
        assertTrue(record.terminallyAbandon());
        assertFalse(record.queueAccepted());
        assertFalse(record.terminallyAbandon());
        assertFalse(record.rendererDetached());
        assertEquals(
                PageSurfaceGenerationReleaseRecord.State.TERMINALLY_ABANDONED,
                record.getState());
        assertTrue(record.complete());
        assertFalse(record.queueAccepted());
        assertFalse(record.rendererDetached());
        assertFalse(record.terminallyAbandon());
        assertFalse(record.complete());
    }

    @Test
    public void acceptedNoRunIsTerminallyAbandonedOnRendererLossAndLateCallbackIsIgnored() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry leases = new DeckLeaseRegistry();
        PortraitPageDeck<String> deck = portraitDeck(410, "accepted-no-run");
        PageSurfaceListener owner = new PageSurfaceListener() {};
        coordinator.offer(deck);
        assertTrue(leases.acquire(410, owner));
        PageSurfaceDeckReleaseGate<String> gate =
                new PageSurfaceDeckReleaseGate<>(coordinator, leases);
        assertEquals(
                PageSurfaceDeckReleaseResult.Status.ACCEPTED,
                gate.request(410, (generationId, reason) -> {}).getStatus());
        assertEquals(PageSurfaceGenerationReleaseRecord.State.QUEUE_ACCEPTED, gate.stateFor(410));

        List<Long> detached = new java.util.ArrayList<>();
        List<PageSurfaceGenerationReleaseRecord<String>> abandoned =
                gate.terminallyAbandonAccepted(generationId -> {
                    assertEquals(
                            PageSurfaceGenerationReleaseRecord.State.QUEUE_ACCEPTED,
                            gate.stateFor(generationId));
                    detached.add(generationId);
                });

        assertEquals(java.util.Collections.singletonList(410L), detached);
        assertEquals(1, abandoned.size());
        assertEquals(410, abandoned.get(0).getGenerationId());
        assertEquals(
                PageSurfaceGenerationReleaseRecord.State.TERMINALLY_ABANDONED,
                abandoned.get(0).getState());
        assertTrue(gate.complete(410));
        DeckLeaseRegistry.Lease terminalLease = leases.release(
                410,
                abandoned.get(0).getReason());
        assertSame(owner, terminalLease.getListener());
        assertEquals(DeckReleaseReason.EXPLICIT, terminalLease.getReleaseReason());
        assertFalse(gate.rendererDetached(410));
        assertFalse(gate.complete(410));
        assertNull(leases.release(410));

        PageDeckCoordinator<String> replacementCoordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry replacementLeases = new DeckLeaseRegistry();
        PortraitPageDeck<String> replacement = portraitDeck(411, "replacement");
        replacementCoordinator.offer(replacement);
        assertTrue(replacementLeases.acquire(411, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> replacementGate =
                new PageSurfaceDeckReleaseGate<>(replacementCoordinator, replacementLeases);
        assertNotEquals(gate.sessionId(), replacementGate.sessionId());
        assertEquals(
                PageSurfaceDeckReleaseResult.Status.ACCEPTED,
                replacementGate.request(411, (generationId, reason) -> {}).getStatus());
    }

    @Test
    public void normalCallbackRacingRendererLossCompletesExactRecordOnce() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry leases = new DeckLeaseRegistry();
        PortraitPageDeck<String> deck = portraitDeck(420, "race");
        coordinator.offer(deck);
        assertTrue(leases.acquire(420, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> gate =
                new PageSurfaceDeckReleaseGate<>(coordinator, leases);
        assertEquals(
                PageSurfaceDeckReleaseResult.Status.ACCEPTED,
                gate.request(420, (generationId, reason) -> {}).getStatus());

        assertTrue(gate.rendererDetached(420));
        assertTrue(gate.terminallyAbandonAccepted().isEmpty());
        assertTrue(gate.complete(420));
        assertFalse(gate.rendererDetached(420));
        assertFalse(gate.complete(420));
    }

    @Test
    public void closeRetainsAcceptedCommandForTerminalAbandonButRejectsNewGeneration() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry leases = new DeckLeaseRegistry();
        coordinator.offer(portraitDeck(430, "close"));
        assertTrue(leases.acquire(430, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> gate =
                new PageSurfaceDeckReleaseGate<>(coordinator, leases);
        assertEquals(
                PageSurfaceDeckReleaseResult.Status.ACCEPTED,
                gate.request(430, (generationId, reason) -> {}).getStatus());
        gate.close();
        assertEquals(
                PageSurfaceDeckReleaseResult.RejectionReason.DISPOSED,
                gate.request(430, (generationId, reason) -> {}).getRejectionReason());
        assertEquals(1, gate.terminallyAbandonAccepted().size());
    }

    @Test
    public void nonterminalPauseRetainsSelectedAcceptedReleaseButCloseForcesItOnce() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry leases = new DeckLeaseRegistry();
        PageSurfaceDeckReleaseGate<String> gate =
                new PageSurfaceDeckReleaseGate<>(coordinator, leases);
        coordinator.offer(portraitDeck(301, "selected"));
        assertTrue(leases.acquire(301, new PageSurfaceListener() {}));
        PageDeckCoordinator.Offer<String> first = coordinator.offer(portraitDeck(302, "obsolete"));
        assertTrue(leases.acquire(302, new PageSurfaceListener() {}));
        gate.queueAutomatic(first.getReleases(), () -> {});
        PageDeckCoordinator.Offer<String> second = coordinator.offer(portraitDeck(303, "candidate"));
        assertTrue(leases.acquire(303, new PageSurfaceListener() {}));
        gate.queueAutomatic(second.getReleases(), () -> {});
        List<Long> detached = new java.util.ArrayList<>();

        List<PageSurfaceGenerationReleaseRecord<String>> paused =
                gate.terminallyAbandonAccepted(generation -> generation == 301L, detached::add);

        assertEquals(java.util.Collections.singletonList(302L), detached);
        assertEquals(1, paused.size());
        assertEquals(PageSurfaceGenerationReleaseRecord.State.QUEUE_ACCEPTED, gate.stateFor(301));
        assertEquals(PageSurfaceDeckReleaseResult.Status.ALREADY_ACCEPTED,
                gate.request(301, (generation, reason) -> {
                    throw new AssertionError("Accepted selected release cannot queue twice");
                }).getStatus());
        assertTrue(gate.complete(302));
        assertTrue(leases.release(302) != null);
        assertTrue(gate.terminallyAbandonAccepted(generation -> generation == 301L, detached::add).isEmpty());
        assertEquals(1, gate.releaseInFlightCount(303, -1));

        gate.close();
        List<PageSurfaceGenerationReleaseRecord<String>> closed =
                gate.terminallyAbandonAccepted(detached::add);
        assertEquals(java.util.Arrays.asList(302L, 301L), detached);
        assertEquals(1, closed.size());
        assertEquals(DeckReleaseReason.REPLACED, closed.get(0).getReason());
        assertTrue(gate.complete(301));
        assertTrue(leases.release(301) != null);
        assertTrue(gate.terminallyAbandonAccepted(detached::add).isEmpty());
        assertFalse(gate.rendererDetached(301));
        assertFalse(gate.complete(301));
        assertEquals(0, gate.releaseInFlightCount(303, -1));
        assertEquals(1, leases.size());
    }

    private static PortraitPageDeck<String> portraitDeck(long generationId, String label) {
        return new PortraitPageDeck<>(
                page(generationId, label + "-previous", 0),
                page(generationId, label + "-current", 1),
                page(generationId, label + "-next", 2));
    }

    private static PageImage<String> page(long generationId, String identity, int ordinal) {
        return new PageImage<>(generationId, identity, ordinal, 100, 140, identity);
    }
}
