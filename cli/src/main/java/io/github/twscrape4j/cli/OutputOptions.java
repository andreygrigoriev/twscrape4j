package io.github.twscrape4j.cli;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

/** Output options shared by every data command (picocli mixin). */
public class OutputOptions {

    /** Output format: one compact object per line, or a pretty-printed JSON document. */
    public enum Format { JSONL, JSON }

    /** {@code --limit} value meaning "no limit". */
    public static final int UNLIMITED = -1;

    /** Default {@code --limit}. */
    public static final int DEFAULT_LIMIT = 20;

    @Spec(Spec.Target.MIXEE)
    CommandSpec mixee;

    @Option(names = "--format", defaultValue = "jsonl", paramLabel = "FORMAT",
            description = "Output format: jsonl (one object per line) or json (pretty array/object). Default: ${DEFAULT-VALUE}.")
    Format format = Format.JSONL;

    private int limit = DEFAULT_LIMIT;

    @Option(names = "--raw", description = "Print the raw GraphQL JSON instead of the mapped model.")
    boolean raw;

    @Option(names = "--limit", defaultValue = "" + DEFAULT_LIMIT, paramLabel = "N",
            description = "Maximum number of items; -1 means no limit. Default: ${DEFAULT-VALUE}.")
    void setLimit(int limit) {
        if (limit != UNLIMITED && limit < 1) {
            throw new ParameterException(mixee.commandLine(),
                    "--limit must be -1 (no limit) or at least 1, but was " + limit);
        }
        this.limit = limit;
    }

    public Format format() {
        return format;
    }

    public int limit() {
        return limit;
    }

    public boolean raw() {
        return raw;
    }
}
