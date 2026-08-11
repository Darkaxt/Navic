package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void dynamicOverlayReplacementPeakCountsOldAndIncomingMasks() {
        PortraitPageDeck<String> deck = portraitDeck(1, 1000, 1000);

        TextureBudget.Result result = TextureBudget.evaluate(
                deck,
                null,
                4096,
                16_000_000L,
                8_000_000L);

        assertEquals(
                RenderFailureReason.GPU_BUDGET_EXCEEDED,
                result.getFailureReason());
        assertEquals(20_000_000L, result.getRequiredBytes());
    }

    @Test
    public void twoIdentityDistinctLandscapeDecksReachTheStructuralSlotLimit() {
        LandscapePageDeck<String> active = landscapeDeck(31L, "active", -1);
        LandscapePageDeck<String> pending = landscapeDeck(32L, "pending", -1);

        assertEquals(
                TextureBudget.maximumTextureSlots(),
                TextureBudget.identityDistinctTextureCount(active, pending));
        assertEquals(24, TextureBudget.maximumTextureSlots());
    }

    @Test
    public void eachFillerRemovesBaseAndOverlayBytesAndSlots() {
        LandscapePageDeck<String> active = landscapeDeck(41L, "active", -1);
        LandscapePageDeck<String> pending = landscapeDeck(42L, "pending", 5);

        assertEquals(
                TextureBudget.maximumTextureSlots() - 2,
                TextureBudget.identityDistinctTextureCount(active, pending));
        TextureBudget.Result result =
                TextureBudget.evaluate(active, pending, 4096, Long.MAX_VALUE);
        assertNull(result.getFailureReason());
        assertEquals(22L * 10L * 20L * 4L, result.getRequiredBytes());
    }

    @Test
    public void everyAcceptedPortraitLandscapePairFitsTheStructuralSlotLimit() {
        PortraitPageDeck<String> portrait = portraitDeckWithOverlays(51L, "portrait");
        LandscapePageDeck<String> landscape = landscapeDeck(52L, "landscape", -1);
        LandscapePageDeck<String> secondLandscape =
                landscapeDeck(53L, "second-landscape", -1);

        assertTrue(TextureBudget.identityDistinctTextureCount(portrait, portrait)
                <= TextureBudget.maximumTextureSlots());
        assertTrue(TextureBudget.identityDistinctTextureCount(portrait, landscape)
                <= TextureBudget.maximumTextureSlots());
        assertTrue(TextureBudget.identityDistinctTextureCount(landscape, secondLandscape)
                <= TextureBudget.maximumTextureSlots());
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

    private static PortraitPageDeck<String> portraitDeckWithOverlays(
            long generationId,
            String prefix) {
        return new PortraitPageDeck<>(
                imageWithOverlay(generationId, prefix + "-previous", 0),
                imageWithOverlay(generationId, prefix + "-current", 1),
                imageWithOverlay(generationId, prefix + "-next", 2));
    }

    @SuppressWarnings("unchecked")
    private static LandscapePageDeck<String> landscapeDeck(
            long generationId,
            String prefix,
            int fillerIndex) {
        PageImage<String>[] pages = new PageImage[6];
        for (int index = 0; index < pages.length; index++) {
            String id = prefix + "-" + index;
            pages[index] = index == fillerIndex
                    ? PageImage.filler(
                            generationId,
                            id,
                            index,
                            10,
                            20,
                            id + "-borrowed",
                            0xFFFFFFFF)
                    : imageWithOverlay(generationId, id, index);
        }
        return new LandscapePageDeck<>(
                pages[0], pages[1], pages[2], pages[3], pages[4], pages[5]);
    }

    private static PageImage<String> imageWithOverlay(
            long generationId,
            String id,
            int ordinal) {
        return new PageImage<>(
                generationId,
                id,
                ordinal,
                10,
                20,
                id + "-base",
                id + "-overlay");
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
