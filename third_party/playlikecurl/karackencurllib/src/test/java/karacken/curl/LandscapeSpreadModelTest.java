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

    private static final float TOLERANCE = 0.0001f;
}
