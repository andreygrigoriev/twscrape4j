package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.api.TrendCategory;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.models.Trend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrendsOperation {

    private static final String OPERATION_ID = "ee4dBLWL8a8qg6n19m1htQ";
    private static final String OPERATION_NAME = "GenericTimelineById";

    private final GraphQLClient graphQLClient;

    public TrendsOperation(GraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public List<Trend> fetch(TrendCategory category, AccountPool pool) {
        var handle = pool.acquire(OPERATION_NAME);
        try {
            var response = graphQLClient.get(handle, OPERATION_ID, OPERATION_NAME,
                    variables(category), Map.of());
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
                    variables(category), Map.of());
            pool.updateRateLimit(handle.account().username(), OPERATION_NAME,
                    response.rateLimitRemaining(), response.rateLimitResetAt());
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
        collectTrends(body, trends);
        return trends;
    }

    /** Trend items sit at varying depths of the timeline; collect every TimelineTrend object. */
    private void collectTrends(JsonNode node, List<JsonNode> out) {
        if (node.isObject() && "TimelineTrend".equals(node.path("__typename").asText(""))) {
            out.add(node);
            return;
        }
        if (node.isContainer()) {
            for (JsonNode child : node) collectTrends(child, out);
        }
    }

    private Map<String, Object> variables(TrendCategory category) {
        return Map.of(
                "timelineId", timelineId(category),
                "count", 20,
                "withQuickPromoteEligibilityTweetFields", true);
    }

    private String timelineId(TrendCategory category) {
        return switch (category) {
            case TRENDING -> "VGltZWxpbmU6DAC2CwABAAAACHRyZW5kaW5nAAA";
            case NEWS -> "VGltZWxpbmU6DAC2CwABAAAABG5ld3MAAA";
            case SPORT -> "VGltZWxpbmU6DAC2CwABAAAABnNwb3J0cwAA";
            case ENTERTAINMENT -> "VGltZWxpbmU6DAC2CwABAAAADWVudGVydGFpbm1lbnQAAA";
        };
    }
}
