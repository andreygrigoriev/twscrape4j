package io.github.twscrape4j.accounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test for {@link AccountRepository} implementations.
 * Extend this class and provide a concrete repository via {@link #createRepository()}.
 */
public abstract class AccountRepositoryContractTest {

    protected AccountRepository repo;

    protected abstract AccountRepository createRepository();

    @BeforeEach
    void setUp() {
        repo = createRepository();
    }

    protected Account activeAccount(String username) {
        return new Account(username, "pass", "email@test.com", "emailpass",
                "auth_token_" + username, "ct0_" + username,
                null, true, true, null, null, 0L);
    }

    @Test
    void saveAndFindByUsername() {
        var account = activeAccount("alice");
        repo.save(account);

        var found = repo.findByUsername("alice");
        assertTrue(found.isPresent());
        assertEquals("alice", found.get().username());
        assertEquals("auth_token_alice", found.get().authToken());
    }

    @Test
    void findByUsernameReturnsEmptyWhenNotFound() {
        assertTrue(repo.findByUsername("nobody").isEmpty());
    }

    @Test
    void findActiveReturnsOnlyActiveAccounts() {
        repo.save(activeAccount("active1"));
        repo.save(activeAccount("active2"));
        repo.save(activeAccount("inactive").withActive(false));

        List<Account> active = repo.findActive();
        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(Account::active));
    }

    @Test
    void updateStatePersistsLockAndError() {
        repo.save(activeAccount("bob"));

        var locked = activeAccount("bob")
                .withLockedUntil(Instant.parse("2099-01-01T00:00:00Z"))
                .withActive(false)
                .withError("rate limited");

        repo.updateState(locked);

        var found = repo.findByUsername("bob").orElseThrow();
        assertFalse(found.active());
        assertEquals("rate limited", found.errorMsg());
        assertNotNull(found.lockedUntil());
    }

    @Test
    void saveIsUpsertByUsername() {
        repo.save(activeAccount("carol"));
        var updated = activeAccount("carol").withActive(false);
        repo.save(updated);

        var found = repo.findByUsername("carol").orElseThrow();
        assertFalse(found.active());
    }

    @Test
    void findActiveExcludesLockedAccounts() {
        repo.save(activeAccount("locked").withLockedUntil(Instant.parse("2099-01-01T00:00:00Z")));
        repo.save(activeAccount("unlocked"));

        List<Account> active = repo.findActive();
        assertTrue(active.stream().noneMatch(a -> a.username().equals("locked")));
        assertTrue(active.stream().anyMatch(a -> a.username().equals("unlocked")));
    }
}
