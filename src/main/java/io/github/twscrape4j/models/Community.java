package io.github.twscrape4j.models;

import com.fasterxml.jackson.databind.JsonNode;

public record Community(
        long id,
        String name,
        String description,
        long memberCount,
        JsonNode raw
) {}
