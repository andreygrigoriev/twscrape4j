package io.github.twscrape4j.accounts;

import io.github.twscrape4j.http.AccountHandle;
import io.github.twscrape4j.http.HttpClientFactory;
import io.github.twscrape4j.ratelimit.RateLimitTracker;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountPoolTest {

    private AccountRepository repo;
    private RateLimitTracker rateTracker;
    private HttpClientFactory httpFactory;
    private CloseableHttpClient mockClient;

    @BeforeEach
    void setUp() {
        repo = mock(AccountRepository.class);
        rateTracker = mock(RateLimitTracker.class);
        httpFactory = mock(HttpClientFactory.class);
        mockClient = mock(CloseableHttpClient.class);
        when(httpFactory.buildClient(any())).thenReturn(mockClient);
    }

    private Account activeAccount(String username) {
        return new Account(username, "pass", "e@t.com", "ep",
                "tok", "ct0", null, true, true, null, null, 0L);
    }

    @Test
    void acquireReturnsHandleForUnblockedAccount() {
        when(repo.findActive()).thenReturn(List.of(activeAccount("alice")));
        when(rateTracker.isBlocked(eq("alice"), anyString())).thenReturn(false);

        var pool = new AccountPool(repo, rateTracker, httpFactory);
        AccountHandle handle = pool.acquire("SearchTimeline");

        assertNotNull(handle);
        assertEquals("alice", handle.account().username());
        assertSame(mockClient, handle.http());
    }

    @Test
    void releaseIsNoOp() {
        when(repo.findActive()).thenReturn(List.of(activeAccount("alice")));
        when(rateTracker.isBlocked(any(), any())).thenReturn(false);
        var pool = new AccountPool(repo, rateTracker, httpFactory);

        assertDoesNotThrow(() -> pool.release(new AccountHandle(activeAccount("alice"), mockClient)));
    }

    @Test
    @Timeout(3)
    void acquireUnblocksWhenRateLimitExpires() throws Exception {
        var account = activeAccount("bob");
        when(repo.findActive()).thenReturn(List.of(account));

        // Block for the first call, unblock after
        var callCount = new AtomicInteger(0);
        when(rateTracker.isBlocked(eq("bob"), anyString())).thenAnswer(inv -> {
            int c = callCount.incrementAndGet();
            return c <= 2; // blocked on first 2 checks
        });
        when(rateTracker.earliestUnblock())
                .thenReturn(Optional.of(Instant.now().plus(50, ChronoUnit.MILLIS)));

        var pool = new AccountPool(repo, rateTracker, httpFactory);
        AccountHandle handle = pool.acquire("SearchTimeline");
        assertNotNull(handle);
    }

    @Test
    void markRateLimitedUpdatesTrackerAndRepo() {
        when(repo.findActive()).thenReturn(List.of(activeAccount("carol")));
        when(rateTracker.isBlocked(any(), any())).thenReturn(false);
        when(repo.findByUsername("carol")).thenReturn(Optional.of(activeAccount("carol")));

        var pool = new AccountPool(repo, rateTracker, httpFactory);
        var resetAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        pool.markRateLimited("carol", "SearchTimeline", resetAt);

        verify(rateTracker).update("carol", "SearchTimeline", 0, resetAt);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(repo).updateState(captor.capture());
        assertEquals(resetAt, captor.getValue().lockedUntil());
    }

    @Test
    void markErrorDisablesAccountAndRemovesFromPool() {
        when(repo.findActive()).thenReturn(List.of(activeAccount("dave")));
        when(rateTracker.isBlocked(any(), any())).thenReturn(false);
        when(repo.findByUsername("dave")).thenReturn(Optional.of(activeAccount("dave")));

        var pool = new AccountPool(repo, rateTracker, httpFactory);
        assertEquals(1, pool.size());

        pool.markError("dave", "suspended");

        verify(repo).updateState(argThat(a -> !a.active() && "suspended".equals(a.errorMsg())));
        assertEquals(0, pool.size());
    }

    @Test
    void reloadAddsNewAccounts() {
        when(repo.findActive())
                .thenReturn(List.of(activeAccount("eve")))
                .thenReturn(List.of(activeAccount("eve"), activeAccount("frank")));
        when(rateTracker.isBlocked(any(), any())).thenReturn(false);

        var pool = new AccountPool(repo, rateTracker, httpFactory);
        assertEquals(1, pool.size());

        pool.reload();
        assertEquals(2, pool.size());
    }
}
