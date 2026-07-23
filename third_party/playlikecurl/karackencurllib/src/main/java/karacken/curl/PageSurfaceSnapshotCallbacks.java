package karacken.curl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class PageSurfaceSnapshotCallbacks<T> {
    interface Callback<T> {
        void onSnapshot(T snapshot);
    }

    interface FailureHandler {
        void onFailure(Object callback, Throwable failure);
    }

    private final int capacity;
    private final FailureHandler failureHandler;
    private final List<Callback<T>> callbacks = new ArrayList<>();

    PageSurfaceSnapshotCallbacks(
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

    boolean add(Callback<T> callback) {
        Objects.requireNonNull(callback, "callback");
        if (callbacks.size() == capacity) {
            return false;
        }
        callbacks.add(callback);
        return true;
    }

    int pendingCount() {
        return callbacks.size();
    }

    void completeBatch(T snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<Callback<T>> batch = new ArrayList<>(callbacks);
        callbacks.clear();
        for (Callback<T> callback : batch) {
            try {
                callback.onSnapshot(snapshot);
            } catch (Throwable failure) {
                failureHandler.onFailure(callback, failure);
            }
        }
    }
}
