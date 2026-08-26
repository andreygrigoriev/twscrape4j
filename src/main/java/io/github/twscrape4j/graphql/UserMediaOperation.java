package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Tweet;

import java.util.HashMap;
import java.util.Map;

public class UserMediaOperation {

    private static final String OPERATION_ID = "oMVVrI5kt3kOpyHHTTKf5Q";
    private static final String OPERATION_NAME = "UserMedia";

    private final GraphQLClient graphQLClient;

    public UserMediaOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public Page<Tweet> fetch(long userId, String cursor, AccountPool pool) {
        var raw = fetchRaw(userId, cursor, pool);
        return new Page<>(raw.items().stream().map(ModelMapper::toTweet).toList(), raw.nextCursor());
    }

    public Page<JsonNode> fetchRaw(long userId, String cursor, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var vars = new HashMap<String, Object>();
            vars.put("userId", String.valueOf(userId));
            vars.put("count", 20);
            vars.put("includePromotedContent", false);
            vars.put("withClientEventToken", false);
            vars.put("withBirdwatchNotes", false);
            vars.put("withVoice", true);
            vars.put("withV2Timeline", true);
            if (cursor != null) vars.put("cursor", cursor);

            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME, vars, Map.of());
            pool.updateRateLimit(handle.account().username(), OPERATION_NAME,
                    response.rateLimitRemaining(), response.rateLimitResetAt());
            JsonNode instructions = response.body().path("data").path("user").path("result")
                    .path("timeline_v2").path("timeline").path("instructions");
            var data = ModelMapper.extractTimeline(instructions);
            return new Page<>(data.tweetResults(), data.nextCursor());
        } finally {
            pool.release(handle);
        }
    }
}
