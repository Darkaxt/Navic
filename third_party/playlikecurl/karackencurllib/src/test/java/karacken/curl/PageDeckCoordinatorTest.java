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
    public void releaseClaimFailureRollsBackCoordinatorLeaseAndRecord() {
        AtomicInteger leaseMutations = new AtomicInteger();
        DeckLeaseRegistry leases = new DeckLeaseRegistry(() -> {
            if (leaseMutations.incrementAndGet() == 2) {
                throw new IllegalStateException("injected release-claim failure");
            }
        });
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> deck = portraitDeck(9, "claim-failure");
        coordinator.offer(deck);
        assertTrue(leases.acquire(9, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> gate =
                new PageSurfaceDeckReleaseGate<>(coordinator, leases);
        AtomicInteger queuedCommands = new AtomicInteger();

        PageSurfaceDeckReleaseResult rejected = gate.request(
                9,
                (generationId, reason) -> queuedCommands.incrementAndGet());

        assertEquals(
                PageSurfaceDeckReleaseResult.Status.REJECTED,
                rejected.getStatus());
        assertEquals(
                PageSurfaceDeckReleaseResult.RejectionReason.QUEUE_REJECTED,
                rejected.getRejectionReason());
        assertEquals(0, queuedCommands.get());
        assertSame(deck, coordinator.getActiveDeck());
        assertTrue(leases.contains(9));
        assertFalse(leases.isReleaseRequested(9));
        assertFalse(gate.isReleaseInFlight(9));
    }

    @Test
    public void automaticReplacementSharesDurableAcceptedReleaseWithExplicitRetry() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry leases = new DeckLeaseRegistry();
        PortraitPageDeck<String> original = portraitDeck(40, "original");
        PortraitPageDeck<String> replacement = portraitDeck(41, "replacement");
        PageSurfaceListener listener = new PageSurfaceListener() {};
        coordinator.offer(original);
        assertTrue(leases.acquire(40, listener));
        PageSurfaceDeckReleaseGate<String> gate =
                new PageSurfaceDeckReleaseGate<>(coordinator, leases);
        PageDeckCoordinator.Offer<String> offer = coordinator.offer(replacement);
        assertTrue(leases.acquire(41, listener));
        AtomicInteger queuedCommands = new AtomicInteger();

        gate.queueAutomatic(
                offer.getReleases(),
                queuedCommands::incrementAndGet);
        PageSurfaceDeckReleaseResult duplicate = gate.request(
                40,
                (generationId, reason) -> queuedCommands.incrementAndGet());

        assertEquals(
                PageSurfaceDeckReleaseResult.Status.ALREADY_ACCEPTED,
                duplicate.getStatus());
        assertEquals(1, queuedCommands.get());
        assertTrue(gate.isReleaseInFlight(40));
        assertTrue(leases.isReleaseRequested(40));
        assertSame(replacement, coordinator.getActiveDeck());

        assertTrue(gate.complete(40));
        DeckLeaseRegistry.Lease completed = leases.release(40);
        assertNotNull(completed);
        assertSame(listener, completed.getListener());
        assertEquals(DeckReleaseReason.REPLACED, completed.getReleaseReason());
        assertFalse(gate.complete(40));
        assertNull(leases.release(40));
    }

    @Test
    public void promotionDetachAndTerminalDisposalUseSameDurableReleaseRegistry() {
        PageDeckCoordinator<String> promotionCoordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry promotionLeases = new DeckLeaseRegistry();
        PortraitPageDeck<String> active = portraitDeck(50, "active");
        PortraitPageDeck<String> pending = portraitDeck(51, "pending");
        promotionCoordinator.offer(active);
        promotionCoordinator.beginSettlement();
        promotionCoordinator.offer(pending);
        assertTrue(promotionLeases.acquire(50, new PageSurfaceListener() {}));
        assertTrue(promotionLeases.acquire(51, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> promotionGate =
                new PageSurfaceDeckReleaseGate<>(promotionCoordinator, promotionLeases);
        PageDeckCoordinator.Promotion<String> promotion =
                promotionCoordinator.completeSettlement();
        promotionGate.queueAutomatic(
                java.util.Collections.singletonList(promotion.getRelease()),
                () -> {});
        assertEquals(
                PageSurfaceDeckReleaseResult.Status.ALREADY_ACCEPTED,
                promotionGate.request(50, (generationId, reason) -> {}).getStatus());

        PageDeckCoordinator<String> detachCoordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry detachLeases = new DeckLeaseRegistry();
        PortraitPageDeck<String> detachedActive = portraitDeck(60, "detached-active");
        PortraitPageDeck<String> detachedPending = portraitDeck(61, "detached-pending");
        detachCoordinator.offer(detachedActive);
        detachCoordinator.beginSettlement();
        detachCoordinator.offer(detachedPending);
        detachCoordinator.cancelSettlement();
        assertTrue(detachLeases.acquire(60, new PageSurfaceListener() {}));
        assertTrue(detachLeases.acquire(61, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> detachGate =
                new PageSurfaceDeckReleaseGate<>(detachCoordinator, detachLeases);
        PageDeckCoordinator.Release<String> detachedRelease =
                detachCoordinator.releasePending(DeckReleaseReason.SESSION_DETACHED);
        detachGate.queueAutomatic(
                java.util.Collections.singletonList(detachedRelease),
                () -> {});
        assertEquals(
                PageSurfaceDeckReleaseResult.Status.ALREADY_ACCEPTED,
                detachGate.request(61, (generationId, reason) -> {}).getStatus());

        PageDeckCoordinator<String> terminalCoordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry terminalLeases = new DeckLeaseRegistry();
        PortraitPageDeck<String> terminalActive = portraitDeck(70, "terminal-active");
        PortraitPageDeck<String> terminalPending = portraitDeck(71, "terminal-pending");
        terminalCoordinator.offer(terminalActive);
        terminalCoordinator.beginSettlement();
        terminalCoordinator.offer(terminalPending);
        assertTrue(terminalLeases.acquire(70, new PageSurfaceListener() {}));
        assertTrue(terminalLeases.acquire(71, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> terminalGate =
                new PageSurfaceDeckReleaseGate<>(terminalCoordinator, terminalLeases);
        terminalGate.acceptTerminal(terminalCoordinator.dispose());
        terminalGate.close();
        assertTrue(terminalGate.isReleaseInFlight(70));
        assertTrue(terminalGate.isReleaseInFlight(71));
        assertEquals(
                PageSurfaceDeckReleaseResult.RejectionReason.DISPOSED,
                terminalGate.request(70, (generationId, reason) -> {}).getRejectionReason());
    }

    @Test
    public void automaticQueueRejectionCanRestorePromotionAndDetachedPendingClaims() {
        PageDeckCoordinator<String> promotionCoordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry promotionLeases = new DeckLeaseRegistry();
        PortraitPageDeck<String> active = portraitDeck(80, "active");
        PortraitPageDeck<String> pending = portraitDeck(81, "pending");
        promotionCoordinator.offer(active);
        promotionCoordinator.beginSettlement();
        promotionCoordinator.offer(pending);
        assertTrue(promotionLeases.acquire(80, new PageSurfaceListener() {}));
        assertTrue(promotionLeases.acquire(81, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> promotionGate =
                new PageSurfaceDeckReleaseGate<>(promotionCoordinator, promotionLeases);
        PageDeckCoordinator.Promotion<String> promotion =
                promotionCoordinator.completeSettlement();
        try {
            promotionGate.queueAutomatic(
                    java.util.Collections.singletonList(promotion.getRelease()),
                    () -> {
                        throw new IllegalStateException("injected promotion queue rejection");
                    });
        } catch (IllegalStateException expected) {
            assertEquals("injected promotion queue rejection", expected.getMessage());
        }
        assertTrue(promotionCoordinator.rollbackPromotion(promotion));
        assertSame(active, promotionCoordinator.getActiveDeck());
        assertSame(pending, promotionCoordinator.getPendingDeck());
        assertTrue(promotionCoordinator.isSettling());
        assertFalse(promotionLeases.isReleaseRequested(80));
        assertFalse(promotionGate.isReleaseInFlight(80));

        PageDeckCoordinator<String> detachCoordinator = new PageDeckCoordinator<>();
        DeckLeaseRegistry detachLeases = new DeckLeaseRegistry();
        PortraitPageDeck<String> detachedActive = portraitDeck(90, "detached-active");
        PortraitPageDeck<String> detachedPending = portraitDeck(91, "detached-pending");
        detachCoordinator.offer(detachedActive);
        detachCoordinator.beginSettlement();
        detachCoordinator.offer(detachedPending);
        detachCoordinator.cancelSettlement();
        assertTrue(detachLeases.acquire(90, new PageSurfaceListener() {}));
        assertTrue(detachLeases.acquire(91, new PageSurfaceListener() {}));
        PageSurfaceDeckReleaseGate<String> detachGate =
                new PageSurfaceDeckReleaseGate<>(detachCoordinator, detachLeases);
        PageDeckCoordinator.Release<String> detachedRelease =
                detachCoordinator.releasePending(DeckReleaseReason.SESSION_DETACHED);
        try {
            detachGate.queueAutomatic(
                    java.util.Collections.singletonList(detachedRelease),
                    () -> {
                        throw new IllegalStateException("injected detach queue rejection");
                    });
        } catch (IllegalStateException expected) {
            assertEquals("injected detach queue rejection", expected.getMessage());
        }
        assertTrue(detachCoordinator.rollbackRelease(detachedRelease));
        assertSame(detachedPending, detachCoordinator.getPendingDeck());
        assertFalse(detachLeases.isReleaseRequested(91));
        assertFalse(detachGate.isReleaseInFlight(91));
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

        assertTrue(gate.isReleaseInFlight(8));
        assertTrue(leases.contains(8));
        assertTrue(leases.isReleaseRequested(8));
        List<DeckLeaseRegistry.Lease> terminalLeases =
                leases.releaseAll(DeckReleaseReason.DISPOSED);
        assertEquals(1, terminalLeases.size());
        assertTrue(gate.complete(terminalLeases.get(0).getGenerationId()));
        assertFalse(gate.isReleaseInFlight(8));
        assertFalse(gate.complete(8));
        assertNull(leases.release(8));
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
