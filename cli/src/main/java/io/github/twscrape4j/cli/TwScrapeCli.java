package io.github.twscrape4j.cli;

import io.github.twscrape4j.cli.commands.ListCommands;
import io.github.twscrape4j.cli.commands.SearchCommand;
import io.github.twscrape4j.cli.commands.TrendsCommand;
import io.github.twscrape4j.cli.commands.TweetCommands;
import io.github.twscrape4j.cli.commands.UserCommands;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * Root {@code twscrape} command. Results go to stdout; logs and errors go to stderr.
 *
 * <p>Subcommands are declared in the annotation so they exist before {@code configure(...)} propagates
 * settings to them.
 */
@Command(name = "twscrape", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
        description = "Stateless Twitter/X scraper. Writes results to stdout as JSON Lines or JSON.",
        subcommands = {
                SearchCommand.class,
                TrendsCommand.class,
                TweetCommands.TweetCommand.class,
                TweetCommands.RepliesCommand.class,
                TweetCommands.RetweetersCommand.class,
                UserCommands.UserCommand.class,
                UserCommands.UserByIdCommand.class,
                UserCommands.TweetsCommand.class,
                UserCommands.MediaCommand.class,
                UserCommands.FollowersCommand.class,
                UserCommands.FollowingCommand.class,
                ListCommands.ListTimelineCommand.class,
                ListCommands.ListMembersCommand.class,
        })
public class TwScrapeCli implements Callable<Integer> {

    /** slf4j-simple level key for the library's own loggers; httpclient stays quiet to avoid leaking cookies. */
    static final String VERBOSE_LOG_PROPERTY = "org.slf4j.simpleLogger.log.io.github.twscrape4j";
    /** Overrides the {@code error} level {@code simplelogger.properties} sets for {@code HttpClientFactory}. */
    static final String HTTP_CLIENT_FACTORY_LOG_PROPERTY = VERBOSE_LOG_PROPERTY + ".http.HttpClientFactory";

    /** Longest error message printed; longer ones (e.g. an HTML response body) are cut. */
    static final int MAX_ERROR_LENGTH = 500;
    private static final Pattern LINE_BREAKS = Pattern.compile("\\s*\\R\\s*");

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
        applyVerbosity(args);
        // FileOutputStream instead of System.out: PrintStream hides write errors (closed pipe, full disk)
        // from the PrintWriter, which JsonOutput checks after every flush
        var out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(FileDescriptor.out),
                StandardCharsets.UTF_8), true);
        var err = new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true);
        ScraperFactory factory = new DefaultScraperFactory(System.getenv(), System.console());
        System.exit(execute(newCommandLine(factory, out, err), args));
    }

    /**
     * Runs {@code cmd}; picocli hands only {@link Exception}s to the execution exception handler, so an
     * {@link Error} (e.g. {@code NoClassDefFoundError}, {@code OutOfMemoryError}) is reported here the same way.
     */
    static int execute(CommandLine cmd, String[] args) {
        try {
            return cmd.execute(args);
        } catch (Throwable t) {
            report(cmd, t);
            return ExitCodes.RUNTIME;
        }
    }

    /**
     * With {@code -v}, raises the library loggers to debug, including {@code HttpClientFactory}, which the
     * logging config otherwise keeps at error to hide the expected Conscrypt fallback warning.
     */
    static void applyVerbosity(String[] args) {
        if (isVerbose(args)) {
            System.setProperty(VERBOSE_LOG_PROPERTY, "debug");
            System.setProperty(HTTP_CLIENT_FACTORY_LOG_PROPERTY, "debug");
        }
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
        // mixinStandardHelpOptions gives every subcommand -V/--version; make it print the real version
        cmd.getSubcommands().values().forEach(sub -> sub.getCommandSpec().versionProvider(new VersionProvider()));
        return cmd;
    }

    /**
     * Maps exceptions thrown by commands to exit codes and writes a one-line {@code error: <message>} to stderr,
     * plus the stack trace with {@code -v}. Nothing is written to stdout.
     *
     * @see IExecutionExceptionHandler
     */
    static int handleExecutionException(Exception ex, CommandLine cmd, ParseResult parseResult) {
        report(cmd, ex);
        return exitCode(ex);
    }

    /** Writes {@code error: <message>} to {@code cmd}'s stderr, plus the stack trace when the root has {@code -v}. */
    private static void report(CommandLine cmd, Throwable t) {
        PrintWriter err = cmd.getErr();
        err.println("error: " + oneLine(t));
        if (cmd.getCommandSpec().root().userObject() instanceof TwScrapeCli root && root.verbose) {
            t.printStackTrace(err);
        }
        err.flush();
    }

    /** The message of {@code t} (or its class name) on a single line, at most {@link #MAX_ERROR_LENGTH} chars. */
    static String oneLine(Throwable t) {
        String message = t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : t.getClass().getName();
        message = LINE_BREAKS.matcher(message.strip()).replaceAll(" ");
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH) + "...";
    }

    static int exitCode(Exception ex) {
        return switch (ex) {
            case CliConfigException _ -> ExitCodes.CONFIG;
            case NotFoundException _ -> ExitCodes.NOT_FOUND;
            // TwitterException, I/O and anything unexpected
            default -> ExitCodes.RUNTIME;
        };
    }

    /**
     * Detects {@code -v}/{@code --verbose} (also clustered, e.g. {@code -vh}, or {@code --verbose=true})
     * before {@code --}.
     */
    static boolean isVerbose(String[] args) {
        for (String arg : args) {
            if ("--".equals(arg)) {
                return false;
            }
            if ("--verbose".equals(arg) || CLUSTERED_VERBOSE.matcher(arg).matches()
                    || arg.startsWith("--verbose=") && !"--verbose=false".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
