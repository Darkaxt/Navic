package karacken.curl;

/** Two-phase leaf transition derived from PlayLikeCurl's unchanged curl position. */
final class LandscapeSpreadTransition {
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
                        PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION,
                        turningProgress),
                lerp(
                        PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION,
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
        return progress > 0.5f;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }
}
