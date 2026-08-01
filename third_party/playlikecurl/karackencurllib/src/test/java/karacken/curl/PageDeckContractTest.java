package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

public class PageDeckContractTest {
    private static final int PAPER_COLOR = 0xFFF5F2EA;

    @Test
    public void portraitDeckCarriesOpaqueIdentityAndExactSlots() {
        PageImage<String> previous = page(7, "chapter-2/page-4", 4);
        PageImage<String> current = page(7, "chapter-2/page-5", 5);
        PageImage<String> next = page(7, "chapter-2/page-6", 6);

        PortraitPageDeck<String> deck = new PortraitPageDeck<>(
                previous, current, next);

        assertEquals(7, deck.getGenerationId());
        assertEquals(PageDeckMode.PORTRAIT, deck.getMode());
        assertSame(previous, deck.getPrevious());
        assertSame(current, deck.getCurrent());
        assertSame(next, deck.getNext());
        assertEquals("chapter-2/page-5", deck.getCurrent().getLogicalPageId());
        assertEquals("bitmap-5", deck.getCurrent().getContent());
    }

    @Test
    public void landscapeDeckCarriesSixPhysicalLeaves() {
        LandscapePageDeck<String> deck = new LandscapePageDeck<>(
                page(9, "previous-left", 0),
                page(9, "previous-right", 1),
                page(9, "current-left", 2),
                page(9, "current-right", 3),
                page(9, "next-left", 4),
                page(9, "next-right", 5));

        assertEquals(9, deck.getGenerationId());
        assertEquals(PageDeckMode.LANDSCAPE, deck.getMode());
        assertEquals("previous-left", deck.getPreviousLeft().getLogicalPageId());
        assertEquals("previous-right", deck.getPreviousRight().getLogicalPageId());
        assertEquals("current-left", deck.getCurrentLeft().getLogicalPageId());
        assertEquals("current-right", deck.getCurrentRight().getLogicalPageId());
        assertEquals("next-left", deck.getNextLeft().getLogicalPageId());
        assertEquals("next-right", deck.getNextRight().getLogicalPageId());
    }

    @Test
    public void deckRejectsPagesFromDifferentGenerations() {
        assertThrows(IllegalArgumentException.class, () -> new PortraitPageDeck<>(
                page(3, "previous", 0),
                page(4, "current", 1),
                page(3, "next", 2)));
    }

    @Test
    public void portraitBoundaryDuplicationDoesNotCreateFakePages() {
        PageImage<String> first = page(11, "first", 0);
        PageImage<String> second = page(11, "second", 1);
        PortraitPageDeck<String> deck = new PortraitPageDeck<>(first, first, second);

        PageDeckWindow<String> window = PageDeckWindow.from(deck);

        assertEquals(Arrays.asList(first, second), window.getPages());
        assertEquals(0, window.getCurrentIndex());
    }

    @Test
    public void landscapeWindowPreservesCurrentLeftPosition() {
        PageImage<String> previousLeft = page(13, "previous-left", 0);
        PageImage<String> previousRight = page(13, "previous-right", 1);
        PageImage<String> currentLeft = page(13, "current-left", 2);
        PageImage<String> currentRight = page(13, "current-right", 3);
        PageImage<String> nextLeft = page(13, "next-left", 4);
        PageImage<String> nextRight = page(13, "next-right", 5);
        LandscapePageDeck<String> deck = new LandscapePageDeck<>(
                previousLeft,
                previousRight,
                currentLeft,
                currentRight,
                nextLeft,
                nextRight);

        PageDeckWindow<String> window = PageDeckWindow.from(deck);

        assertEquals(
                Arrays.asList(
                        previousLeft,
                        previousRight,
                        currentLeft,
                        currentRight,
                        nextLeft,
                        nextRight),
                window.getPages());
        assertEquals(2, window.getCurrentIndex());
    }

    @Test
    public void pageImageCarriesOptionalGenerationBoundOverlay() {
        PageImage<String> baseOnly = page(17, "base-only", 0);
        PageImage<String> highlighted = new PageImage<>(
                17,
                "highlighted",
                1,
                1200,
                1800,
                "base-bitmap",
                "highlight-overlay");

        assertFalse(baseOnly.hasOverlay());
        assertTrue(highlighted.hasOverlay());
        assertEquals("highlight-overlay", highlighted.getOverlayContent());
        assertEquals(
                highlighted.identityKey() + "\u0000overlay",
                highlighted.overlayIdentityKey());
    }

    @Test
    public void portraitBoundaryUsesPixelsAsFillerButCannotNavigatePastBoundary() {
        PageImage<String> first = page(21, "first", 0);
        PageImage<String> second = page(21, "second", 1);
        PortraitPageDeck<String> deck = new PortraitPageDeck<>(
                first, first, second, false, true);

        assertFalse(deck.canTurn(PageChange.PREVIOUS));
        assertTrue(deck.canTurn(PageChange.NEXT));
        assertSame(first, deck.getSettlementPage(PageChange.NONE));
        assertSame(first, deck.getSettlementPage(PageChange.PREVIOUS));
        assertSame(second, deck.getSettlementPage(PageChange.NEXT));
    }

    @Test
    public void landscapeBoundaryRejectsWholeUnavailableSpread() {
        PageImage<String> previousLeft = page(22, "previous-left", 0);
        PageImage<String> previousRight = page(22, "previous-right", 1);
        PageImage<String> currentLeft = page(22, "current-left", 2);
        PageImage<String> currentRight = page(22, "current-right", 3);
        PageImage<String> nextLeft = page(22, "next-left", 4);
        PageImage<String> nextRight = page(22, "next-right", 5);
        LandscapePageDeck<String> deck = new LandscapePageDeck<>(
                previousLeft,
                previousRight,
                currentLeft,
                currentRight,
                nextLeft,
                nextRight,
                false,
                true);

        assertFalse(deck.canTurn(PageChange.PREVIOUS));
        assertTrue(deck.canTurn(PageChange.NEXT));
    }

    @Test
    public void legacyPortraitConstructorRemainsSourceCompatible() {
        PortraitPageDeck<String> deck = new PortraitPageDeck<>(
                page(23, "previous", 0),
                page(23, "current", 1),
                page(23, "next", 2));

        assertTrue(deck.canTurn(PageChange.PREVIOUS));
        assertTrue(deck.canTurn(PageChange.NEXT));
    }

    @Test
    public void legacyLandscapeConstructorRemainsSourceCompatible() {
        LandscapePageDeck<String> deck = new LandscapePageDeck<>(
                page(24, "previous-left", 0),
                page(24, "previous-right", 1),
                page(24, "current-left", 2),
                page(24, "current-right", 3),
                page(24, "next-left", 4),
                page(24, "next-right", 5));

        assertTrue(deck.canTurn(PageChange.PREVIOUS));
        assertTrue(deck.canTurn(PageChange.NEXT));
    }

    @Test
    public void rtlLandscapeSettlementUsesCanonicalLogicalAnchorNotPhysicalLeft() {
        PageImage<String> previousLeft = page(25, "previous-left", 0);
        PageImage<String> previousRight = page(25, "previous-right", 4);
        PageImage<String> currentLeft = page(25, "current-left", 2);
        PageImage<String> currentRight = page(25, "current-right", 1);
        PageImage<String> nextLeft = page(25, "next-left", 5);
        PageImage<String> nextRight = page(25, "next-right", 3);
        LandscapePageDeck<String> deck = new LandscapePageDeck<>(
                previousLeft,
                previousRight,
                currentLeft,
                currentRight,
                nextLeft,
                nextRight,
                true,
                true,
                previousLeft,
                currentRight,
                nextRight);

        assertEquals(0, deck.getSettlementPage(PageChange.PREVIOUS).getOrdinal());
        assertEquals(1, deck.getSettlementPage(PageChange.NONE).getOrdinal());
        assertEquals(3, deck.getSettlementPage(PageChange.NEXT).getOrdinal());
    }

    @Test
    public void rtlMirrorsPhysicalCoordinateAndFlingDirection() {
        assertEquals(760f, ReadingDirection.RIGHT_TO_LEFT.toLogicalX(240f, 1000f), 0.001f);
        assertEquals(
                PageChange.PREVIOUS,
                ReadingDirection.RIGHT_TO_LEFT.pageChangeForVelocity(-1f));
        assertEquals(
                PageChange.NEXT,
                ReadingDirection.RIGHT_TO_LEFT.pageChangeForVelocity(0f));
        assertEquals(
                PageChange.NEXT,
                ReadingDirection.RIGHT_TO_LEFT.pageChangeForVelocity(1f));
    }

    @Test
    public void ltrKeepsExistingDirection() {
        assertEquals(240f, ReadingDirection.LEFT_TO_RIGHT.toLogicalX(240f, 1000f), 0.001f);
        assertEquals(
                PageChange.NEXT,
                ReadingDirection.LEFT_TO_RIGHT.pageChangeForVelocity(-1f));
        assertEquals(
                PageChange.PREVIOUS,
                ReadingDirection.LEFT_TO_RIGHT.pageChangeForVelocity(0f));
        assertEquals(
                PageChange.PREVIOUS,
                ReadingDirection.LEFT_TO_RIGHT.pageChangeForVelocity(1f));
    }

    @Test
    public void rejectedForwardDragRestoresWithoutChangingPosition() {
        PlayLikeCurlModel model = new PlayLikeCurlModel(3, 1);
        Settlement rejected = dragForward(model);
        assertEquals(PageChange.NEXT, rejected.getPageChange());

        Settlement restoration = PageSurfaceView.boundaryRestoreSettlement(model);
        assertEquals(PlayLikeCurlModel.LEFT_ENDPOINT_PERCENT, restoration.getTargetPercent());
        assertEquals(PlayLikeCurlModel.SETTLEMENT_DURATION_MILLIS, restoration.getDurationMillis());
        assertEquals(SettlementInterpolator.ACCELERATE_DECELERATE, restoration.getInterpolator());
        assertEquals(PageChange.NONE, restoration.getPageChange());
        model.completeSettlement(restoration);

        assertEquals(1, model.getCurrentPosition());
        assertEquals(PageChange.NEXT, dragForward(model).getPageChange());
    }

    @Test
    public void rejectedBackwardDragRestoresAndAllowsNextGesture() {
        PlayLikeCurlModel model = new PlayLikeCurlModel(3, 1);
        Settlement rejected = dragBackward(model);
        assertEquals(PageChange.PREVIOUS, rejected.getPageChange());

        Settlement restoration = PageSurfaceView.boundaryRestoreSettlement(model);
        assertEquals(PlayLikeCurlModel.RIGHT_ENDPOINT_PERCENT, restoration.getTargetPercent());
        assertEquals(PlayLikeCurlModel.SETTLEMENT_DURATION_MILLIS, restoration.getDurationMillis());
        assertEquals(SettlementInterpolator.ACCELERATE_DECELERATE, restoration.getInterpolator());
        assertEquals(PageChange.NONE, restoration.getPageChange());
        model.completeSettlement(restoration);

        assertEquals(1, model.getCurrentPosition());
        assertEquals(PageChange.PREVIOUS, dragBackward(model).getPageChange());
    }

    @Test
    public void rendererMapsLogicalTurnsToThePhysicalLeafUnderTheFinger() {
        assertTrue(PageRenderer.turnsPhysicalRightLeaf(
                ReadingDirection.LEFT_TO_RIGHT, PageChange.NEXT));
        assertFalse(PageRenderer.turnsPhysicalRightLeaf(
                ReadingDirection.LEFT_TO_RIGHT, PageChange.PREVIOUS));
        assertFalse(PageRenderer.turnsPhysicalRightLeaf(
                ReadingDirection.RIGHT_TO_LEFT, PageChange.NEXT));
        assertTrue(PageRenderer.turnsPhysicalRightLeaf(
                ReadingDirection.RIGHT_TO_LEFT, PageChange.PREVIOUS));
        assertThrows(IllegalArgumentException.class, () ->
                PageRenderer.turnsPhysicalRightLeaf(
                        ReadingDirection.LEFT_TO_RIGHT, PageChange.NONE));
    }

    @Test
    public void fillerCarriesOpaquePaperColorWithoutTextureBudget() {
        PageImage<String> filler = PageImage.filler(
                26, "leading-filler", 0, 120, 180, "borrowed", PAPER_COLOR);
        PortraitPageDeck<String> deck = new PortraitPageDeck<>(
                filler,
                smallPage(26, "current", 1),
                smallPage(26, "next", 2),
                false,
                true);

        assertTrue(filler.isFiller());
        assertEquals(PAPER_COLOR, filler.getFillerColorArgb());
        assertFalse(filler.hasOverlay());
        assertNull(filler.getOverlayContent());
        assertEquals(
                2L * 120L * 180L * 4L,
                TextureBudget.evaluate(deck, null, 1024, Long.MAX_VALUE).getRequiredBytes());
    }

    @Test
    public void fillerRequiresOpaqueColorAndBorrowedLifetimeContent() {
        assertThrows(IllegalArgumentException.class, () -> PageImage.filler(
                27, "transparent", 0, 120, 180, "borrowed", 0x7FF5F2EA));
        assertThrows(NullPointerException.class, () -> PageImage.filler(
                27, "missing-content", 0, 120, 180, null, PAPER_COLOR));
        assertThrows(IllegalArgumentException.class, () -> PageImage.filler(
                27, "   ", 0, 120, 180, "borrowed", PAPER_COLOR));

        PageImage<String> normal = smallPage(27, "normal", 1);
        assertFalse(normal.isFiller());
        assertThrows(IllegalStateException.class, normal::getFillerColorArgb);
    }

    @Test
    public void fillerMetadataParticipatesInSameGenerationDeckIdentity() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> first = fillerDeck(28, PAPER_COLOR);
        PortraitPageDeck<String> differentColor = fillerDeck(28, 0xFFEEE9DE);

        assertEquals(PageDeckCoordinator.Placement.ACTIVE, coordinator.offer(first).getPlacement());
        PageDeckCoordinator.Offer<String> conflict = coordinator.offer(differentColor);

        assertEquals(PageDeckCoordinator.Placement.REJECTED, conflict.getPlacement());
        assertEquals(DeckRejectionReason.CONFLICTING_GENERATION, conflict.getRejectionReason());
    }

    @Test
    public void explicitSettlementMetadataNeverInventsAFillerPosition() {
        PageImage<String> filler = PageImage.filler(
                29, "physical-current-filler", 99, 120, 180, "borrowed", PAPER_COLOR);
        PageImage<String> previousLeft = smallPage(29, "previous-left", 0);
        PageImage<String> previousRight = smallPage(29, "previous-right", 1);
        PageImage<String> currentRight = smallPage(29, "canonical-current", 2);
        PageImage<String> nextLeft = smallPage(29, "next-left", 3);
        PageImage<String> nextRight = smallPage(29, "next-right", 4);
        LandscapePageDeck<String> deck = new LandscapePageDeck<>(
                previousLeft,
                previousRight,
                filler,
                currentRight,
                nextLeft,
                nextRight,
                false,
                true,
                previousLeft,
                currentRight,
                nextLeft);

        assertSame(currentRight, deck.getSettlementPage(PageChange.NONE));
        assertEquals(2, PageSurfaceView.currentPosition(deck));
    }

    @Test
    public void boundaryRestorationRequiresArmAndCompleteFrameBeforeOneCompletion() {
        PageSurfaceView.BoundaryRestorationProtocol protocol =
                new PageSurfaceView.BoundaryRestorationProtocol();
        long token = protocol.beginAwaitingFrame();

        assertTrue(token != PageSurfaceView.NO_BOUNDARY_FRAME_TOKEN);
        assertEquals(token, protocol.pendingToken());
        assertNull(protocol.complete(token, true, true, true));
        assertTrue(protocol.arm(token));
        assertEquals(token, protocol.armedToken());
        assertNull(protocol.complete(token, false, true, true));
        assertNull(protocol.complete(token, true, false, true));

        PageSurfaceView.BoundaryRestorationProtocol.Completion completion =
                protocol.complete(token, true, true, true);
        assertNotNull(completion);
        assertTrue(completion.shouldReleasePending());
        assertTrue(completion.shouldPublishBoundary());
        assertNull(protocol.complete(token, true, true, true));
        assertFalse(protocol.arm(token));
        assertEquals(PageSurfaceView.NO_BOUNDARY_FRAME_TOKEN, protocol.pendingToken());
        assertEquals(PageSurfaceView.NO_BOUNDARY_FRAME_TOKEN, protocol.armedToken());

        long nextToken = protocol.beginAwaitingFrame();
        assertTrue(nextToken > token);
        assertThrows(IllegalStateException.class, protocol::beginAwaitingFrame);
        protocol.cancel(nextToken);
    }

    @Test
    public void boundaryRestorationCancellationWinsBeforeAndAfterArm() {
        PageSurfaceView.BoundaryRestorationProtocol protocol =
                new PageSurfaceView.BoundaryRestorationProtocol();
        long beforeArm = protocol.beginAwaitingFrame();
        protocol.cancel(beforeArm);
        assertEquals(PageSurfaceView.NO_BOUNDARY_FRAME_TOKEN, protocol.armedToken());
        assertEquals(PageSurfaceView.NO_BOUNDARY_FRAME_TOKEN, protocol.pendingToken());
        assertFalse(protocol.arm(beforeArm));
        assertNull(protocol.complete(beforeArm, true, true, true));

        long afterArm = protocol.beginAwaitingFrame();
        assertTrue(protocol.arm(afterArm));
        protocol.cancel(afterArm);
        assertEquals(PageSurfaceView.NO_BOUNDARY_FRAME_TOKEN, protocol.armedToken());
        assertEquals(PageSurfaceView.NO_BOUNDARY_FRAME_TOKEN, protocol.pendingToken());
        assertNull(protocol.complete(afterArm, true, true, true));
    }

    @Test
    public void boundaryRestorationWithoutPendingDeckDoesNotReleaseActive() {
        PageSurfaceView.BoundaryRestorationProtocol protocol =
                new PageSurfaceView.BoundaryRestorationProtocol();
        long token = protocol.beginAwaitingFrame();
        assertTrue(protocol.arm(token));

        PageSurfaceView.BoundaryRestorationProtocol.Completion completion =
                protocol.complete(token, true, true, false);

        assertNotNull(completion);
        assertFalse(completion.shouldReleasePending());
        assertTrue(completion.shouldPublishBoundary());
    }

    @Test
    public void cancelledBoundaryReleasesOnlyPendingDeckAndRetainsActive() {
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> active = ordinaryDeck(30);
        PortraitPageDeck<String> pending = ordinaryDeck(31);
        assertEquals(PageDeckCoordinator.Placement.ACTIVE, coordinator.offer(active).getPlacement());
        coordinator.beginSettlement();
        assertEquals(PageDeckCoordinator.Placement.PENDING, coordinator.offer(pending).getPlacement());

        coordinator.cancelSettlement();
        PageDeckCoordinator.Release<String> release =
                coordinator.releasePending(DeckReleaseReason.REPLACED);

        assertNotNull(release);
        assertSame(pending, release.getDeck());
        assertEquals(DeckReleaseReason.REPLACED, release.getReason());
        assertSame(active, coordinator.getActiveDeck());
        assertNull(coordinator.getPendingDeck());
    }

    @Test
    public void boundaryTerminalIsPublishedOnlyAfterTokenArmedRestoredFrame() throws IOException {
        String surface = productionSource("PageSurfaceView.java");
        String listener = productionSource("PageSurfaceListener.java");
        String renderer = productionSource("PageRenderer.java");
        String start = methodBody(surface, "private void startBoundaryRestoration(");
        String animationCompletion = methodBody(
                surface, "private void completeBoundaryRestorationAnimation(");
        String rendered = methodBody(surface, "private void handleRenderedFrame(");
        String cancel = methodBody(surface, "private SettlementContext cancelSettlementAnimator(");
        String constructor = methodBody(surface, "public PageSurfaceView(Context context)");
        String drawFrame = methodBody(renderer, "boolean drawFrame(");
        String drawPage = methodBody(renderer, "private boolean drawPage(");
        String drawTextures = methodBody(renderer, "private void drawPageTextures(");

        assertTrue(start.contains("rejectedSettlement.getPageChange()"));
        assertTrue(start.contains("deckCoordinator.beginSettlement()"));
        assertTrue(start.contains("startSettlementAnimation("));
        assertFalse(start.contains("rejectGesture("));
        assertFalse(start.contains("onGestureRejected("));

        assertTrue(animationCompletion.contains("completeSettlement("));
        int begin = animationCompletion.indexOf("beginAwaitingFrame()");
        int queue = animationCompletion.indexOf("queueEvent(");
        int arm = animationCompletion.indexOf(".arm(");
        int request = animationCompletion.indexOf("requestRender()");
        assertTrue(begin >= 0 && queue > begin && arm > queue && request > arm);
        assertFalse(animationCompletion.contains("frameSequence"));
        assertFalse(animationCompletion.contains("rejectGesture("));
        assertFalse(animationCompletion.contains("onGestureRejected("));

        assertTrue(rendered.contains("boundaryRestorationProtocol.complete("));
        assertTrue(rendered.contains("deckCoordinator.cancelSettlement()"));
        assertTrue(rendered.contains("shouldReleasePending()"));
        assertTrue(rendered.contains("releasePending(DeckReleaseReason.REPLACED)"));
        assertEquals(1, occurrences(rendered, "GestureRejectionReason.BOUNDARY"));
        assertTrue(rendered.contains("context.pageChange"));
        assertTrue(listener.contains("PageChange pageChange"));
        assertFalse(rendered.contains("onSettlement"));
        assertFalse(rendered.contains("onPageChanged"));
        assertFalse(rendered.contains("completeSettlement("));

        assertTrue(cancel.contains("boundaryRestorationProtocol.pendingToken()"));
        assertTrue(cancel.contains("boundaryRestorationProtocol.cancel("));

        assertTrue(constructor.contains("new GLSurfaceView.Renderer()"));
        assertTrue(constructor.contains("boolean frameRendered"));
        assertTrue(constructor.contains(".drawFrame(gl)"));
        assertTrue(constructor.contains("if (!frameRendered)"));
        assertTrue(constructor.contains("postOnAnimation(() -> handleRenderedFrame(frameToken))"));
        assertTrue(constructor.contains("setRenderer(renderer)"));

        assertTrue(drawFrame.contains("GLES20.glClear("));
        assertTrue(drawFrame.contains("GLES20.glUseProgram(program)"));
        assertTrue(drawFrame.contains("return drawLandscapeSpread()"));
        assertTrue(drawFrame.contains("return drawPortraitPage()"));
        assertTrue(drawFrame.contains("return false"));

        int missingBase = drawPage.indexOf("!resource.isFiller() && baseTexture == null");
        int missingOverlay = drawPage.indexOf("resource.hasOverlay() && overlayTexture == null");
        int draw = drawPage.indexOf("GLES20.glDrawElements(");
        int success = drawPage.lastIndexOf("return true;");
        assertTrue(missingBase >= 0 && missingBase < draw);
        assertTrue(missingOverlay >= 0 && missingOverlay < draw);
        assertTrue(success > draw);

        int fillerBranch = renderer.indexOf("if (uIsFiller");
        int textureSample = renderer.indexOf("texture2D(");
        assertTrue(fillerBranch >= 0 && fillerBranch < textureSample);
        assertTrue(drawTextures.contains("getFillerColorArgb()"));
        assertTrue(drawTextures.contains("colorChannel("));
        assertTrue(drawTextures.contains("glUniform4f(fillerColorUniform"));
    }

    @Test
    public void rendererMirrorsGeometryWithoutMirroringTextAndCoversBothLeafSides()
            throws IOException {
        String source = productionSource("PageRenderer.java");
        String portrait = methodBody(source, "private boolean drawPortraitPage(");
        String landscape = methodBody(source, "private boolean drawLandscapeSpread(");
        String mesh = methodBody(source, "void uploadPositions()");

        assertTrue(source.contains(
                "mirroredRightMesh = new GpuMesh(PageRole.RIGHT, true)"));
        assertTrue(source.contains("mirrorTextureCoordinates("));
        assertTrue(source.contains("turnsPhysicalRightLeaf("));
        assertTrue(portrait.contains("readingDirection == ReadingDirection.RIGHT_TO_LEFT"));
        assertTrue(portrait.contains("mirroredLeftMesh"));
        assertTrue(portrait.contains("mirroredFrontMesh"));
        assertTrue(portrait.contains("mirroredRightMesh"));
        assertTrue(mesh.contains("1f - positions[offset]"));

        assertTrue(landscape.contains("turnsPhysicalRightLeaf(readingDirection, pageChange)"));
        assertTrue(landscape.contains("spreadCurrentLeftResource"));
        assertTrue(landscape.contains("spreadCurrentRightResource"));
        assertTrue(landscape.contains("destinationLeft"));
        assertTrue(landscape.contains("destinationRight"));
        assertTrue(landscape.contains("mirroredLeftMesh"));
        assertTrue(landscape.contains("mirroredFrontMesh"));
        assertTrue(landscape.contains("frontMesh"));
        assertTrue(landscape.contains("leftMesh"));
        assertTrue(landscape.contains("&& rendered"));
    }

    @Test
    public void programmaticBoundaryRejectionPrecedesModelMutation() throws IOException {
        String surface = productionSource("PageSurfaceView.java");
        String turn = methodBody(
                surface, "public boolean turn(PageChange pageChange, long gestureId)");
        String context = methodBody(
                surface,
                "private SettlementContext settlementContext(\n" +
                        "            PageChange pageChange,");

        int invalid = turn.indexOf("throw new IllegalArgumentException");
        int mutation = turn.indexOf("activeGestureId = gestureId");
        int boundary = turn.indexOf("canSettle(pageChange)");
        int modelTurn = turn.indexOf("interaction.turn(pageChange)");
        int direction = turn.indexOf("renderer.setReadingDirection(");
        assertTrue(invalid >= 0 && invalid < mutation);
        assertTrue(boundary >= 0 && boundary < modelTurn);
        assertTrue(direction >= 0 && direction < modelTurn);
        assertTrue(turn.contains(
                "rejectGesture(gestureId, GestureRejectionReason.BOUNDARY, pageChange)"));
        assertTrue(turn.contains("GestureRejectionReason.DECK_NOT_PREPARED"));
        assertTrue(turn.contains("GestureRejectionReason.MODEL_REJECTED"));
        assertEquals(4, occurrences(turn, "return false;"));
        assertEquals(4, occurrences(turn, "rejectGesture("));
        assertTrue(context.contains("getSettlementPage(PageChange.NONE)"));
        assertTrue(context.contains("getSettlementPage(pageChange)"));
    }

    @Test
    public void currentPositionUsesExplicitCanonicalSettlementPage() {
        PageImage<String> previousLeft = smallPage(32, "previous-left", 0);
        PageImage<String> previousRight = smallPage(32, "previous-right", 1);
        PageImage<String> canonicalCurrent = smallPage(32, "canonical-current", 2);
        PageImage<String> physicalCurrentLeft = smallPage(32, "physical-current-left", 3);
        PageImage<String> nextLeft = smallPage(32, "next-left", 4);
        PageImage<String> nextRight = smallPage(32, "next-right", 5);
        LandscapePageDeck<String> rtlDeck = new LandscapePageDeck<>(
                previousLeft,
                previousRight,
                physicalCurrentLeft,
                canonicalCurrent,
                nextLeft,
                nextRight,
                true,
                true,
                previousLeft,
                canonicalCurrent,
                nextRight);

        assertEquals(2, PageSurfaceView.currentPosition(rtlDeck));
        assertEquals(-1, PageSurfaceView.currentPosition(null));
    }

    @Test
    public void currentPositionHandlesLeadingAndTrailingPartialFillerDecks() {
        PageImage<String> leadingFiller = PageImage.filler(
                33, "leading", 0, 120, 180, "borrowed", PAPER_COLOR);
        PortraitPageDeck<String> leading = new PortraitPageDeck<>(
                leadingFiller,
                smallPage(33, "first-real", 1),
                smallPage(33, "second-real", 2),
                false,
                true);
        PageImage<String> trailingFiller = PageImage.filler(
                34, "trailing", 2, 120, 180, "borrowed", PAPER_COLOR);
        PortraitPageDeck<String> trailing = new PortraitPageDeck<>(
                smallPage(34, "penultimate-real", 0),
                smallPage(34, "last-real", 1),
                trailingFiller,
                true,
                false);

        assertEquals(1, PageSurfaceView.currentPosition(leading));
        assertEquals(1, PageSurfaceView.currentPosition(trailing));
        assertFalse(leading.canTurn(PageChange.PREVIOUS));
        assertFalse(trailing.canTurn(PageChange.NEXT));
    }

    @Test
    public void fillersNeverAllocateRendererTextureIdentity() throws IOException {
        String source = productionSource("PageRenderer.java");
        assertFillerSkippedBeforeIdentity(methodBody(source, "private void uploadDeck("));
        assertFillerSkippedBeforeIdentity(methodBody(source, "private void registerDeck("));
        assertFillerSkippedBeforeIdentity(methodBody(source, "private static void collectDeckKeys("));
        String texture = methodBody(source, "private GpuTexture texture(");
        String overlayTexture = methodBody(source, "private GpuTexture overlayTexture(");
        assertTrue(texture.contains("page == null || page.isFiller()"));
        assertTrue(overlayTexture.contains("page == null || page.isFiller()"));
    }

    @Test
    public void productionSourcesUseApi24CompatibleJavaCollectionAndStringApis()
            throws IOException {
        Path root = Paths.get("src/main/java");
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                if (!Files.isRegularFile(path) || !path.toString().endsWith(".java")) {
                    continue;
                }
                String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                assertFalse(path + " uses List.of", source.contains("List.of("));
                assertFalse(path + " uses List.copyOf", source.contains("List.copyOf("));
                assertFalse(path + " uses String.isBlank", source.contains(".isBlank()"));
            }
        }
    }

    private static Settlement dragForward(PlayLikeCurlModel model) {
        model.beginGesture(100f);
        model.dragTo(0f, 100f);
        return model.release();
    }

    private static Settlement dragBackward(PlayLikeCurlModel model) {
        model.beginGesture(0f);
        model.dragTo(100f, 100f);
        return model.release();
    }

    private static PortraitPageDeck<String> fillerDeck(long generationId, int color) {
        return new PortraitPageDeck<>(
                PageImage.filler(
                        generationId, "filler", 0, 120, 180, "borrowed", color),
                smallPage(generationId, "current", 1),
                smallPage(generationId, "next", 2),
                false,
                true);
    }

    private static PortraitPageDeck<String> ordinaryDeck(long generationId) {
        return new PortraitPageDeck<>(
                smallPage(generationId, "previous", 0),
                smallPage(generationId, "current", 1),
                smallPage(generationId, "next", 2));
    }

    private static PageImage<String> smallPage(
            long generationId,
            String logicalPageId,
            int ordinal) {
        return new PageImage<>(
                generationId,
                logicalPageId,
                ordinal,
                120,
                180,
                "bitmap-" + logicalPageId);
    }

    private static PageImage<String> page(
            long generationId,
            String logicalPageId,
            int ordinal) {
        return new PageImage<>(
                generationId,
                logicalPageId,
                ordinal,
                1200,
                1800,
                "bitmap-" + ordinal);
    }

    private static String productionSource(String fileName) throws IOException {
        Path path = Paths.get("src/main/java/karacken/curl", fileName);
        assertNotNull(path);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int signatureStart = source.indexOf(signature);
        assertTrue("Missing method signature: " + signature, signatureStart >= 0);
        int bodyStart = source.indexOf('{', signatureStart);
        assertTrue("Missing method body: " + signature, bodyStart >= 0);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, index + 1);
                }
            }
        }
        throw new AssertionError("Unterminated method body: " + signature);
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private static void assertFillerSkippedBeforeIdentity(String method) {
        int fillerGuard = method.indexOf("page.isFiller()");
        int identity = method.indexOf("page.identityKey()");
        assertTrue(fillerGuard >= 0);
        assertTrue(identity > fillerGuard);
    }
}
