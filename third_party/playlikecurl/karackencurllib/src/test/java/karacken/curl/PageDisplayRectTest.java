package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PageDisplayRectTest {
    @Test
    public void displayPlacementIsIndependentFromTextureResolution() {
        PageDisplayRect placement = new PageDisplayRect(80, 0, 1530, 1848);
        PageImage<String> balanced = new PageImage<>(
                41, "balanced", 0, 725, 924, placement, "balanced-bitmap");
        PageImage<String> full = new PageImage<>(
                41, "full", 1, 1450, 1848, placement, "full-bitmap");

        assertEquals(725, balanced.getWidthPx());
        assertEquals(924, balanced.getHeightPx());
        assertEquals(1450, full.getWidthPx());
        assertEquals(1848, full.getHeightPx());
        assertTrue(balanced.hasExplicitDisplayRect());
        assertTrue(full.hasExplicitDisplayRect());
        assertEquals(placement, balanced.getDisplayRect());
        assertEquals(placement, full.getDisplayRect());
        assertEquals(1450, placement.getWidthPx());
        assertEquals(1848, placement.getHeightPx());
    }

    @Test
    public void legacyPageImageKeepsImplicitPlacement() {
        PageImage<String> page = new PageImage<>(
                42, "legacy", 0, 1200, 1800, "bitmap");

        assertFalse(page.hasExplicitDisplayRect());
        assertEquals(null, page.getDisplayRect());
    }

    @Test
    public void displayRectUsesTopOriginCoordinatesAndConvertsToGlViewport() {
        PageDisplayRect placement = new PageDisplayRect(60, 100, 1510, 1800);

        assertEquals(60, placement.getLeftPx());
        assertEquals(100, placement.getTopPx());
        assertEquals(1510, placement.getRightPx());
        assertEquals(1800, placement.getBottomPx());
        assertEquals(48, placement.glBottomPx(1848));
        assertTrue(placement.fitsWithin(2960, 1848));
        assertFalse(placement.fitsWithin(1500, 1848));
    }

    @Test
    public void displayRectRejectsEmptyOrNegativeGeometry() {
        assertThrows(IllegalArgumentException.class, () ->
                new PageDisplayRect(-1, 0, 100, 100));
        assertThrows(IllegalArgumentException.class, () ->
                new PageDisplayRect(0, -1, 100, 100));
        assertThrows(IllegalArgumentException.class, () ->
                new PageDisplayRect(100, 0, 100, 100));
        assertThrows(IllegalArgumentException.class, () ->
                new PageDisplayRect(0, 100, 100, 100));
    }

    @Test
    public void fillerCanUseTheSameExplicitPhysicalPlacement() {
        PageDisplayRect placement = new PageDisplayRect(0, 0, 1450, 1848);
        PageImage<String> filler = PageImage.filler(
                43,
                "filler",
                0,
                725,
                924,
                placement,
                "borrowed",
                0xFFF5F2EA);

        assertTrue(filler.isFiller());
        assertEquals(placement, filler.getDisplayRect());
    }

    @Test
    public void portraitDeckRequiresOnePhysicalPlacementForAllSlots() {
        PageDisplayRect placement = new PageDisplayRect(100, 0, 1550, 1848);
        PageDisplayRect shifted = new PageDisplayRect(101, 0, 1551, 1848);

        assertThrows(IllegalArgumentException.class, () -> new PortraitPageDeck<>(
                page(44, "previous", 0, placement),
                page(44, "current", 1, placement),
                page(44, "next", 2, shifted)));
        assertThrows(IllegalArgumentException.class, () -> new PortraitPageDeck<>(
                page(44, "previous", 0, placement),
                new PageImage<>(44, "current", 1, 725, 924, "implicit"),
                page(44, "next", 2, placement)));
    }

    @Test
    public void landscapeDeckRequiresStableNonOverlappingPhysicalLeafSlots() {
        PageDisplayRect left = new PageDisplayRect(30, 0, 1480, 1848);
        PageDisplayRect right = new PageDisplayRect(1480, 0, 2930, 1848);
        PageDisplayRect shiftedLeft = new PageDisplayRect(31, 0, 1481, 1848);
        PageDisplayRect overlappingRight = new PageDisplayRect(1479, 0, 2929, 1848);

        assertThrows(IllegalArgumentException.class, () -> new LandscapePageDeck<>(
                page(45, "previous-left", 0, left),
                page(45, "previous-right", 1, right),
                page(45, "current-left", 2, left),
                page(45, "current-right", 3, right),
                page(45, "next-left", 4, shiftedLeft),
                page(45, "next-right", 5, right)));
        assertThrows(IllegalArgumentException.class, () -> new LandscapePageDeck<>(
                page(46, "previous-left", 0, left),
                page(46, "previous-right", 1, overlappingRight),
                page(46, "current-left", 2, left),
                page(46, "current-right", 3, overlappingRight),
                page(46, "next-left", 4, left),
                page(46, "next-right", 5, overlappingRight)));
    }

    @Test
    public void gestureNormalizationUsesThePhysicalPageOffsetAndWidth() {
        PageDisplayRect placement = new PageDisplayRect(80, 0, 1530, 1848);
        PortraitPageDeck<String> deck = new PortraitPageDeck<>(
                page(47, "previous", 0, placement),
                page(47, "current", 1, placement),
                page(47, "next", 2, placement));

        PageDisplayRect gestureRect = PageSurfaceView.gestureDisplayRect(
                deck, 900f, 2960, 1848);

        assertEquals(placement, gestureRect);
        assertEquals(
                0f,
                PageSurfaceView.logicalGestureX(
                        80f, gestureRect, ReadingDirection.LEFT_TO_RIGHT),
                0.001f);
        assertEquals(
                1450f,
                PageSurfaceView.logicalGestureX(
                        1530f, gestureRect, ReadingDirection.LEFT_TO_RIGHT),
                0.001f);
        assertEquals(
                1450f,
                PageSurfaceView.logicalGestureX(
                        80f, gestureRect, ReadingDirection.RIGHT_TO_LEFT),
                0.001f);
        assertEquals(
                0f,
                PageSurfaceView.logicalGestureX(
                        1530f, gestureRect, ReadingDirection.RIGHT_TO_LEFT),
                0.001f);
    }

    @Test
    public void landscapeGestureUsesThePhysicalLeafUnderTheFinger() {
        PageDisplayRect left = new PageDisplayRect(30, 0, 1480, 1848);
        PageDisplayRect right = new PageDisplayRect(1490, 0, 2930, 1848);
        LandscapePageDeck<String> deck = new LandscapePageDeck<>(
                page(48, "previous-left", 0, left),
                page(48, "previous-right", 1, right),
                page(48, "current-left", 2, left),
                page(48, "current-right", 3, right),
                page(48, "next-left", 4, left),
                page(48, "next-right", 5, right));

        assertEquals(left, PageSurfaceView.gestureDisplayRect(
                deck, 100f, 2960, 1848));
        assertEquals(right, PageSurfaceView.gestureDisplayRect(
                deck, 2800f, 2960, 1848));
        assertEquals(right, PageSurfaceView.gestureDisplayRect(
                deck, 1487f, 2960, 1848));
    }

    @Test
    public void conflictingGenerationDetectsChangedPhysicalPlacement() {
        PageDisplayRect firstPlacement = new PageDisplayRect(80, 0, 1530, 1848);
        PageDisplayRect shiftedPlacement = new PageDisplayRect(81, 0, 1531, 1848);
        PageDeckCoordinator<String> coordinator = new PageDeckCoordinator<>();
        PortraitPageDeck<String> first = new PortraitPageDeck<>(
                page(49, "previous", 0, firstPlacement),
                page(49, "current", 1, firstPlacement),
                page(49, "next", 2, firstPlacement));
        PortraitPageDeck<String> shifted = new PortraitPageDeck<>(
                page(49, "previous", 0, shiftedPlacement),
                page(49, "current", 1, shiftedPlacement),
                page(49, "next", 2, shiftedPlacement));

        assertEquals(
                PageDeckCoordinator.Placement.ACTIVE,
                coordinator.offer(first).getPlacement());
        PageDeckCoordinator.Offer<String> conflict = coordinator.offer(shifted);
        assertEquals(PageDeckCoordinator.Placement.REJECTED, conflict.getPlacement());
        assertEquals(
                DeckRejectionReason.CONFLICTING_GENERATION,
                conflict.getRejectionReason());
    }

    private static PageImage<String> page(
            long generationId,
            String id,
            int ordinal,
            PageDisplayRect placement) {
        return new PageImage<>(
                generationId,
                id,
                ordinal,
                725,
                924,
                placement,
                "bitmap-" + ordinal);
    }
}
