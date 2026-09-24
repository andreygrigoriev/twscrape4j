package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.SearchMode;
import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.CliHarness;
import io.github.twscrape4j.cli.ExitCodes;
import io.github.twscrape4j.http.TwitterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.twscrape4j.cli.commands.Fixtures.raw;
import static io.github.twscrape4j.cli.commands.Fixtures.tweet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchCommandTest {

    private final TwScrape scraper = mock(TwScrape.class);
    private final CliHarness cli = new CliHarness(scraper);

    @Test
    void searchDefaultsToLatestAndWritesJsonLines() {
        when(scraper.search("java", SearchMode.LATEST)).thenReturn(Stream.of(tweet(1), tweet(2)));
        assertEquals(ExitCodes.OK, cli.run("search", "java"));
        assertEquals("{\"id\":\"1\",\"text\":\"t1\"}\n{\"id\":\"2\",\"text\":\"t2\"}\n", cli.out());
        assertEquals("", cli.err());
        verify(scraper, never()).searchRaw(anyString(), any());
        verify(scraper).close();
    }

    @ParameterizedTest
    @CsvSource({"top,TOP", "TOP,TOP", "Media,MEDIA", "latest,LATEST"})
    void modeIsParsedCaseInsensitively(String arg, SearchMode expected) {
        when(scraper.search("q", expected)).thenReturn(Stream.empty());
        assertEquals(ExitCodes.OK, cli.run("search", "q", "--mode", arg));
        verify(scraper).search("q", expected);
        assertEquals("", cli.out());
    }

    @Test
    void rawUsesRawMethod() {
        when(scraper.searchRaw("q", SearchMode.TOP)).thenReturn(Stream.of(raw("9")));
        assertEquals(ExitCodes.OK, cli.run("search", "q", "--mode", "top", "--raw"));
        assertEquals("{\"rest_id\":\"9\"}\n", cli.out());
        verify(scraper, never()).search(anyString(), any());
    }

    @Test
    void limitIsHonored() {
        when(scraper.search("q", SearchMode.LATEST))
                .thenReturn(IntStream.rangeClosed(1, 100).mapToObj(i -> tweet(i)));
        assertEquals(ExitCodes.OK, cli.run("search", "q", "--limit", "3"));
        assertEquals(3, cli.out().lines().count());
    }

    @Test
    void defaultLimitIsTwenty() {
        when(scraper.search("q", SearchMode.LATEST))
                .thenReturn(IntStream.rangeClosed(1, 100).mapToObj(i -> tweet(i)));
        assertEquals(ExitCodes.OK, cli.run("search", "q"));
        assertEquals(20, cli.out().lines().count());
    }

    @Test
    void formatJsonWritesArray() {
        when(scraper.search("q", SearchMode.LATEST)).thenReturn(Stream.of(tweet(1)));
        assertEquals(ExitCodes.OK, cli.run("search", "q", "--format", "json"));
        String out = cli.out().strip();
        assertTrue(out.startsWith("[") && out.endsWith("]"), out);
        assertTrue(out.contains("\"id\" : \"1\""), out);
    }

    @Test
    void invalidModeIsUsageError() {
        assertEquals(ExitCodes.USAGE, cli.run("search", "q", "--mode", "oldest"));
        assertTrue(cli.err().contains("--mode"), cli.err());
        verifyNoInteractions(scraper);
    }

    @Test
    void missingQueryIsUsageError() {
        assertEquals(ExitCodes.USAGE, cli.run("search"));
        verifyNoInteractions(scraper);
    }

    @Test
    void twitterExceptionExitsOne() {
        when(scraper.search("q", SearchMode.LATEST)).thenThrow(new TwitterException("rate limited"));
        assertEquals(ExitCodes.RUNTIME, cli.run("search", "q"));
        assertEquals("error: rate limited", cli.err().strip());
        assertEquals("", cli.out());
        verify(scraper).close();
    }
}
