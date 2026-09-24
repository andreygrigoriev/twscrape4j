package io.github.twscrape4j.cli;

import io.github.twscrape4j.api.TwScrape;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Runs the real {@code twscrape} command line against a given {@link TwScrape} and captures stdout/stderr. */
public final class CliHarness {

    private final TwScrape scraper;
    private StringWriter out = new StringWriter();
    private StringWriter err = new StringWriter();

    public CliHarness(TwScrape scraper) {
        this.scraper = scraper;
    }

    /** Executes {@code args} and returns the exit code; output of previous runs is discarded. */
    public int run(String... args) {
        out = new StringWriter();
        err = new StringWriter();
        return TwScrapeCli.newCommandLine(() -> scraper, new PrintWriter(out, true), new PrintWriter(err, true))
                .execute(args);
    }

    /** Captured stdout with line endings normalized to {@code \n}. */
    public String out() {
        return out.toString().replace("\r\n", "\n");
    }

    public String err() {
        return err.toString();
    }
}
