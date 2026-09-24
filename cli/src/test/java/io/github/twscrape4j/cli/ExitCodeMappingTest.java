package io.github.twscrape4j.cli;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.commands.DataCommand;
import io.github.twscrape4j.http.TwitterException;
import io.github.twscrape4j.api.SearchMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExitCodeMappingTest {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();
    private final TwScrape scraper = mock(TwScrape.class);

    /** Test command whose behaviour is supplied per test. */
    @Command(name = "stub")
    static class StubCommand extends DataCommand {
        private final Consumer<StubCommand> action;
        TwScrape scraper;

        StubCommand(Consumer<StubCommand> action) {
            this.action = action;
        }

        @Override
        protected void run(TwScrape scraper, JsonOutput out) {
            this.scraper = scraper;
            action.accept(this);
        }

        void stream() {
            emitStream(() -> scraper.search("q", SearchMode.LATEST),
                    t -> NODES.objectNode().put("id", Long.toString(t.id())),
                    () -> scraper.searchRaw("q", SearchMode.LATEST));
        }

        void single() {
            emitOptional(() -> scraper.userById(42), ModelJson::user,
                    () -> scraper.userByIdRaw(42), "user 42 not found");
        }
    }

    @AfterEach
    void clearVerboseProperty() {
        System.clearProperty(TwScrapeCli.VERBOSE_LOG_PROPERTY);
    }

    private int run(ScraperFactory factory, Consumer<StubCommand> action, String... args) {
        var outWriter = new PrintWriter(out, true);
        var errWriter = new PrintWriter(err, true);
        CommandLine cmd = TwScrapeCli.newCommandLine(factory, outWriter, errWriter);
        cmd.addSubcommand(new StubCommand(action));
        return TwScrapeCli.configure(cmd, outWriter, errWriter).execute(args);
    }

    private int run(Consumer<StubCommand> action, String... args) {
        return run(() -> scraper, action, args);
    }

    private static Consumer<StubCommand> throwing(RuntimeException e) {
        return c -> {
            throw e;
        };
    }

    // ---- exit codes ----

    @Test
    void successExitsZeroAndClosesScraper() {
        assertEquals(ExitCodes.OK, run(c -> c.scraper.accounts(), "stub"));
        verify(scraper).close();
        assertEquals("", err.toString());
    }

    @Test
    void configErrorExitsThree() {
        assertEquals(ExitCodes.CONFIG, run(throwing(new CliConfigException("no account configured")), "stub"));
        assertEquals("error: no account configured", err.toString().strip());
        assertEquals("", out.toString());
    }

    @Test
    void configErrorFromFactoryExitsThree() {
        ScraperFactory failing = () -> {
            throw new CliConfigException("set TWSCRAPE_AUTH_TOKEN");
        };
        assertEquals(ExitCodes.CONFIG, run(failing, c -> {
            throw new AssertionError("must not run");
        }, "stub"));
        assertEquals("error: set TWSCRAPE_AUTH_TOKEN", err.toString().strip());
        assertEquals("", out.toString());
    }

    @Test
    void notFoundExitsFour() {
        assertEquals(ExitCodes.NOT_FOUND, run(throwing(new NotFoundException("tweet 1 not found")), "stub"));
        assertEquals("error: tweet 1 not found", err.toString().strip());
        assertEquals("", out.toString());
    }

    @Test
    void twitterExceptionExitsOneAndClosesScraper() {
        var e = new TwitterException.TwitterApiException(500, "boom");
        assertEquals(ExitCodes.RUNTIME, run(throwing(e), "stub"));
        assertEquals("error: Twitter API error 500: boom", err.toString().strip());
        assertEquals("", out.toString());
        verify(scraper).close();
    }

    @Test
    void otherExceptionExitsOne() {
        var e = new UncheckedIOException(new java.io.IOException("connection reset"));
        assertEquals(ExitCodes.RUNTIME, run(throwing(e), "stub"));
        assertTrue(err.toString().startsWith("error: "), err.toString());
        assertTrue(err.toString().contains("connection reset"), err.toString());
        assertEquals("", out.toString());
    }

    @Test
    void exceptionWithoutMessageReportsClassName() {
        assertEquals(ExitCodes.RUNTIME, run(throwing(new IllegalStateException()), "stub"));
        assertEquals("error: java.lang.IllegalStateException", err.toString().strip());
    }

    @Test
    void usageErrorStillExitsTwo() {
        assertEquals(ExitCodes.USAGE, run(c -> {
            throw new AssertionError("must not run");
        }, "stub", "--limit", "0"));
        assertTrue(err.toString().contains("--limit"), err.toString());
        verifyNoInteractions(scraper);
    }

    // ---- stack traces ----

    @Test
    void noStackTraceWithoutVerbose() {
        run(throwing(new TwitterException("boom")), "stub");
        assertEquals(1, err.toString().strip().lines().count(), err.toString());
    }

    @Test
    void stackTraceWithRootVerbose() {
        run(throwing(new TwitterException("boom")), "-v", "stub");
        assertTrue(err.toString().startsWith("error: boom"), err.toString());
        assertTrue(err.toString().contains("\tat "), err.toString());
    }

    @Test
    void stackTraceWithVerboseAfterSubcommand() {
        run(throwing(new TwitterException("boom")), "stub", "--verbose");
        assertTrue(err.toString().contains("\tat "), err.toString());
    }

    // ---- emit helpers ----

    @Test
    void emitStreamMapsTypedItems() {
        var tweet = mock(io.github.twscrape4j.models.Tweet.class);
        when(tweet.id()).thenReturn(7L);
        when(scraper.search("q", SearchMode.LATEST)).thenReturn(Stream.of(tweet, tweet));
        assertEquals(0, run(StubCommand::stream, "stub"));
        assertEquals("{\"id\":\"7\"}\n{\"id\":\"7\"}\n", out.toString().replace("\r\n", "\n"));
        verify(scraper, never()).searchRaw("q", SearchMode.LATEST);
    }

    @Test
    void emitStreamRawUsesRawSupplierOnly() {
        JsonNode raw = NODES.objectNode().put("rest_id", "9");
        when(scraper.searchRaw("q", SearchMode.LATEST)).thenReturn(Stream.of(raw));
        assertEquals(0, run(StubCommand::stream, "stub", "--raw"));
        assertEquals("{\"rest_id\":\"9\"}", out.toString().strip());
        verify(scraper, never()).search("q", SearchMode.LATEST);
    }

    @Test
    void emitOptionalWritesSingleResult() {
        JsonNode raw = NODES.objectNode().put("rest_id", "42");
        when(scraper.userByIdRaw(42)).thenReturn(Optional.of(raw));
        assertEquals(0, run(StubCommand::single, "stub", "--raw", "--format", "json"));
        assertTrue(out.toString().contains("\"rest_id\" : \"42\""), out.toString());
        verify(scraper, never()).userById(42);
    }

    @Test
    void emitOptionalEmptyExitsFour() {
        when(scraper.userById(42)).thenReturn(Optional.empty());
        assertEquals(ExitCodes.NOT_FOUND, run(StubCommand::single, "stub"));
        assertEquals("error: user 42 not found", err.toString().strip());
        assertEquals("", out.toString());
        verify(scraper).close();
    }

    @Test
    void jsonModeMidStreamErrorLeavesStdoutEmpty() {
        var tweet = mock(io.github.twscrape4j.models.Tweet.class);
        when(scraper.search("q", SearchMode.LATEST)).thenReturn(Stream.concat(Stream.of(tweet),
                Stream.generate(() -> {
                    throw new TwitterException("page 2 failed");
                })));
        assertEquals(ExitCodes.RUNTIME, run(StubCommand::stream, "stub", "--format", "json"));
        assertEquals("", out.toString());
        assertFalse(err.toString().isBlank());
    }
}
