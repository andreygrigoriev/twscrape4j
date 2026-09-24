package io.github.twscrape4j.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonOutputTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final StringWriter buffer = new StringWriter();

    private JsonOutput output(OutputOptions.Format format, int limit) {
        return new JsonOutput(new PrintWriter(buffer), format, limit);
    }

    private static Stream<JsonNode> items(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> MAPPER.createObjectNode().put("n", i));
    }

    private String[] lines() {
        String text = buffer.toString();
        return text.isEmpty() ? new String[0] : text.split("\n");
    }

    @Test
    void jsonlLineCountEqualsLimit() {
        output(OutputOptions.Format.JSONL, 3).writeStream(items(10));

        String[] lines = lines();
        assertEquals(3, lines.length);
        assertEquals("{\"n\":1}", lines[0]);
        assertEquals("{\"n\":3}", lines[2]);
    }

    @Test
    void unlimitedWritesEverything() {
        output(OutputOptions.Format.JSONL, OutputOptions.UNLIMITED).writeStream(items(50));

        assertEquals(50, lines().length);
    }

    @Test
    void emptyStreamWritesNothingInJsonl() {
        output(OutputOptions.Format.JSONL, 20).writeStream(Stream.empty());

        assertEquals("", buffer.toString());
    }

    @Test
    void emptyStreamWritesEmptyArrayInJson() {
        output(OutputOptions.Format.JSON, 20).writeStream(Stream.empty());

        assertEquals("[]", buffer.toString().strip());
    }

    @Test
    void jsonOutputParsesBackAsArray() {
        output(OutputOptions.Format.JSON, 2).writeStream(items(5));

        JsonNode parsed = MAPPER.readTree(buffer.toString());
        assertTrue(parsed.isArray());
        assertEquals(2, parsed.size());
        assertEquals(2, parsed.get(1).get("n").asInt());
        assertTrue(buffer.toString().contains("\n  "), "expected pretty-printed output");
    }

    @Test
    void streamIsCutLazilyAtLimit() {
        var produced = new AtomicInteger();
        Stream<JsonNode> infinite = Stream.generate(
                () -> MAPPER.createObjectNode().put("n", produced.incrementAndGet()));

        output(OutputOptions.Format.JSONL, 4).writeStream(infinite);

        assertEquals(4, produced.get());
        assertEquals(4, lines().length);
    }

    @Test
    void jsonModeMidStreamErrorWritesNothing() {
        Stream<JsonNode> failing = Stream.concat(items(2),
                Stream.<JsonNode>generate(() -> { throw new IllegalStateException("boom"); }));

        var ex = assertThrows(IllegalStateException.class,
                () -> output(OutputOptions.Format.JSON, OutputOptions.UNLIMITED).writeStream(failing));

        assertEquals("boom", ex.getMessage());
        assertEquals("", buffer.toString());
    }

    @Test
    void jsonlModeMidStreamErrorKeepsEarlierLines() {
        Stream<JsonNode> failing = Stream.concat(items(2),
                Stream.<JsonNode>generate(() -> { throw new IllegalStateException("boom"); }));

        assertThrows(IllegalStateException.class,
                () -> output(OutputOptions.Format.JSONL, OutputOptions.UNLIMITED).writeStream(failing));

        assertEquals(2, lines().length);
        assertEquals("{\"n\":2}", lines()[1]);
    }

    @Test
    void writeSingleCompactInJsonl() {
        output(OutputOptions.Format.JSONL, 20).writeSingle(MAPPER.createObjectNode().put("a", 1));

        assertEquals("{\"a\":1}", buffer.toString().strip());
    }

    @Test
    void writeSinglePrettyInJson() {
        output(OutputOptions.Format.JSON, 20).writeSingle(MAPPER.createObjectNode().put("a", 1));

        JsonNode parsed = MAPPER.readTree(buffer.toString());
        assertTrue(parsed.isObject());
        assertTrue(buffer.toString().contains("\n"));
    }

    // ---- write failures (closed pipe, full disk) ----

    /** A writer that accepts {@code capacity} chars and then fails, like stdout after the reader of a pipe exits. */
    private static final class FailingWriter extends Writer {
        private int capacity;

        FailingWriter(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            if (len > capacity) {
                throw new IOException("Broken pipe");
            }
            capacity -= len;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    @Test
    void jsonlWriteFailureStopsConsumingTheStream() {
        var produced = new AtomicInteger();
        Stream<JsonNode> infinite = Stream.generate(
                () -> MAPPER.createObjectNode().put("n", produced.incrementAndGet()));
        var out = new JsonOutput(new PrintWriter(new FailingWriter(20)), OutputOptions.Format.JSONL,
                OutputOptions.UNLIMITED);

        var ex = assertThrows(UncheckedIOException.class, () -> out.writeStream(infinite));

        assertTrue(ex.getMessage().contains("stdout"), ex.getMessage());
        // {"n":1} plus a line break fits, {"n":2} does not
        assertTrue(produced.get() <= 3, "stream must stop after the failed write, produced " + produced.get());
    }

    @Test
    void jsonWriteFailureIsReported() {
        var out = new JsonOutput(new PrintWriter(new FailingWriter(0)), OutputOptions.Format.JSON, 20);

        assertThrows(UncheckedIOException.class, () -> out.writeStream(items(3)));
    }

    @Test
    void writeSingleFailureIsReported() {
        var out = new JsonOutput(new PrintWriter(new FailingWriter(0)), OutputOptions.Format.JSONL, 20);

        assertThrows(UncheckedIOException.class, () -> out.writeSingle(MAPPER.createObjectNode().put("a", 1)));
    }

    // ---- the source stream is closed (releases pagination resources) ----

    @Test
    void streamIsClosedAfterFullRead() {
        var closed = new AtomicBoolean();
        output(OutputOptions.Format.JSONL, OutputOptions.UNLIMITED).writeStream(items(3).onClose(() -> closed.set(true)));
        assertTrue(closed.get());
    }

    @Test
    void streamIsClosedWhenCutAtLimit() {
        var closed = new AtomicBoolean();
        output(OutputOptions.Format.JSON, 2).writeStream(items(10).onClose(() -> closed.set(true)));
        assertTrue(closed.get());
    }

    @Test
    void streamIsClosedOnMidStreamFailure() {
        var closed = new AtomicBoolean();
        Stream<JsonNode> failing = Stream.concat(items(1),
                Stream.<JsonNode>generate(() -> { throw new IllegalStateException("boom"); }))
                .onClose(() -> closed.set(true));

        assertThrows(IllegalStateException.class,
                () -> output(OutputOptions.Format.JSONL, OutputOptions.UNLIMITED).writeStream(failing));
        assertTrue(closed.get());
    }

    @Command(name = "stub")
    static class StubCommand implements Runnable {
        @Mixin
        OutputOptions opts;

        @Override
        public void run() {
        }
    }

    private static CommandLine stubCommandLine(StubCommand stub, StringWriter err) {
        var cmd = new CommandLine(stub);
        cmd.setCaseInsensitiveEnumValuesAllowed(true);
        cmd.setErr(new PrintWriter(err));
        return cmd;
    }

    @Test
    void mixinDefaults() {
        var stub = new StubCommand();
        assertEquals(0, stubCommandLine(stub, new StringWriter()).execute());

        assertEquals(OutputOptions.Format.JSONL, stub.opts.format());
        assertEquals(20, stub.opts.limit());
        assertEquals(false, stub.opts.raw());
    }

    @Test
    void mixinParsesOptions() {
        var stub = new StubCommand();
        assertEquals(0, stubCommandLine(stub, new StringWriter()).execute("--format", "json", "--limit", "-1", "--raw"));

        assertEquals(OutputOptions.Format.JSON, stub.opts.format());
        assertEquals(OutputOptions.UNLIMITED, stub.opts.limit());
        assertTrue(stub.opts.raw());
    }

    @Test
    void mixinRejectsInvalidLimitWithUsageError() {
        for (String bad : new String[] {"0", "-2"}) {
            var err = new StringWriter();
            int code = stubCommandLine(new StubCommand(), err).execute("--limit", bad);

            assertEquals(2, code);
            assertTrue(err.toString().contains("--limit must be -1"), err.toString());
        }
    }

    @Test
    void mixinRejectsUnknownFormat() {
        assertEquals(2, stubCommandLine(new StubCommand(), new StringWriter()).execute("--format", "xml"));
    }
}
