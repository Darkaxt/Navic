package karacken.curl;

import java.util.Objects;

final class PageSurfaceRequiredTerminalCallback<T> {
    interface Callback<T> {
        void onResult(T result);
    }

    interface FailureHandler {
        void onFailure(Object callback, Throwable failure);
    }

    private final FailureHandler failureHandler;
    private Callback<T> callback;
    private T result;
    private boolean registered;

    PageSurfaceRequiredTerminalCallback(FailureHandler failureHandler) {
        this.failureHandler = Objects.requireNonNull(
                failureHandler,
                "failureHandler");
    }

    void register(Callback<T> requiredCallback) {
        Objects.requireNonNull(requiredCallback, "requiredCallback");
        if (registered) {
            throw new IllegalStateException(
                    "Required terminal owner is already registered");
        }
        registered = true;
        if (result != null) {
            notifyCallback(requiredCallback, result);
            return;
        }
        callback = requiredCallback;
    }

    boolean complete(T terminalResult) {
        Objects.requireNonNull(terminalResult, "terminalResult");
        if (result != null) {
            return false;
        }
        result = terminalResult;
        Callback<T> pending = callback;
        callback = null;
        if (pending != null) {
            notifyCallback(pending, terminalResult);
        }
        return true;
    }

    int pendingCount() {
        return callback == null ? 0 : 1;
    }

    private void notifyCallback(Callback<T> target, T value) {
        try {
            target.onResult(value);
        } catch (Throwable failure) {
            try {
                failureHandler.onFailure(target, failure);
            } catch (Throwable ignored) {
                // Failure reporting cannot change terminal ownership.
            }
        }
    }
}
