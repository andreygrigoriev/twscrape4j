package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.CliHarness;
import io.github.twscrape4j.cli.ExitCodes;
import io.github.twscrape4j.http.TwitterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.twscrape4j.cli.commands.Fixtures.raw;
import static io.github.twscrape4j.cli.commands.Fixtures.tweet;
import static io.github.twscrape4j.cli.commands.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TweetCommandsTest {

    private static final long BIG_ID = 1_838_000_000_000_000_123L;

    private final TwScrape scraper = mock(TwScrape.class);
    private final CliHarness cli = new CliHarness(scraper);

    // ---- tweet ----

    @Test
    void tweetWritesSingleObject() {
        when(scraper.tweetDetails(BIG_ID)).thenReturn(Optional.of(tweet(BIG_ID)));
        assertEquals(ExitCodes.OK, cli.run("tweet", Long.toString(BIG_ID)));
        assertEquals("{\"id\":\"" + BIG_ID + "\",\"text\":\"t" + BIG_ID + "\"}\n", cli.out());
        verify(scraper, never()).tweetDetailsRaw(anyLong());
        verify(scraper).close();
    }

    @Test
    void tweetRawUsesRawMethod() {
        when(scraper.tweetDetailsRaw(5)).thenReturn(Optional.of(raw("5")));
        assertEquals(ExitCodes.OK, cli.run("tweet", "5", "--raw", "--format", "json"));
        assertTrue(cli.out().contains("\"rest_id\" : \"5\""), cli.out());
        verify(scraper, never()).tweetDetails(anyLong());
    }

    @Test
    void tweetNotFoundExitsFour() {
        when(scraper.tweetDetails(5)).thenReturn(Optional.empty());
        assertEquals(ExitCodes.NOT_FOUND, cli.run("tweet", "5"));
        assertEquals("error: tweet 5 not found", cli.err().strip());
        assertEquals("", cli.out());
    }

    @Test
    void tweetTwitterExceptionExitsOne() {
        when(scraper.tweetDetails(5)).thenThrow(new TwitterException("boom"));
        assertEquals(ExitCodes.RUNTIME, cli.run("tweet", "5"));
        assertEquals("error: boom", cli.err().strip());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-5", "1.5", "99999999999999999999"})
    void invalidTweetIdIsUsageError(String id) {
        assertEquals(ExitCodes.USAGE, cli.run("tweet", "--", id));
        assertTrue(cli.err().contains("not a valid ID"), cli.err());
        verifyNoInteractions(scraper);
    }

    @Test
    void invalidIdIsRejectedByAllTweetCommands() {
        assertEquals(ExitCodes.USAGE, cli.run("replies", "x"));
        assertEquals(ExitCodes.USAGE, cli.run("retweeters", "0"));
        verifyNoInteractions(scraper);
    }

    // ---- replies ----

    @Test
    void repliesStreamsTweets() {
        when(scraper.tweetReplies(7)).thenReturn(IntStream.rangeClosed(1, 5).mapToObj(i -> tweet(i)));
        assertEquals(ExitCodes.OK, cli.run("replies", "7", "--limit", "2"));
        assertEquals("{\"id\":\"1\",\"text\":\"t1\"}\n{\"id\":\"2\",\"text\":\"t2\"}\n", cli.out());
        verify(scraper, never()).tweetRepliesRaw(anyLong());
    }

    @Test
    void repliesRawUsesRawMethod() {
        when(scraper.tweetRepliesRaw(7)).thenReturn(Stream.of(raw("1")));
        assertEquals(ExitCodes.OK, cli.run("replies", "7", "--raw"));
        assertEquals("{\"rest_id\":\"1\"}\n", cli.out());
        verify(scraper, never()).tweetReplies(anyLong());
    }

    @Test
    void repliesEmptyStreamExitsZero() {
        when(scraper.tweetReplies(7)).thenReturn(Stream.empty());
        assertEquals(ExitCodes.OK, cli.run("replies", "7"));
        assertEquals("", cli.out());
    }

    // ---- retweeters ----

    @Test
    void retweetersStreamsUsers() {
        when(scraper.tweetRetweeters(7)).thenReturn(Stream.of(user(42, "jack")));
        assertEquals(ExitCodes.OK, cli.run("retweeters", "7"));
        assertEquals("{\"id\":\"42\",\"username\":\"jack\",\"followers\":1,\"following\":2,"
                + "\"verified\":false,\"url\":\"https://x.com/jack\"}\n", cli.out());
        verify(scraper, never()).tweetRetweetersRaw(anyLong());
    }

    @Test
    void retweetersJsonFormatAndRaw() {
        when(scraper.tweetRetweetersRaw(7)).thenReturn(Stream.of(raw("42")));
        assertEquals(ExitCodes.OK, cli.run("retweeters", "7", "--raw", "--format", "json"));
        String out = cli.out().strip();
        assertTrue(out.startsWith("[") && out.contains("\"rest_id\" : \"42\""), out);
        verify(scraper, never()).tweetRetweeters(anyLong());
    }

    @Test
    void retweetersMidStreamErrorExitsOneKeepingPartialJsonl() {
        when(scraper.tweetRetweeters(7)).thenReturn(Stream.concat(Stream.of(user(1, "a")),
                Stream.generate(() -> {
                    throw new TwitterException("page 2 failed");
                })));
        assertEquals(ExitCodes.RUNTIME, cli.run("retweeters", "7"));
        assertEquals(1, cli.out().lines().count());
        assertEquals("error: page 2 failed", cli.err().strip());
    }
}
