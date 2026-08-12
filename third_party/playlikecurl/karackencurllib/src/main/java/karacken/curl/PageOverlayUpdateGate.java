package karacken.curl;

import java.util.LinkedHashSet;
import java.util.List;
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
