package karacken.curl;

import java.util.Objects;

final class PageSurfaceOwnershipRetryEdge {
    interface Host {
        boolean isOwnershipAvailable();

        Runnable ownershipRetryListener();

        boolean post(Runnable action);
    }

    private final Host host;
    private boolean deliveryPosted;

    PageSurfaceOwnershipRetryEdge(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    void schedule() {
        if (deliveryPosted
                || !host.isOwnershipAvailable()
                || host.ownershipRetryListener() == null) {
            return;
        }
        deliveryPosted = true;
        if (!host.post(this::deliver)) {
            deliveryPosted = false;
        }
    }

    private void deliver() {
        deliveryPosted = false;
        Runnable listener = host.ownershipRetryListener();
        if (host.isOwnershipAvailable() && listener != null) {
            listener.run();
        }
    }
}
