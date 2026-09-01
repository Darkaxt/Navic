package karacken.curl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Keeps deck replacement deterministic while a page turn is settling.
 *
 * <p>The coordinator is deliberately independent from Android and OpenGL so the replacement
 * policy can be verified without renderer timing.
 */
final class PageDeckCoordinator<T> {
    enum Placement {
        ACTIVE,
        PENDING,
        UNCHANGED,
        REJECTED
    }

    static final class Release<T> {
        private final PageDeck<T> deck;
        private final DeckReleaseReason reason;
        private final Boolean releasedFromActive;

        Release(PageDeck<T> deck, DeckReleaseReason reason) {
            this(deck, reason, null);
        }

        private Release(
                PageDeck<T> deck,
                DeckReleaseReason reason,
                Boolean releasedFromActive) {
            this.deck = Objects.requireNonNull(deck, "deck");
            this.reason = Objects.requireNonNull(reason, "reason");
            this.releasedFromActive = releasedFromActive;
        }

        static <T> Release<T> explicit(PageDeck<T> deck, boolean releasedFromActive) {
            return rollbackable(
                    deck,
                    DeckReleaseReason.EXPLICIT,
                    releasedFromActive);
        }

        static <T> Release<T> rollbackable(
                PageDeck<T> deck,
                DeckReleaseReason reason,
                boolean releasedFromActive) {
            return new Release<>(deck, reason, releasedFromActive);
        }

        PageDeck<T> getDeck() {
            return deck;
        }

        DeckReleaseReason getReason() {
            return reason;
        }

        boolean canRollbackRelease() {
            return releasedFromActive != null;
        }

        boolean wasReleasedFromActive() {
            if (releasedFromActive == null) {
                throw new IllegalStateException("Release was not an explicit rollback claim");
            }
            return releasedFromActive;
        }
    }

    static final class Promotion<T> {
        private final PageDeck<T> activatedDeck;
        private final Release<T> release;
        private final PageDeck<T> previousActiveDeck;
        private final PageDeck<T> previousPendingDeck;
        private final Boolean previousSettling;

        private Promotion(
                PageDeck<T> activatedDeck,
                Release<T> release,
                PageDeck<T> previousActiveDeck,
                PageDeck<T> previousPendingDeck,
                Boolean previousSettling) {
            this.activatedDeck = activatedDeck;
            this.release = release;
            this.previousActiveDeck = previousActiveDeck;
            this.previousPendingDeck = previousPendingDeck;
            this.previousSettling = previousSettling;
        }

        static <T> Promotion<T> none() {
            return new Promotion<>(null, null, null, null, null);
        }

        static <T> Promotion<T> activated(
                PageDeck<T> deck,
                PageDeck<T> releasedDeck,
                PageDeck<T> previousActiveDeck,
                PageDeck<T> previousPendingDeck,
                boolean previousSettling) {
            Release<T> release = releasedDeck == null
                    ? null
                    : new Release<>(releasedDeck, DeckReleaseReason.REPLACED);
            return new Promotion<>(
                    deck,
                    release,
                    previousActiveDeck,
                    previousPendingDeck,
                    previousSettling);
        }

        PageDeck<T> getActivatedDeck() {
            return activatedDeck;
        }

        PageDeck<T> getReleasedDeck() {
            return release == null ? null : release.getDeck();
        }

        DeckReleaseReason getReleaseReason() {
            return release == null ? null : release.getReason();
        }

        Release<T> getRelease() {
            return release;
        }

        boolean canRollback() {
            return previousSettling != null;
        }
    }

    static final class Offer<T> {
        private final Placement placement;
        private final DeckRejectionReason rejectionReason;
        private final List<Release<T>> releases;
        private final PageDeck<T> previousActiveDeck;
        private final PageDeck<T> previousPendingDeck;
        private final long previousLatestGeneration;

        private Offer(
                Placement placement,
                DeckRejectionReason rejectionReason,
                List<Release<T>> releases,
                PageDeck<T> previousActiveDeck,
                PageDeck<T> previousPendingDeck,
                long previousLatestGeneration) {
            this.placement = placement;
            this.rejectionReason = rejectionReason;
            this.releases = releases;
            this.previousActiveDeck = previousActiveDeck;
            this.previousPendingDeck = previousPendingDeck;
            this.previousLatestGeneration = previousLatestGeneration;
        }

        static <T> Offer<T> active(
                List<Release<T>> releases,
                PageDeck<T> previousActiveDeck,
                PageDeck<T> previousPendingDeck,
                long previousLatestGeneration) {
            return new Offer<>(
                    Placement.ACTIVE,
                    null,
                    Collections.unmodifiableList(new ArrayList<>(releases)),
                    previousActiveDeck,
                    previousPendingDeck,
                    previousLatestGeneration);
        }

        static <T> Offer<T> pending(
                Release<T> release,
                PageDeck<T> previousActiveDeck,
                PageDeck<T> previousPendingDeck,
                long previousLatestGeneration) {
            return new Offer<>(
                    Placement.PENDING,
                    null,
                    release == null
                            ? Collections.emptyList()
                            : Collections.singletonList(release),
                    previousActiveDeck,
                    previousPendingDeck,
                    previousLatestGeneration);
        }

        static <T> Offer<T> unchanged() {
            return new Offer<>(
                    Placement.UNCHANGED,
                    null,
                    Collections.emptyList(),
                    null,
                    null,
                    Long.MIN_VALUE);
        }

        static <T> Offer<T> rejected(DeckRejectionReason reason) {
            return new Offer<>(
                    Placement.REJECTED,
                    reason,
                    Collections.emptyList(),
                    null,
                    null,
                    Long.MIN_VALUE);
        }

        Placement getPlacement() {
            return placement;
        }

        DeckRejectionReason getRejectionReason() {
            return rejectionReason;
        }

        List<Release<T>> getReleases() {
            return releases;
        }

        PageDeck<T> getReleasedDeck() {
            return releases.isEmpty() ? null : releases.get(0).getDeck();
        }

        DeckReleaseReason getReleaseReason() {
            return releases.isEmpty() ? null : releases.get(0).getReason();
        }
    }

    private final Runnable ownershipMutated;
    private PageDeck<T> activeDeck;
    private PageDeck<T> pendingDeck;
    private long latestGeneration = Long.MIN_VALUE;
    private boolean settling;
    private boolean disposed;

    PageDeckCoordinator() {
        this(() -> {});
    }

    PageDeckCoordinator(Runnable ownershipMutated) {
        this.ownershipMutated =
                Objects.requireNonNull(ownershipMutated, "ownershipMutated");
    }

    Offer<T> offer(PageDeck<T> deck) {
        final Offer<T> result;
        synchronized (this) {
            if (disposed) {
                return Offer.rejected(DeckRejectionReason.DISPOSED);
            }
            long generationId = deck.getGenerationId();
            if (generationId < latestGeneration) {
                return Offer.rejected(DeckRejectionReason.STALE_GENERATION);
            }
            if (generationId == latestGeneration) {
                PageDeck<T> retained = retainedDeck(generationId);
                if (retained != null && hasSameIdentity(retained, deck)) {
                    return Offer.unchanged();
                }
                return Offer.rejected(DeckRejectionReason.CONFLICTING_GENERATION);
            }

            PageDeck<T> previousActiveDeck = activeDeck;
            PageDeck<T> previousPendingDeck = pendingDeck;
            long previousLatestGeneration = latestGeneration;
            latestGeneration = generationId;
            if (settling) {
                PageDeck<T> replaced = pendingDeck;
                pendingDeck = deck;
                Release<T> release = replaced == null
                        ? null
                        : new Release<>(replaced, DeckReleaseReason.REPLACED);
                result = Offer.pending(
                        release,
                        previousActiveDeck,
                        previousPendingDeck,
                        previousLatestGeneration);
            } else {
                List<Release<T>> releases = new ArrayList<>(2);
                addRelease(releases, activeDeck, DeckReleaseReason.REPLACED);
                if (pendingDeck != activeDeck) {
                    addRelease(releases, pendingDeck, DeckReleaseReason.REPLACED);
                }
                activeDeck = deck;
                pendingDeck = null;
                result = Offer.active(
                        releases,
                        previousActiveDeck,
                        previousPendingDeck,
                        previousLatestGeneration);
            }
        }
        ownershipMutated.run();
        return result;
    }

    boolean rollback(PageDeck<T> deck, Offer<T> offer) {
        Objects.requireNonNull(deck, "deck");
        Objects.requireNonNull(offer, "offer");
        synchronized (this) {
            switch (offer.getPlacement()) {
                case ACTIVE:
                    if (activeDeck != deck || pendingDeck != null) {
                        return false;
                    }
                    break;
                case PENDING:
                    if (pendingDeck != deck) {
                        return false;
                    }
                    break;
                default:
                    return false;
            }
            if (latestGeneration != deck.getGenerationId()) {
                return false;
            }
            activeDeck = offer.previousActiveDeck;
            pendingDeck = offer.previousPendingDeck;
            latestGeneration = offer.previousLatestGeneration;
        }
        ownershipMutated.run();
        return true;
    }

    void beginSettlement() {
        synchronized (this) {
            if (disposed || settling) {
                return;
            }
            settling = true;
        }
        ownershipMutated.run();
    }

    Promotion<T> completeSettlement() {
        final Promotion<T> result;
        synchronized (this) {
            if (!settling && pendingDeck == null) {
                return Promotion.none();
            }
            PageDeck<T> previousActiveDeck = activeDeck;
            PageDeck<T> previousPendingDeck = pendingDeck;
            boolean previousSettling = settling;
            settling = false;
            if (pendingDeck != null) {
                PageDeck<T> released = activeDeck;
                activeDeck = pendingDeck;
                pendingDeck = null;
                result = Promotion.activated(
                        activeDeck,
                        released,
                        previousActiveDeck,
                        previousPendingDeck,
                        previousSettling);
            } else {
                result = Promotion.none();
            }
        }
        ownershipMutated.run();
        return result;
    }

    void cancelSettlement() {
        synchronized (this) {
            if (!settling) {
                return;
            }
            settling = false;
        }
        ownershipMutated.run();
    }

    Promotion<T> activatePending() {
        final Promotion<T> result;
        synchronized (this) {
            if (disposed || settling || pendingDeck == null) {
                return Promotion.none();
            }
            PageDeck<T> previousActiveDeck = activeDeck;
            PageDeck<T> previousPendingDeck = pendingDeck;
            boolean previousSettling = settling;
            PageDeck<T> released = activeDeck;
            activeDeck = pendingDeck;
            pendingDeck = null;
            result = Promotion.activated(
                    activeDeck,
                    released,
                    previousActiveDeck,
                    previousPendingDeck,
                    previousSettling);
        }
        ownershipMutated.run();
        return result;
    }

    boolean rollbackPromotion(Promotion<T> promotion) {
        Objects.requireNonNull(promotion, "promotion");
        if (!promotion.canRollback()) {
            return false;
        }
        synchronized (this) {
            if (disposed
                    || activeDeck != promotion.activatedDeck
                    || pendingDeck != null
                    || settling) {
                return false;
            }
            activeDeck = promotion.previousActiveDeck;
            pendingDeck = promotion.previousPendingDeck;
            settling = promotion.previousSettling;
        }
        ownershipMutated.run();
        return true;
    }

    synchronized PageDeck<T> getActiveDeck() {
        return activeDeck;
    }

    synchronized PageDeck<T> getPendingDeck() {
        return pendingDeck;
    }

    synchronized boolean isSettling() {
        return settling;
    }

    Release<T> release(long generationId) {
        final Release<T> result;
        synchronized (this) {
            if (activeDeck != null && activeDeck.getGenerationId() == generationId) {
                PageDeck<T> released = activeDeck;
                activeDeck = null;
                result = Release.explicit(released, true);
            } else if (pendingDeck != null
                    && pendingDeck.getGenerationId() == generationId) {
                PageDeck<T> released = pendingDeck;
                pendingDeck = null;
                result = Release.explicit(released, false);
            } else {
                return null;
            }
        }
        ownershipMutated.run();
        return result;
    }

    boolean rollbackRelease(Release<T> release) {
        Objects.requireNonNull(release, "release");
        if (!release.canRollbackRelease()) {
            return false;
        }
        synchronized (this) {
            if (disposed) {
                return false;
            }
            if (release.wasReleasedFromActive()) {
                if (activeDeck != null) {
                    return false;
                }
                activeDeck = release.getDeck();
            } else {
                if (pendingDeck != null) {
                    return false;
                }
                pendingDeck = release.getDeck();
            }
        }
        ownershipMutated.run();
        return true;
    }

    Release<T> releasePending(DeckReleaseReason reason) {
        Objects.requireNonNull(reason, "reason");
        final Release<T> result;
        synchronized (this) {
            if (pendingDeck == null) {
                return null;
            }
            PageDeck<T> released = pendingDeck;
            pendingDeck = null;
            result = Release.rollbackable(released, reason, false);
        }
        ownershipMutated.run();
        return result;
    }

    List<Release<T>> dispose() {
        final List<Release<T>> result;
        synchronized (this) {
            if (disposed) {
                return Collections.emptyList();
            }
            disposed = true;
            settling = false;
            List<Release<T>> releases = new ArrayList<>(2);
            addRelease(releases, activeDeck, DeckReleaseReason.DISPOSED);
            if (pendingDeck != activeDeck) {
                addRelease(releases, pendingDeck, DeckReleaseReason.DISPOSED);
            }
            activeDeck = null;
            pendingDeck = null;
            result = Collections.unmodifiableList(new ArrayList<>(releases));
        }
        ownershipMutated.run();
        return result;
    }

    private PageDeck<T> retainedDeck(long generationId) {
        if (pendingDeck != null && pendingDeck.getGenerationId() == generationId) {
            return pendingDeck;
        }
        if (activeDeck != null && activeDeck.getGenerationId() == generationId) {
            return activeDeck;
        }
        return null;
    }

    private static <T> boolean hasSameIdentity(
            PageDeck<T> first,
            PageDeck<T> second) {
        if (first.getMode() != second.getMode()
                || first.canTurn(PageChange.PREVIOUS) != second.canTurn(PageChange.PREVIOUS)
                || first.canTurn(PageChange.NEXT) != second.canTurn(PageChange.NEXT)) {
            return false;
        }
        List<PageImage<T>> firstPages = first.getPages();
        List<PageImage<T>> secondPages = second.getPages();
        if (firstPages.size() != secondPages.size()) {
            return false;
        }
        for (int index = 0; index < firstPages.size(); index++) {
            if (!hasSamePageMetadata(firstPages.get(index), secondPages.get(index))) {
                return false;
            }
        }
        return hasSamePageMetadata(
                        first.getSettlementPage(PageChange.PREVIOUS),
                        second.getSettlementPage(PageChange.PREVIOUS))
                && hasSamePageMetadata(
                        first.getSettlementPage(PageChange.NONE),
                        second.getSettlementPage(PageChange.NONE))
                && hasSamePageMetadata(
                        first.getSettlementPage(PageChange.NEXT),
                        second.getSettlementPage(PageChange.NEXT));
    }

    private static boolean hasSamePageMetadata(PageImage<?> first, PageImage<?> second) {
        return first.getLogicalPageId().equals(second.getLogicalPageId())
                && first.getOrdinal() == second.getOrdinal()
                && first.getWidthPx() == second.getWidthPx()
                && first.getHeightPx() == second.getHeightPx()
                && Objects.equals(first.getDisplayRect(), second.getDisplayRect())
                && Objects.equals(first.getMaterial(), second.getMaterial())
                && first.hasOverlay() == second.hasOverlay()
                && first.isFiller() == second.isFiller()
                && (!first.isFiller()
                        || first.getFillerColorArgb() == second.getFillerColorArgb());
    }

    private static <T> void addRelease(
            List<Release<T>> releases,
            PageDeck<T> deck,
            DeckReleaseReason reason) {
        if (deck != null) {
            releases.add(new Release<>(deck, reason));
        }
    }
}
