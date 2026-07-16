package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class PageDeckContractTest {
    @Test
    public void portraitDeckCarriesOpaqueIdentityAndExactSlots() {
        PageImage<String> previous = page(7, "chapter-2/page-4", 4);
        PageImage<String> current = page(7, "chapter-2/page-5", 5);
        PageImage<String> next = page(7, "chapter-2/page-6", 6);

        PortraitPageDeck<String> deck = new PortraitPageDeck<>(
                previous, current, next);

        assertEquals(7, deck.getGenerationId());
        assertEquals(PageDeckMode.PORTRAIT, deck.getMode());
        assertSame(previous, deck.getPrevious());
        assertSame(current, deck.getCurrent());
        assertSame(next, deck.getNext());
        assertEquals("chapter-2/page-5", deck.getCurrent().getLogicalPageId());
        assertEquals("bitmap-5", deck.getCurrent().getContent());
    }

    @Test
    public void landscapeDeckCarriesSixPhysicalLeaves() {
        LandscapePageDeck<String> deck = new LandscapePageDeck<>(
                page(9, "previous-left", 0),
                page(9, "previous-right", 1),
                page(9, "current-left", 2),
                page(9, "current-right", 3),
                page(9, "next-left", 4),
                page(9, "next-right", 5));

        assertEquals(9, deck.getGenerationId());
        assertEquals(PageDeckMode.LANDSCAPE, deck.getMode());
        assertEquals("previous-left", deck.getPreviousLeft().getLogicalPageId());
        assertEquals("previous-right", deck.getPreviousRight().getLogicalPageId());
        assertEquals("current-left", deck.getCurrentLeft().getLogicalPageId());
        assertEquals("current-right", deck.getCurrentRight().getLogicalPageId());
        assertEquals("next-left", deck.getNextLeft().getLogicalPageId());
        assertEquals("next-right", deck.getNextRight().getLogicalPageId());
    }

    @Test
    public void deckRejectsPagesFromDifferentGenerations() {
        assertThrows(IllegalArgumentException.class, () -> new PortraitPageDeck<>(
                page(3, "previous", 0),
                page(4, "current", 1),
                page(3, "next", 2)));
    }

    @Test
    public void portraitBoundaryDuplicationDoesNotCreateFakePages() {
        PageImage<String> first = page(11, "first", 0);
        PageImage<String> second = page(11, "second", 1);
        PortraitPageDeck<String> deck = new PortraitPageDeck<>(first, first, second);

        PageDeckWindow<String> window = PageDeckWindow.from(deck);

        assertEquals(List.of(first, second), window.getPages());
        assertEquals(0, window.getCurrentIndex());
    }

    @Test
    public void landscapeWindowPreservesCurrentLeftPosition() {
        PageImage<String> previousLeft = page(13, "previous-left", 0);
        PageImage<String> previousRight = page(13, "previous-right", 1);
        PageImage<String> currentLeft = page(13, "current-left", 2);
        PageImage<String> currentRight = page(13, "current-right", 3);
        PageImage<String> nextLeft = page(13, "next-left", 4);
        PageImage<String> nextRight = page(13, "next-right", 5);
        LandscapePageDeck<String> deck = new LandscapePageDeck<>(
                previousLeft,
                previousRight,
                currentLeft,
                currentRight,
                nextLeft,
                nextRight);

        PageDeckWindow<String> window = PageDeckWindow.from(deck);

        assertEquals(
                List.of(
                        previousLeft,
                        previousRight,
                        currentLeft,
                        currentRight,
                        nextLeft,
                        nextRight),
                window.getPages());
        assertEquals(2, window.getCurrentIndex());
    }

    @Test
    public void pageImageCarriesOptionalGenerationBoundOverlay() {
        PageImage<String> baseOnly = page(17, "base-only", 0);
        PageImage<String> highlighted = new PageImage<>(
                17,
                "highlighted",
                1,
                1200,
                1800,
                "base-bitmap",
                "highlight-overlay");

        assertFalse(baseOnly.hasOverlay());
        assertTrue(highlighted.hasOverlay());
        assertEquals("highlight-overlay", highlighted.getOverlayContent());
        assertEquals(
                highlighted.identityKey() + "\u0000overlay",
                highlighted.overlayIdentityKey());
    }

    private static PageImage<String> page(
            long generationId,
            String logicalPageId,
            int ordinal) {
        return new PageImage<>(
                generationId,
                logicalPageId,
                ordinal,
                1200,
                1800,
                "bitmap-" + ordinal);
    }
}
