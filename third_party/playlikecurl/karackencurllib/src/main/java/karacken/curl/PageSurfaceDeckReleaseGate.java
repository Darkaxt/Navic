package karacken.curl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

final class PageSurfaceDeckReleaseGate<T> {
    @FunctionalInterface
    interface RendererReleaseQueue {
        void enqueue(long generationId, DeckReleaseReason reason);
    }

    private static final AtomicLong NEXT_SESSION_ID = new AtomicLong();

    private final PageDeckCoordinator<T> coordinator;
    private final DeckLeaseRegistry leaseRegistry;
    private final long sessionId = NEXT_SESSION_ID.incrementAndGet();
    private final Map<Long, PageSurfaceGenerationReleaseRecord<T>> releaseRecords =
            new LinkedHashMap<>();
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
        PageSurfaceGenerationReleaseRecord<T> retained = releaseRecords.get(generationId);
        if (retained != null) {
            return duplicateResult(retained);
        }
        if (!leaseRegistry.contains(generationId)) {
            return PageSurfaceDeckReleaseResult.rejected(
                    PageSurfaceDeckReleaseResult.RejectionReason.STATE_CONFLICT);
        }
        PageDeckCoordinator.Release<T> release = coordinator.release(generationId);
        if (release == null) {
            return PageSurfaceDeckReleaseResult.rejected(
                    PageSurfaceDeckReleaseResult.RejectionReason.NOT_RETAINED);
        }
        PageSurfaceGenerationReleaseRecord<T> record = claimRelease(release);
        try {
            queue.enqueue(generationId, release.getReason());
            acceptClaim(record);
        } catch (RuntimeException | Error queueFailure) {
            rollbackRejectedQueue(release, record, queueFailure);
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
        List<PageSurfaceGenerationReleaseRecord<T>> claimed = claimReleases(releases);
        try {
            queue.run();
            acceptClaims(claimed);
        } catch (RuntimeException | Error queueFailure) {
            rollbackClaims(claimed);
            throw queueFailure;
        }
    }

    synchronized void acceptTerminal(List<PageDeckCoordinator.Release<T>> releases) {
        Objects.requireNonNull(releases, "releases");
        List<PageSurfaceGenerationReleaseRecord<T>> claimed = claimReleases(releases);
        acceptClaims(claimed);
    }

    synchronized boolean rendererDetached(long generationId) {
        PageSurfaceGenerationReleaseRecord<T> record = releaseRecords.get(generationId);
        return record != null
                && record.matches(sessionId, generationId)
                && record.rendererDetached();
    }

    synchronized boolean rendererDetached(
            long generationId,
            DeckReleaseReason rendererReason) {
        Objects.requireNonNull(rendererReason, "rendererReason");
        PageSurfaceGenerationReleaseRecord<T> record = releaseRecords.get(generationId);
        if (record != null) {
            return record.matches(sessionId, generationId) && record.rendererDetached();
        }
        if (closed || !leaseRegistry.contains(generationId)) {
            return false;
        }
        PageDeckCoordinator.Release<T> retained = coordinator.release(generationId);
        if (retained == null) {
            return false;
        }
        PageDeckCoordinator.Release<T> release = PageDeckCoordinator.Release.rollbackable(
                retained.getDeck(),
                rendererReason,
                retained.wasReleasedFromActive());
        record = claimRelease(release);
        acceptClaim(record);
        return record.rendererDetached();
    }

    synchronized List<PageSurfaceGenerationReleaseRecord<T>> terminallyAbandonAccepted() {
        return terminallyAbandonAccepted(generationId -> {});
    }

    synchronized List<PageSurfaceGenerationReleaseRecord<T>> terminallyAbandonAccepted(
            LongConsumer detachRendererReferences) {
        return terminallyAbandonAccepted(generationId -> false, detachRendererReferences);
    }

    synchronized List<PageSurfaceGenerationReleaseRecord<T>> terminallyAbandonAccepted(
            java.util.function.LongPredicate retainSelected,
            LongConsumer detachRendererReferences) {
        Objects.requireNonNull(retainSelected, "retainSelected");
        Objects.requireNonNull(detachRendererReferences, "detachRendererReferences");
        List<PageSurfaceGenerationReleaseRecord<T>> abandoned = new ArrayList<>();
        for (PageSurfaceGenerationReleaseRecord<T> record :
                new ArrayList<>(releaseRecords.values())) {
            long generationId = record.getGenerationId();
            if (!record.matches(sessionId, generationId)
                    || (!closed && retainSelected.test(generationId)
                        && record.getReason() != DeckReleaseReason.FAILED
                        && record.getReason() != DeckReleaseReason.DISPOSED)
                    || record.getState()
                            != PageSurfaceGenerationReleaseRecord.State.QUEUE_ACCEPTED) {
                continue;
            }
            detachRendererReferences.accept(generationId);
            if (releaseRecords.get(generationId) == record
                    && record.terminallyAbandon()) {
                abandoned.add(record);
            }
        }
        return Collections.unmodifiableList(abandoned);
    }

    synchronized boolean complete(long generationId) {
        PageSurfaceGenerationReleaseRecord<T> record = releaseRecords.get(generationId);
        if (record == null
                || !record.matches(sessionId, generationId)
                || !record.complete()) {
            return false;
        }
        releaseRecords.remove(generationId, record);
        return true;
    }

    synchronized PageSurfaceGenerationReleaseRecord.State stateFor(long generationId) {
        PageSurfaceGenerationReleaseRecord<T> record = releaseRecords.get(generationId);
        return record == null ? null : record.getState();
    }

    synchronized DeckReleaseReason releaseReason(long generationId) {
        PageSurfaceGenerationReleaseRecord<T> record = releaseRecords.get(generationId);
        return record == null ? null : record.getReason();
    }

    synchronized boolean isReleaseInFlight(long generationId) {
        return releaseRecords.containsKey(generationId);
    }

    synchronized int releaseInFlightCount(
            long activeGenerationId,
            long pendingGenerationId) {
        int count = 0;
        for (PageSurfaceGenerationReleaseRecord<T> record : releaseRecords.values()) {
            long generationId = record.getGenerationId();
            if (generationId != activeGenerationId
                    && generationId != pendingGenerationId
                    && record.getState()
                            != PageSurfaceGenerationReleaseRecord.State.COMPLETED) {
                count += 1;
            }
        }
        return count;
    }

    synchronized long sessionId() {
        return sessionId;
    }

    synchronized void close() {
        closed = true;
    }

    private PageSurfaceDeckReleaseResult duplicateResult(
            PageSurfaceGenerationReleaseRecord<T> record) {
        switch (record.duplicateDisposition()) {
            case ALREADY_ACCEPTED:
                return PageSurfaceDeckReleaseResult.alreadyAccepted();
            case STATE_CONFLICT:
            case COMPLETED:
                return PageSurfaceDeckReleaseResult.rejected(
                        PageSurfaceDeckReleaseResult.RejectionReason.STATE_CONFLICT);
            default:
                throw new AssertionError("Unhandled duplicate release disposition");
        }
    }

    private List<PageSurfaceGenerationReleaseRecord<T>> claimReleases(
            List<PageDeckCoordinator.Release<T>> releases) {
        List<PageSurfaceGenerationReleaseRecord<T>> claimed =
                new ArrayList<>(releases.size());
        try {
            for (PageDeckCoordinator.Release<T> release : releases) {
                claimed.add(claimRelease(release));
            }
            return claimed;
        } catch (RuntimeException | Error claimFailure) {
            rollbackClaims(claimed);
            throw claimFailure;
        }
    }

    private PageSurfaceGenerationReleaseRecord<T> claimRelease(
            PageDeckCoordinator.Release<T> release) {
        Objects.requireNonNull(release, "release");
        long generationId = release.getDeck().getGenerationId();
        if (releaseRecords.containsKey(generationId)
                || !leaseRegistry.contains(generationId)) {
            throw new IllegalStateException(
                    "Release generation does not have one unclaimed lease");
        }
        PageSurfaceGenerationReleaseRecord<T> record =
                PageSurfaceGenerationReleaseRecord.requested(sessionId, release);
        releaseRecords.put(generationId, record);
        return record;
    }

    private void acceptClaims(
            List<PageSurfaceGenerationReleaseRecord<T>> claimed) {
        for (PageSurfaceGenerationReleaseRecord<T> record : claimed) {
            acceptClaim(record);
        }
    }

    private void acceptClaim(PageSurfaceGenerationReleaseRecord<T> record) {
        if (releaseRecords.get(record.getGenerationId()) == record
                && record.queueAccepted()) {
            return;
        }
        throw new IllegalStateException(
                "Release claim was not retained through queue acceptance");
    }

    private void rollbackRejectedQueue(
            PageDeckCoordinator.Release<T> release,
            PageSurfaceGenerationReleaseRecord<T> record,
            Throwable queueFailure) {
        rollbackClaim(record);
        final boolean coordinatorRolledBack;
        try {
            coordinatorRolledBack = coordinator.rollbackRelease(release);
        } catch (Throwable failure) {
            IllegalStateException rollbackFailure = new IllegalStateException(
                    "Rejected renderer release queue could not restore coordinator ownership",
                    queueFailure);
            if (failure != queueFailure) {
                rollbackFailure.addSuppressed(failure);
            }
            throw rollbackFailure;
        }
        if (!coordinatorRolledBack) {
            throw new IllegalStateException(
                    "Rejected renderer release queue could not restore surface ownership",
                    queueFailure);
        }
    }

    private void rollbackClaims(
            List<PageSurfaceGenerationReleaseRecord<T>> claimed) {
        for (int index = claimed.size() - 1; index >= 0; index -= 1) {
            rollbackClaim(claimed.get(index));
        }
    }

    private void rollbackClaim(PageSurfaceGenerationReleaseRecord<T> record) {
        if (record.getState() != PageSurfaceGenerationReleaseRecord.State.REQUESTED
                || !releaseRecords.remove(record.getGenerationId(), record)) {
            throw new IllegalStateException(
                    "Release claim was not retained for queue rollback");
        }
    }
}
