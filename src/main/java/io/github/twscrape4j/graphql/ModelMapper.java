package io.github.twscrape4j.graphql;

import tools.jackson.databind.JsonNode;
import io.github.twscrape4j.models.Tweet;
import io.github.twscrape4j.models.TweetStats;
import io.github.twscrape4j.models.Trend;
import io.github.twscrape4j.models.User;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps raw Twitter GraphQL response nodes to model records.
 * All methods return sensible defaults on missing fields rather than throwing.
 */
public final class ModelMapper {

    private static final DateTimeFormatter TWITTER_DATE =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);

    private ModelMapper() {}

    public static User toUser(JsonNode result) {
        JsonNode legacy = result.path("legacy");
        return new User(
                result.path("rest_id").asLong(),
                legacy.path("screen_name").asText(""),
                legacy.path("name").asText(""),
                legacy.path("description").asText(""),
                legacy.path("followers_count").asLong(),
                legacy.path("friends_count").asLong(),
                legacy.path("verified").asBoolean() || result.path("is_blue_verified").asBoolean(),
                parseDate(legacy.path("created_at").asText()),
                result
        );
    }

    public static Tweet toTweet(JsonNode result) {
        JsonNode legacy = result.path("legacy");
        JsonNode userResult = result.path("core").path("user_results").path("result");
        User author = userResult.isMissingNode() ? null : toUser(userResult);

        return new Tweet(
                result.path("rest_id").asLong(),
                legacy.path("full_text").asText(legacy.path("text").asText("")),
                author,
                parseDate(legacy.path("created_at").asText()),
                toStats(legacy),
                legacy.path("lang").asText(""),
                legacy.path("conversation_id_str").asLong(),
                result
        );
    }

    public static TweetStats toStats(JsonNode legacy) {
        return new TweetStats(
                legacy.path("favorite_count").asLong(),
                legacy.path("reply_count").asLong(),
                legacy.path("retweet_count").asLong(),
                legacy.path("quote_count").asLong(),
                legacy.path("views").path("count").asLong()
        );
    }

    public static Trend toTrend(JsonNode node) {
        return new Trend(
                node.path("name").asText(""),
                node.path("tweet_count").asLong(),
                node
        );
    }

    /** Extracts tweet result nodes and bottom cursor from a timeline instructions array. */
    public static TimelineData extractTimeline(JsonNode instructions) {
        var tweets = new ArrayList<JsonNode>();
        String cursor = null;

        for (JsonNode instruction : instructions) {
            String type = instruction.path("type").asText("");
            if ("TimelineAddEntries".equals(type) || "TimelinePinEntry".equals(type)) {
                JsonNode entries = instruction.path("entries");
                if (entries.isMissingNode()) entries = instruction.path("entry").path("content")
                        .path("items"); // pin entry has different shape
                for (JsonNode entry : entries) {
                    String entryId = entry.path("entryId").asText("");
                    JsonNode content = entry.path("content");
                    String entryType = content.path("entryType").asText("");

                    if ("TimelineTimelineItem".equals(entryType)) {
                        JsonNode result = content.path("itemContent")
                                .path("tweet_results").path("result");
                        if (!result.isMissingNode()) tweets.add(result);

                    } else if ("TimelineTimelineCursor".equals(entryType)) {
                        String cursorType = content.path("cursorType").asText("");
                        if ("Bottom".equals(cursorType)) {
                            cursor = content.path("value").asText(null);
                        }
                    } else if ("TimelineTimelineModule".equals(entryType)) {
                        for (JsonNode item : content.path("items")) {
                            JsonNode result = item.path("item").path("itemContent")
                                    .path("tweet_results").path("result");
                            if (!result.isMissingNode()) tweets.add(result);
                        }
                    }
                }
            } else if ("TimelineReplaceEntry".equals(type)) {
                JsonNode content = instruction.path("entry").path("content");
                if ("TimelineTimelineCursor".equals(content.path("entryType").asText(""))
                        && "Bottom".equals(content.path("cursorType").asText(""))) {
                    cursor = content.path("value").asText(null);
                }
            }
        }
        return new TimelineData(tweets, cursor);
    }

    public record TimelineData(List<JsonNode> tweetResults, String nextCursor) {}

    private static Instant parseDate(String twitterDate) {
        if (twitterDate == null || twitterDate.isBlank()) return Instant.EPOCH;
        try {
            return TWITTER_DATE.parse(twitterDate, Instant::from);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }
}
