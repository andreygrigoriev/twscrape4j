package io.github.twscrape4j.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.User;

import java.util.Map;
import java.util.Optional;

public class UserByIdOperation {

    private static final String OPERATION_ID = "GazOglcBvgLigl3ywt6b3Q";
    private static final String OPERATION_NAME = "UserById";

    private final GraphQLClient graphQLClient;

    public UserByIdOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public Optional<User> fetch(long userId, AccountPool pool) {
        return fetchRaw(userId, pool).map(ModelMapper::toUser);
    }

    public Optional<JsonNode> fetchRaw(long userId, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME,
                    Map.of("userId", String.valueOf(userId), "withSafetyModeUserFields", true),
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
