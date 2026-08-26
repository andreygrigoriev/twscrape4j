package io.github.twscrape4j.models;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record Tweet(
        long id,
        String text,
        User author,
        Instant createdAt,
        TweetStats stats,
        String lang,
        long conversationId,
        JsonNode raw
) {}
