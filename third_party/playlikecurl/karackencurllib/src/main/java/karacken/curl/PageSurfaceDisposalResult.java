package karacken.curl;

import java.util.Objects;

public final class PageSurfaceDisposalResult {
    public interface Callback {
        void onComplete(PageSurfaceDisposalResult result);
    }

    private final PageSurfaceOwnershipSnapshot ownership;
    private final PageSurfaceDisposalStage failureStage;
    private final Throwable failure;
    private final int suppressedFailureCount;
    private final boolean detachedFallback;

    public PageSurfaceDisposalResult(
            PageSurfaceOwnershipSnapshot ownership,
            PageSurfaceDisposalStage failureStage,
            Throwable failure,
            int suppressedFailureCount,
            boolean detachedFallback) {
        this.ownership = Objects.requireNonNull(ownership, "ownership");
        this.failureStage = Objects.requireNonNull(failureStage, "failureStage");
        this.failure = failure;
        this.suppressedFailureCount = suppressedFailureCount;
        this.detachedFallback = detachedFallback;
    }

    public PageSurfaceOwnershipSnapshot getOwnership() {
        return ownership;
    }

    public PageSurfaceDisposalStage getFailureStage() {
        return failureStage;
    }

    public Throwable getFailure() {
        return failure;
    }

    public int getSuppressedFailureCount() {
        return suppressedFailureCount;
    }

    public boolean usedDetachedFallback() {
        return detachedFallback;
    }

    public boolean isSuccessful() {
        return failureStage == PageSurfaceDisposalStage.NONE && failure == null;
    }
}
