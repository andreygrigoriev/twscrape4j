# twscrape4j

Java 25+ library for scraping X/Twitter's internal GraphQL API. Manages a pool of authenticated accounts, rotates them on rate limits, and streams results lazily via `Stream<T>`.

Inspired by the Python library [twscrape](https://github.com/vladkens/twscrape).

## Requirements

- Java 25+
- Maven 3.9+ (or the bundled Maven wrapper `./mvnw`)

## Building

The project is a multi-module Maven build:

| Module  | Artifact                                  | Contents                                          |
|---------|-------------------------------------------|---------------------------------------------------|
| `core/` | `io.github.twscrape4j:twscrape4j`         | the library (the artifact library users depend on) |
| `cli/`  | `io.github.twscrape4j:twscrape4j-cli`     | the `twscrape` command-line tool (picocli)        |

```bash
./mvnw verify                  # build and test all modules
./mvnw -pl core install        # build and install only the library
./mvnw -pl cli -am package     # build the CLI (and core); produces cli/target/twscrape4j-cli-<version>-all.jar
```

The library artifact has no dependency on picocli, slf4j-simple or the native-image setup; those
live only in `cli/`.

## Quick start

No database needed for one-off scripts:

```java
try (var scraper = TwScrape.create()) {

    // Add account via browser cookies (recommended)
    scraper.addAccountByCookies("myuser", "auth_token_value", "ct0_value");

    // Search
    scraper.search("#java", SearchMode.LATEST)
           .limit(100)
           .forEach(tweet -> System.out.println(tweet.id() + " " + tweet.text()));
}
```

For persistent sessions (accounts survive restart), use `SqliteAccountRepository`:

```java
var repo = new SqliteAccountRepository("accounts.db");
try (var scraper = TwScrape.create(repo)) {
    scraper.addAccountByCookies("myuser", "auth_token_value", "ct0_value");

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

## Command-line tool

The `cli/` module ships `twscrape`, a stateless command-line tool that exposes the API above as
subcommands and writes results to stdout as JSON Lines (default) or JSON, ready for `jq`.
It keeps no account database: every run builds one in-memory account from environment variables.

```bash
export TWSCRAPE_AUTH_TOKEN=... TWSCRAPE_CT0=...
twscrape search "#java" --limit 5 | jq -r .text
twscrape user @jack --format json
twscrape tweets @jack --limit -1 > tweets.jsonl
```

### Commands

| Command                                                        | Library call      | Result |
|----------------------------------------------------------------|-------------------|--------|
| `search <query> [--mode top\|latest\|media]` (default `latest`) | `search`          | stream |
| `trends [--category news\|sport\|entertainment\|trending]` (default `trending`) | `trends` | list |
| `tweet <tweetId>`                                              | `tweetDetails`    | single |
| `replies <tweetId>`                                            | `tweetReplies`    | stream |
| `retweeters <tweetId>`                                         | `tweetRetweeters` | stream |
| `user <login>` (with or without a leading `@`)                 | `userByLogin`     | single |
| `user-by-id <userId>`                                          | `userById`        | single |
| `tweets <user>`                                                | `userTweets`      | stream |
| `media <user>`                                                 | `userMedia`       | stream |
| `followers <user>`                                             | `userFollowers`   | stream |
| `following <user>`                                             | `userFollowing`   | stream |
| `list-timeline <listId>`                                       | `listTimeline`    | stream |
| `list-members <listId>`                                        | `listMembers`     | stream |

A `<user>` argument is either a numeric user ID or `@login`; a login is resolved to an ID via
`userByLogin` first (exit code 4 if not found). A bare name without `@` is rejected as a usage
error. Tweet, user and list IDs must be positive numbers. `--mode` and `--category` values are
case-insensitive.

Options shared by every data command:

| Option                | Description                                                                                 |
|-----------------------|---------------------------------------------------------------------------------------------|
| `--format jsonl\|json` | `jsonl` (default): one compact object per line, flushed per item. `json`: a pretty-printed array for streams/lists, a pretty-printed object for single results |
| `--limit N`           | maximum number of items for streams and lists (default `20`; `-1` = no limit). Pagination stops early |
| `--raw`               | print the raw GraphQL JSON from the `*Raw` API methods instead of the mapped model           |
| `-v, --verbose`       | debug logging for `io.github.twscrape4j` on stderr, plus stack traces on errors. Accepted before or after the subcommand |

The root command also has `-h/--help` and `-V/--version`; every subcommand has `-h/--help`.

stdout carries data only. Logs and error messages (`error: <message>`) go to stderr. With
`--verbose`, HTTP client logging stays quiet on purpose: it would print the account cookies.

### Account configuration (environment)

| Variable                  | Purpose                                                                           |
|---------------------------|-----------------------------------------------------------------------------------|
| `TWSCRAPE_AUTH_TOKEN`     | cookie `auth_token` (cookie mode, preferred)                                      |
| `TWSCRAPE_CT0`            | cookie `ct0` (cookie mode)                                                        |
| `TWSCRAPE_USERNAME`       | account username (required for login; optional label for cookies, default `cli`)  |
| `TWSCRAPE_PASSWORD`       | password (login mode)                                                             |
| `TWSCRAPE_EMAIL`          | email (login mode)                                                                |
| `TWSCRAPE_CHALLENGE_CODE` | email verification code for non-interactive login                                 |

If both cookie variables are set, cookie mode is used. Otherwise, if username, password and email
are all set, the CLI logs in. Anything else, including only one of the two cookie variables, fails
with exit code 3. Blank values count as unset.

When the login flow asks for a verification code, the CLI uses `TWSCRAPE_CHALLENGE_CODE`, or
prompts on the terminal (never on stdout). Without a terminal, for example when stdout is piped,
set `TWSCRAPE_CHALLENGE_CODE` or the run fails with exit code 3.

### Output schema

Typed output is built explicitly (not by reflection over the model records). IDs are strings,
because Twitter IDs exceed 2^53 and would lose precision in JavaScript and `jq`. Timestamps are
ISO-8601 strings. Null fields are omitted, and the raw GraphQL payload is never included (use
`--raw` for that).

```json
// Tweet
{"id":"123","text":"...","createdAt":"2026-09-24T10:00:00Z","lang":"en","conversationId":"123",
 "author":{...User...},"stats":{"likes":1,"replies":0,"retweets":0,"quotes":0,"views":10},
 "url":"https://x.com/<username>/status/123"}
// User
{"id":"42","username":"jack","displayName":"...","bio":"...","followers":1,"following":2,
 "verified":false,"createdAt":"...","url":"https://x.com/jack"}
// Trend
{"name":"...","tweetCount":1000}
```

An empty stream prints nothing in `jsonl` mode and `[]` in `json` mode. In `json` mode the whole
stream is buffered, so an error mid-stream prints nothing to stdout. In `jsonl` mode the lines
already written stay, and the command still exits 1.

### Exit codes

| Code | Meaning                                                                                  |
|------|------------------------------------------------------------------------------------------|
| 0    | success (including an empty stream)                                                      |
| 1    | runtime / API error (`TwitterException`, network or I/O, also during login)              |
| 2    | usage error (bad arguments or options)                                                   |
| 3    | configuration / auth error (missing or partial env, login rejected, no challenge code)   |
| 4    | not found (`tweet`, `user` or `user-by-id` found nothing, or a `@login` did not resolve)  |

### Running on the JVM

```bash
./mvnw -pl cli -am package -DskipTests
java -jar cli/target/twscrape4j-cli-*-all.jar search "java" --limit 5
```

The CLI omits Conscrypt, SQLite and jOOQ from its dependencies and uses the JDK TLS stack.

### Native executable

With GraalVM 25 (`native-image` on the `PATH`, `JAVA_HOME` pointing at GraalVM):

```bash
./mvnw -Pnative -pl cli -am package
cli/target/twscrape --help
```

The binary links only glibc dynamically (`--static-nolibc`) and is built with
`-march=compatibility`, so it runs on any CPU of the build architecture.

### Docker

The multi-stage `Dockerfile` compiles the native executable in a GraalVM builder image, runs smoke
checks against it, and copies it into `gcr.io/distroless/base-debian12:nonroot`. No local GraalVM
is needed. The resulting image is about 81 MB and runs as the non-root user (uid 65532).

```bash
docker build -t twscrape4j-cli .
docker run --rm -e TWSCRAPE_AUTH_TOKEN -e TWSCRAPE_CT0 twscrape4j-cli search "java" | jq
```

- The image is built for the build host's architecture (for example arm64 on Apple Silicon).
- The native-image builder needs a few GB of memory. On a small VM, cap its heap with
  `--build-arg NATIVE_IMAGE_OPTIONS=-J-Xmx2800m`.
- Podman works the same way (`podman build`, `podman run`).

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

Built-in implementations: `SqliteAccountRepository("accounts.db")` (persistent) and `InMemoryAccountRepository` (ephemeral, used by `TwScrape.create()`).

## TLS fingerprinting

twscrape4j uses [Conscrypt](https://github.com/google/conscrypt) to register a BoringSSL-backed TLS provider, producing a browser-like JA3 fingerprint. When the Conscrypt native library is unavailable (e.g. on unsupported platforms), the library falls back to the JDK's default TLS stack with a warning.

## Breaking changes / Migration notes

### Jackson 3 (tools.jackson)

This library uses Jackson 3.x (`tools.jackson.*`), not Jackson 2.x (`com.fasterxml.jackson.*`).
The `*Raw` variants return `Stream<tools.jackson.databind.JsonNode>`. If you import `JsonNode`
directly, update your import:

```java
// Jackson 2 (old):
import com.fasterxml.jackson.databind.JsonNode;

// Jackson 3 (required):
import tools.jackson.databind.JsonNode;
```

Your Maven dependency on `tools.jackson.core:jackson-databind` must be version 3.x. Jackson 2
(`com.fasterxml.jackson.core:jackson-databind`) is not compatible and will cause
`NoClassDefFoundError` at runtime.

## Keeping operation IDs current

Twitter's internal GraphQL operation IDs rotate periodically. The constants are defined in each operation class (e.g. `SearchTimelineOperation.OPERATION_ID`). Cross-reference with [twscrape's API source](https://github.com/vladkens/twscrape/blob/main/twscrape/api.py) when you encounter 400/404 errors on specific operations.

Shared feature flags live in `GraphQLClient.DEFAULT_FEATURES` (mirror of twscrape's `GQL_FEATURES`); a 400 error
listing missing features means that map needs updating.

Every GraphQL request also carries an `x-client-transaction-id` header generated by `ClientTransaction` (a port of
twscrape's `xclid.py`). Its key material is scraped once per account from the X web app; on a 404 the client
regenerates it and retries up to 3 times. A persistent 404 means either the operation ID is outdated or X changed
the web app layout the generator parses.
