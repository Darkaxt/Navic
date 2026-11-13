package karacken.curl;

/** Two-phase leaf transition derived from PlayLikeCurl's unchanged curl position. */
final class LandscapeSpreadTransition {
    /**
     * Each leaf is clipped to its own half of the spread. The turning face therefore ends its half
     * where its projected fold edge reaches the spine, and the reverse face starts where its
     * projected near edge leaves the spine, so the sheet never disappears mid-turn.
     */
    static final float TURNING_SPINE_CURL_POSITION = solveCurl(true);
    static final float INCOMING_SPINE_CURL_POSITION = solveCurl(false);

    private final boolean forward;
    private final float progress;
    private final float turningCurlPosition;
    private final float incomingCurlPosition;

    private LandscapeSpreadTransition(
            boolean forward,
            float progress,
            float turningCurlPosition,
            float incomingCurlPosition) {
        this.forward = forward;
        this.progress = progress;
        this.turningCurlPosition = turningCurlPosition;
        this.incomingCurlPosition = incomingCurlPosition;
    }

    static LandscapeSpreadTransition from(PlayLikeCurlModel motion) {
        boolean forward = motion.getActivePage() != ActivePage.LEFT;
        float curlPosition = forward
                ? motion.getFrontPage().getCurlPosition()
                : motion.getLeftPage().getCurlPosition();
        float range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION;
        float progress = forward
                ? (PlayLikeCurlModel.GRID - curlPosition) / range
                : (curlPosition - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION) / range;
        progress = clamp(progress);
        float turningProgress = clamp(progress * 2f);
        float incomingProgress = clamp((progress - 0.5f) * 2f);
        return new LandscapeSpreadTransition(
                forward,
                progress,
                lerp(
                        PlayLikeCurlModel.GRID,
                        TURNING_SPINE_CURL_POSITION,
                        turningProgress),
                lerp(
                        INCOMING_SPINE_CURL_POSITION,
                        PlayLikeCurlModel.GRID,
                        incomingProgress));
    }

    boolean isForward() {
        return forward;
    }

    float getProgress() {
        return progress;
    }

    float getTurningCurlPosition() {
        return turningCurlPosition;
    }

    float getIncomingCurlPosition() {
        return incomingCurlPosition;
    }

    boolean isTurningCurrentLeafVisible() {
        return progress > 0f && progress < 0.5f;
    }

    boolean isIncomingReverseLeafVisible() {
        return progress >= 0.5f;
    }

    /** Outermost projected x of the turning face (unmirrored right-half orientation). */
    static float turningFaceOuterX(float curlPosition) {
        float max = Float.NEGATIVE_INFINITY;
        for (int column = 0; column <= PlayLikeCurlModel.GRID; column++) {
            max = Math.max(max, PlayLikeCurlGeometry.projectedActiveColumnX(
                    PageRole.FRONT, column, curlPosition, false));
        }
        return max;
    }

    /** Innermost projected x of the mirrored reverse face (left-half orientation; 1 is the spine). */
    static float incomingFaceInnerX(float curlPosition) {
        float min = Float.POSITIVE_INFINITY;
        for (int column = 0; column <= PlayLikeCurlModel.GRID; column++) {
            min = Math.min(min, PlayLikeCurlGeometry.projectedActiveColumnX(
                    PageRole.LEFT, column, curlPosition, true));
        }
        return min;
    }

    private static float solveCurl(boolean turning) {
        float low = PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION;
        float high = PlayLikeCurlModel.GRID;
        // Spine-relative signed edge: positive while the face still shows inside its own half.
        if (spineEdge(turning, low) >= 0f) return low;
        for (int iteration = 0; iteration < 40; iteration++) {
            float middle = (low + high) / 2f;
            if (spineEdge(turning, middle) >= 0f) {
                high = middle;
            } else {
                low = middle;
            }
        }
        return high;
    }

    private static float spineEdge(boolean turning, float curlPosition) {
        return turning
                ? turningFaceOuterX(curlPosition)
                : 1f - incomingFaceInnerX(curlPosition);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}
