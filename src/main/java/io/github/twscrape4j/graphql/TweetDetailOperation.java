package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Tweet;

import java.util.Map;
import java.util.Optional;

public class TweetDetailOperation {

    private static final String OPERATION_ID = "XMOz5h24KAZ86qKffKTLdQ";
    private static final String OPERATION_NAME = "TweetDetail";

    private final GraphQLClient graphQLClient;

    public TweetDetailOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public Optional<Tweet> fetch(long tweetId, AccountPool pool) {
        return fetchRaw(tweetId, pool).map(ModelMapper::toTweet);
    }

    public Optional<JsonNode> fetchRaw(long tweetId, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME,
                    Map.of("focalTweetId", String.valueOf(tweetId),
                            "referrer", "tweet", "count", 20,
                            "with_rux_injections", false,
                            "includePromotedContent", true,
                            "withCommunity", true,
                            "withQuickPromoteEligibilityTweetFields", true,
                            "withBirdwatchNotes", true,
                            "withVoice", true),
                    Map.of());
            pool.updateRateLimit(handle.account().username(), OPERATION_NAME,
                    response.rateLimitRemaining(), response.rateLimitResetAt());
            return extractTweet(response.body(), tweetId);
        } finally {
            pool.release(handle);
        }
    }

    private Optional<JsonNode> extractTweet(JsonNode body, long tweetId) {
        JsonNode instructions = body.path("data").path("threaded_conversation_with_injections_v2")
                .path("instructions");
        var data = ModelMapper.extractTimeline(instructions);
        return data.tweetResults().stream()
                .filter(n -> n.path("rest_id").asLong() == tweetId)
                .findFirst();
    }
}
