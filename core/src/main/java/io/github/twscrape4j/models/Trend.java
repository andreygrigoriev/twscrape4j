package io.github.twscrape4j.models;

import tools.jackson.databind.JsonNode;

public record Trend(
        String name,
        long tweetCount,
        JsonNode raw
) {}
