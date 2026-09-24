package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.SearchMode;
import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.ModelJson;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** {@code twscrape search <query>}: tweets matching a search query. */
@Command(name = "search", mixinStandardHelpOptions = true, description = "Search tweets.")
public class SearchCommand extends DataCommand {

    @Parameters(index = "0", paramLabel = "QUERY", converter = NonBlankConverter.class,
            description = "Search query (Twitter search syntax).")
    String query;

    @Option(names = "--mode", defaultValue = "latest", paramLabel = "MODE",
            description = "Search mode: top, latest or media. Default: latest.")
    SearchMode mode = SearchMode.LATEST;

    @Override
    protected void run(TwScrape scraper) {
        emitStream(() -> scraper.search(query, mode), ModelJson::tweet, () -> scraper.searchRaw(query, mode));
    }
}
