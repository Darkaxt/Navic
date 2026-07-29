package karacken.curl;

import static org.junit.Assert.assertEquals;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class PageSurfaceOwnershipRetryEdgeTest {
    @Test
    public void coalescesTriggersUntilThePostedDeliveryRuns() {
        FakeHost host = new FakeHost();
        AtomicInteger callbacks = new AtomicInteger();
        host.listener = callbacks::incrementAndGet;
        PageSurfaceOwnershipRetryEdge edge = new PageSurfaceOwnershipRetryEdge(host);

        edge.schedule();
        edge.schedule();
        edge.schedule();

        assertEquals(1, host.posts.size());
        host.runNext();
        assertEquals(1, callbacks.get());
    }

    @Test
    public void deliveryUsesTheCurrentListenerAndHonorsClearing() {
        FakeHost host = new FakeHost();
        AtomicInteger original = new AtomicInteger();
        AtomicInteger replacement = new AtomicInteger();
        host.listener = original::incrementAndGet;
        PageSurfaceOwnershipRetryEdge edge = new PageSurfaceOwnershipRetryEdge(host);

        edge.schedule();
        host.listener = replacement::incrementAndGet;
        edge.schedule();
        host.runNext();

        assertEquals(0, original.get());
        assertEquals(1, replacement.get());

        edge.schedule();
        host.listener = null;
        host.runNext();
        assertEquals(1, replacement.get());
    }

    @Test
    public void deliveryRevalidatesAvailability() {
        FakeHost host = new FakeHost();
        AtomicInteger callbacks = new AtomicInteger();
        host.listener = callbacks::incrementAndGet;
        PageSurfaceOwnershipRetryEdge edge = new PageSurfaceOwnershipRetryEdge(host);

        edge.schedule();
        host.available = false;
        host.runNext();
        assertEquals(0, callbacks.get());

        host.available = true;
        edge.schedule();
        host.runNext();
        assertEquals(1, callbacks.get());
    }

    @Test
    public void waitsForAvailabilityAndRecoversFromPostRejection() {
        FakeHost host = new FakeHost();
        AtomicInteger callbacks = new AtomicInteger();
        host.listener = callbacks::incrementAndGet;
        host.available = false;
        PageSurfaceOwnershipRetryEdge edge = new PageSurfaceOwnershipRetryEdge(host);

        edge.schedule();
        assertEquals(0, host.posts.size());

        host.available = true;
        host.acceptPosts = false;
        edge.schedule();
        assertEquals(0, host.posts.size());

        host.acceptPosts = true;
        edge.schedule();
        host.runNext();
        assertEquals(1, callbacks.get());
    }

    private static final class FakeHost implements PageSurfaceOwnershipRetryEdge.Host {
        final Queue<Runnable> posts = new ArrayDeque<>();
        boolean available = true;
        boolean acceptPosts = true;
        Runnable listener;

        @Override
        public boolean isOwnershipAvailable() {
            return available;
        }

        @Override
        public Runnable ownershipRetryListener() {
            return listener;
        }

        @Override
        public boolean post(Runnable action) {
            if (!acceptPosts) {
                return false;
            }
            posts.add(action);
            return true;
        }

        void runNext() {
            posts.remove().run();
        }
    }
}
