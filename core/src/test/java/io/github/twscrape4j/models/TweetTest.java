package io.github.twscrape4j.models;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TweetTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Test
    void userRecordHoldsAllFields() {
        JsonNode raw = MAPPER.createObjectNode();
        var user = new User(123L, "testuser", "Test User", "bio text",
                500L, 100L, false, Instant.parse("2020-01-01T00:00:00Z"), raw);

        assertEquals(123L, user.id());
        assertEquals("testuser", user.username());
        assertEquals("Test User", user.displayName());
        assertEquals("bio text", user.bio());
        assertEquals(500L, user.followersCount());
        assertEquals(100L, user.followingCount());
        assertFalse(user.verified());
        assertEquals(Instant.parse("2020-01-01T00:00:00Z"), user.createdAt());
        assertSame(raw, user.raw());
    }

    @Test
    void tweetRecordHoldsAllFields() {
        JsonNode raw = MAPPER.createObjectNode();
        var stats = new TweetStats(10L, 2L, 3L, 1L, 500L);
        var author = new User(1L, "alice", "Alice", "", 0L, 0L, false,
                Instant.EPOCH, MAPPER.createObjectNode());
        var tweet = new Tweet(42L, "Hello world", author,
                Instant.parse("2024-06-01T12:00:00Z"), stats, "en", 42L, raw);

        assertEquals(42L, tweet.id());
        assertEquals("Hello world", tweet.text());
        assertSame(author, tweet.author());
        assertEquals(Instant.parse("2024-06-01T12:00:00Z"), tweet.createdAt());
        assertEquals(10L, tweet.stats().likeCount());
        assertEquals("en", tweet.lang());
        assertEquals(42L, tweet.conversationId());
        assertSame(raw, tweet.raw());
    }

    @Test
    void tweetSerializesAndDeserializesViaJackson() throws Exception {
        JsonNode raw = MAPPER.readTree("{\"extra\":\"data\"}");
        var stats = new TweetStats(5L, 1L, 2L, 0L, 100L);
        var author = new User(7L, "bob", "Bob", "desc", 10L, 5L, true,
                Instant.parse("2019-03-15T08:00:00Z"), MAPPER.createObjectNode());
        var tweet = new Tweet(99L, "Test tweet", author,
                Instant.parse("2025-01-01T00:00:00Z"), stats, "fr", 99L, raw);

        String json = MAPPER.writeValueAsString(tweet);
        Tweet deserialized = MAPPER.readValue(json, Tweet.class);

        assertEquals(tweet.id(), deserialized.id());
        assertEquals(tweet.text(), deserialized.text());
        assertEquals(tweet.author().username(), deserialized.author().username());
        assertEquals(tweet.createdAt(), deserialized.createdAt());
        assertEquals(tweet.stats().likeCount(), deserialized.stats().likeCount());
        assertEquals(tweet.lang(), deserialized.lang());
        assertEquals("data", deserialized.raw().get("extra").asText());
    }
}
