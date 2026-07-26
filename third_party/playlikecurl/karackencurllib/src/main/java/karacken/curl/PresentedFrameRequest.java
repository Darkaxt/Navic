package karacken.curl;

import java.util.Objects;

/** Owns one callback waiting for a complete renderer frame. */
final class PresentedFrameRequest {
    static final long NO_REQUEST_ID = 0L;

    private long lastRequestId;
    private long requestedId = NO_REQUEST_ID;
    private long armedId = NO_REQUEST_ID;
    private long renderedId = NO_REQUEST_ID;
    private Runnable callback;

    synchronized long request(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        if (requestedId != NO_REQUEST_ID) {
            throw new IllegalStateException("A presented-frame request is already pending");
        }
        lastRequestId = Math.incrementExact(lastRequestId);
        requestedId = lastRequestId;
        armedId = NO_REQUEST_ID;
        renderedId = NO_REQUEST_ID;
        this.callback = callback;
        return requestedId;
    }

    synchronized boolean arm(long requestId) {
        if (requestId == NO_REQUEST_ID
                || requestId != requestedId
                || armedId != NO_REQUEST_ID
                || renderedId != NO_REQUEST_ID) {
            return false;
        }
        armedId = requestId;
        return true;
    }

    synchronized long markRendered() {
        if (armedId == NO_REQUEST_ID
                || armedId != requestedId
                || renderedId != NO_REQUEST_ID) {
            return NO_REQUEST_ID;
        }
        renderedId = armedId;
        armedId = NO_REQUEST_ID;
        return renderedId;
    }

    synchronized Runnable complete(long requestId) {
        if (requestId == NO_REQUEST_ID
                || requestId != requestedId
                || requestId != renderedId) {
            return null;
        }
        Runnable completed = callback;
        clear();
        return completed;
    }

    synchronized boolean cancel(long requestId) {
        if (requestId == NO_REQUEST_ID || requestId != requestedId) {
            return false;
        }
        clear();
        return true;
    }

    synchronized void cancelAll() {
        clear();
    }

    synchronized int pendingCount() {
        return requestedId == NO_REQUEST_ID ? 0 : 1;
    }

    private void clear() {
        requestedId = NO_REQUEST_ID;
        armedId = NO_REQUEST_ID;
        renderedId = NO_REQUEST_ID;
        callback = null;
    }
}
