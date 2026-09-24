package io.github.twscrape4j.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.ScopeType;
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

    @Option(names = {"-v", "--verbose"}, scope = ScopeType.INHERIT,
            description = "Debug logging for twscrape4j to stderr; adds stack traces to errors.")
    boolean verbose;

    TwScrapeCli(ScraperFactory scraperFactory) {
        this.scraperFactory = scraperFactory;
    }

    /** The factory data commands use to open their {@code TwScrape}. */
    public ScraperFactory scraperFactory() {
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
        return configure(new CommandLine(new TwScrapeCli(factory)), out, err);
    }

    /**
     * Applies writers, enum parsing and exit-code mapping to {@code cmd} and its subcommands. picocli only
     * propagates these to subcommands registered at the time of the call, so run it after adding subcommands.
     */
    static CommandLine configure(CommandLine cmd, PrintWriter out, PrintWriter err) {
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.setOut(out);
        cmd.setErr(err);
        cmd.setExecutionExceptionHandler(TwScrapeCli::handleExecutionException);
        return cmd;
    }

    /**
     * Maps exceptions thrown by commands to exit codes and writes a one-line {@code error: <message>} to stderr,
     * plus the stack trace with {@code -v}. Nothing is written to stdout.
     *
     * @see IExecutionExceptionHandler
     */
    static int handleExecutionException(Exception ex, CommandLine cmd, ParseResult parseResult) {
        PrintWriter err = cmd.getErr();
        String message = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage() : ex.getClass().getName();
        err.println("error: " + message);
        if (cmd.getCommandSpec().root().userObject() instanceof TwScrapeCli root && root.verbose) {
            ex.printStackTrace(err);
        }
        err.flush();
        return exitCode(ex);
    }

    static int exitCode(Exception ex) {
        return switch (ex) {
            case CliConfigException _ -> ExitCodes.CONFIG;
            case NotFoundException _ -> ExitCodes.NOT_FOUND;
            // TwitterException, I/O and anything unexpected
            default -> ExitCodes.RUNTIME;
        };
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
