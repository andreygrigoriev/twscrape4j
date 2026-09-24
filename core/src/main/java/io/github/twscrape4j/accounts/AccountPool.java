package io.github.twscrape4j.accounts;

import io.github.twscrape4j.http.AccountHandle;
import io.github.twscrape4j.http.HttpClientFactory;
import io.github.twscrape4j.ratelimit.RateLimitTracker;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AccountPool {

    private final AccountRepository repo;
    private final RateLimitTracker rateTracker;
    private final HttpClientFactory httpFactory;

    private volatile List<Account> accounts;
    private final Map<String, CloseableHttpClient> clientCache = new ConcurrentHashMap<>();

    public AccountPool(AccountRepository repo, RateLimitTracker rateTracker, HttpClientFactory httpFactory) {
        this.repo = repo;
        this.rateTracker = rateTracker;
        this.httpFactory = httpFactory;
        reload();
    }

    public AccountHandle acquire(String operationName) {
        while (true) {
            var candidate = accounts.stream()
                    .filter(a -> !rateTracker.isBlocked(a.username(), operationName))
                    .findFirst();

            if (candidate.isPresent()) {
                var account = candidate.get();
                var client = clientCache.computeIfAbsent(account.username(),
                        u -> httpFactory.buildClient(account));
                log.debug("Acquired account {} for operation {}", account.username(), operationName);
                return new AccountHandle(account, client);
            }

            var earliest = rateTracker.earliestUnblock().orElse(Instant.now().plusSeconds(60));
            long waitMs = Math.max(100, earliest.toEpochMilli() - Instant.now().toEpochMilli());
            log.debug("All accounts blocked for {}, waiting {}ms until {}", operationName, waitMs, earliest);
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for available account", e);
            }
        }
    }

    /** No-op: per-operation rate limiting is tracked separately; no object-level lock to release. */
    public void release(AccountHandle handle) {}

    public void markRateLimited(String username, String operationName, Instant resetAt) {
        rateTracker.update(username, operationName, 0, resetAt);
        repo.findByUsername(username).ifPresent(a -> {
            var updated = a.withLockedUntil(resetAt);
            repo.updateState(updated);
            replaceInList(updated);
        });
        log.warn("Account {} rate-limited for {} until {}", username, operationName, resetAt);
    }

    public void markError(String username, String errorMsg) {
        repo.findByUsername(username).ifPresent(a -> {
            var updated = a.withError(errorMsg);
            repo.updateState(updated);
            clientCache.remove(username);
            accounts = accounts.stream()
                    .filter(acc -> !acc.username().equals(username))
                    .toList();
        });
        log.warn("Account {} disabled: {}", username, errorMsg);
    }

    public void updateRateLimit(String username, String operationName, int remaining, java.time.Instant resetAt) {
        if (remaining >= 0) {
            rateTracker.update(username, operationName, remaining, resetAt);
        }
    }

    public void reload() {
        accounts = repo.findActive();
        accounts.forEach(a -> clientCache.computeIfAbsent(a.username(),
                u -> httpFactory.buildClient(a)));
        log.debug("Pool reloaded: {} active accounts", accounts.size());
    }

    public int size() {
        return accounts.size();
    }

    private void replaceInList(Account updated) {
        accounts = accounts.stream()
                .map(a -> a.username().equals(updated.username()) ? updated : a)
                .toList();
    }
}
