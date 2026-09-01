package karacken.curl;

import java.util.Objects;

/** Reports whether an explicit renderer-deck release command acquired surface ownership. */
public final class PageSurfaceDeckReleaseResult {
    public enum Status {
        ACCEPTED,
        ALREADY_ACCEPTED,
        REJECTED
    }

    public enum RejectionReason {
        NOT_RETAINED,
        QUEUE_REJECTED,
        DISPOSED,
        STATE_CONFLICT
    }

    private static final PageSurfaceDeckReleaseResult ACCEPTED =
            new PageSurfaceDeckReleaseResult(Status.ACCEPTED, null);
    private static final PageSurfaceDeckReleaseResult ALREADY_ACCEPTED =
            new PageSurfaceDeckReleaseResult(Status.ALREADY_ACCEPTED, null);

    private final Status status;
    private final RejectionReason rejectionReason;

    private PageSurfaceDeckReleaseResult(
            Status status,
            RejectionReason rejectionReason) {
        this.status = Objects.requireNonNull(status, "status");
        this.rejectionReason = rejectionReason;
        if ((status == Status.REJECTED) != (rejectionReason != null)) {
            throw new IllegalArgumentException(
                    "Exactly rejected releases require a rejection reason");
        }
    }

    public static PageSurfaceDeckReleaseResult accepted() {
        return ACCEPTED;
    }

    public static PageSurfaceDeckReleaseResult alreadyAccepted() {
        return ALREADY_ACCEPTED;
    }

    public static PageSurfaceDeckReleaseResult rejected(RejectionReason reason) {
        return new PageSurfaceDeckReleaseResult(
                Status.REJECTED,
                Objects.requireNonNull(reason, "reason"));
    }

    public Status getStatus() {
        return status;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED || status == Status.ALREADY_ACCEPTED;
    }
}
