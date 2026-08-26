package io.github.twscrape4j.accounts;

import java.time.Instant;

public record Account(
        String username,
        String password,
        String email,
        String emailPassword,
        String authToken,
        String ct0,
        String proxy,
        boolean active,
        boolean loggedIn,
        Instant lockedUntil,
        String errorMsg,
        long totalRequests
) {
    public Account withLockedUntil(Instant lockedUntil) {
        return new Account(username, password, email, emailPassword, authToken, ct0,
                proxy, active, loggedIn, lockedUntil, errorMsg, totalRequests);
    }

    public Account withActive(boolean active) {
        return new Account(username, password, email, emailPassword, authToken, ct0,
                proxy, active, loggedIn, lockedUntil, errorMsg, totalRequests);
    }

    public Account withLoggedIn(boolean loggedIn) {
        return new Account(username, password, email, emailPassword, authToken, ct0,
                proxy, active, loggedIn, lockedUntil, errorMsg, totalRequests);
    }

    public Account withError(String errorMsg) {
        return new Account(username, password, email, emailPassword, authToken, ct0,
                proxy, false, loggedIn, lockedUntil, errorMsg, totalRequests);
    }

    public Account withAuthCookies(String authToken, String ct0) {
        return new Account(username, password, email, emailPassword, authToken, ct0,
                proxy, active, true, lockedUntil, errorMsg, totalRequests);
    }

    public Account incrementRequests() {
        return new Account(username, password, email, emailPassword, authToken, ct0,
                proxy, active, loggedIn, lockedUntil, errorMsg, totalRequests + 1);
    }
}
