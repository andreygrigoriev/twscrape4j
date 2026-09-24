package io.github.twscrape4j.cli;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.auth.ChallengeHandler;
import io.github.twscrape4j.http.TwitterException;

import java.io.Console;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds a stateless {@link TwScrape} with one in-memory account from environment variables.
 *
 * <p>Auth and challenge rejections become {@link CliConfigException} (exit 3); network and I/O errors propagate
 * unchanged (exit 1).
 */
public class DefaultScraperFactory implements ScraperFactory {

    private final Map<String, String> env;
    private final Console console;
    private final Function<ChallengeHandler, TwScrape> creator;

    public DefaultScraperFactory(Map<String, String> env, Console console) {
        this(env, console, TwScrape::create);
    }

    DefaultScraperFactory(Map<String, String> env, Console console, Function<ChallengeHandler, TwScrape> creator) {
        this.env = env;
        this.console = console;
        this.creator = creator;
    }

    @Override
    public TwScrape open() {
        EnvAccountConfig config = EnvAccountConfig.load(env);
        TwScrape scraper = creator.apply(new CliChallengeHandler(env, console));
        try {
            switch (config) {
                case EnvAccountConfig.Cookies c -> scraper.addAccountByCookies(c.username(), c.authToken(), c.ct0());
                case EnvAccountConfig.Login l -> login(scraper, l);
            }
            return scraper;
        } catch (RuntimeException e) {
            scraper.close();
            throw e;
        }
    }

    private static void login(TwScrape scraper, EnvAccountConfig.Login l) {
        try {
            scraper.addAccount(l.username(), l.password(), l.email());
        } catch (RuntimeException e) {
            // the login flow wraps handler exceptions, so dig out a challenge failure first
            CliConfigException challenge = findCause(e, CliConfigException.class);
            if (challenge != null) {
                throw challenge;
            }
            if (isAuthRejection(e)) {
                throw new CliConfigException("login rejected for " + l.username() + ": " + e.getMessage(), e);
            }
            throw e;
        }
    }

    /** 4xx answers from the login flow (except 429) and missing session cookies mean the credentials were refused. */
    private static boolean isAuthRejection(RuntimeException e) {
        if (e instanceof TwitterException.TwitterApiException api) {
            return api.code() >= 400 && api.code() < 500 && api.code() != 429;
        }
        return e instanceof IllegalStateException || e instanceof TwitterException.AccountSuspendedException;
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return null;
    }
}
