package io.github.twscrape4j.cli.commands;

import io.github.twscrape4j.cli.UserRef;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** picocli converter for a login with an optional leading {@code @}; returns the login without {@code @}. */
public final class LoginConverter implements ITypeConverter<String> {

    @Override
    public String convert(String value) {
        String v = value.strip();
        String name = v.startsWith("@") ? v.substring(1) : v;
        if (!UserRef.LOGIN_NAME.matcher(name).matches()) {
            throw new TypeConversionException("'" + value + "' is not a valid login (letters, digits and _, "
                    + "optionally with a leading @)");
        }
        return name;
    }
}
