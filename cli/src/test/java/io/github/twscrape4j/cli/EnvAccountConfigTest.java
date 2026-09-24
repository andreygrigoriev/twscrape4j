package io.github.twscrape4j.cli;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvAccountConfigTest {

    @Test
    void cookieModeWithUsername() {
        var config = EnvAccountConfig.load(Map.of(
                "TWSCRAPE_AUTH_TOKEN", "tok", "TWSCRAPE_CT0", "ct", "TWSCRAPE_USERNAME", "alice"));
        assertEquals(new EnvAccountConfig.Cookies("alice", "tok", "ct"), config);
    }

    @Test
    void cookieModeDefaultsUsernameToCli() {
        var config = EnvAccountConfig.load(Map.of("TWSCRAPE_AUTH_TOKEN", "tok", "TWSCRAPE_CT0", "ct"));
        assertEquals(new EnvAccountConfig.Cookies("cli", "tok", "ct"), config);
    }

    @Test
    void loginMode() {
        var config = EnvAccountConfig.load(Map.of(
                "TWSCRAPE_USERNAME", "alice", "TWSCRAPE_PASSWORD", "pw", "TWSCRAPE_EMAIL", "a@example.com"));
        assertEquals(new EnvAccountConfig.Login("alice", "pw", "a@example.com"), config);
    }

    @Test
    void cookiesTakePrecedenceOverLogin() {
        var config = EnvAccountConfig.load(Map.of(
                "TWSCRAPE_AUTH_TOKEN", "tok", "TWSCRAPE_CT0", "ct",
                "TWSCRAPE_USERNAME", "alice", "TWSCRAPE_PASSWORD", "pw", "TWSCRAPE_EMAIL", "a@example.com"));
        assertEquals(new EnvAccountConfig.Cookies("alice", "tok", "ct"), config);
    }

    @Test
    void blankValuesCountAsUnset() {
        var config = EnvAccountConfig.load(Map.of(
                "TWSCRAPE_AUTH_TOKEN", "tok", "TWSCRAPE_CT0", "ct", "TWSCRAPE_USERNAME", "   "));
        assertEquals(new EnvAccountConfig.Cookies("cli", "tok", "ct"), config);

        var ex = assertThrows(CliConfigException.class, () -> EnvAccountConfig.load(Map.of(
                "TWSCRAPE_AUTH_TOKEN", " ", "TWSCRAPE_CT0", "",
                "TWSCRAPE_USERNAME", "alice", "TWSCRAPE_PASSWORD", "", "TWSCRAPE_EMAIL", "a@example.com")));
        assertTrue(ex.getMessage().contains("TWSCRAPE_PASSWORD"), ex.getMessage());
    }

    @Test
    void onlyAuthTokenIsAnError() {
        var ex = assertThrows(CliConfigException.class,
                () -> EnvAccountConfig.load(Map.of("TWSCRAPE_AUTH_TOKEN", "tok")));
        assertTrue(ex.getMessage().contains("TWSCRAPE_CT0"), ex.getMessage());
    }

    @Test
    void onlyCt0IsAnErrorEvenWithLoginCredentials() {
        var ex = assertThrows(CliConfigException.class, () -> EnvAccountConfig.load(Map.of(
                "TWSCRAPE_CT0", "ct",
                "TWSCRAPE_USERNAME", "alice", "TWSCRAPE_PASSWORD", "pw", "TWSCRAPE_EMAIL", "a@example.com")));
        assertTrue(ex.getMessage().contains("TWSCRAPE_AUTH_TOKEN"), ex.getMessage());
    }

    @Test
    void incompleteLoginIsAnError() {
        assertThrows(CliConfigException.class, () -> EnvAccountConfig.load(Map.of(
                "TWSCRAPE_USERNAME", "alice", "TWSCRAPE_PASSWORD", "pw")));
    }

    @Test
    void nothingSetNamesAllVariables() {
        var ex = assertThrows(CliConfigException.class, () -> EnvAccountConfig.load(Map.of()));
        for (String name : new String[]{"TWSCRAPE_AUTH_TOKEN", "TWSCRAPE_CT0",
                "TWSCRAPE_USERNAME", "TWSCRAPE_PASSWORD", "TWSCRAPE_EMAIL"}) {
            assertTrue(ex.getMessage().contains(name), ex.getMessage());
        }
    }

    @Test
    void toStringDoesNotLeakSecrets() {
        var cookies = new EnvAccountConfig.Cookies("alice", "secret-token", "secret-ct0").toString();
        var login = new EnvAccountConfig.Login("alice", "secret-pw", "a@example.com").toString();
        assertFalse(cookies.contains("secret"), cookies);
        assertFalse(login.contains("secret"), login);
    }
}
