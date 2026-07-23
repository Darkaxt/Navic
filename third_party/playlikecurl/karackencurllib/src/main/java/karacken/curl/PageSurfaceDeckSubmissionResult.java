package karacken.curl;

import java.util.Objects;

public final class PageSurfaceDeckSubmissionResult {
    public enum Status {
        ACCEPTED,
        UNCHANGED,
        REJECTED
    }

    private final Status status;
    private final DeckRejectionReason rejectionReason;

    private PageSurfaceDeckSubmissionResult(
            Status status,
            DeckRejectionReason rejectionReason) {
        this.status = Objects.requireNonNull(status, "status");
        if ((status == Status.REJECTED) != (rejectionReason != null)) {
            throw new IllegalArgumentException(
                    "Only rejected submissions have a rejection reason");
        }
        this.rejectionReason = rejectionReason;
    }

    public static PageSurfaceDeckSubmissionResult accepted() {
        return new PageSurfaceDeckSubmissionResult(Status.ACCEPTED, null);
    }

    public static PageSurfaceDeckSubmissionResult unchanged() {
        return new PageSurfaceDeckSubmissionResult(Status.UNCHANGED, null);
    }

    public static PageSurfaceDeckSubmissionResult rejected(
            DeckRejectionReason reason) {
        return new PageSurfaceDeckSubmissionResult(
                Status.REJECTED,
                Objects.requireNonNull(reason, "reason"));
    }

    public Status getStatus() {
        return status;
    }

    public DeckRejectionReason getRejectionReason() {
        return rejectionReason;
    }
}
