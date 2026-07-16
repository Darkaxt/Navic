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

    private static final float TOLERANCE = 0.0001f;
}
