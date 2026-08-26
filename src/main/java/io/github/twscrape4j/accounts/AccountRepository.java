package io.github.twscrape4j.accounts;

import java.util.List;
import java.util.Optional;

/**
 * Persistent store for scraping accounts.
 *
 * <p>Implement this interface to use a custom storage backend:
 * <pre>{@code
 * class MyRepo implements AccountRepository {
 *     public void save(Account account) { ... }
 *     public Optional<Account> findByUsername(String username) { ... }
 *     public List<Account> findActive() { ... }
 *     public void updateState(Account account) { ... }
 * }
 * TwScrape scraper = TwScrape.create(new MyRepo());
 * }</pre>
 *
 * <p>The default implementation is {@link SqliteAccountRepository}.
 */
public interface AccountRepository {

    /** Inserts or updates an account (upsert by username). */
    void save(Account account);

    Optional<Account> findByUsername(String username);

    /**
     * Returns all accounts eligible for scraping: active and not currently rate-limited.
     * Implementations may filter by {@code active=true} and defer lock-expiry filtering to
     * the caller, or apply it directly in the query.
     */
    List<Account> findActive();

    /** Persists runtime state (lock status, error message, request count) without touching credentials. */
    void updateState(Account account);
}
