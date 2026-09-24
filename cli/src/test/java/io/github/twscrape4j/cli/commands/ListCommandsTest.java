package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.CliHarness;
import io.github.twscrape4j.cli.ExitCodes;
import io.github.twscrape4j.http.TwitterException;
import org.junit.jupiter.api.Test;

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

class ListCommandsTest {

    private final TwScrape scraper = mock(TwScrape.class);
    private final CliHarness cli = new CliHarness(scraper);

    @Test
    void listTimelineStreamsTweetsWithLimit() {
        when(scraper.listTimeline(9)).thenReturn(IntStream.rangeClosed(1, 5).mapToObj(i -> tweet(i)));
        assertEquals(ExitCodes.OK, cli.run("list-timeline", "9", "--limit", "2"));
        assertEquals("{\"id\":\"1\",\"text\":\"t1\"}\n{\"id\":\"2\",\"text\":\"t2\"}\n", cli.out());
        verify(scraper, never()).listTimelineRaw(anyLong());
        verify(scraper).close();
    }

    @Test
    void listTimelineRawUsesRawMethod() {
        when(scraper.listTimelineRaw(9)).thenReturn(Stream.of(raw("1")));
        assertEquals(ExitCodes.OK, cli.run("list-timeline", "9", "--raw"));
        assertEquals("{\"rest_id\":\"1\"}\n", cli.out());
        verify(scraper, never()).listTimeline(anyLong());
    }

    @Test
    void listMembersStreamsUsers() {
        when(scraper.listMembers(9)).thenReturn(Stream.of(user(42, "jack")));
        assertEquals(ExitCodes.OK, cli.run("list-members", "9", "--format", "json"));
        String out = cli.out().strip();
        assertTrue(out.startsWith("[") && out.contains("\"username\" : \"jack\""), out);
        verify(scraper, never()).listMembersRaw(anyLong());
    }

    @Test
    void listMembersRawUsesRawMethod() {
        when(scraper.listMembersRaw(9)).thenReturn(Stream.of(raw("42")));
        assertEquals(ExitCodes.OK, cli.run("list-members", "9", "--raw"));
        assertEquals("{\"rest_id\":\"42\"}\n", cli.out());
        verify(scraper, never()).listMembers(anyLong());
    }

    @Test
    void invalidListIdIsUsageError() {
        assertEquals(ExitCodes.USAGE, cli.run("list-timeline", "abc"));
        assertEquals(ExitCodes.USAGE, cli.run("list-members", "0"));
        verifyNoInteractions(scraper);
    }

    @Test
    void twitterExceptionExitsOne() {
        when(scraper.listMembers(9)).thenThrow(new TwitterException("boom"));
        assertEquals(ExitCodes.RUNTIME, cli.run("list-members", "9"));
        assertEquals("error: boom", cli.err().strip());
        assertEquals("", cli.out());
    }
}
