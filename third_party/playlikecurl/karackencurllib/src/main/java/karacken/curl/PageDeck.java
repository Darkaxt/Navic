package karacken.curl;

import java.util.List;

/** Exact set of physical pages required for one bidirectional interaction window. */
public interface PageDeck<T> {
    long getGenerationId();

    PageDeckMode getMode();

    List<PageImage<T>> getPages();

    /** Returns whether the logical direction is available from the current page. */
    boolean canTurn(PageChange pageChange);

    /** Returns the canonical logical source or first destination page for settlement. */
    PageImage<T> getSettlementPage(PageChange pageChange);
}
