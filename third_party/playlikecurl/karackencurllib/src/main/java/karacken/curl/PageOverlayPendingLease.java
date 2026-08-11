package karacken.curl;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** One bounded value that either transfers to the GL thread or is disposed by fallback. */
final class PageOverlayPendingLease<T> {
    private final AtomicReference<T> value;
    private final Consumer<T> disposer;

    PageOverlayPendingLease(T value, Consumer<T> disposer) {
        this.value = new AtomicReference<>(Objects.requireNonNull(value, "value"));
        this.disposer = Objects.requireNonNull(disposer, "disposer");
    }

    T claim() {
        return value.getAndSet(null);
    }

    T withdraw() {
        return value.getAndSet(null);
    }

    void abandon() {
        T retained = value.getAndSet(null);
        if (retained != null) {
            disposer.accept(retained);
        }
    }
}
