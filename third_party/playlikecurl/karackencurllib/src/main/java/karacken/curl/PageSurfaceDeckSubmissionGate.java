package karacken.curl;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class PageSurfaceDeckSubmissionGate<T> {
    static final class Result<T> {
        private final PageDeckCoordinator.Offer<T> offer;
        private final PageSurfaceDeckSubmissionResult publicResult;

        Result(
                PageDeckCoordinator.Offer<T> offer,
                PageSurfaceDeckSubmissionResult publicResult) {
            this.offer = offer;
            this.publicResult = Objects.requireNonNull(
                    publicResult,
                    "publicResult");
            if (offer == null
                    && publicResult.getStatus()
                            != PageSurfaceDeckSubmissionResult.Status.REJECTED) {
                throw new IllegalArgumentException(
                        "Only a pre-offer rejection may omit the offer");
            }
        }

        PageDeckCoordinator.Offer<T> offer() {
            return offer;
        }

        PageSurfaceDeckSubmissionResult publicResult() {
            return publicResult;
        }
    }

    private final PageDeckCoordinator<T> coordinator;
    private final DeckLeaseRegistry leaseRegistry;
    private final Set<Long> releaseBlockedGenerations = new HashSet<>();
    private boolean globalCapacitySignalArmed;
    private boolean closed;

    PageSurfaceDeckSubmissionGate(
            PageDeckCoordinator<T> coordinator,
            DeckLeaseRegistry leaseRegistry) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.leaseRegistry = Objects.requireNonNull(leaseRegistry, "leaseRegistry");
    }

    synchronized Result<T> submit(
            PageDeck<T> deck,
            PageSurfaceListener listener) {
        Objects.requireNonNull(deck, "deck");
        Objects.requireNonNull(listener, "listener");
        if (closed) {
            return preOfferRejected(DeckRejectionReason.DISPOSED);
        }
        long generationId = deck.getGenerationId();
        PageDeck<T> active = coordinator.getActiveDeck();
        PageDeck<T> pending = coordinator.getPendingDeck();
        boolean alreadyPlaced =
                (active != null && active.getGenerationId() == generationId)
                        || (pending != null
                                && pending.getGenerationId() == generationId);
        boolean existingLease = leaseRegistry.contains(generationId);
        if (!alreadyPlaced && existingLease) {
            releaseBlockedGenerations.add(generationId);
            return preOfferRejected(DeckRejectionReason.RESOURCE_CAPACITY);
        }
        if (!alreadyPlaced && !leaseRegistry.hasCapacity()) {
            globalCapacitySignalArmed = true;
            return preOfferRejected(DeckRejectionReason.RESOURCE_CAPACITY);
        }

        PageDeckCoordinator.Offer<T> offer = coordinator.offer(deck);
        switch (offer.getPlacement()) {
            case REJECTED:
                return result(offer);
            case UNCHANGED:
                if (!alreadyPlaced) {
                    throw new IllegalStateException(
                            "Only an active or pending generation may be unchanged");
                }
                return new Result<>(
                        offer,
                        PageSurfaceDeckSubmissionResult.unchanged());
            case ACTIVE:
            case PENDING:
                if (alreadyPlaced
                        || !leaseRegistry.acquire(generationId, listener)) {
                    throw new IllegalStateException(
                            "Accepted placement did not acquire one fresh lease");
                }
                return new Result<>(
                        offer,
                        PageSurfaceDeckSubmissionResult.accepted());
            default:
                throw new AssertionError("Unhandled deck placement");
        }
    }

    synchronized void rollbackAccepted(
            PageDeck<T> deck,
            Result<T> accepted) {
        Objects.requireNonNull(deck, "deck");
        Objects.requireNonNull(accepted, "accepted");
        if (accepted.publicResult().getStatus()
                != PageSurfaceDeckSubmissionResult.Status.ACCEPTED) {
            throw new IllegalArgumentException(
                    "Only a fresh accepted submission may be rolled back");
        }
        long generationId = deck.getGenerationId();
        if (!coordinator.rollback(deck, accepted.offer())) {
            throw new IllegalStateException(
                    "Accepted coordinator placement could not be rolled back");
        }
        if (leaseRegistry.release(generationId) == null) {
            throw new IllegalStateException(
                    "Accepted deck lease could not be rolled back");
        }
        releaseBlockedGenerations.remove(generationId);
    }

    private Result<T> preOfferRejected(DeckRejectionReason reason) {
        return new Result<>(
                null,
                PageSurfaceDeckSubmissionResult.rejected(reason));
    }

    private Result<T> result(PageDeckCoordinator.Offer<T> offer) {
        DeckRejectionReason reason = offer.getRejectionReason();
        if (reason == null) {
            throw new IllegalStateException(
                    "Rejected coordinator offer omitted its reason");
        }
        return new Result<>(
                offer,
                PageSurfaceDeckSubmissionResult.rejected(reason));
    }

    synchronized boolean takeCapacityAvailableSignal(long releasedGenerationId) {
        if (closed) {
            return false;
        }
        boolean generationAvailable =
                releaseBlockedGenerations.remove(releasedGenerationId);
        boolean hasCapacity = leaseRegistry.hasCapacity();
        if (generationAvailable && !hasCapacity) {
            globalCapacitySignalArmed = true;
            return false;
        }
        boolean globalCapacityAvailable =
                globalCapacitySignalArmed && hasCapacity;
        if (globalCapacityAvailable) {
            globalCapacitySignalArmed = false;
        }
        return generationAvailable || globalCapacityAvailable;
    }

    synchronized void close() {
        closed = true;
        globalCapacitySignalArmed = false;
        releaseBlockedGenerations.clear();
    }
}
