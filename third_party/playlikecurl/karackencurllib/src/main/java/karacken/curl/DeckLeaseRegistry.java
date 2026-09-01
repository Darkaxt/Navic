package karacken.curl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Keeps each accepted bitmap lease bound to the listener that acquired it. */
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

    synchronized PageSurfaceListener listenerFor(
            long generationId,
            PageSurfaceListener fallback) {
        PageSurfaceListener owner = owners.get(generationId);
        return owner == null ? fallback : owner;
    }

    synchronized PageSurfaceListener ownerFor(long generationId) {
        return owners.get(generationId);
    }

    Lease release(long generationId) {
        return release(generationId, null);
    }

    Lease release(long generationId, DeckReleaseReason reason) {
        final Lease lease;
        synchronized (this) {
            PageSurfaceListener listener = owners.remove(generationId);
            if (listener == null) {
                return null;
            }
            lease = new Lease(generationId, listener, reason);
        }
        ownershipMutated.run();
        return lease;
    }

    List<Lease> releaseAll(DeckReleaseReason reason) {
        Objects.requireNonNull(reason, "reason");
        final List<Lease> result;
        synchronized (this) {
            if (owners.isEmpty()) {
                return Collections.emptyList();
            }
            List<Lease> leases = new ArrayList<>(owners.size());
            for (Map.Entry<Long, PageSurfaceListener> entry : owners.entrySet()) {
                leases.add(new Lease(entry.getKey(), entry.getValue(), reason));
            }
            owners.clear();
            result = Collections.unmodifiableList(leases);
        }
        ownershipMutated.run();
        return result;
    }

    synchronized boolean hasOutstandingLeases() {
        return !owners.isEmpty();
    }
}
