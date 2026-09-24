package io.github.twscrape4j.cli;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.models.User;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

import java.util.regex.Pattern;

/**
 * A {@code <user>} argument: a numeric user ID, or {@code @login} that is resolved to an ID via
 * {@link TwScrape#userByLogin(String)}.
 */
public sealed interface UserRef {

    /** Returns the user ID, looking up a login when needed; an unknown login throws {@link NotFoundException}. */
    long resolve(TwScrape scraper);

    /** A numeric user ID; used as is. */
    record Id(long id) implements UserRef {
        @Override
        public long resolve(TwScrape scraper) {
            return id;
        }
    }

    /** A login (without the leading {@code @}); resolved through {@code userByLogin}. */
    record Login(String login) implements UserRef {
        @Override
        public long resolve(TwScrape scraper) {
            return scraper.userByLogin(login).map(User::id)
                    .orElseThrow(() -> new NotFoundException("user @" + login + " not found"));
        }
    }

    /** picocli converter: digits → {@link Id}, {@code @login} → {@link Login}, anything else is a usage error. */
    final class Converter implements ITypeConverter<UserRef> {

        private static final Pattern DIGITS = Pattern.compile("\\d+");
        private static final Pattern LOGIN = Pattern.compile("@[A-Za-z0-9_]+");

        @Override
        public UserRef convert(String value) {
            String v = value.strip();
            if (DIGITS.matcher(v).matches()) {
                long id;
                try {
                    id = Long.parseLong(v);
                } catch (NumberFormatException e) {
                    throw new TypeConversionException("'" + value + "' is not a valid user ID (number too large)");
                }
                if (id <= 0) {
                    throw new TypeConversionException("'" + value + "' is not a valid user ID (expected a positive number)");
                }
                return new Id(id);
            }
            if (LOGIN.matcher(v).matches()) {
                return new Login(v.substring(1));
            }
            throw new TypeConversionException("'" + value + "' is not a valid user: use a numeric user ID or @login"
                    + (LOGIN.matcher("@" + v).matches() ? " (did you mean @" + v + "?)" : ""));
        }
    }
}
