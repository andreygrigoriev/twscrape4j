package io.github.twscrape4j.graphql;

import io.github.twscrape4j.accounts.AccountPool;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Spliterator;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Lazy {@link Spliterator} over a cursor-paginated Twitter API endpoint.
 * Fetches the next page on demand; parks the virtual thread via {@link AccountPool} during each call.
 *
 * @param <T> the item type (e.g. {@code Tweet}, {@code User}, {@code JsonNode})
 */
public class PaginatingSpliterator<T> implements Spliterator<T> {

    private final AccountPool pool;
    private final BiFunction<String, AccountPool, Page<T>> pageFetcher;
    private final Deque<T> buffer = new ArrayDeque<>();
    private String cursor = null;
    private boolean exhausted = false;

    public PaginatingSpliterator(AccountPool pool, BiFunction<String, AccountPool, Page<T>> pageFetcher) {
        this.pool = pool;
        this.pageFetcher = pageFetcher;
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
        if (!buffer.isEmpty()) {
            action.accept(buffer.poll());
            return true;
        }
        if (exhausted) return false;

        Page<T> page = pageFetcher.apply(cursor, pool);
        buffer.addAll(page.items());

        if (!page.hasMore()) {
            exhausted = true;
        } else {
            cursor = page.nextCursor();
        }

        if (buffer.isEmpty()) return false;
        action.accept(buffer.poll());
        return true;
    }

    @Override
    public Spliterator<T> trySplit() {
        return null; // no parallel split — ordered, sequential only
    }

    @Override
    public long estimateSize() {
        return Long.MAX_VALUE;
    }

    @Override
    public int characteristics() {
        return ORDERED | NONNULL;
    }
}
