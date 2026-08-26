# twscrape4j

Java 25+ library for scraping X/Twitter's internal GraphQL API. Manages a pool of authenticated accounts, rotates them on rate limits, and streams results lazily via `Stream<T>`.

Inspired by the Python library [twscrape](https://github.com/vladkens/twscrape).

## Requirements

- Java 25+
- Maven 3.9+

## Quick start

```java
var repo = new SqliteAccountRepository("accounts.db");
try (var scraper = TwScrape.create(repo)) {

    // Add account via browser cookies (recommended)
    scraper.addAccountByCookies("myuser", "auth_token_value", "ct0_value");

    // Search
    scraper.search("#java", SearchMode.LATEST)
           .limit(100)
           .forEach(tweet -> System.out.println(tweet.id() + " " + tweet.text()));

    // User lookup
    scraper.userByLogin("username").ifPresent(user ->
        System.out.println(user.username() + ": " + user.followersCount() + " followers"));

    // User tweets (lazy paginated stream)
    scraper.userTweets(userId)
           .limit(50)
           .forEach(System.out::println);
}
```

## Maven dependency

```xml
<dependency>
    <groupId>io.github.twscrape4j</groupId>
    <artifactId>twscrape4j</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Account setup

### Cookie-based (recommended, most stable)

Export `auth_token` and `ct0` from your browser's cookie store for `x.com`, then:

```java
scraper.addAccountByCookies("username", "auth_token_value", "ct0_value");
```

### Username/password login

```java
scraper.addAccount("username", "password", "email@example.com");
```

If Twitter sends an email verification code, the default handler reads it from stdin.
To handle it programmatically:

```java
var scraper = TwScrape.create(repo, (type, prompt) -> myEmailClient.fetchLatestCode());
```

## API surface

```java
// Search
Stream<Tweet>  search(String query, SearchMode mode)   // SearchMode: TOP, LATEST, MEDIA
List<Trend>    trends(TrendCategory category)          // TrendCategory: NEWS, SPORT, ENTERTAINMENT, TRENDING

// Tweets
Optional<Tweet>  tweetDetails(long tweetId)
Stream<Tweet>    tweetReplies(long tweetId)
Stream<User>     tweetRetweeters(long tweetId)

// Users
Optional<User>  userByLogin(String login)
Optional<User>  userById(long userId)
Stream<Tweet>   userTweets(long userId)
Stream<Tweet>   userMedia(long userId)
Stream<User>    userFollowers(long userId)
Stream<User>    userFollowing(long userId)

// Lists
Stream<Tweet>   listTimeline(long listId)
Stream<User>    listMembers(long listId)
```

Every paginated method has a `*Raw` variant returning `Stream<JsonNode>` for unmapped fields.

## Custom storage

Implement `AccountRepository` to use any backend:

```java
class PostgresAccountRepository implements AccountRepository {
    public void save(Account a) { ... }
    public Optional<Account> findByUsername(String username) { ... }
    public List<Account> findActive() { ... }
    public void updateState(Account a) { ... }
}
```

The default is `SqliteAccountRepository("accounts.db")`.

## TLS fingerprinting

twscrape4j uses [Conscrypt](https://github.com/google/conscrypt) to register a BoringSSL-backed TLS provider, producing a browser-like JA3 fingerprint. When the Conscrypt native library is unavailable (e.g. on unsupported platforms), the library falls back to the JDK's default TLS stack with a warning.

## Keeping operation IDs current

Twitter's internal GraphQL operation IDs rotate periodically. The constants are defined in each operation class (e.g. `SearchTimelineOperation.OPERATION_ID`). Cross-reference with [twscrape's API source](https://github.com/vladkens/twscrape/blob/main/twscrape/api.py) when you encounter 400/404 errors on specific operations.
