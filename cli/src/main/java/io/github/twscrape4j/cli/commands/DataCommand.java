package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.JsonOutput;
import io.github.twscrape4j.cli.NotFoundException;
import io.github.twscrape4j.cli.OutputOptions;
import io.github.twscrape4j.cli.ScraperFactory;
import io.github.twscrape4j.cli.TwScrapeCli;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Base class for commands that fetch data: opens a {@link TwScrape} from the root command's {@link ScraperFactory},
 * runs the operation and closes the scraper. Exceptions propagate to the root's exit-code mapping.
 */
public abstract class DataCommand implements Callable<Integer> {

    @Spec
    protected CommandSpec spec;

    @Mixin
    protected OutputOptions output = new OutputOptions();

    private JsonOutput json;

    @Override
    public Integer call() {
        json = new JsonOutput(spec.commandLine().getOut(), output);
        try (TwScrape scraper = scraperFactory().open()) {
            run(scraper, json);
        }
        return 0;
    }

    /** Performs the operation and writes its results to {@code out}. */
    protected abstract void run(TwScrape scraper, JsonOutput out);

    /**
     * Writes a stream (or list) result: the typed items mapped by {@code mapper}, or the raw GraphQL nodes with
     * {@code --raw}. Only the chosen supplier is invoked, so the other API call is never made.
     */
    protected <T> void emitStream(Supplier<? extends Stream<T>> typed, Function<? super T, ? extends JsonNode> mapper,
                                  Supplier<? extends Stream<JsonNode>> raw) {
        json.writeStream(output.raw() ? raw.get() : typed.get().map(mapper));
    }

    /**
     * Writes a single result like {@link #emitStream}; an empty result throws {@link NotFoundException}
     * with {@code notFoundMessage}.
     */
    protected <T> void emitOptional(Supplier<Optional<T>> typed, Function<? super T, ? extends JsonNode> mapper,
                                    Supplier<Optional<JsonNode>> raw, String notFoundMessage) {
        Optional<? extends JsonNode> result = output.raw() ? raw.get() : typed.get().map(mapper);
        json.writeSingle(result.orElseThrow(() -> new NotFoundException(notFoundMessage)));
    }

    private ScraperFactory scraperFactory() {
        if (spec.root().userObject() instanceof TwScrapeCli root) {
            return root.scraperFactory();
        }
        throw new IllegalStateException("data command " + spec.name() + " is not attached to the twscrape root command");
    }
}
