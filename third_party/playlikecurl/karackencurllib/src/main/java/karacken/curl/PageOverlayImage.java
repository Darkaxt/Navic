package karacken.curl;

import java.util.Objects;

/** One renderer-owned transparent overlay targeting an exact page ordinal. */
public final class PageOverlayImage<T> {
    private final int ordinal;
    private final T content;

    public PageOverlayImage(int ordinal, T content) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        this.ordinal = ordinal;
        this.content = Objects.requireNonNull(content, "content");
    }

    public int getOrdinal() {
        return ordinal;
    }

    public T getContent() {
        return content;
    }
}
