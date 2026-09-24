package io.github.twscrape4j.cli.commands;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Parses a positive {@code long} ID (tweet, user or list ID); anything else is a usage error (exit 2). */
public final class PositiveLongConverter implements ITypeConverter<Long> {

    @Override
    public Long convert(String value) {
        long id;
        try {
            id = Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            throw new TypeConversionException("'" + value + "' is not a valid ID (expected a positive number)");
        }
        if (id <= 0) {
            throw new TypeConversionException("'" + value + "' is not a valid ID (expected a positive number)");
        }
        return id;
    }
}
