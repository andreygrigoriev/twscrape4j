package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.User;

import java.util.Map;
import java.util.Optional;

public class UserByScreenNameOperation {

    private static final String OPERATION_ID = "qW5u-DAuXpMEG0zA1F7UGQ";
    private static final String OPERATION_NAME = "UserByScreenName";

    private final GraphQLClient graphQLClient;

    public UserByScreenNameOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public Optional<User> fetch(String screenName, AccountPool pool) {
        return fetchRaw(screenName, pool).map(ModelMapper::toUser);
    }

    public Optional<JsonNode> fetchRaw(String screenName, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME,
                    Map.of("screen_name", screenName, "withSafetyModeUserFields", true),
                    Map.of());
            pool.updateRateLimit(handle.account().username(), OPERATION_NAME,
                    response.rateLimitRemaining(), response.rateLimitResetAt());
            JsonNode result = response.body().path("data").path("user").path("result");
            return result.isMissingNode() ? Optional.empty() : Optional.of(result);
        } finally {
            pool.release(handle);
        }
    }
}
