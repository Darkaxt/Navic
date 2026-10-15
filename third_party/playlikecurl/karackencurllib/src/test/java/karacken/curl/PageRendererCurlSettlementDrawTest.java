package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/** Original renderer control flow only: controlled GLES responses are not EGL/display proof. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = {
        PageRendererCurlSettlementDrawTest.ControlledGles.class,
        PageRendererCurlSettlementDrawTest.ControlledUploads.class})
public class PageRendererCurlSettlementDrawTest {
    private final List<Long> prepared = new ArrayList<>();
    private final List<Long> released = new ArrayList<>();
    private PageRenderer renderer;
    private int failures;

    @Before
    public void setUp() {
        ControlledGles.reset();
        renderer = new PageRenderer(new PageRenderer.Events() {
            @Override public void onCapabilitiesAvailable(RenderCapabilities capabilities) {}
            @Override public void onDeckPrepared(long generationId) { prepared.add(generationId); }
            @Override public void onDeckReleased(long generationId, DeckReleaseReason reason) {
                released.add(generationId);
            }
            @Override public void onPageOverlayUpdateCompleted(long generationId, boolean applied) {}
            @Override public void onRenderFailure(RenderFailure failure) { failures++; }
        });
    }

    @Test
    public void initializationAndOrdinaryEmptyDrawDoNotInventCompletePageDraw() {
        assertFalse(renderer.drawFrame(null));
        renderer.onSurfaceCreated(null, null);
        renderer.onSurfaceChanged(null, 80, 60);
        assertTrue(renderer.getRendererGeneration() > 0L);
        assertFalse(renderer.drawFrame(null));
        assertEquals(0, ControlledGles.draws);
        assertEquals(0, failures);
    }

    @Test
    public void actualPrepareUploadsEveryBaseAndDeclaredOverlayBeforeActualDraw() {
        initialize();
        renderer.prepareDeck(deck(11L, true), true);
        assertEquals(List.of(11L), prepared);
        assertEquals(6, ControlledGles.uploads);
        assertEquals(0, ControlledGles.draws);
        assertTrue(renderer.drawFrame(null));
        assertEquals(3, ControlledGles.draws);
        assertEquals(0, failures);
        renderer.dispose();
        assertEquals(List.of(11L), released);
    }

    @Test
    public void failedDeclaredOverlayUploadCannotPublishPreparationOrCompleteDraw() {
        initialize();
        ControlledGles.failUploadAt = 2;
        renderer.prepareDeck(deck(11L, true), true);
        assertTrue(prepared.isEmpty());
        assertEquals(1, failures);
        assertEquals(List.of(11L), released);
        assertFalse(renderer.drawFrame(null));
        assertEquals(0, ControlledGles.draws);
    }

    @Test
    public void partialOriginalDrawExceptionIsNotSuccessfulDraw() {
        initialize();
        renderer.prepareDeck(deck(11L, false), true);
        ControlledGles.failDrawAt = 2;
        assertFalse(renderer.drawFrame(null));
        assertEquals(2, ControlledGles.draws);
        assertEquals(1, failures);
        ControlledGles.failDrawAt = -1;
        assertTrue(renderer.drawFrame(null));
        assertEquals(5, ControlledGles.draws);
    }

    @Test
    public void preparationDoesNotSelectSuccessorAndConditionalRollbackUsesOriginalSlots() {
        initialize();
        renderer.prepareDeck(deck(11L, false), true);
        renderer.setSelectedFrameGeneration(11L);
        assertTrue(renderer.hasRetainedSelectedFrame(11L));
        assertFalse(renderer.hasRetainedSelectedFrame(29L));
        renderer.releaseDeck(11L, DeckReleaseReason.REPLACED);
        renderer.prepareDeck(deck(29L, false), true);
        assertEquals(List.of(11L, 29L), prepared);
        assertTrue(renderer.hasActiveFrame(11L));
        assertTrue(renderer.hasActiveFrame(11L, 4));
        assertFalse(renderer.hasActiveFrame(11L, 5));
        assertFalse(renderer.hasActiveFrame(29L));
        assertFalse(renderer.hasActiveFrame(29L, 4));
        assertTrue(released.isEmpty());
        assertTrue(renderer.drawFrame(null));

        renderer.activateDeck(29L);
        assertTrue(renderer.hasActiveFrame(29L));
        assertTrue(renderer.drawFrame(null));
        assertTrue(renderer.restoreSelectedFrame(29L));
        assertTrue(renderer.hasActiveFrame(11L));
        assertTrue(renderer.drawFrame(null));
        assertTrue(released.isEmpty());

        renderer.activateDeck(29L);
        renderer.setSelectedFrameGeneration(29L);
        assertFalse(renderer.hasRetainedSelectedFrame(11L));
        assertTrue(renderer.hasRetainedSelectedFrame(29L));
        assertEquals(List.of(11L), released);
        assertFalse(renderer.restoreSelectedFrame(29L));
        assertTrue(renderer.hasActiveFrame(29L));
        renderer.dispose();
        assertEquals(List.of(11L, 29L), released);
    }

    @Test
    public void contextRecreationReuploadsOriginalMaterialsBeforeAnotherSuccessfulDraw() {
        initialize();
        renderer.prepareDeck(deck(11L, true), true);
        long context = renderer.getRendererGeneration();
        assertTrue(renderer.drawFrame(null));
        renderer.onSurfaceCreated(null, null);
        assertTrue(renderer.getRendererGeneration() > context);
        assertEquals(12, ControlledGles.uploads);
        assertEquals(List.of(11L, 11L), prepared);
        assertTrue(renderer.drawFrame(null));
        assertEquals(0, failures);
    }

    private void initialize() {
        renderer.onSurfaceCreated(null, null);
        renderer.onSurfaceChanged(null, 80, 60);
        assertTrue("CURL_FIXTURE_ORIGINAL_CONTEXT", renderer.getRendererGeneration() > 0L);
        assertEquals(0, failures);
    }

    private static PortraitPageDeck<Bitmap> deck(long generation, boolean overlays) {
        PageDisplayRect rect = new PageDisplayRect(0, 0, 80, 60);
        PageMaterial material = new PageMaterial(generation,
                0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFFFFFFFF,
                PageLeafRole.FULL, rect, rect, null, 1);
        return new PortraitPageDeck<>(page(material, 3, overlays),
                page(material, 4, overlays), page(material, 5, overlays));
    }

    private static PageImage<Bitmap> page(PageMaterial material, int ordinal, boolean overlay) {
        Bitmap base = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        base.setHasAlpha(false);
        Bitmap layer = overlay ? Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) : null;
        if (layer != null) {
            layer.setHasAlpha(true);
            layer.setPremultiplied(true);
        }
        return new PageImage<>(material.getGenerationId(), "synthetic-curl-leaf-" + ordinal,
                ordinal, 4, 4, material.getDisplayRect(), base, layer, material);
    }

    @Implements(value = GLES20.class, callThroughByDefault = false)
    public static class ControlledGles {
        static int draws;
        static int uploads;
        static int failDrawAt;
        static int failUploadAt;
        private static int nextObject;
        private static int error;

        static void reset() {
            draws = 0; uploads = 0; failDrawAt = -1; failUploadAt = -1;
            nextObject = 1; error = GLES20.GL_NO_ERROR;
        }
        @Implementation protected static int glCreateShader(int type) { return nextObject++; }
        @Implementation protected static int glCreateProgram() { return nextObject++; }
        @Implementation protected static int glGetAttribLocation(int program, String name) { return 1; }
        @Implementation protected static int glGetUniformLocation(int program, String name) { return 1; }
        @Implementation protected static void glGetShaderiv(int shader, int name, int[] result, int offset) {
            result[offset] = GLES20.GL_TRUE;
        }
        @Implementation protected static void glGetProgramiv(int program, int name, int[] result, int offset) {
            result[offset] = GLES20.GL_TRUE;
        }
        @Implementation protected static void glGetIntegerv(int name, int[] result, int offset) {
            result[offset] = 4096;
        }
        @Implementation protected static void glGenBuffers(int count, int[] result, int offset) {
            for (int index = 0; index < count; index++) result[offset + index] = nextObject++;
        }
        @Implementation protected static void glGenTextures(int count, int[] result, int offset) {
            for (int index = 0; index < count; index++) result[offset + index] = nextObject++;
        }
        @Implementation protected static int glGetError() {
            int result = error; error = GLES20.GL_NO_ERROR; return result;
        }
        @Implementation protected static void glDrawElements(int mode, int count, int type, int offset) {
            draw();
        }
        @Implementation protected static void glDrawElements(int mode, int count, int type, Buffer indices) {
            draw();
        }
        private static void draw() {
            draws++;
            if (draws == failDrawAt) throw new IllegalStateException("synthetic-draw-boundary");
        }
    }

    @Implements(value = GLUtils.class, callThroughByDefault = false)
    public static class ControlledUploads {
        @Implementation protected static void texImage2D(int target, int level, Bitmap bitmap, int border) {
            ControlledGles.uploads++;
            if (ControlledGles.uploads == ControlledGles.failUploadAt) {
                ControlledGles.error = GLES20.GL_INVALID_OPERATION;
            }
        }
    }
}
