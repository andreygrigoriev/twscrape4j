package io.github.twscrape4j.cli;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.cli.commands.PositiveLongConverter;
import io.github.twscrape4j.models.User;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

import java.util.Optional;
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

    /**
     * A login (without the leading {@code @}); resolved through {@code userByLogin}. An unavailable user counts as
     * not found (see {@link #available}).
     */
    record Login(String login) implements UserRef {
        @Override
        public long resolve(TwScrape scraper) {
            return available(scraper.userByLogin(login)).map(User::id)
                    .orElseThrow(() -> new NotFoundException("user @" + login + " not found"));
        }
    }

    /** An unavailable (e.g. suspended) user is returned without an ID (0); treat it as not found. */
    static Optional<User> available(Optional<User> user) {
        return user.filter(u -> u.id() > 0);
    }

    /** Twitter login characters, without the leading {@code @}. */
    Pattern LOGIN_NAME = Pattern.compile("[A-Za-z0-9_]+");

    /** picocli converter: digits → {@link Id}, {@code @login} → {@link Login}, anything else is a usage error. */
    final class Converter implements ITypeConverter<UserRef> {

        private static final Pattern DIGITS = Pattern.compile("\\d+");
        private static final Pattern LOGIN = Pattern.compile("@" + LOGIN_NAME.pattern());

        @Override
        public UserRef convert(String value) {
            String v = value.strip();
            if (DIGITS.matcher(v).matches()) {
                return new Id(PositiveLongConverter.parse(value, "user ID"));
            }
            if (LOGIN.matcher(v).matches()) {
                return new Login(v.substring(1));
            }
            throw new TypeConversionException("'" + value + "' is not a valid user: use a numeric user ID or @login"
                    + (LOGIN.matcher("@" + v).matches() ? " (did you mean @" + v + "?)" : ""));
        }
    }
}
