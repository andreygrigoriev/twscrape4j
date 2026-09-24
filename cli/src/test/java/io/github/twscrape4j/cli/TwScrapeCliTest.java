package io.github.twscrape4j.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;
import org.slf4j.simple.SimpleLoggerFactory;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwScrapeCliTest {

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        ScraperFactory factory = () -> {
            throw new AssertionError("scraper must not be opened");
        };
        return TwScrapeCli.newCommandLine(factory, new PrintWriter(out, true), new PrintWriter(err, true))
                .execute(args);
    }

    @AfterEach
    void clearVerboseProperty() {
        System.clearProperty(TwScrapeCli.VERBOSE_LOG_PROPERTY);
    }

    // ---- root command ----

    @Test
    void helpExitsZeroAndPrintsUsage() {
        assertEquals(0, run("--help"));
        assertTrue(out.toString().contains("Usage: twscrape"), out.toString());
        assertTrue(out.toString().contains("--verbose"), out.toString());
    }

    @Test
    void versionPrintsProjectVersion() {
        String expected = System.getProperty("project.version");
        assertEquals(0, run("--version"));
        assertEquals("twscrape " + expected, out.toString().strip());
        assertFalse(out.toString().contains("${"), "version.properties must be Maven-filtered");
    }

    @Test
    void unknownSubcommandExitsTwoWithMessageOnStderr() {
        assertEquals(2, run("no-such-command"));
        assertTrue(err.toString().contains("no-such-command"), err.toString());
        assertEquals("", out.toString());
    }

    @Test
    void noSubcommandIsUsageError() {
        assertEquals(2, run());
        assertTrue(err.toString().contains("Missing required subcommand"), err.toString());
        assertEquals("", out.toString());
    }

    @Test
    void verboseFlagIsAcceptedByRootCommand() {
        assertEquals(0, run("-vh"));
        assertTrue(out.toString().contains("Usage: twscrape"));
    }

    // ---- dependency exclusions ----

    @ParameterizedTest
    @ValueSource(strings = {"org.sqlite.JDBC", "org.jooq.DSLContext", "org.conscrypt.Conscrypt"})
    void excludedDependenciesAreNotOnClasspath(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }

    // ---- -v pre-scan ----

    @Test
    void preScanDetectsShortVerbose() {
        assertTrue(TwScrapeCli.isVerbose(new String[]{"-v", "search", "java"}));
    }

    @Test
    void preScanDetectsLongVerbose() {
        assertTrue(TwScrapeCli.isVerbose(new String[]{"search", "--verbose", "java"}));
    }

    @Test
    void preScanDetectsClusteredVerbose() {
        assertTrue(TwScrapeCli.isVerbose(new String[]{"-vh"}));
        assertTrue(TwScrapeCli.isVerbose(new String[]{"-hv"}));
    }

    @Test
    void preScanStopsAtDoubleDash() {
        assertFalse(TwScrapeCli.isVerbose(new String[]{"search", "--", "-v"}));
    }

    @Test
    void preScanIgnoresOtherArguments() {
        assertFalse(TwScrapeCli.isVerbose(new String[]{"search", "java", "--limit", "5"}));
        assertFalse(TwScrapeCli.isVerbose(new String[]{"search", "-vx"}));
        assertFalse(TwScrapeCli.isVerbose(new String[]{}));
    }

    // ---- logging ----

    @Test
    void slf4jBindingIsSimpleLoggerNotNop() {
        var factory = LoggerFactory.getILoggerFactory();
        assertFalse(factory instanceof NOPLoggerFactory);
        assertInstanceOf(SimpleLoggerFactory.class, factory);
    }

    @Test
    void conscryptFallbackWarningIsNotPrinted() throws Exception {
        PrintStream original = System.err;
        var captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            assertEquals(0, run("--help"));
            // static initializer tries Conscrypt, which is excluded, and logs the fallback at WARN
            Class.forName("io.github.twscrape4j.http.HttpClientFactory", true, getClass().getClassLoader());
        } finally {
            System.setErr(original);
        }
        assertFalse(captured.toString(StandardCharsets.UTF_8).contains("Conscrypt"), captured.toString());
        assertFalse(LoggerFactory.getLogger("io.github.twscrape4j.http.HttpClientFactory").isWarnEnabled());
    }

    @Test
    void defaultLevelIsWarn() {
        var log = LoggerFactory.getLogger("io.github.twscrape4j.test.DefaultLevel");
        assertTrue(log.isWarnEnabled());
        assertFalse(log.isInfoEnabled());
    }

    @Test
    void verboseRaisesOnlyLibraryLoggersToDebug() {
        // what main() does for -v; slf4j-simple resolves levels when each logger is first created
        System.setProperty(TwScrapeCli.VERBOSE_LOG_PROPERTY, "debug");
        assertTrue(LoggerFactory.getLogger("io.github.twscrape4j.test.Verbose").isDebugEnabled());
        assertFalse(LoggerFactory.getLogger("org.apache.hc.client5.http.headers.VerboseTest").isDebugEnabled());
        assertFalse(LoggerFactory.getLogger("org.apache.hc.client5.http.wire.VerboseTest").isDebugEnabled());
    }

    @Test
    void newCommandLineUsesGivenWriters() {
        CommandLine cmd = TwScrapeCli.newCommandLine(() -> null, new PrintWriter(out), new PrintWriter(err));
        assertEquals("twscrape", cmd.getCommandName());
    }
}
