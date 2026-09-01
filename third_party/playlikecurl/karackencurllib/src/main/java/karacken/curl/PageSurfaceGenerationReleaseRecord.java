package karacken.curl;

import java.util.Objects;

/** Exact release command and lifecycle for one deck generation in one surface session. */
final class PageSurfaceGenerationReleaseRecord<T> {
    enum State {
        REQUESTED,
        QUEUE_ACCEPTED,
        RENDERER_DETACHED,
        TERMINALLY_ABANDONED,
        COMPLETED
    }

    enum DuplicateDisposition {
        STATE_CONFLICT,
        ALREADY_ACCEPTED,
        COMPLETED
    }

    private final long sessionId;
    private final PageDeckCoordinator.Release<T> release;
    private State state = State.REQUESTED;

    private PageSurfaceGenerationReleaseRecord(
            long sessionId,
            PageDeckCoordinator.Release<T> release) {
        this.sessionId = sessionId;
        this.release = Objects.requireNonNull(release, "release");
    }

    static <T> PageSurfaceGenerationReleaseRecord<T> requested(
            long sessionId,
            PageDeckCoordinator.Release<T> release) {
        return new PageSurfaceGenerationReleaseRecord<>(sessionId, release);
    }

    synchronized boolean matches(long expectedSessionId, long generationId) {
        return sessionId == expectedSessionId && getGenerationId() == generationId;
    }

    synchronized boolean queueAccepted() {
        if (state != State.REQUESTED) {
            return false;
        }
        state = State.QUEUE_ACCEPTED;
        return true;
    }

    synchronized boolean rendererDetached() {
        if (state != State.QUEUE_ACCEPTED) {
            return false;
        }
        state = State.RENDERER_DETACHED;
        return true;
    }

    synchronized boolean terminallyAbandon() {
        if (state != State.QUEUE_ACCEPTED) {
            return false;
        }
        state = State.TERMINALLY_ABANDONED;
        return true;
    }

    synchronized boolean complete() {
        if (state != State.RENDERER_DETACHED
                && state != State.TERMINALLY_ABANDONED) {
            return false;
        }
        state = State.COMPLETED;
        return true;
    }

    synchronized DuplicateDisposition duplicateDisposition() {
        switch (state) {
            case REQUESTED:
                return DuplicateDisposition.STATE_CONFLICT;
            case QUEUE_ACCEPTED:
            case RENDERER_DETACHED:
            case TERMINALLY_ABANDONED:
                return DuplicateDisposition.ALREADY_ACCEPTED;
            case COMPLETED:
                return DuplicateDisposition.COMPLETED;
            default:
                throw new AssertionError("Unhandled release state " + state);
        }
    }

    synchronized State getState() {
        return state;
    }

    long getSessionId() {
        return sessionId;
    }

    long getGenerationId() {
        return release.getDeck().getGenerationId();
    }

    DeckReleaseReason getReason() {
        return release.getReason();
    }

    PageDeckCoordinator.Release<T> getRelease() {
        return release;
    }
}
