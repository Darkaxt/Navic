package karacken.curl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.Test;

public class ProductionBitmapApiSourceTest {
    @Test
    public void rendererNeverDecodesAssetsOrReadsFiles() throws IOException {
        String source = source("PageRenderer.java");

        assertFalse(source.contains("BitmapFactory"));
        assertFalse(source.contains("getAssets().open"));
        assertFalse(source.contains("InputStream"));
        assertFalse(source.contains("IOException"));
        assertTrue(source.contains("PageImage<Bitmap>"));
    }

    @Test
    public void assetStringSubmissionApiIsRemoved() {
        assertFalse(Files.exists(
                Path.of("src/main/java/karacken/curl/PageCurlAdapter.java")));
    }

    @Test
    public void rendererUsesBoundedTexturesAndExplicitDeletion() throws IOException {
        String source = source("PageRenderer.java");

        assertFalse(source.contains("ConcurrentHashMap"));
        assertTrue(source.contains("glDeleteTextures"));
        assertTrue(source.contains("releaseDeck"));
        assertTrue(source.contains("dispose"));
    }

    @Test
    public void surfaceUsesWhenDirtyRenderingAndProductionLifecycle() throws IOException {
        String source = source("PageSurfaceView.java");

        assertTrue(source.contains("RENDERMODE_WHEN_DIRTY"));
        assertTrue(source.contains("public void attach()"));
        assertTrue(source.contains("public void detach()"));
        assertTrue(source.contains("submitDeck("));
        assertTrue(source.contains("setVisible("));
        assertTrue(source.contains("cancelGesture("));
        assertTrue(source.contains("releaseDeck("));
        assertTrue(source.contains("dispose("));
        assertTrue(source.contains("requestRender()"));
    }

    @Test
    public void submissionAddsTypedResultWithoutChangingVoidDescriptor() throws IOException {
        String source = source("PageSurfaceView.java");
        String compatibilityBody = methodBody(source, "public void submitDeck");
        String typedBody = methodBody(
                source,
                "public PageSurfaceDeckSubmissionResult submitDeckWithResult(\n"
                        + "            PageDeck<Bitmap> deck,\n"
                        + "            Runnable onOwnershipTransferred)");

        assertTrue(compatibilityBody.contains("submitDeckWithResult(deck)"));
        assertTrue(typedBody.contains("submissionGate.submit"));
        assertTrue(typedBody.contains("PageSurfaceDeckSubmissionResult"));
        assertTrue(typedBody.contains("onDeckRejected"));
    }

    @Test
    public void surfaceExposesTheAuthoritativeSettlementPlacementState() throws IOException {
        String source = source("PageSurfaceView.java");
        String method = methodBody(source, "public boolean isSettlementRunning()");

        assertTrue(method.contains("requireMainThread()"));
        assertTrue(method.contains("deckCoordinator.isSettling()"));
    }

    @Test
    public void attachDetachAreIdempotentAndWindowAttachmentDoesNotForgeSessionState()
            throws IOException {
        String source = source("PageSurfaceView.java");
        String attachBody = methodBody(source, "public void attach()");
        String detachBody = methodBody(source, "public void detach()");
        String submitBody = methodBody(
                source,
                "public PageSurfaceDeckSubmissionResult submitDeckWithResult(\n"
                        + "            PageDeck<Bitmap> deck,\n"
                        + "            Runnable onOwnershipTransferred)");
        String windowAttachBody = methodBody(source, "protected void onAttachedToWindow()");

        assertTrue(attachBody.contains("if (disposed || attached)"));
        assertTrue(submitBody.contains("DeckRejectionReason.SESSION_DETACHED"));
        assertTrue(detachBody.contains("releasePending"));
        assertTrue(detachBody.contains("DeckReleaseReason.SESSION_DETACHED"));
        assertTrue(
                detachBody.indexOf("releasePending")
                        < detachBody.indexOf("onPause()"));
        assertFalse(windowAttachBody.contains("attached = true"));
    }

    @Test
    public void rejectedDownQuarantinesTheRemainingTouchSequence() throws IOException {
        String source = source("PageSurfaceView.java");

        assertTrue(source.contains("gestureAccepted"));
        assertTrue(source.contains("gestureAccepted = gestureReady()"));
        assertTrue(source.contains("if (!gestureAccepted)"));
        assertTrue(source.contains("gestureAccepted = false"));
    }

    @Test
    public void surfaceReportsPreparationFailureAndSettlementEvents() throws IOException {
        String source = source("PageSurfaceView.java");
        String listenerSource = source("PageSurfaceListener.java");

        assertTrue(source.contains("onDeckPrepared"));
        assertTrue(source.contains("onDeckRejected"));
        assertTrue(source.contains("onRenderFailure"));
        assertTrue(source.contains("onSettlementStarted"));
        assertTrue(source.contains("onSettlementCompleted"));
        assertTrue(source.contains("onSettlementCancelled"));
        assertTrue(source.contains("onDeckReleased"));
        assertTrue(listenerSource.contains("onSettlementCancelled"));
        assertTrue(listenerSource.contains("onDeckReleased"));
    }

    @Test
    public void gestureIdentitySurvivesEveryAsynchronousSettlementCallback()
            throws IOException {
        String surfaceSource = source("PageSurfaceView.java");
        String listenerSource = source("PageSurfaceListener.java");

        assertTrue(surfaceSource.contains(
                "public boolean onPageTouchEvent(MotionEvent event, long gestureId)"));
        assertTrue(surfaceSource.contains(
                "public boolean turn(PageChange pageChange, long gestureId)"));
        assertTrue(surfaceSource.contains("private long activeGestureId"));
        assertTrue(surfaceSource.contains("SettlementContext.from(activeGestureId"));
        assertTrue(surfaceSource.contains("onGestureRejected(gestureId"));
        assertTrue(surfaceSource.contains("onGestureCancelled(gestureId"));

        assertTrue(listenerSource.contains("onGestureRejected(\n            long gestureId,"));
        assertTrue(listenerSource.contains("onGestureCancelled(\n            long gestureId,"));
        assertTrue(listenerSource.contains("onSettlementStarted(\n            long gestureId,"));
        assertTrue(listenerSource.contains("onSettlementCompleted(\n            long gestureId,"));
        assertTrue(listenerSource.contains("onSettlementCancelled(\n            long gestureId,"));
    }

    @Test
    public void acceptedDeckCallbacksStayWithTheirLeaseOwner() throws IOException {
        String source = source("PageSurfaceView.java");
        String gateSource = source("PageSurfaceDeckSubmissionGate.java");

        assertTrue(source.contains("DeckLeaseRegistry"));
        assertTrue(source.contains("PageSurfaceDeckSubmissionGate"));
        assertTrue(gateSource.contains("leaseRegistry.acquire"));
        assertTrue(source.contains("leaseRegistry.listenerFor"));
        assertTrue(source.contains("leaseRegistry.ownerFor"));
        assertTrue(source.contains("leaseRegistry.release"));
        assertTrue(source.contains("handleRenderFailure"));
    }

    @Test
    public void disposalUsesRequiredLifecycleOwnerAndTerminalGate() throws IOException {
        String surfaceSource = source("PageSurfaceView.java");
        String rendererSource = source("PageRenderer.java");
        String lifecycleDispose = methodBody(
                surfaceSource,
                "public void disposeForLifecycleOwner");
        String startDispose = methodBody(
                surfaceSource,
                "private void startDisposeIfNeeded()");

        assertTrue(lifecycleDispose.contains("requiredDisposeCallback.register"));
        assertTrue(lifecycleDispose.contains("startDisposeIfNeeded()"));
        assertTrue(startDispose.contains("terminalDisposalGate.start"));
        assertTrue(startDispose.contains("submissionGate.close()"));
        assertTrue(startDispose.indexOf("submissionGate.close()")
                < startDispose.indexOf("deckCoordinator.dispose()"));
        assertTrue(rendererSource.contains("void abandonClientState()"));
        assertTrue(rendererSource.contains("textureCache.clear()"));
        assertTrue(surfaceSource.contains("leaseRegistry.markReleaseRequested"));
        assertTrue(surfaceSource.contains("lease.getReleaseReason()"));
        assertFalse(surfaceSource.contains(
                "private void releaseAllOutstandingLeases()"));
    }

    @Test
    public void dragAndTapPathsAreSeparated() throws IOException {
        String source = source("PageSurfaceView.java");

        assertTrue(source.contains("gestureMoved"));
        assertTrue(source.contains("if (!gestureMoved)"));
        assertFalse(source.contains("settle(interaction.release());\n                performClick();"));
    }

    @Test
    public void preparedEdgeTapCanStartTheReferenceSettlementDirectly() throws IOException {
        String surfaceSource = source("PageSurfaceView.java");
        String modelSource = source("PlayLikeCurlModel.java");
        String turnBody =
                methodBody(
                        surfaceSource,
                        "public boolean turn(PageChange pageChange, long gestureId)");
        String modelTurnBody =
                methodBody(modelSource, "Settlement turn(PageChange pageChange)");

        assertTrue(turnBody.contains("gestureReady()"));
        assertTrue(turnBody.contains("PageChange.PREVIOUS"));
        assertTrue(turnBody.contains("PageChange.NEXT"));
        assertTrue(turnBody.contains("interaction.turn(pageChange)"));
        assertTrue(turnBody.contains("settle(settlement)"));
        assertFalse(turnBody.contains("performClick()"));
        assertTrue(modelTurnBody.contains("flingTowardPrevious()"));
        assertTrue(modelTurnBody.contains("flingTowardNext()"));
    }

    @Test
    public void rendererValidatesProductionBitmapAndGpuLimits() throws IOException {
        String source = source("PageRenderer.java");
        String budgetSource = source("TextureBudget.java");

        assertTrue(source.contains("Bitmap.Config.ARGB_8888"));
        assertTrue(source.contains("isPremultiplied()"));
        assertTrue(source.contains("hasAlpha()"));
        assertTrue(source.contains("GL_MAX_TEXTURE_SIZE"));
        assertTrue(source.contains("TEXTURE_TOO_LARGE"));
        assertTrue(source.contains("GPU_BUDGET_EXCEEDED"));
        assertTrue(source.contains("TextureBudget.evaluate"));
        assertTrue(budgetSource.contains("widthPx"));
        assertTrue(budgetSource.contains("heightPx"));
        assertTrue(budgetSource.contains("BYTES_PER_PIXEL"));
    }

    @Test
    public void opaqueBasePagesDoNotRequirePremultipliedAlpha() throws IOException {
        String source = source("PageRenderer.java");
        String validationBody = methodBody(source, "private void validateDeck");
        String baseValidation = validationBody.substring(
                0,
                validationBody.indexOf("Bitmap overlay"));

        assertTrue(baseValidation.contains("bitmap.hasAlpha()"));
        assertFalse(baseValidation.contains("!bitmap.isPremultiplied()"));
    }

    @Test
    public void capabilitiesArePublishedBeforeDeckSubmission() throws IOException {
        String rendererSource = source("PageRenderer.java");
        String surfaceSource = source("PageSurfaceView.java");
        String listenerSource = source("PageSurfaceListener.java");

        assertTrue(rendererSource.contains("onCapabilitiesAvailable"));
        assertTrue(rendererSource.contains("glGetIntegerv"));
        assertTrue(surfaceSource.contains("RenderCapabilities renderCapabilities"));
        assertTrue(surfaceSource.contains("CAPABILITIES_UNAVAILABLE"));
        assertTrue(surfaceSource.contains("setGpuBudgetBytes"));
        assertTrue(listenerSource.contains("onCapabilitiesAvailable"));
    }

    @Test
    public void publicSurfaceMutationsAreMainThreadBound() throws IOException {
        String source = source("PageSurfaceView.java");

        assertTrue(source.contains("Looper.myLooper()"));
        assertTrue(source.contains("Looper.getMainLooper()"));
        assertTrue(source.contains("requireMainThread();"));
        assertTrue(source.contains("public void submitDeck"));
        assertTrue(source.contains("public void setGpuBudgetBytes"));
        assertTrue(source.contains("public void activatePendingDeck"));
    }

    @Test
    public void contextRehydrationRevalidatesAndReleasesOnlyFailedDecks()
            throws IOException {
        String source = source("PageRenderer.java");

        assertTrue(source.contains("rehydrateRetainedDecks"));
        assertTrue(source.contains("rehydrateDeck"));
        assertTrue(source.contains("validateDeck(deck)"));
        assertTrue(source.contains("TextureBudget.evaluate"));
        assertTrue(source.contains("RenderFailureReason.TEXTURE_UPLOAD"));
        assertTrue(source.contains("releaseDeck(deck.getGenerationId(), DeckReleaseReason.FAILED)"));
    }

    @Test
    public void productionApiIsVersionedAndDocumented() throws IOException {
        String apiSource = source("PlayLikeCurlApi.java");
        String apiDocumentation = Files.readString(
                Path.of("PRODUCTION_API.md"),
                StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

        assertTrue(apiSource.contains("public static final int PRODUCTION_API_VERSION"));
        assertTrue(apiDocumentation.contains("bitmap page decks"));
        assertTrue(apiDocumentation.contains("bitmap lease"));
        assertTrue(apiDocumentation.contains("opaque argb_8888 base pages"));
        assertTrue(apiDocumentation.contains("premultiplied argb_8888 overlays"));
        assertTrue(apiDocumentation.contains("context recreation"));
    }

    @Test
    public void optionalOverlayUsesTheBasePageMeshAndTextureCoordinates()
            throws IOException {
        String imageSource = source("PageImage.java");
        String rendererSource = source("PageRenderer.java");

        assertTrue(imageSource.contains("getOverlayContent"));
        assertTrue(imageSource.contains("overlayIdentityKey"));
        assertTrue(rendererSource.contains("uniform sampler2D uOverlayTexture"));
        assertTrue(rendererSource.contains("uniform float uHasOverlay"));
        assertTrue(rendererSource.contains("drawPageTextures"));
        assertFalse(rendererSource.contains("overlayMesh"));
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(
                Path.of("src/main/java/karacken/curl/" + fileName),
                StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        assertTrue("Missing method " + signature, signatureIndex >= 0);
        int openBrace = source.indexOf('{', signatureIndex);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, index);
                }
            }
        }
        throw new AssertionError("Unclosed method " + signature);
    }
}
