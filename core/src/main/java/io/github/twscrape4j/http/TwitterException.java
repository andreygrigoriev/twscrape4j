package io.github.twscrape4j.http;

import java.time.Instant;

public class TwitterException extends RuntimeException {

    public TwitterException(String message) {
        super(message);
    }

    public TwitterException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class RateLimitedException extends TwitterException {
        private final Instant resetAt;

        public RateLimitedException(Instant resetAt) {
            super("Rate limited until " + resetAt);
            this.resetAt = resetAt;
        }

        public Instant resetAt() {
            return resetAt;
        }
    }

    public static class AccountSuspendedException extends TwitterException {
        public AccountSuspendedException(String username) {
            super("Account suspended: " + username);
        }
    }

    /** The login flow did not produce a session: X denied the login or set no session cookies. */
    public static class LoginFailedException extends TwitterException {
        public LoginFailedException(String username, String reason) {
            super("Login failed for " + username + ": " + reason);
        }
    }

    /**
     * The login flow asked for a step this client does not support (e.g. a captcha) or broke the expected
     * protocol (no flow token, never finished); retrying with other credentials will not help, cookies will.
     */
    public static class LoginUnsupportedException extends LoginFailedException {
        public LoginUnsupportedException(String username, String reason) {
            super(username, reason);
        }
    }

    public static class TwitterApiException extends TwitterException {
        private final int code;

        public TwitterApiException(int code, String message) {
            super("Twitter API error " + code + ": " + message);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }
}
