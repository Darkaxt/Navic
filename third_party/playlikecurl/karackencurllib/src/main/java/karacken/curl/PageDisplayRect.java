package karacken.curl;

import java.util.Objects;

/**
 * Immutable page placement in top-origin pixels relative to the complete renderer surface.
 * Texture dimensions remain independent and may be downsampled without changing this rectangle.
 */
public final class PageDisplayRect {
    private final int leftPx;
    private final int topPx;
    private final int rightPx;
    private final int bottomPx;

    public PageDisplayRect(int leftPx, int topPx, int rightPx, int bottomPx) {
        if (leftPx < 0 || topPx < 0) {
            throw new IllegalArgumentException("Display rectangle origin must not be negative");
        }
        if (rightPx <= leftPx || bottomPx <= topPx) {
            throw new IllegalArgumentException("Display rectangle dimensions must be positive");
        }
        this.leftPx = leftPx;
        this.topPx = topPx;
        this.rightPx = rightPx;
        this.bottomPx = bottomPx;
    }

    public int getLeftPx() {
        return leftPx;
    }

    public int getTopPx() {
        return topPx;
    }

    public int getRightPx() {
        return rightPx;
    }

    public int getBottomPx() {
        return bottomPx;
    }

    public int getWidthPx() {
        return rightPx - leftPx;
    }

    public int getHeightPx() {
        return bottomPx - topPx;
    }

    boolean fitsWithin(int surfaceWidthPx, int surfaceHeightPx) {
        return surfaceWidthPx > 0
                && surfaceHeightPx > 0
                && rightPx <= surfaceWidthPx
                && bottomPx <= surfaceHeightPx;
    }

    int glBottomPx(int surfaceHeightPx) {
        if (surfaceHeightPx <= 0 || bottomPx > surfaceHeightPx) {
            throw new IllegalArgumentException("Display rectangle exceeds the renderer surface");
        }
        return surfaceHeightPx - bottomPx;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PageDisplayRect)) {
            return false;
        }
        PageDisplayRect rectangle = (PageDisplayRect) other;
        return leftPx == rectangle.leftPx
                && topPx == rectangle.topPx
                && rightPx == rectangle.rightPx
                && bottomPx == rectangle.bottomPx;
    }

    @Override
    public int hashCode() {
        return Objects.hash(leftPx, topPx, rightPx, bottomPx);
    }

    @Override
    public String toString() {
        return "PageDisplayRect("
                + leftPx + ", "
                + topPx + ", "
                + rightPx + ", "
                + bottomPx + ")";
    }
}
