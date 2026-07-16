package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class RenderCapabilitiesTest {
    @Test
    public void exposesTextureAndBudgetLimits() {
        RenderCapabilities capabilities =
                new RenderCapabilities(4096, 96L * 1024L * 1024L);

        assertEquals(4096, capabilities.getMaxTextureSize());
        assertEquals(96L * 1024L * 1024L, capabilities.getGpuBudgetBytes());
        assertEquals(4, capabilities.getBytesPerPixel());
    }

    @Test
    public void rejectsInvalidLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderCapabilities(0, 1024));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RenderCapabilities(4096, 0));
    }
}
