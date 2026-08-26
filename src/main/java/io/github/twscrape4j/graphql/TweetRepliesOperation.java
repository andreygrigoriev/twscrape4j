package io.github.twscrape4j.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Tweet;

import java.util.HashMap;
import java.util.Map;

public class TweetRepliesOperation {

    private static final String OPERATION_ID = "0hWvDhmW8YQ-S_ib3azIrw";
    private static final String OPERATION_NAME = "TweetDetail";

    private final GraphQLClient graphQLClient;

    public TweetRepliesOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public Page<Tweet> fetch(long tweetId, String cursor, AccountPool pool) {
        var raw = fetchRaw(tweetId, cursor, pool);
        var tweets = raw.items().stream().map(ModelMapper::toTweet).toList();
        return new Page<>(tweets, raw.nextCursor());
    }

    public Page<JsonNode> fetchRaw(long tweetId, String cursor, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var vars = new HashMap<String, Object>();
            vars.put("focalTweetId", String.valueOf(tweetId));
            vars.put("count", 20);
            vars.put("with_rux_injections", false);
            vars.put("includePromotedContent", true);
            vars.put("withCommunity", true);
            vars.put("withVoice", true);
            if (cursor != null) vars.put("cursor", cursor);

            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME, vars, Map.of());
            pool.updateRateLimit(handle.account().username(), OPERATION_NAME,
                    response.rateLimitRemaining(), response.rateLimitResetAt());

            JsonNode instructions = response.body()
                    .path("data").path("threaded_conversation_with_injections_v2").path("instructions");
            var data = ModelMapper.extractTimeline(instructions);
            return new Page<>(data.tweetResults(), data.nextCursor());
        } finally {
            pool.release(handle);
        }
    }
}
