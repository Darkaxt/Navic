package karacken.curl;

import java.util.Objects;

/**
 * Immutable client page identity paired with already prepared image content.
 * Generation identifiers are non-negative; negative values are reserved for internal absence.
 *
 * <p>For the Android production API, {@code content} is an immutable, opaque
 * {@link android.graphics.Bitmap.Config#ARGB_8888 ARGB_8888} bitmap. Opaque base pages do not
 * need to report premultiplied alpha because every stored alpha value is fully opaque. The client
 * retains ownership but must not modify or recycle the bitmap after submitting its deck. The
 * client may release or recycle it only after
 * {@link PageSurfaceListener#onDeckReleased(long, DeckReleaseReason)} reports that generation.
 * Filler content is borrowed only to carry dimensions and lifetime; it is never uploaded or
 * sampled by the renderer.
 */
public final class PageImage<T> {
    private final long generationId;
    private final String logicalPageId;
    private final int ordinal;
    private final int widthPx;
    private final int heightPx;
    private final PageDisplayRect displayRect;
    private final T content;
    private final T overlayContent;
    private final boolean filler;
    private final int fillerColorArgb;
    private final PageDisplayRect backingRect;
    private final int backingColorArgb;
    private final PageMaterial material;

    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            T content) {
        this(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                null,
                content,
                null,
                false,
                0,
                null,
                0,
                null);
    }

    /**
     * Creates a page whose texture is rendered into an explicit physical surface rectangle.
     * Texture dimensions may be smaller than the display rectangle without changing placement.
     */
    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            PageDisplayRect displayRect,
            T content) {
        this(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                Objects.requireNonNull(displayRect, "displayRect"),
                content,
                null,
                false,
                0,
                null,
                0,
                null);
    }

    /** Creates an explicitly placed page with complete generation-owned presentation material. */
    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            PageDisplayRect displayRect,
            T content,
            PageMaterial material) {
        this(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                Objects.requireNonNull(displayRect, "displayRect"),
                content,
                null,
                false,
                0,
                fixedBorderRect(material),
                fixedBorderColor(material),
                Objects.requireNonNull(material, "material"));
    }

    /**
     * Creates an explicitly placed page with fixed opaque material beside its raster.
     * The backing must be directly adjacent to one horizontal page edge.
     */
    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            PageDisplayRect displayRect,
            T content,
            PageDisplayRect backingRect,
            int backingColorArgb) {
        this(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                Objects.requireNonNull(displayRect, "displayRect"),
                content,
                null,
                false,
                0,
                Objects.requireNonNull(backingRect, "backingRect"),
                backingColorArgb,
                legacyBackingMaterial(
                        generationId, displayRect, backingRect, backingColorArgb));
    }

    /**
     * Creates a page with an optional ephemeral overlay sampled through the same deformation.
     *
     * <p>The overlay shares this page's identity, generation, dimensions, and bitmap lease. For
     * Android rendering it must be an ARGB_8888 premultiplied bitmap that reports alpha.
     */
    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            T content,
            T overlayContent) {
        this(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                null,
                content,
                overlayContent,
                false,
                0,
                null,
                0,
                null);
    }

    /** Creates an explicitly placed page with an optional ephemeral overlay. */
    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            PageDisplayRect displayRect,
            T content,
            T overlayContent) {
        this(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                Objects.requireNonNull(displayRect, "displayRect"),
                content,
                overlayContent,
                false,
                0,
                null,
                0,
                null);
    }

    /** Creates an explicitly placed page with a deformed overlay and complete material. */
    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            PageDisplayRect displayRect,
            T content,
            T overlayContent,
            PageMaterial material) {
        this(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                Objects.requireNonNull(displayRect, "displayRect"),
                content,
                overlayContent,
                false,
                0,
                fixedBorderRect(material),
                fixedBorderColor(material),
                Objects.requireNonNull(material, "material"));
    }

    /** Creates an explicitly placed page with both a deformed overlay and fixed backing material. */
    public PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            PageDisplayRect displayRect,
            T content,
            T overlayContent,
            PageDisplayRect backingRect,
            int backingColorArgb) {
        this(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                Objects.requireNonNull(displayRect, "displayRect"),
                content,
                overlayContent,
                false,
                0,
                Objects.requireNonNull(backingRect, "backingRect"),
                backingColorArgb,
                legacyBackingMaterial(
                        generationId, displayRect, backingRect, backingColorArgb));
    }

    /** Creates a paper-colored physical filler that borrows content only for its lease. */
    public static <T> PageImage<T> filler(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            T borrowedContent,
            int fillerColorArgb) {
        return new PageImage<>(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                null,
                borrowedContent,
                null,
                true,
                fillerColorArgb,
                null,
                0,
                null);
    }

    /** Creates an explicitly placed paper-colored physical filler. */
    public static <T> PageImage<T> filler(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            PageDisplayRect displayRect,
            T borrowedContent,
            int fillerColorArgb) {
        return new PageImage<>(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                Objects.requireNonNull(displayRect, "displayRect"),
                borrowedContent,
                null,
                true,
                fillerColorArgb,
                null,
                0,
                null);
    }

    /** Creates an explicitly placed filler with complete generation-owned presentation material. */
    public static <T> PageImage<T> filler(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            PageDisplayRect displayRect,
            T borrowedContent,
            PageMaterial material) {
        PageMaterial exact = Objects.requireNonNull(material, "material");
        return new PageImage<>(
                generationId,
                logicalPageId,
                ordinal,
                widthPx,
                heightPx,
                Objects.requireNonNull(displayRect, "displayRect"),
                borrowedContent,
                null,
                true,
                exact.getFrontPaperColorArgb(),
                fixedBorderRect(exact),
                fixedBorderColor(exact),
                exact);
    }

    private PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            PageDisplayRect displayRect,
            T content,
            T overlayContent,
            boolean filler,
            int fillerColorArgb,
            PageDisplayRect backingRect,
            int backingColorArgb,
            PageMaterial material) {
        if (generationId < 0) {
            throw new IllegalArgumentException("generationId must not be negative");
        }
        if (logicalPageId == null || logicalPageId.trim().isEmpty()) {
            throw new IllegalArgumentException("logicalPageId must not be blank");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("page dimensions must be positive");
        }
        if (filler && overlayContent != null) {
            throw new IllegalArgumentException("Filler pages cannot have overlays");
        }
        if (filler && (fillerColorArgb >>> 24) != 0xFF) {
            throw new IllegalArgumentException("Filler color must be fully opaque ARGB");
        }
        if (backingRect != null) {
            if (displayRect == null) {
                throw new IllegalArgumentException(
                        "Fixed backing material requires explicit page placement");
            }
            if ((backingColorArgb >>> 24) != 0xFF) {
                throw new IllegalArgumentException("Backing color must be fully opaque ARGB");
            }
            boolean horizontallyAdjacent = backingRect.getRightPx() == displayRect.getLeftPx()
                    || backingRect.getLeftPx() == displayRect.getRightPx();
            if (!horizontallyAdjacent
                    || backingRect.getTopPx() != displayRect.getTopPx()
                    || backingRect.getBottomPx() != displayRect.getBottomPx()) {
                throw new IllegalArgumentException(
                        "Fixed backing material must be horizontally adjacent to the page");
            }
        }
        if (material != null) {
            if (material.getGenerationId() != generationId) {
                throw new IllegalArgumentException(
                        "Page material must share the page generation");
            }
            if (!Objects.equals(material.getDisplayRect(), displayRect)) {
                throw new IllegalArgumentException(
                        "Page material must share the physical display rectangle");
            }
        }
        this.generationId = generationId;
        this.logicalPageId = logicalPageId;
        this.ordinal = ordinal;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.displayRect = displayRect;
        this.content = Objects.requireNonNull(content, "content");
        this.overlayContent = overlayContent;
        this.filler = filler;
        this.fillerColorArgb = fillerColorArgb;
        this.backingRect = backingRect;
        this.backingColorArgb = backingColorArgb;
        this.material = material;
    }

    public long getGenerationId() {
        return generationId;
    }

    public String getLogicalPageId() {
        return logicalPageId;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public int getWidthPx() {
        return widthPx;
    }

    public int getHeightPx() {
        return heightPx;
    }

    public boolean hasExplicitDisplayRect() {
        return displayRect != null;
    }

    public PageDisplayRect getDisplayRect() {
        return displayRect;
    }

    public T getContent() {
        return content;
    }

    public boolean hasOverlay() {
        return overlayContent != null;
    }

    public T getOverlayContent() {
        return overlayContent;
    }

    public boolean isFiller() {
        return filler;
    }

    public int getFillerColorArgb() {
        if (!filler) {
            throw new IllegalStateException("Normal pages do not have a filler color");
        }
        return fillerColorArgb;
    }

    public boolean hasBacking() {
        return backingRect != null;
    }

    public PageDisplayRect getBackingRect() {
        if (backingRect == null) {
            throw new IllegalStateException("Page does not have fixed backing material");
        }
        return backingRect;
    }

    public int getBackingColorArgb() {
        if (backingRect == null) {
            throw new IllegalStateException("Page does not have fixed backing material");
        }
        return backingColorArgb;
    }

    public boolean hasCompleteMaterial() {
        return material != null;
    }

    public PageMaterial getMaterial() {
        return material;
    }

    String identityKey() {
        return generationId + "\u0000" + logicalPageId + "\u0000" + ordinal;
    }

    String overlayIdentityKey() {
        return identityKey() + "\u0000overlay";
    }

    private static PageDisplayRect fixedBorderRect(PageMaterial material) {
        PageMaterial exact = Objects.requireNonNull(material, "material");
        return exact.getFixedBorderRect();
    }

    private static int fixedBorderColor(PageMaterial material) {
        return Objects.requireNonNull(material, "material").getFixedBorderColorArgb();
    }

    private static PageMaterial legacyBackingMaterial(
            long generationId,
            PageDisplayRect displayRect,
            PageDisplayRect backingRect,
            int backingColorArgb) {
        int left = Math.min(displayRect.getLeftPx(), backingRect.getLeftPx());
        int top = Math.min(displayRect.getTopPx(), backingRect.getTopPx());
        int right = Math.max(displayRect.getRightPx(), backingRect.getRightPx());
        int bottom = Math.max(displayRect.getBottomPx(), backingRect.getBottomPx());
        return new PageMaterial(
                generationId,
                backingColorArgb,
                backingColorArgb,
                backingColorArgb,
                backingColorArgb,
                PageLeafRole.FULL,
                displayRect,
                new PageDisplayRect(left, top, right, bottom),
                backingRect,
                1);
    }
}
