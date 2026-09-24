package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.JsonOutput;
import io.github.twscrape4j.cli.ModelJson;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Commands that operate on a list: {@code list-timeline} and {@code list-members}. */
public final class ListCommands {

    private ListCommands() {
    }

    /** Shared {@code <listId>} parameter. */
    abstract static class ListIdCommand extends DataCommand {
        @Parameters(index = "0", paramLabel = "LIST_ID", converter = PositiveLongConverter.class,
                description = "List ID (a positive number).")
        long listId;
    }

    /** {@code twscrape list-timeline <listId>}: tweets of a list. */
    @Command(name = "list-timeline", mixinStandardHelpOptions = true, description = "List tweets of a list.")
    public static class ListTimelineCommand extends ListIdCommand {
        @Override
        protected void run(TwScrape scraper, JsonOutput out) {
            emitStream(() -> scraper.listTimeline(listId), ModelJson::tweet, () -> scraper.listTimelineRaw(listId));
        }
    }

    /** {@code twscrape list-members <listId>}: members of a list. */
    @Command(name = "list-members", mixinStandardHelpOptions = true, description = "List members of a list.")
    public static class ListMembersCommand extends ListIdCommand {
        @Override
        protected void run(TwScrape scraper, JsonOutput out) {
            emitStream(() -> scraper.listMembers(listId), ModelJson::user, () -> scraper.listMembersRaw(listId));
        }
    }
}
