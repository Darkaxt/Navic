package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class DeckLeaseRegistryTest {
    @Test
    public void releaseReturnsTheListenerThatAcceptedTheGenerationExactlyOnce() {
        DeckLeaseRegistry registry = new DeckLeaseRegistry();
        PageSurfaceListener firstListener = new PageSurfaceListener() {};
        PageSurfaceListener laterListener = new PageSurfaceListener() {};

        assertTrue(registry.acquire(7L, firstListener));
        assertTrue(registry.hasOutstandingLeases());

        DeckLeaseRegistry.Lease firstRelease = registry.release(7L);
        assertSame(firstListener, firstRelease.getListener());
        assertNull(firstRelease.getReleaseReason());
        assertNull(registry.release(7L));
        assertNull(registry.ownerFor(7L));
        assertFalse(registry.hasOutstandingLeases());

        assertTrue(registry.acquire(8L, laterListener));
        assertSame(laterListener, registry.listenerFor(8L, firstListener));
    }

    @Test
    public void duplicateAcquisitionDoesNotTransferListenerOwnership() {
        DeckLeaseRegistry registry = new DeckLeaseRegistry();
        PageSurfaceListener owner = new PageSurfaceListener() {};
        PageSurfaceListener replacement = new PageSurfaceListener() {};

        assertTrue(registry.acquire(11L, owner));
        assertFalse(registry.acquire(11L, replacement));

        assertSame(owner, registry.listenerFor(11L, replacement));
        assertSame(owner, registry.ownerFor(11L));
        assertSame(owner, registry.release(11L).getListener());
    }

    @Test
    public void terminalDrainReturnsEveryOutstandingLeaseExactlyOnce() {
        DeckLeaseRegistry registry = new DeckLeaseRegistry();
        PageSurfaceListener first = new PageSurfaceListener() {};
        PageSurfaceListener second = new PageSurfaceListener() {};

        assertTrue(registry.acquire(21L, first));
        assertTrue(registry.acquire(22L, second));

        registry.markReleaseRequested(21L, DeckReleaseReason.EXPLICIT);
        List<DeckLeaseRegistry.Lease> leases =
                registry.releaseAll(DeckReleaseReason.DISPOSED);

        assertEquals(2, leases.size());
        assertEquals(21L, leases.get(0).getGenerationId());
        assertSame(first, leases.get(0).getListener());
        assertEquals(DeckReleaseReason.EXPLICIT, leases.get(0).getReleaseReason());
        assertEquals(22L, leases.get(1).getGenerationId());
        assertSame(second, leases.get(1).getListener());
        assertEquals(DeckReleaseReason.DISPOSED, leases.get(1).getReleaseReason());
        assertFalse(registry.hasOutstandingLeases());
        assertTrue(registry.releaseAll(DeckReleaseReason.DISPOSED).isEmpty());
        assertNull(registry.release(21L));
        assertNull(registry.release(22L));
    }

    @Test
    public void firstRequestedTerminalReasonWins() {
        DeckLeaseRegistry registry = new DeckLeaseRegistry();
        PageSurfaceListener listener = new PageSurfaceListener() {};
        assertTrue(registry.acquire(31L, listener));

        registry.markReleaseRequested(31L, DeckReleaseReason.REPLACED);
        registry.markReleaseRequested(31L, DeckReleaseReason.DISPOSED);
        DeckLeaseRegistry.Lease release = registry.release(31L);

        assertEquals(DeckReleaseReason.REPLACED, release.getReleaseReason());
        assertFalse(registry.hasOutstandingLeases());
    }

    @Test
    public void fixedCapacityRejectsGrowthUntilAnOwnedLeaseIsReleased() {
        DeckLeaseRegistry registry = new DeckLeaseRegistry(2);
        PageSurfaceListener listener = new PageSurfaceListener() {};

        assertEquals(2, registry.capacity());
        assertTrue(registry.hasCapacity());
        assertTrue(registry.acquire(41L, listener));
        assertTrue(registry.acquire(42L, listener));
        assertFalse(registry.hasCapacity());
        assertEquals(2, registry.size());
        assertTrue(registry.contains(41L));
        assertTrue(registry.contains(42L));
        assertFalse(registry.acquire(41L, new PageSurfaceListener() {}));
        assertFalse(registry.acquire(43L, listener));
        assertEquals(2, registry.size());

        assertSame(listener, registry.release(41L).getListener());
        assertTrue(registry.hasCapacity());
        assertFalse(registry.contains(41L));
        assertEquals(1, registry.size());
        assertTrue(registry.acquire(43L, listener));
        assertEquals(2, registry.size());
    }

    @Test
    public void releaseInFlightCountExcludesActiveAndPendingPlacements() {
        DeckLeaseRegistry registry = new DeckLeaseRegistry();
        PageSurfaceListener listener = new PageSurfaceListener() {};
        assertTrue(registry.acquire(51L, listener));
        assertTrue(registry.acquire(52L, listener));
        assertTrue(registry.acquire(53L, listener));
        registry.markReleaseRequested(51L, DeckReleaseReason.DISPOSED);
        registry.markReleaseRequested(52L, DeckReleaseReason.REPLACED);
        registry.markReleaseRequested(53L, DeckReleaseReason.REPLACED);

        assertEquals(1, registry.releaseInFlightCount(51L, 52L));
        assertEquals(2, registry.releaseInFlightCount(51L, -1L));
    }
}
