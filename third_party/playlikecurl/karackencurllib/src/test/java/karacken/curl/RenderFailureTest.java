package karacken.curl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RenderFailureTest {
    @Test
    public void carriesTextureLimitMetadataForQualityDowngrade() {
        RenderFailure failure = new RenderFailure(
                7,
                true,
                RenderFailureReason.TEXTURE_TOO_LARGE,
                "Texture is too large",
                null,
                5000,
                3200,
                4096,
                64L * 1024L * 1024L,
                96L * 1024L * 1024L);

        assertEquals(5000, failure.getRequestedWidthPx());
        assertEquals(3200, failure.getRequestedHeightPx());
        assertEquals(4096, failure.getMaxTextureSize());
        assertEquals(64L * 1024L * 1024L, failure.getRequiredBytes());
        assertEquals(96L * 1024L * 1024L, failure.getGpuBudgetBytes());
    }
}
