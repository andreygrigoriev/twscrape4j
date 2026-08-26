package io.github.twscrape4j.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.twscrape4j.accounts.Account;
import io.github.twscrape4j.accounts.AccountPool;
import io.github.twscrape4j.accounts.AccountRepository;
import io.github.twscrape4j.auth.ChallengeHandler;
import io.github.twscrape4j.auth.CookieAccountFactory;
import io.github.twscrape4j.auth.LoginClient;
import io.github.twscrape4j.graphql.*;
import io.github.twscrape4j.http.GraphQLClient;
import io.github.twscrape4j.http.HttpClientFactory;
import io.github.twscrape4j.models.Trend;
import io.github.twscrape4j.models.Tweet;
import io.github.twscrape4j.models.User;
import io.github.twscrape4j.ratelimit.RateLimitTracker;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Entry point for twscrape4j.
 *
 * <pre>{@code
 * var repo = new SqliteAccountRepository("accounts.db");
 * try (var scraper = TwScrape.create(repo)) {
 *     scraper.addAccountByCookies("myuser", "auth_token_value", "ct0_value");
 *     scraper.search("#java", SearchMode.LATEST)
 *            .limit(100)
 *            .forEach(System.out::println);
 * }
 * }</pre>
 */
@Slf4j
public final class TwScrape implements AutoCloseable {

    private final AccountRepository repo;
    private final AccountPool pool;
    private final HttpClientFactory httpFactory;
    private final GraphQLClient graphQLClient;
    private final ChallengeHandler challengeHandler;

    // Operations
    private final SearchTimelineOperation searchOp;
    private final TrendsOperation trendsOp;
    private final TweetDetailOperation tweetDetailOp;
    private final TweetRepliesOperation tweetRepliesOp;
    private final TweetRetweetersOperation tweetRetweetersOp;
    private final UserByScreenNameOperation userByScreenNameOp;
    private final UserByIdOperation userByIdOp;
    private final UserTweetsOperation userTweetsOp;
    private final UserMediaOperation userMediaOp;
    private final UserFollowersOperation userFollowersOp;
    private final UserFollowingOperation userFollowingOp;
    private final ListTimelineOperation listTimelineOp;
    private final ListMembersOperation listMembersOp;

    private TwScrape(AccountRepository repo, ChallengeHandler challengeHandler) {
        this.repo = repo;
        this.challengeHandler = challengeHandler;
        this.httpFactory = new HttpClientFactory();
        this.graphQLClient = new GraphQLClient();
        var rateTracker = new RateLimitTracker();
        this.pool = new AccountPool(repo, rateTracker, httpFactory);

        this.searchOp = new SearchTimelineOperation(graphQLClient);
        this.trendsOp = new TrendsOperation(graphQLClient);
        this.tweetDetailOp = new TweetDetailOperation(graphQLClient);
        this.tweetRepliesOp = new TweetRepliesOperation(graphQLClient);
        this.tweetRetweetersOp = new TweetRetweetersOperation(graphQLClient);
        this.userByScreenNameOp = new UserByScreenNameOperation(graphQLClient);
        this.userByIdOp = new UserByIdOperation(graphQLClient);
        this.userTweetsOp = new UserTweetsOperation(graphQLClient);
        this.userMediaOp = new UserMediaOperation(graphQLClient);
        this.userFollowersOp = new UserFollowersOperation(graphQLClient);
        this.userFollowingOp = new UserFollowingOperation(graphQLClient);
        this.listTimelineOp = new ListTimelineOperation(graphQLClient);
        this.listMembersOp = new ListMembersOperation(graphQLClient);
    }

    /** Creates a scraper using the default {@link ChallengeHandler#stdin()} for email verification. */
    public static TwScrape create(AccountRepository repo) {
        return new TwScrape(repo, ChallengeHandler.stdin());
    }

    /** Creates a scraper with a custom {@link ChallengeHandler} for email verification. */
    public static TwScrape create(AccountRepository repo, ChallengeHandler challengeHandler) {
        return new TwScrape(repo, challengeHandler);
    }

    // ---- Account management ----

    /**
     * Adds an account via username/password login.
     * Triggers the Twitter onboarding flow; invokes {@code challengeHandler} on email verification.
     */
    public void addAccount(String username, String password, String email) {
        var loginClient = new LoginClient(httpFactory.buildClient(
                new Account(username, password, email, "", "", "", null, false, false, null, null, 0L)),
                challengeHandler);
        Account account = loginClient.login(username, password, email);
        repo.save(account);
        pool.reload();
        log.info("Account {} added via login", username);
    }

    /** Adds an account from browser cookies (no login call needed). */
    public void addAccountByCookies(String username, String authToken, String ct0) {
        Account account = CookieAccountFactory.fromCookies(username, authToken, ct0);
        repo.save(account);
        pool.reload();
        log.info("Account {} added via cookies", username);
    }

    /** Returns all accounts currently tracked in the repository. */
    public List<Account> accounts() {
        return repo.findActive();
    }

    // ---- Search ----

    /** Returns a lazy stream of tweets matching {@code query} in the given {@link SearchMode}. */
    public Stream<Tweet> search(String query, SearchMode mode) {
        return paginated(cursor -> searchOp.fetch(query, mode, cursor, pool));
    }

    public Stream<JsonNode> searchRaw(String query, SearchMode mode) {
        return paginated(cursor -> searchOp.fetchRaw(query, mode, cursor, pool));
    }

    // ---- Trends ----

    public List<Trend> trends(TrendCategory category) {
        return trendsOp.fetch(category, pool);
    }

    public List<JsonNode> trendsRaw(TrendCategory category) {
        return trendsOp.fetchRaw(category, pool);
    }

    // ---- Tweets ----

    public Optional<Tweet> tweetDetails(long tweetId) {
        return tweetDetailOp.fetch(tweetId, pool);
    }

    public Optional<JsonNode> tweetDetailsRaw(long tweetId) {
        return tweetDetailOp.fetchRaw(tweetId, pool);
    }

    public Stream<Tweet> tweetReplies(long tweetId) {
        return paginated(cursor -> tweetRepliesOp.fetch(tweetId, cursor, pool));
    }

    public Stream<JsonNode> tweetRepliesRaw(long tweetId) {
        return paginated(cursor -> tweetRepliesOp.fetchRaw(tweetId, cursor, pool));
    }

    public Stream<User> tweetRetweeters(long tweetId) {
        return paginated(cursor -> tweetRetweetersOp.fetch(tweetId, cursor, pool));
    }

    public Stream<JsonNode> tweetRetweetersRaw(long tweetId) {
        return paginated(cursor -> tweetRetweetersOp.fetchRaw(tweetId, cursor, pool));
    }

    // ---- Users ----

    public Optional<User> userByLogin(String login) {
        return userByScreenNameOp.fetch(login, pool);
    }

    public Optional<JsonNode> userByLoginRaw(String login) {
        return userByScreenNameOp.fetchRaw(login, pool);
    }

    public Optional<User> userById(long userId) {
        return userByIdOp.fetch(userId, pool);
    }

    public Optional<JsonNode> userByIdRaw(long userId) {
        return userByIdOp.fetchRaw(userId, pool);
    }

    public Stream<Tweet> userTweets(long userId) {
        return paginated(cursor -> userTweetsOp.fetch(userId, cursor, pool));
    }

    public Stream<JsonNode> userTweetsRaw(long userId) {
        return paginated(cursor -> userTweetsOp.fetchRaw(userId, cursor, pool));
    }

    public Stream<Tweet> userMedia(long userId) {
        return paginated(cursor -> userMediaOp.fetch(userId, cursor, pool));
    }

    public Stream<JsonNode> userMediaRaw(long userId) {
        return paginated(cursor -> userMediaOp.fetchRaw(userId, cursor, pool));
    }

    public Stream<User> userFollowers(long userId) {
        return paginated(cursor -> userFollowersOp.fetch(userId, cursor, pool));
    }

    public Stream<JsonNode> userFollowersRaw(long userId) {
        return paginated(cursor -> userFollowersOp.fetchRaw(userId, cursor, pool));
    }

    public Stream<User> userFollowing(long userId) {
        return paginated(cursor -> userFollowingOp.fetch(userId, cursor, pool));
    }

    public Stream<JsonNode> userFollowingRaw(long userId) {
        return paginated(cursor -> userFollowingOp.fetchRaw(userId, cursor, pool));
    }

    // ---- Lists ----

    public Stream<Tweet> listTimeline(long listId) {
        return paginated(cursor -> listTimelineOp.fetch(listId, cursor, pool));
    }

    public Stream<JsonNode> listTimelineRaw(long listId) {
        return paginated(cursor -> listTimelineOp.fetchRaw(listId, cursor, pool));
    }

    public Stream<User> listMembers(long listId) {
        return paginated(cursor -> listMembersOp.fetch(listId, cursor, pool));
    }

    public Stream<JsonNode> listMembersRaw(long listId) {
        return paginated(cursor -> listMembersOp.fetchRaw(listId, cursor, pool));
    }

    @Override
    public void close() {
        log.debug("TwScrape closing");
    }

    // ---- Internal helpers ----

    @FunctionalInterface
    private interface PageFetcher<T> {
        Page<T> fetch(String cursor);
    }

    private <T> Stream<T> paginated(PageFetcher<T> fetcher) {
        Spliterator<T> spliterator = new PaginatingSpliterator<>(pool,
                (cursor, p) -> fetcher.fetch(cursor));
        return StreamSupport.stream(spliterator, false);
    }
}
