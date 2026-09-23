package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Tweet;

import java.util.HashMap;
import java.util.Map;

public class ListTimelineOperation {

    private static final String OPERATION_ID = "1LE3u14FJjPZUHKFGzos2g";
    private static final String OPERATION_NAME = "ListLatestTweetsTimeline";

    private final GraphQLClient graphQLClient;

    public ListTimelineOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public Page<Tweet> fetch(long listId, String cursor, AccountPool pool) {
        var raw = fetchRaw(listId, cursor, pool);
        return new Page<>(raw.items().stream().map(ModelMapper::toTweet).toList(), raw.nextCursor());
    }

    public Page<JsonNode> fetchRaw(long listId, String cursor, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var vars = new HashMap<String, Object>();
            vars.put("listId", String.valueOf(listId));
            vars.put("count", 20);
            if (cursor != null) vars.put("cursor", cursor);

            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME, vars, Map.of());
            pool.updateRateLimit(handle.account().username(), OPERATION_NAME,
                    response.rateLimitRemaining(), response.rateLimitResetAt());
            JsonNode instructions = response.body().path("data").path("list")
                    .path("tweets_timeline").path("timeline").path("instructions");
            var data = ModelMapper.extractTimeline(instructions);
            return new Page<>(data.tweetResults(), data.nextCursor());
        } finally {
            pool.release(handle);
        }
    }
}
