package karacken.curl;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class PageSurfaceDeckReleaseGate<T> {
    @FunctionalInterface
    interface RendererReleaseQueue {
        void enqueue(long generationId, DeckReleaseReason reason);
    }

    private final PageDeckCoordinator<T> coordinator;
    private final DeckLeaseRegistry leaseRegistry;
    private final Set<Long> releaseInFlight = new HashSet<>();
    private boolean closed;

    PageSurfaceDeckReleaseGate(
            PageDeckCoordinator<T> coordinator,
            DeckLeaseRegistry leaseRegistry) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.leaseRegistry = Objects.requireNonNull(leaseRegistry, "leaseRegistry");
    }

    synchronized PageSurfaceDeckReleaseResult request(
            long generationId,
            RendererReleaseQueue queue) {
        Objects.requireNonNull(queue, "queue");
        if (closed) {
            return PageSurfaceDeckReleaseResult.rejected(
                    PageSurfaceDeckReleaseResult.RejectionReason.DISPOSED);
        }
        if (releaseInFlight.contains(generationId)) {
            return PageSurfaceDeckReleaseResult.alreadyAccepted();
        }
        if (!leaseRegistry.contains(generationId)
                || leaseRegistry.isReleaseRequested(generationId)) {
            return PageSurfaceDeckReleaseResult.rejected(
                    PageSurfaceDeckReleaseResult.RejectionReason.STATE_CONFLICT);
        }
        PageDeckCoordinator.Release<T> release = coordinator.release(generationId);
        if (release == null) {
            return PageSurfaceDeckReleaseResult.rejected(
                    PageSurfaceDeckReleaseResult.RejectionReason.NOT_RETAINED);
        }
        releaseInFlight.add(generationId);
        leaseRegistry.markReleaseRequested(generationId, release.getReason());
        try {
            queue.enqueue(generationId, release.getReason());
        } catch (RuntimeException | Error queueFailure) {
            rollbackRejectedQueue(release, queueFailure);
            return PageSurfaceDeckReleaseResult.rejected(
                    PageSurfaceDeckReleaseResult.RejectionReason.QUEUE_REJECTED);
        }
        return PageSurfaceDeckReleaseResult.accepted();
    }

    private void rollbackRejectedQueue(
            PageDeckCoordinator.Release<T> release,
            Throwable queueFailure) {
        long generationId = release.getDeck().getGenerationId();
        releaseInFlight.remove(generationId);
        boolean leaseRolledBack =
                leaseRegistry.rollbackReleaseRequested(generationId, release.getReason());
        boolean coordinatorRolledBack = coordinator.rollbackRelease(release);
        if (leaseRolledBack && coordinatorRolledBack) {
            return;
        }
        IllegalStateException rollbackFailure = new IllegalStateException(
                "Rejected renderer release queue could not restore surface ownership");
        rollbackFailure.addSuppressed(queueFailure);
        throw rollbackFailure;
    }

    synchronized boolean complete(long generationId) {
        return releaseInFlight.remove(generationId);
    }

    synchronized boolean isReleaseInFlight(long generationId) {
        return releaseInFlight.contains(generationId);
    }

    synchronized void close() {
        closed = true;
        releaseInFlight.clear();
    }
}
