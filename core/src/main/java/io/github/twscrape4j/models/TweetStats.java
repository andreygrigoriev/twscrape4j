package io.github.twscrape4j.models;

public record TweetStats(
        long likeCount,
        long replyCount,
        long retweetCount,
        long quoteCount,
        long viewCount
) {}
