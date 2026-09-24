package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.models.Trend;
import io.github.twscrape4j.models.Tweet;
import io.github.twscrape4j.models.User;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/** Model instances shared by command tests. */
final class Fixtures {

    private Fixtures() {
    }

    static User user(long id, String username) {
        return new User(id, username, null, null, 1, 2, false, null, null);
    }

    static Tweet tweet(long id) {
        return new Tweet(id, "t" + id, null, null, null, null, 0L, null);
    }

    static Trend trend(String name, long count) {
        return new Trend(name, count, null);
    }

    static JsonNode raw(String restId) {
        return JsonNodeFactory.instance.objectNode().put("rest_id", restId);
    }
}
