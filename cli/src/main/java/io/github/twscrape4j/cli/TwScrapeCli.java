package io.github.twscrape4j.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/** Root {@code twscrape} command. Results go to stdout; logs and errors go to stderr. */
@Command(name = "twscrape", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
        description = "Stateless Twitter/X scraper. Writes results to stdout as JSON Lines or JSON.")
public class TwScrapeCli implements Callable<Integer> {

    /** slf4j-simple level key for the library's own loggers; httpclient stays quiet to avoid leaking cookies. */
    static final String VERBOSE_LOG_PROPERTY = "org.slf4j.simpleLogger.log.io.github.twscrape4j";

    /** Clustered short flags that contain {@code -v}, e.g. {@code -vh}; limited to the root's known short options. */
    private static final Pattern CLUSTERED_VERBOSE = Pattern.compile("-[hvV]*v[hvV]*");

    private final ScraperFactory scraperFactory;

    @Spec
    CommandSpec spec;

    @Option(names = {"-v", "--verbose"}, description = "Debug logging for twscrape4j to stderr.")
    boolean verbose;

    TwScrapeCli(ScraperFactory scraperFactory) {
        this.scraperFactory = scraperFactory;
    }

    ScraperFactory scraperFactory() {
        return scraperFactory;
    }

    @Override
    public Integer call() {
        throw new ParameterException(spec.commandLine(), "Missing required subcommand");
    }

    public static void main(String[] args) {
        // must run before any logger initializes: slf4j-simple reads levels when loggers are created
        if (isVerbose(args)) {
            System.setProperty(VERBOSE_LOG_PROPERTY, "debug");
        }
        var out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        var err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        ScraperFactory factory = new DefaultScraperFactory(System.getenv(), System.console());
        System.exit(newCommandLine(factory, out, err).execute(args));
    }

    /** Builds the configured command line; tests pass their own factory and writers. */
    static CommandLine newCommandLine(ScraperFactory factory, PrintWriter out, PrintWriter err) {
        var cmd = new CommandLine(new TwScrapeCli(factory));
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.setOut(out);
        cmd.setErr(err);
        return cmd;
    }

    /** Detects {@code -v}/{@code --verbose} (also clustered, e.g. {@code -vh}) before {@code --}. */
    static boolean isVerbose(String[] args) {
        for (String arg : args) {
            if ("--".equals(arg)) {
                return false;
            }
            if ("--verbose".equals(arg) || CLUSTERED_VERBOSE.matcher(arg).matches()) {
                return true;
            }
        }
        return false;
    }
}
