package io.github.twscrape4j.graphql;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.github.twscrape4j.models.Tweet;
import io.github.twscrape4j.models.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ModelMapper covering edge-case and fallback branches.
 */
class ModelMapperTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Test
    void toTweetSetsNullAuthorWhenUserResultsMissing() throws Exception {
        // result node has no "core.user_results" — author should be null
        String json = """
                {
                  "rest_id": "42",
                  "legacy": {
                    "full_text": "no author tweet",
                    "created_at": "Mon Jan 01 12:00:00 +0000 2024",
                    "lang": "en",
                    "conversation_id_str": "42",
                    "favorite_count": 0,
                    "reply_count": 0,
                    "retweet_count": 0,
                    "quote_count": 0
                  }
                }
                """;
        Tweet tweet = ModelMapper.toTweet(MAPPER.readTree(json));

        assertEquals(42L, tweet.id());
        assertEquals("no author tweet", tweet.text());
        assertNull(tweet.author());
    }

    @Test
    void toTweetSetsNullAuthorWhenCoreNodeMissing() throws Exception {
        // result node has "core" but no "user_results" path
        String json = """
                {
                  "rest_id": "7",
                  "legacy": {
                    "full_text": "core but no user",
                    "created_at": "Mon Jan 01 12:00:00 +0000 2024",
                    "lang": "en",
                    "conversation_id_str": "7",
                    "favorite_count": 0,
                    "reply_count": 0,
                    "retweet_count": 0,
                    "quote_count": 0
                  },
                  "core": {}
                }
                """;
        Tweet tweet = ModelMapper.toTweet(MAPPER.readTree(json));
        assertNull(tweet.author());
    }

    @Test
    void parseDateFallbackReturnsEpochOnMalformedInput() throws Exception {
        // Provide a created_at that is not in Twitter's date format
        String json = """
                {
                  "rest_id": "99",
                  "legacy": {
                    "full_text": "bad date",
                    "created_at": "not-a-valid-date",
                    "lang": "en",
                    "conversation_id_str": "99",
                    "favorite_count": 0,
                    "reply_count": 0,
                    "retweet_count": 0,
                    "quote_count": 0
                  }
                }
                """;
        Tweet tweet = ModelMapper.toTweet(MAPPER.readTree(json));
        assertEquals(Instant.EPOCH, tweet.createdAt());
    }

    @Test
    void parseDateFallbackReturnsEpochOnBlankInput() throws Exception {
        String json = """
                {
                  "rest_id": "1",
                  "legacy": {
                    "full_text": "blank date",
                    "created_at": "",
                    "lang": "en",
                    "conversation_id_str": "1",
                    "favorite_count": 0,
                    "reply_count": 0,
                    "retweet_count": 0,
                    "quote_count": 0
                  }
                }
                """;
        Tweet tweet = ModelMapper.toTweet(MAPPER.readTree(json));
        assertEquals(Instant.EPOCH, tweet.createdAt());
    }

    @Test
    void toUserParseDateFallbackOnMalformedCreatedAt() throws Exception {
        String json = """
                {
                  "rest_id": "5",
                  "legacy": {
                    "screen_name": "u",
                    "name": "U",
                    "description": "",
                    "followers_count": 0,
                    "friends_count": 0,
                    "verified": false,
                    "created_at": "INVALID"
                  }
                }
                """;
        User user = ModelMapper.toUser(MAPPER.readTree(json));
        assertEquals(Instant.EPOCH, user.createdAt());
    }
}
