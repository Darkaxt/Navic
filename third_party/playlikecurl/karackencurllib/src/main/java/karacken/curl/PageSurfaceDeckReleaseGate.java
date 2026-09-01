package karacken.curl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PageSurfaceDeckReleaseGate<T> {
    @FunctionalInterface
    interface RendererReleaseQueue {
        void enqueue(long generationId, DeckReleaseReason reason);
    }

    private enum RecordState {
        CLAIMED,
        ACCEPTED
    }

    private final PageDeckCoordinator<T> coordinator;
    private final DeckLeaseRegistry leaseRegistry;
    private final Map<Long, RecordState> releaseRecords = new LinkedHashMap<>();
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
        RecordState retained = releaseRecords.get(generationId);
        if (retained == RecordState.ACCEPTED) {
            return PageSurfaceDeckReleaseResult.alreadyAccepted();
        }
        if (retained != null) {
            return PageSurfaceDeckReleaseResult.rejected(
                    PageSurfaceDeckReleaseResult.RejectionReason.STATE_CONFLICT);
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
        List<PageDeckCoordinator.Release<T>> claimed = java.util.Collections.emptyList();
        try {
            claimed = claimReleases(java.util.Collections.singletonList(release));
            queue.enqueue(generationId, release.getReason());
            acceptClaims(claimed);
        } catch (RuntimeException | Error queueFailure) {
            rollbackRejectedQueue(release, claimed, queueFailure);
            return PageSurfaceDeckReleaseResult.rejected(
                    PageSurfaceDeckReleaseResult.RejectionReason.QUEUE_REJECTED);
        }
        return PageSurfaceDeckReleaseResult.accepted();
    }

    synchronized void queueAutomatic(
            List<PageDeckCoordinator.Release<T>> releases,
            Runnable queue) {
        Objects.requireNonNull(releases, "releases");
        Objects.requireNonNull(queue, "queue");
        if (closed) {
            throw new IllegalStateException("Release gate is closed");
        }
        List<PageDeckCoordinator.Release<T>> claimed = claimReleases(releases);
        try {
            queue.run();
            acceptClaims(claimed);
        } catch (RuntimeException | Error queueFailure) {
            rollbackClaims(claimed, queueFailure);
            throw queueFailure;
        }
    }

    synchronized void acceptTerminal(
            List<PageDeckCoordinator.Release<T>> releases) {
        Objects.requireNonNull(releases, "releases");
        List<PageDeckCoordinator.Release<T>> claimed = claimReleases(releases);
        acceptClaims(claimed);
    }

    private List<PageDeckCoordinator.Release<T>> claimReleases(
            List<PageDeckCoordinator.Release<T>> releases) {
        List<PageDeckCoordinator.Release<T>> claimed = new ArrayList<>(releases.size());
        try {
            for (PageDeckCoordinator.Release<T> release : releases) {
                Objects.requireNonNull(release, "release");
                long generationId = release.getDeck().getGenerationId();
                if (releaseRecords.containsKey(generationId)
                        || !leaseRegistry.contains(generationId)
                        || leaseRegistry.isReleaseRequested(generationId)) {
                    throw new IllegalStateException(
                            "Release generation does not have one unclaimed lease");
                }
                releaseRecords.put(generationId, RecordState.CLAIMED);
                claimed.add(release);
                leaseRegistry.markReleaseRequested(generationId, release.getReason());
                if (!leaseRegistry.isReleaseRequested(generationId)) {
                    throw new IllegalStateException(
                            "Release claim did not mark its bitmap lease");
                }
            }
            return claimed;
        } catch (RuntimeException | Error claimFailure) {
            rollbackClaims(claimed, claimFailure);
            throw claimFailure;
        }
    }

    private void acceptClaims(List<PageDeckCoordinator.Release<T>> claimed) {
        for (PageDeckCoordinator.Release<T> release : claimed) {
            long generationId = release.getDeck().getGenerationId();
            if (releaseRecords.replace(
                    generationId,
                    RecordState.CLAIMED,
                    RecordState.ACCEPTED)) {
                continue;
            }
            throw new IllegalStateException(
                    "Release claim was not retained through queue acceptance");
        }
    }

    private void rollbackRejectedQueue(
            PageDeckCoordinator.Release<T> release,
            List<PageDeckCoordinator.Release<T>> claimed,
            Throwable queueFailure) {
        Throwable rollbackFailure = rollbackClaims(claimed, queueFailure);
        final boolean coordinatorRolledBack;
        try {
            coordinatorRolledBack = coordinator.rollbackRelease(release);
        } catch (Throwable failure) {
            IllegalStateException rollbackFailureException = new IllegalStateException(
                    "Rejected renderer release queue could not restore coordinator ownership",
                    queueFailure);
            if (failure != queueFailure) {
                rollbackFailureException.addSuppressed(failure);
            }
            throw rollbackFailureException;
        }
        if (rollbackFailure == null && coordinatorRolledBack) {
            return;
        }
        IllegalStateException failure = new IllegalStateException(
                "Rejected renderer release queue could not restore surface ownership",
                queueFailure);
        if (rollbackFailure != null && rollbackFailure != queueFailure) {
            failure.addSuppressed(rollbackFailure);
        }
        throw failure;
    }

    private Throwable rollbackClaims(
            List<PageDeckCoordinator.Release<T>> claimed,
            Throwable originalFailure) {
        Throwable rollbackFailure = null;
        for (int index = claimed.size() - 1; index >= 0; index -= 1) {
            PageDeckCoordinator.Release<T> release = claimed.get(index);
            long generationId = release.getDeck().getGenerationId();
            releaseRecords.remove(generationId, RecordState.CLAIMED);
            try {
                if (!leaseRegistry.rollbackReleaseRequested(
                        generationId,
                        release.getReason())) {
                    throw new IllegalStateException(
                            "Release claim did not retain its lease marker");
                }
            } catch (Throwable failure) {
                if (rollbackFailure == null) {
                    rollbackFailure = failure;
                } else if (failure != rollbackFailure) {
                    rollbackFailure.addSuppressed(failure);
                }
            }
        }
        if (rollbackFailure != null
                && originalFailure != null
                && rollbackFailure != originalFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
        return rollbackFailure;
    }

    synchronized boolean complete(long generationId) {
        return releaseRecords.remove(generationId) != null;
    }

    synchronized boolean isReleaseInFlight(long generationId) {
        return releaseRecords.containsKey(generationId);
    }

    synchronized void close() {
        closed = true;
    }
}
