package io.github.twscrape4j.cli.commands;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Accepts any text that is not empty or whitespace only; a blank value is a usage error (exit 2). */
public final class NonBlankConverter implements ITypeConverter<String> {

    @Override
    public String convert(String value) {
        if (value.isBlank()) {
            throw new TypeConversionException("must not be blank");
        }
        return value;
    }
}
