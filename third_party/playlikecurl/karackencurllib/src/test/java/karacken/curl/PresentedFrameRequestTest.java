package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class PresentedFrameRequestTest {
    @Test
    public void callbackCompletesOnlyAfterItsArmedFrameRenders() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        Runnable callback = () -> {};
        long requestId = request.request(callback);

        assertEquals(1, request.pendingCount());
        assertEquals(PresentedFrameRequest.NO_REQUEST_ID, request.markRendered());
        assertNull(request.complete(requestId));
        assertTrue(request.arm(requestId));
        assertEquals(requestId, request.markRendered());
        assertEquals(PresentedFrameRequest.NO_REQUEST_ID, request.markRendered());
        assertSame(callback, request.complete(requestId));
        assertEquals(0, request.pendingCount());
        assertNull(request.complete(requestId));
    }

    @Test
    public void cancellationSuppressesArmingAndLateCompletion() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        long requestId = request.request(() -> {});

        assertTrue(request.cancel(requestId));
        assertFalse(request.arm(requestId));
        assertEquals(PresentedFrameRequest.NO_REQUEST_ID, request.markRendered());
        assertNull(request.complete(requestId));
        assertEquals(0, request.pendingCount());
    }

    @Test
    public void onePendingRequestOwnsTheBoundedCallbackSlot() {
        PresentedFrameRequest request = new PresentedFrameRequest();
        request.request(() -> {});

        try {
            request.request(() -> {});
            fail("A second callback must not replace the pending owner");
        } catch (IllegalStateException expected) {
            assertEquals(1, request.pendingCount());
        }

        request.cancelAll();
        assertEquals(0, request.pendingCount());
        assertTrue(request.request(() -> {}) > 0L);
    }
}
