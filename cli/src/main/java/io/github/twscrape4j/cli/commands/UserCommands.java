package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.ModelJson;
import io.github.twscrape4j.cli.UserRef;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Commands that operate on a user: {@code user}, {@code user-by-id}, {@code tweets}, {@code media},
 * {@code followers} and {@code following}.
 */
public final class UserCommands {

    private UserCommands() {
    }

    /** {@code twscrape user <login>}: a user by login; exits 4 when the user is not found. */
    @Command(name = "user", mixinStandardHelpOptions = true, description = "Show a user by login.")
    public static class UserCommand extends DataCommand {
        @Parameters(index = "0", paramLabel = "LOGIN", converter = LoginConverter.class,
                description = "User login (a leading @ is optional).")
        String login;

        @Override
        protected void run(TwScrape scraper) {
            emitOptional(() -> UserRef.available(scraper.userByLogin(login)), ModelJson::user,
                    () -> scraper.userByLoginRaw(login), "user @" + login + " not found");
        }
    }

    /** {@code twscrape user-by-id <userId>}: a user by ID; exits 4 when the user is not found. */
    @Command(name = "user-by-id", mixinStandardHelpOptions = true, description = "Show a user by ID.")
    public static class UserByIdCommand extends DataCommand {
        @Parameters(index = "0", paramLabel = "USER_ID", converter = PositiveLongConverter.class,
                description = "User ID (a positive number).")
        long userId;

        @Override
        protected void run(TwScrape scraper) {
            emitOptional(() -> UserRef.available(scraper.userById(userId)), ModelJson::user,
                    () -> scraper.userByIdRaw(userId), "user " + userId + " not found");
        }
    }

    /** Shared {@code <user>} parameter: a numeric ID or {@code @login}, resolved once before the stream call. */
    abstract static class UserRefCommand extends DataCommand {
        @Parameters(index = "0", paramLabel = "USER", converter = UserRef.Converter.class,
                description = "User ID (a positive number) or @login.")
        UserRef user;
    }

    /** {@code twscrape tweets <user>}: a user's tweets. */
    @Command(name = "tweets", mixinStandardHelpOptions = true, description = "List a user's tweets.")
    public static class TweetsCommand extends UserRefCommand {
        @Override
        protected void run(TwScrape scraper) {
            long id = user.resolve(scraper);
            emitStream(() -> scraper.userTweets(id), ModelJson::tweet, () -> scraper.userTweetsRaw(id));
        }
    }

    /** {@code twscrape media <user>}: a user's media tweets. */
    @Command(name = "media", mixinStandardHelpOptions = true, description = "List a user's media tweets.")
    public static class MediaCommand extends UserRefCommand {
        @Override
        protected void run(TwScrape scraper) {
            long id = user.resolve(scraper);
            emitStream(() -> scraper.userMedia(id), ModelJson::tweet, () -> scraper.userMediaRaw(id));
        }
    }

    /** {@code twscrape followers <user>}: a user's followers. */
    @Command(name = "followers", mixinStandardHelpOptions = true, description = "List a user's followers.")
    public static class FollowersCommand extends UserRefCommand {
        @Override
        protected void run(TwScrape scraper) {
            long id = user.resolve(scraper);
            emitStream(() -> scraper.userFollowers(id), ModelJson::user, () -> scraper.userFollowersRaw(id));
        }
    }

    /** {@code twscrape following <user>}: users a user follows. */
    @Command(name = "following", mixinStandardHelpOptions = true, description = "List users a user follows.")
    public static class FollowingCommand extends UserRefCommand {
        @Override
        protected void run(TwScrape scraper) {
            long id = user.resolve(scraper);
            emitStream(() -> scraper.userFollowing(id), ModelJson::user, () -> scraper.userFollowingRaw(id));
        }
    }
}
