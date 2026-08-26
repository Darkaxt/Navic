package karacken.curl;

import java.util.Objects;

/** One renderer-owned transparent overlay targeting an exact owned page leaf. */
public final class PageOverlayImage<T> {
    private final long deckGenerationId;
    private final String destinationCommitIdentity;
    private final Object receiptIdentity;
    private final int visualPageOrdinal;
    private final PageLeafRole leafRole;
    private final long anchorGeneration;
    private final long boundaryGeneration;
    private final int ordinal;
    private final T content;

    /**
     * Compatibility constructor for callers that do not submit to the production ownership gate.
     * Production submission rejects this deliberately incomplete ownership identity.
     */
    public PageOverlayImage(int ordinal, T content) {
        this(-1L, null, null, -1, null, -1L, -1L, ordinal, content, false);
    }

    public PageOverlayImage(
            long deckGenerationId,
            String destinationCommitIdentity,
            Object receiptIdentity,
            int visualPageOrdinal,
            PageLeafRole leafRole,
            long anchorGeneration,
            long boundaryGeneration,
            int ordinal,
            T content) {
        this(
                deckGenerationId,
                destinationCommitIdentity,
                receiptIdentity,
                visualPageOrdinal,
                leafRole,
                anchorGeneration,
                boundaryGeneration,
                ordinal,
                content,
                true);
    }

    private PageOverlayImage(
            long deckGenerationId,
            String destinationCommitIdentity,
            Object receiptIdentity,
            int visualPageOrdinal,
            PageLeafRole leafRole,
            long anchorGeneration,
            long boundaryGeneration,
            int ordinal,
            T content,
            boolean complete) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        if (complete) {
            if (deckGenerationId < 0) {
                throw new IllegalArgumentException("deckGenerationId must not be negative");
            }
            if (destinationCommitIdentity == null
                    || destinationCommitIdentity.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "destinationCommitIdentity must not be blank");
            }
            if (visualPageOrdinal < 0) {
                throw new IllegalArgumentException("visualPageOrdinal must not be negative");
            }
            if (anchorGeneration <= 0 || boundaryGeneration < 0) {
                throw new IllegalArgumentException(
                        "Highlight boundary ownership must be non-negative and anchored");
            }
            Objects.requireNonNull(receiptIdentity, "receiptIdentity");
            Objects.requireNonNull(leafRole, "leafRole");
        }
        this.deckGenerationId = deckGenerationId;
        this.destinationCommitIdentity = destinationCommitIdentity;
        this.receiptIdentity = receiptIdentity;
        this.visualPageOrdinal = visualPageOrdinal;
        this.leafRole = leafRole;
        this.anchorGeneration = anchorGeneration;
        this.boundaryGeneration = boundaryGeneration;
        this.ordinal = ordinal;
        this.content = Objects.requireNonNull(content, "content");
    }

    public long getDeckGenerationId() {
        return deckGenerationId;
    }

    public String getDestinationCommitIdentity() {
        return destinationCommitIdentity;
    }

    public Object getReceiptIdentity() {
        return receiptIdentity;
    }

    public int getVisualPageOrdinal() {
        return visualPageOrdinal;
    }

    public PageLeafRole getLeafRole() {
        return leafRole;
    }

    public long getAnchorGeneration() {
        return anchorGeneration;
    }

    public long getBoundaryGeneration() {
        return boundaryGeneration;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public T getContent() {
        return content;
    }

    boolean hasCompleteOwnership() {
        return deckGenerationId >= 0
                && destinationCommitIdentity != null
                && !destinationCommitIdentity.trim().isEmpty()
                && receiptIdentity != null
                && visualPageOrdinal >= 0
                && leafRole != null
                && anchorGeneration > 0
                && boundaryGeneration >= 0;
    }

    boolean hasSameReceiptOwnership(PageOverlayImage<?> other) {
        return other != null
                && deckGenerationId == other.deckGenerationId
                && destinationCommitIdentity.equals(other.destinationCommitIdentity)
                && receiptIdentity.equals(other.receiptIdentity)
                && visualPageOrdinal == other.visualPageOrdinal
                && anchorGeneration == other.anchorGeneration
                && boundaryGeneration == other.boundaryGeneration;
    }
}
