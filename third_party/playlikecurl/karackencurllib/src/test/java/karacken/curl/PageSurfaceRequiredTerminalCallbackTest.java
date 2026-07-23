package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class PageSurfaceRequiredTerminalCallbackTest {
    @Test
    public void registeredOwnerReceivesTerminalResultExactlyOnce() {
        PageSurfaceRequiredTerminalCallback<Object> required =
                new PageSurfaceRequiredTerminalCallback<>((callback, failure) -> {});
        List<Object> delivered = new ArrayList<>();
        Object terminal = new Object();
        required.register(delivered::add);

        assertEquals(1, required.pendingCount());
        assertTrue(required.complete(terminal));
        assertFalse(required.complete(new Object()));

        assertEquals(0, required.pendingCount());
        assertEquals(1, delivered.size());
        assertSame(terminal, delivered.get(0));
    }

    @Test
    public void terminalReplayStillRejectsSecondLifecycleOwner() {
        PageSurfaceRequiredTerminalCallback<Object> required =
                new PageSurfaceRequiredTerminalCallback<>((callback, failure) -> {});
        Object terminal = new Object();
        List<Object> firstDelivery = new ArrayList<>();
        assertTrue(required.complete(terminal));

        required.register(firstDelivery::add);

        assertEquals(1, firstDelivery.size());
        assertSame(terminal, firstDelivery.get(0));
        assertThrows(
                IllegalStateException.class,
                () -> required.register(ignored -> fail("second owner ran")));
        assertEquals(0, required.pendingCount());
    }

    @Test
    public void duplicateRegistrationCannotReplacePendingOwner() {
        PageSurfaceRequiredTerminalCallback<Object> required =
                new PageSurfaceRequiredTerminalCallback<>((callback, failure) -> {});
        List<Object> firstDelivery = new ArrayList<>();
        required.register(firstDelivery::add);

        assertThrows(
                IllegalStateException.class,
                () -> required.register(ignored -> fail("replacement ran")));
        Object terminal = new Object();
        assertTrue(required.complete(terminal));

        assertEquals(1, firstDelivery.size());
        assertSame(terminal, firstDelivery.get(0));
    }

    @Test
    public void hostileOwnerAndFailureHandlerCannotChangeTerminalOwnership() {
        AtomicInteger failureReports = new AtomicInteger();
        PageSurfaceRequiredTerminalCallback<Object> required =
                new PageSurfaceRequiredTerminalCallback<>((callback, failure) -> {
                    failureReports.incrementAndGet();
                    throw new AssertionError("diagnostic-failed");
                });
        required.register(ignored -> {
            throw new IllegalStateException("hostile");
        });

        assertTrue(required.complete(new Object()));
        assertEquals(1, failureReports.get());
        assertEquals(0, required.pendingCount());
    }

    @Test
    public void auxiliarySaturationCannotConsumeRequiredLifecycleSlot() {
        PageSurfaceTerminalCallbacks<Object> auxiliary =
                new PageSurfaceTerminalCallbacks<>(2, (callback, failure) -> {});
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.ACCEPTED,
                auxiliary.add(ignored -> {}));
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.ACCEPTED,
                auxiliary.add(ignored -> {}));
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.CALLBACK_CAPACITY,
                auxiliary.add(ignored -> {}));

        PageSurfaceRequiredTerminalCallback<Object> required =
                new PageSurfaceRequiredTerminalCallback<>((callback, failure) -> {});
        List<Object> delivered = new ArrayList<>();
        Object terminal = new Object();
        required.register(delivered::add);

        assertTrue(required.complete(terminal));
        assertEquals(0, required.pendingCount());
        assertEquals(1, delivered.size());
        assertSame(terminal, delivered.get(0));
        assertEquals(2, auxiliary.pendingCount());
    }
}
