package karacken.curl;

import java.util.Objects;

/** Typed result for an asynchronous surface ownership request. */
public final class PageSurfaceOwnershipResult {
    public enum Status {
        AVAILABLE,
        SURFACE_UNAVAILABLE,
        QUEUE_REJECTED,
        CALLBACK_CAPACITY
    }

    public interface Callback {
        void onResult(PageSurfaceOwnershipResult result);
    }

    private final Status status;
    private final PageSurfaceOwnershipSnapshot snapshot;

    private PageSurfaceOwnershipResult(
            Status status,
            PageSurfaceOwnershipSnapshot snapshot) {
        this.status = Objects.requireNonNull(status, "status");
        this.snapshot = snapshot;
    }

    public static PageSurfaceOwnershipResult available(
            PageSurfaceOwnershipSnapshot snapshot) {
        return new PageSurfaceOwnershipResult(
                Status.AVAILABLE,
                Objects.requireNonNull(snapshot, "snapshot"));
    }

    public static PageSurfaceOwnershipResult unavailable(Status status) {
        if (status == Status.AVAILABLE) {
            throw new IllegalArgumentException(
                    "AVAILABLE requires a snapshot");
        }
        return new PageSurfaceOwnershipResult(status, null);
    }

    public Status getStatus() {
        return status;
    }

    public PageSurfaceOwnershipSnapshot getSnapshot() {
        return snapshot;
    }
}
