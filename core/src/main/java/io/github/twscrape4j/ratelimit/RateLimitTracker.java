package io.github.twscrape4j.ratelimit;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitTracker {

    public record RateLimit(int remaining, Instant resetAt) {}

    private final ConcurrentHashMap<String, RateLimit> limits = new ConcurrentHashMap<>();

    private String key(String username, String operationName) {
        return username + ":" + operationName;
    }

    public void update(String username, String operationName, int remaining, Instant resetAt) {
        limits.put(key(username, operationName), new RateLimit(remaining, resetAt));
    }

    public boolean isBlocked(String username, String operationName) {
        var limit = limits.get(key(username, operationName));
        return limit != null && limit.remaining() == 0 && Instant.now().isBefore(limit.resetAt());
    }

    public Optional<Instant> nextResetFor(String username, String operationName) {
        if (!isBlocked(username, operationName)) return Optional.empty();
        return Optional.ofNullable(limits.get(key(username, operationName))).map(RateLimit::resetAt);
    }

    public Optional<Instant> earliestUnblock() {
        return limits.values().stream()
                .filter(l -> l.remaining() == 0 && Instant.now().isBefore(l.resetAt()))
                .map(RateLimit::resetAt)
                .min(Instant::compareTo);
    }
}
