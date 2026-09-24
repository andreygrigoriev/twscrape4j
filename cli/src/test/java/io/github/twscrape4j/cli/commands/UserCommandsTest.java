package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.CliHarness;
import io.github.twscrape4j.cli.ExitCodes;
import io.github.twscrape4j.http.TwitterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.twscrape4j.cli.commands.Fixtures.raw;
import static io.github.twscrape4j.cli.commands.Fixtures.tweet;
import static io.github.twscrape4j.cli.commands.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserCommandsTest {

    private static final long BIG_ID = 1_838_000_000_000_000_123L;
    private static final String JACK_JSON = "{\"id\":\"42\",\"username\":\"jack\",\"followers\":1,\"following\":2,"
            + "\"verified\":false,\"url\":\"https://x.com/jack\"}\n";

    private final TwScrape scraper = mock(TwScrape.class);
    private final CliHarness cli = new CliHarness(scraper);

    // ---- user ----

    @Test
    void userByLoginWritesUser() {
        when(scraper.userByLogin("jack")).thenReturn(Optional.of(user(42, "jack")));
        assertEquals(ExitCodes.OK, cli.run("user", "jack"));
        assertEquals(JACK_JSON, cli.out());
        verify(scraper, never()).userByLoginRaw(anyString());
        verify(scraper).close();
    }

    @Test
    void userAcceptsLeadingAt() {
        when(scraper.userByLogin("jack")).thenReturn(Optional.of(user(42, "jack")));
        assertEquals(ExitCodes.OK, cli.run("user", "@jack"));
        assertEquals(JACK_JSON, cli.out());
    }

    @Test
    void userRawUsesRawMethod() {
        when(scraper.userByLoginRaw("jack")).thenReturn(Optional.of(raw("42")));
        assertEquals(ExitCodes.OK, cli.run("user", "jack", "--raw"));
        assertEquals("{\"rest_id\":\"42\"}\n", cli.out());
        verify(scraper, never()).userByLogin(anyString());
    }

    @Test
    void userNotFoundExitsFour() {
        when(scraper.userByLogin("ghost")).thenReturn(Optional.empty());
        assertEquals(ExitCodes.NOT_FOUND, cli.run("user", "ghost"));
        assertEquals("error: user @ghost not found", cli.err().strip());
        assertEquals("", cli.out());
    }

    @Test
    void userRawEmptyExitsFour() {
        when(scraper.userByLoginRaw("ghost")).thenReturn(Optional.empty());
        assertEquals(ExitCodes.NOT_FOUND, cli.run("user", "@ghost", "--raw"));
        assertEquals("error: user @ghost not found", cli.err().strip());
        assertEquals("", cli.out());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "@", "a b", "@jack@", "jack!"})
    void userRejectsInvalidLogin(String login) {
        assertEquals(ExitCodes.USAGE, cli.run("user", login));
        assertTrue(cli.err().contains("not a valid login"), cli.err());
        verifyNoInteractions(scraper);
    }

    @Test
    void unavailableUserWithoutIdIsNotFound() {
        when(scraper.userByLogin("suspended")).thenReturn(Optional.of(user(0, "suspended")));
        assertEquals(ExitCodes.NOT_FOUND, cli.run("user", "@suspended"));
        assertEquals("error: user @suspended not found", cli.err().strip());
        assertEquals("", cli.out());
    }

    // ---- user-by-id ----

    @Test
    void userByIdWritesUserWithStringId() {
        when(scraper.userById(BIG_ID)).thenReturn(Optional.of(user(BIG_ID, "big")));
        assertEquals(ExitCodes.OK, cli.run("user-by-id", Long.toString(BIG_ID)));
        assertTrue(cli.out().startsWith("{\"id\":\"" + BIG_ID + "\""), cli.out());
        verify(scraper, never()).userByIdRaw(anyLong());
    }

    @Test
    void userByIdRawUsesRawMethod() {
        when(scraper.userByIdRaw(42)).thenReturn(Optional.of(raw("42")));
        assertEquals(ExitCodes.OK, cli.run("user-by-id", "42", "--raw"));
        assertEquals("{\"rest_id\":\"42\"}\n", cli.out());
        verify(scraper, never()).userById(anyLong());
    }

    @Test
    void userByIdEmptyExitsFour() {
        when(scraper.userById(42)).thenReturn(Optional.empty());
        assertEquals(ExitCodes.NOT_FOUND, cli.run("user-by-id", "42"));
        assertEquals("error: user 42 not found", cli.err().strip());
    }

    @Test
    void userByIdUnavailableUserIsNotFound() {
        when(scraper.userById(42)).thenReturn(Optional.of(user(0, "")));
        assertEquals(ExitCodes.NOT_FOUND, cli.run("user-by-id", "42"));
    }

    @Test
    void userByIdRejectsInvalidId() {
        assertEquals(ExitCodes.USAGE, cli.run("user-by-id", "@jack"));
        verifyNoInteractions(scraper);
    }

    // ---- tweets / media / followers / following ----

    @Test
    void tweetsByNumericIdSkipsLookup() {
        when(scraper.userTweets(42)).thenReturn(IntStream.rangeClosed(1, 5).mapToObj(i -> tweet(i)));
        assertEquals(ExitCodes.OK, cli.run("tweets", "42", "--limit", "2"));
        assertEquals("{\"id\":\"1\",\"text\":\"t1\"}\n{\"id\":\"2\",\"text\":\"t2\"}\n", cli.out());
        verify(scraper, never()).userByLogin(anyString());
        verify(scraper, never()).userTweetsRaw(anyLong());
    }

    /** The four {@code <user>} commands with their typed and raw stream calls. */
    enum UserRefCase {
        TWEETS("tweets", s -> s.userTweets(anyLong()), s -> s.userTweetsRaw(anyLong())),
        MEDIA("media", s -> s.userMedia(anyLong()), s -> s.userMediaRaw(anyLong())),
        FOLLOWERS("followers", s -> s.userFollowers(anyLong()), s -> s.userFollowersRaw(anyLong())),
        FOLLOWING("following", s -> s.userFollowing(anyLong()), s -> s.userFollowingRaw(anyLong()));

        final String command;
        final Consumer<TwScrape> typed;
        final Consumer<TwScrape> raw;

        UserRefCase(String command, Consumer<TwScrape> typed, Consumer<TwScrape> raw) {
            this.command = command;
            this.typed = typed;
            this.raw = raw;
        }
    }

    @ParameterizedTest
    @EnumSource(UserRefCase.class)
    void loginIsResolvedOnceBeforeStream(UserRefCase c) {
        when(scraper.userByLogin("jack")).thenReturn(Optional.of(user(42, "jack")));
        when(scraper.userTweets(42)).thenReturn(Stream.of(tweet(1)));
        when(scraper.userMedia(42)).thenReturn(Stream.of(tweet(1)));
        when(scraper.userFollowers(42)).thenReturn(Stream.of(user(1, "a")));
        when(scraper.userFollowing(42)).thenReturn(Stream.of(user(1, "a")));

        assertEquals(ExitCodes.OK, cli.run(c.command, "@jack"));

        assertTrue(cli.out().startsWith("{\"id\":\"1\""), cli.out());
        InOrder order = inOrder(scraper);
        order.verify(scraper, times(1)).userByLogin("jack");
        c.typed.accept(order.verify(scraper));
    }

    @ParameterizedTest
    @EnumSource(UserRefCase.class)
    void unknownLoginExitsFourWithoutStreamCall(UserRefCase c) {
        when(scraper.userByLogin("ghost")).thenReturn(Optional.empty());
        assertEquals(ExitCodes.NOT_FOUND, cli.run(c.command, "@ghost"));
        assertEquals("error: user @ghost not found", cli.err().strip());
        assertEquals("", cli.out());
        c.typed.accept(verify(scraper, never()));
        c.raw.accept(verify(scraper, never()));
    }

    @ParameterizedTest
    @EnumSource(UserRefCase.class)
    void unavailableLoginWithoutIdExitsFour(UserRefCase c) {
        when(scraper.userByLogin("suspended")).thenReturn(Optional.of(user(0, "suspended")));
        assertEquals(ExitCodes.NOT_FOUND, cli.run(c.command, "@suspended"));
        assertEquals("error: user @suspended not found", cli.err().strip());
        c.typed.accept(verify(scraper, never()));
    }

    @ParameterizedTest
    @EnumSource(UserRefCase.class)
    void loginLookupFailureExitsOneWithoutStreamCall(UserRefCase c) {
        when(scraper.userByLogin("jack")).thenThrow(new TwitterException("lookup failed"));
        assertEquals(ExitCodes.RUNTIME, cli.run(c.command, "@jack"));
        assertEquals("error: lookup failed", cli.err().strip());
        c.typed.accept(verify(scraper, never()));
        c.raw.accept(verify(scraper, never()));
        verify(scraper).close();
    }

    @Test
    void bareLoginIsUsageError() {
        assertEquals(ExitCodes.USAGE, cli.run("tweets", "jack"));
        assertTrue(cli.err().contains("did you mean @jack?"), cli.err());
        verifyNoInteractions(scraper);
    }

    @Test
    void tweetsRawUsesRawMethod() {
        when(scraper.userByLogin("jack")).thenReturn(Optional.of(user(42, "jack")));
        when(scraper.userTweetsRaw(42)).thenReturn(Stream.of(raw("1")));
        assertEquals(ExitCodes.OK, cli.run("tweets", "@jack", "--raw"));
        assertEquals("{\"rest_id\":\"1\"}\n", cli.out());
        verify(scraper, times(1)).userByLogin("jack");
        verify(scraper, never()).userTweets(anyLong());
    }

    @Test
    void mediaCallsUserMedia() {
        when(scraper.userMedia(42)).thenReturn(Stream.of(tweet(3)));
        assertEquals(ExitCodes.OK, cli.run("media", "42"));
        assertEquals("{\"id\":\"3\",\"text\":\"t3\"}\n", cli.out());
        verify(scraper, never()).userMediaRaw(anyLong());
    }

    @Test
    void mediaRawUsesRawMethod() {
        when(scraper.userMediaRaw(42)).thenReturn(Stream.of(raw("3")));
        assertEquals(ExitCodes.OK, cli.run("media", "42", "--raw"));
        assertEquals("{\"rest_id\":\"3\"}\n", cli.out());
        verify(scraper, never()).userMedia(anyLong());
    }

    @Test
    void followersCallsUserFollowers() {
        when(scraper.userFollowers(7)).thenReturn(Stream.of(user(42, "jack")));
        assertEquals(ExitCodes.OK, cli.run("followers", "7"));
        assertEquals(JACK_JSON, cli.out());
        verify(scraper, never()).userFollowersRaw(anyLong());
    }

    @Test
    void followersRawJsonFormat() {
        when(scraper.userFollowersRaw(7)).thenReturn(Stream.of(raw("42")));
        assertEquals(ExitCodes.OK, cli.run("followers", "7", "--raw", "--format", "json"));
        String out = cli.out().strip();
        assertTrue(out.startsWith("[") && out.contains("\"rest_id\" : \"42\""), out);
        verify(scraper, never()).userFollowers(anyLong());
    }

    @Test
    void followingCallsUserFollowing() {
        when(scraper.userFollowing(7)).thenReturn(Stream.of(user(42, "jack")));
        assertEquals(ExitCodes.OK, cli.run("following", "7"));
        assertEquals(JACK_JSON, cli.out());
        verify(scraper, never()).userFollowingRaw(anyLong());
    }

    @Test
    void followingRawUsesRawMethod() {
        when(scraper.userFollowingRaw(7)).thenReturn(Stream.of(raw("42")));
        assertEquals(ExitCodes.OK, cli.run("following", "7", "--raw"));
        assertEquals("{\"rest_id\":\"42\"}\n", cli.out());
        verify(scraper, never()).userFollowing(anyLong());
    }

    @Test
    void twitterExceptionExitsOne() {
        when(scraper.userFollowing(7)).thenThrow(new TwitterException("boom"));
        assertEquals(ExitCodes.RUNTIME, cli.run("following", "7"));
        assertEquals("error: boom", cli.err().strip());
    }
}
