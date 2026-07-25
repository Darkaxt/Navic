package karacken.curl;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class PageSurfaceTerminalDisposalGate {
    interface Host {
        boolean isWindowAttached();

        boolean isHolderSurfaceAvailable();

        boolean isLogicallyAttached();

        void resumeForTerminalWork();

        void queueTerminalWork(Runnable action);
    }

    interface QueueEntryWatchdog {
        Cancellation arm(Runnable timeoutAction);
    }

    interface Cancellation {
        void cancel();
    }

    enum FailureKind {
        SURFACE_UNAVAILABLE,
        SURFACE_LOST,
        RESUME,
        QUEUE,
        QUEUE_ENTRY_TIMEOUT
    }

    static boolean isExpectedDetachedFallback(
            FailureKind kind,
            boolean holderSurfaceAvailable,
            boolean logicallyAttached) {
        return !holderSurfaceAvailable
                && !logicallyAttached
                && (kind == FailureKind.SURFACE_UNAVAILABLE
                        || kind == FailureKind.SURFACE_LOST);
    }

    interface Fallback {
        void run(FailureKind kind, Throwable failure);
    }

    private enum Owner {
        NONE,
        GL_EXECUTION,
        GL_PUBLICATION,
        FALLBACK,
        TERMINAL
    }

    private final AtomicReference<Owner> owner =
            new AtomicReference<>(Owner.NONE);
    private final AtomicBoolean queueEntryClaimed = new AtomicBoolean();
    private final AtomicReference<Cancellation> queueEntryWatchdog =
            new AtomicReference<>();

    void start(
            Host host,
            QueueEntryWatchdog watchdog,
            Runnable glAction,
            Fallback fallback) {
        if (!host.isWindowAttached()
                || !host.isHolderSurfaceAvailable()) {
            fallback(
                    fallback,
                    FailureKind.SURFACE_UNAVAILABLE,
                    new IllegalStateException(
                            "GL surface is unavailable for terminal disposal"));
            return;
        }
        if (!host.isLogicallyAttached()) {
            try {
                host.resumeForTerminalWork();
            } catch (Throwable resumeFailure) {
                fallback(fallback, FailureKind.RESUME, resumeFailure);
                return;
            }
        }
        try {
            host.queueTerminalWork(() -> {
                if (owner.compareAndSet(Owner.NONE, Owner.GL_EXECUTION)) {
                    queueEntryClaimed.set(true);
                    cancelQueueEntryWatchdog();
                    glAction.run();
                }
            });
        } catch (Throwable queueFailure) {
            fallback(fallback, FailureKind.QUEUE, queueFailure);
            return;
        }

        try {
            Cancellation cancellation = Objects.requireNonNull(
                    watchdog.arm(() -> {
                        if (!queueEntryClaimed.get()) {
                            fallback(
                                    fallback,
                                    FailureKind.QUEUE_ENTRY_TIMEOUT,
                                    new IllegalStateException(
                                            "GL queue did not enter terminal disposal before timeout"));
                        }
                    }),
                    "queue-entry watchdog cancellation");
            if (!queueEntryWatchdog.compareAndSet(null, cancellation)) {
                cancellation.cancel();
                throw new IllegalStateException(
                        "GL queue-entry watchdog was already armed");
            }
            if (queueEntryClaimed.get() || owner.get() != Owner.NONE) {
                cancelQueueEntryWatchdog();
            }
        } catch (Throwable watchdogFailure) {
            if (!queueEntryClaimed.get()) {
                fallback(fallback, FailureKind.QUEUE, watchdogFailure);
            }
        }
    }

    void onSurfaceUnavailable(
            Throwable failure,
            Fallback fallback) {
        fallback(fallback, FailureKind.SURFACE_LOST, failure);
    }

    boolean glOwnsExecution() {
        Owner current = owner.get();
        return current == Owner.GL_EXECUTION
                || current == Owner.GL_PUBLICATION;
    }

    boolean completeGlExecution() {
        return owner.compareAndSet(
                Owner.GL_EXECUTION,
                Owner.GL_PUBLICATION);
    }

    boolean completeGlPublication() {
        return owner.compareAndSet(
                Owner.GL_PUBLICATION,
                Owner.TERMINAL);
    }

    boolean abandonGlPublication() {
        return owner.compareAndSet(
                Owner.GL_PUBLICATION,
                Owner.NONE);
    }

    private void cancelQueueEntryWatchdog() {
        Cancellation cancellation = queueEntryWatchdog.getAndSet(null);
        if (cancellation != null) {
            cancellation.cancel();
        }
    }

    private void fallback(
            Fallback fallback,
            FailureKind kind,
            Throwable failure) {
        if (owner.compareAndSet(Owner.NONE, Owner.FALLBACK)) {
            cancelQueueEntryWatchdog();
            fallback.run(kind, failure);
        }
    }
}
