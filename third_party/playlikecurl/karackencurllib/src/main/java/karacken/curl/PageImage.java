package karacken.curl;

import java.util.Objects;

/**
 * Immutable client page identity paired with already prepared image content.
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
    private final T content;
    private final T overlayContent;
    private final boolean filler;
    private final int fillerColorArgb;

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
                content,
                null,
                false,
                0);
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
                content,
                overlayContent,
                false,
                0);
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
                borrowedContent,
                null,
                true,
                fillerColorArgb);
    }

    private PageImage(
            long generationId,
            String logicalPageId,
            int ordinal,
            int widthPx,
            int heightPx,
            T content,
            T overlayContent,
            boolean filler,
            int fillerColorArgb) {
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
        this.generationId = generationId;
        this.logicalPageId = logicalPageId;
        this.ordinal = ordinal;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.content = Objects.requireNonNull(content, "content");
        this.overlayContent = overlayContent;
        this.filler = filler;
        this.fillerColorArgb = fillerColorArgb;
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

    String identityKey() {
        return generationId + "\u0000" + logicalPageId + "\u0000" + ordinal;
    }

    String overlayIdentityKey() {
        return identityKey() + "\u0000overlay";
    }
}
