package io.github.twscrape4j.graphql;

import java.util.List;

/** A single page of results with a cursor for the next page. */
public record Page<T>(List<T> items, String nextCursor) {

    public boolean hasMore() {
        return nextCursor != null && !nextCursor.isBlank();
    }
}
