package io.github.twscrape4j.accounts;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-persistent {@link AccountRepository} for ephemeral sessions.
 * Accounts are stored in memory and lost when the JVM exits.
 * Lock-expiry filtering is intentionally omitted from {@link #findActive()} —
 * it is delegated to {@code AccountPool} via {@code RateLimitTracker}.
 */
public class InMemoryAccountRepository implements AccountRepository {

    private final ConcurrentHashMap<String, Account> store = new ConcurrentHashMap<>();

    @Override
    public void save(Account account) {
        store.put(account.username(), account);
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return Optional.ofNullable(store.get(username));
    }

    @Override
    public List<Account> findActive() {
        return store.values().stream()
                .filter(Account::active)
                .toList();
    }

    @Override
    public void updateState(Account account) {
        store.put(account.username(), account);
    }
}
