package io.github.twscrape4j.cli;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import io.github.twscrape4j.models.Trend;
import io.github.twscrape4j.models.Tweet;
import io.github.twscrape4j.models.TweetStats;
import io.github.twscrape4j.models.User;

import java.time.Instant;

/**
 * Maps library models to the CLI output schema using the Jackson tree model only (no reflection),
 * so native builds need no metadata for the model records. IDs are strings (Twitter IDs exceed 2^53),
 * instants are ISO-8601 strings, absent fields are omitted and {@code raw} is never included. The core mapper
 * fills missing strings with {@code ""} and missing dates with {@link Instant#EPOCH}, so those count as absent.
 */
public final class ModelJson {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final String BASE_URL = "https://x.com/";

    private ModelJson() {
    }

    public static ObjectNode tweet(Tweet tweet) {
        ObjectNode node = NODES.objectNode();
        node.put("id", Long.toString(tweet.id()));
        putIfPresent(node, "text", tweet.text());
        putIfPresent(node, "createdAt", tweet.createdAt());
        putIfPresent(node, "lang", tweet.lang());
        // the mapper yields 0 when conversation_id_str is absent
        if (tweet.conversationId() != 0) {
            node.put("conversationId", Long.toString(tweet.conversationId()));
        }
        if (tweet.author() != null) {
            node.set("author", user(tweet.author()));
        }
        if (tweet.stats() != null) {
            node.set("stats", stats(tweet.stats()));
        }
        if (tweet.author() != null && isPresent(tweet.author().username())) {
            node.put("url", BASE_URL + tweet.author().username() + "/status/" + tweet.id());
        }
        return node;
    }

    public static ObjectNode user(User user) {
        ObjectNode node = NODES.objectNode();
        node.put("id", Long.toString(user.id()));
        putIfPresent(node, "username", user.username());
        putIfPresent(node, "displayName", user.displayName());
        putIfPresent(node, "bio", user.bio());
        node.put("followers", user.followersCount());
        node.put("following", user.followingCount());
        node.put("verified", user.verified());
        putIfPresent(node, "createdAt", user.createdAt());
        if (isPresent(user.username())) {
            node.put("url", BASE_URL + user.username());
        }
        return node;
    }

    public static ObjectNode trend(Trend trend) {
        ObjectNode node = NODES.objectNode();
        putIfPresent(node, "name", trend.name());
        node.put("tweetCount", trend.tweetCount());
        return node;
    }

    static ObjectNode stats(TweetStats stats) {
        ObjectNode node = NODES.objectNode();
        node.put("likes", stats.likeCount());
        node.put("replies", stats.replyCount());
        node.put("retweets", stats.retweetCount());
        node.put("quotes", stats.quoteCount());
        node.put("views", stats.viewCount());
        return node;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (isPresent(value)) {
            node.put(field, value);
        }
    }

    private static void putIfPresent(ObjectNode node, String field, Instant value) {
        if (value != null && !Instant.EPOCH.equals(value)) {
            node.put(field, value.toString());
        }
    }
}
