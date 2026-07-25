package karacken.curl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Previous, current, and next two-leaf spreads for one landscape interaction window. */
public final class LandscapePageDeck<T> implements PageDeck<T> {
    private final long generationId;
    private final PageImage<T> previousLeft;
    private final PageImage<T> previousRight;
    private final PageImage<T> currentLeft;
    private final PageImage<T> currentRight;
    private final PageImage<T> nextLeft;
    private final PageImage<T> nextRight;
    private final boolean previousAvailable;
    private final boolean nextAvailable;
    private final PageImage<T> previousSettlementPage;
    private final PageImage<T> currentSettlementPage;
    private final PageImage<T> nextSettlementPage;
    private final List<PageImage<T>> pages;

    public LandscapePageDeck(
            PageImage<T> previousLeft,
            PageImage<T> previousRight,
            PageImage<T> currentLeft,
            PageImage<T> currentRight,
            PageImage<T> nextLeft,
            PageImage<T> nextRight) {
        this(
                previousLeft,
                previousRight,
                currentLeft,
                currentRight,
                nextLeft,
                nextRight,
                true,
                true);
    }

    public LandscapePageDeck(
            PageImage<T> previousLeft,
            PageImage<T> previousRight,
            PageImage<T> currentLeft,
            PageImage<T> currentRight,
            PageImage<T> nextLeft,
            PageImage<T> nextRight,
            boolean previousAvailable,
            boolean nextAvailable) {
        this(
                previousLeft,
                previousRight,
                currentLeft,
                currentRight,
                nextLeft,
                nextRight,
                previousAvailable,
                nextAvailable,
                previousLeft,
                currentLeft,
                nextLeft);
    }

    public LandscapePageDeck(
            PageImage<T> previousLeft,
            PageImage<T> previousRight,
            PageImage<T> currentLeft,
            PageImage<T> currentRight,
            PageImage<T> nextLeft,
            PageImage<T> nextRight,
            boolean previousAvailable,
            boolean nextAvailable,
            PageImage<T> previousSettlementPage,
            PageImage<T> currentSettlementPage,
            PageImage<T> nextSettlementPage) {
        this.previousLeft = Objects.requireNonNull(previousLeft, "previousLeft");
        this.previousRight = Objects.requireNonNull(previousRight, "previousRight");
        this.currentLeft = Objects.requireNonNull(currentLeft, "currentLeft");
        this.currentRight = Objects.requireNonNull(currentRight, "currentRight");
        this.nextLeft = Objects.requireNonNull(nextLeft, "nextLeft");
        this.nextRight = Objects.requireNonNull(nextRight, "nextRight");
        this.previousSettlementPage = Objects.requireNonNull(
                previousSettlementPage, "previousSettlementPage");
        this.currentSettlementPage = Objects.requireNonNull(
                currentSettlementPage, "currentSettlementPage");
        this.nextSettlementPage = Objects.requireNonNull(
                nextSettlementPage, "nextSettlementPage");
        this.previousAvailable = previousAvailable;
        this.nextAvailable = nextAvailable;
        generationId = this.currentSettlementPage.getGenerationId();
        requireGeneration(this.previousLeft);
        requireGeneration(this.previousRight);
        requireGeneration(this.currentLeft);
        requireGeneration(this.currentRight);
        requireGeneration(this.nextLeft);
        requireGeneration(this.nextRight);
        requireDisplayPlacement(
                this.currentLeft,
                this.previousLeft,
                this.nextLeft,
                "left");
        requireDisplayPlacement(
                this.currentRight,
                this.previousRight,
                this.nextRight,
                "right");
        requirePhysicalLeafOrder();
        pages = Collections.unmodifiableList(Arrays.asList(
                this.previousLeft,
                this.previousRight,
                this.currentLeft,
                this.currentRight,
                this.nextLeft,
                this.nextRight));
        requireDeckPage(this.previousSettlementPage, "previousSettlementPage");
        requireDeckPage(this.currentSettlementPage, "currentSettlementPage");
        requireDeckPage(this.nextSettlementPage, "nextSettlementPage");
    }

    @Override
    public long getGenerationId() {
        return generationId;
    }

    @Override
    public PageDeckMode getMode() {
        return PageDeckMode.LANDSCAPE;
    }

    @Override
    public List<PageImage<T>> getPages() {
        return pages;
    }

    @Override
    public boolean canTurn(PageChange pageChange) {
        Objects.requireNonNull(pageChange, "pageChange");
        if (pageChange == PageChange.PREVIOUS) {
            return previousAvailable;
        }
        if (pageChange == PageChange.NEXT) {
            return nextAvailable;
        }
        return false;
    }

    @Override
    public PageImage<T> getSettlementPage(PageChange pageChange) {
        Objects.requireNonNull(pageChange, "pageChange");
        if (pageChange == PageChange.PREVIOUS) {
            return previousSettlementPage;
        }
        if (pageChange == PageChange.NEXT) {
            return nextSettlementPage;
        }
        return currentSettlementPage;
    }

    public PageImage<T> getPreviousLeft() {
        return previousLeft;
    }

    public PageImage<T> getPreviousRight() {
        return previousRight;
    }

    public PageImage<T> getCurrentLeft() {
        return currentLeft;
    }

    public PageImage<T> getCurrentRight() {
        return currentRight;
    }

    public PageImage<T> getNextLeft() {
        return nextLeft;
    }

    public PageImage<T> getNextRight() {
        return nextRight;
    }

    private void requireGeneration(PageImage<T> page) {
        if (page.getGenerationId() != generationId) {
            throw new IllegalArgumentException("All landscape leaves must share one generation");
        }
    }

    private void requireDisplayPlacement(
            PageImage<T> reference,
            PageImage<T> first,
            PageImage<T> second,
            String physicalSlot) {
        PageDisplayRect placement = reference.getDisplayRect();
        if (!Objects.equals(placement, first.getDisplayRect())
                || !Objects.equals(placement, second.getDisplayRect())) {
            throw new IllegalArgumentException(
                    "All " + physicalSlot + " leaves must share one physical display rectangle");
        }
    }

    private void requirePhysicalLeafOrder() {
        PageDisplayRect leftPlacement = currentLeft.getDisplayRect();
        PageDisplayRect rightPlacement = currentRight.getDisplayRect();
        if ((leftPlacement == null) != (rightPlacement == null)) {
            throw new IllegalArgumentException(
                    "Landscape display placement must describe both physical leaves");
        }
        if (leftPlacement != null
                && leftPlacement.getRightPx() > rightPlacement.getLeftPx()) {
            throw new IllegalArgumentException(
                    "Physical landscape leaf display rectangles must not overlap");
        }
    }

    private void requireDeckPage(PageImage<T> page, String name) {
        if (!pages.contains(page)) {
            throw new IllegalArgumentException(name + " must be one of the physical deck pages");
        }
    }
}
