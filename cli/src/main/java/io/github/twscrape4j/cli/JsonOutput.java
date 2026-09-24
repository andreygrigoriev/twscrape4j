package io.github.twscrape4j.cli;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

/**
 * Writes results to stdout. JSONL writes and flushes one compact line per item so pipes see results
 * live; JSON buffers the whole stream first so stdout never holds a truncated array.
 *
 * <p>{@link PrintWriter} swallows I/O errors, so every flush is followed by {@link PrintWriter#checkError()}:
 * a closed pipe or a full disk stops consuming the stream (so pagination halts) and fails the command.
 */
public final class JsonOutput {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final ObjectWriter PRETTY = MAPPER.writerWithDefaultPrettyPrinter();

    private final PrintWriter out;
    private final OutputOptions.Format format;
    private final int limit;

    /** @param limit maximum number of stream items, or {@link OutputOptions#UNLIMITED} */
    public JsonOutput(PrintWriter out, OutputOptions.Format format, int limit) {
        this.out = out;
        this.format = format;
        this.limit = limit;
    }

    /** Writes up to {@code limit} items; the stream is cut lazily so pagination stops early. Closes {@code items}. */
    public void writeStream(Stream<? extends JsonNode> items) {
        try (Stream<? extends JsonNode> limited = limit == OutputOptions.UNLIMITED ? items : items.limit(limit)) {
            if (format == OutputOptions.Format.JSONL) {
                limited.forEachOrdered(item -> {
                    out.println(MAPPER.writeValueAsString(item));
                    flushOrFail();
                });
                return;
            }
            ArrayNode array = MAPPER.createArrayNode();
            limited.forEachOrdered(array::add);
            out.println(array.isEmpty() ? "[]" : PRETTY.writeValueAsString(array));
            flushOrFail();
        }
    }

    /** Writes a single result: a compact line in JSONL mode, a pretty object in JSON mode. */
    public void writeSingle(JsonNode item) {
        out.println(format == OutputOptions.Format.JSONL
                ? MAPPER.writeValueAsString(item)
                : PRETTY.writeValueAsString(item));
        flushOrFail();
    }

    private void flushOrFail() {
        // checkError() flushes, then reports any error the writer swallowed so far
        if (out.checkError()) {
            throw new UncheckedIOException(new IOException("cannot write to stdout (closed pipe or full disk?)"));
        }
    }
}
