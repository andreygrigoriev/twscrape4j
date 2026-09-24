package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TweetRetweetersOperation {

    private static final String OPERATION_ID = "ROjiuYueotTnWoI8m2YaiQ";
    private static final String OPERATION_NAME = "Retweeters";

    private final GraphQLClient graphQLClient;

    public TweetRetweetersOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public Page<User> fetch(long tweetId, String cursor, AccountPool pool) {
        var raw = fetchRaw(tweetId, cursor, pool);
        var users = raw.items().stream().map(ModelMapper::toUser).toList();
        return new Page<>(users, raw.nextCursor());
    }

    public Page<JsonNode> fetchRaw(long tweetId, String cursor, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var vars = new HashMap<String, Object>();
            vars.put("tweetId", String.valueOf(tweetId));
            vars.put("count", 20);
            if (cursor != null) vars.put("cursor", cursor);

            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME, vars, Map.of());
            pool.updateRateLimit(handle.account().username(), OPERATION_NAME,
                    response.rateLimitRemaining(), response.rateLimitResetAt());
            return parseUsers(response.body());
        } finally {
            pool.release(handle);
        }
    }

    private Page<JsonNode> parseUsers(JsonNode body) {
        var users = new ArrayList<JsonNode>();
        String cursor = null;
        JsonNode timeline = body.path("data").path("retweeters_timeline").path("timeline");
        for (JsonNode instruction : timeline.path("instructions")) {
            for (JsonNode entry : instruction.path("entries")) {
                JsonNode content = entry.path("content");
                String entryType = content.path("entryType").asText("");
                if ("TimelineTimelineItem".equals(entryType)) {
                    JsonNode userResult = content.path("itemContent").path("user_results").path("result");
                    if (!userResult.isMissingNode()) users.add(userResult);
                } else if ("TimelineTimelineCursor".equals(entryType)
                        && "Bottom".equals(content.path("cursorType").asText(""))) {
                    cursor = content.path("value").asText(null);
                }
            }
        }
        return new Page<>(users, cursor);
    }
}
