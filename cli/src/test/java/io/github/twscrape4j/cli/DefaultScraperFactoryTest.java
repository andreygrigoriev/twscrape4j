package io.github.twscrape4j.cli;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.auth.ChallengeHandler;
import io.github.twscrape4j.http.TwitterException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DefaultScraperFactoryTest {

    private static final Map<String, String> LOGIN_ENV = Map.of(
            "TWSCRAPE_USERNAME", "alice", "TWSCRAPE_PASSWORD", "pw", "TWSCRAPE_EMAIL", "a@example.com");

    private final TwScrape scraper = mock(TwScrape.class);
    private final AtomicReference<ChallengeHandler> handler = new AtomicReference<>();

    private DefaultScraperFactory factory(Map<String, String> env) {
        return new DefaultScraperFactory(env, null, h -> {
            handler.set(h);
            return scraper;
        });
    }

    @Test
    void cookieModeAddsCookieAccount() {
        var result = factory(Map.of("TWSCRAPE_AUTH_TOKEN", "tok", "TWSCRAPE_CT0", "ct")).open();
        assertSame(scraper, result);
        verify(scraper).addAccountByCookies("cli", "tok", "ct");
        assertInstanceOf(CliChallengeHandler.class, handler.get());
    }

    @Test
    void loginModeAddsLoginAccount() {
        assertSame(scraper, factory(LOGIN_ENV).open());
        verify(scraper).addAccount("alice", "pw", "a@example.com");
    }

    @Test
    void missingEnvFailsBeforeCreatingScraper() {
        assertThrows(CliConfigException.class, () -> factory(Map.of()).open());
        verifyNoInteractions(scraper);
    }

    @Test
    void loginRejectionIsConfigErrorAndClosesScraper() {
        doThrow(new TwitterException.TwitterApiException(400, "Wrong password"))
                .when(scraper).addAccount("alice", "pw", "a@example.com");
        var ex = assertThrows(CliConfigException.class, () -> factory(LOGIN_ENV).open());
        assertInstanceOf(TwitterException.TwitterApiException.class, ex.getCause());
        verify(scraper).close();
    }

    @Test
    void wrappedChallengeFailureIsUnwrapped() {
        var challenge = new CliConfigException("login requires a verification code; set TWSCRAPE_CHALLENGE_CODE");
        doThrow(new TwitterException("Login failed for alice", challenge))
                .when(scraper).addAccount("alice", "pw", "a@example.com");
        assertSame(challenge, assertThrows(CliConfigException.class, () -> factory(LOGIN_ENV).open()));
        verify(scraper).close();
    }

    @Test
    void networkErrorPropagatesUnchanged() {
        var network = new TwitterException("Login failed for alice", new IOException("connection refused"));
        doThrow(network).when(scraper).addAccount("alice", "pw", "a@example.com");
        assertSame(network, assertThrows(TwitterException.class, () -> factory(LOGIN_ENV).open()));
        verify(scraper).close();
    }

    @Test
    void serverErrorDuringLoginPropagatesUnchanged() {
        var server = new TwitterException.TwitterApiException(503, "Service unavailable");
        doThrow(server).when(scraper).addAccount("alice", "pw", "a@example.com");
        assertSame(server, assertThrows(TwitterException.class, () -> factory(LOGIN_ENV).open()));
    }
}
