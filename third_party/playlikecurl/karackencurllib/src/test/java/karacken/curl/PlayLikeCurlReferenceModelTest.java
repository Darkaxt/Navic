package karacken.curl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayLikeCurlReferenceModelTest {
    private static final float TOLERANCE = 0.0001f;

    @Test
    public void persistentPagesKeepReferenceDrawOrderDepthAndBoundaryImages() {
        PlayLikeCurlModel model = new PlayLikeCurlModel(4, 0);

        assertArrayEquals(
                new PageRole[]{PageRole.LEFT, PageRole.FRONT, PageRole.RIGHT},
                model.getDrawOrder().stream().map(PageState::getRole).toArray(PageRole[]::new));
        assertEquals(-0.001f, model.getLeftPage().getDepth(), TOLERANCE);
        assertEquals(-0.002f, model.getFrontPage().getDepth(), TOLERANCE);
        assertEquals(-0.003f, model.getRightPage().getDepth(), TOLERANCE);
        assertArrayEquals(new int[]{0, 0, 1}, pageIndices(model));
        assertEquals(ActivePage.CURRENT, model.getActivePage());
        assertEquals(PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION, model.getLeftPage().getCurlPosition(), TOLERANCE);
        assertEquals(PlayLikeCurlModel.GRID, model.getFrontPage().getCurlPosition(), TOLERANCE);
        assertEquals(PlayLikeCurlModel.GRID, model.getRightPage().getCurlPosition(), TOLERANCE);

        PageState left = model.getLeftPage();
        PageState front = model.getFrontPage();
        PageState right = model.getRightPage();
        model.jumpTo(3);

        assertSame(left, model.getLeftPage());
        assertSame(front, model.getFrontPage());
        assertSame(right, model.getRightPage());
        assertArrayEquals(new int[]{2, 3, 3}, pageIndices(model));
    }

    @Test
    public void forwardDragAndFlingCommitOnlyAfterReferenceSettlementCompletes() {
        PlayLikeCurlModel model = new PlayLikeCurlModel(4, 0);

        model.beginGesture(100f);
        model.dragTo(50f, 100f);

        assertEquals(ActivePage.CURRENT, model.getActivePage());
        assertEquals(12.5f, model.getFrontPage().getCurlPosition(), TOLERANCE);

        Settlement settlement = model.flingTowardNext();
        assertEquals(-5, settlement.getTargetPercent());
        assertEquals(300L, settlement.getDurationMillis());
        assertEquals(SettlementInterpolator.DECELERATE, settlement.getInterpolator());
        assertEquals(PageChange.NEXT, settlement.getPageChange());
        assertEquals(0, model.getCurrentPosition());

        model.completeSettlement(settlement);

        assertEquals(1, model.getCurrentPosition());
        assertArrayEquals(new int[]{0, 1, 2}, pageIndices(model));
        assertEquals(ActivePage.CURRENT, model.getActivePage());
        assertEquals(PlayLikeCurlModel.GRID, model.getFrontPage().getCurlPosition(), TOLERANCE);
    }

    @Test
    public void backwardDragUsesLeftPageAndReleaseWithoutFlingDoesNotNavigate() {
        PlayLikeCurlModel model = new PlayLikeCurlModel(4, 2);

        model.beginGesture(0f);
        model.dragTo(50f, 100f);

        assertEquals(ActivePage.LEFT, model.getActivePage());
        assertEquals(11.25f, model.getLeftPage().getCurlPosition(), TOLERANCE);

        Settlement settlement = model.release();
        assertEquals(-5, settlement.getTargetPercent());
        assertEquals(SettlementInterpolator.ACCELERATE_DECELERATE, settlement.getInterpolator());
        assertEquals(PageChange.NONE, settlement.getPageChange());

        model.completeSettlement(settlement);
        assertEquals(2, model.getCurrentPosition());
        assertEquals(ActivePage.CURRENT, model.getActivePage());
    }

    @Test
    public void backwardFlingRotatesPageIdentitiesOnlyAfterSettlement() {
        PlayLikeCurlModel model = new PlayLikeCurlModel(4, 2);
        model.beginGesture(0f);
        model.dragTo(50f, 100f);

        Settlement settlement = model.flingTowardPrevious();

        assertEquals(100, settlement.getTargetPercent());
        assertEquals(PageChange.PREVIOUS, settlement.getPageChange());
        assertEquals(2, model.getCurrentPosition());
        assertArrayEquals(new int[]{1, 2, 3}, pageIndices(model));

        model.completeSettlement(settlement);

        assertEquals(1, model.getCurrentPosition());
        assertArrayEquals(new int[]{0, 1, 2}, pageIndices(model));
    }

    @Test
    public void programmaticTurnsStartFromTheSameReferenceEndpointsAsEdgeDrags() {
        PlayLikeCurlModel forward = new PlayLikeCurlModel(4, 1);
        Settlement forwardSettlement = forward.turn(PageChange.NEXT);

        assertEquals(ActivePage.CURRENT, forward.getActivePage());
        assertEquals(PlayLikeCurlModel.GRID, forward.getFrontPage().getCurlPosition(), TOLERANCE);
        assertEquals(PageChange.NEXT, forwardSettlement.getPageChange());

        PlayLikeCurlModel backward = new PlayLikeCurlModel(4, 2);
        Settlement backwardSettlement = backward.turn(PageChange.PREVIOUS);

        assertEquals(ActivePage.LEFT, backward.getActivePage());
        assertEquals(
                PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION,
                backward.getLeftPage().getCurlPosition(),
                TOLERANCE);
        assertEquals(PageChange.PREVIOUS, backwardSettlement.getPageChange());
    }

    @Test
    public void slowForwardReleasePastMidpointCommitsTheNextPage() {
        PlayLikeCurlModel model = new PlayLikeCurlModel(4, 0);
        model.beginGesture(100f);
        model.dragTo(25f, 100f);

        Settlement settlement = model.release();

        assertEquals(-5, settlement.getTargetPercent());
        assertEquals(PageChange.NEXT, settlement.getPageChange());
        model.completeSettlement(settlement);
        assertEquals(1, model.getCurrentPosition());
    }

    @Test
    public void slowBackwardReleasePastMidpointCommitsThePreviousPage() {
        PlayLikeCurlModel model = new PlayLikeCurlModel(4, 2);
        model.beginGesture(0f);
        model.dragTo(75f, 100f);

        Settlement settlement = model.release();

        assertEquals(100, settlement.getTargetPercent());
        assertEquals(PageChange.PREVIOUS, settlement.getPageChange());
        model.completeSettlement(settlement);
        assertEquals(1, model.getCurrentPosition());
    }

    @Test
    public void slowReleaseBeforeMidpointStillRollsBack() {
        PlayLikeCurlModel forward = new PlayLikeCurlModel(4, 0);
        forward.beginGesture(100f);
        forward.dragTo(75f, 100f);

        PlayLikeCurlModel backward = new PlayLikeCurlModel(4, 2);
        backward.beginGesture(0f);
        backward.dragTo(25f, 100f);

        assertEquals(PageChange.NONE, forward.release().getPageChange());
        assertEquals(PageChange.NONE, backward.release().getPageChange());
    }

    @Test
    public void referenceGeometryPreservesAspectCorrectionAndRoleSpecificDeformation() {
        PageGeometry portrait = PlayLikeCurlGeometry.createPage(
                PageRole.FRONT, 1000, 1500, PageOrientation.PORTRAIT);
        PageGeometry landscape = PlayLikeCurlGeometry.createPage(
                PageRole.LEFT, 1600, 1000, PageOrientation.LANDSCAPE);

        assertEquals(-0.25f, portrait.positionY(0, 0), TOLERANCE);
        assertEquals(1.25f, portrait.positionY(0, PlayLikeCurlModel.GRID), TOLERANCE);
        assertEquals(-0.3f, landscape.positionY(0, 0), TOLERANCE);
        assertEquals(1.3f, landscape.positionY(0, PlayLikeCurlModel.GRID), TOLERANCE);

        PlayLikeCurlGeometry.update(portrait, 12.5f, true);
        PlayLikeCurlGeometry.update(landscape, 12.5f, true);

        assertFalse(java.util.Arrays.equals(portrait.getPositions(), landscape.getPositions()));
        for (float value : portrait.getPositions()) assertTrue(Float.isFinite(value));
        for (float value : landscape.getPositions()) assertTrue(Float.isFinite(value));
    }

    @Test
    public void meshAndProjectionMatchReferenceConstants() {
        PageGeometry page = PlayLikeCurlGeometry.createPage(
                PageRole.FRONT, 1000, 1500, PageOrientation.PORTRAIT);

        assertEquals(26 * 26 * 3, page.getPositions().length);
        assertEquals(26 * 26 * 2, page.getTextureCoordinates().length);
        assertEquals(25 * 25 * 6, page.getIndices().length);
        assertEquals(0f, page.getTextureCoordinates()[0], TOLERANCE);
        assertEquals(1f, page.getTextureCoordinates()[1], TOLERANCE);
        assertEquals(0.5f, PlayLikeCurlGeometry.projectionAspect(1000, 2000), TOLERANCE);
        assertEquals(2f, PlayLikeCurlGeometry.projectionAspect(2000, 1000), TOLERANCE);
    }

    @Test
    public void portraitActivePlaneFillsItsPhysicalViewportWithoutClipping() {
        int displayWidth = 1450;
        int displayHeight = 1848;
        float aspect = PlayLikeCurlGeometry.projectionAspect(displayWidth, displayHeight);
        float ratio = PlayLikeCurlGeometry.pageRatio(
                displayWidth, displayHeight, PageOrientation.PORTRAIT);
        float scale = PlayLikeCurlGeometry.restingPlaneScale(
                displayWidth, displayHeight, PageOrientation.PORTRAIT, 0f);
        float visibleHeight = PlayLikeCurlGeometry.visiblePlaneHeight(0f);
        float visibleWidth = visibleHeight * aspect;

        assertEquals(1f, scale / visibleWidth, TOLERANCE);
        assertEquals(1f, scale * ratio / visibleHeight, TOLERANCE);
    }

    @Test
    public void restingPagePlaneFillsItsPhysicalViewport() {
        int displayWidth = 1450;
        int displayHeight = 1848;
        float aspect = PlayLikeCurlGeometry.projectionAspect(displayWidth, displayHeight);
        float ratio = PlayLikeCurlGeometry.pageRatio(
                displayWidth, displayHeight, PageOrientation.PORTRAIT);
        float scale = PlayLikeCurlGeometry.restingPlaneScale(
                displayWidth, displayHeight, PageOrientation.PORTRAIT);
        float visibleHeight = PlayLikeCurlGeometry.visiblePlaneHeight();
        float visibleWidth = visibleHeight * aspect;

        assertEquals(1f, scale / visibleWidth, TOLERANCE);
        assertEquals(1f, scale * ratio / visibleHeight, TOLERANCE);
    }

    private static int[] pageIndices(PlayLikeCurlModel model) {
        return model.getDrawOrder().stream().mapToInt(PageState::getPageIndex).toArray();
    }
}
