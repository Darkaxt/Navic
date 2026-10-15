package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.opengl.GLES20;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, shadows = PageRendererContextGenerationTest.ContextGles20.class)
public class PageRendererContextGenerationTest {
    private final List<Long> capabilityContexts = new ArrayList<>();
    private PageRenderer renderer;
    private int failures;

    @Before
    public void setUp() {
        ContextGles20.compileAllowed = true;
        renderer = new PageRenderer(new PageRenderer.Events() {
            @Override
            public void onCapabilitiesAvailable(RenderCapabilities capabilities) {
                capabilityContexts.add(renderer.getRendererGeneration());
            }

            @Override public void onDeckPrepared(long generationId) {}
            @Override public void onDeckReleased(long generationId, DeckReleaseReason reason) {}
            @Override public void onPageOverlayUpdateCompleted(long generationId, boolean applied) {}
            @Override public void onRenderFailure(RenderFailure failure) { failures++; }
        });
    }

    @Test
    public void originalSurfaceInitializationPublishesCapabilitiesWithoutRenderFailure() {
        renderer.onSurfaceCreated(null, null);

        assertEquals(0, failures);
        assertEquals(1, capabilityContexts.size());
    }

    @Test
    public void contextIdentityComesFromSuccessfulOriginalSurfaceInitialization() {
        assertEquals(0L, renderer.getRendererGeneration());

        renderer.onSurfaceCreated(null, null);

        assertEquals(0, failures);
        assertTrue(renderer.getRendererGeneration() > 0L);
        assertEquals(List.of(renderer.getRendererGeneration()), capabilityContexts);
        long context = renderer.getRendererGeneration();
        renderer.onSurfaceChanged(null, 800, 600);
        renderer.setGpuBudgetBytes(128L * 1024L * 1024L);
        assertEquals(context, renderer.getRendererGeneration());
    }

    @Test
    public void recreatedSurfaceCannotReuseThePreviousRendererContext() {
        renderer.onSurfaceCreated(null, null);
        long first = renderer.getRendererGeneration();

        renderer.onSurfaceCreated(null, null);

        assertEquals(0, failures);
        assertTrue(first > 0L);
        assertTrue(renderer.getRendererGeneration() > first);
        assertEquals(List.of(first, renderer.getRendererGeneration()), capabilityContexts);
    }

    @Test
    public void failedInitializationInvalidatesOldContextBeforeTheNextActualCreation() {
        renderer.onSurfaceCreated(null, null);
        long first = renderer.getRendererGeneration();
        ContextGles20.compileAllowed = false;

        renderer.onSurfaceCreated(null, null);

        assertEquals(1, failures);
        assertEquals(0L, renderer.getRendererGeneration());
        assertEquals(List.of(first), capabilityContexts);
        ContextGles20.compileAllowed = true;
        renderer.onSurfaceCreated(null, null);
        assertTrue(renderer.getRendererGeneration() > first + 1L);
        assertEquals(List.of(first, renderer.getRendererGeneration()), capabilityContexts);
    }

    @Test
    public void terminalDisposalCannotPublishOrRecreateAContextIdentity() {
        renderer.onSurfaceCreated(null, null);
        assertTrue(renderer.getRendererGeneration() > 0L);

        renderer.dispose();
        renderer.onSurfaceCreated(null, null);

        assertEquals(0L, renderer.getRendererGeneration());
        assertEquals(1, capabilityContexts.size());
    }

    // Public shader/limit responses let the original lifecycle run on the host;
    // they do not render a page or provide a display receipt.
    @Implements(value = GLES20.class, callThroughByDefault = false)
    public static class ContextGles20 {
        static boolean compileAllowed;

        @Implementation protected static int glCreateShader(int type) { return 1; }
        @Implementation protected static int glCreateProgram() { return 1; }
        @Implementation protected static int glGetAttribLocation(int program, String name) { return 1; }
        @Implementation protected static int glGetUniformLocation(int program, String name) { return 1; }

        @Implementation
        protected static void glGetShaderiv(int shader, int name, int[] result, int offset) {
            result[offset] = compileAllowed ? GLES20.GL_TRUE : GLES20.GL_FALSE;
        }

        @Implementation
        protected static void glGetProgramiv(int program, int name, int[] result, int offset) {
            result[offset] = GLES20.GL_TRUE;
        }

        @Implementation
        protected static void glGetIntegerv(int name, int[] result, int offset) {
            result[offset] = 4096;
        }
    }
}
