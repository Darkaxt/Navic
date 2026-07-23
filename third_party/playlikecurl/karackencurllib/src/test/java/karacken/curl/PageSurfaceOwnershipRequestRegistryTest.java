package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class PageSurfaceOwnershipRequestRegistryTest {
    @Test
    public void fullRegistryRejectsWithoutAllocatingAndTakeReturnsCapacity() {
        PageSurfaceOwnershipRequestRegistry registry =
                new PageSurfaceOwnershipRequestRegistry(1);
        PageSurfaceOwnershipResult.Callback callback = result -> {};
        AtomicInteger capacityEdges = new AtomicInteger();
        registry.setCapacityAvailableListener(capacityEdges::incrementAndGet);
        PageSurfaceOwnershipRequestRegistry.Registration accepted =
                registry.register(callback);

        assertEquals(
                PageSurfaceOwnershipRequestRegistry.Registration.Status.ACCEPTED,
                accepted.status());
        assertEquals(1, registry.size());
        assertEquals(1, registry.capacity());
        assertEquals(
                PageSurfaceOwnershipRequestRegistry.Registration.Status.CALLBACK_CAPACITY,
                registry.register(result -> {}).status());
        assertEquals(1, registry.size());

        assertSame(callback, registry.take(accepted.token()));
        assertEquals(1, capacityEdges.get());
        assertNull(registry.take(accepted.token()));
        assertEquals(1, capacityEdges.get());
        assertEquals(0, registry.size());
        assertEquals(
                PageSurfaceOwnershipRequestRegistry.Registration.Status.ACCEPTED,
                registry.register(result -> {}).status());
    }

    @Test
    public void ownershipMutationBetweenGlRequestAndCompletionRetriesFreshSample() {
        PageSurfaceOwnershipRequestRegistry registry =
                new PageSurfaceOwnershipRequestRegistry(1);
        PageSurfaceOwnershipResult.Callback callback = result -> {};
        long token = registry.register(callback).token();
        PageSurfaceOwnershipRequestRegistry.Attempt first =
                registry.beginAttempt(token, 7L);

        assertEquals(
                PageSurfaceOwnershipRequestRegistry.Completion.Status.RETRY,
                registry.finishAttempt(first, 8L).status());
        assertEquals(1, registry.size());

        PageSurfaceOwnershipRequestRegistry.Attempt second =
                registry.beginAttempt(token, 8L);
        assertEquals(
                PageSurfaceOwnershipRequestRegistry.Completion.Status.MISSING,
                registry.finishAttempt(first, 8L).status());
        PageSurfaceOwnershipRequestRegistry.Completion completed =
                registry.finishAttempt(second, 8L);

        assertEquals(
                PageSurfaceOwnershipRequestRegistry.Completion.Status.COMPLETE,
                completed.status());
        assertSame(callback, completed.callback());
        assertEquals(0, registry.size());
    }

    @Test
    public void disposalTransferMovesCallbacksWithoutDoubleCounting() {
        PageSurfaceOwnershipRequestRegistry registry =
                new PageSurfaceOwnershipRequestRegistry(2);
        AtomicInteger delivered = new AtomicInteger();
        registry.register(result -> delivered.incrementAndGet());
        registry.register(result -> delivered.incrementAndGet());
        PageSurfaceTerminalCallbacks<PageSurfaceOwnershipResult> terminal =
                new PageSurfaceTerminalCallbacks<>(
                        2,
                        (callback, failure) -> {});

        List<PageSurfaceOwnershipResult.Callback> transferred =
                registry.drain();
        for (PageSurfaceOwnershipResult.Callback callback : transferred) {
            assertEquals(
                    PageSurfaceTerminalCallbacks.AddResult.ACCEPTED,
                    terminal.add(callback::onResult));
        }

        assertEquals(0, registry.size());
        assertEquals(2, terminal.pendingCount());
        assertEquals(
                PageSurfaceTerminalCallbacks.AddResult.CALLBACK_CAPACITY,
                terminal.add(result -> delivered.addAndGet(100)));
        assertEquals(2, terminal.pendingCount());
        assertEquals(
                2,
                registry.size() + terminal.pendingCount());

        assertTrue(
                terminal.complete(
                        PageSurfaceOwnershipResult.unavailable(
                                PageSurfaceOwnershipResult.Status
                                        .SURFACE_UNAVAILABLE)));
        assertEquals(2, delivered.get());
        assertEquals(0, terminal.pendingCount());
    }

    @Test
    public void drainReturnsEveryCallbackAndInvalidatesLateAttempts() {
        PageSurfaceOwnershipRequestRegistry registry =
                new PageSurfaceOwnershipRequestRegistry(2);
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        PageSurfaceOwnershipRequestRegistry.Registration first =
                registry.register(result -> firstCalls.incrementAndGet());
        PageSurfaceOwnershipRequestRegistry.Registration second =
                registry.register(result -> secondCalls.incrementAndGet());
        PageSurfaceOwnershipRequestRegistry.Attempt late =
                registry.beginAttempt(first.token(), 3L);

        List<PageSurfaceOwnershipResult.Callback> drained = registry.drain();
        PageSurfaceOwnershipResult unavailable =
                PageSurfaceOwnershipResult.unavailable(
                        PageSurfaceOwnershipResult.Status.SURFACE_UNAVAILABLE);
        drained.forEach(callback -> callback.onResult(unavailable));

        assertEquals(2, drained.size());
        assertEquals(1, firstCalls.get());
        assertEquals(1, secondCalls.get());
        assertNull(registry.take(first.token()));
        assertNull(registry.take(second.token()));
        assertEquals(
                PageSurfaceOwnershipRequestRegistry.Completion.Status.MISSING,
                registry.finishAttempt(late, 3L).status());
        assertEquals(0, registry.drain().size());
    }
}
