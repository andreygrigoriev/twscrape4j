package io.github.twscrape4j.auth;

import io.github.twscrape4j.accounts.Account;

public final class CookieAccountFactory {

    private CookieAccountFactory() {}

    /**
     * Creates an {@link Account} pre-populated with browser cookies — no login call needed.
     * Export {@code auth_token} and {@code ct0} from your browser's cookie store for x.com.
     */
    public static Account fromCookies(String username, String authToken, String ct0) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username must not be blank");
        if (authToken == null || authToken.isBlank()) throw new IllegalArgumentException("auth_token must not be blank");
        if (ct0 == null || ct0.isBlank()) throw new IllegalArgumentException("ct0 must not be blank");
        return new Account(username, "", "", "", authToken, ct0,
                null, true, true, null, null, 0L);
    }
}
