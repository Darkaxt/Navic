package karacken.curl;

import java.util.Objects;

/**
 * Immutable opaque paper and physical presentation material for one deck leaf.
 *
 * <p>The colors and geometry are supplied by the client. PlayLikeCurl may compare this value and
 * transform its validated geometry, but it does not infer publication semantics.
 */
public final class PageMaterial {
    private final long generationId;
    private final int frontPaperColorArgb;
    private final int reversePaperColorArgb;
    private final int fixedBorderColorArgb;
    private final int uncoveredBackgroundColorArgb;
    private final PageLeafRole leafRole;
    private final PageDisplayRect displayRect;
    private final PageDisplayRect clippingRect;
    private final PageDisplayRect fixedBorderRect;
    private final int fixedBorderWidthPx;

    public PageMaterial(
            long generationId,
            int frontPaperColorArgb,
            int reversePaperColorArgb,
            int fixedBorderColorArgb,
            int uncoveredBackgroundColorArgb,
            PageLeafRole leafRole,
            PageDisplayRect displayRect,
            PageDisplayRect clippingRect,
            PageDisplayRect fixedBorderRect,
            int fixedBorderWidthPx) {
        if (generationId < 0) {
            throw new IllegalArgumentException("generationId must not be negative");
        }
        requireOpaque(frontPaperColorArgb, "Front paper");
        requireOpaque(reversePaperColorArgb, "Reverse paper");
        requireOpaque(fixedBorderColorArgb, "Fixed border");
        requireOpaque(uncoveredBackgroundColorArgb, "Uncovered background");
        if (fixedBorderWidthPx <= 0) {
            throw new IllegalArgumentException("Fixed border width must be positive");
        }
        this.generationId = generationId;
        this.frontPaperColorArgb = frontPaperColorArgb;
        this.reversePaperColorArgb = reversePaperColorArgb;
        this.fixedBorderColorArgb = fixedBorderColorArgb;
        this.uncoveredBackgroundColorArgb = uncoveredBackgroundColorArgb;
        this.leafRole = Objects.requireNonNull(leafRole, "leafRole");
        this.displayRect = Objects.requireNonNull(displayRect, "displayRect");
        this.clippingRect = Objects.requireNonNull(clippingRect, "clippingRect");
        this.fixedBorderRect = fixedBorderRect;
        this.fixedBorderWidthPx = fixedBorderWidthPx;
        requireContained(clippingRect, displayRect, "Display rectangle");
        if (fixedBorderRect != null) {
            requireContained(clippingRect, fixedBorderRect, "Fixed border rectangle");
            boolean horizontallyAdjacent =
                    fixedBorderRect.getRightPx() == displayRect.getLeftPx()
                            || fixedBorderRect.getLeftPx() == displayRect.getRightPx();
            if (!horizontallyAdjacent
                    || fixedBorderRect.getTopPx() != displayRect.getTopPx()
                    || fixedBorderRect.getBottomPx() != displayRect.getBottomPx()) {
                throw new IllegalArgumentException(
                        "Fixed border rectangle must be horizontally adjacent to the page");
            }
        }
    }

    public long getGenerationId() {
        return generationId;
    }

    public int getFrontPaperColorArgb() {
        return frontPaperColorArgb;
    }

    public int getReversePaperColorArgb() {
        return reversePaperColorArgb;
    }

    public int getFixedBorderColorArgb() {
        return fixedBorderColorArgb;
    }

    public int getUncoveredBackgroundColorArgb() {
        return uncoveredBackgroundColorArgb;
    }

    public PageLeafRole getLeafRole() {
        return leafRole;
    }

    public PageDisplayRect getDisplayRect() {
        return displayRect;
    }

    public PageDisplayRect getClippingRect() {
        return clippingRect;
    }

    public boolean hasFixedBorderRect() {
        return fixedBorderRect != null;
    }

    public PageDisplayRect getFixedBorderRect() {
        return fixedBorderRect;
    }

    public int getFixedBorderWidthPx() {
        return fixedBorderWidthPx;
    }

    boolean hasSameDeckMaterialIdentity(PageMaterial other) {
        return other != null
                && generationId == other.generationId
                && frontPaperColorArgb == other.frontPaperColorArgb
                && reversePaperColorArgb == other.reversePaperColorArgb
                && fixedBorderColorArgb == other.fixedBorderColorArgb
                && uncoveredBackgroundColorArgb == other.uncoveredBackgroundColorArgb
                && fixedBorderWidthPx == other.fixedBorderWidthPx
                && clippingRect.equals(other.clippingRect);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PageMaterial)) {
            return false;
        }
        PageMaterial material = (PageMaterial) other;
        return generationId == material.generationId
                && frontPaperColorArgb == material.frontPaperColorArgb
                && reversePaperColorArgb == material.reversePaperColorArgb
                && fixedBorderColorArgb == material.fixedBorderColorArgb
                && uncoveredBackgroundColorArgb == material.uncoveredBackgroundColorArgb
                && fixedBorderWidthPx == material.fixedBorderWidthPx
                && leafRole == material.leafRole
                && displayRect.equals(material.displayRect)
                && clippingRect.equals(material.clippingRect)
                && Objects.equals(fixedBorderRect, material.fixedBorderRect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                generationId,
                frontPaperColorArgb,
                reversePaperColorArgb,
                fixedBorderColorArgb,
                uncoveredBackgroundColorArgb,
                leafRole,
                displayRect,
                clippingRect,
                fixedBorderRect,
                fixedBorderWidthPx);
    }

    private static void requireOpaque(int colorArgb, String name) {
        if ((colorArgb >>> 24) != 0xFF) {
            throw new IllegalArgumentException(name + " color must be fully opaque ARGB");
        }
    }

    private static void requireContained(
            PageDisplayRect outer,
            PageDisplayRect inner,
            String name) {
        if (inner.getLeftPx() < outer.getLeftPx()
                || inner.getTopPx() < outer.getTopPx()
                || inner.getRightPx() > outer.getRightPx()
                || inner.getBottomPx() > outer.getBottomPx()) {
            throw new IllegalArgumentException(name + " must fit the clipping rectangle");
        }
    }
}
