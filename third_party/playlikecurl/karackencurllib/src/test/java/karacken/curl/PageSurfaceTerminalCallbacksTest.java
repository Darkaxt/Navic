package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class PageSurfaceTerminalCallbacksTest {
    @Test
    public void hostileCallbackCannotSuppressLaterWaiters() {
        List<Throwable> failures = new ArrayList<>();
        PageSurfaceTerminalCallbacks<Object> callbacks =
                new PageSurfaceTerminalCallbacks<>(
                        2,
                        (callback, failure) -> failures.add(failure));
        AtomicInteger delivered = new AtomicInteger();
        Object result = new Object();
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.ACCEPTED,
                callbacks.add(ignored -> {
                    throw new IllegalStateException("hostile");
                }));
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.ACCEPTED,
                callbacks.add(ignored -> delivered.incrementAndGet()));

        assertTrue(callbacks.complete(result));

        assertEquals(1, failures.size());
        assertEquals(1, delivered.get());
        assertEquals(0, callbacks.pendingCount());
        assertSame(result, callbacks.result());
    }

    @Test
    public void fullRegistryRejectsWithoutGrowthAndCompletionReturnsCapacity() {
        PageSurfaceTerminalCallbacks<Object> callbacks =
                new PageSurfaceTerminalCallbacks<>(
                        1,
                        (callback, failure) -> {});
        AtomicInteger delivered = new AtomicInteger();

        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.ACCEPTED,
                callbacks.add(ignored -> delivered.incrementAndGet()));
        assertEquals(1, callbacks.pendingCount());
        assertEquals(1, callbacks.capacity());
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.CALLBACK_CAPACITY,
                callbacks.add(ignored -> delivered.addAndGet(100)));
        assertEquals(1, callbacks.pendingCount());

        Object result = new Object();
        assertTrue(callbacks.complete(result));
        assertEquals(1, delivered.get());
        assertEquals(0, callbacks.pendingCount());
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.DELIVERED_TERMINAL,
                callbacks.add(ignored -> delivered.incrementAndGet()));
        assertEquals(2, delivered.get());
        assertEquals(0, callbacks.pendingCount());
    }

    @Test
    public void repeatedCompletionKeepsFirstResultAndLateWaiterGetsIt() {
        PageSurfaceTerminalCallbacks<Object> callbacks =
                new PageSurfaceTerminalCallbacks<>(
                        1,
                        (callback, failure) -> {});
        Object first = new Object();
        Object second = new Object();
        List<Object> delivered = new ArrayList<>();

        assertTrue(callbacks.complete(first));
        assertFalse(callbacks.complete(second));
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.DELIVERED_TERMINAL,
                callbacks.add(delivered::add));

        assertEquals(1, delivered.size());
        assertSame(first, delivered.get(0));
        assertSame(first, callbacks.result());
    }

    @Test
    public void hostileFailureHandlerCannotSuppressLaterWaiters() {
        PageSurfaceTerminalCallbacks<Object> callbacks =
                new PageSurfaceTerminalCallbacks<>(2, (callback, failure) -> {
                    throw new AssertionError("diagnostic-failed");
                });
        AtomicInteger delivered = new AtomicInteger();
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.ACCEPTED,
                callbacks.add(ignored -> {
                    throw new IllegalStateException("hostile");
                }));
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.ACCEPTED,
                callbacks.add(ignored -> delivered.incrementAndGet()));

        assertTrue(callbacks.complete(new Object()));
        assertEquals(1, delivered.get());
    }
}
