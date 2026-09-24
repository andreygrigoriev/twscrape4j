package io.github.twscrape4j.cli;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import java.io.PrintWriter;
import java.util.stream.Stream;

/**
 * Writes results to stdout. JSONL writes and flushes one compact line per item so pipes see results
 * live; JSON buffers the whole stream first so stdout never holds a truncated array.
 */
public final class JsonOutput {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final ObjectWriter PRETTY = MAPPER.writerWithDefaultPrettyPrinter();

    private final PrintWriter out;
    private final OutputOptions opts;

    public JsonOutput(PrintWriter out, OutputOptions opts) {
        this.out = out;
        this.opts = opts;
    }

    /** Writes up to {@code --limit} items; the stream is cut lazily so pagination stops early. */
    public void writeStream(Stream<? extends JsonNode> items) {
        try (Stream<? extends JsonNode> limited = opts.limit() == OutputOptions.UNLIMITED
                ? items : items.limit(opts.limit())) {
            if (opts.format() == OutputOptions.Format.JSONL) {
                limited.forEachOrdered(item -> {
                    out.println(MAPPER.writeValueAsString(item));
                    out.flush();
                });
                return;
            }
            ArrayNode array = MAPPER.createArrayNode();
            limited.forEachOrdered(array::add);
            out.println(array.isEmpty() ? "[]" : PRETTY.writeValueAsString(array));
            out.flush();
        }
    }

    /** Writes a single result: a compact line in JSONL mode, a pretty object in JSON mode. */
    public void writeSingle(JsonNode item) {
        out.println(opts.format() == OutputOptions.Format.JSONL
                ? MAPPER.writeValueAsString(item)
                : PRETTY.writeValueAsString(item));
        out.flush();
    }
}
