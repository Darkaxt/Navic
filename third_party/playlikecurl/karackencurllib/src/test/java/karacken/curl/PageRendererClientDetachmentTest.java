package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PageRendererClientDetachmentTest {
    private static final long GENERATION = 301L;

    @Test
    public void overlayCleanupFailurePublishesOnlyAfterRealRendererReferencesDetach()
            throws Exception {
        AtomicInteger released = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        PageRenderer[] holder = new PageRenderer[1];
        PageRenderer renderer = new PageRenderer(
                events(released, failures, holder),
                (stage, index) -> {
                    if (stage == PageRenderer.ReleaseCleanupStage.OVERLAY && index == 0) {
                        throw new IllegalStateException("injected overlay cleanup failure");
                    }
                });
        holder[0] = renderer;
        installActiveDeck(renderer, true);
        assertTrue(retainsGeneration(renderer, GENERATION));

        renderer.releaseDeck(GENERATION, DeckReleaseReason.EXPLICIT);

        assertEquals(1, released.get());
        assertEquals(1, failures.get());
        assertFalse(retainsGeneration(renderer, GENERATION));
        assertEquals(0, dynamicOverlayCount(renderer));
        assertEquals(0, textureCount(renderer, GENERATION));
    }

    @Test
    public void preparationCleanupFailureUsesReleaseLifecycleAfterRendererRetainsDeck()
            throws Exception {
        AtomicInteger released = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        PageRenderer[] holder = new PageRenderer[1];
        PageRenderer renderer = new PageRenderer(
                events(released, failures, holder),
                (stage, index) -> {
                    if (stage == PageRenderer.ReleaseCleanupStage.OVERLAY && index == 0) {
                        throw new IllegalStateException("injected preparation cleanup failure");
                    }
                });
        holder[0] = renderer;
        installActiveDeck(renderer, true);
        setField(renderer, "maxTextureSize", 4096);

        renderer.prepareDeck(validPortraitDeck(), true);

        assertEquals(1, released.get());
        assertEquals(1, failures.get());
        assertFalse(retainsGeneration(renderer, GENERATION));
    }

    @Test
    public void textureCleanupFailurePublishesOnlyAfterWholeCacheDetaches()
            throws Exception {
        AtomicInteger released = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        PageRenderer[] holder = new PageRenderer[1];
        PageRenderer renderer = new PageRenderer(
                events(released, failures, holder),
                (stage, index) -> {
                    if (stage == PageRenderer.ReleaseCleanupStage.TEXTURE && index == 1) {
                        throw new IllegalStateException("injected texture cleanup failure");
                    }
                });
        holder[0] = renderer;
        installActiveDeck(renderer, false);
        assertTrue(textureCount(renderer, GENERATION) >= 3);

        renderer.releaseDeck(GENERATION, DeckReleaseReason.EXPLICIT);

        assertEquals(1, released.get());
        assertEquals(1, failures.get());
        assertFalse(retainsGeneration(renderer, GENERATION));
        assertEquals(0, textureCount(renderer, GENERATION));
    }

    @Test
    public void terminalAbandonDetachesClientReferencesWithoutGlCleanup()
            throws Exception {
        AtomicInteger released = new AtomicInteger();
        AtomicInteger cleanupCalls = new AtomicInteger();
        PageRenderer renderer = new PageRenderer(
                events(released, new AtomicInteger(), new PageRenderer[1]),
                (stage, index) -> cleanupCalls.incrementAndGet());
        installActiveDeck(renderer, true);

        assertTrue(renderer.terminallyAbandonDeck(GENERATION));

        assertFalse(retainsGeneration(renderer, GENERATION));
        assertEquals(0, dynamicOverlayCount(renderer));
        assertEquals(0, textureCount(renderer, GENERATION));
        assertEquals(0, cleanupCalls.get());
        assertFalse(renderer.terminallyAbandonDeck(GENERATION));
        assertEquals(0, released.get());
    }

    private static PageRenderer.Events events(
            AtomicInteger released,
            AtomicInteger failures,
            PageRenderer[] holder) {
        return new PageRenderer.Events() {
            @Override public void onCapabilitiesAvailable(RenderCapabilities capabilities) {}
            @Override public void onDeckPrepared(long generationId) {}
            @Override
            public void onDeckReleased(long generationId, DeckReleaseReason reason) {
                assertEquals(GENERATION, generationId);
                if (holder[0] != null) {
                    try {
                        assertFalse(retainsGeneration(holder[0], generationId));
                    } catch (ReflectiveOperationException failure) {
                        throw new AssertionError(failure);
                    }
                }
                released.incrementAndGet();
            }
            @Override public void onPageOverlayUpdateCompleted(long generationId, boolean applied) {}
            @Override public void onRenderFailure(RenderFailure failure) { failures.incrementAndGet(); }
        };
    }

    private static void installActiveDeck(PageRenderer renderer, boolean withOverlay)
            throws Exception {
        PageImage<Bitmap> previous = page("previous", 0);
        PageImage<Bitmap> current = page("current", 1);
        PageImage<Bitmap> next = page("next", 2);
        PortraitPageDeck<Bitmap> deck = new PortraitPageDeck<>(previous, current, next);
        setField(renderer, "activeDeck", deck);
        renderer.applyActiveDeck(deck);
        renderer.retainDeckTextures();
        if (withOverlay) {
            Bitmap overlayBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
            overlayBitmap.setPremultiplied(true);
            PageOverlayImage<Bitmap> overlay = new PageOverlayImage<>(1, overlayBitmap);
            Class<?> type = Class.forName("karacken.curl.PageRenderer$DynamicPageOverlayTexture");
            Constructor<?> constructor = type.getDeclaredConstructor(
                    PageRenderer.class, PageImage.class, PageOverlayImage.class);
            constructor.setAccessible(true);
            Object texture = constructor.newInstance(renderer, current, overlay);
            @SuppressWarnings("unchecked")
            PageOverlayReplacementStore<String, Object> store =
                    (PageOverlayReplacementStore<String, Object>) field(
                            renderer, "dynamicPageOverlays");
            Map<String, Object> replacement = new LinkedHashMap<>();
            replacement.put(current.identityKey(), texture);
            store.replace(replacement);
        }
    }

    private static PortraitPageDeck<Bitmap> validPortraitDeck() {
        PageDisplayRect rect = new PageDisplayRect(0, 0, 4, 4);
        PageMaterial material = new PageMaterial(
                GENERATION,
                0xFFFFFFFF,
                0xFFFFFFFF,
                0xFF000000,
                0xFFFFFFFF,
                PageLeafRole.FULL,
                rect,
                rect,
                null,
                1);
        return new PortraitPageDeck<>(
                validPage("valid-previous", 0, rect, material),
                validPage("valid-current", 1, rect, material),
                validPage("valid-next", 2, rect, material));
    }

    private static PageImage<Bitmap> validPage(
            String identity,
            int ordinal,
            PageDisplayRect rect,
            PageMaterial material) {
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(false);
        bitmap.eraseColor(0xFFFFFFFF);
        return new PageImage<>(
                GENERATION,
                identity,
                ordinal,
                4,
                4,
                rect,
                bitmap,
                material);
    }

    private static PageImage<Bitmap> page(String identity, int ordinal) {
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0xFFFFFFFF);
        return new PageImage<>(GENERATION, identity, ordinal, 4, 4, bitmap);
    }

    private static boolean retainsGeneration(PageRenderer renderer, long generationId)
            throws ReflectiveOperationException {
        PageDeck<?> active = (PageDeck<?>) field(renderer, "activeDeck");
        PageDeck<?> replacement = (PageDeck<?>) field(renderer, "replacementDeck");
        if (generation(active) == generationId || generation(replacement) == generationId) {
            return true;
        }
        String[] pageFields = {
                "portraitLeftResource", "portraitFrontResource", "portraitRightResource",
                "spreadPreviousLeftResource", "spreadPreviousRightResource",
                "spreadCurrentLeftResource", "spreadCurrentRightResource",
                "spreadNextLeftResource", "spreadNextRightResource"
        };
        for (String name : pageFields) {
            PageImage<?> page = (PageImage<?>) field(renderer, name);
            if (page != null && page.getGenerationId() == generationId) return true;
        }
        return dynamicOverlayCount(renderer) != 0 || textureCount(renderer, generationId) != 0;
    }

    private static long generation(PageDeck<?> deck) {
        return deck == null ? -1L : deck.getGenerationId();
    }

    private static int dynamicOverlayCount(PageRenderer renderer)
            throws ReflectiveOperationException {
        return ((PageOverlayReplacementStore<?, ?>) field(renderer, "dynamicPageOverlays")).size();
    }

    private static int textureCount(PageRenderer renderer, long generationId)
            throws ReflectiveOperationException {
        @SuppressWarnings("unchecked")
        Map<String, Object> cache = (Map<String, Object>) field(renderer, "textureCache");
        int count = 0;
        for (Object texture : cache.values()) {
            PageImage<?> page = (PageImage<?>) field(texture, "page");
            if (page.getGenerationId() == generationId) count += 1;
        }
        return count;
    }

    private static Object field(Object target, String name)
            throws ReflectiveOperationException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

}
