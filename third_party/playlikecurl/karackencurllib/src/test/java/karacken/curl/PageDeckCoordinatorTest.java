package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class PageDeckCoordinatorTest {
    @Test
    public void installsLatestDeckImmediatelyWhenIdle() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> first = portraitDeck(1, "one");
        PortraitPageDeck<String> second = portraitDeck(2, "two");

        assertEquals(
                PageDeckCoordinator.Placement.ACTIVE,
                coordinator.offer(first).getPlacement());
        assertEquals(
                PageDeckCoordinator.Placement.ACTIVE,
                coordinator.offer(second).getPlacement());
        assertSame(second, coordinator.getActiveDeck());
        assertNull(coordinator.getPendingDeck());
    }

    @Test
    public void replacingIdleDeckReturnsTheReleasedActiveLease() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> first = portraitDeck(1, "one");
        PortraitPageDeck<String> second = portraitDeck(2, "two");
        coordinator.offer(first);

        PageDeckCoordinator.Offer<String> result = coordinator.offer(second);

        assertSame(first, result.getReleasedDeck());
        assertEquals(DeckReleaseReason.REPLACED, result.getReleaseReason());
        assertSame(second, coordinator.getActiveDeck());
    }

    @Test
    public void acceptedIdleOfferCanRollbackBeforeAdmission() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> first = portraitDeck(1, "one");
        PortraitPageDeck<String> second = portraitDeck(2, "two");
        coordinator.offer(first);

        PageDeckCoordinator.Offer<String> accepted = coordinator.offer(second);

        assertTrue(coordinator.rollback(second, accepted));
        assertSame(first, coordinator.getActiveDeck());
        assertNull(coordinator.getPendingDeck());
        assertEquals(
                PageDeckCoordinator.Placement.ACTIVE,
                coordinator.offer(second).getPlacement());
    }

    @Test
    public void acceptedPendingOfferRollbackRestoresReplacedPendingDeck() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> active = portraitDeck(1, "active");
        PortraitPageDeck<String> firstPending = portraitDeck(2, "pending-one");
        PortraitPageDeck<String> secondPending = portraitDeck(3, "pending-two");
        coordinator.offer(active);
        coordinator.beginSettlement();
        coordinator.offer(firstPending);

        PageDeckCoordinator.Offer<String> accepted = coordinator.offer(secondPending);

        assertTrue(coordinator.rollback(secondPending, accepted));
        assertSame(active, coordinator.getActiveDeck());
        assertSame(firstPending, coordinator.getPendingDeck());
        assertFalse(coordinator.rollback(secondPending, accepted));
    }

    @Test
    public void identicalSameGenerationSubmissionIsIdempotent() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> original = portraitDeck(4, "same");
        PortraitPageDeck<String> duplicate = portraitDeck(4, "same");
        coordinator.offer(original);

        PageDeckCoordinator.Offer<String> result = coordinator.offer(duplicate);

        assertEquals(PageDeckCoordinator.Placement.UNCHANGED, result.getPlacement());
        assertSame(original, coordinator.getActiveDeck());
        assertNull(result.getReleasedDeck());
    }

    @Test
    public void conflictingSameGenerationSubmissionIsRejected() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> original = portraitDeck(4, "original");
        coordinator.offer(original);

        PageDeckCoordinator.Offer<String> result =
                coordinator.offer(portraitDeck(4, "different"));

        assertEquals(PageDeckCoordinator.Placement.REJECTED, result.getPlacement());
        assertEquals(
                DeckRejectionReason.CONFLICTING_GENERATION,
                result.getRejectionReason());
        assertSame(original, coordinator.getActiveDeck());
    }

    @Test
    public void sameGenerationCannotSilentlyAddAnOverlay() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> original = portraitDeck(4, "same");
        coordinator.offer(original);

        PageDeckCoordinator.Offer<String> result =
                coordinator.offer(portraitDeckWithCurrentOverlay(4, "same"));

        assertEquals(PageDeckCoordinator.Placement.REJECTED, result.getPlacement());
        assertEquals(
                DeckRejectionReason.CONFLICTING_GENERATION,
                result.getRejectionReason());
        assertSame(original, coordinator.getActiveDeck());
    }

    @Test
    public void sameGenerationCannotSilentlyReplacePresentationMaterial() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> original = portraitDeckWithBacking(
                4, "same", 0xFFF5F2EA);
        coordinator.offer(original);

        PortraitPageDeck<String> replacement = portraitDeckWithBacking(
                4, "same", 0xFF403B35);
        assertFalse(original.getMaterial().equals(replacement.getMaterial()));

        PageDeckCoordinator.Offer<String> result = coordinator.offer(replacement);

        assertEquals(PageDeckCoordinator.Placement.REJECTED, result.getPlacement());
        assertEquals(
                DeckRejectionReason.CONFLICTING_GENERATION,
                result.getRejectionReason());
        assertSame(original, coordinator.getActiveDeck());
    }

    @Test
    public void rejectsStaleGenerationWithoutReplacingActiveDeck() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> current = portraitDeck(4, "current");
        coordinator.offer(current);

        PageDeckCoordinator.Offer<String> result =
                coordinator.offer(portraitDeck(3, "stale"));

        assertEquals(PageDeckCoordinator.Placement.REJECTED, result.getPlacement());
        assertEquals(DeckRejectionReason.STALE_GENERATION, result.getRejectionReason());
        assertSame(current, coordinator.getActiveDeck());
    }

    @Test
    public void keepsOnlyOneReplacementDeckDuringSettlement() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        coordinator.offer(portraitDeck(1, "active"));
        coordinator.beginSettlement();

        PortraitPageDeck<String> firstPending = portraitDeck(2, "pending-one");
        PortraitPageDeck<String> latestPending = portraitDeck(3, "pending-two");
        coordinator.offer(firstPending);
        PageDeckCoordinator.Offer<String> result = coordinator.offer(latestPending);

        assertEquals(PageDeckCoordinator.Placement.PENDING, result.getPlacement());
        assertSame(firstPending, result.getReleasedDeck());
        assertEquals(DeckReleaseReason.REPLACED, result.getReleaseReason());
        assertSame(latestPending, coordinator.getPendingDeck());
    }

    @Test
    public void promotesPendingDeckWhenSettlementCompletes() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> original = portraitDeck(1, "active");
        PortraitPageDeck<String> replacement = portraitDeck(2, "pending");
        coordinator.offer(original);
        coordinator.beginSettlement();
        coordinator.offer(replacement);

        PageDeckCoordinator.Promotion<String> promotion =
                coordinator.completeSettlement();

        assertSame(replacement, promotion.getActivatedDeck());
        assertSame(original, promotion.getReleasedDeck());
        assertEquals(DeckReleaseReason.REPLACED, promotion.getReleaseReason());
        assertSame(replacement, coordinator.getActiveDeck());
        assertNull(coordinator.getPendingDeck());
    }

    @Test
    public void cancellingSettlementDoesNotPromotePendingDeck() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> original = portraitDeck(1, "active");
        PortraitPageDeck<String> replacement = portraitDeck(2, "pending");
        coordinator.offer(original);
        coordinator.beginSettlement();
        coordinator.offer(replacement);

        coordinator.cancelSettlement();

        assertSame(original, coordinator.getActiveDeck());
        assertSame(replacement, coordinator.getPendingDeck());
        assertTrue(!coordinator.isSettling());
    }

    @Test
    public void explicitPendingActivationReleasesOldActiveDeck() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> original = portraitDeck(1, "active");
        PortraitPageDeck<String> replacement = portraitDeck(2, "pending");
        coordinator.offer(original);
        coordinator.beginSettlement();
        coordinator.offer(replacement);
        coordinator.cancelSettlement();

        PageDeckCoordinator.Promotion<String> promotion =
                coordinator.activatePending();

        assertSame(replacement, promotion.getActivatedDeck());
        assertSame(original, promotion.getReleasedDeck());
        assertEquals(DeckReleaseReason.REPLACED, promotion.getReleaseReason());
    }

    @Test
    public void explicitReleaseReturnsTheReleasedLease() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> active = portraitDeck(1, "active");
        coordinator.offer(active);

        PageDeckCoordinator.Release<String> release = coordinator.release(1);

        assertSame(active, release.getDeck());
        assertEquals(DeckReleaseReason.EXPLICIT, release.getReason());
        assertNull(coordinator.getActiveDeck());
    }

    @Test
    public void rejectedReleaseQueueRollsBackAndAcceptedRetryCompletesOnce() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry leases = new DeckLeaseRegistry();
        PortraitPageDeck<String> deck = portraitDeck(7, "transactional");
        PageSurfaceListener listener = new PageSurfaceListener() {};
        coordinator.offer(deck);
        assertTrue(leases.acquire(7, listener));
        PageSurfaceDeckReleaseGate<String> gate =
                new PageSurfaceDeckReleaseGate<>(coordinator, leases);
        AtomicInteger queuedCommands = new AtomicInteger();

        PageSurfaceDeckReleaseResult rejected = gate.request(
                7,
                (generationId, reason) -> {
                    queuedCommands.incrementAndGet();
                    assertNull(coordinator.getActiveDeck());
                    assertTrue(leases.isReleaseRequested(generationId));
                    throw new IllegalStateException("injected queue rejection");
                });

        assertEquals(
                PageSurfaceDeckReleaseResult.Status.REJECTED,
                rejected.getStatus());
        assertEquals(
                PageSurfaceDeckReleaseResult.RejectionReason.QUEUE_REJECTED,
                rejected.getRejectionReason());
        assertSame(deck, coordinator.getActiveDeck());
        assertTrue(leases.contains(7));
        assertFalse(leases.isReleaseRequested(7));
        assertFalse(gate.isReleaseInFlight(7));

        PageSurfaceDeckReleaseResult accepted = gate.request(
                7,
                (generationId, reason) -> queuedCommands.incrementAndGet());
        PageSurfaceDeckReleaseResult duplicate = gate.request(
                7,
                (generationId, reason) -> queuedCommands.incrementAndGet());

        assertEquals(PageSurfaceDeckReleaseResult.Status.ACCEPTED, accepted.getStatus());
        assertEquals(
                PageSurfaceDeckReleaseResult.Status.ALREADY_ACCEPTED,
                duplicate.getStatus());
        assertEquals(2, queuedCommands.get());
        assertNull(coordinator.getActiveDeck());
        assertTrue(leases.contains(7));
        assertTrue(leases.isReleaseRequested(7));
        assertTrue(gate.isReleaseInFlight(7));

        assertTrue(gate.complete(7));
        assertFalse(gate.complete(7));
        assertTrue(leases.rollbackReleaseRequested(7, DeckReleaseReason.EXPLICIT));
        PageSurfaceDeckReleaseResult missingCoordinatorOwnership = gate.request(
                7,
                (generationId, reason) -> queuedCommands.incrementAndGet());
        assertEquals(
                PageSurfaceDeckReleaseResult.Status.REJECTED,
                missingCoordinatorOwnership.getStatus());
        assertEquals(
                PageSurfaceDeckReleaseResult.RejectionReason.NOT_RETAINED,
                missingCoordinatorOwnership.getRejectionReason());
        assertEquals(2, queuedCommands.get());
        leases.markReleaseRequested(7, DeckReleaseReason.EXPLICIT);
        DeckLeaseRegistry.Lease completed = leases.release(7);
        assertNotNull(completed);
        assertSame(listener, completed.getListener());
        assertEquals(DeckReleaseReason.EXPLICIT, completed.getReleaseReason());
        assertNull(leases.release(7));
    }

    @Test
    public void releaseGateCloseRejectsNewWorkAndRetainsAcceptedLeaseForDisposal() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry leases = new DeckLeaseRegistry();
        PortraitPageDeck<String> deck = portraitDeck(8, "disposing");
        coordinator.offer(deck);
        assertTrue(leases.acquire(8, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> gate =
                new PageSurfaceDeckReleaseGate<>(coordinator, leases);
        assertEquals(
                PageSurfaceDeckReleaseResult.Status.ACCEPTED,
                gate.request(8, (generationId, reason) -> {}).getStatus());

        gate.close();

        assertFalse(gate.isReleaseInFlight(8));
        assertTrue(leases.contains(8));
        assertTrue(leases.isReleaseRequested(8));
        assertEquals(1, leases.releaseAll(DeckReleaseReason.DISPOSED).size());
        assertEquals(
                PageSurfaceDeckReleaseResult.RejectionReason.DISPOSED,
                gate.request(8, (generationId, reason) -> {}).getRejectionReason());
    }

    @Test
    public void detachedSessionReleasesOnlyPendingDeck() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> active = portraitDeck(1, "active");
        PortraitPageDeck<String> pending = portraitDeck(2, "pending");
        coordinator.offer(active);
        coordinator.beginSettlement();
        coordinator.offer(pending);
        coordinator.cancelSettlement();

        PageDeckCoordinator.Release<String> release =
                coordinator.releasePending(DeckReleaseReason.SESSION_DETACHED);

        assertSame(pending, release.getDeck());
        assertEquals(DeckReleaseReason.SESSION_DETACHED, release.getReason());
        assertSame(active, coordinator.getActiveDeck());
        assertNull(coordinator.getPendingDeck());
    }

    @Test
    public void disposalIsIdempotentAndRejectsFutureDecks() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> active = portraitDeck(1, "active");
        PortraitPageDeck<String> pending = portraitDeck(2, "pending");
        coordinator.offer(active);
        coordinator.beginSettlement();
        coordinator.offer(pending);

        List<PageDeckCoordinator.Release<String>> releases = coordinator.dispose();
        List<PageDeckCoordinator.Release<String>> repeated = coordinator.dispose();
        PageDeckCoordinator.Offer<String> result =
                coordinator.offer(portraitDeck(3, "late"));

        assertEquals(2, releases.size());
        assertSame(active, releases.get(0).getDeck());
        assertSame(pending, releases.get(1).getDeck());
        assertEquals(DeckReleaseReason.DISPOSED, releases.get(0).getReason());
        assertEquals(DeckReleaseReason.DISPOSED, releases.get(1).getReason());
        assertTrue(repeated.isEmpty());
        assertEquals(PageDeckCoordinator.Placement.REJECTED, result.getPlacement());
        assertEquals(DeckRejectionReason.DISPOSED, result.getRejectionReason());
        assertNull(coordinator.getActiveDeck());
        assertNull(coordinator.getPendingDeck());
    }

    @Test
    public void ownershipObserverReportsEachRealMutationOutsideMonitor() {
        AtomicInteger mutations = new AtomicInteger();
        AtomicBoolean callbackHeldMonitor = new AtomicBoolean();
        @SuppressWarnings("unchecked")
        PageDeckCoordinator<String>[] owner = new PageDeckCoordinator[1];
        owner[0] = new PageDeckCoordinator<>(() -> {
            mutations.incrementAndGet();
            callbackHeldMonitor.set(
                    callbackHeldMonitor.get()
                            || Thread.holdsLock(owner[0]));
        });
        PageDeckCoordinator<String> coordinator = owner[0];
        PortraitPageDeck<String> first = portraitDeck(1, "first");
        PortraitPageDeck<String> second = portraitDeck(2, "second");
        PortraitPageDeck<String> third = portraitDeck(3, "third");

        coordinator.offer(first);
        assertEquals(1, mutations.get());
        coordinator.offer(portraitDeck(1, "first"));
        coordinator.offer(portraitDeck(0, "stale"));
        assertEquals(1, mutations.get());

        PageDeckCoordinator.Offer<String> activeOffer = coordinator.offer(second);
        assertEquals(2, mutations.get());
        assertTrue(coordinator.rollback(second, activeOffer));
        assertEquals(3, mutations.get());
        assertFalse(coordinator.rollback(second, activeOffer));
        assertEquals(3, mutations.get());

        coordinator.beginSettlement();
        coordinator.beginSettlement();
        assertEquals(4, mutations.get());
        coordinator.offer(second);
        assertEquals(5, mutations.get());
        coordinator.completeSettlement();
        assertEquals(6, mutations.get());
        coordinator.completeSettlement();
        assertEquals(6, mutations.get());

        coordinator.beginSettlement();
        coordinator.completeSettlement();
        assertEquals(8, mutations.get());

        coordinator.beginSettlement();
        coordinator.offer(third);
        coordinator.cancelSettlement();
        coordinator.cancelSettlement();
        assertEquals(11, mutations.get());
        coordinator.releasePending(DeckReleaseReason.SESSION_DETACHED);
        coordinator.releasePending(DeckReleaseReason.SESSION_DETACHED);
        assertEquals(12, mutations.get());

        coordinator.release(2);
        coordinator.release(2);
        assertEquals(13, mutations.get());
        coordinator.dispose();
        coordinator.dispose();
        coordinator.offer(portraitDeck(4, "late"));
        assertEquals(14, mutations.get());
        assertFalse(callbackHeldMonitor.get());
    }

    @Test
    public void oneHundredSettlementsKeepExactlyOneActiveDeckAndNoPendingGrowth() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> active = portraitDeck(1, "page-1");
        coordinator.offer(active);

        for (long generation = 2; generation <= 101; generation += 1) {
            PortraitPageDeck<String> replacement =
                    portraitDeck(generation, "page-" + generation);
            coordinator.beginSettlement();
            PageDeckCoordinator.Offer<String> offer = coordinator.offer(replacement);

            assertEquals(PageDeckCoordinator.Placement.PENDING, offer.getPlacement());
            assertSame(active, coordinator.getActiveDeck());
            assertSame(replacement, coordinator.getPendingDeck());

            PageDeckCoordinator.Promotion<String> promotion =
                    coordinator.completeSettlement();

            assertSame(replacement, promotion.getActivatedDeck());
            assertSame(active, promotion.getReleasedDeck());
            assertSame(replacement, coordinator.getActiveDeck());
            assertNull(coordinator.getPendingDeck());
            assertTrue(!coordinator.isSettling());
            active = replacement;
        }

        List<PageDeckCoordinator.Release<String>> releases = coordinator.dispose();
        assertEquals(1, releases.size());
        assertSame(active, releases.get(0).getDeck());
    }

    private static PortraitPageDeck<String> portraitDeck(long generationId, String prefix) {
        return new PortraitPageDeck<>(
                image(generationId, prefix + "-previous", 0),
                image(generationId, prefix + "-current", 1),
                image(generationId, prefix + "-next", 2));
    }

    private static PortraitPageDeck<String> portraitDeckWithCurrentOverlay(
            long generationId,
            String prefix) {
        return new PortraitPageDeck<>(
                image(generationId, prefix + "-previous", 0),
                imageWithOverlay(generationId, prefix + "-current", 1),
                image(generationId, prefix + "-next", 2));
    }

    private static PortraitPageDeck<String> portraitDeckWithBacking(
            long generationId,
            String prefix,
            int backingColorArgb) {
        PageDisplayRect display = new PageDisplayRect(0, 0, 98, 200);
        PageDisplayRect backing = new PageDisplayRect(98, 0, 100, 200);
        return new PortraitPageDeck<>(
                imageWithBacking(
                        generationId, prefix + "-previous", 0, display, backing, backingColorArgb),
                imageWithBacking(
                        generationId, prefix + "-current", 1, display, backing, backingColorArgb),
                imageWithBacking(
                        generationId, prefix + "-next", 2, display, backing, backingColorArgb));
    }

    private static PageImage<String> image(long generationId, String id, int ordinal) {
        return new PageImage<>(generationId, id, ordinal, 100, 200, id);
    }

    private static PageImage<String> imageWithBacking(
            long generationId,
            String id,
            int ordinal,
            PageDisplayRect display,
            PageDisplayRect backing,
            int backingColorArgb) {
        return new PageImage<>(
                generationId,
                id,
                ordinal,
                100,
                200,
                display,
                id,
                backing,
                backingColorArgb);
    }

    private static PageImage<String> imageWithOverlay(
            long generationId,
            String id,
            int ordinal) {
        return new PageImage<>(
                generationId,
                id,
                ordinal,
                100,
                200,
                id,
                id + "-overlay");
    }
}
