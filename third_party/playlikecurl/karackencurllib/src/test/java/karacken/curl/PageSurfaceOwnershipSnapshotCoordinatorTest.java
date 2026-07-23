package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.Test;

public final class PageSurfaceOwnershipSnapshotCoordinatorTest {
    @Test
    public void stableEpochPublishesOneSnapshotOnMain() {
        FakeHost host = new FakeHost();
        host.activeLeases = 1;
        host.textures = 3;
        PageSurfaceOwnershipSnapshotCoordinator coordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(host, 2);

        coordinator.request(host::record);
        assertEquals(1, coordinator.size());
        assertEquals(1, host.glActions.size());
        assertTrue(host.results.isEmpty());

        host.runNextGl();
        assertEquals(1, host.mainActions.size());
        assertTrue(host.results.isEmpty());
        host.runNextMain();

        assertEquals(0, coordinator.size());
        assertEquals(1, host.results.size());
        PageSurfaceOwnershipResult result = host.results.get(0);
        assertEquals(PageSurfaceOwnershipResult.Status.AVAILABLE, result.getStatus());
        assertEquals(1, result.getSnapshot().getActiveDeckLeases());
        assertEquals(3, result.getSnapshot().getTextures());
    }

    @Test
    public void mutationBeforeGlExecutionRetriesOneTokenWithFreshSample() {
        FakeHost host = new FakeHost();
        host.activeLeases = 1;
        PageSurfaceOwnershipSnapshotCoordinator coordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(host, 1);
        coordinator.request(host::record);

        host.mutate(2);
        host.runNextGl();
        host.runNextMain();

        assertEquals(1, coordinator.size());
        assertEquals(1, host.glActions.size());
        assertTrue(host.results.isEmpty());
        host.runNextGl();
        host.runNextMain();

        assertEquals(0, coordinator.size());
        assertEquals(1, host.results.size());
        assertEquals(2, host.results.get(0).getSnapshot().getActiveDeckLeases());
    }

    @Test
    public void mutationAfterGlSamplingRetriesAndIgnoresRepeatedOldCompletion() {
        FakeHost host = new FakeHost();
        host.activeLeases = 1;
        PageSurfaceOwnershipSnapshotCoordinator coordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(host, 1);
        coordinator.request(host::record);
        host.runNextGl();
        Runnable oldCompletion = host.mainActions.removeFirst();

        host.mutate(4);
        oldCompletion.run();
        assertEquals(1, host.glActions.size());
        oldCompletion.run();
        assertEquals(1, host.glActions.size());
        assertTrue(host.results.isEmpty());

        host.runNextGl();
        host.runNextMain();
        assertEquals(1, host.results.size());
        assertEquals(4, host.results.get(0).getSnapshot().getActiveDeckLeases());
        assertEquals(0, coordinator.size());
    }

    @Test
    public void repeatedMutationsRetainOneTokenAndOneCallback() {
        FakeHost host = new FakeHost();
        PageSurfaceOwnershipSnapshotCoordinator coordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(host, 1);
        coordinator.request(host::record);

        for (int active = 1; active <= 3; active++) {
            host.mutate(active);
            host.runNextGl();
            host.runNextMain();
            assertEquals(1, coordinator.size());
            assertTrue(host.results.isEmpty());
        }

        host.runNextGl();
        host.runNextMain();
        assertEquals(1, host.results.size());
        assertEquals(3, host.results.get(0).getSnapshot().getActiveDeckLeases());
        assertEquals(0, coordinator.size());
    }

    @Test
    public void glQueueRejectionDeliversExactlyOnceOnMain() {
        FakeHost host = new FakeHost();
        host.acceptGl = false;
        PageSurfaceOwnershipSnapshotCoordinator coordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(host, 1);

        coordinator.request(host::record);

        assertEquals(1, host.results.size());
        assertEquals(
                PageSurfaceOwnershipResult.Status.QUEUE_REJECTED,
                host.results.get(0).getStatus());
        assertEquals(0, coordinator.size());
        assertTrue(host.glActions.isEmpty());
        assertTrue(host.mainActions.isEmpty());
    }

    @Test
    public void glTextureCaptureFailurePostsRejectionBeforeDelivery() {
        FakeHost host = new FakeHost();
        host.throwTextureCapture = true;
        PageSurfaceOwnershipSnapshotCoordinator coordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(host, 1);
        coordinator.request(host::record);

        host.runNextGl();
        assertTrue(host.results.isEmpty());
        assertEquals(1, host.mainActions.size());
        host.runNextMain();

        assertEquals(1, host.results.size());
        assertEquals(
                PageSurfaceOwnershipResult.Status.QUEUE_REJECTED,
                host.results.get(0).getStatus());
        assertEquals(0, coordinator.size());
    }

    @Test
    public void terminalizedMainPostRequiresPriorRemovalAndCannotRetainToken() {
        FakeHost host = new FakeHost();
        PageSurfaceOwnershipSnapshotCoordinator coordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(host, 1);
        coordinator.request(host::record);

        List<PageSurfaceOwnershipResult.Callback> transferred = coordinator.drain();
        assertEquals(1, transferred.size());
        assertEquals(0, coordinator.size());
        host.terminalizeMainPosts = true;
        host.runNextGl();

        assertEquals(0, coordinator.size());
        assertTrue(host.results.isEmpty());
        assertTrue(host.mainActions.isEmpty());
    }

    @Test
    public void terminalizedPostWhileTokenIsLiveThrowsInvariantFailure() {
        FakeHost host = new FakeHost();
        PageSurfaceOwnershipSnapshotCoordinator coordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(host, 1);
        coordinator.request(host::record);
        host.terminalizeMainPosts = true;

        assertThrows(IllegalStateException.class, host::runNextGl);
        assertEquals(1, coordinator.size());
        assertTrue(host.results.isEmpty());
    }

    @Test
    public void callbackCapacityRemainsBoundedAcrossRetryAttempts() {
        FakeHost host = new FakeHost();
        PageSurfaceOwnershipSnapshotCoordinator coordinator =
                new PageSurfaceOwnershipSnapshotCoordinator(host, 1);
        assertEquals(
                PageSurfaceOwnershipRequestRegistry.Registration.Status.ACCEPTED,
                coordinator.request(host::record).status());
        assertEquals(
                PageSurfaceOwnershipRequestRegistry.Registration.Status.CALLBACK_CAPACITY,
                coordinator.request(host::record).status());
        assertEquals(1, coordinator.size());
        assertEquals(1, coordinator.capacity());

        host.mutate(1);
        host.runNextGl();
        host.runNextMain();
        assertEquals(1, coordinator.size());
        assertEquals(1, host.glActions.size());

        host.runNextGl();
        host.runNextMain();
        assertEquals(0, coordinator.size());
        assertEquals(1, host.results.size());
    }

    private static final class Sample
            implements PageSurfaceOwnershipSnapshotCoordinator.MainSample {
        private final long epoch;
        private final int activeLeases;

        Sample(long epoch, int activeLeases) {
            this.epoch = epoch;
            this.activeLeases = activeLeases;
        }

        @Override
        public long ownershipEpoch() {
            return epoch;
        }

        @Override
        public PageSurfaceOwnershipSnapshot withTextures(
                int textures,
                int textureLimit) {
            return new PageSurfaceOwnershipSnapshot(
                    activeLeases,
                    1,
                    0,
                    1,
                    0,
                    DeckLeaseRegistry.MAX_DECK_LEASES,
                    0,
                    0,
                    textures,
                    textureLimit);
        }
    }

    private static final class FakeHost
            implements PageSurfaceOwnershipSnapshotCoordinator.Host {
        final Deque<Runnable> glActions = new ArrayDeque<>();
        final Deque<Runnable> mainActions = new ArrayDeque<>();
        final List<PageSurfaceOwnershipResult> results = new ArrayList<>();
        boolean mainThread = true;
        boolean acceptGl = true;
        boolean terminalizeMainPosts;
        boolean throwTextureCapture;
        long epoch;
        int activeLeases;
        int textures = 2;
        int textureLimit = TextureBudget.maximumTextureSlots();

        @Override
        public void requireMainThread() {
            if (!mainThread) {
                throw new IllegalStateException("Expected fake main thread");
            }
        }

        @Override
        public PageSurfaceOwnershipSnapshotCoordinator.MainSample captureMainSample() {
            requireMainThread();
            return new Sample(epoch, activeLeases);
        }

        @Override
        public long currentOwnershipEpoch() {
            requireMainThread();
            return epoch;
        }

        @Override
        public int captureTextureCount() {
            if (mainThread) {
                throw new IllegalStateException("Texture count must be sampled on GL");
            }
            if (throwTextureCapture) {
                throw new IllegalStateException("texture-capture");
            }
            return textures;
        }

        @Override
        public int captureTextureLimit() {
            if (mainThread) {
                throw new IllegalStateException("Texture limit must be sampled on GL");
            }
            return textureLimit;
        }

        @Override
        public boolean queueGl(Runnable action) {
            requireMainThread();
            if (!acceptGl) {
                return false;
            }
            glActions.addLast(action);
            return true;
        }

        @Override
        public PageSurfaceOwnershipSnapshotCoordinator.MainPostStatus postMain(
                Runnable action) {
            if (mainThread) {
                throw new IllegalStateException("GL must post main completion");
            }
            if (terminalizeMainPosts) {
                return PageSurfaceOwnershipSnapshotCoordinator.MainPostStatus
                        .TERMINALIZED;
            }
            mainActions.addLast(action);
            return PageSurfaceOwnershipSnapshotCoordinator.MainPostStatus.ACCEPTED;
        }

        @Override
        public void deliver(
                PageSurfaceOwnershipResult.Callback callback,
                PageSurfaceOwnershipResult result) {
            requireMainThread();
            callback.onResult(result);
        }

        void record(PageSurfaceOwnershipResult result) {
            requireMainThread();
            results.add(result);
        }

        void mutate(int active) {
            requireMainThread();
            activeLeases = active;
            epoch += 1L;
        }

        void runNextGl() {
            Runnable action = glActions.removeFirst();
            mainThread = false;
            try {
                action.run();
            } finally {
                mainThread = true;
            }
        }

        void runNextMain() {
            requireMainThread();
            mainActions.removeFirst().run();
        }
    }
}
