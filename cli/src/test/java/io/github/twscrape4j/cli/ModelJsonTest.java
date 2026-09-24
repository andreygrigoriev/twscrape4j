package io.github.twscrape4j.cli;

import io.github.twscrape4j.models.Trend;
import io.github.twscrape4j.models.Tweet;
import io.github.twscrape4j.models.TweetStats;
import io.github.twscrape4j.models.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelJsonTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final User JACK = new User(42L, "jack", "Jack", "just setting up", 1L, 2L, false,
            Instant.parse("2006-03-21T20:50:14Z"), MAPPER.createObjectNode().put("secret", "raw"));

    @Test
    void fullTweetWithNestedAuthorAndStats() {
        var tweet = new Tweet(123L, "hello", JACK, Instant.parse("2026-09-24T10:00:00Z"),
                new TweetStats(1, 0, 0, 0, 10), "en", 123L, MAPPER.createObjectNode().put("secret", "raw"));

        String json = MAPPER.writeValueAsString(ModelJson.tweet(tweet));

        assertEquals("{\"id\":\"123\",\"text\":\"hello\",\"createdAt\":\"2026-09-24T10:00:00Z\",\"lang\":\"en\","
                + "\"conversationId\":\"123\","
                + "\"author\":{\"id\":\"42\",\"username\":\"jack\",\"displayName\":\"Jack\",\"bio\":\"just setting up\","
                + "\"followers\":1,\"following\":2,\"verified\":false,\"createdAt\":\"2006-03-21T20:50:14Z\","
                + "\"url\":\"https://x.com/jack\"},"
                + "\"stats\":{\"likes\":1,\"replies\":0,\"retweets\":0,\"quotes\":0,\"views\":10},"
                + "\"url\":\"https://x.com/jack/status/123\"}", json);
    }

    @Test
    void nullableFieldsAreOmitted() {
        var tweet = new Tweet(7L, null, null, null, null, null, 0L, null);

        JsonNode node = ModelJson.tweet(tweet);

        assertEquals("{\"id\":\"7\"}", MAPPER.writeValueAsString(node));
    }

    @Test
    void rawIsNeverIncluded() {
        assertFalse(ModelJson.user(JACK).has("raw"));
        assertFalse(ModelJson.trend(new Trend("x", 1, MAPPER.createObjectNode())).has("raw"));
    }

    @Test
    void largeIdRoundTripsExactlyAsString() {
        long big = 1_838_000_000_000_000_123L; // > 2^53
        var tweet = new Tweet(big, "t", JACK, null, null, null, big, null);

        JsonNode parsed = MAPPER.readTree(MAPPER.writeValueAsString(ModelJson.tweet(tweet)));

        assertTrue(parsed.get("id").isString());
        assertEquals(Long.toString(big), parsed.get("id").asString());
        assertEquals(Long.toString(big), parsed.get("conversationId").asString());
        assertEquals("https://x.com/jack/status/" + big, parsed.get("url").asString());
    }

    @Test
    void userWithoutUsernameHasNoUrl() {
        var user = new User(5L, null, "Anon", null, 0, 0, true, null, null);

        JsonNode node = ModelJson.user(user);

        assertFalse(node.has("url"));
        assertFalse(node.has("username"));
        assertEquals("{\"id\":\"5\",\"displayName\":\"Anon\",\"followers\":0,\"following\":0,\"verified\":true}",
                MAPPER.writeValueAsString(node));
    }

    @Test
    void tweetWhoseAuthorHasNoUsernameHasNoUrl() {
        var anon = new User(5L, null, null, null, 0, 0, false, null, null);
        assertFalse(ModelJson.tweet(new Tweet(1L, "t", anon, null, null, null, 0L, null)).has("url"));
    }

    @Test
    void trend() {
        assertEquals("{\"name\":\"#java\",\"tweetCount\":1000}",
                MAPPER.writeValueAsString(ModelJson.trend(new Trend("#java", 1000, null))));
    }
}
