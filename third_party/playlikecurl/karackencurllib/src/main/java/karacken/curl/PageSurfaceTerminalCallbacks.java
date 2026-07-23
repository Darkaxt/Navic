package karacken.curl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class PageSurfaceTerminalCallbacks<T> {
    enum AddResult {
        ACCEPTED,
        DELIVERED_TERMINAL,
        CALLBACK_CAPACITY
    }

    interface Callback<T> {
        void onResult(T result);
    }

    interface FailureHandler {
        void onFailure(Object callback, Throwable failure);
    }

    private final int capacity;
    private final FailureHandler failureHandler;
    private final List<Callback<T>> callbacks = new ArrayList<>();
    private T result;

    PageSurfaceTerminalCallbacks(
            int capacity,
            FailureHandler failureHandler) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.failureHandler = Objects.requireNonNull(
                failureHandler,
                "failureHandler");
    }

    AddResult add(Callback<T> callback) {
        Objects.requireNonNull(callback, "callback");
        if (result != null) {
            notifyCallback(callback, result);
            return AddResult.DELIVERED_TERMINAL;
        }
        if (callbacks.size() == capacity) {
            return AddResult.CALLBACK_CAPACITY;
        }
        callbacks.add(callback);
        return AddResult.ACCEPTED;
    }

    boolean complete(T terminalResult) {
        Objects.requireNonNull(terminalResult, "terminalResult");
        if (result != null) {
            return false;
        }
        result = terminalResult;
        List<Callback<T>> pending = new ArrayList<>(callbacks);
        callbacks.clear();
        for (Callback<T> callback : pending) {
            notifyCallback(callback, terminalResult);
        }
        return true;
    }

    T result() {
        return result;
    }

    int pendingCount() {
        return callbacks.size();
    }

    int capacity() {
        return capacity;
    }

    private void notifyCallback(Callback<T> callback, T terminalResult) {
        try {
            callback.onResult(terminalResult);
        } catch (Throwable failure) {
            try {
                failureHandler.onFailure(callback, failure);
            } catch (Throwable ignored) {
                // Terminal publication cannot depend on diagnostics.
            }
        }
    }
}
