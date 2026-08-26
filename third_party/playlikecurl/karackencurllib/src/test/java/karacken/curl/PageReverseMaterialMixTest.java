package karacken.curl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PageReverseMaterialMixTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void mixTracksTurningAndIncomingCurlProgressInEitherDirection() {
        float endpoint = PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION;
        float midpoint = (PlayLikeCurlModel.GRID + endpoint) / 2f;

        assertEquals(0f, PageReverseMaterialMix.fromCurlPosition(PlayLikeCurlModel.GRID), EPSILON);
        assertEquals(0.5f, PageReverseMaterialMix.fromCurlPosition(midpoint), EPSILON);
        assertEquals(1f, PageReverseMaterialMix.fromCurlPosition(endpoint), EPSILON);
        assertEquals(0f, PageReverseMaterialMix.fromCurlPosition(PlayLikeCurlModel.GRID + 5f), EPSILON);
        assertEquals(1f, PageReverseMaterialMix.fromCurlPosition(endpoint - 5f), EPSILON);
    }
}
