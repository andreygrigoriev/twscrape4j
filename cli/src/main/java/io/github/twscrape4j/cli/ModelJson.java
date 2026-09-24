package io.github.twscrape4j.cli;

import io.github.twscrape4j.models.Trend;
import io.github.twscrape4j.models.Tweet;
import io.github.twscrape4j.models.TweetStats;
import io.github.twscrape4j.models.User;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Maps library models to the CLI output schema using the Jackson tree model only (no reflection),
 * so native builds need no metadata for the model records. IDs are strings (Twitter IDs exceed 2^53),
 * instants are ISO-8601 strings, null fields are omitted and {@code raw} is never included.
 */
public final class ModelJson {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final String BASE_URL = "https://x.com/";

    private ModelJson() {
    }

    public static ObjectNode tweet(Tweet tweet) {
        ObjectNode node = NODES.objectNode();
        node.put("id", Long.toString(tweet.id()));
        putIfNotNull(node, "text", tweet.text());
        putIfNotNull(node, "createdAt", tweet.createdAt());
        putIfNotNull(node, "lang", tweet.lang());
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
        if (tweet.author() != null && tweet.author().username() != null) {
            node.put("url", BASE_URL + tweet.author().username() + "/status/" + tweet.id());
        }
        return node;
    }

    public static ObjectNode user(User user) {
        ObjectNode node = NODES.objectNode();
        node.put("id", Long.toString(user.id()));
        putIfNotNull(node, "username", user.username());
        putIfNotNull(node, "displayName", user.displayName());
        putIfNotNull(node, "bio", user.bio());
        node.put("followers", user.followersCount());
        node.put("following", user.followingCount());
        node.put("verified", user.verified());
        putIfNotNull(node, "createdAt", user.createdAt());
        if (user.username() != null) {
            node.put("url", BASE_URL + user.username());
        }
        return node;
    }

    public static ObjectNode trend(Trend trend) {
        ObjectNode node = NODES.objectNode();
        putIfNotNull(node, "name", trend.name());
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

    private static void putIfNotNull(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private static void putIfNotNull(ObjectNode node, String field, Instant value) {
        if (value != null) {
            node.put(field, value.toString());
        }
    }
}
