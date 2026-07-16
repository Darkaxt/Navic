package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TextureBudgetTest {
    @Test
    public void acceptsUniquePagesWithinTextureAndByteLimits() {
        PortraitPageDeck<String> deck = portraitDeck(1, 1024, 1536);

        TextureBudget.Result result =
                TextureBudget.evaluate(deck, null, 4096, 32L * 1024L * 1024L);

        assertNull(result.getFailureReason());
        assertEquals(3L * 1024L * 1536L * 4L, result.getRequiredBytes());
    }

    @Test
    public void countsBoundaryDuplicatesOnlyOnce() {
        PageImage<String> first = image(1, "first", 0, 1000, 1000);
        PageImage<String> second = image(1, "second", 1, 1000, 1000);
        PortraitPageDeck<String> deck = new PortraitPageDeck<>(first, first, second);

        TextureBudget.Result result =
                TextureBudget.evaluate(deck, null, 4096, 16L * 1024L * 1024L);

        assertNull(result.getFailureReason());
        assertEquals(8_000_000L, result.getRequiredBytes());
    }

    @Test
    public void rejectsPageAboveGlTextureLimit() {
        PortraitPageDeck<String> deck = portraitDeck(1, 4097, 2048);

        TextureBudget.Result result =
                TextureBudget.evaluate(deck, null, 4096, Long.MAX_VALUE);

        assertEquals(RenderFailureReason.TEXTURE_TOO_LARGE, result.getFailureReason());
        assertEquals(4097, result.getRequestedWidthPx());
        assertEquals(2048, result.getRequestedHeightPx());
        assertEquals(4096, result.getMaxTextureSize());
    }

    @Test
    public void rejectsCombinedActiveAndPendingGpuBudget() {
        PortraitPageDeck<String> active = portraitDeck(1, 1024, 1024);
        PortraitPageDeck<String> pending = portraitDeck(2, 1024, 1024);

        TextureBudget.Result result =
                TextureBudget.evaluate(active, pending, 4096, 16L * 1024L * 1024L);

        assertEquals(
                RenderFailureReason.GPU_BUDGET_EXCEEDED,
                result.getFailureReason());
        assertEquals(24L * 1024L * 1024L, result.getRequiredBytes());
        assertEquals(16L * 1024L * 1024L, result.getGpuBudgetBytes());
    }

    @Test
    public void overlayTextureCountsAgainstSameBoundedBudget() {
        PageImage<String> previous = image(21, "previous", 0, 1000, 1000);
        PageImage<String> current = new PageImage<>(
                21,
                "current",
                1,
                1000,
                1000,
                "current-base",
                "current-overlay");
        PageImage<String> next = image(21, "next", 2, 1000, 1000);

        TextureBudget.Result result = TextureBudget.evaluate(
                new PortraitPageDeck<>(previous, current, next),
                null,
                4096,
                32L * 1024L * 1024L);

        assertNull(result.getFailureReason());
        assertEquals(16_000_000L, result.getRequiredBytes());
    }

    private static PortraitPageDeck<String> portraitDeck(
            long generationId,
            int width,
            int height) {
        return new PortraitPageDeck<>(
                image(generationId, "previous", 0, width, height),
                image(generationId, "current", 1, width, height),
                image(generationId, "next", 2, width, height));
    }

    private static PageImage<String> image(
            long generationId,
            String id,
            int ordinal,
            int width,
            int height) {
        return new PageImage<>(
                generationId,
                id,
                ordinal,
                width,
                height,
                id);
    }
}
