package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.TrendCategory;
import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.CliHarness;
import io.github.twscrape4j.cli.ExitCodes;
import io.github.twscrape4j.http.TwitterException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.twscrape4j.cli.commands.Fixtures.raw;
import static io.github.twscrape4j.cli.commands.Fixtures.trend;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TrendsCommandTest {

    private final TwScrape scraper = mock(TwScrape.class);
    private final CliHarness cli = new CliHarness(scraper);

    @Test
    void defaultsToTrendingCategory() {
        when(scraper.trends(TrendCategory.TRENDING)).thenReturn(List.of(trend("#java", 1000), trend("jdk", 5)));
        assertEquals(ExitCodes.OK, cli.run("trends"));
        assertEquals("{\"name\":\"#java\",\"tweetCount\":1000}\n{\"name\":\"jdk\",\"tweetCount\":5}\n", cli.out());
        verify(scraper, never()).trendsRaw(any());
    }

    @Test
    void categoryIsParsedCaseInsensitively() {
        when(scraper.trends(TrendCategory.SPORT)).thenReturn(List.of());
        assertEquals(ExitCodes.OK, cli.run("trends", "--category", "Sport"));
        verify(scraper).trends(TrendCategory.SPORT);
        assertEquals("", cli.out());
    }

    @Test
    void rawUsesRawMethod() {
        when(scraper.trendsRaw(TrendCategory.NEWS)).thenReturn(List.of(raw("a"), raw("b")));
        assertEquals(ExitCodes.OK, cli.run("trends", "--category", "news", "--raw"));
        assertEquals("{\"rest_id\":\"a\"}\n{\"rest_id\":\"b\"}\n", cli.out());
        verify(scraper, never()).trends(any());
    }

    @Test
    void limitAndJsonFormatAreHonored() {
        when(scraper.trends(TrendCategory.TRENDING)).thenReturn(List.of(trend("a", 1), trend("b", 2), trend("c", 3)));
        assertEquals(ExitCodes.OK, cli.run("trends", "--limit", "2", "--format", "json"));
        String out = cli.out();
        assertTrue(out.strip().startsWith("["), out);
        assertTrue(out.contains("\"a\"") && out.contains("\"b\"") && !out.contains("\"c\""), out);
    }

    @Test
    void invalidCategoryIsUsageError() {
        assertEquals(ExitCodes.USAGE, cli.run("trends", "--category", "politics"));
        verifyNoInteractions(scraper);
    }

    @Test
    void twitterExceptionExitsOne() {
        when(scraper.trends(TrendCategory.TRENDING)).thenThrow(new TwitterException("boom"));
        assertEquals(ExitCodes.RUNTIME, cli.run("trends"));
        assertEquals("error: boom", cli.err().strip());
        assertEquals("", cli.out());
    }
}
