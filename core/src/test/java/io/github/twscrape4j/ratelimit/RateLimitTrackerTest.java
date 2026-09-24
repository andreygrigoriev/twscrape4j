package io.github.twscrape4j.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitTrackerTest {

    private RateLimitTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new RateLimitTracker();
    }

    @Test
    void notBlockedWhenNoEntryExists() {
        assertFalse(tracker.isBlocked("alice", "SearchTimeline"));
    }

    @Test
    void notBlockedWhenRemainingIsPositive() {
        var reset = Instant.now().plus(15, ChronoUnit.MINUTES);
        tracker.update("alice", "SearchTimeline", 5, reset);
        assertFalse(tracker.isBlocked("alice", "SearchTimeline"));
    }

    @Test
    void blockedWhenRemainingIsZeroAndResetInFuture() {
        var reset = Instant.now().plus(15, ChronoUnit.MINUTES);
        tracker.update("alice", "SearchTimeline", 0, reset);
        assertTrue(tracker.isBlocked("alice", "SearchTimeline"));
    }

    @Test
    void notBlockedAfterResetTimeHasPassed() {
        var pastReset = Instant.now().minus(1, ChronoUnit.SECONDS);
        tracker.update("alice", "SearchTimeline", 0, pastReset);
        assertFalse(tracker.isBlocked("alice", "SearchTimeline"));
    }

    @Test
    void nextResetForBlockedAccount() {
        var reset = Instant.now().plus(10, ChronoUnit.MINUTES);
        tracker.update("alice", "SearchTimeline", 0, reset);
        var next = tracker.nextResetFor("alice", "SearchTimeline");
        assertTrue(next.isPresent());
        assertEquals(reset, next.get());
    }

    @Test
    void nextResetForUnblockedAccountIsEmpty() {
        tracker.update("alice", "SearchTimeline", 5, Instant.now().plusSeconds(60));
        assertTrue(tracker.nextResetFor("alice", "SearchTimeline").isEmpty());
    }

    @Test
    void earliestUnblockAcrossMultipleAccounts() {
        var soon = Instant.now().plus(5, ChronoUnit.MINUTES);
        var later = Instant.now().plus(15, ChronoUnit.MINUTES);
        tracker.update("alice", "SearchTimeline", 0, later);
        tracker.update("bob", "SearchTimeline", 0, soon);
        tracker.update("carol", "SearchTimeline", 3, later);

        var earliest = tracker.earliestUnblock();
        assertTrue(earliest.isPresent());
        assertEquals(soon, earliest.get());
    }

    @Test
    void earliestUnblockEmptyWhenNoneBlocked() {
        tracker.update("alice", "SearchTimeline", 10, Instant.now().plusSeconds(60));
        assertTrue(tracker.earliestUnblock().isEmpty());
    }

    @Test
    void blockingIsPerOperationName() {
        var reset = Instant.now().plus(15, ChronoUnit.MINUTES);
        tracker.update("alice", "SearchTimeline", 0, reset);
        assertFalse(tracker.isBlocked("alice", "UserTweets"));
    }
}
