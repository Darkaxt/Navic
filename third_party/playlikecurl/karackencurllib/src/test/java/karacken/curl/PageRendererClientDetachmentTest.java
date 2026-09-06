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

    @Test
    public void selectedPredecessorSurvivesAutomaticReplacementBeforeCandidatePreparation()
            throws Exception {
        AtomicInteger released = new AtomicInteger();
        PageRenderer[] holder = new PageRenderer[1];
        PageRenderer renderer = new PageRenderer(events(released, new AtomicInteger(), holder));
        holder[0] = renderer;
        setField(renderer, "maxTextureSize", 4096);
        renderer.prepareDeck(validPortraitDeck(), true);
        renderer.setSelectedFrameGeneration(GENERATION);

        // Same renderer command order as PageSurfaceView's accepted idle offer.
        renderer.releaseDeck(GENERATION, DeckReleaseReason.REPLACED);
        renderer.prepareDeck(validPortraitDeck(303L), true);

        assertEquals("Submission is not a presentation receipt", 0, released.get());
        assertEquals(3, textureCount(renderer, GENERATION));
        assertEquals(GENERATION, generation((PageDeck<?>) field(renderer, "activeDeck")));
    }

    @Test
    public void selectedDrawableDoesNotChangeOnPreparationAlone() throws Exception {
        PageRenderer renderer = new PageRenderer(
                events(new AtomicInteger(), new AtomicInteger(), new PageRenderer[1]));
        setField(renderer, "maxTextureSize", 4096);
        renderer.prepareDeck(validPortraitDeck(), true);
        renderer.setSelectedFrameGeneration(GENERATION);

        renderer.prepareDeck(validPortraitDeck(303L), true);

        assertEquals("Fresh preparation must stage, not replace the selected draw owner",
                GENERATION, generation((PageDeck<?>) field(renderer, "activeDeck")));
        assertEquals(GENERATION,
                ((PageImage<?>) field(renderer, "portraitFrontResource")).getGenerationId());
        assertEquals(3, textureCount(renderer, GENERATION));
        assertEquals(3, textureCount(renderer, 303L));
    }

    @Test
    public void ordinaryUnselectedAutomaticReplacementStillReleasesImmediately() throws Exception {
        AtomicInteger released = new AtomicInteger();
        PageRenderer[] holder = new PageRenderer[1];
        PageRenderer renderer = new PageRenderer(events(released, new AtomicInteger(), holder));
        holder[0] = renderer;
        setField(renderer, "maxTextureSize", 4096);
        renderer.prepareDeck(validPortraitDeck(), true);

        renderer.releaseDeck(GENERATION, DeckReleaseReason.REPLACED);
        renderer.prepareDeck(validPortraitDeck(303L), true);

        assertEquals(1, released.get());
        assertFalse(retainsGeneration(renderer, GENERATION));
        assertEquals(303L, generation((PageDeck<?>) field(renderer, "activeDeck")));
        assertEquals(3, textureCount(renderer, 303L));
    }

    @Test
    public void selectedPredecessorCountsAgainstRejectedSuccessorBudget() throws Exception {
        Map<Long, Integer> released = new LinkedHashMap<>();
        AtomicInteger budgetFailures = new AtomicInteger();
        PageRenderer renderer = new PageRenderer(recordingEvents(released, budgetFailures));
        setField(renderer, "maxTextureSize", 4096);
        renderer.setGpuBudgetBytes(256L); // One 192-byte deck fits; two do not.
        renderer.prepareDeck(validPortraitDeck(), true);
        renderer.setSelectedFrameGeneration(GENERATION);

        renderer.releaseDeck(GENERATION, DeckReleaseReason.REPLACED);
        renderer.prepareDeck(validPortraitDeck(303L), true);

        assertEquals(1, budgetFailures.get());
        assertEquals(Integer.valueOf(1), released.get(303L));
        assertFalse(released.containsKey(GENERATION));
        assertEquals(GENERATION, generation((PageDeck<?>) field(renderer, "activeDeck")));
        assertEquals(3, textureCount(renderer, GENERATION));
        assertEquals(0, textureCount(renderer, 303L));
        renderer.dispose();
        assertEquals(Integer.valueOf(1), released.get(GENERATION));
        assertEquals(0, renderer.textureCount());
    }

    @Test
    public void selectedAcceptedReleaseDrainsOnceWhenSelectionClears() throws Exception {
        Map<Long, Integer> released = new LinkedHashMap<>();
        PageRenderer renderer = new PageRenderer(recordingEvents(released, new AtomicInteger()));
        setField(renderer, "maxTextureSize", 4096);
        renderer.prepareDeck(validPortraitDeck(), true);
        renderer.setSelectedFrameGeneration(GENERATION);
        renderer.releaseDeck(GENERATION, DeckReleaseReason.REPLACED);
        renderer.prepareDeck(validPortraitDeck(303L), true);
        assertFalse(released.containsKey(GENERATION));

        renderer.setSelectedFrameGeneration(-1L);
        renderer.setSelectedFrameGeneration(-1L);

        assertEquals(Integer.valueOf(1), released.get(GENERATION));
        assertEquals(0, textureCount(renderer, GENERATION));
        renderer.dispose();
        renderer.dispose();
        assertEquals(Integer.valueOf(1), released.get(GENERATION));
        assertEquals(Integer.valueOf(1), released.get(303L));
        assertEquals(0, renderer.textureCount());
    }

    @Test
    public void candidateActivationRetainsDeformationAndOverlayUntilConditionalRollback()
            throws Exception {
        Map<Long, Integer> released = new LinkedHashMap<>();
        PageRenderer renderer = new PageRenderer(recordingEvents(released, new AtomicInteger()));
        setField(renderer, "maxTextureSize", 4096);
        installActiveDeck(renderer, true);
        Object model = renderer.getPortraitModel();
        Object overlays = field(renderer, "dynamicPageOverlays");
        renderer.getPortraitModel().getFrontPage().setCurlPosition(0.4f);
        renderer.setSelectedFrameGeneration(GENERATION);
        renderer.releaseDeck(GENERATION, DeckReleaseReason.REPLACED);
        renderer.prepareDeck(validPortraitDeck(303L), true);

        renderer.activateDeck(303L);

        assertEquals(303L, generation((PageDeck<?>) field(renderer, "activeDeck")));
        assertEquals(GENERATION, generation((PageDeck<?>) field(renderer, "replacementDeck")));
        assertFalse(released.containsKey(GENERATION));
        assertEquals(3, textureCount(renderer, GENERATION));
        assertTrue(renderer.restoreSelectedFrame(303L));
        assertTrue(model == renderer.getPortraitModel());
        assertTrue(overlays == field(renderer, "dynamicPageOverlays"));
        assertEquals(0.4f, renderer.getPortraitModel().getFrontPage().getCurlPosition(), 0f);
        assertEquals(1, dynamicOverlayCount(renderer));
        renderer.activateDeck(303L);
        renderer.setSelectedFrameGeneration(303L);
        assertFalse("Obsolete cancellation must not restore retired authority",
                renderer.restoreSelectedFrame(303L));
        assertEquals(303L, generation((PageDeck<?>) field(renderer, "activeDeck")));
        assertEquals(Integer.valueOf(1), released.get(GENERATION));
        renderer.dispose();
        assertEquals(Integer.valueOf(1), released.get(303L));
        assertEquals(0, renderer.textureCount());
    }

    @Test
    public void interveningSubmissionCannotEvictSelectedReplacementSlot() throws Exception {
        Map<Long, Integer> released = new LinkedHashMap<>();
        AtomicInteger capacityFailures = new AtomicInteger();
        PageRenderer renderer = new PageRenderer(recordingEvents(released, capacityFailures));
        setField(renderer, "maxTextureSize", 4096);
        renderer.prepareDeck(validPortraitDeck(), true);
        renderer.setSelectedFrameGeneration(GENERATION);
        renderer.releaseDeck(GENERATION, DeckReleaseReason.REPLACED);
        renderer.prepareDeck(validPortraitDeck(303L), true);
        renderer.activateDeck(303L);

        renderer.prepareDeck(validPortraitDeck(304L), true);

        assertEquals(1, capacityFailures.get());
        assertEquals(Integer.valueOf(1), released.get(304L));
        assertFalse(released.containsKey(GENERATION));
        assertEquals(303L, generation((PageDeck<?>) field(renderer, "activeDeck")));
        assertEquals(GENERATION, generation((PageDeck<?>) field(renderer, "replacementDeck")));
        assertEquals(6, renderer.textureCount());
        // The actual automatic replacement order can free the candidate, not the predecessor.
        renderer.releaseDeck(303L, DeckReleaseReason.REPLACED);
        assertEquals(GENERATION, generation((PageDeck<?>) field(renderer, "activeDeck")));
        renderer.prepareDeck(validPortraitDeck(305L), true);
        assertEquals(305L, generation((PageDeck<?>) field(renderer, "replacementDeck")));
        renderer.dispose();
        assertEquals(Integer.valueOf(1), released.get(GENERATION));
        assertEquals(Integer.valueOf(1), released.get(303L));
        assertEquals(Integer.valueOf(1), released.get(305L));
        assertEquals(0, renderer.textureCount());
    }

    @Test
    @Config(shadows = QueuedSurfaceShadow.class)
    public void cancelledAndSupersededCandidateQueuesCannotActivateOrPublish() throws Exception {
        try (CandidateSurfaceFixture fixture = new CandidateSurfaceFixture()) {
            fixture.prepare();
            AtomicInteger frames = new AtomicInteger();
            long cancelled = fixture.surface.requestNativePagePresentedFrame(303L, frames::incrementAndGet);
            assertTrue(fixture.surface.cancelPresentedFrameRequest(cancelled));
            fixture.drain();
            assertTrue(fixture.renderer.hasActiveFrame(GENERATION));
            assertEquals(PresentedFrameRequest.NO_REQUEST_ID, fixture.requests().markRendered());

            long obsolete = fixture.surface.requestNativePagePresentedFrame(303L, frames::incrementAndGet);
            long current = fixture.surface.requestNativePagePresentedFrame(303L, frames::incrementAndGet);
            assertFalse(fixture.surface.cancelPresentedFrameRequest(obsolete));
            fixture.drain();
            assertTrue(fixture.renderer.hasActiveFrame(303L));
            assertEquals(3, textureCount(fixture.renderer, GENERATION));
            assertTrue(fixture.surface.cancelPresentedFrameRequest(current));
            fixture.drain();
            assertTrue(fixture.renderer.hasActiveFrame(GENERATION));
            assertEquals(PresentedFrameRequest.NO_REQUEST_ID, fixture.requests().markRendered());
            assertEquals(0, frames.get());
        }
    }

    @Test
    @Config(shadows = QueuedSurfaceShadow.class)
    public void candidateActivationIsNotConfusedWithFirstSharedFrameWaiter() throws Exception {
        try (CandidateSurfaceFixture fixture = new CandidateSurfaceFixture()) {
            fixture.prepare();
            AtomicInteger genericFrames = new AtomicInteger();
            AtomicInteger nativeFrames = new AtomicInteger();
            fixture.surface.requestNextPresentedFrame(genericFrames::incrementAndGet);
            fixture.surface.requestNativePagePresentedFrame(303L, () -> {
                assertTrue(fixture.renderer.hasActiveFrame(303L));
                fixture.surface.setSelectedFrameGeneration(303L);
                nativeFrames.incrementAndGet();
            });
            fixture.drain();
            assertTrue(fixture.renderer.hasActiveFrame(303L));
            assertFalse(fixture.released.containsKey(GENERATION));
            long rendered = fixture.requests().markRendered();
            fixture.requests().complete(rendered).run();
            fixture.drain();
            assertEquals(1, genericFrames.get());
            assertEquals(1, nativeFrames.get());
            assertEquals(Integer.valueOf(1), fixture.released.get(GENERATION));
            assertTrue(fixture.renderer.hasActiveFrame(303L));
            assertEquals(0, fixture.surface.getPendingCallbackCount());
        }
    }

    @org.robolectric.annotation.Implements(android.opengl.GLSurfaceView.class)
    public static class QueuedSurfaceShadow extends org.robolectric.shadows.ShadowGLSurfaceView {
        final java.util.ArrayDeque<Runnable> queued = new java.util.ArrayDeque<>();
        @org.robolectric.annotation.Implementation
        public void queueEvent(Runnable action) { queued.add(action); }
        @org.robolectric.annotation.Implementation
        public void requestRender() {}
        void drain() { while (!queued.isEmpty()) queued.remove().run(); }
    }

    private static final class CandidateSurfaceFixture implements AutoCloseable {
        final org.robolectric.android.controller.ActivityController<android.app.Activity> activity =
                org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup();
        final PageSurfaceView surface = new PageSurfaceView(activity.get());
        final PageRenderer renderer;
        final QueuedSurfaceShadow shadow;
        final Map<Long, Integer> released = new LinkedHashMap<>();
        CandidateSurfaceFixture() throws Exception {
            activity.get().setContentView(surface);
            shadow = org.robolectric.shadow.api.Shadow.extract(surface);
            renderer = (PageRenderer) field(surface, "renderer");
            surface.registerMainTerminalExecutor(action -> { action.run(); return true; });
            surface.setPageSurfaceListener(new PageSurfaceListener() {
                @Override public void onDeckReleased(long generationId, DeckReleaseReason reason) {
                    released.put(generationId, released.getOrDefault(generationId, 0) + 1);
                }
            });
            surface.attach();
            setField(renderer, "maxTextureSize", 4096);
            setField(renderer, "glReady", true);
            renderer.setGpuBudgetBytes(1024L);
            drain();
        }
        void prepare() {
            assertTrue(surface.submitDeckWithResult(validPortraitDeck()).getStatus()
                    == PageSurfaceDeckSubmissionResult.Status.ACCEPTED);
            drain();
            surface.setSelectedFrameGeneration(GENERATION);
            drain();
            assertTrue(surface.submitDeckWithResult(validPortraitDeck(303L)).getStatus()
                    == PageSurfaceDeckSubmissionResult.Status.ACCEPTED);
            drain();
            assertTrue(renderer.hasActiveFrame(GENERATION));
        }
        PresentedFrameRequest requests() throws Exception {
            return (PresentedFrameRequest) field(surface, "presentedFrameRequest");
        }
        void drain() {
            shadow.drain();
            org.robolectric.shadows.ShadowLooper.runUiThreadTasks();
        }
        @Override public void close() {
            surface.dispose();
            drain();
            activity.pause().stop().destroy();
            drain();
            assertEquals(0, renderer.textureCount());
            assertEquals(0, surface.getPendingCallbackCount());
        }
    }

    private static PageRenderer.Events recordingEvents(
            Map<Long, Integer> released, AtomicInteger budgetFailures) {
        return new PageRenderer.Events() {
            @Override public void onCapabilitiesAvailable(RenderCapabilities capabilities) {}
            @Override public void onDeckPrepared(long generationId) {}
            @Override public void onDeckReleased(long generationId, DeckReleaseReason reason) {
                released.put(generationId, released.getOrDefault(generationId, 0) + 1);
            }
            @Override public void onPageOverlayUpdateCompleted(long generationId, boolean applied) {}
            @Override public void onRenderFailure(RenderFailure failure) {
                assertEquals(RenderFailureReason.GPU_BUDGET_EXCEEDED, failure.getReason());
                budgetFailures.incrementAndGet();
            }
        };
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
        PortraitPageDeck<Bitmap> deck = validPortraitDeck();
        PageImage<Bitmap> current = deck.getCurrent();
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
        return validPortraitDeck(GENERATION);
    }

    private static PortraitPageDeck<Bitmap> validPortraitDeck(long generationId) {
        PageDisplayRect rect = new PageDisplayRect(0, 0, 4, 4);
        PageMaterial material = new PageMaterial(
                generationId,
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
                material.getGenerationId(),
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
