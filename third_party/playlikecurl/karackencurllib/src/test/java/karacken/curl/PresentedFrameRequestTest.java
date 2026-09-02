package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class PresentedFrameRequestTest {
    @Test
    public void callbackCompletesOnlyAfterItsArmedFrameRenders() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        List<String> callbacks = new ArrayList<>();
        long requestId = request.request(() -> callbacks.add("publisher"));

        assertEquals(1, request.pendingCount());
        assertEquals(PresentedFrameRequest.NO_REQUEST_ID, request.markRendered());
        assertNull(request.complete(requestId));
        assertTrue(request.arm(requestId));
        long frameId = request.markRendered();
        assertNotEquals(PresentedFrameRequest.NO_REQUEST_ID, frameId);
        assertEquals(PresentedFrameRequest.NO_REQUEST_ID, request.markRendered());
        request.complete(frameId).run();
        assertEquals(Arrays.asList("publisher"), callbacks);
        assertEquals(0, request.pendingCount());
        assertNull(request.complete(frameId));
    }

    @Test
    public void cancellationSuppressesOnlyItsLogicalConsumer() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        List<String> callbacks = new ArrayList<>();
        long publisher = request.request(() -> callbacks.add("publisher"));
        long capture = request.request(() -> callbacks.add("capture"));
        long gesture = request.request(() -> callbacks.add("gesture"));

        assertTrue(request.cancel(capture));
        assertFalse(request.arm(capture));
        assertTrue(request.arm(publisher));
        assertFalse(request.arm(gesture));
        long frameId = request.markRendered();
        request.complete(frameId).run();

        assertEquals(Arrays.asList("publisher", "gesture"), callbacks);
        assertFalse(request.cancel(capture));
        assertEquals(0, request.pendingCount());
    }

    @Test
    public void cancellationAfterRenderBeforeDispatchSuppressesOnlyItsConsumer() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        List<String> callbacks = new ArrayList<>();
        long publisher = request.request(() -> callbacks.add("publisher"));
        long capture = request.request(() -> callbacks.add("capture"));
        assertTrue(request.arm(publisher));
        assertFalse(request.arm(capture));
        long frameId = request.markRendered();

        assertTrue(request.cancel(capture));
        request.complete(frameId).run();

        assertEquals(Arrays.asList("publisher"), callbacks);
        assertEquals(0, request.pendingCount());
    }

    @Test
    public void publisherAndLiveCaptureShareOneFrameInBothRegistrationOrders() {
        assertTwoConsumersShareFrame("publisher", "capture");
        assertTwoConsumersShareFrame("capture", "publisher");
    }

    @Test
    public void publisherAndGestureRevealShareOneFrameInBothRegistrationOrders() {
        assertTwoConsumersShareFrame("publisher", "gesture");
        assertTwoConsumersShareFrame("gesture", "publisher");
    }

    @Test
    public void publisherCaptureAndGestureFanOutFromOnePhysicalFrame() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        List<String> callbacks = new ArrayList<>();
        long publisher = request.request(() -> callbacks.add("publisher"));
        long capture = request.request(() -> callbacks.add("capture"));
        long gesture = request.request(() -> callbacks.add("gesture"));

        assertTrue(request.arm(publisher));
        assertFalse(request.arm(capture));
        assertFalse(request.arm(gesture));
        assertEquals(3, request.pendingCount());
        long frameId = request.markRendered();
        request.complete(frameId).run();

        assertEquals(Arrays.asList("publisher", "capture", "gesture"), callbacks);
        assertEquals(0, request.pendingCount());
    }

    @Test
    public void cancelAllClearsSharedWaitersAndLaterRecreationCanRequest() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        long publisher = request.request(() -> {});
        long capture = request.request(() -> {});
        long gesture = request.request(() -> {});
        assertTrue(request.arm(publisher));
        assertFalse(request.arm(capture));
        assertFalse(request.arm(gesture));

        request.cancelAll();

        assertEquals(0, request.pendingCount());
        assertEquals(PresentedFrameRequest.NO_REQUEST_ID, request.markRendered());
        assertFalse(request.arm(publisher));
        long recreated = request.request(() -> {});
        assertTrue(request.arm(recreated));
        assertNotEquals(PresentedFrameRequest.NO_REQUEST_ID, request.markRendered());
    }

    @Test
    public void oneConsumerFailureDoesNotSuppressRemainingConsumers() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        List<String> callbacks = new ArrayList<>();
        long failing = request.request(() -> {
            callbacks.add("failing");
            throw new IllegalStateException("synthetic callback failure");
        });
        long current = request.request(() -> callbacks.add("current"));
        assertTrue(request.arm(failing));
        assertFalse(request.arm(current));
        Runnable completion = request.complete(request.markRendered());

        try {
            completion.run();
            fail("The isolated aggregate must report the first callback failure");
        } catch (IllegalStateException expected) {
            assertEquals(
                    Arrays.asList("failing", "current"),
                    callbacks);
        }
        assertEquals(0, request.pendingCount());
    }

    @Test
    public void requestRegisteredDuringDispatchBelongsToNextFrame() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        List<String> callbacks = new ArrayList<>();
        long[] nextRequestId = {PresentedFrameRequest.NO_REQUEST_ID};
        long first = request.request(() -> {
            callbacks.add("first");
            nextRequestId[0] = request.request(() -> callbacks.add("next"));
            assertTrue(request.arm(nextRequestId[0]));
        });
        assertTrue(request.arm(first));
        Runnable firstCompletion = request.complete(request.markRendered());

        firstCompletion.run();

        assertEquals(Arrays.asList("first"), callbacks);
        assertEquals(1, request.pendingCount());
        long nextFrameId = request.markRendered();
        assertNotEquals(PresentedFrameRequest.NO_REQUEST_ID, nextFrameId);
        request.complete(nextFrameId).run();
        assertEquals(Arrays.asList("first", "next"), callbacks);
    }

    @Test
    public void staleGenerationConsumerRejectsWhileCurrentConsumerSucceeds() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        AtomicLong generation = new AtomicLong(2L);
        List<String> callbacks = new ArrayList<>();
        long stale = request.request(() -> {
            if (generation.get() == 1L) {
                callbacks.add("stale");
            }
        });
        long current = request.request(() -> {
            if (generation.get() == 2L) {
                callbacks.add("current");
            }
        });

        assertTrue(request.arm(stale));
        assertFalse(request.arm(current));
        request.complete(request.markRendered()).run();

        assertEquals(Arrays.asList("current"), callbacks);
    }

    @Test
    public void threeProductionConsumersBoundTheLogicalCallbackCapacity() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        request.request(() -> {});
        request.request(() -> {});
        request.request(() -> {});

        try {
            request.request(() -> {});
            fail("A fourth logical callback must not exceed production consumer capacity");
        } catch (IllegalStateException expected) {
            assertEquals(3, request.pendingCount());
        }
    }

    private static void assertTwoConsumersShareFrame(
            String firstName,
            String secondName) {
        PresentedFrameRequest request = new PresentedFrameRequest();
        List<String> callbacks = new ArrayList<>();
        long first = request.request(() -> callbacks.add(firstName));
        long second = request.request(() -> callbacks.add(secondName));

        assertTrue(request.arm(first));
        assertFalse(request.arm(second));
        long frameId = request.markRendered();
        request.complete(frameId).run();

        assertEquals(Arrays.asList(firstName, secondName), callbacks);
        assertEquals(0, request.pendingCount());
    }
}
