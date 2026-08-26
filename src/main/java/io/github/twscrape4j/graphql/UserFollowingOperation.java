package io.github.twscrape4j.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class UserFollowingOperation {

    private static final String OPERATION_ID = "eWTmcJY3EMh-dxITvBRRgA";
    private static final String OPERATION_NAME = "Following";

    private final GraphQLClient graphQLClient;

    public UserFollowingOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public Page<User> fetch(long userId, String cursor, AccountPool pool) {
        var raw = fetchRaw(userId, cursor, pool);
        return new Page<>(raw.items().stream().map(ModelMapper::toUser).toList(), raw.nextCursor());
    }

    public Page<JsonNode> fetchRaw(long userId, String cursor, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var vars = new HashMap<String, Object>();
            vars.put("userId", String.valueOf(userId));
            vars.put("count", 20);
            vars.put("includePromotedContent", false);
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
        for (JsonNode instruction : body.path("data").path("following_timeline")
                .path("timeline").path("instructions")) {
            for (JsonNode entry : instruction.path("entries")) {
                JsonNode content = entry.path("content");
                if ("TimelineTimelineItem".equals(content.path("entryType").asText(""))) {
                    JsonNode result = content.path("itemContent").path("user_results").path("result");
                    if (!result.isMissingNode()) users.add(result);
                } else if ("TimelineTimelineCursor".equals(content.path("entryType").asText(""))
                        && "Bottom".equals(content.path("cursorType").asText(""))) {
                    cursor = content.path("value").asText(null);
                }
            }
        }
        return new Page<>(users, cursor);
    }
}
