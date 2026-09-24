package io.github.twscrape4j.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLoggerFactory;
import org.slf4j.simple.SimpleLoggerFactory;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

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
        System.clearProperty(TwScrapeCli.HTTP_CLIENT_FACTORY_LOG_PROPERTY);
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

    /**
     * Checks the cli module's resolved dependencies (the Maven test classpath). The shaded {@code -all.jar} is built
     * from the same runtime dependencies, and the Docker build smoke-tests the native binary built from them.
     */
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
    void preScanDetectsVerboseWithAttachedValue() {
        assertTrue(TwScrapeCli.isVerbose(new String[]{"search", "--verbose=true", "java"}));
        assertFalse(TwScrapeCli.isVerbose(new String[]{"search", "--verbose=false", "java"}));
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
    void conscryptFallbackWarningIsNotPrinted() {
        // HttpClientFactory's static initializer logs the (expected) Conscrypt fallback at WARN; whether it already
        // ran depends on test order, so check the logger level that hides it instead of capturing stderr
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
        // slf4j-simple resolves levels when each logger is first created, so use fresh logger names
        TwScrapeCli.applyVerbosity(new String[]{"search", "-v", "java"});
        assertTrue(LoggerFactory.getLogger("io.github.twscrape4j.test.Verbose").isDebugEnabled());
        assertTrue(LoggerFactory.getLogger("io.github.twscrape4j.http.HttpClientFactory.VerboseTest").isDebugEnabled());
        assertFalse(LoggerFactory.getLogger("org.apache.hc.client5.http.headers.VerboseTest").isDebugEnabled());
        assertFalse(LoggerFactory.getLogger("org.apache.hc.client5.http.wire.VerboseTest").isDebugEnabled());
    }

    @Test
    void withoutVerboseLevelsStayUnchanged() {
        TwScrapeCli.applyVerbosity(new String[]{"search", "java"});
        assertEquals(null, System.getProperty(TwScrapeCli.VERBOSE_LOG_PROPERTY));
        assertEquals(null, System.getProperty(TwScrapeCli.HTTP_CLIENT_FACTORY_LOG_PROPERTY));
    }

    @Test
    void newCommandLineUsesGivenWriters() {
        assertEquals(0, run("--help"));
        assertTrue(out.toString().contains("Usage: twscrape"), out.toString());
        assertEquals(2, run("no-such-command"));
        assertTrue(err.toString().contains("no-such-command"), err.toString());
    }

    // ---- subcommand version / error formatting ----

    @ParameterizedTest
    @ValueSource(strings = {"search", "user", "tweets", "list-members", "trends"})
    void subcommandVersionPrintsProjectVersion(String subcommand) {
        assertEquals(0, run(subcommand, "-V"));
        assertEquals("twscrape " + System.getProperty("project.version"), out.toString().strip());
    }

    @Test
    void errorMessageIsCollapsedToOneLine() {
        String message = TwScrapeCli.oneLine(new RuntimeException("Twitter API error 500: <html>\n  <body>\r\nboom\n"));
        assertEquals("Twitter API error 500: <html> <body> boom", message);
    }

    @Test
    void longErrorMessageIsTruncated() {
        String message = TwScrapeCli.oneLine(new RuntimeException("x".repeat(2000)));
        assertEquals(TwScrapeCli.MAX_ERROR_LENGTH + 3, message.length());
        assertTrue(message.endsWith("..."));
    }

    @Test
    void errorEscapingPicocliIsReportedAsRuntimeError() {
        ScraperFactory factory = () -> {
            throw new NoClassDefFoundError("org/sqlite/JDBC");
        };
        CommandLine cmd = TwScrapeCli.newCommandLine(factory, new PrintWriter(out, true), new PrintWriter(err, true));

        assertEquals(ExitCodes.RUNTIME, TwScrapeCli.execute(cmd, new String[]{"trends"}));
        assertEquals("error: org/sqlite/JDBC", err.toString().strip());
        assertEquals("", out.toString());
    }
}
