package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class PageSurfaceSnapshotCallbacksTest {
    @Test
    public void completedBatchDoesNotReplayIntoALaterSample() {
        PageSurfaceSnapshotCallbacks<String> callbacks =
                new PageSurfaceSnapshotCallbacks<>(2, (callback, failure) -> {});
        List<String> delivered = new ArrayList<>();

        assertTrue(callbacks.add(delivered::add));
        callbacks.completeBatch("first");
        assertEquals(0, callbacks.pendingCount());
        assertTrue(callbacks.add(delivered::add));
        callbacks.completeBatch("second");

        assertEquals(List.of("first", "second"), delivered);
    }

    @Test
    public void callbackCapacityIsBoundedPerPendingBatch() {
        PageSurfaceSnapshotCallbacks<String> callbacks =
                new PageSurfaceSnapshotCallbacks<>(1, (callback, failure) -> {});

        assertTrue(callbacks.add(ignored -> {}));
        assertFalse(callbacks.add(ignored -> {}));
        callbacks.completeBatch("snapshot");
        assertTrue(callbacks.add(ignored -> {}));
    }

    @Test
    public void throwingCallbackDoesNotBlockTheRemainingBatchOrNextBatch() {
        List<Throwable> failures = new ArrayList<>();
        PageSurfaceSnapshotCallbacks<String> callbacks =
                new PageSurfaceSnapshotCallbacks<>(2,
                        (callback, failure) -> failures.add(failure));
        List<String> delivered = new ArrayList<>();
        callbacks.add(ignored -> {
            throw new IllegalStateException("callback-failed");
        });
        callbacks.add(delivered::add);

        callbacks.completeBatch("first");
        callbacks.add(delivered::add);
        callbacks.completeBatch("second");

        assertEquals(1, failures.size());
        assertEquals(List.of("first", "second"), delivered);
    }
}
