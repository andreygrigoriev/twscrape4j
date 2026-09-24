package io.github.twscrape4j.cli;

/** Process exit codes of the {@code twscrape} command. */
public final class ExitCodes {

    /** Success, including an empty stream. */
    public static final int OK = 0;
    /** Runtime or API error ({@code TwitterException}, I/O). */
    public static final int RUNTIME = 1;
    /** Usage error (bad arguments); picocli's default for parameter errors. */
    public static final int USAGE = 2;
    /** Configuration or authentication error. */
    public static final int CONFIG = 3;
    /** A single-result command found nothing, or a {@code @login} could not be resolved. */
    public static final int NOT_FOUND = 4;

    private ExitCodes() {
    }
}
