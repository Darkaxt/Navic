package karacken.curl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PageSurfaceOwnershipRequestRegistry {
    static final class Registration {
        enum Status {
            ACCEPTED,
            CALLBACK_CAPACITY
        }

        private final Status status;
        private final long token;

        private Registration(Status status, long token) {
            this.status = status;
            this.token = token;
        }

        static Registration accepted(long token) {
            return new Registration(Status.ACCEPTED, token);
        }

        static Registration rejected() {
            return new Registration(Status.CALLBACK_CAPACITY, 0L);
        }

        Status status() {
            return status;
        }

        long token() {
            if (status != Status.ACCEPTED) {
                throw new IllegalStateException(
                        "Rejected ownership registration has no token");
            }
            return token;
        }
    }

    static final class Attempt {
        private final long token;
        private final long ordinal;
        private final long ownershipEpoch;

        Attempt(long token, long ordinal, long ownershipEpoch) {
            this.token = token;
            this.ordinal = ordinal;
            this.ownershipEpoch = ownershipEpoch;
        }

        long token() {
            return token;
        }

        long ordinal() {
            return ordinal;
        }

        long ownershipEpoch() {
            return ownershipEpoch;
        }
    }

    static final class Completion {
        enum Status {
            MISSING,
            RETRY,
            COMPLETE
        }

        private final Status status;
        private final PageSurfaceOwnershipResult.Callback callback;

        private Completion(
                Status status,
                PageSurfaceOwnershipResult.Callback callback) {
            this.status = status;
            this.callback = callback;
        }

        static Completion missing() {
            return new Completion(Status.MISSING, null);
        }

        static Completion retry() {
            return new Completion(Status.RETRY, null);
        }

        static Completion complete(
                PageSurfaceOwnershipResult.Callback callback) {
            return new Completion(
                    Status.COMPLETE,
                    Objects.requireNonNull(callback, "callback"));
        }

        Status status() {
            return status;
        }

        PageSurfaceOwnershipResult.Callback callback() {
            return callback;
        }
    }

    private static final class Request {
        final PageSurfaceOwnershipResult.Callback callback;
        long attemptOrdinal;

        Request(PageSurfaceOwnershipResult.Callback callback) {
            this.callback = callback;
        }
    }

    private final int capacity;
    private long nextToken = 1L;
    private final Map<Long, Request> requests = new LinkedHashMap<>();
    private Runnable capacityAvailable = () -> {};

    PageSurfaceOwnershipRequestRegistry(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    synchronized void setCapacityAvailableListener(Runnable listener) {
        capacityAvailable = Objects.requireNonNull(listener, "listener");
    }

    synchronized void clearCapacityAvailableListener(Runnable listener) {
        if (capacityAvailable == listener) {
            capacityAvailable = () -> {};
        }
    }

    synchronized Registration register(
            PageSurfaceOwnershipResult.Callback callback) {
        Objects.requireNonNull(callback, "callback");
        if (requests.size() == capacity) {
            return Registration.rejected();
        }
        long token = nextToken++;
        requests.put(token, new Request(callback));
        return Registration.accepted(token);
    }

    synchronized Attempt beginAttempt(long token, long ownershipEpoch) {
        Request request = requests.get(token);
        if (request == null) {
            return null;
        }
        request.attemptOrdinal += 1L;
        return new Attempt(
                token,
                request.attemptOrdinal,
                ownershipEpoch);
    }

    Completion finishAttempt(Attempt attempt, long currentOwnershipEpoch) {
        Objects.requireNonNull(attempt, "attempt");
        final Completion completion;
        final Runnable capacityEdge;
        synchronized (this) {
            Request request = requests.get(attempt.token());
            if (request == null
                    || request.attemptOrdinal != attempt.ordinal()) {
                return Completion.missing();
            }
            if (attempt.ownershipEpoch() != currentOwnershipEpoch) {
                return Completion.retry();
            }
            boolean wasFull = requests.size() == capacity;
            requests.remove(attempt.token());
            completion = Completion.complete(request.callback);
            capacityEdge = wasFull ? capacityAvailable : null;
        }
        if (capacityEdge != null) {
            capacityEdge.run();
        }
        return completion;
    }

    PageSurfaceOwnershipResult.Callback take(long token) {
        final Request request;
        final Runnable capacityEdge;
        synchronized (this) {
            boolean wasFull = requests.size() == capacity;
            request = requests.remove(token);
            capacityEdge = wasFull && request != null
                    ? capacityAvailable
                    : null;
        }
        if (capacityEdge != null) {
            capacityEdge.run();
        }
        return request == null ? null : request.callback;
    }

    List<PageSurfaceOwnershipResult.Callback> drain() {
        final List<PageSurfaceOwnershipResult.Callback> drained =
                new ArrayList<>();
        final Runnable capacityEdge;
        synchronized (this) {
            boolean wasFull = requests.size() == capacity;
            for (Request request : requests.values()) {
                drained.add(request.callback);
            }
            requests.clear();
            capacityEdge = wasFull ? capacityAvailable : null;
        }
        if (capacityEdge != null) {
            capacityEdge.run();
        }
        return drained;
    }

    synchronized boolean contains(long token) {
        return requests.containsKey(token);
    }

    synchronized int size() {
        return requests.size();
    }

    int capacity() {
        return capacity;
    }
}
