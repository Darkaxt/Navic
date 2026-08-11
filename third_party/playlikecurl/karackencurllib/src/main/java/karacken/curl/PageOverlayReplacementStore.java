package karacken.curl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Owns one atomic renderer resource set and disposes the set it replaces. */
final class PageOverlayReplacementStore<K, V> {
    private final Consumer<V> disposer;
    private Map<K, V> values = new LinkedHashMap<>();

    PageOverlayReplacementStore(Consumer<V> disposer) {
        this.disposer = Objects.requireNonNull(disposer, "disposer");
    }

    V get(K key) {
        return values.get(key);
    }

    int size() {
        return values.size();
    }

    void forEach(Consumer<V> action) {
        values.values().forEach(action);
    }

    void replace(Map<K, V> replacements) {
        Objects.requireNonNull(replacements, "replacements");
        Map<K, V> previous = values;
        values = new LinkedHashMap<>(replacements);
        for (V value : previous.values()) {
            disposer.accept(value);
        }
    }

    void clear() {
        replace(new LinkedHashMap<>());
    }
}
