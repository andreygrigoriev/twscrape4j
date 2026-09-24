package io.github.twscrape4j.cli;

import org.junit.jupiter.api.Test;

import java.io.Console;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CliChallengeHandlerTest {

    @Test
    void envCodeWinsOverConsole() {
        Console console = mock(Console.class);
        var handler = new CliChallengeHandler(Map.of("TWSCRAPE_CHALLENGE_CODE", " 123456 "), console);
        assertEquals("123456", handler.resolve("LoginAcid", "Enter code"));
        verifyNoInteractions(console);
    }

    @Test
    void promptsOnTerminalConsole() {
        Console console = mock(Console.class);
        when(console.isTerminal()).thenReturn(true);
        when(console.readLine(anyString(), any(Object[].class))).thenReturn(" 654321 ");
        var handler = new CliChallengeHandler(Map.of(), console);
        assertEquals("654321", handler.resolve("LoginAcid", "Enter code"));
    }

    @Test
    void emptyTerminalAnswerIsConfigError() {
        Console console = mock(Console.class);
        when(console.isTerminal()).thenReturn(true);
        when(console.readLine(anyString(), any(Object[].class))).thenReturn(null);
        var handler = new CliChallengeHandler(Map.of(), console);
        assertThrows(CliConfigException.class, () -> handler.resolve("LoginAcid", "Enter code"));
    }

    @Test
    void nonTerminalConsoleWithoutEnvFails() {
        Console console = mock(Console.class);
        when(console.isTerminal()).thenReturn(false);
        var handler = new CliChallengeHandler(Map.of(), console);
        var ex = assertThrows(CliConfigException.class, () -> handler.resolve("LoginAcid", "Enter code"));
        assertTrue(ex.getMessage().contains("TWSCRAPE_CHALLENGE_CODE"), ex.getMessage());
    }

    @Test
    void nullConsoleWithoutEnvFails() {
        var handler = new CliChallengeHandler(Map.of("TWSCRAPE_CHALLENGE_CODE", "  "), null);
        var ex = assertThrows(CliConfigException.class, () -> handler.resolve("LoginAcid", "Enter code"));
        assertTrue(ex.getMessage().contains("TWSCRAPE_CHALLENGE_CODE"), ex.getMessage());
    }
}
