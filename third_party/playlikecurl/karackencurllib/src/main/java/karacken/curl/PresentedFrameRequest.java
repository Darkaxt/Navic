package karacken.curl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Multiplexes the next complete renderer frame across bounded one-shot consumers. */
final class PresentedFrameRequest {
    static final long NO_REQUEST_ID = 0L;
    static final int MAX_PENDING_CALLBACKS = 3;

    private long lastRequestId;
    private long lastCompletionId;
    private final Map<Long, Runnable> requestedCallbacks = new LinkedHashMap<>();
    private final Set<Long> armedRequestIds = new LinkedHashSet<>();
    private final Map<Long, Map<Long, Runnable>> completedCallbacks =
            new LinkedHashMap<>();

    synchronized long request(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        if (pendingCountLocked() >= MAX_PENDING_CALLBACKS) {
            throw new IllegalStateException(
                    "Presented-frame callback capacity is exhausted");
        }
        lastRequestId = Math.incrementExact(lastRequestId);
        requestedCallbacks.put(lastRequestId, callback);
        return lastRequestId;
    }

    synchronized boolean arm(long requestId) {
        if (requestId == NO_REQUEST_ID
                || !requestedCallbacks.containsKey(requestId)
                || !armedRequestIds.add(requestId)) {
            return false;
        }
        return armedRequestIds.size() == 1;
    }

    synchronized long markRendered() {
        if (armedRequestIds.isEmpty()) {
            return NO_REQUEST_ID;
        }
        Map<Long, Runnable> renderedCallbacks = new LinkedHashMap<>();
        for (Map.Entry<Long, Runnable> entry : requestedCallbacks.entrySet()) {
            if (armedRequestIds.contains(entry.getKey())) {
                renderedCallbacks.put(entry.getKey(), entry.getValue());
            }
        }
        for (Long requestId : renderedCallbacks.keySet()) {
            requestedCallbacks.remove(requestId);
        }
        armedRequestIds.clear();
        if (renderedCallbacks.isEmpty()) {
            return NO_REQUEST_ID;
        }
        lastCompletionId = Math.incrementExact(lastCompletionId);
        completedCallbacks.put(lastCompletionId, renderedCallbacks);
        return lastCompletionId;
    }

    synchronized Runnable complete(long completionId) {
        if (completionId == NO_REQUEST_ID) {
            return null;
        }
        Map<Long, Runnable> completed = completedCallbacks.remove(completionId);
        if (completed == null) {
            return null;
        }
        List<Runnable> callbacks = new ArrayList<>(completed.values());
        return () -> runIsolated(callbacks);
    }

    synchronized boolean cancel(long requestId) {
        if (requestId == NO_REQUEST_ID) {
            return false;
        }
        Runnable requested = requestedCallbacks.remove(requestId);
        if (requested != null) {
            armedRequestIds.remove(requestId);
            return true;
        }
        Iterator<Map.Entry<Long, Map<Long, Runnable>>> batches =
                completedCallbacks.entrySet().iterator();
        while (batches.hasNext()) {
            Map<Long, Runnable> callbacks = batches.next().getValue();
            if (callbacks.remove(requestId) != null) {
                if (callbacks.isEmpty()) {
                    batches.remove();
                }
                return true;
            }
        }
        return false;
    }

    synchronized void cancelAll() {
        requestedCallbacks.clear();
        armedRequestIds.clear();
        completedCallbacks.clear();
    }

    synchronized int pendingCount() {
        return pendingCountLocked();
    }

    private int pendingCountLocked() {
        int count = requestedCallbacks.size();
        for (Map<Long, Runnable> callbacks : completedCallbacks.values()) {
            count += callbacks.size();
        }
        return count;
    }

    private static void runIsolated(List<Runnable> callbacks) {
        Throwable firstFailure = null;
        for (Runnable callback : callbacks) {
            try {
                callback.run();
            } catch (Throwable callbackFailure) {
                if (firstFailure == null) {
                    firstFailure = callbackFailure;
                } else if (callbackFailure != firstFailure) {
                    firstFailure.addSuppressed(callbackFailure);
                }
            }
        }
        if (firstFailure instanceof RuntimeException) {
            throw (RuntimeException) firstFailure;
        }
        if (firstFailure instanceof Error) {
            throw (Error) firstFailure;
        }
        if (firstFailure != null) {
            throw new IllegalStateException(
                    "Presented-frame callback failed",
                    firstFailure);
        }
    }
}
