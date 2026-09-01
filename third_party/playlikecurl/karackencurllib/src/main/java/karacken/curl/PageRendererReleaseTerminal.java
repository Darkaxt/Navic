package karacken.curl;

import java.util.Objects;

final class PageRendererReleaseTerminal {
    private PageRendererReleaseTerminal() {}

    static Throwable execute(
            Runnable terminalCallback,
            Runnable... cleanupActions) {
        Objects.requireNonNull(terminalCallback, "terminalCallback");
        Objects.requireNonNull(cleanupActions, "cleanupActions");
        Throwable failure = null;
        for (Runnable cleanupAction : cleanupActions) {
            Objects.requireNonNull(cleanupAction, "cleanupAction");
            try {
                cleanupAction.run();
            } catch (Throwable cleanupFailure) {
                failure = capture(failure, cleanupFailure);
            }
        }
        try {
            terminalCallback.run();
        } catch (Throwable terminalFailure) {
            failure = capture(failure, terminalFailure);
        }
        return failure;
    }

    private static Throwable capture(
            Throwable retained,
            Throwable failure) {
        if (retained == null) {
            return failure;
        }
        if (failure != retained) {
            retained.addSuppressed(failure);
        }
        return retained;
    }
}
