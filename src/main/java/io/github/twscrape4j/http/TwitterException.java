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
