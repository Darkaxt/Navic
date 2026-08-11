package karacken.curl;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import org.junit.Test;

public class PageOverlayUpdateGateTest {
    @Test
    public void currentPreparedGenerationAndPageAreAccepted() {
        PortraitPageDeck<String> deck = portraitDeck(7L);

        assertEquals(
                PageOverlayUpdateResult.ACCEPTED,
                PageOverlayUpdateGate.evaluate(
                        deck,
                        new LinkedHashSet<>(Collections.singletonList(7L)),
                        true,
                        false,
                        false,
                        7L,
                        Collections.singletonList(1)));
    }

    @Test
    public void staleGenerationOrPageIsRejected() {
        PortraitPageDeck<String> deck = portraitDeck(7L);
        LinkedHashSet<Long> prepared = new LinkedHashSet<>(Collections.singletonList(7L));

        assertEquals(
                PageOverlayUpdateResult.STALE_TARGET,
                PageOverlayUpdateGate.evaluate(
                        deck, prepared, true, false, false, 8L, Collections.singletonList(1)));
        assertEquals(
                PageOverlayUpdateResult.STALE_TARGET,
                PageOverlayUpdateGate.evaluate(
                        deck, prepared, true, false, false, 7L, Collections.singletonList(4)));
    }

    @Test
    public void activeInteractionOrPendingUploadSuspendsReplacement() {
        PortraitPageDeck<String> deck = portraitDeck(7L);
        LinkedHashSet<Long> prepared = new LinkedHashSet<>(Collections.singletonList(7L));

        assertEquals(
                PageOverlayUpdateResult.INTERACTION_ACTIVE,
                PageOverlayUpdateGate.evaluate(
                        deck, prepared, true, true, false, 7L, Collections.singletonList(1)));
        assertEquals(
                PageOverlayUpdateResult.UPDATE_PENDING,
                PageOverlayUpdateGate.evaluate(
                        deck, prepared, true, false, true, 7L, Collections.singletonList(1)));
    }

    @Test
    public void everyLeafInAnAtomicReplacementMustBelongToTheCurrentDeck() {
        LandscapePageDeck<String> deck = landscapeDeck(7L);
        LinkedHashSet<Long> prepared = new LinkedHashSet<>(Collections.singletonList(7L));

        assertEquals(
                PageOverlayUpdateResult.ACCEPTED,
                PageOverlayUpdateGate.evaluate(
                        deck, prepared, true, false, false, 7L, Arrays.asList(2, 3)));
        assertEquals(
                PageOverlayUpdateResult.STALE_TARGET,
                PageOverlayUpdateGate.evaluate(
                        deck, prepared, true, false, false, 7L, Arrays.asList(2, 4)));
    }

    private static PortraitPageDeck<String> portraitDeck(long generationId) {
        return new PortraitPageDeck<>(
                page(generationId, 0),
                page(generationId, 1),
                page(generationId, 2));
    }

    private static LandscapePageDeck<String> landscapeDeck(long generationId) {
        return new LandscapePageDeck<>(
                page(generationId, 0),
                page(generationId, 1),
                page(generationId, 2),
                page(generationId, 3),
                page(generationId, 4),
                page(generationId, 5));
    }

    private static PageImage<String> page(long generationId, int ordinal) {
        return new PageImage<>(generationId, "page-" + ordinal, ordinal, 100, 200, "content");
    }
}
