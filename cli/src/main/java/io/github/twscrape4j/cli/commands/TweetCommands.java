package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.ModelJson;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Commands that operate on a single tweet: {@code tweet}, {@code replies} and {@code retweeters}. */
public final class TweetCommands {

    private TweetCommands() {
    }

    /** Shared {@code <tweetId>} parameter. */
    abstract static class TweetIdCommand extends DataCommand {
        @Parameters(index = "0", paramLabel = "TWEET_ID", converter = PositiveLongConverter.class,
                description = "Tweet ID (a positive number).")
        long tweetId;
    }

    /** {@code twscrape tweet <tweetId>}: tweet details; exits 4 when the tweet is not found. */
    @Command(name = "tweet", mixinStandardHelpOptions = true, description = "Show a single tweet.")
    public static class TweetCommand extends TweetIdCommand {
        @Override
        protected void run(TwScrape scraper) {
            emitOptional(() -> scraper.tweetDetails(tweetId), ModelJson::tweet,
                    () -> scraper.tweetDetailsRaw(tweetId), "tweet " + tweetId + " not found");
        }
    }

    /** {@code twscrape replies <tweetId>}: replies to a tweet. */
    @Command(name = "replies", mixinStandardHelpOptions = true, description = "List replies to a tweet.")
    public static class RepliesCommand extends TweetIdCommand {
        @Override
        protected void run(TwScrape scraper) {
            emitStream(() -> scraper.tweetReplies(tweetId), ModelJson::tweet,
                    () -> scraper.tweetRepliesRaw(tweetId));
        }
    }

    /** {@code twscrape retweeters <tweetId>}: users who retweeted a tweet. */
    @Command(name = "retweeters", mixinStandardHelpOptions = true, description = "List users who retweeted a tweet.")
    public static class RetweetersCommand extends TweetIdCommand {
        @Override
        protected void run(TwScrape scraper) {
            emitStream(() -> scraper.tweetRetweeters(tweetId), ModelJson::user,
                    () -> scraper.tweetRetweetersRaw(tweetId));
        }
    }
}
