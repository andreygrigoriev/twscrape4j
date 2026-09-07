package io.github.twscrape4j.api;

import io.github.twscrape4j.accounts.Account;
import io.github.twscrape4j.accounts.AccountRepository;
import io.github.twscrape4j.auth.ChallengeHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwScrapeTest {

    private AccountRepository emptyRepo() {
        var repo = mock(AccountRepository.class);
        when(repo.findActive()).thenReturn(List.of());
        return repo;
    }

    @Test
    void createNoArg_returnsNonNull() {
        assertNotNull(TwScrape.create());
    }

    @Test
    void createNoArgWithChallengeHandler_returnsNonNull() {
        ChallengeHandler handler = (type, prompt) -> "123456";
        assertNotNull(TwScrape.create(handler));
    }

    @Test
    void createNoArg_addAccountByCookies_thenAccounts_roundTrips() {
        var scraper = TwScrape.create();
        scraper.addAccountByCookies("inMemUser", "tok", "ct0");
        var accounts = scraper.accounts();
        assertEquals(1, accounts.size());
        assertEquals("inMemUser", accounts.get(0).username());
        assertEquals("tok", accounts.get(0).authToken());
    }

    @Test
    void createWithDefaultChallengeHandler() {
        var scraper = TwScrape.create(emptyRepo());
        assertNotNull(scraper);
    }

    @Test
    void createWithCustomChallengeHandler() {
        ChallengeHandler handler = (type, prompt) -> "123456";
        var scraper = TwScrape.create(emptyRepo(), handler);
        assertNotNull(scraper);
    }

    @Test
    void closeDoesNotThrow() {
        assertDoesNotThrow(() -> {
            try (var scraper = TwScrape.create(emptyRepo())) {
                // no-op
            }
        });
    }

    @Test
    void addAccountByCookiesSavesAndReloads() {
        var repo = emptyRepo();
        var scraper = TwScrape.create(repo);
        scraper.addAccountByCookies("alice", "auth_tok", "ct0_val");

        verify(repo).save(argThat(a ->
                "alice".equals(a.username())
                        && "auth_tok".equals(a.authToken())
                        && "ct0_val".equals(a.ct0())
                        && a.active()
                        && a.loggedIn()));
        verify(repo, atLeast(2)).findActive(); // once on construction, once after reload
    }

    @Test
    void accountsReturnsDelegatedToRepo() {
        var repo = emptyRepo();
        var account = new Account("bob", "", "", "", "t", "c",
                null, true, true, null, null, 0L);
        when(repo.findActive()).thenReturn(List.of(account));
        var scraper = TwScrape.create(repo);
        var accounts = scraper.accounts();
        assertEquals(1, accounts.size());
        assertEquals("bob", accounts.get(0).username());
    }

    @Test
    void searchReturnsLazyStream() {
        // With no active accounts the stream should be empty (pool.acquire would block, but stream is lazy)
        var scraper = TwScrape.create(emptyRepo());
        // Just verify the stream object is non-null — fetching from it would block waiting for accounts
        assertNotNull(scraper.search("#java", SearchMode.LATEST));
    }

    @Test
    void allMethodsReturnNonNullStreams() {
        var scraper = TwScrape.create(emptyRepo());
        assertNotNull(scraper.search("#test", SearchMode.TOP));
        assertNotNull(scraper.searchRaw("#test", SearchMode.LATEST));
        assertNotNull(scraper.tweetReplies(1L));
        assertNotNull(scraper.tweetRepliesRaw(1L));
        assertNotNull(scraper.tweetRetweeters(1L));
        assertNotNull(scraper.tweetRetweetersRaw(1L));
        assertNotNull(scraper.userTweets(1L));
        assertNotNull(scraper.userTweetsRaw(1L));
        assertNotNull(scraper.userMedia(1L));
        assertNotNull(scraper.userMediaRaw(1L));
        assertNotNull(scraper.userFollowers(1L));
        assertNotNull(scraper.userFollowersRaw(1L));
        assertNotNull(scraper.userFollowing(1L));
        assertNotNull(scraper.userFollowingRaw(1L));
        assertNotNull(scraper.listTimeline(1L));
        assertNotNull(scraper.listTimelineRaw(1L));
        assertNotNull(scraper.listMembers(1L));
        assertNotNull(scraper.listMembersRaw(1L));
    }
}
