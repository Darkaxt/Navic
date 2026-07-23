package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class PageSurfaceDeckSubmissionGateTest {
    private static final PageSurfaceListener LISTENER = new PageSurfaceListener() {};

    @Test
    public void negativeGenerationCannotReachSubmissionGate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> portraitDeck(-1L, "negative"));
    }

    @Test
    public void preAdmissionRollbackRestoresCoordinatorAndLeaseCapacity() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry registry = new DeckLeaseRegistry();
        PageSurfaceDeckSubmissionGate<String> gate =
                new PageSurfaceDeckSubmissionGate<>(coordinator, registry);
        PortraitPageDeck<String> first = portraitDeck(1L, "one");
        PortraitPageDeck<String> second = portraitDeck(2L, "two");
        assertAccepted(gate.submit(first, LISTENER));

        PageSurfaceDeckSubmissionGate.Result<String> accepted =
                gate.submit(second, LISTENER);
        gate.rollbackAccepted(second, accepted);

        assertSame(first, coordinator.getActiveDeck());
        assertTrue(registry.contains(1L));
        assertFalse(registry.contains(2L));
        assertEquals(1, registry.size());
        assertAccepted(gate.submit(second, LISTENER));
    }

    @Test
    public void saturationRejectsBeforeCoordinatorMutationAndCapacityEdgeAllowsRetry() {
        Saturated saturated = saturatedGate();
        PortraitPageDeck<String> fifth = portraitDeck(5L, "five");

        PageSurfaceDeckSubmissionGate.Result<String> rejected =
                saturated.gate.submit(fifth, LISTENER);

        assertNull(rejected.offer());
        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.REJECTED,
                rejected.publicResult().getStatus());
        assertEquals(
                DeckRejectionReason.RESOURCE_CAPACITY,
                rejected.publicResult().getRejectionReason());
        assertEquals(4L, saturated.coordinator.getPendingDeck().getGenerationId());
        assertEquals(4, saturated.registry.size());

        assertTrue(saturated.registry.release(2L) != null);
        assertTrue(saturated.gate.takeCapacityAvailableSignal(2L));
        assertFalse(saturated.gate.takeCapacityAvailableSignal(2L));

        PageSurfaceDeckSubmissionGate.Result<String> accepted =
                saturated.gate.submit(fifth, LISTENER);
        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.ACCEPTED,
                accepted.publicResult().getStatus());
        assertEquals(
                PageDeckCoordinator.Placement.PENDING,
                accepted.offer().getPlacement());
        assertEquals(5L, saturated.coordinator.getPendingDeck().getGenerationId());
        assertTrue(saturated.registry.contains(5L));
        assertEquals(4, saturated.registry.size());
    }

    @Test
    public void saturatedPlacedGenerationRemainsUnchangedWithoutNewLeaseOrRelease() {
        Saturated saturated = saturatedGate();

        PageSurfaceDeckSubmissionGate.Result<String> unchanged =
                saturated.gate.submit(portraitDeck(4L, "four"), LISTENER);

        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.UNCHANGED,
                unchanged.publicResult().getStatus());
        assertEquals(
                PageDeckCoordinator.Placement.UNCHANGED,
                unchanged.offer().getPlacement());
        assertTrue(unchanged.offer().getReleases().isEmpty());
        assertEquals(4, saturated.registry.size());
    }

    @Test
    public void releaseInFlightGenerationCannotUseUnchangedPath() {
        Saturated saturated = saturatedGate();
        PageDeck<String> pending = saturated.coordinator.getPendingDeck();

        PageSurfaceDeckSubmissionGate.Result<String> result =
                saturated.gate.submit(portraitDeck(3L, "three"), LISTENER);

        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.REJECTED,
                result.publicResult().getStatus());
        assertEquals(
                DeckRejectionReason.RESOURCE_CAPACITY,
                result.publicResult().getRejectionReason());
        assertNull(result.offer());
        assertSame(pending, saturated.coordinator.getPendingDeck());
    }

    @Test
    public void unrelatedReleaseCannotConsumeGenerationSpecificCapacityEdge() {
        Saturated saturated = saturatedGate();

        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.REJECTED,
                saturated.gate.submit(portraitDeck(3L, "three"), LISTENER)
                        .publicResult().getStatus());

        assertTrue(saturated.registry.release(2L) != null);
        assertFalse(saturated.gate.takeCapacityAvailableSignal(2L));
        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.REJECTED,
                saturated.gate.submit(portraitDeck(3L, "three"), LISTENER)
                        .publicResult().getStatus());

        assertTrue(saturated.registry.release(3L) != null);
        assertTrue(saturated.gate.takeCapacityAvailableSignal(3L));
        assertFalse(saturated.gate.takeCapacityAvailableSignal(3L));
    }

    @Test
    public void reentrantSubmissionDefersExactReleaseEdgeUntilCapacityReturns() {
        Saturated saturated = saturatedGate();
        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.REJECTED,
                saturated.gate.submit(portraitDeck(3L, "three"), LISTENER)
                        .publicResult().getStatus());

        assertTrue(saturated.registry.release(3L) != null);
        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.ACCEPTED,
                saturated.gate.submit(portraitDeck(5L, "five"), LISTENER)
                        .publicResult().getStatus());
        assertFalse(saturated.gate.takeCapacityAvailableSignal(3L));

        assertTrue(saturated.registry.release(2L) != null);
        assertTrue(saturated.gate.takeCapacityAvailableSignal(2L));
        assertFalse(saturated.gate.takeCapacityAvailableSignal(2L));
    }

    @Test
    public void duplicateAcknowledgementDoesNotEmitAnotherCapacityEdge() {
        Saturated saturated = saturatedGate();
        saturated.gate.submit(portraitDeck(5L, "five"), LISTENER);

        assertTrue(saturated.registry.release(2L) != null);
        assertTrue(saturated.gate.takeCapacityAvailableSignal(2L));
        assertNull(saturated.registry.release(2L));
        assertFalse(saturated.gate.takeCapacityAvailableSignal(2L));
    }

    @Test
    public void secondSaturationRearmsExactlyOneLaterCapacityEdge() {
        Saturated saturated = saturatedGate();
        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.REJECTED,
                saturated.gate.submit(portraitDeck(5L, "five"), LISTENER)
                        .publicResult().getStatus());
        saturated.registry.release(2L);
        assertTrue(saturated.gate.takeCapacityAvailableSignal(2L));
        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.ACCEPTED,
                saturated.gate.submit(portraitDeck(5L, "five"), LISTENER)
                        .publicResult().getStatus());

        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.REJECTED,
                saturated.gate.submit(portraitDeck(6L, "six"), LISTENER)
                        .publicResult().getStatus());
        saturated.registry.release(3L);
        assertTrue(saturated.gate.takeCapacityAvailableSignal(3L));
        assertFalse(saturated.gate.takeCapacityAvailableSignal(3L));
    }

    @Test
    public void closeSuppressesArmedCapacityAfterLateReleaseAcknowledgement() {
        Saturated saturated = saturatedGate();
        saturated.gate.submit(portraitDeck(5L, "five"), LISTENER);

        saturated.gate.close();
        saturated.registry.release(2L);

        assertFalse(saturated.gate.takeCapacityAvailableSignal(2L));
        PageSurfaceDeckSubmissionGate.Result<String> late =
                saturated.gate.submit(portraitDeck(5L, "five"), LISTENER);
        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.REJECTED,
                late.publicResult().getStatus());
        assertEquals(
                DeckRejectionReason.DISPOSED,
                late.publicResult().getRejectionReason());
    }

    @Test
    public void submitSignalAndCloseSerializeConcurrentCallers() throws Exception {
        Saturated saturated = saturatedGate();
        saturated.gate.submit(portraitDeck(5L, "five"), LISTENER);
        saturated.registry.release(2L);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> calls = new ArrayList<>();
        try {
            calls.add(executor.submit(() -> {
                await(start);
                saturated.gate.submit(portraitDeck(5L, "five"), LISTENER);
            }));
            calls.add(executor.submit(() -> {
                await(start);
                saturated.gate.takeCapacityAvailableSignal(2L);
            }));
            calls.add(executor.submit(() -> {
                await(start);
                saturated.gate.close();
            }));
            start.countDown();
            for (Future<?> call : calls) {
                call.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertFalse(saturated.gate.takeCapacityAvailableSignal(2L));
        assertEquals(
                DeckRejectionReason.DISPOSED,
                saturated.gate.submit(portraitDeck(6L, "six"), LISTENER)
                        .publicResult().getRejectionReason());
    }

    private static Saturated saturatedGate() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry registry = new DeckLeaseRegistry();
        PageSurfaceDeckSubmissionGate<String> gate =
                new PageSurfaceDeckSubmissionGate<>(coordinator, registry);
        assertAccepted(gate.submit(portraitDeck(1L, "one"), LISTENER));
        coordinator.beginSettlement();
        assertAccepted(gate.submit(portraitDeck(2L, "two"), LISTENER));
        PageSurfaceDeckSubmissionGate.Result<String> third =
                gate.submit(portraitDeck(3L, "three"), LISTENER);
        assertAccepted(third);
        markReleaseRequested(third, registry);
        PageSurfaceDeckSubmissionGate.Result<String> fourth =
                gate.submit(portraitDeck(4L, "four"), LISTENER);
        assertAccepted(fourth);
        markReleaseRequested(fourth, registry);
        assertEquals(DeckLeaseRegistry.MAX_DECK_LEASES, registry.size());
        return new Saturated(coordinator, registry, gate);
    }

    private static void assertAccepted(
            PageSurfaceDeckSubmissionGate.Result<String> result) {
        assertEquals(
                PageSurfaceDeckSubmissionResult.Status.ACCEPTED,
                result.publicResult().getStatus());
    }

    private static void markReleaseRequested(
            PageSurfaceDeckSubmissionGate.Result<String> result,
            DeckLeaseRegistry registry) {
        for (PageDeckCoordinator.Release<String> release : result.offer().getReleases()) {
            registry.markReleaseRequested(
                    release.getDeck().getGenerationId(),
                    release.getReason());
        }
    }

    private static PortraitPageDeck<String> portraitDeck(
            long generationId,
            String prefix) {
        return new PortraitPageDeck<>(
                image(generationId, prefix + "-previous", 0),
                image(generationId, prefix + "-current", 1),
                image(generationId, prefix + "-next", 2));
    }

    private static PageImage<String> image(
            long generationId,
            String id,
            int ordinal) {
        return new PageImage<>(generationId, id, ordinal, 100, 200, id);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent start");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static final class Saturated {
        final PageDeckCoordinator<String> coordinator;
        final DeckLeaseRegistry registry;
        final PageSurfaceDeckSubmissionGate<String> gate;

        Saturated(
                PageDeckCoordinator<String> coordinator,
                DeckLeaseRegistry registry,
                PageSurfaceDeckSubmissionGate<String> gate) {
            this.coordinator = coordinator;
            this.registry = registry;
            this.gate = gate;
        }
    }
}
