package io.github.twscrape4j.cli;

import io.github.twscrape4j.api.TwScrape;

/** Supplies a ready-to-use {@link TwScrape} for a single CLI run; injectable so tests can pass a mock. */
@FunctionalInterface
public interface ScraperFactory {

    /** Opens a scraper with its account configured. The caller closes it. */
    TwScrape open();
}
