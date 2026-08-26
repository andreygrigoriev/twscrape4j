package io.github.twscrape4j.models;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

public record User(
        long id,
        String username,
        String displayName,
        String bio,
        long followersCount,
        long followingCount,
        boolean verified,
        Instant createdAt,
        JsonNode raw
) {}
