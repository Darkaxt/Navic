package karacken.curl;

/** Resolves explicit reverse-paper sampling from the simplified curl position. */
final class PageReverseMaterialMix {
    private PageReverseMaterialMix() {}

    static float fromCurlPosition(float curlPosition) {
        float range = PlayLikeCurlModel.GRID - PlayLikeCurlModel.RIGHT_ENDPOINT_POSITION;
        float progress = (PlayLikeCurlModel.GRID - curlPosition) / range;
        return Math.max(0f, Math.min(1f, progress));
    }
}
