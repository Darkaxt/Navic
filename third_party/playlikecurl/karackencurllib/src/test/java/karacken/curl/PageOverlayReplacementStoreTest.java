package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class PageOverlayReplacementStoreTest {
    @Test
    public void atomicReplacementDisposesEveryPreviousRendererOwnedOverlay() {
        int[] disposalCount = {0};
        PageOverlayReplacementStore<String, Object> store =
                new PageOverlayReplacementStore<>(ignored -> disposalCount[0]++);
        Object firstLeft = new Object();
        Object firstRight = new Object();
        Object secondLeft = new Object();

        store.replace(overlays("left", firstLeft, "right", firstRight));
        store.replace(overlays("left", secondLeft));

        assertEquals(2, disposalCount[0]);
        assertEquals(1, store.size());
        assertSame(secondLeft, store.get("left"));
    }

    @Test
    public void clearDisposesTheCurrentRendererOwnedOverlayOnce() {
        int[] disposalCount = {0};
        PageOverlayReplacementStore<String, Object> store =
                new PageOverlayReplacementStore<>(ignored -> disposalCount[0]++);
        store.replace(overlays("page", new Object()));

        store.clear();
        store.clear();

        assertEquals(1, disposalCount[0]);
        assertEquals(0, store.size());
    }

    private static Map<String, Object> overlays(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return result;
    }
}
