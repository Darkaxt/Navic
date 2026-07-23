package karacken.curl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.Test;

public class Gles2RendererSourceTest {
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
    public void landscapeUsesTwoLeafViewportsWithoutChangingPortraitComposition() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("drawLandscapeSpread"));
        assertTrue(source.contains("viewportWidth / 2"));
        assertTrue(source.contains("LandscapeSpreadTransition"));
        assertTrue(source.contains("spreadNextLeftResource"));
        assertTrue(source.contains("mirroredLeftMesh"));
        assertTrue(source.contains("mirroredFrontMesh"));
        assertTrue(source.contains("drawPortraitPage"));
        assertTrue(source.contains("GLES20.glViewport(0, 0, viewportWidth, viewportHeight)"));
    }

    @Test
    public void rendererDrawsASeparateBlendedFoldShadow() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/karacken/curl/PageRenderer.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("SHADOW_FRAGMENT_SHADER"));
        assertTrue(source.contains("drawFoldShadow"));
        assertTrue(source.contains("GLES20.glEnable(GLES20.GL_BLEND)"));
        assertTrue(source.contains("GLES20.glBlendFunc("));
        assertTrue(source.contains("GLES20.GL_SRC_ALPHA"));
        assertTrue(source.contains("GLES20.GL_ONE_MINUS_SRC_ALPHA"));
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
                "public void releaseDeck(long generationId)");
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
                < releaseDeck.indexOf("deckCoordinator.release(generationId)"));
        assertTrue(deckReleased.indexOf("cancelGesture()")
                < deckReleased.indexOf("deckCoordinator.release(generationId)"));
        assertTrue(deckReleased.indexOf("leaseRegistry.markReleaseRequested(")
                < deckReleased.indexOf("deckCoordinator.release(generationId)"));
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
        assertTrue(submission.indexOf("leaseRegistry.markReleaseRequested(")
                < submission.indexOf("onOwnershipTransferred.run()"));
        assertTrue(submission.indexOf("onOwnershipTransferred.run()")
                < submission.indexOf("requestRender()"));
        assertTrue(surfaceSource.contains("onDeckSubmissionCapacityAvailable()"));
        assertTrue(deckReleased.indexOf("notifyDeckReleased(")
                < deckReleased.indexOf("takeCapacityAvailableSignal(generationId)"));
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

        assertTrue(surfaceDestroyed.indexOf("super.surfaceDestroyed(holder)")
                < surfaceDestroyed.indexOf("terminalDisposalGate.onSurfaceUnavailable"));
        assertTrue(detached.indexOf("super.onDetachedFromWindow()")
                < detached.indexOf("terminalDisposalGate.onSurfaceUnavailable"));
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
