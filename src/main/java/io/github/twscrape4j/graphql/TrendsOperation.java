package io.github.twscrape4j.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.api.TrendCategory;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Trend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrendsOperation {

    private static final String OPERATION_ID = "tQ_TNzs-EKuRvpQVpHZqQQ";
    private static final String OPERATION_NAME = "ExplorePage";

    private final GraphQLClient graphQLClient;

    public TrendsOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public List<Trend> fetch(TrendCategory category, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME,
                    Map.of("categoryId", categoryId(category)), Map.of());
            pool.updateRateLimit(handle.account().username(), OPERATION_NAME,
                    response.rateLimitRemaining(), response.rateLimitResetAt());
            return parseTrends(response.body());
        } finally {
            pool.release(handle);
        }
    }

    public List<JsonNode> fetchRaw(TrendCategory category, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME,
                    Map.of("categoryId", categoryId(category)), Map.of());
            return parseTrendNodes(response.body());
        } finally {
            pool.release(handle);
        }
    }

    private List<Trend> parseTrends(JsonNode body) {
        return parseTrendNodes(body).stream().map(ModelMapper::toTrend).toList();
    }

    private List<JsonNode> parseTrendNodes(JsonNode body) {
        var trends = new ArrayList<JsonNode>();
        JsonNode entries = body.path("data").path("explore_page").path("body")
                .path("initialTimeline").path("timeline").path("instructions");
        for (JsonNode instruction : entries) {
            for (JsonNode entry : instruction.path("entries")) {
                JsonNode item = entry.path("content").path("content").path("timelineTrend");
                if (!item.isMissingNode()) trends.add(item);
            }
        }
        return trends;
    }

    private String categoryId(TrendCategory category) {
        return switch (category) {
            case NEWS -> "1";
            case SPORT -> "2";
            case ENTERTAINMENT -> "3";
            case TRENDING -> "4";
        };
    }
}
