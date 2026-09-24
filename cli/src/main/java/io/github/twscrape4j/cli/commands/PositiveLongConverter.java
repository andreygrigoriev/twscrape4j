package io.github.twscrape4j.cli.commands;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

import java.util.regex.Pattern;

/** Parses a positive {@code long} ID (tweet, user or list ID); anything else is a usage error (exit 2). */
public final class PositiveLongConverter implements ITypeConverter<Long> {

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    @Override
    public Long convert(String value) {
        return parse(value, "ID");
    }

    /**
     * Parses {@code value} as a positive {@code long}; {@code label} names the value in the error message
     * (e.g. {@code "user ID"}).
     *
     * @throws TypeConversionException if {@code value} is not a positive number or does not fit a {@code long}
     */
    public static long parse(String value, String label) {
        String v = value.strip();
        String invalid = "'" + value + "' is not a valid " + label;
        long id;
        try {
            id = Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new TypeConversionException(invalid + (DIGITS.matcher(v).matches()
                    ? " (number too large)" : " (expected a positive number)"));
        }
        if (id <= 0) {
            throw new TypeConversionException(invalid + " (expected a positive number)");
        }
        return id;
    }
}
