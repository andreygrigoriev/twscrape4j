package io.github.twscrape4j.cli;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.auth.ChallengeHandler;
import io.github.twscrape4j.http.TwitterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    static Stream<Arguments> loginFailures() {
        return Stream.of(
                Arguments.of(new TwitterException.TwitterApiException(399, "odd"), ExitCodes.RUNTIME),
                Arguments.of(new TwitterException.TwitterApiException(400, "Wrong password"), ExitCodes.CONFIG),
                Arguments.of(new TwitterException.TwitterApiException(403, "Forbidden"), ExitCodes.CONFIG),
                Arguments.of(new TwitterException.TwitterApiException(429, "Rate limit"), ExitCodes.RUNTIME),
                Arguments.of(new TwitterException.TwitterApiException(499, "odd"), ExitCodes.CONFIG),
                Arguments.of(new TwitterException.TwitterApiException(500, "Server error"), ExitCodes.RUNTIME),
                Arguments.of(new TwitterException.LoginFailedException("alice", "no session cookies"), ExitCodes.CONFIG),
                Arguments.of(new TwitterException.LoginUnsupportedException("alice", "unsupported login step"),
                        ExitCodes.CONFIG),
                Arguments.of(new TwitterException.AccountSuspendedException("alice"), ExitCodes.CONFIG),
                Arguments.of(new TwitterException("Login failed for alice", new IOException("reset")), ExitCodes.RUNTIME),
                // an unrelated bug (e.g. in the repository or pool) is not a credential problem
                Arguments.of(new IllegalStateException("pool not initialised"), ExitCodes.RUNTIME),
                Arguments.of(new CliConfigException("login requires a verification code"), ExitCodes.CONFIG));
    }

    @ParameterizedTest
    @MethodSource("loginFailures")
    void loginFailureMapsToExitCodeAndClosesScraper(RuntimeException failure, int expectedExitCode) {
        doThrow(failure).when(scraper).addAccount("alice", "pw", "a@example.com");

        var ex = assertThrows(RuntimeException.class, () -> factory(LOGIN_ENV).open());

        assertEquals(expectedExitCode, TwScrapeCli.exitCode(ex));
        if (expectedExitCode == ExitCodes.RUNTIME) {
            assertSame(failure, ex, "runtime errors must propagate unchanged");
        } else if (failure instanceof TwitterException.LoginUnsupportedException) {
            assertSame(failure, ex.getCause());
            assertTrue(ex.getMessage().startsWith("login flow not supported for alice"), ex.getMessage());
        } else if (!(failure instanceof CliConfigException)) {
            assertSame(failure, ex.getCause());
            assertTrue(ex.getMessage().startsWith("login rejected for alice"), ex.getMessage());
        }
        verify(scraper).close();
    }

    @Test
    void cookieModeFailureClosesScraperAndPropagates() {
        var failure = new IllegalArgumentException("auth_token must not be blank");
        doThrow(failure).when(scraper).addAccountByCookies("cli", "tok", "ct");

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> factory(Map.of("TWSCRAPE_AUTH_TOKEN", "tok", "TWSCRAPE_CT0", "ct")).open()));
        verify(scraper).close();
    }

    @Test
    void closeFailureIsSuppressedAndKeepsOriginalError() {
        doThrow(new TwitterException.TwitterApiException(401, "Bad credentials"))
                .when(scraper).addAccount("alice", "pw", "a@example.com");
        var closeError = new IllegalStateException("close failed");
        doThrow(closeError).when(scraper).close();

        var ex = assertThrows(CliConfigException.class, () -> factory(LOGIN_ENV).open());

        assertEquals(ExitCodes.CONFIG, TwScrapeCli.exitCode(ex));
        assertSame(closeError, ex.getSuppressed()[0]);
    }
}
