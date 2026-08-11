package karacken.curl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** GLES2 renderer for client-prepared page bitmaps and PlayLikeCurl deformation. */
public final class PageRenderer implements GLSurfaceView.Renderer {
    interface Events {
        void onCapabilitiesAvailable(RenderCapabilities capabilities);

        void onDeckPrepared(long generationId);

        void onDeckReleased(long generationId, DeckReleaseReason reason);

        void onPageOverlayUpdateCompleted(long generationId, boolean applied);

        void onRenderFailure(RenderFailure failure);
    }

    private static final String VERTEX_SHADER =
            "uniform mat4 uMvpMatrix;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute vec2 aTextureCoordinate;\n"
                    + "varying vec2 vTextureCoordinate;\n"
                    + "void main() {\n"
                    + "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);\n"
                    + "  vTextureCoordinate = aTextureCoordinate;\n"
                    + "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform sampler2D uTexture;\n"
                    + "uniform sampler2D uOverlayTexture;\n"
                    + "uniform float uHasOverlay;\n"
                    + "uniform float uIsFiller;\n"
                    + "uniform vec4 uFillerColor;\n"
                    + "varying vec2 vTextureCoordinate;\n"
                    + "void main() {\n"
                    + "  if (uIsFiller > 0.5) {\n"
                    + "    gl_FragColor = uFillerColor;\n"
                    + "    return;\n"
                    + "  }\n"
                    + "  vec4 base = texture2D(uTexture, vTextureCoordinate);\n"
                    + "  vec4 overlay = texture2D(uOverlayTexture, vTextureCoordinate);\n"
                    + "  gl_FragColor = mix(base, overlay + base * (1.0 - overlay.a), uHasOverlay);\n"
                    + "}\n";

    private static final String SHADOW_VERTEX_SHADER =
            "uniform mat4 uMvpMatrix;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute float aGradient;\n"
                    + "varying float vGradient;\n"
                    + "void main() {\n"
                    + "  gl_Position = uMvpMatrix * vec4(aPosition, 1.0);\n"
                    + "  vGradient = aGradient;\n"
                    + "}\n";

    private static final String SHADOW_FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "uniform float uOpacity;\n"
                    + "varying float vGradient;\n"
                    + "void main() {\n"
                    + "  float falloff = 1.0 - smoothstep(0.0, 1.0, vGradient);\n"
                    + "  gl_FragColor = vec4(0.0, 0.0, 0.0, uOpacity * falloff);\n"
                    + "}\n";

    private static final short[] SHADOW_INDICES = {0, 1, 2, 2, 1, 3};
    private static final long DEFAULT_GPU_BUDGET_BYTES = 128L * 1024L * 1024L;

    private final Events events;
    private final GpuMesh leftMesh = new GpuMesh(PageRole.LEFT);
    private final GpuMesh frontMesh = new GpuMesh(PageRole.FRONT);
    private final GpuMesh mirroredLeftMesh = new GpuMesh(PageRole.LEFT, true);
    private final GpuMesh mirroredFrontMesh = new GpuMesh(PageRole.FRONT, true);
    private final GpuMesh rightMesh = new GpuMesh(PageRole.RIGHT);
    private final GpuMesh mirroredRightMesh = new GpuMesh(PageRole.RIGHT, true);
    private final Map<String, GpuTexture> textureCache = new LinkedHashMap<>();
    private final PageOverlayReplacementStore<String, DynamicPageOverlayTexture>
            dynamicPageOverlays = new PageOverlayReplacementStore<>(
                    DynamicPageOverlayTexture::dispose);
    private final PageState flatState = new PageState(
            PageRole.RIGHT, PlayLikeCurlModel.RIGHT_DEPTH, PlayLikeCurlModel.GRID, 0);
    private final PageState turningState = new PageState(
            PageRole.FRONT, PlayLikeCurlModel.FRONT_DEPTH, PlayLikeCurlModel.GRID, 0);
    private final PageState incomingState = new PageState(
            PageRole.LEFT,
            PlayLikeCurlModel.LEFT_DEPTH,
            PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION,
            0);
    private final float[] projectionMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];
    private final FloatBuffer shadowPositionBuffer = directFloatBuffer(12);
    private final FloatBuffer shadowGradientBuffer = directFloatBuffer(4);
    private final ShortBuffer shadowIndexBuffer = directShortBuffer(SHADOW_INDICES.length);

    private PlayLikeCurlModel portraitModel;
    private LandscapeSpreadModel landscapeSpreadModel;
    private PageDeck<Bitmap> activeDeck;
    private PageDeck<Bitmap> replacementDeck;
    private PageImage<Bitmap> portraitLeftResource;
    private PageImage<Bitmap> portraitFrontResource;
    private PageImage<Bitmap> portraitRightResource;
    private PageImage<Bitmap> spreadPreviousLeftResource;
    private PageImage<Bitmap> spreadPreviousRightResource;
    private PageImage<Bitmap> spreadCurrentLeftResource;
    private PageImage<Bitmap> spreadCurrentRightResource;
    private PageImage<Bitmap> spreadNextLeftResource;
    private PageImage<Bitmap> spreadNextRightResource;
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private int program;
    private int positionAttribute;
    private int textureCoordinateAttribute;
    private int matrixUniform;
    private int textureUniform;
    private int overlayTextureUniform;
    private int hasOverlayUniform;
    private int isFillerUniform;
    private int fillerColorUniform;
    private int shadowProgram;
    private int shadowPositionAttribute;
    private int shadowGradientAttribute;
    private int shadowMatrixUniform;
    private int shadowOpacityUniform;
    private int maxTextureSize;
    private long gpuBudgetBytes = DEFAULT_GPU_BUDGET_BYTES;
    private volatile ReadingDirection readingDirection = ReadingDirection.LEFT_TO_RIGHT;
    private boolean glReady;
    private boolean disposed;

    PageRenderer(Events events) {
        this.events = events;
    }

    void setReadingDirection(ReadingDirection readingDirection) {
        this.readingDirection = Objects.requireNonNull(readingDirection, "readingDirection");
    }

    static boolean turnsPhysicalRightLeaf(
            ReadingDirection readingDirection,
            PageChange pageChange) {
        Objects.requireNonNull(readingDirection, "readingDirection");
        if (pageChange != PageChange.PREVIOUS && pageChange != PageChange.NEXT) {
            throw new IllegalArgumentException("A physical turning leaf requires PREVIOUS or NEXT");
        }
        boolean logicalNext = pageChange == PageChange.NEXT;
        return readingDirection == ReadingDirection.LEFT_TO_RIGHT
                ? logicalNext
                : !logicalNext;
    }

    void prepareDeck(PageDeck<Bitmap> deck, boolean activateWhenPrepared) {
        if (disposed) {
            reportFailure(
                    deck.getGenerationId(),
                    false,
                    RenderFailureReason.DISPOSED,
                    "Renderer is disposed",
                    null);
            events.onDeckReleased(deck.getGenerationId(), DeckReleaseReason.FAILED);
            return;
        }
        boolean retained = false;
        try {
            validateDeck(deck);
            PageDeck<Bitmap> prospectiveActive =
                    activateWhenPrepared ? deck : activeDeck;
            PageDeck<Bitmap> prospectivePending =
                    activateWhenPrepared ? null : deck;
            TextureBudget.Result budget = TextureBudget.evaluate(
                    prospectiveActive,
                    prospectivePending,
                    maxTextureSize,
                    gpuBudgetBytes);
            if (budget.getFailureReason() != null) {
                reportBudgetFailure(deck.getGenerationId(), budget);
                events.onDeckReleased(deck.getGenerationId(), DeckReleaseReason.FAILED);
                return;
            }
            if (activateWhenPrepared) {
                activeDeck = deck;
                replacementDeck = null;
                applyActiveDeck(deck);
            } else {
                replacementDeck = deck;
            }
            retained = true;
            retainDeckTextures();
            if (glReady) {
                uploadDeck(deck);
                events.onDeckPrepared(deck.getGenerationId());
            }
        } catch (RuntimeException exception) {
            reportFailure(
                    deck.getGenerationId(),
                    true,
                    RenderFailureReason.BITMAP,
                    "Could not prepare page deck",
                    exception);
            if (retained) {
                releaseDeck(deck.getGenerationId(), DeckReleaseReason.FAILED);
            } else {
                events.onDeckReleased(deck.getGenerationId(), DeckReleaseReason.FAILED);
            }
        }
    }

    void setGpuBudgetBytes(long gpuBudgetBytes) {
        if (gpuBudgetBytes <= 0) {
            throw new IllegalArgumentException("gpuBudgetBytes must be positive");
        }
        this.gpuBudgetBytes = gpuBudgetBytes;
        publishCapabilities();
    }

    void activateDeck(long generationId) {
        if (disposed) {
            return;
        }
        if (replacementDeck != null && replacementDeck.getGenerationId() == generationId) {
            PageDeck<Bitmap> releasedDeck = activeDeck;
            activeDeck = replacementDeck;
            replacementDeck = null;
            applyActiveDeck(activeDeck);
            retainDeckTextures();
            if (releasedDeck != null
                    && releasedDeck.getGenerationId() != activeDeck.getGenerationId()) {
                events.onDeckReleased(
                        releasedDeck.getGenerationId(),
                        DeckReleaseReason.REPLACED);
            }
        }
    }

    void replacePageOverlays(
            long generationId,
            List<PageOverlayImage<Bitmap>> overlays) {
        boolean applied = false;
        try {
            if (disposed || activeDeck == null || activeDeck.getGenerationId() != generationId) {
                recycleOverlayInputs(overlays);
                return;
            }
            long overlayPeakBytes = Math.addExact(
                    dynamicPageOverlayBytes(),
                    pageOverlayBytes(overlays));
            TextureBudget.Result budget = TextureBudget.evaluate(
                    activeDeck,
                    replacementDeck,
                    maxTextureSize,
                    gpuBudgetBytes,
                    overlayPeakBytes);
            if (budget.getFailureReason() != null) {
                dynamicPageOverlays.clear();
                recycleOverlayInputs(overlays);
                return;
            }
            Map<String, DynamicPageOverlayTexture> replacements = new LinkedHashMap<>();
            try {
                for (PageOverlayImage<Bitmap> overlay : overlays) {
                    PageImage<Bitmap> page = currentPage(overlay.getOrdinal());
                    if (page == null || page.isFiller() || page.hasOverlay()) {
                        throw new IllegalArgumentException(
                                "Overlay target is not a current base page");
                    }
                    DynamicPageOverlayTexture texture = new DynamicPageOverlayTexture(
                            page,
                            overlay.getContent());
                    replacements.put(page.identityKey(), texture);
                }
                for (DynamicPageOverlayTexture texture : replacements.values()) {
                    texture.ensureUploaded();
                }
                dynamicPageOverlays.replace(replacements);
                applied = true;
            } catch (RuntimeException | Error failure) {
                for (DynamicPageOverlayTexture texture : replacements.values()) {
                    texture.dispose();
                }
                throw failure;
            }
        } catch (RuntimeException exception) {
            dynamicPageOverlays.clear();
            recycleOverlayInputs(overlays);
        } finally {
            events.onPageOverlayUpdateCompleted(generationId, applied);
        }
    }

    private PageImage<Bitmap> currentPage(int ordinal) {
        if (activeDeck instanceof PortraitPageDeck<?>) {
            PageImage<Bitmap> current = ((PortraitPageDeck<Bitmap>) activeDeck).getCurrent();
            return current.getOrdinal() == ordinal ? current : null;
        }
        if (activeDeck instanceof LandscapePageDeck<?>) {
            LandscapePageDeck<Bitmap> spread = (LandscapePageDeck<Bitmap>) activeDeck;
            if (spread.getCurrentLeft().getOrdinal() == ordinal) {
                return spread.getCurrentLeft();
            }
            if (spread.getCurrentRight().getOrdinal() == ordinal) {
                return spread.getCurrentRight();
            }
        }
        return null;
    }

    private static void recycleOverlayInputs(List<PageOverlayImage<Bitmap>> overlays) {
        for (PageOverlayImage<Bitmap> overlay : overlays) {
            Bitmap bitmap = overlay.getContent();
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private long dynamicPageOverlayBytes() {
        long[] total = {0L};
        dynamicPageOverlays.forEach(texture ->
                total[0] = Math.addExact(total[0], texture.gpuBytes()));
        return total[0];
    }

    private static long pageOverlayBytes(
            List<PageOverlayImage<Bitmap>> overlays) {
        long total = 0L;
        for (PageOverlayImage<Bitmap> overlay : overlays) {
            Bitmap bitmap = overlay.getContent();
            total = Math.addExact(total, gpuBytes(bitmap.getWidth(), bitmap.getHeight()));
        }
        return total;
    }

    private static long gpuBytes(int width, int height) {
        return Math.multiplyExact(
                Math.multiplyExact((long) width, (long) height),
                4L);
    }

    PlayLikeCurlModel getPortraitModel() {
        return portraitModel;
    }

    LandscapeSpreadModel getLandscapeSpreadModel() {
        return landscapeSpreadModel;
    }

    void setViewport(int width, int height) {
        viewportWidth = Math.max(width, 1);
        viewportHeight = Math.max(height, 1);
    }

    void releaseDeck(long generationId, DeckReleaseReason reason) {
        boolean released = false;
        if (activeDeck != null && activeDeck.getGenerationId() == generationId) {
            activeDeck = null;
            clearActiveDeck();
            released = true;
        }
        if (replacementDeck != null && replacementDeck.getGenerationId() == generationId) {
            replacementDeck = null;
            released = true;
        }
        retainDeckTextures();
        if (released) {
            events.onDeckReleased(generationId, reason);
        }
    }

    void dispose() {
        if (disposed) {
            return;
        }
        Set<Long> releasedGenerations = new LinkedHashSet<>();
        if (activeDeck != null) {
            releasedGenerations.add(activeDeck.getGenerationId());
        }
        if (replacementDeck != null) {
            releasedGenerations.add(replacementDeck.getGenerationId());
        }

        disposed = true;
        activeDeck = null;
        replacementDeck = null;
        clearActiveDeck();
        glReady = false;

        DisposalFailure failure = new DisposalFailure();
        List<GpuTexture> textures =
                new ArrayList<>(textureCache.values());
        textureCache.clear();
        for (GpuTexture texture : textures) {
            failure.capture(texture::deleteGl);
        }

        failure.capture(leftMesh::dispose);
        failure.capture(frontMesh::dispose);
        failure.capture(mirroredLeftMesh::dispose);
        failure.capture(mirroredFrontMesh::dispose);
        failure.capture(rightMesh::dispose);
        failure.capture(mirroredRightMesh::dispose);

        int retainedProgram = program;
        program = 0;
        if (retainedProgram != 0) {
            failure.capture(() -> GLES20.glDeleteProgram(retainedProgram));
        }
        int retainedShadowProgram = shadowProgram;
        shadowProgram = 0;
        if (retainedShadowProgram != 0) {
            failure.capture(
                    () -> GLES20.glDeleteProgram(retainedShadowProgram));
        }

        for (long generationId : releasedGenerations) {
            failure.capture(() -> events.onDeckReleased(
                    generationId,
                    DeckReleaseReason.DISPOSED));
        }
        failure.throwIfPresent();
    }

    private static final class DisposalFailure {
        private Throwable first;

        void capture(Runnable action) {
            try {
                action.run();
            } catch (Throwable next) {
                if (first == null) {
                    first = next;
                } else if (next != first) {
                    first.addSuppressed(next);
                }
            }
        }

        void throwIfPresent() {
            if (first == null) {
                return;
            }
            if (first instanceof RuntimeException) {
                throw (RuntimeException) first;
            }
            if (first instanceof Error) {
                throw (Error) first;
            }
            throw new IllegalStateException(
                    "Unexpected checked renderer disposal failure",
                    first);
        }
    }

    int textureCount() {
        return textureCache.size() + dynamicPageOverlays.size();
    }

    int textureLimit() {
        return TextureBudget.maximumTextureSlots();
    }

    /**
     * Drops every client bitmap reference after the GL thread has been paused.
     *
     * <p>This is the terminal fallback for a detached surface whose GL event queue can no longer
     * be relied upon to execute {@link #dispose()}. The EGL context owns any remaining GPU object
     * deletion when the terminal pause destroys that context.
     */
    void abandonClientState() {
        if (disposed) {
            return;
        }
        disposed = true;
        activeDeck = null;
        replacementDeck = null;
        glReady = false;
        clearActiveDeck();
        textureCache.clear();
    }

    @Override
    public void onSurfaceCreated(GL10 ignored, EGLConfig config) {
        if (disposed) {
            return;
        }
        try {
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            positionAttribute = GLES20.glGetAttribLocation(program, "aPosition");
            textureCoordinateAttribute =
                    GLES20.glGetAttribLocation(program, "aTextureCoordinate");
            matrixUniform = GLES20.glGetUniformLocation(program, "uMvpMatrix");
            textureUniform = GLES20.glGetUniformLocation(program, "uTexture");
            overlayTextureUniform =
                    GLES20.glGetUniformLocation(program, "uOverlayTexture");
            hasOverlayUniform = GLES20.glGetUniformLocation(program, "uHasOverlay");
            isFillerUniform = GLES20.glGetUniformLocation(program, "uIsFiller");
            fillerColorUniform = GLES20.glGetUniformLocation(program, "uFillerColor");
            if (isFillerUniform < 0 || fillerColorUniform < 0) {
                throw new IllegalStateException("Filler shader uniforms are unavailable");
            }
            shadowProgram = createProgram(SHADOW_VERTEX_SHADER, SHADOW_FRAGMENT_SHADER);
            shadowPositionAttribute =
                    GLES20.glGetAttribLocation(shadowProgram, "aPosition");
            shadowGradientAttribute =
                    GLES20.glGetAttribLocation(shadowProgram, "aGradient");
            shadowMatrixUniform =
                    GLES20.glGetUniformLocation(shadowProgram, "uMvpMatrix");
            shadowOpacityUniform =
                    GLES20.glGetUniformLocation(shadowProgram, "uOpacity");

            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClearDepthf(1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            int[] textureLimits = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, textureLimits, 0);
            if (textureLimits[0] <= 0) {
                throw new IllegalStateException(
                        "GL_MAX_TEXTURE_SIZE was not reported");
            }
            maxTextureSize = textureLimits[0];

            leftMesh.initializeGl();
            frontMesh.initializeGl();
            mirroredLeftMesh.initializeGl();
            mirroredFrontMesh.initializeGl();
            rightMesh.initializeGl();
            mirroredRightMesh.initializeGl();
            for (GpuTexture texture : textureCache.values()) {
                texture.resetGl();
            }
            dynamicPageOverlays.forEach(DynamicPageOverlayTexture::resetGl);
            glReady = true;
            publishCapabilities();
            rehydrateRetainedDecks();
            rehydrateDynamicPageOverlays();
        } catch (RuntimeException exception) {
            glReady = false;
            reportFailure(
                    activeGeneration(),
                    false,
                    RenderFailureReason.SHADER,
                    "Could not initialize PlayLikeCurl GLES2 renderer",
                    exception);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 ignored, int width, int height) {
        setViewport(width, height);
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
    }

    @Override
    public void onDrawFrame(GL10 ignored) {
        drawFrame(ignored);
    }

    boolean drawFrame(GL10 ignored) {
        if (disposed || !glReady) {
            return false;
        }
        try {
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (activeDeck == null) {
                return false;
            }
            GLES20.glUseProgram(program);
            GLES20.glUniform1i(textureUniform, 0);
            GLES20.glUniform1i(overlayTextureUniform, 1);

            if (landscapeSpreadModel != null) {
                return drawLandscapeSpread();
            }
            if (portraitModel != null) {
                return drawPortraitPage();
            }
            return false;
        } catch (RuntimeException exception) {
            reportFailure(
                    activeGeneration(),
                    true,
                    RenderFailureReason.CONTEXT,
                    "Could not render page frame",
                    exception);
            return false;
        }
    }

    private void applyActiveDeck(PageDeck<Bitmap> deck) {
        dynamicPageOverlays.clear();
        if (deck instanceof PortraitPageDeck) {
            PortraitPageDeck<Bitmap> portrait = (PortraitPageDeck<Bitmap>) deck;
            portraitLeftResource = portrait.getPrevious();
            portraitFrontResource = portrait.getCurrent();
            portraitRightResource = portrait.getNext();
            portraitModel = new PlayLikeCurlModel(3, 1);
            landscapeSpreadModel = null;
            clearSpreadResources();
        } else if (deck instanceof LandscapePageDeck) {
            LandscapePageDeck<Bitmap> spread = (LandscapePageDeck<Bitmap>) deck;
            spreadPreviousLeftResource = spread.getPreviousLeft();
            spreadPreviousRightResource = spread.getPreviousRight();
            spreadCurrentLeftResource = spread.getCurrentLeft();
            spreadCurrentRightResource = spread.getCurrentRight();
            spreadNextLeftResource = spread.getNextLeft();
            spreadNextRightResource = spread.getNextRight();
            landscapeSpreadModel = new LandscapeSpreadModel(6, 2);
            portraitModel = null;
            clearPortraitResources();
        } else {
            throw new IllegalArgumentException("Unsupported page deck type");
        }
    }

    private void clearActiveDeck() {
        dynamicPageOverlays.clear();
        portraitModel = null;
        landscapeSpreadModel = null;
        clearPortraitResources();
        clearSpreadResources();
    }

    private void clearPortraitResources() {
        portraitLeftResource = null;
        portraitFrontResource = null;
        portraitRightResource = null;
    }

    private void clearSpreadResources() {
        spreadPreviousLeftResource = null;
        spreadPreviousRightResource = null;
        spreadCurrentLeftResource = null;
        spreadCurrentRightResource = null;
        spreadNextLeftResource = null;
        spreadNextRightResource = null;
    }

    private void validateDeck(PageDeck<Bitmap> deck) {
        if (deck == null) {
            throw new IllegalArgumentException("deck must not be null");
        }
        for (PageImage<Bitmap> page : deck.getPages()) {
            if (page.isFiller()) {
                if (page.getWidthPx() <= 0 || page.getHeightPx() <= 0) {
                    throw new IllegalArgumentException("Filler dimensions must be positive");
                }
                if (page.hasOverlay()) {
                    throw new IllegalArgumentException("Filler pages cannot have overlays");
                }
                if ((page.getFillerColorArgb() >>> 24) != 0xFF) {
                    throw new IllegalArgumentException("Filler color must be fully opaque ARGB");
                }
                continue;
            }
            Bitmap bitmap = page.getContent();
            if (bitmap.isRecycled()) {
                throw new IllegalArgumentException(
                        "Bitmap is recycled for " + page.getLogicalPageId());
            }
            if (bitmap.getWidth() != page.getWidthPx()
                    || bitmap.getHeight() != page.getHeightPx()) {
                throw new IllegalArgumentException(
                        "Bitmap dimensions differ from PageImage metadata for "
                                + page.getLogicalPageId());
            }
            if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                throw new IllegalArgumentException(
                        "Bitmap must use ARGB_8888 for "
                                + page.getLogicalPageId());
            }
            if (bitmap.hasAlpha()) {
                throw new IllegalArgumentException(
                        "Bitmap must be composited onto an opaque page background for "
                                + page.getLogicalPageId());
            }
            Bitmap overlay = page.getOverlayContent();
            if (overlay != null) {
                if (overlay.isRecycled()) {
                    throw new IllegalArgumentException(
                            "Overlay bitmap is recycled for " + page.getLogicalPageId());
                }
                if (overlay.getWidth() != page.getWidthPx()
                        || overlay.getHeight() != page.getHeightPx()) {
                    throw new IllegalArgumentException(
                            "Overlay dimensions differ from PageImage metadata for "
                                    + page.getLogicalPageId());
                }
                if (overlay.getConfig() != Bitmap.Config.ARGB_8888) {
                    throw new IllegalArgumentException(
                            "Overlay bitmap must use ARGB_8888 for "
                                    + page.getLogicalPageId());
                }
                if (!overlay.isPremultiplied() || !overlay.hasAlpha()) {
                    throw new IllegalArgumentException(
                            "Overlay bitmap must be premultiplied and retain alpha for "
                                    + page.getLogicalPageId());
                }
            }
        }
    }

    private void publishCapabilities() {
        if (maxTextureSize > 0 && !disposed) {
            events.onCapabilitiesAvailable(
                    new RenderCapabilities(maxTextureSize, gpuBudgetBytes));
        }
    }

    private void reportBudgetFailure(
            long generationId,
            TextureBudget.Result budget) {
        RenderFailureReason reason = budget.getFailureReason();
        String message;
        if (reason == RenderFailureReason.TEXTURE_TOO_LARGE) {
            message = "Page texture exceeds the device texture-size limit";
        } else if (reason == RenderFailureReason.GPU_BUDGET_EXCEEDED) {
            message = "Page decks exceed the configured GPU byte budget";
        } else {
            throw new IllegalArgumentException("Unsupported budget failure " + reason);
        }
        events.onRenderFailure(
                new RenderFailure(
                        generationId,
                        true,
                        reason,
                        message,
                        null,
                        budget.getRequestedWidthPx(),
                        budget.getRequestedHeightPx(),
                        budget.getMaxTextureSize(),
                        budget.getRequiredBytes(),
                        budget.getGpuBudgetBytes()));
    }

    private void uploadDeck(PageDeck<Bitmap> deck) {
        for (PageImage<Bitmap> page : deck.getPages()) {
            if (page.isFiller()) {
                continue;
            }
            GpuTexture texture = textureCache.get(page.identityKey());
            if (texture == null) {
                texture = new GpuTexture(page, false);
                textureCache.put(page.identityKey(), texture);
            }
            texture.ensureUploaded();
            if (page.hasOverlay()) {
                GpuTexture overlay = textureCache.get(page.overlayIdentityKey());
                if (overlay == null) {
                    overlay = new GpuTexture(page, true);
                    textureCache.put(page.overlayIdentityKey(), overlay);
                }
                overlay.ensureUploaded();
            }
        }
    }

    private void rehydrateRetainedDecks() {
        PageDeck<Bitmap> retainedActive = activeDeck;
        if (retainedActive != null) {
            rehydrateDeck(retainedActive, retainedActive, null);
        }
        PageDeck<Bitmap> retainedReplacement = replacementDeck;
        if (retainedReplacement != null) {
            rehydrateDeck(
                    retainedReplacement,
                    activeDeck,
                    retainedReplacement);
        }
    }

    private void rehydrateDynamicPageOverlays() {
        try {
            dynamicPageOverlays.forEach(DynamicPageOverlayTexture::ensureUploaded);
        } catch (RuntimeException exception) {
            dynamicPageOverlays.clear();
            reportFailure(
                    activeGeneration(),
                    true,
                    RenderFailureReason.TEXTURE_UPLOAD,
                    "Could not restore page overlays after GL context recreation",
                    exception);
        }
    }

    private boolean rehydrateDeck(
            PageDeck<Bitmap> deck,
            PageDeck<Bitmap> prospectiveActive,
            PageDeck<Bitmap> prospectivePending) {
        try {
            validateDeck(deck);
        } catch (RuntimeException exception) {
            reportFailure(
                    deck.getGenerationId(),
                    true,
                    RenderFailureReason.BITMAP,
                    "Retained page bitmap is no longer valid",
                    exception);
            releaseDeck(deck.getGenerationId(), DeckReleaseReason.FAILED);
            return false;
        }

        TextureBudget.Result budget = TextureBudget.evaluate(
                prospectiveActive,
                prospectivePending,
                maxTextureSize,
                gpuBudgetBytes);
        if (budget.getFailureReason() != null) {
            reportBudgetFailure(deck.getGenerationId(), budget);
            releaseDeck(deck.getGenerationId(), DeckReleaseReason.FAILED);
            return false;
        }

        try {
            uploadDeck(deck);
            events.onDeckPrepared(deck.getGenerationId());
            return true;
        } catch (RuntimeException exception) {
            reportFailure(
                    deck.getGenerationId(),
                    true,
                    RenderFailureReason.TEXTURE_UPLOAD,
                    "Could not restore page textures after GL context recreation",
                    exception);
            releaseDeck(deck.getGenerationId(), DeckReleaseReason.FAILED);
            return false;
        }
    }

    private void retainDeckTextures() {
        Set<String> retainedKeys = new LinkedHashSet<>();
        collectDeckKeys(activeDeck, retainedKeys);
        collectDeckKeys(replacementDeck, retainedKeys);
        Iterator<Map.Entry<String, GpuTexture>> iterator =
                textureCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, GpuTexture> entry = iterator.next();
            if (!retainedKeys.contains(entry.getKey())) {
                entry.getValue().deleteGl();
                iterator.remove();
            }
        }
        registerDeck(activeDeck);
        registerDeck(replacementDeck);
    }

    private void registerDeck(PageDeck<Bitmap> deck) {
        if (deck == null) {
            return;
        }
        for (PageImage<Bitmap> page : deck.getPages()) {
            if (page.isFiller()) {
                continue;
            }
            textureCache.computeIfAbsent(
                    page.identityKey(),
                    key -> new GpuTexture(page, false));
            if (page.hasOverlay()) {
                textureCache.computeIfAbsent(
                        page.overlayIdentityKey(),
                        key -> new GpuTexture(page, true));
            }
        }
    }

    private static void collectDeckKeys(PageDeck<Bitmap> deck, Set<String> keys) {
        if (deck == null) {
            return;
        }
        for (PageImage<Bitmap> page : deck.getPages()) {
            if (page.isFiller()) {
                continue;
            }
            keys.add(page.identityKey());
            if (page.hasOverlay()) {
                keys.add(page.overlayIdentityKey());
            }
        }
    }

    private boolean drawPortraitPage() {
        PageDisplayRect displayRect = displayRect(portraitFrontResource, fullViewportRect());
        drawPageBacking(portraitFrontResource);
        configureDisplayViewport(displayRect, PageOrientation.PORTRAIT, 0f);
        int displayWidth = displayRect.getWidthPx();
        int displayHeight = displayRect.getHeightPx();
        boolean rtl = readingDirection == ReadingDirection.RIGHT_TO_LEFT;
        GpuMesh leftPageMesh = rtl ? mirroredLeftMesh : leftMesh;
        GpuMesh frontPageMesh = rtl ? mirroredFrontMesh : frontMesh;
        GpuMesh rightPageMesh = rtl ? mirroredRightMesh : rightMesh;
        boolean rendered = true;
        if (portraitModel.getActivePage() == ActivePage.LEFT) {
            rendered = drawPage(
                    rightPageMesh,
                    portraitRightResource,
                    portraitModel.getRightPage(),
                    false,
                    PageOrientation.PORTRAIT,
                    displayWidth,
                    displayHeight) && rendered;
            rendered = drawPage(
                    frontPageMesh,
                    portraitFrontResource,
                    portraitModel.getFrontPage(),
                    false,
                    PageOrientation.PORTRAIT,
                    displayWidth,
                    displayHeight) && rendered;
            rendered = drawMovingPage(
                    leftPageMesh,
                    portraitLeftResource,
                    portraitModel.getLeftPage(),
                    PageOrientation.PORTRAIT,
                    displayWidth,
                    displayHeight) && rendered;
            return rendered;
        }
        rendered = drawPage(
                leftPageMesh,
                portraitLeftResource,
                portraitModel.getLeftPage(),
                false,
                PageOrientation.PORTRAIT,
                displayWidth,
                displayHeight) && rendered;
        rendered = drawPage(
                rightPageMesh,
                portraitRightResource,
                portraitModel.getRightPage(),
                false,
                PageOrientation.PORTRAIT,
                displayWidth,
                displayHeight) && rendered;
        rendered = drawMovingPage(
                frontPageMesh,
                portraitFrontResource,
                portraitModel.getFrontPage(),
                PageOrientation.PORTRAIT,
                displayWidth,
                displayHeight) && rendered;
        return rendered;
    }

    private boolean drawLandscapeSpread() {
        preloadSpreadWindow();
        PageDisplayRect leftFallback = leftHalfViewportRect();
        PageDisplayRect rightFallback = rightHalfViewportRect();
        drawPageBacking(spreadCurrentLeftResource);
        drawPageBacking(spreadCurrentRightResource);
        LandscapeSpreadTransition transition = landscapeSpreadModel.getTransition();
        boolean rendered = true;

        if (transition.getProgress() == 0f) {
            rendered = drawFlatLeaf(spreadCurrentLeftResource, leftFallback) && rendered;
            rendered = drawFlatLeaf(spreadCurrentRightResource, rightFallback) && rendered;
            return rendered;
        }

        PageChange pageChange = transition.isForward()
                ? PageChange.NEXT
                : PageChange.PREVIOUS;
        PageImage<Bitmap> destinationLeft = transition.isForward()
                ? spreadNextLeftResource
                : spreadPreviousLeftResource;
        PageImage<Bitmap> destinationRight = transition.isForward()
                ? spreadNextRightResource
                : spreadPreviousRightResource;
        boolean physicalRight = turnsPhysicalRightLeaf(readingDirection, pageChange);
        turningState.setCurlPosition(transition.getTurningCurlPosition());
        incomingState.setCurlPosition(transition.getIncomingCurlPosition());

        if (physicalRight) {
            rendered = drawFlatLeaf(spreadCurrentLeftResource, leftFallback) && rendered;
            rendered = drawFlatLeaf(destinationRight, rightFallback) && rendered;
            if (transition.isTurningCurrentLeafVisible()) {
                rendered = drawLeaf(
                        spreadCurrentRightResource,
                        rightFallback,
                        frontMesh,
                        turningState,
                        true) && rendered;
            }
            if (transition.isIncomingReverseLeafVisible()) {
                rendered = drawLeaf(
                        destinationLeft,
                        leftFallback,
                        mirroredLeftMesh,
                        incomingState,
                        true) && rendered;
            }
        } else {
            rendered = drawFlatLeaf(destinationLeft, leftFallback) && rendered;
            rendered = drawFlatLeaf(spreadCurrentRightResource, rightFallback) && rendered;
            if (transition.isTurningCurrentLeafVisible()) {
                rendered = drawLeaf(
                        spreadCurrentLeftResource,
                        leftFallback,
                        mirroredFrontMesh,
                        turningState,
                        true) && rendered;
            }
            if (transition.isIncomingReverseLeafVisible()) {
                rendered = drawLeaf(
                        destinationRight,
                        rightFallback,
                        leftMesh,
                        incomingState,
                        true) && rendered;
            }
        }
        return rendered;
    }

    private boolean drawFlatLeaf(
            PageImage<Bitmap> resource,
            PageDisplayRect fallbackDisplayRect) {
        return drawLeaf(resource, fallbackDisplayRect, rightMesh, flatState, false);
    }

    private boolean drawLeaf(
            PageImage<Bitmap> resource,
            PageDisplayRect fallbackDisplayRect,
            GpuMesh mesh,
            PageState state,
            boolean active) {
        PageDisplayRect displayRect = displayRect(resource, fallbackDisplayRect);
        configureDisplayViewport(
                displayRect,
                PageOrientation.PORTRAIT,
                PlayLikeCurlModel.RIGHT_DEPTH);
        int displayWidth = displayRect.getWidthPx();
        int displayHeight = displayRect.getHeightPx();
        if (active) {
            return drawMovingPage(
                    mesh,
                    resource,
                    state,
                    PageOrientation.PORTRAIT,
                    displayWidth,
                    displayHeight);
        }
        return drawPage(
                mesh,
                resource,
                state,
                false,
                PageOrientation.PORTRAIT,
                displayWidth,
                displayHeight);
    }

    private PageDisplayRect fullViewportRect() {
        return new PageDisplayRect(0, 0, viewportWidth, viewportHeight);
    }

    private PageDisplayRect leftHalfViewportRect() {
        int split = viewportWidth / 2;
        if (split <= 0) {
            return fullViewportRect();
        }
        return new PageDisplayRect(0, 0, split, viewportHeight);
    }

    private PageDisplayRect rightHalfViewportRect() {
        int split = viewportWidth / 2;
        if (split <= 0 || split >= viewportWidth) {
            return fullViewportRect();
        }
        return new PageDisplayRect(split, 0, viewportWidth, viewportHeight);
    }

    private PageDisplayRect displayRect(
            PageImage<Bitmap> resource,
            PageDisplayRect fallbackDisplayRect) {
        return resource != null && resource.hasExplicitDisplayRect()
                ? resource.getDisplayRect()
                : fallbackDisplayRect;
    }

    private void drawPageBacking(PageImage<Bitmap> resource) {
        if (resource == null || !resource.hasBacking()) {
            return;
        }
        PageDisplayRect backingRect = resource.getBackingRect();
        if (!backingRect.fitsWithin(viewportWidth, viewportHeight)) {
            throw new IllegalArgumentException(
                    "Page backing rectangle exceeds the renderer surface");
        }
        int color = resource.getBackingColorArgb();
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        try {
            GLES20.glScissor(
                    backingRect.getLeftPx(),
                    backingRect.glBottomPx(viewportHeight),
                    backingRect.getWidthPx(),
                    backingRect.getHeightPx());
            GLES20.glClearColor(
                    colorChannel(color, 16),
                    colorChannel(color, 8),
                    colorChannel(color, 0),
                    colorChannel(color, 24));
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        } finally {
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        }
    }

    private void configureDisplayViewport(
            PageDisplayRect displayRect,
            PageOrientation orientation,
            float restingPlaneDepth) {
        if (!displayRect.fitsWithin(viewportWidth, viewportHeight)) {
            throw new IllegalArgumentException(
                    "Page display rectangle exceeds the renderer surface");
        }
        GLES20.glViewport(
                displayRect.getLeftPx(),
                displayRect.glBottomPx(viewportHeight),
                displayRect.getWidthPx(),
                displayRect.getHeightPx());
        updateMvp(
                displayRect.getWidthPx(),
                displayRect.getHeightPx(),
                orientation,
                restingPlaneDepth);
    }

    private boolean drawMovingPage(
            GpuMesh mesh,
            PageImage<Bitmap> resource,
            PageState state,
            PageOrientation orientation,
            int displayWidth,
            int displayHeight) {
        drawFoldShadow(
                mesh,
                resource,
                state,
                orientation,
                displayWidth,
                displayHeight);
        return drawPage(
                mesh,
                resource,
                state,
                true,
                orientation,
                displayWidth,
                displayHeight);
    }

    private void drawFoldShadow(
            GpuMesh mesh,
            PageImage<Bitmap> resource,
            PageState state,
            PageOrientation orientation,
            int displayWidth,
            int displayHeight) {
        if (resource == null) {
            return;
        }
        GpuTexture texture = texture(resource);
        if (!resource.isFiller() && texture == null) {
            return;
        }
        FoldShadowModel.State shadow = FoldShadowModel.resolve(
                mesh.role, state.getCurlPosition(), mesh.horizontallyMirrored);
        if (shadow.getOpacity() <= 0.001f) {
            return;
        }

        float pageRatio = PlayLikeCurlGeometry.pageRatio(
                displayWidth, displayHeight, orientation);
        float heightCorrection = (pageRatio - 1f) / 2f;
        float bottom = -heightCorrection;
        float top = pageRatio - heightCorrection;
        shadowPositionBuffer.clear();
        shadowPositionBuffer.put(new float[] {
                shadow.getStartX(), bottom, FoldShadowModel.SHADOW_DEPTH,
                shadow.getEndX(), bottom, FoldShadowModel.SHADOW_DEPTH,
                shadow.getStartX(), top, FoldShadowModel.SHADOW_DEPTH,
                shadow.getEndX(), top, FoldShadowModel.SHADOW_DEPTH
        }).position(0);
        shadowGradientBuffer.clear();
        shadowGradientBuffer.put(shadow.isDarkAtStart()
                ? new float[] {0f, 1f, 0f, 1f}
                : new float[] {1f, 0f, 1f, 0f}).position(0);
        shadowIndexBuffer.clear();
        shadowIndexBuffer.put(SHADOW_INDICES).position(0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glUseProgram(shadowProgram);
        GLES20.glUniformMatrix4fv(shadowMatrixUniform, 1, false, mvpMatrix, 0);
        GLES20.glUniform1f(shadowOpacityUniform, shadow.getOpacity());
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA,
                GLES20.GL_ONE,
                GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnableVertexAttribArray(shadowPositionAttribute);
        GLES20.glVertexAttribPointer(
                shadowPositionAttribute, 3, GLES20.GL_FLOAT, false, 0, shadowPositionBuffer);
        GLES20.glEnableVertexAttribArray(shadowGradientAttribute);
        GLES20.glVertexAttribPointer(
                shadowGradientAttribute, 1, GLES20.GL_FLOAT, false, 0, shadowGradientBuffer);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                SHADOW_INDICES.length,
                GLES20.GL_UNSIGNED_SHORT,
                shadowIndexBuffer);
        GLES20.glDisableVertexAttribArray(shadowPositionAttribute);
        GLES20.glDisableVertexAttribArray(shadowGradientAttribute);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(program);
        GLES20.glUniform1i(textureUniform, 0);
        GLES20.glUniform1i(overlayTextureUniform, 1);
    }

    private void preloadSpreadWindow() {
        // Deck preparation uploads the complete six-leaf window before readiness is reported.
    }

    private boolean drawPage(
            GpuMesh mesh,
            PageImage<Bitmap> resource,
            PageState state,
            boolean active,
            PageOrientation orientation,
            int displayWidth,
            int displayHeight) {
        if (resource == null) {
            return false;
        }
        GpuTexture baseTexture = texture(resource);
        GpuTexture overlayTexture = overlayTexture(resource);
        if (!resource.isFiller() && baseTexture == null) {
            return false;
        }
        if (resource.hasOverlay() && overlayTexture == null) {
            return false;
        }
        mesh.ensureGeometry(displayWidth, displayHeight, orientation);
        PlayLikeCurlGeometry.update(mesh.geometry, state.getCurlPosition(), active);
        mesh.uploadPositions();

        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, mvpMatrix, 0);
        drawPageTextures(resource, baseTexture, overlayTexture);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.positionBufferId);
        GLES20.glEnableVertexAttribArray(positionAttribute);
        GLES20.glVertexAttribPointer(positionAttribute, 3, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.textureBufferId);
        GLES20.glEnableVertexAttribArray(textureCoordinateAttribute);
        GLES20.glVertexAttribPointer(
                textureCoordinateAttribute, 2, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.indexBufferId);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                mesh.geometry.getIndices().length,
                GLES20.GL_UNSIGNED_SHORT,
                0);
        GLES20.glDisableVertexAttribArray(positionAttribute);
        GLES20.glDisableVertexAttribArray(textureCoordinateAttribute);
        return true;
    }

    private void drawPageTextures(
            PageImage<Bitmap> resource,
            GpuTexture baseTexture,
            GpuTexture overlayTexture) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                baseTexture == null ? 0 : baseTexture.textureId);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                overlayTexture == null ? 0 : overlayTexture.textureId);
        GLES20.glUniform1f(hasOverlayUniform, overlayTexture == null ? 0f : 1f);
        if (resource.isFiller()) {
            int color = resource.getFillerColorArgb();
            GLES20.glUniform1f(isFillerUniform, 1f);
            GLES20.glUniform4f(fillerColorUniform,
                    colorChannel(color, 16),
                    colorChannel(color, 8),
                    colorChannel(color, 0),
                    colorChannel(color, 24));
        } else {
            GLES20.glUniform1f(isFillerUniform, 0f);
            GLES20.glUniform4f(fillerColorUniform, 0f, 0f, 0f, 0f);
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    private static float colorChannel(int color, int shift) {
        return ((color >>> shift) & 0xFF) / 255f;
    }

    private GpuTexture texture(PageImage<Bitmap> page) {
        if (page == null || page.isFiller()) {
            return null;
        }
        GpuTexture texture = textureCache.get(page.identityKey());
        return texture != null && texture.uploaded ? texture : null;
    }

    private GpuTexture overlayTexture(PageImage<Bitmap> page) {
        if (page == null || page.isFiller()) {
            return null;
        }
        DynamicPageOverlayTexture dynamic = dynamicPageOverlays.get(page.identityKey());
        if (dynamic != null) {
            return dynamic.uploaded ? dynamic : null;
        }
        if (!page.hasOverlay()) {
            return null;
        }
        GpuTexture texture = textureCache.get(page.overlayIdentityKey());
        return texture != null && texture.uploaded ? texture : null;
    }

    private void updateMvp(
            int width,
            int height,
            PageOrientation orientation,
            float restingPlaneDepth) {
        Matrix.perspectiveM(
                projectionMatrix,
                0,
                PlayLikeCurlGeometry.FIELD_OF_VIEW_DEGREES,
                PlayLikeCurlGeometry.projectionAspect(width, height),
                0.1f,
                100f);
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.translateM(modelMatrix, 0, 0f, 0f, -PlayLikeCurlGeometry.CAMERA_DISTANCE);
        float scale = PlayLikeCurlGeometry.restingPlaneScale(
                width, height, orientation, restingPlaneDepth);
        Matrix.scaleM(modelMatrix, 0, scale, scale, 1f);
        Matrix.translateM(modelMatrix, 0, -0.5f, -0.5f, 0f);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0);
    }

    private long activeGeneration() {
        return activeDeck == null ? -1L : activeDeck.getGenerationId();
    }

    private void reportFailure(
            long generationId,
            boolean recoverable,
            RenderFailureReason reason,
            String message,
            Throwable cause) {
        events.onRenderFailure(
                new RenderFailure(generationId, recoverable, reason, message, cause));
    }

    private final class DynamicPageOverlayTexture extends GpuTexture {
        private final Bitmap bitmap;

        DynamicPageOverlayTexture(PageImage<Bitmap> page, Bitmap bitmap) {
            super(page, false);
            this.bitmap = bitmap;
            validateOverlayBitmap(page, bitmap);
        }

        @Override
        void ensureUploaded() {
            if (uploaded) {
                return;
            }
            validateOverlayBitmap(page, bitmap);
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            textureId = ids[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                deleteGl();
                throw new IllegalStateException(
                        "Overlay texture upload failed with GLES error " + error);
            }
            uploaded = true;
        }

        void dispose() {
            if (glReady) {
                deleteGl();
            } else {
                resetGl();
            }
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }

        long gpuBytes() {
            return PageRenderer.gpuBytes(page.getWidthPx(), page.getHeightPx());
        }
    }

    private static void validateOverlayBitmap(PageImage<Bitmap> page, Bitmap bitmap) {
        if (bitmap.isRecycled()) {
            throw new IllegalArgumentException(
                    "Overlay bitmap is recycled for " + page.getLogicalPageId());
        }
        if (bitmap.getWidth() != page.getWidthPx()
                || bitmap.getHeight() != page.getHeightPx()) {
            throw new IllegalArgumentException(
                    "Overlay dimensions differ from the current page");
        }
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888
                || !bitmap.isPremultiplied()
                || !bitmap.hasAlpha()) {
            throw new IllegalArgumentException(
                    "Overlay bitmap must be premultiplied ARGB_8888 with alpha");
        }
    }

    private class GpuTexture {
        final PageImage<Bitmap> page;
        private final boolean overlay;
        int textureId;
        boolean uploaded;

        GpuTexture(PageImage<Bitmap> page, boolean overlay) {
            if (page.isFiller()) {
                throw new IllegalArgumentException("Filler pages do not allocate GPU textures");
            }
            this.page = page;
            this.overlay = overlay;
        }

        void resetGl() {
            textureId = 0;
            uploaded = false;
        }

        void ensureUploaded() {
            if (uploaded) {
                return;
            }
            Bitmap bitmap = overlay ? page.getOverlayContent() : page.getContent();
            if (bitmap == null) {
                throw new IllegalStateException(
                        "Overlay bitmap is missing for " + page.getLogicalPageId());
            }
            if (bitmap.isRecycled()) {
                throw new IllegalStateException(
                        (overlay ? "Overlay bitmap" : "Bitmap")
                                + " was recycled for "
                                + page.getLogicalPageId());
            }
            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            textureId = ids[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                deleteGl();
                throw new IllegalStateException(
                        "Texture upload failed with GLES error " + error);
            }
            uploaded = true;
        }

        void deleteGl() {
            if (textureId != 0) {
                int[] ids = {textureId};
                GLES20.glDeleteTextures(1, ids, 0);
            }
            textureId = 0;
            uploaded = false;
        }
    }

    private final class GpuMesh {
        private final PageRole role;
        private final boolean horizontallyMirrored;
        private final FloatBuffer positionBuffer;
        private final FloatBuffer textureBuffer;
        private final ShortBuffer indexBuffer;
        private final int[] bufferIds = new int[3];
        private PageGeometry geometry;
        private int geometryWidth = -1;
        private int geometryHeight = -1;
        private PageOrientation geometryOrientation;
        private int positionBufferId;
        private int textureBufferId;
        private int indexBufferId;

        GpuMesh(PageRole role) {
            this(role, false);
        }

        GpuMesh(PageRole role, boolean horizontallyMirrored) {
            this.role = role;
            this.horizontallyMirrored = horizontallyMirrored;
            geometry = PlayLikeCurlGeometry.createPage(role, 1, 1, PageOrientation.PORTRAIT);
            if (horizontallyMirrored) {
                mirrorTextureCoordinates(geometry.getTextureCoordinates());
            }
            positionBuffer = directFloatBuffer(geometry.getPositions().length);
            textureBuffer = directFloatBuffer(geometry.getTextureCoordinates().length);
            indexBuffer = directShortBuffer(geometry.getIndices().length);
        }

        void initializeGl() {
            dispose();
            GLES20.glGenBuffers(bufferIds.length, bufferIds, 0);
            positionBufferId = bufferIds[0];
            textureBufferId = bufferIds[1];
            indexBufferId = bufferIds[2];

            textureBuffer.clear();
            textureBuffer.put(geometry.getTextureCoordinates()).position(0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, textureBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    geometry.getTextureCoordinates().length * Float.BYTES,
                    textureBuffer,
                    GLES20.GL_STATIC_DRAW);

            indexBuffer.clear();
            indexBuffer.put(geometry.getIndices()).position(0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    geometry.getIndices().length * Short.BYTES,
                    indexBuffer,
                    GLES20.GL_STATIC_DRAW);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    geometry.getPositions().length * Float.BYTES,
                    null,
                    GLES20.GL_DYNAMIC_DRAW);
            geometryWidth = -1;
            geometryHeight = -1;
            geometryOrientation = null;
        }

        void ensureGeometry(int width, int height, PageOrientation orientation) {
            if (width == geometryWidth
                    && height == geometryHeight
                    && orientation == geometryOrientation) {
                return;
            }
            geometry = PlayLikeCurlGeometry.createPage(role, width, height, orientation);
            if (horizontallyMirrored) {
                mirrorTextureCoordinates(geometry.getTextureCoordinates());
            }
            geometryWidth = width;
            geometryHeight = height;
            geometryOrientation = orientation;
        }

        void uploadPositions() {
            float[] positions = geometry.getPositions();
            positionBuffer.clear();
            if (horizontallyMirrored) {
                for (int offset = 0; offset < positions.length; offset += 3) {
                    positionBuffer.put(1f - positions[offset]);
                    positionBuffer.put(positions[offset + 1]);
                    positionBuffer.put(positions[offset + 2]);
                }
            } else {
                positionBuffer.put(positions);
            }
            positionBuffer.position(0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBufferId);
            GLES20.glBufferSubData(
                    GLES20.GL_ARRAY_BUFFER,
                    0,
                    positions.length * Float.BYTES,
                    positionBuffer);
        }

        void dispose() {
            if (positionBufferId != 0 || textureBufferId != 0 || indexBufferId != 0) {
                int[] ids = {positionBufferId, textureBufferId, indexBufferId};
                GLES20.glDeleteBuffers(ids.length, ids, 0);
            }
            positionBufferId = 0;
            textureBufferId = 0;
            indexBufferId = 0;
        }
    }

    private static void mirrorTextureCoordinates(float[] coordinates) {
        for (int offset = 0; offset < coordinates.length; offset += 2) {
            coordinates[offset] = 1f - coordinates[offset];
        }
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int createdProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(createdProgram, vertexShader);
        GLES20.glAttachShader(createdProgram, fragmentShader);
        GLES20.glLinkProgram(createdProgram);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(createdProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetProgramInfoLog(createdProgram);
            GLES20.glDeleteProgram(createdProgram);
            throw new IllegalStateException(
                    "Could not link PlayLikeCurl GLES2 program: " + log);
        }
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        return createdProgram;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException(
                    "Could not compile PlayLikeCurl GLES2 shader: " + log);
        }
        return shader;
    }

    private static FloatBuffer directFloatBuffer(int size) {
        return ByteBuffer.allocateDirect(size * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }

    private static ShortBuffer directShortBuffer(int size) {
        return ByteBuffer.allocateDirect(size * Short.BYTES)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
    }
}
