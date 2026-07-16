package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FoldShadowModelTest {
    private static final float TOLERANCE = 0.0001f;

    @Test
    public void shadowIsInvisibleAtBothSettledEndpoints() {
        FoldShadowModel.State flat = FoldShadowModel.resolve(
                PageRole.FRONT, PlayLikeCurlModel.GRID, false);
        FoldShadowModel.State turned = FoldShadowModel.resolve(
                PageRole.FRONT, PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION, false);

        assertEquals(0f, flat.getOpacity(), TOLERANCE);
        assertEquals(0f, turned.getOpacity(), TOLERANCE);
    }

    @Test
    public void shadowPeaksNearTheMiddleOfTheTurn() {
        float midpoint = (PlayLikeCurlModel.GRID
                + PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / 2f;

        FoldShadowModel.State shadow = FoldShadowModel.resolve(
                PageRole.FRONT, midpoint, false);

        assertEquals(FoldShadowModel.MAX_OPACITY, shadow.getOpacity(), TOLERANCE);
        assertTrue(shadow.getEndX() > shadow.getFoldEdgeX());
        assertTrue(shadow.isDarkAtStart());
    }

    @Test
    public void mirroredLeafCastsTheFadeOnTheOppositeSide() {
        float midpoint = (PlayLikeCurlModel.GRID
                + PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / 2f;

        FoldShadowModel.State normal = FoldShadowModel.resolve(
                PageRole.FRONT, midpoint, false);
        FoldShadowModel.State mirrored = FoldShadowModel.resolve(
                PageRole.FRONT, midpoint, true);

        assertEquals(1f - normal.getFoldEdgeX(), mirrored.getFoldEdgeX(), TOLERANCE);
        assertTrue(normal.getEndX() > normal.getFoldEdgeX());
        assertTrue(mirrored.getStartX() < mirrored.getFoldEdgeX());
        assertTrue(normal.isDarkAtStart());
        assertFalse(mirrored.isDarkAtStart());
    }

    @Test
    public void shadowTracksTheSameDeformedEdgeAsTheMovingLeaf() {
        float curl = 8f;

        FoldShadowModel.State shadow = FoldShadowModel.resolve(PageRole.LEFT, curl, false);

        assertEquals(
                PlayLikeCurlGeometry.foldEdgeX(PageRole.LEFT, curl),
                shadow.getFoldEdgeX(),
                TOLERANCE);
    }
}
