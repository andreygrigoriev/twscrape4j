package io.github.twscrape4j.cli;

import io.github.twscrape4j.api.TwScrape;
import io.github.twscrape4j.models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine.TypeConversionException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserRefTest {

    private final UserRef.Converter converter = new UserRef.Converter();

    @Test
    void numericValueIsId() {
        assertEquals(new UserRef.Id(42), converter.convert("42"));
        assertEquals(new UserRef.Id(Long.MAX_VALUE), converter.convert(Long.toString(Long.MAX_VALUE)));
    }

    @Test
    void atNameIsLogin() {
        assertEquals(new UserRef.Login("jack_1"), converter.convert("@jack_1"));
    }

    @Test
    void bareNameIsRejectedWithHint() {
        var ex = assertThrows(TypeConversionException.class, () -> converter.convert("jack"));
        assertTrue(ex.getMessage().contains("numeric user ID or @login"), ex.getMessage());
        assertTrue(ex.getMessage().contains("did you mean @jack?"), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"@", "@a b", "", "-5", "0", "1.5", "@@jack"})
    void malformedValuesAreRejected(String value) {
        assertThrows(TypeConversionException.class, () -> converter.convert(value));
    }

    @Test
    void overflowIsRejected() {
        var ex = assertThrows(TypeConversionException.class, () -> converter.convert("99999999999999999999"));
        assertTrue(ex.getMessage().contains("too large"), ex.getMessage());
    }

    @Test
    void idResolvesWithoutLookup() {
        TwScrape scraper = mock(TwScrape.class);
        assertEquals(7, new UserRef.Id(7).resolve(scraper));
        verifyNoInteractions(scraper);
    }

    @Test
    void loginResolvesViaUserByLogin() {
        TwScrape scraper = mock(TwScrape.class);
        when(scraper.userByLogin("jack")).thenReturn(Optional.of(new User(12, "jack", null, null, 0, 0, false, null, null)));
        assertEquals(12, new UserRef.Login("jack").resolve(scraper));
    }

    @Test
    void unknownLoginThrowsNotFound() {
        TwScrape scraper = mock(TwScrape.class);
        when(scraper.userByLogin("ghost")).thenReturn(Optional.empty());
        var ex = assertThrows(NotFoundException.class, () -> new UserRef.Login("ghost").resolve(scraper));
        assertEquals("user @ghost not found", ex.getMessage());
    }
}
