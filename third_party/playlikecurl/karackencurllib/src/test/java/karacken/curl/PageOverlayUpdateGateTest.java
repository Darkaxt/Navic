package karacken.curl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public void currentGenerationClearTakesPriorityOverActiveInteraction() {
        PortraitPageDeck<String> deck = portraitDeck(7L);
        LinkedHashSet<Long> prepared = new LinkedHashSet<>(Collections.singletonList(7L));

        assertEquals(
                PageOverlayUpdateResult.ACCEPTED,
                PageOverlayUpdateGate.evaluate(
                        deck, prepared, true, true, false, 7L, Collections.emptyList()));
        assertEquals(
                PageOverlayUpdateResult.INTERACTION_ACTIVE,
                PageOverlayUpdateGate.evaluate(
                        deck, prepared, true, true, false, 7L, Collections.singletonList(1)));
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

    @Test
    public void completeOverlayOwnershipMustMatchTheCurrentDeckAndLeaf() {
        PortraitPageDeck<String> deck = ownedPortraitDeck(7L);
        LinkedHashSet<Long> prepared = new LinkedHashSet<>(Collections.singletonList(7L));
        PageOverlayImage<String> owned = overlay(
                7L, "destination-a", "receipt-a", 12, PageLeafRole.FULL, 4L, 9L, 1);

        assertEquals(
                PageOverlayUpdateResult.ACCEPTED,
                PageOverlayUpdateGate.evaluateOwned(
                        deck, prepared, true, false, false, 7L,
                        Collections.singletonList(owned)));
        assertEquals(
                PageOverlayUpdateResult.STALE_TARGET,
                PageOverlayUpdateGate.evaluateOwned(
                        deck, prepared, true, false, false, 7L,
                        Collections.singletonList(overlay(
                                8L, "destination-a", "receipt-a", 12,
                                PageLeafRole.FULL, 4L, 9L, 1))));
        assertEquals(
                PageOverlayUpdateResult.STALE_TARGET,
                PageOverlayUpdateGate.evaluateOwned(
                        deck, prepared, true, false, false, 7L,
                        Collections.singletonList(overlay(
                                7L, "destination-a", "receipt-a", 12,
                                PageLeafRole.LEFT, 4L, 9L, 1))));
        assertEquals(
                PageOverlayUpdateResult.INVALID_CONTENT,
                PageOverlayUpdateGate.evaluateOwned(
                        deck, prepared, true, false, false, 7L,
                        Collections.singletonList(new PageOverlayImage<>(1, "legacy-mask"))));
    }

    @Test
    public void atomicSpreadReplacementRejectsMixedReceiptOrDestinationOwnership() {
        LandscapePageDeck<String> deck = ownedLandscapeDeck(7L);
        LinkedHashSet<Long> prepared = new LinkedHashSet<>(Collections.singletonList(7L));
        PageOverlayImage<String> left = overlay(
                7L, "destination-a", "receipt-a", 12, PageLeafRole.LEFT, 4L, 9L, 2);
        PageOverlayImage<String> right = overlay(
                7L, "destination-b", "receipt-a", 12, PageLeafRole.RIGHT, 4L, 9L, 3);

        assertEquals(
                PageOverlayUpdateResult.INVALID_CONTENT,
                PageOverlayUpdateGate.evaluateOwned(
                        deck, prepared, true, false, false, 7L, Arrays.asList(left, right)));
    }

    @Test
    public void rendererGateChecksCompleteDestinationAndBoundaryOwnership()
            throws IOException {
        String overlay = productionSource("PageOverlayImage.java");
        String gate = productionSource("PageOverlayUpdateGate.java");
        String renderer = productionSource("PageRenderer.java");
        String[] ownershipAccessors = {
            "getDeckGenerationId()",
            "getDestinationCommitIdentity()",
            "getReceiptIdentity()",
            "getVisualPageOrdinal()",
            "getLeafRole()",
            "getBoundaryGeneration()"
        };

        for (String accessor : ownershipAccessors) {
            assertTrue("Overlay omits " + accessor, overlay.contains(accessor));
            assertTrue("Admission omits " + accessor, gate.contains(accessor));
            assertTrue("Renderer omits " + accessor, renderer.contains(accessor));
        }
    }

    private static String productionSource(String fileName) throws IOException {
        return Files.readString(
                Path.of("src/main/java/karacken/curl/" + fileName),
                StandardCharsets.UTF_8);
    }

    private static PageOverlayImage<String> overlay(
            long deckGenerationId,
            String destinationCommitIdentity,
            Object receiptIdentity,
            int visualPageOrdinal,
            PageLeafRole leafRole,
            long anchorGeneration,
            long boundaryGeneration,
            int ordinal) {
        return new PageOverlayImage<>(
                deckGenerationId,
                destinationCommitIdentity,
                receiptIdentity,
                visualPageOrdinal,
                leafRole,
                anchorGeneration,
                boundaryGeneration,
                ordinal,
                "mask-" + ordinal);
    }

    private static PortraitPageDeck<String> ownedPortraitDeck(long generationId) {
        PageDisplayRect display = new PageDisplayRect(0, 0, 100, 200);
        return new PortraitPageDeck<>(
                ownedPage(generationId, 0, PageLeafRole.FULL, display, display),
                ownedPage(generationId, 1, PageLeafRole.FULL, display, display),
                ownedPage(generationId, 2, PageLeafRole.FULL, display, display));
    }

    private static LandscapePageDeck<String> ownedLandscapeDeck(long generationId) {
        PageDisplayRect clip = new PageDisplayRect(0, 0, 200, 200);
        PageDisplayRect left = new PageDisplayRect(0, 0, 100, 200);
        PageDisplayRect right = new PageDisplayRect(100, 0, 200, 200);
        return new LandscapePageDeck<>(
                ownedPage(generationId, 0, PageLeafRole.LEFT, left, clip),
                ownedPage(generationId, 1, PageLeafRole.RIGHT, right, clip),
                ownedPage(generationId, 2, PageLeafRole.LEFT, left, clip),
                ownedPage(generationId, 3, PageLeafRole.RIGHT, right, clip),
                ownedPage(generationId, 4, PageLeafRole.LEFT, left, clip),
                ownedPage(generationId, 5, PageLeafRole.RIGHT, right, clip));
    }

    private static PageImage<String> ownedPage(
            long generationId,
            int ordinal,
            PageLeafRole leafRole,
            PageDisplayRect display,
            PageDisplayRect clip) {
        PageMaterial material = new PageMaterial(
                generationId,
                0xFFF5F2EA,
                0xFFE9E3D8,
                0xFF5B554C,
                0xFFF5F2EA,
                leafRole,
                display,
                clip,
                null,
                1);
        return new PageImage<>(
                generationId,
                "owned-page-" + ordinal,
                ordinal,
                100,
                200,
                display,
                "content",
                material);
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
