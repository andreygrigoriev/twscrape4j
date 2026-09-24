package io.github.twscrape4j.accounts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryAccountRepositoryTest {

    private InMemoryAccountRepository repo;

    private static Account activeAccount(String username) {
        return new Account(username, "pw", "e@x.com", "", "tok", "ct0",
                null, true, true, null, null, 0L);
    }

    private static Account inactiveAccount(String username) {
        return activeAccount(username).withActive(false);
    }

    @BeforeEach
    void setUp() {
        repo = new InMemoryAccountRepository();
    }

    @Test
    void saveAndFindByUsername_roundTrips() {
        var acc = activeAccount("alice");
        repo.save(acc);
        assertEquals(Optional.of(acc), repo.findByUsername("alice"));
    }

    @Test
    void findByUsername_returnsEmptyForUnknown() {
        assertEquals(Optional.empty(), repo.findByUsername("nobody"));
    }

    @Test
    void findActive_returnsOnlyActiveAccounts() {
        repo.save(activeAccount("alice"));
        repo.save(inactiveAccount("bob"));
        var active = repo.findActive();
        assertEquals(1, active.size());
        assertEquals("alice", active.get(0).username());
    }

    @Test
    void findActive_returnsEmptyWhenNoneActive() {
        repo.save(inactiveAccount("alice"));
        assertTrue(repo.findActive().isEmpty());
    }

    @Test
    void updateState_replacesEntryWithoutDuplicates() {
        var lockedUntil = Instant.now().plusSeconds(60);
        repo.save(activeAccount("alice"));
        repo.updateState(activeAccount("alice").withLockedUntil(lockedUntil));

        var result = repo.findByUsername("alice");
        assertTrue(result.isPresent());
        assertEquals(lockedUntil, result.get().lockedUntil());
        // still active — findActive returns exactly one entry, not two
        assertEquals(1, repo.findActive().size());
    }

    @Test
    void updateState_deactivation_excludedFromFindActive() {
        repo.save(activeAccount("alice"));
        repo.updateState(inactiveAccount("alice"));
        assertTrue(repo.findActive().isEmpty());
    }

    @Test
    void save_upsertByUsername_doesNotDuplicate() {
        repo.save(activeAccount("alice"));
        repo.save(activeAccount("alice"));
        assertEquals(1, repo.findActive().size());
    }

    @Test
    void concurrentSaveAndFindActive_doesNotThrow() throws InterruptedException {
        int threads = 8;
        int ops = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        List<Throwable> errors = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                ready.countDown();
                try {
                    ready.await();
                    for (int i = 0; i < ops; i++) {
                        repo.save(activeAccount("user-" + tid + "-" + i));
                        repo.findActive();
                    }
                } catch (Throwable e) {
                    synchronized (errors) { errors.add(e); }
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();
        assertTrue(errors.isEmpty(), "Unexpected errors: " + errors);
    }
}
