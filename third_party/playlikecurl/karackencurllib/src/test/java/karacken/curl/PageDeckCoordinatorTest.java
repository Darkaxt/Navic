package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
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

    private static PageImage<String> image(long generationId, String id, int ordinal) {
        return new PageImage<>(generationId, id, ordinal, 100, 200, id);
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
