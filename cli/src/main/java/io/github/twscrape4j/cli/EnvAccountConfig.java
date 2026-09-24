package io.github.twscrape4j.cli;

import java.util.Map;

/** The single account a CLI run uses, resolved from environment variables. Cookies take precedence over login. */
public sealed interface EnvAccountConfig {

    String AUTH_TOKEN = "TWSCRAPE_AUTH_TOKEN";
    String CT0 = "TWSCRAPE_CT0";
    String USERNAME = "TWSCRAPE_USERNAME";
    String PASSWORD = "TWSCRAPE_PASSWORD";
    String EMAIL = "TWSCRAPE_EMAIL";

    /** Label used for cookie accounts when {@code TWSCRAPE_USERNAME} is not set. */
    String DEFAULT_USERNAME = "cli";

    String username();

    /** Cookie mode: {@code auth_token} + {@code ct0} exported from a browser session. */
    record Cookies(String username, String authToken, String ct0) implements EnvAccountConfig {
        @Override
        public String toString() {
            return "Cookies[username=" + username + "]";
        }
    }

    /** Login mode: username/password/email run through the login flow. */
    record Login(String username, String password, String email) implements EnvAccountConfig {
        @Override
        public String toString() {
            return "Login[username=" + username + ", email=" + email + "]";
        }
    }

    /**
     * Resolves the account from {@code env}; blank values count as unset and are stripped, except
     * {@code TWSCRAPE_PASSWORD}, which is used verbatim (only an empty value counts as unset).
     *
     * @throws CliConfigException when neither complete cookie nor complete login credentials are present
     */
    static EnvAccountConfig load(Map<String, String> env) {
        String authToken = value(env, AUTH_TOKEN);
        String ct0 = value(env, CT0);
        String username = value(env, USERNAME);
        // the password is used verbatim: leading/trailing whitespace may be part of it
        String rawPassword = env.get(PASSWORD);
        String password = rawPassword == null || rawPassword.isEmpty() ? null : rawPassword;
        String email = value(env, EMAIL);

        if (authToken != null && ct0 != null) {
            return new Cookies(username != null ? username : DEFAULT_USERNAME, authToken, ct0);
        }
        if (authToken != null || ct0 != null) {
            String missing = authToken == null ? AUTH_TOKEN : CT0;
            throw new CliConfigException("incomplete cookie credentials: " + missing
                    + " is not set (cookie mode needs both " + AUTH_TOKEN + " and " + CT0 + ")");
        }
        if (username != null && password != null && email != null) {
            return new Login(username, password, email);
        }
        throw new CliConfigException("no account configured: set " + AUTH_TOKEN + " and " + CT0
                + ", or " + USERNAME + ", " + PASSWORD + " and " + EMAIL);
    }

    private static String value(Map<String, String> env, String key) {
        String v = env.get(key);
        return v == null || v.isBlank() ? null : v.strip();
    }
}
