package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.TrendCategory;
import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.JsonOutput;
import io.github.twscrape4j.cli.ModelJson;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** {@code twscrape trends}: current trends of a category. */
@Command(name = "trends", mixinStandardHelpOptions = true, description = "List trends.")
public class TrendsCommand extends DataCommand {

    @Option(names = "--category", defaultValue = "trending", paramLabel = "CATEGORY",
            description = "Trend category: news, sport, entertainment or trending. Default: trending.")
    TrendCategory category = TrendCategory.TRENDING;

    @Override
    protected void run(TwScrape scraper, JsonOutput out) {
        emitStream(() -> scraper.trends(category).stream(), ModelJson::trend,
                () -> scraper.trendsRaw(category).stream());
    }
}
