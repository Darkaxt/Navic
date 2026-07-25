package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class PageSurfaceTerminalDisposalGateTest {
    private static final class FakeHost
            implements PageSurfaceTerminalDisposalGate.Host {
        boolean windowAttached = true;
        boolean holderSurfaceAvailable = true;
        boolean logicallyAttached = true;
        boolean resumed;
        Throwable resumeFailure;
        Throwable queueFailure;
        boolean runQueuedSynchronously;
        Runnable queued;

        @Override
        public boolean isWindowAttached() {
            return windowAttached;
        }

        @Override
        public boolean isHolderSurfaceAvailable() {
            return holderSurfaceAvailable;
        }

        @Override
        public boolean isLogicallyAttached() {
            return logicallyAttached;
        }

        @Override
        public void resumeForTerminalWork() {
            resumed = true;
            if (resumeFailure != null) {
                sneakyThrow(resumeFailure);
            }
        }

        @Override
        public void queueTerminalWork(Runnable action) {
            if (queueFailure != null) {
                sneakyThrow(queueFailure);
            }
            if (runQueuedSynchronously) {
                action.run();
            } else {
                queued = action;
            }
        }
    }

    private static final class FakeWatchdog
            implements PageSurfaceTerminalDisposalGate.QueueEntryWatchdog {
        Runnable timeoutAction;
        boolean cancelled;
        Throwable armFailure;

        @Override
        public PageSurfaceTerminalDisposalGate.Cancellation arm(
                Runnable action) {
            if (armFailure != null) {
                sneakyThrow(armFailure);
            }
            timeoutAction = action;
            return () -> {
                cancelled = true;
                if (timeoutAction == action) {
                    timeoutAction = null;
                }
            };
        }

        void fire() {
            Runnable action = timeoutAction;
            if (action != null) {
                action.run();
            }
        }
    }

    @Test
    public void attachedPausedSurfaceResumesThenGlClaimsTerminalWork() {
        PageSurfaceTerminalDisposalGate gate =
                new PageSurfaceTerminalDisposalGate();
        FakeHost host = new FakeHost();
        host.logicallyAttached = false;
        AtomicInteger glRuns = new AtomicInteger();
        AtomicInteger fallbacks = new AtomicInteger();

        gate.start(
                host,
                new FakeWatchdog(),
                glRuns::incrementAndGet,
                (kind, ignored) -> fallbacks.incrementAndGet());
        assertTrue(host.resumed);
        host.queued.run();

        assertEquals(1, glRuns.get());
        assertEquals(0, fallbacks.get());
        assertTrue(gate.glOwnsExecution());
    }

    @Test
    public void completedGlWorkRetainsOwnershipUntilMainPublicationCompletes() {
        PageSurfaceTerminalDisposalGate gate =
                new PageSurfaceTerminalDisposalGate();
        FakeHost host = new FakeHost();
        AtomicInteger fallbacks = new AtomicInteger();
        gate.start(
                host,
                new FakeWatchdog(),
                () -> {},
                (kind, ignored) -> fallbacks.incrementAndGet());
        host.queued.run();

        assertTrue(gate.completeGlExecution());
        assertTrue(gate.glOwnsExecution());
        gate.onSurfaceUnavailable(
                new IllegalStateException(),
                (kind, ignored) -> fallbacks.incrementAndGet());
        assertEquals(0, fallbacks.get());

        assertTrue(gate.completeGlPublication());
        assertFalse(gate.glOwnsExecution());
        gate.onSurfaceUnavailable(
                new IllegalStateException(),
                (kind, ignored) -> fallbacks.incrementAndGet());
        assertEquals(0, fallbacks.get());
        assertFalse(gate.completeGlExecution());
    }

    @Test
    public void failedMainPublicationReleasesOwnershipForFallback() {
        PageSurfaceTerminalDisposalGate gate =
                new PageSurfaceTerminalDisposalGate();
        FakeHost host = new FakeHost();
        AtomicInteger fallbacks = new AtomicInteger();
        gate.start(
                host,
                new FakeWatchdog(),
                () -> {},
                (kind, ignored) -> fallbacks.incrementAndGet());
        host.queued.run();

        assertTrue(gate.completeGlExecution());
        assertTrue(gate.abandonGlPublication());
        gate.onSurfaceUnavailable(
                new IllegalStateException(),
                (kind, ignored) -> fallbacks.incrementAndGet());

        assertEquals(1, fallbacks.get());
        assertFalse(gate.abandonGlPublication());
    }

    @Test
    public void missingHolderSurfaceUsesImmediateFallback() {
        PageSurfaceTerminalDisposalGate gate =
                new PageSurfaceTerminalDisposalGate();
        FakeHost host = new FakeHost();
        host.holderSurfaceAvailable = false;
        AtomicInteger fallbacks = new AtomicInteger();

        gate.start(
                host,
                new FakeWatchdog(),
                () -> fail("GL must not run"),
                (kind, ignored) -> fallbacks.incrementAndGet());

        assertEquals(1, fallbacks.get());
        assertNull(host.queued);
    }

    @Test
    public void surfaceDestroyedAfterQueueBeforeEntryMakesLateGlRunnableNoOp() {
        PageSurfaceTerminalDisposalGate gate =
                new PageSurfaceTerminalDisposalGate();
        FakeHost host = new FakeHost();
        AtomicInteger glRuns = new AtomicInteger();
        AtomicInteger fallbacks = new AtomicInteger();
        gate.start(
                host,
                new FakeWatchdog(),
                glRuns::incrementAndGet,
                (kind, ignored) -> fallbacks.incrementAndGet());

        gate.onSurfaceUnavailable(
                new IllegalStateException(),
                (kind, ignored) -> fallbacks.incrementAndGet());
        host.queued.run();

        assertEquals(1, fallbacks.get());
        assertEquals(0, glRuns.get());
    }

    @Test
    public void synchronousGlEntryWinsEvenWhenWatchdogCannotArm() {
        PageSurfaceTerminalDisposalGate gate =
                new PageSurfaceTerminalDisposalGate();
        FakeHost host = new FakeHost();
        host.runQueuedSynchronously = true;
        FakeWatchdog watchdog = new FakeWatchdog();
        watchdog.armFailure = new IllegalStateException("watchdog-rejected");
        AtomicInteger glRuns = new AtomicInteger();
        AtomicInteger fallbacks = new AtomicInteger();

        gate.start(
                host,
                watchdog,
                glRuns::incrementAndGet,
                (kind, ignored) -> fallbacks.incrementAndGet());

        assertEquals(1, glRuns.get());
        assertEquals(0, fallbacks.get());
        assertTrue(gate.glOwnsExecution());
    }

    @Test
    public void queueEntryTimeoutClaimsFallbackAndLateGlRunnableCannotRun() {
        PageSurfaceTerminalDisposalGate gate =
                new PageSurfaceTerminalDisposalGate();
        FakeHost host = new FakeHost();
        FakeWatchdog watchdog = new FakeWatchdog();
        AtomicInteger glRuns = new AtomicInteger();
        List<PageSurfaceTerminalDisposalGate.FailureKind> failures =
                new ArrayList<>();

        gate.start(
                host,
                watchdog,
                glRuns::incrementAndGet,
                (kind, ignored) -> failures.add(kind));
        watchdog.fire();
        host.queued.run();

        assertEquals(0, glRuns.get());
        assertEquals(1, failures.size());
        assertEquals(
                PageSurfaceTerminalDisposalGate.FailureKind.QUEUE_ENTRY_TIMEOUT,
                failures.get(0));
        assertTrue(watchdog.cancelled);
        assertFalse(gate.glOwnsExecution());
    }

    @Test
    public void resumeAndQueueFailuresCompleteThroughTypedFallback() {
        for (boolean failResume : new boolean[] {true, false}) {
            PageSurfaceTerminalDisposalGate gate =
                    new PageSurfaceTerminalDisposalGate();
            FakeHost host = new FakeHost();
            host.logicallyAttached = !failResume;
            if (failResume) {
                host.resumeFailure = new IllegalStateException();
            } else {
                host.queueFailure = new IllegalStateException();
            }
            List<PageSurfaceTerminalDisposalGate.FailureKind> failures =
                    new ArrayList<>();

            gate.start(
                    host,
                    new FakeWatchdog(),
                    () -> fail("GL must not run"),
                    (kind, ignored) -> failures.add(kind));

            assertEquals(1, failures.size());
            assertEquals(
                    failResume
                            ? PageSurfaceTerminalDisposalGate.FailureKind.RESUME
                            : PageSurfaceTerminalDisposalGate.FailureKind.QUEUE,
                    failures.get(0));
        }
    }

    @Test
    public void detachedSurfaceLossIsAnExpectedOwnershipFallback() {
        assertTrue(PageSurfaceTerminalDisposalGate.isExpectedDetachedFallback(
                PageSurfaceTerminalDisposalGate.FailureKind.SURFACE_UNAVAILABLE,
                false,
                false));
        assertTrue(PageSurfaceTerminalDisposalGate.isExpectedDetachedFallback(
                PageSurfaceTerminalDisposalGate.FailureKind.SURFACE_LOST,
                false,
                false));
    }

    @Test
    public void liveOrBrokenQueueFallbacksRemainFailures() {
        assertFalse(PageSurfaceTerminalDisposalGate.isExpectedDetachedFallback(
                PageSurfaceTerminalDisposalGate.FailureKind.SURFACE_UNAVAILABLE,
                false,
                true));
        assertFalse(PageSurfaceTerminalDisposalGate.isExpectedDetachedFallback(
                PageSurfaceTerminalDisposalGate.FailureKind.SURFACE_LOST,
                false,
                true));
        assertFalse(PageSurfaceTerminalDisposalGate.isExpectedDetachedFallback(
                PageSurfaceTerminalDisposalGate.FailureKind.QUEUE,
                false,
                false));
        assertFalse(PageSurfaceTerminalDisposalGate.isExpectedDetachedFallback(
                PageSurfaceTerminalDisposalGate.FailureKind.QUEUE_ENTRY_TIMEOUT,
                false,
                false));
        assertFalse(PageSurfaceTerminalDisposalGate.isExpectedDetachedFallback(
                PageSurfaceTerminalDisposalGate.FailureKind.RESUME,
                false,
                false));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable failure)
            throws E {
        throw (E) failure;
    }
}
