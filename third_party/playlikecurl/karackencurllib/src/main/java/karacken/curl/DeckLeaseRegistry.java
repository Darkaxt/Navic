package karacken.curl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps each accepted bitmap lease bound to the listener that acquired it.
 *
 * <p>The registry also makes terminal release idempotent when renderer and lifecycle cleanup
 * converge on the same generation.
 */
final class DeckLeaseRegistry {
    static final int MAX_DECK_LEASES = 4;

    static final class Lease {
        private final long generationId;
        private final PageSurfaceListener listener;
        private final DeckReleaseReason releaseReason;

        private Lease(
                long generationId,
                PageSurfaceListener listener,
                DeckReleaseReason releaseReason) {
            this.generationId = generationId;
            this.listener = listener;
            this.releaseReason = releaseReason;
        }

        long getGenerationId() {
            return generationId;
        }

        PageSurfaceListener getListener() {
            return listener;
        }

        DeckReleaseReason getReleaseReason() {
            return releaseReason;
        }
    }

    private final int capacity;
    private final Runnable ownershipMutated;
    private final Map<Long, PageSurfaceListener> owners = new LinkedHashMap<>();
    private final Map<Long, DeckReleaseReason> requestedReleaseReasons =
            new LinkedHashMap<>();

    DeckLeaseRegistry() {
        this(MAX_DECK_LEASES, () -> {});
    }

    DeckLeaseRegistry(int capacity) {
        this(capacity, () -> {});
    }

    DeckLeaseRegistry(Runnable ownershipMutated) {
        this(MAX_DECK_LEASES, ownershipMutated);
    }

    DeckLeaseRegistry(int capacity, Runnable ownershipMutated) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.ownershipMutated =
                Objects.requireNonNull(ownershipMutated, "ownershipMutated");
    }

    boolean acquire(long generationId, PageSurfaceListener listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (this) {
            if (owners.containsKey(generationId)
                    || owners.size() >= capacity) {
                return false;
            }
            owners.put(generationId, listener);
        }
        ownershipMutated.run();
        return true;
    }

    synchronized boolean hasCapacity() {
        return owners.size() < capacity;
    }

    synchronized int capacity() {
        return capacity;
    }

    synchronized int size() {
        return owners.size();
    }

    synchronized boolean contains(long generationId) {
        return owners.containsKey(generationId);
    }

    synchronized boolean isReleaseRequested(long generationId) {
        return requestedReleaseReasons.containsKey(generationId);
    }

    synchronized int releaseInFlightCount(
            long activeGenerationId,
            long pendingGenerationId) {
        int count = 0;
        for (long generationId : requestedReleaseReasons.keySet()) {
            if (generationId != activeGenerationId
                    && generationId != pendingGenerationId) {
                count += 1;
            }
        }
        return count;
    }

    synchronized PageSurfaceListener listenerFor(
            long generationId,
            PageSurfaceListener fallback) {
        PageSurfaceListener owner = owners.get(generationId);
        return owner == null ? fallback : owner;
    }

    synchronized PageSurfaceListener ownerFor(long generationId) {
        return owners.get(generationId);
    }

    void markReleaseRequested(
            long generationId,
            DeckReleaseReason reason) {
        Objects.requireNonNull(reason, "reason");
        synchronized (this) {
            if (!owners.containsKey(generationId)
                    || requestedReleaseReasons.containsKey(generationId)) {
                return;
            }
            requestedReleaseReasons.put(generationId, reason);
        }
        ownershipMutated.run();
    }

    boolean rollbackReleaseRequested(
            long generationId,
            DeckReleaseReason reason) {
        Objects.requireNonNull(reason, "reason");
        synchronized (this) {
            if (requestedReleaseReasons.get(generationId) != reason) {
                return false;
            }
            requestedReleaseReasons.remove(generationId);
        }
        ownershipMutated.run();
        return true;
    }

    Lease release(long generationId) {
        final Lease lease;
        synchronized (this) {
            PageSurfaceListener listener = owners.remove(generationId);
            DeckReleaseReason reason = requestedReleaseReasons.remove(generationId);
            if (listener == null) {
                return null;
            }
            lease = new Lease(generationId, listener, reason);
        }
        ownershipMutated.run();
        return lease;
    }

    List<Lease> releaseAll(DeckReleaseReason fallbackReason) {
        Objects.requireNonNull(fallbackReason, "fallbackReason");
        final List<Lease> result;
        synchronized (this) {
            if (owners.isEmpty()) {
                return Collections.emptyList();
            }
            List<Lease> leases = new ArrayList<>(owners.size());
            for (Map.Entry<Long, PageSurfaceListener> entry : owners.entrySet()) {
                DeckReleaseReason reason = requestedReleaseReasons.getOrDefault(
                        entry.getKey(),
                        fallbackReason);
                leases.add(new Lease(entry.getKey(), entry.getValue(), reason));
            }
            owners.clear();
            requestedReleaseReasons.clear();
            result = Collections.unmodifiableList(new ArrayList<>(leases));
        }
        ownershipMutated.run();
        return result;
    }

    synchronized boolean hasOutstandingLeases() {
        return !owners.isEmpty();
    }
}
