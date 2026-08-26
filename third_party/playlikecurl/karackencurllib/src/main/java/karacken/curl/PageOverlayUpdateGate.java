package karacken.curl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Pure current-page and interaction admission for atomic page-overlay replacement. */
final class PageOverlayUpdateGate {
    private PageOverlayUpdateGate() {}

    static PageOverlayUpdateResult evaluate(
            PageDeck<?> activeDeck,
            Set<Long> preparedGenerations,
            boolean surfaceAvailable,
            boolean interactionActive,
            boolean updatePending,
            long requestedGenerationId,
            List<Integer> requestedOrdinals) {
        if (requestedGenerationId < 0 || requestedOrdinals == null) {
            return PageOverlayUpdateResult.INVALID_CONTENT;
        }
        if (!surfaceAvailable) {
            return PageOverlayUpdateResult.SURFACE_UNAVAILABLE;
        }
        if (activeDeck == null
                || activeDeck.getGenerationId() != requestedGenerationId
                || !preparedGenerations.contains(requestedGenerationId)) {
            return PageOverlayUpdateResult.STALE_TARGET;
        }
        Set<Integer> uniqueOrdinals = new LinkedHashSet<>();
        for (Integer ordinal : requestedOrdinals) {
            if (ordinal == null || ordinal < 0 || !uniqueOrdinals.add(ordinal)) {
                return PageOverlayUpdateResult.INVALID_CONTENT;
            }
            PageImage<?> page = currentPage(activeDeck, ordinal);
            if (page == null || page.isFiller() || page.hasOverlay()) {
                return PageOverlayUpdateResult.STALE_TARGET;
            }
        }
        if (interactionActive && !requestedOrdinals.isEmpty()) {
            return PageOverlayUpdateResult.INTERACTION_ACTIVE;
        }
        if (updatePending) {
            return PageOverlayUpdateResult.UPDATE_PENDING;
        }
        return PageOverlayUpdateResult.ACCEPTED;
    }

    static PageOverlayUpdateResult evaluateOwned(
            PageDeck<?> activeDeck,
            Set<Long> preparedGenerations,
            boolean surfaceAvailable,
            boolean interactionActive,
            boolean updatePending,
            long requestedGenerationId,
            List<? extends PageOverlayImage<?>> requestedOverlays) {
        return evaluate(
                activeDeck,
                preparedGenerations,
                surfaceAvailable,
                interactionActive,
                updatePending,
                requestedGenerationId,
                requestedOverlays,
                true);
    }

    static PageOverlayUpdateResult evaluate(
            PageDeck<?> activeDeck,
            Set<Long> preparedGenerations,
            boolean surfaceAvailable,
            boolean interactionActive,
            boolean updatePending,
            long requestedGenerationId,
            List<? extends PageOverlayImage<?>> requestedOverlays,
            boolean requireCompleteOwnership) {
        if (!requireCompleteOwnership || requestedOverlays == null) {
            return PageOverlayUpdateResult.INVALID_CONTENT;
        }
        List<Integer> requestedOrdinals = new ArrayList<>(requestedOverlays.size());
        for (PageOverlayImage<?> overlay : requestedOverlays) {
            if (overlay == null) {
                return PageOverlayUpdateResult.INVALID_CONTENT;
            }
            requestedOrdinals.add(overlay.getOrdinal());
        }
        PageOverlayUpdateResult base = evaluate(
                activeDeck,
                preparedGenerations,
                surfaceAvailable,
                interactionActive,
                updatePending,
                requestedGenerationId,
                requestedOrdinals);
        if (base != PageOverlayUpdateResult.ACCEPTED || requestedOverlays.isEmpty()) {
            return base;
        }

        PageOverlayImage<?> first = requestedOverlays.get(0);
        for (PageOverlayImage<?> overlay : requestedOverlays) {
            if (!overlay.hasCompleteOwnership()) {
                return PageOverlayUpdateResult.INVALID_CONTENT;
            }
            if (overlay.getDeckGenerationId() != requestedGenerationId) {
                return PageOverlayUpdateResult.STALE_TARGET;
            }
            PageImage<?> page = currentPage(activeDeck, overlay.getOrdinal());
            PageMaterial material = page == null ? null : page.getMaterial();
            if (material == null || material.getLeafRole() != overlay.getLeafRole()) {
                return PageOverlayUpdateResult.STALE_TARGET;
            }
            if (!first.hasSameReceiptOwnership(overlay)
                    || !Objects.equals(
                            first.getDestinationCommitIdentity(),
                            overlay.getDestinationCommitIdentity())
                    || !Objects.equals(first.getReceiptIdentity(), overlay.getReceiptIdentity())
                    || first.getVisualPageOrdinal() != overlay.getVisualPageOrdinal()
                    || first.getAnchorGeneration() != overlay.getAnchorGeneration()
                    || first.getBoundaryGeneration() != overlay.getBoundaryGeneration()) {
                return PageOverlayUpdateResult.INVALID_CONTENT;
            }
        }
        return PageOverlayUpdateResult.ACCEPTED;
    }

    static PageImage<?> currentPage(PageDeck<?> deck, int ordinal) {
        if (deck instanceof PortraitPageDeck<?>) {
            PageImage<?> current = ((PortraitPageDeck<?>) deck).getCurrent();
            return current.getOrdinal() == ordinal ? current : null;
        }
        if (deck instanceof LandscapePageDeck<?>) {
            LandscapePageDeck<?> spread = (LandscapePageDeck<?>) deck;
            if (spread.getCurrentLeft().getOrdinal() == ordinal) {
                return spread.getCurrentLeft();
            }
            if (spread.getCurrentRight().getOrdinal() == ordinal) {
                return spread.getCurrentRight();
            }
        }
        return null;
    }
}
