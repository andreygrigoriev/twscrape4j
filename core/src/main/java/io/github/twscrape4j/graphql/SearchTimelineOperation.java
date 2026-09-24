package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.api.SearchMode;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Tweet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchTimelineOperation {

    // Update from twscrape source when Twitter rotates these
    private static final String OPERATION_ID = "hyPfJYJ_XAtDYoslQc-Rgg";
    private static final String OPERATION_NAME = "SearchTimeline";

    private final GraphQLClient graphQLClient;

    public SearchTimelineOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public Page<Tweet> fetch(String rawQuery, SearchMode mode, String cursor, AccountPool pool) {
        var result = execute(rawQuery, mode, cursor, pool);
        var data = ModelMapper.extractTimeline(instructions(result.body()));
        var tweets = data.tweetResults().stream()
                .filter(n -> !n.isMissingNode())
                .map(ModelMapper::toTweet)
                .toList();
        return new Page<>(tweets, data.nextCursor());
    }

    public Page<JsonNode> fetchRaw(String rawQuery, SearchMode mode, String cursor, AccountPool pool) {
        var result = execute(rawQuery, mode, cursor, pool);
        var data = ModelMapper.extractTimeline(instructions(result.body()));
        return new Page<>(List.copyOf(data.tweetResults()), data.nextCursor());
    }

    private GraphQLClient.GraphQLResponse execute(String rawQuery, SearchMode mode,
                                                   String cursor, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            Map<String, Object> variables = buildVariables(rawQuery, mode, cursor);
            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME, variables, Map.of());
            pool.updateRateLimit(handle.account().username(), OPERATION_NAME,
                    response.rateLimitRemaining(), response.rateLimitResetAt());
            return response;
        } finally {
            pool.release(handle);
        }
    }

    private Map<String, Object> buildVariables(String rawQuery, SearchMode mode, String cursor) {
        var vars = new HashMap<String, Object>();
        vars.put("rawQuery", rawQuery);
        vars.put("count", 20);
        vars.put("querySource", "typed_query");
        vars.put("product", switch (mode) {
            case TOP -> "Top";
            case LATEST -> "Latest";
            case MEDIA -> "Photos";
        });
        if (cursor != null) vars.put("cursor", cursor);
        return vars;
    }

    private JsonNode instructions(JsonNode body) {
        return body.path("data")
                .path("search_by_raw_query")
                .path("search_timeline")
                .path("timeline")
                .path("instructions");
    }
}
