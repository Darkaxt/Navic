package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PageOverlayPendingLeaseTest {
    @Test
    public void fallbackDisposesAnUnclaimedValueExactlyOnce() {
        List<String> disposed = new ArrayList<>();
        PageOverlayPendingLease<String> lease =
                new PageOverlayPendingLease<>("overlay", disposed::add);

        lease.abandon();
        lease.abandon();

        assertEquals(List.of("overlay"), disposed);
        assertNull(lease.claim());
    }

    @Test
    public void glClaimPreventsFallbackFromDisposingTransferredValue() {
        List<String> disposed = new ArrayList<>();
        PageOverlayPendingLease<String> lease =
                new PageOverlayPendingLease<>("overlay", disposed::add);

        assertSame("overlay", lease.claim());
        lease.abandon();

        assertEquals(List.of(), disposed);
    }

    @Test
    public void queueFailureWithdrawsWithoutDisposingCallerOwnedValue() {
        List<String> disposed = new ArrayList<>();
        PageOverlayPendingLease<String> lease =
                new PageOverlayPendingLease<>("overlay", disposed::add);

        assertSame("overlay", lease.withdraw());
        lease.abandon();

        assertEquals(List.of(), disposed);
    }
}
