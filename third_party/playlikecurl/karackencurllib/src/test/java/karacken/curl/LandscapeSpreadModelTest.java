package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LandscapeSpreadModelTest {
    @Test
    public void forwardTurnMovesFromPagesOneAndTwoToPagesThreeAndFour() {
        LandscapeSpreadModel model = new LandscapeSpreadModel(4, 0);

        assertEquals(0, model.getCurrentLeftPageIndex());
        assertEquals(1, model.getCurrentRightPageIndex());
        assertEquals(2, model.getNextLeftPageIndex());
        assertEquals(3, model.getNextRightPageIndex());

        model.beginGesture(100f);
        model.dragTo(0f, 100f);
        Settlement settlement = model.flingTowardNext();

        assertEquals(PageChange.NEXT, settlement.getPageChange());
        assertEquals(1, model.getForwardTurningPageIndex());
        assertEquals(2, model.getForwardReversePageIndex());
        assertEquals(3, model.getForwardUnderneathPageIndex());

        model.completeSettlement(settlement);

        assertEquals(2, model.getCurrentLeftPageIndex());
        assertEquals(3, model.getCurrentRightPageIndex());
    }

    @Test
    public void backwardTurnRestoresThePreviousTwoPageSpread() {
        LandscapeSpreadModel model = new LandscapeSpreadModel(4, 2);

        model.beginGesture(0f);
        model.dragTo(100f, 100f);
        Settlement settlement = model.flingTowardPrevious();

        assertEquals(PageChange.PREVIOUS, settlement.getPageChange());
        assertEquals(2, model.getBackwardTurningPageIndex());
        assertEquals(1, model.getBackwardReversePageIndex());
        assertEquals(0, model.getBackwardUnderneathPageIndex());

        model.completeSettlement(settlement);

        assertEquals(0, model.getCurrentLeftPageIndex());
        assertEquals(1, model.getCurrentRightPageIndex());
    }

    @Test
    public void slowForwardReleaseAtTheBindingCommitsTheNextSpread() {
        LandscapeSpreadModel model = new LandscapeSpreadModel(4, 0);
        model.beginGesture(200f);
        model.dragTo(100f, 100f);

        Settlement settlement = model.release();

        assertEquals(PageChange.NEXT, settlement.getPageChange());
        model.completeSettlement(settlement);
        assertEquals(2, model.getCurrentLeftPageIndex());
        assertEquals(3, model.getCurrentRightPageIndex());
    }

    @Test
    public void slowBackwardReleaseDraggedPastTheLeafCommitsThePreviousSpread() {
        LandscapeSpreadModel model = new LandscapeSpreadModel(4, 2);
        model.beginGesture(0f);
        model.dragTo(40f, 100f);
        model.dragTo(180f, 100f);

        Settlement settlement = model.release();

        assertEquals(PageChange.PREVIOUS, settlement.getPageChange());
    }

    @Test
    public void slowBackwardReleaseAtTheBindingCommitsThePreviousSpread() {
        LandscapeSpreadModel model = new LandscapeSpreadModel(4, 2);
        model.beginGesture(0f);
        model.dragTo(100f, 100f);

        Settlement settlement = model.release();

        assertEquals(PageChange.PREVIOUS, settlement.getPageChange());
        model.completeSettlement(settlement);
        assertEquals(0, model.getCurrentLeftPageIndex());
        assertEquals(1, model.getCurrentRightPageIndex());
    }

    @Test
    public void oddFinalPageDoesNotEscapeTheAdapter() {
        LandscapeSpreadModel model = new LandscapeSpreadModel(5, 4);

        assertEquals(4, model.getCurrentLeftPageIndex());
        assertEquals(4, model.getCurrentRightPageIndex());
        assertEquals(4, model.getNextLeftPageIndex());
        assertEquals(4, model.getNextRightPageIndex());
    }

    @Test
    public void forwardTransitionChangesFacesAtTheBinding() {
        LandscapeSpreadModel model = new LandscapeSpreadModel(4, 0);
        model.beginGesture(100f);

        model.dragTo(75f, 100f);
        LandscapeSpreadTransition rightLeaf = model.getTransition();

        assertTrue(rightLeaf.isForward());
        assertTrue(rightLeaf.isTurningCurrentLeafVisible());
        assertFalse(rightLeaf.isIncomingReverseLeafVisible());
        assertEquals(1, model.getForwardTurningPageIndex());
        assertEquals(3, model.getForwardUnderneathPageIndex());

        model.dragTo(25f, 100f);
        LandscapeSpreadTransition leftLeaf = model.getTransition();

        assertTrue(leftLeaf.isForward());
        assertFalse(leftLeaf.isTurningCurrentLeafVisible());
        assertTrue(leftLeaf.isIncomingReverseLeafVisible());
        assertEquals(2, model.getForwardReversePageIndex());
        assertEquals(3, model.getForwardUnderneathPageIndex());
    }

    @Test
    public void draggingFromOuterEdgeToBindingCompletesTheSpreadProgress() {
        LandscapeSpreadModel model = new LandscapeSpreadModel(4, 0);
        model.beginGesture(200f);

        model.dragTo(100f, 100f);

        assertEquals(1f, model.getTransition().getProgress(), TOLERANCE);
        assertEquals(
                PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION,
                model.getMotionModel().getFrontPage().getCurlPosition(),
                TOLERANCE);
    }

    @Test
    public void forwardTurnKeepsASheetVisibleThroughoutTheSpreadTransition() {
        assertSheetVisibleThroughout(new LandscapeSpreadModel(4, 0), 200f, -1f);
    }

    @Test
    public void backwardTurnKeepsASheetVisibleThroughoutTheSpreadTransition() {
        assertSheetVisibleThroughout(new LandscapeSpreadModel(4, 2), 0f, 1f);
    }

    @Test
    public void turningAndIncomingFacesMeetAtTheSpine() {
        assertEquals(0f, LandscapeSpreadTransition.turningFaceOuterX(
                LandscapeSpreadTransition.TURNING_SPINE_CURL_POSITION), 0.001f);
        assertTrue(LandscapeSpreadTransition.incomingFaceInnerX(
                LandscapeSpreadTransition.INCOMING_SPINE_CURL_POSITION) >= 0.999f);
    }

    /**
     * Each face is clipped to its own half, so the projected sheet must stay inside that half for
     * every progress except the edge-on instant at the spine (the old hand-off drew nothing for
     * roughly 0.37 < p < 0.5).
     */
    private static void assertSheetVisibleThroughout(
            LandscapeSpreadModel model, float start, float direction) {
        model.beginGesture(start);
        for (int step = 1; step < 200; step++) {
            model.dragTo(start + direction * step, 100f);
            LandscapeSpreadTransition transition = model.getTransition();
            float progress = transition.getProgress();
            if (progress <= 0f || progress >= 1f || Math.abs(progress - 0.5f) < 0.02f) continue;
            float width = 0f;
            if (transition.isTurningCurrentLeafVisible()) {
                width = Math.max(width, Math.min(1f, LandscapeSpreadTransition.turningFaceOuterX(
                        transition.getTurningCurlPosition())));
            }
            if (transition.isIncomingReverseLeafVisible()) {
                width = Math.max(width, 1f - Math.max(0f, LandscapeSpreadTransition.incomingFaceInnerX(
                        transition.getIncomingCurlPosition())));
            }
            assertTrue("no sheet visible at progress " + progress, width > 0.01f);
        }
    }

    @Test
    public void hingedLeafStaysPinnedAtTheSpineAndFoldsTowardIt() {
        int grid = PlayLikeCurlModel.GRID;
        float previousEdge = Float.POSITIVE_INFINITY;
        for (int step = 0; step <= 20; step++) {
            float angle = (float) (Math.PI / 2) * step / 20f;
            float[] x = new float[grid + 1];
            float[] z = new float[grid + 1];
            PlayLikeCurlGeometry.hingedColumns(angle, x, z);
            // The spine edge never moves and no column crosses into the other half.
            assertEquals(0f, x[0], TOLERANCE);
            for (int column = 1; column <= grid; column++) {
                assertTrue(x[column] >= x[column - 1] - TOLERANCE);
            }
            // The face is compressed toward the spine as the leaf rises, never slid past it.
            assertTrue(x[grid] <= previousEdge + TOLERANCE);
            previousEdge = x[grid];
        }
        assertEquals(1f, flatEdge(0f), 0.001f);
        assertEquals(0f, flatEdge((float) (Math.PI / 2)), 0.001f);
    }

    @Test
    public void hingeAnglesRiseToUprightAtTheSpineAndLayTheIncomingLeafFlat() {
        LandscapeSpreadModel model = new LandscapeSpreadModel(4, 0);
        model.beginGesture(200f);
        model.dragTo(150f, 100f);
        LandscapeSpreadTransition half = model.getTransition();
        assertEquals(0.5f, half.getProgress(), TOLERANCE);
        assertEquals((float) (Math.PI / 2), half.getTurningHingeAngle(), 0.001f);
        assertEquals((float) (Math.PI / 2), half.getIncomingHingeAngle(), 0.001f);
        model.dragTo(100f, 100f);
        assertEquals(0f, model.getTransition().getIncomingHingeAngle(), 0.001f);
        model.dragTo(175f, 100f);
        LandscapeSpreadTransition quarter = model.getTransition();
        // The flexed outer edge follows the gesture: halfway to the spine at a quarter of the turn.
        assertEquals(0.5f, flatEdge(quarter.getTurningHingeAngle()), 0.001f);
    }

    @Test
    public void turningLeafFlexesLikePaperSoItsTextCompressesTowardTheCurledEdge() {
        int grid = PlayLikeCurlModel.GRID;
        float[] x = new float[grid + 1];
        float[] z = new float[grid + 1];
        PlayLikeCurlGeometry.hingedColumns((float) (Math.PI / 4), x, z);
        float spineColumn = x[1] - x[0];
        float edgeColumn = x[grid] - x[grid - 1];
        // A rigid slate would compress every column equally; paper bends more toward the edge.
        assertTrue(spineColumn > edgeColumn * 2f);
        for (int column = 2; column <= grid; column++) {
            assertTrue(x[column] - x[column - 1] <= x[column - 1] - x[column - 2] + TOLERANCE);
        }
        // Flat and upright leaves are straight.
        assertEquals(0f, PlayLikeCurlGeometry.hingeFlex(0f), TOLERANCE);
        assertEquals(0f, PlayLikeCurlGeometry.hingeFlex((float) (Math.PI / 2)), 0.001f);
    }

    @Test
    public void hingedShadowFallsOutsideTheLeafEdgeOnItsOwnHalf() {
        FoldShadowModel.State flat = FoldShadowModel.resolveHinged(0f, false);
        assertEquals(0f, flat.getOpacity(), TOLERANCE);
        FoldShadowModel.State rising = FoldShadowModel.resolveHinged(0.8f, false);
        assertTrue(rising.getOpacity() > 0f);
        assertTrue(rising.getEndX() > rising.getStartX());
        FoldShadowModel.State mirrored = FoldShadowModel.resolveHinged(0.8f, true);
        assertEquals(1f - rising.getStartX(), mirrored.getEndX(), 0.001f);
    }

    private static float flatEdge(float angle) {
        int grid = PlayLikeCurlModel.GRID;
        float[] x = new float[grid + 1];
        float[] z = new float[grid + 1];
        PlayLikeCurlGeometry.hingedColumns(angle, x, z);
        return x[grid];
    }

    private static final float TOLERANCE = 0.0001f;
}
