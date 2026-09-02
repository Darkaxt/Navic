package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.Test;

public class Gles2RendererSourceTest {
    @Test
    public void releaseCleanupFailureDropsIndependentReferencesAndCompletesTerminalOnce()
            throws IOException {
        AtomicReference<String> activeReference = new AtomicReference<>("active");
        AtomicReference<String> replacementReference =
                new AtomicReference<>("replacement");
        AtomicInteger terminalCallbacks = new AtomicInteger();
        AtomicInteger independentCleanup = new AtomicInteger();

        Throwable failure = PageRendererReleaseTerminal.execute(
                terminalCallbacks::incrementAndGet,
                () -> {
                    activeReference.set(null);
                    throw new IllegalStateException("injected texture cleanup failure");
                },
                () -> {
                    replacementReference.set(null);
                    independentCleanup.incrementAndGet();
                });

        assertNotNull(failure);
        assertEquals(IllegalStateException.class, failure.getClass());
        assertNull(activeReference.get());
        assertNull(replacementReference.get());
        assertEquals(1, independentCleanup.get());
        assertEquals(1, terminalCallbacks.get());
        String rendererSource = source("PageRenderer.java");
        String rendererRelease = methodBody(
                rendererSource,
                "void releaseDeck(long generationId, DeckReleaseReason reason)");
        String rendererActivation = methodBody(
                rendererSource,
                "void activateDeck(long generationId)");
        assertTrue(rendererRelease.contains("PageRendererReleaseTerminal.execute("));
        assertTrue(rendererRelease.contains("events.onDeckReleased(generationId, reason)"));
        assertFalse(rendererRelease.contains(
                "if (!releasesActive && !releasesReplacement) {\n            return;"));
        assertTrue(rendererActivation.contains("PageRendererReleaseTerminal.execute("));
        assertTrue(rendererActivation.contains("events.onDeckReleased("));
    }

    @Test
    public void rendererUsesShadersWithoutFixedFunctionCalls() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("GLES20"));
        assertTrue(source.contains("glCreateShader"));
        assertTrue(source.contains("glUseProgram"));
        assertFalse(source.contains("GLU.gluPerspective"));
        assertFalse(source.contains("glMatrixMode"));
        assertFalse(source.contains("glVertexPointer"));
        assertFalse(source.contains("glTexCoordPointer"));
        assertFalse(source.contains("glEnableClientState"));
    }

    @Test
    public void surfaceRequestsAnOpenGlEs2ContextBeforeInstallingRenderer() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageSurfaceView.java"),
                StandardCharsets.UTF_8);

        int contextRequest = source.indexOf("setEGLContextClientVersion(2)");
        int rendererInstall = source.indexOf("setRenderer(renderer)");
        assertTrue(contextRequest >= 0);
        assertTrue(rendererInstall > contextRequest);
    }

    @Test
    public void rendererUsesPhysicalDisplayRectsForLeafViewportsAndGeometry() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("drawLandscapeSpread"));
        assertTrue(source.contains("displayRect("));
        assertTrue(source.contains("resource.getDisplayRect()"));
        assertTrue(source.contains("displayRect.glBottomPx(viewportHeight)"));
        assertTrue(source.contains("displayRect.getWidthPx()"));
        assertTrue(source.contains("displayRect.getHeightPx()"));
        assertTrue(source.contains("mesh.ensureGeometry(displayWidth, displayHeight, orientation)"));
        assertTrue(source.contains("LandscapeSpreadTransition"));
        assertTrue(source.contains("spreadNextLeftResource"));
        assertTrue(source.contains("mirroredLeftMesh"));
        assertTrue(source.contains("mirroredFrontMesh"));
        assertTrue(source.contains("drawPortraitPage"));
        assertTrue(source.contains(
                "configureDisplayViewport(displayRect, PageOrientation.PORTRAIT, 0f)"));
        assertTrue(source.contains("PlayLikeCurlModel.RIGHT_DEPTH"));
        assertTrue(source.contains("restingPlaneDepth"));
    }

    @Test
    public void translucentSurfaceClearsUncoveredPixelsWithOpaqueDeckMaterial()
            throws IOException {
        String renderer = source("PageRenderer.java");
        String surface = source("PageSurfaceView.java");
        String drawFrame = methodBody(renderer, "boolean drawFrame(GL10 ignored)");

        assertTrue(surface.contains("setEGLConfigChooser(8, 8, 8, 8, 16, 0)"));
        assertTrue(surface.contains("getHolder().setFormat(PixelFormat.TRANSLUCENT)"));
        assertTrue(renderer.contains("getUncoveredBackgroundColorArgb()"));
        int background = drawFrame.indexOf(
                "clearUncoveredBackground(activeDeck.getMaterial())");
        int clear = drawFrame.indexOf("GLES20.glClear(");
        assertTrue(background >= 0 && background < clear);
        assertFalse(drawFrame.contains("GLES20.glClearColor(0f, 0f, 0f, 0f)"));
    }

    @Test
    public void rendererOwnsClipReverseAndFixedBorderAcrossEveryFramePath()
            throws IOException {
        String source = source("PageRenderer.java");
        String portrait = methodBody(source, "private boolean drawPortraitPage()");
        String landscape = methodBody(source, "private boolean drawLandscapeSpread()");
        String page = methodBody(source, "private boolean drawPage(");
        String moving = methodBody(source, "private boolean drawMovingPage(");
        String textures = methodBody(source, "private void drawPageTextures(");

        int portraitMaterial = portrait.indexOf("drawFixedPageMaterial(");
        int portraitMotion = portrait.indexOf("if (portraitModel.getActivePage()");
        assertTrue(portraitMaterial >= 0 && portraitMaterial < portraitMotion);
        int landscapeMaterial = landscape.indexOf("drawFixedPageMaterial(");
        int landscapeMotion = landscape.indexOf("LandscapeSpreadTransition transition");
        assertTrue(landscapeMaterial >= 0 && landscapeMaterial < landscapeMotion);
        assertTrue(source.contains("getFrontPaperColorArgb()"));
        assertTrue(source.contains("getReversePaperColorArgb()"));
        assertTrue(source.contains("getFixedBorderColorArgb()"));
        assertTrue(source.contains("uniform float uReverseMaterialMix"));
        assertFalse(source.contains("gl_FrontFacing"));
        assertTrue(moving.contains(
                "PageReverseMaterialMix.fromCurlPosition(state.getCurlPosition())"));
        assertTrue(textures.contains(
                "GLES20.glUniform1f(reverseMaterialMixUniform, reverseMaterialMix)"));
        assertTrue(source.contains("mix(frontColor, uReversePaperColor, uReverseMaterialMix)"));
        int clip = page.indexOf("GLES20.glEnable(GLES20.GL_SCISSOR_TEST)");
        int draw = page.indexOf("GLES20.glDrawElements(");
        int unclip = page.indexOf("GLES20.glDisable(GLES20.GL_SCISSOR_TEST)");
        assertTrue(clip >= 0 && clip < draw && unclip > draw);
    }

    @Test
    public void hiddenSurfacePresentationWaitsForAnArmedCompleteFrame() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageSurfaceView.java"),
                StandardCharsets.UTF_8);
        String request = methodBody(
                source,
                "public long requestNextPresentedFrame(Runnable callback)");
        String draw = methodBody(
                source,
                "public void onDrawFrame(GL10 gl)");
        String detach = methodBody(source, "public void detach()");
        String visibility = methodBody(source, "public void setVisible(boolean visible)");
        String cancelGesture = methodBody(source, "public void cancelGesture(long gestureId)");
        String renderFailure = methodBody(
                source,
                "private void handleRenderFailure(RenderFailure failure)");
        String surfaceDestroyed = methodBody(
                source,
                "public void surfaceDestroyed(SurfaceHolder holder)");
        String releaseDeck = methodBody(
                source,
                "public PageSurfaceDeckReleaseResult releaseDeck(long generationId)");
        String deckReleased = methodBody(
                source,
                "private void handleDeckReleased(");
        String dispose = methodBody(source, "private void startDisposeIfNeeded()");

        assertTrue(source.contains("PresentedFrameRequest"));
        assertTrue(request.contains("queueEvent(() ->"));
        assertTrue(request.contains("presentedFrameRequest.arm(requestId)"));
        assertTrue(request.contains("requestRender()"));
        assertTrue(draw.indexOf("renderer.drawFrame(gl)")
                < draw.indexOf("presentedFrameRequest.markRendered()"));
        assertTrue(draw.contains("postOnAnimation("));
        assertTrue(source.contains("cancelPresentedFrameRequest(long requestId)"));
        assertTrue(detach.contains("presentedFrameRequest.cancelAll()"));
        assertTrue(visibility.contains("presentedFrameRequest.cancelAll()"));
        assertFalse(cancelGesture.contains("presentedFrameRequest.cancelAll()"));
        assertTrue(renderFailure.contains("presentedFrameRequest.cancelAll()"));
        assertTrue(surfaceDestroyed.contains("presentedFrameRequest.cancelAll()"));
        assertTrue(releaseDeck.contains("presentedFrameRequest.cancelAll()"));
        assertTrue(deckReleased.contains("presentedFrameRequest.cancelAll()"));
        assertTrue(dispose.contains("presentedFrameRequest.cancelAll()"));
        assertTrue(source.contains("presentedFrameRequest.pendingCount()"));
    }

    @Test
    public void destinationActivationIsQueuedBeforeSettlementPublication() throws IOException {
        String source = source("PageSurfaceView.java");
        String settlement = methodBody(
                source,
                "private void completeSettlement(Settlement settlement, SettlementContext context)");

        int activation = settlement.indexOf(
                "queuePromotion(promotion, promoted)");
        int render = settlement.indexOf("requestRender()");
        int completion = settlement.indexOf("onSettlementCompleted(");
        assertTrue(activation >= 0);
        assertTrue(render > activation);
        assertTrue(completion > render);
    }

    @Test
    public void rendererClearsTransparentPixelsBeforeAnEmptyDeckReturn() throws IOException {
        String renderer = source("PageRenderer.java");
        String drawFrame = methodBody(renderer, "boolean drawFrame(GL10 ignored)");

        int clear = drawFrame.indexOf("GLES20.glClear(");
        int emptyDeck = drawFrame.indexOf("if (activeDeck == null)");
        assertTrue(clear >= 0);
        assertTrue(emptyDeck > clear);
    }

    @Test
    public void rendererDrawsASeparateBlendedFoldShadow() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("SHADOW_FRAGMENT_SHADER"));
        assertTrue(source.contains("drawFoldShadow"));
        assertTrue(source.contains("GLES20.glEnable(GLES20.GL_BLEND)"));
        assertTrue(source.contains("GLES20.glBlendFuncSeparate("));
        assertTrue(source.contains("GLES20.GL_SRC_ALPHA"));
        assertTrue(source.contains("GLES20.GL_ONE_MINUS_SRC_ALPHA"));
        assertTrue(source.contains("GLES20.GL_ONE,"));
        assertFalse(source.contains("GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA"));
    }

    @Test
    public void clientBufferShadowPassUnbindsMeshBufferObjects() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageRenderer.java"),
                StandardCharsets.UTF_8);

        int shadowPass = source.indexOf("private void drawFoldShadow");
        int arrayBufferUnbind = source.indexOf(
                "GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)", shadowPass);
        int elementBufferUnbind = source.indexOf(
                "GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)", shadowPass);
        int firstClientAttribute = source.indexOf("GLES20.glVertexAttribPointer(", shadowPass);
        int clientIndexDraw = source.indexOf("GLES20.glDrawElements(", shadowPass);

        assertTrue(arrayBufferUnbind > shadowPass);
        assertTrue(elementBufferUnbind > shadowPass);
        assertTrue(arrayBufferUnbind < firstClientAttribute);
        assertTrue(elementBufferUnbind < clientIndexDraw);
    }

    @Test
    public void terminalDisposalIsOrderedBoundedAndFailureIsolated() throws IOException {
        String surfaceSource = source("PageSurfaceView.java");
        String rendererSource = source("PageRenderer.java");
        String submissionGateSource = source("PageSurfaceDeckSubmissionGate.java");
        String submission = methodBody(
                surfaceSource,
                "public PageSurfaceDeckSubmissionResult submitDeckWithResult(\n"
                        + "            PageDeck<Bitmap> deck,\n"
                        + "            Runnable onOwnershipTransferred)");
        String glDispose = methodBody(surfaceSource, "private void disposeOnGlThread()");
        String glPublication = methodBody(
                surfaceSource,
                "private void finishGlDisposalOnMain");
        String detachedDispose = methodBody(
                surfaceSource,
                "private void finishDetachedDisposal()");
        String pauseForDetachedDispose = methodBody(
                surfaceSource,
                "private boolean pauseForDetachedDisposal()");
        String surfaceDestroyed = methodBody(
                surfaceSource,
                "public void surfaceDestroyed(SurfaceHolder holder)");
        String detached = methodBody(
                surfaceSource,
                "protected void onDetachedFromWindow()");
        String finish = methodBody(
                surfaceSource,
                "private void finishDisposeAfterGl");
        String deckReleased = methodBody(
                surfaceSource,
                "private void handleDeckReleased");
        String releaseDeck = methodBody(
                surfaceSource,
                "public PageSurfaceDeckReleaseResult releaseDeck(long generationId)");
        String automaticRelease = methodBody(
                surfaceSource,
                "private boolean queueDeckRelease(");
        String promotionRelease = methodBody(
                surfaceSource,
                "private boolean queuePromotion(");
        String startDispose = methodBody(
                surfaceSource,
                "private void startDisposeIfNeeded()");
        String rendererDispose = methodBody(rendererSource, "void dispose()");

        assertTrue(glDispose.indexOf("renderer.dispose()")
                < glDispose.indexOf("terminalDisposalGate.completeGlExecution()"));
        assertTrue(glDispose.indexOf("terminalDisposalGate.completeGlExecution()")
                < glDispose.indexOf("enqueueOrRetainMainTerminal("));
        assertTrue(glPublication.indexOf("finishDisposeAfterGl(")
                < glPublication.indexOf("terminalDisposalGate.completeGlPublication()"));
        assertTrue(glPublication.contains("terminalDisposalGate.abandonGlPublication()"));
        assertTrue(glDispose.contains("finally"));
        assertTrue(surfaceSource.contains("retainedOwnershipMainTerminalAction"));
        assertTrue(surfaceSource.contains("retainedDisposalMainTerminalAction"));
        assertTrue(surfaceSource.contains("mainHandler.post(counted)"));
        assertTrue(surfaceSource.contains("registerMainTerminalExecutor("));
        assertTrue(surfaceSource.contains("mainTerminalExecutorRegistered"));
        assertTrue(surfaceSource.contains(
                "Main terminal executor is already registered"));
        assertTrue(surfaceSource.contains("executor.execute(counted)"));
        assertTrue(surfaceSource.contains(
                "pendingMainTerminalActions.incrementAndGet()"));
        assertTrue(surfaceSource.contains(
                "MAIN_TERMINAL_ACTION_LIMIT =\n            OWNERSHIP_CALLBACK_LIMIT + REQUIRED_DISPOSAL_CALLBACK_LIMIT"));
        assertTrue(surfaceSource.contains(
                "PageSurfaceDisposalStage.MAIN_TERMINAL_EXECUTOR"));
        assertTrue(surfaceSource.contains(
                "if (disposeStarted && disposedResult == null)"));
        assertTrue(surfaceSource.contains("finishDetachedDisposal();"));
        assertTrue(surfaceDestroyed.indexOf("drainRetainedMainTerminal()")
                < surfaceDestroyed.indexOf("terminalDisposalGate.onSurfaceUnavailable"));
        assertTrue(detached.indexOf("drainRetainedMainTerminal()")
                < detached.indexOf("terminalDisposalGate.onSurfaceUnavailable"));
        assertTrue(surfaceDestroyed.contains("failLiveOwnershipRequests()"));
        assertTrue(detached.contains("failLiveOwnershipRequests()"));
        assertTrue(detachedDispose.contains("terminalDisposalGate.glOwnsExecution()"));
        assertTrue(detachedDispose.indexOf("pauseForDetachedDisposal()")
                < detachedDispose.indexOf("renderer.abandonClientState()"));
        assertTrue(detachedDispose.indexOf("if (glPaused)")
                < detachedDispose.indexOf("renderer.abandonClientState()"));
        assertTrue(pauseForDetachedDispose.contains("onResume()"));
        assertTrue(pauseForDetachedDispose.indexOf("onResume()")
                < pauseForDetachedDispose.indexOf("onPause()"));
        assertTrue(pauseForDetachedDispose.contains("onPause()"));
        assertTrue(pauseForDetachedDispose.contains("return false"));
        assertTrue(detachedDispose.contains("clientOwnershipReleased = true"));
        assertTrue(finish.contains("if (releaseDeckLeases)"));
        assertTrue(finish.indexOf("leaseRegistry.releaseAll(")
                < finish.indexOf("int activeDeckLeases"));
        assertTrue(surfaceSource.contains(
                "mainHandler.post(() -> handleDeckReleased(generationId, reason))"));
        assertTrue(releaseDeck.indexOf("cancelGesture()")
                < releaseDeck.indexOf("releaseGate.request("));
        assertTrue(releaseDeck.contains(
                "result.getStatus() == PageSurfaceDeckReleaseResult.Status.ACCEPTED"));
        assertTrue(releaseDeck.indexOf("releaseGate.request(")
                < releaseDeck.indexOf("preparedGenerations.remove(generationId)"));
        assertFalse(releaseDeck.contains("deckCoordinator.release(generationId)"));
        assertTrue(deckReleased.indexOf("releaseGate.rendererDetached(generationId, reason)")
                < deckReleased.indexOf("releaseGate.complete(generationId)"));
        assertTrue(deckReleased.indexOf("releaseGate.complete(generationId)")
                < deckReleased.indexOf("leaseRegistry.release("));
        assertTrue(deckReleased.indexOf("cancelGesture()")
                < deckReleased.indexOf("releaseGate.rendererDetached(generationId, reason)"));
        assertFalse(deckReleased.contains("leaseRegistry.markReleaseRequested("));
        assertFalse(deckReleased.contains("deckCoordinator.release(generationId)"));
        assertTrue(submission.contains("releaseGate.queueAutomatic("));
        assertFalse(submission.contains("leaseRegistry.markReleaseRequested("));
        assertTrue(automaticRelease.contains("releaseGate.queueAutomatic("));
        assertTrue(promotionRelease.contains("releaseGate.queueAutomatic("));
        assertTrue(startDispose.contains("releaseGate.acceptTerminal("));
        assertTrue(startDispose.contains("releaseGate.close()"));
        assertTrue(surfaceSource.contains("holderSurfaceAvailable"));
        assertTrue(surfaceSource.contains("surfaceDestroyed(SurfaceHolder holder)"));
        assertTrue(surfaceSource.contains("PageSurfaceDisposalStage.SURFACE_RESUME"));
        assertTrue(surfaceSource.contains("PageSurfaceDisposalStage.PRE_GL_SETUP"));
        assertTrue(finish.indexOf("disposedResult = result")
                < finish.indexOf("requiredDisposeCallback.complete(result)"));
        assertTrue(finish.indexOf("requiredDisposeCallback.complete(result)")
                < finish.indexOf("disposeCallbacks.complete(result)"));
        assertTrue(finish.indexOf("disposeCallbacks.complete(result)")
                < finish.indexOf("mainTerminalExecutor = null"));
        assertTrue(finish.indexOf("disposedOwnershipSnapshot = snapshot")
                < finish.indexOf("terminalOwnershipCallbacks.complete("));
        assertTrue(finish.contains("releaseInFlightDeckLeases"));
        assertTrue(surfaceSource.contains(
                "PageSurfaceDisposalStage.SETTLEMENT_CANCEL_CALLBACK"));
        assertTrue(surfaceSource.contains(
                "PageSurfaceDisposalStage.DECK_RELEASE_CALLBACK"));
        assertTrue(surfaceSource.contains(
                "PageSurfaceRequiredTerminalCallback<PageSurfaceDisposalResult>"));
        assertTrue(surfaceSource.contains(
                "PageSurfaceTerminalCallbacks<PageSurfaceDisposalResult>"));
        assertTrue(surfaceSource.contains(
                "PageSurfaceTerminalCallbacks<PageSurfaceOwnershipResult>"));
        assertTrue(surfaceSource.contains(
                "PageSurfaceOwnershipSnapshotCoordinator ownershipSnapshotCoordinator"));
        assertTrue(surfaceSource.contains(
                "new PageDeckCoordinator<>(this::advanceOwnershipEpoch)"));
        assertTrue(surfaceSource.contains(
                "new DeckLeaseRegistry(this::advanceOwnershipEpoch)"));
        assertTrue(surfaceSource.contains(
                "ownershipSnapshotCoordinator.request(callback)"));
        assertTrue(surfaceSource.contains(
                "setOwnershipCallbackCapacityListener(Runnable listener)"));
        assertTrue(surfaceSource.contains(
                "clearOwnershipCallbackCapacityListener(Runnable listener)"));
        assertTrue(surfaceSource.contains(
                "ownershipSnapshotCapacityEdge"));
        assertTrue(surfaceSource.contains(
                "ownershipSnapshotCoordinator.drain()"));
        assertTrue(startDispose.indexOf(
                "ownershipSnapshotCoordinator.clearCapacityAvailableListener(")
                < startDispose.indexOf("ownershipSnapshotCoordinator.drain()"));
        assertTrue(startDispose.indexOf("ownershipSnapshotCoordinator.drain()")
                < startDispose.indexOf("disposeStarted = true"));
        assertTrue(surfaceSource.contains(
                "if (disposeStarted) {\n"
                        + "                                    return PageSurfaceOwnershipSnapshotCoordinator"));
        assertFalse(surfaceSource.contains(
                "PageSurfaceSnapshotCallbacks<PageSurfaceOwnershipSnapshot>"));
        assertFalse(surfaceSource.contains("pendingOwnershipCallbacks"));
        assertFalse(surfaceSource.contains("ownershipSnapshotInFlight"));
        assertFalse(surfaceSource.contains("finishLiveOwnershipSnapshot"));
        assertFalse(surfaceSource.contains("completeUnavailableOwnershipRequests"));
        assertTrue(surfaceSource.contains(
                "if (enqueueMainHandlerTerminal(action))"));
        assertFalse(surfaceSource.contains(
                "if (requiredDisposal && enqueueMainHandlerTerminal(action))"));
        assertTrue(surfaceSource.contains(
                "mainTerminalExecutor = executor;\n        drainRetainedMainTerminal();"));
        assertTrue(surfaceSource.contains("REQUIRED_DISPOSAL_CALLBACK_LIMIT"));
        assertTrue(surfaceSource.contains("AUXILIARY_DISPOSAL_CALLBACK_LIMIT"));
        assertTrue(surfaceSource.contains("OWNERSHIP_CALLBACK_LIMIT"));
        assertTrue(surfaceSource.contains(
                "DisposalCallbackRegistration.CALLBACK_CAPACITY"));
        assertTrue(surfaceSource.contains("getPendingCallbackCount()"));
        assertTrue(surfaceSource.contains("getPendingCallbackLimit()"));
        assertTrue(surfaceSource.contains(
                "public void dispose() {\n        requireMainThread();\n        startDisposeIfNeeded();"));
        assertFalse(surfaceSource.contains("dispose(result -> {})"));
        assertTrue(surfaceSource.contains("failure.getClass().getName()"));
        assertFalse(surfaceSource.contains("failure.getMessage()"));
        assertFalse(surfaceSource.contains("Log.getStackTraceString"));
        assertFalse(surfaceSource.contains(
                "private void releaseAllOutstandingLeases()"));
        assertTrue(rendererDispose.contains("mirroredRightMesh::dispose"));
        assertTrue(rendererSource.contains("addSuppressed"));
        assertTrue(submissionGateSource.contains(
                "DeckRejectionReason.RESOURCE_CAPACITY"));
        assertTrue(surfaceSource.contains("PageSurfaceDeckSubmissionGate<"));
        assertTrue(surfaceSource.contains("public void submitDeck("));
        assertTrue(surfaceSource.contains(
                "public PageSurfaceDeckSubmissionResult submitDeckWithResult("));
        assertTrue(surfaceSource.contains("if (!isSupportedDeckType(deck))"));
        assertTrue(surfaceSource.contains("DeckRejectionReason.INVALID_CONTENT"));
        assertTrue(surfaceSource.indexOf("if (!isSupportedDeckType(deck))")
                < surfaceSource.indexOf("submissionGate.submit(deck, pageSurfaceListener)"));
        assertTrue(submission.indexOf("queueEvent(() -> {")
                < submission.indexOf("onOwnershipTransferred.run()"));
        assertTrue(submission.indexOf("renderer.releaseDeck(")
                < submission.indexOf("renderer.prepareDeck(deck, activateWhenPrepared)"));
        assertTrue(submission.contains("submissionGate.rollbackAccepted(deck, gated)"));
        assertTrue(submission.indexOf("releaseGate.queueAutomatic(")
                < submission.indexOf("onOwnershipTransferred.run()"));
        assertTrue(submission.indexOf("onOwnershipTransferred.run()")
                < submission.indexOf("requestRender()"));
        assertTrue(surfaceSource.contains("onDeckSubmissionCapacityAvailable()"));
        assertTrue(deckReleased.indexOf("notifyDeckReleased(")
                < deckReleased.indexOf("notifyDeckSubmissionCapacityIfAvailable(generationId)"));
        assertTrue(surfaceSource.contains("getDeckLeaseLimit()"));
    }

    @Test
    public void surfaceLossNotifiesTerminalGateOnlyAfterSuperclassCallbacks()
            throws IOException {
        String surfaceSource = source("PageSurfaceView.java");
        String surfaceDestroyed = methodBody(
                surfaceSource,
                "public void surfaceDestroyed(SurfaceHolder holder)");
        String detached = methodBody(
                surfaceSource,
                "protected void onDetachedFromWindow()");
        String logicalDetach = methodBody(surfaceSource, "public void detach()");
        String terminalRelease = methodBody(
                surfaceSource,
                "private void terminallyAbandonAcceptedRendererReleases()");

        assertTrue(surfaceDestroyed.indexOf("super.surfaceDestroyed(holder)")
                < surfaceDestroyed.indexOf("terminallyAbandonAcceptedRendererReleases()"));
        assertTrue(surfaceDestroyed.indexOf("super.surfaceDestroyed(holder)")
                < surfaceDestroyed.indexOf("terminalDisposalGate.onSurfaceUnavailable"));
        assertTrue(logicalDetach.indexOf("onPause()")
                < logicalDetach.indexOf("terminallyAbandonAcceptedRendererReleases()"));
        assertTrue(terminalRelease.contains("renderer::terminallyAbandonDeck"));
        assertTrue(terminalRelease.indexOf("releaseGate.complete(generationId)")
                < terminalRelease.indexOf("leaseRegistry.release("));
        assertTrue(detached.indexOf("super.onDetachedFromWindow()")
                < detached.indexOf("terminalDisposalGate.onSurfaceUnavailable"));
        assertTrue(surfaceSource.contains("onRendererAvailabilityRestored()"));
    }

    @Test
    public void surfaceLossInvalidatesPendingAndAppliedDynamicPageOverlays()
            throws IOException {
        String surface = source("PageSurfaceView.java");
        String listener = source("PageSurfaceListener.java");
        String renderer = source("PageRenderer.java");
        String surfaceDestroyed = methodBody(
                surface,
                "public void surfaceDestroyed(SurfaceHolder holder)");
        String surfaceCreated = methodBody(
                renderer,
                "public void onSurfaceCreated(GL10 ignored, EGLConfig config)");

        assertTrue(surfaceDestroyed.contains("abandonPendingPageOverlayUpdate()"));
        assertTrue(surfaceDestroyed.contains("renderer.invalidatePageOverlays()"));
        assertTrue(surfaceDestroyed.contains("pageSurfaceListener.onPageOverlayStateInvalidated()"));
        assertTrue(listener.contains("default void onPageOverlayStateInvalidated() {}"));
        assertTrue(renderer.contains("void invalidatePageOverlays()"));
        assertTrue(renderer.contains("dynamicPageOverlayEpoch.incrementAndGet()"));
        assertTrue(renderer.contains("expectedOverlayEpoch != dynamicPageOverlayEpoch.get()"));
        assertTrue(renderer.contains("dynamicPageOverlays.clear()"));
        assertTrue(surfaceCreated.contains("glReady = false"));
        assertTrue(surfaceCreated.contains("dynamicPageOverlays.clear()"));
        assertTrue(surfaceCreated.indexOf("glReady = false")
                < surfaceCreated.indexOf("dynamicPageOverlays.clear()"));
        assertFalse(surfaceCreated.contains("rehydrateDynamicPageOverlays()"));
    }

    @Test
    public void dynamicPageOverlaysReplaceOnlyTheCurrentTextureAndUseThePageMesh()
            throws IOException {
        String surface = source("PageSurfaceView.java");
        String renderer = source("PageRenderer.java");
        String replace = methodBody(
                surface,
                "public PageOverlayUpdateResult replacePageOverlays(");
        String draw = methodBody(
                renderer,
                "private boolean drawPage(");
        String overlayLookup = methodBody(
                renderer,
                "private GpuTexture overlayTexture(PageImage<Bitmap> page)");

        assertTrue(surface.contains("PageOverlayUpdateGate.evaluate("));
        assertTrue(replace.contains("gestureAccepted || settlementRunning"));
        assertTrue(replace.contains("renderer.replacePageOverlays("));
        assertTrue(renderer.contains("PageOverlayReplacementStore<String, DynamicPageOverlayTexture>"));
        assertTrue(renderer.contains("dynamicPageOverlays.replace("));
        assertTrue(renderer.contains("dynamicPageOverlays.clear()"));
        assertTrue(overlayLookup.contains("dynamicPageOverlays.get(page.identityKey())"));
        assertTrue(draw.contains("GpuTexture overlayTexture = overlayTexture(resource)"));
        assertTrue(draw.indexOf("mesh.ensureGeometry(") < draw.indexOf("drawPageTextures("));
        assertTrue(draw.indexOf("drawPageTextures(") < draw.indexOf("GLES20.glDrawElements("));
        assertTrue(renderer.contains("bitmap.recycle()"));
    }

    @Test
    public void productionSourcesAvoidJavaNineCollectionFactories() throws IOException {
        Path production = Path.of("src/main/java/karacken/curl");
        try (Stream<Path> files = Files.walk(production)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path, StandardCharsets.UTF_8);
                            assertFalse(path + " uses List.of", source.contains("List.of("));
                            assertFalse(
                                    path + " uses List.copyOf",
                                    source.contains("List.copyOf("));
                        } catch (IOException failure) {
                            throw new AssertionError(failure);
                        }
                    });
        }
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
