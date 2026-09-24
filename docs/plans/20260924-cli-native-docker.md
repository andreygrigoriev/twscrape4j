# Stateless CLI with GraalVM Native Image and Docker

## Overview

- Add a `twscrape` command-line tool that exposes the `TwScrape` API as subcommands and writes
  results to stdout as JSON Lines (default) or a JSON array, so output can be piped into `jq` or
  other tools and consumed by scripts.
- The CLI is **stateless**: it keeps no account database. One account is built on every run from
  environment variables, either from cookies (`auth_token` + `ct0`, preferred) or from
  username/password/email login, and stored in `InMemoryAccountRepository`.
- The CLI can be compiled into a GraalVM native executable (fast startup, no JVM needed) and
  shipped as a small Docker image. The image is built by a multi-stage Dockerfile, so no local
  GraalVM install is required.
- The library artifact (`io.github.twscrape4j:twscrape4j`) is unchanged for existing consumers.
  The CLI lives in its own Maven module, so library users never pull in picocli or the native
  build setup.

## Context (from discovery)

- Single-module Maven project, Java 25. Dependencies: httpclient5, Jackson 3 (`tools.jackson.*`),
  Conscrypt (JNI), sqlite-jdbc + jOOQ (JNI), jsoup, Lombok (`@Slf4j`, backed by the transitive
  slf4j-api).
- Public entry point: `api/TwScrape.java` (`final`, `AutoCloseable`). It has static
  `create(...)` factories and about 13 operations, each with a typed variant (`Tweet`/`User`/
  `Trend` records) and a `*Raw` variant (`JsonNode`), returned as `Stream`, `Optional` or `List`.
- Accounts: `addAccountByCookies(username, authToken, ct0)`, and
  `addAccount(username, password, email)`, which runs the login flow and may call a
  `ChallengeHandler`.
- `ChallengeHandler.stdin()` prints its prompt to **stdout**, which would corrupt structured
  output. The CLI must supply its own handler.
- Model records (`Tweet`, `User`, `TweetStats`, `Trend`, `Community`) include a `JsonNode raw`
  field, which must not be dumped in typed output.
- `HttpClientFactory` registers Conscrypt in a static initializer. It already falls back to JDK
  TLS when Conscrypt is unavailable (it catches `Throwable` and logs a WARN).
- Enums: `SearchMode {TOP, LATEST, MEDIA}`, `TrendCategory {NEWS, SPORT, ENTERTAINMENT, TRENDING}`.
- Toolchain: local JDK is Corretto 25, **no GraalVM `native-image` locally**, and Docker is
  available. No Maven wrapper, Dockerfile or CI exists yet.
- ⚠️ The working tree has many uncommitted changes on `fix/graphql-404-transaction-id`. Before
  Task 1 moves `src/` with `git mv`, commit them, merge that branch into `main`, and start this
  work on a fresh `feat/cli-native-docker` branch cut from `main`.

## Development Approach

- **testing approach**: Regular (code first, then tests)
- complete each task fully before moving to the next
- make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
  - tests are not optional - they are a required part of the checklist
  - write unit tests for new functions/methods
  - write unit tests for modified functions/methods
  - add new test cases for new code paths
  - update existing test cases if behavior changes
  - tests cover both success and error scenarios
- **CRITICAL: all tests must pass before starting next task** - no exceptions
- **CRITICAL: update this plan file when scope changes during implementation**
- run tests after each change
- maintain backward compatibility (library artifact coordinates and API unchanged)
- follow `CLAUDE.md` Jackson 3 conventions: `tools.jackson.*` imports, `JsonMapper.builder().build()`

## Testing Strategy

- **unit tests** (JUnit 5 + Mockito, JVM): required for every task. CLI commands are tested by
  running picocli's `CommandLine.execute(...)` with a mocked `TwScrape` and captured stdout/stderr
  writers. The tests assert exit codes and the exact JSON output.
- **native smoke tests**: run inside the Docker builder stage against the compiled binary. They
  check `--help`, `--version`, and missing credentials (exit code 3). Network calls are not part
  of the automated suite; see Post-Completion.
- no UI, so no e2e tests.

## Progress Tracking

- mark completed items with `[x]` immediately when done
- add newly discovered tasks with ➕ prefix
- document issues/blockers with ⚠️ prefix
- update plan if implementation deviates from original scope
- keep plan in sync with actual work done

## Solution Overview

**Module layout**

```
pom.xml                  parent (artifactId twscrape4j-parent, packaging pom)
core/                    library, artifactId twscrape4j (unchanged coordinates), sources moved from ./src
cli/                     artifactId twscrape4j-cli: picocli app, native profile
Dockerfile, .dockerignore, mvnw + .mvn/  (Maven wrapper so the GraalVM builder image can build)
```

**Key decisions**

1. **picocli** for commands. `picocli-codegen` runs as an annotation processor and generates the
   native-image reflection config for command classes automatically
   (`META-INF/native-image/picocli-generated/...`).
2. **Explicit JSON mapping instead of reflection.** The CLI converts `Tweet`/`User`/`Trend`/
   `TweetStats` to `ObjectNode` by hand in a single `ModelJson` class, using the Jackson tree
   model. This means:
   - no reflection metadata is needed for records or mixins, which removes the largest
     native-image risk, and JVM unit tests fully reflect native behavior;
   - the CLI output schema is a stable, explicit contract that is decoupled from the Java record
     layout;
   - `raw` is excluded naturally. `--raw` emits the untouched GraphQL `JsonNode` from the `*Raw`
     API methods instead.
3. **Stateless accounts from env.** `EnvAccountConfig` reads an injectable `Map<String,String>`.
   Cookies take precedence over login. `CliChallengeHandler` reads the code from
   `TWSCRAPE_CHALLENGE_CODE`, or prompts on the TTY via `System.console()` (never stdout), or
   fails with a clear error when non-interactive.
4. **Exclude `sqlite-jdbc`, `jooq` and `conscrypt-openjdk-uber`** from the CLI's dependency on the
   core module. The CLI never touches `SqliteAccountRepository`. Conscrypt's JNI library does not
   load reliably in native image, and the existing fallback to JDK TLS covers it; using JDK TLS in
   both JVM and native CLI modes keeps behavior consistent. The Conscrypt fallback WARN is
   silenced via `simplelogger.properties`
   (`org.slf4j.simpleLogger.log.io.github.twscrape4j.http.HttpClientFactory=error`).
   This is safe for these reasons:
   - only `SqliteAccountRepository` references jOOQ and sqlite, and the CLI never loads it;
   - `HttpClientFactory` catches `Throwable` around `Conscrypt.newProvider()`, so a
     `NoClassDefFoundError` falls back to JDK TLS;
   - native image allows an incomplete classpath by default, and classes are initialized at run
     time by default.

   **Never add `--link-at-build-time` or `--initialize-at-build-time` for
   `io.github.twscrape4j`**, because either would break the exclusion.
5. **stdout is data only.** Logging goes through `slf4j-simple` to stderr (default level
   `warn`). Errors are written to stderr and signalled through exit codes. `-v` raises **only**
   `io.github.twscrape4j` to `debug`; `org.apache.hc` stays at `warn`. httpclient5 logs full
   request headers at DEBUG, and those headers include the `auth_token`/`ct0` cookies and
   `x-csrf-token`, so raising it would leak credentials.
6. **Native build** via `org.graalvm.buildtools:native-maven-plugin` in a `native` profile of the
   `cli` module, using the `compile-no-fork` goal bound to `package`. The build is mostly static
   (`--static-nolibc`: only glibc is linked dynamically), which the distroless `base` image
   supports. `-march=compatibility` keeps the binary portable across CPUs of the build arch. The
   image arch follows the Docker host by default (arm64 on Apple Silicon). The remaining
   resource metadata (httpclient's public-suffix list, the CLI version file) lives in
   `cli/src/main/resources/META-INF/native-image/io.github.twscrape4j/twscrape4j-cli/`.
7. **Docker:** the builder stage is `ghcr.io/graalvm/native-image-community:25`, which runs
   `./mvnw -Pnative` and native smoke checks. The runtime stage is
   `gcr.io/distroless/base-debian12:nonroot` (glibc + CA certificates, non-root, no shell) with
   `ENTRYPOINT ["/usr/local/bin/twscrape"]`.

## Technical Details

**Commands** (binary name `twscrape`)

| Command                          | Library call                     | Result shape |
|----------------------------------|----------------------------------|--------------|
| `search <query> [--mode top\|latest\|media]` | `search` / `searchRaw`   | stream       |
| `trends [--category news\|sport\|entertainment\|trending]` | `trends` / `trendsRaw` | list |
| `tweet <tweetId>`                | `tweetDetails`                   | single       |
| `replies <tweetId>`              | `tweetReplies`                   | stream       |
| `retweeters <tweetId>`           | `tweetRetweeters`                | stream       |
| `user <login>`                   | `userByLogin`                    | single       |
| `user-by-id <userId>`            | `userById`                       | single       |
| `tweets <user>`                  | `userTweets`                     | stream       |
| `media <user>`                   | `userMedia`                      | stream       |
| `followers <user>`               | `userFollowers`                  | stream       |
| `following <user>`               | `userFollowing`                  | stream       |
| `list-timeline <listId>`         | `listTimeline`                   | stream       |
| `list-members <listId>`          | `listMembers`                    | stream       |

For the `<user>` argument, a numeric value is used as a user ID. `@login` is resolved to an ID
through `userByLogin` first, and exits with code 4 if the user is not found.

**Shared options** (a picocli mixin on every data command)

- `--format jsonl|json`. The default is `jsonl`: one compact object per line, flushed after each
  item so pipes see results live. `json` prints a pretty-printed array for streams and lists, and
  a pretty-printed object for single results.
- `--limit N`: the maximum number of items for streams and lists. Default `20`; `-1` means no
  limit. `0` and any other negative value are usage errors (exit 2). Applied via
  `Stream.limit`, so pagination stops early.
- In `--format json` mode, streams are **buffered** (collect, then write) so that stdout is never
  a truncated array. An error mid-stream writes nothing to stdout and exits 1. In `jsonl` mode,
  lines already written stay (partial output) and the command exits 1.
- `--raw`: print the raw GraphQL `JsonNode` instead of the mapped model.
- `-v, --verbose`: debug logging for `io.github.twscrape4j` to stderr (httpclient logging stays
  quiet to avoid leaking cookies).
- The root command also has `-h/--help` and `-V/--version`.

**Environment variables**

| Variable                      | Purpose                                                    |
|-------------------------------|------------------------------------------------------------|
| `TWSCRAPE_AUTH_TOKEN`         | cookie `auth_token` (cookie mode, preferred)                |
| `TWSCRAPE_CT0`                | cookie `ct0` (cookie mode)                                  |
| `TWSCRAPE_USERNAME`           | account username (required for login, optional label for cookies; default `cli`) |
| `TWSCRAPE_PASSWORD`           | password (login mode)                                       |
| `TWSCRAPE_EMAIL`              | email (login mode)                                          |
| `TWSCRAPE_CHALLENGE_CODE`     | pre-supplied email verification code for non-interactive login |

Interactive challenge prompts need a real terminal (`System.console()` non-null and
`isTerminal()`). When piping stdout (`twscrape ... | jq`), use `TWSCRAPE_CHALLENGE_CODE`.

Resolution order: if both `AUTH_TOKEN` and `CT0` are set, cookie mode is used. Otherwise, if
`USERNAME`, `PASSWORD` and `EMAIL` are all set, login mode is used. Otherwise the CLI fails with
exit code 3 and lists the variables it needs. If only one of the two cookie variables is set,
that is also exit code 3.

**Typed output schema** (built by `ModelJson`; `Instant` values are written as ISO-8601 strings)

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

IDs are written as **strings** to avoid precision loss in JavaScript and `jq` (Twitter IDs exceed
2^53). Null fields are omitted.

**Exit codes**

| Code | Meaning                                               |
|------|-------------------------------------------------------|
| 0    | success (including an empty stream)                   |
| 1    | runtime / API error (`TwitterException`, I/O)        |
| 2    | usage error (picocli default for bad args)            |
| 3    | configuration / auth error (missing or partial env, login rejected, challenge unavailable). Network/I/O errors during login stay at 1 |
| 4    | not found (single-result command returned empty `Optional`, or `@login` unresolved) |

**Processing flow**

`main` → set the slf4j level from `-v` (before any logger initializes) → picocli parses args →
the data command asks the injected `ScraperFactory` for a `TwScrape` (the default factory calls
`EnvAccountConfig.load(System.getenv())`, then `TwScrape.create(new CliChallengeHandler(...))`,
then `addAccountByCookies` or `addAccount`) → run the operation inside try-with-resources →
`JsonOutput` writes items to stdout → the exception handler maps exceptions to exit codes and
writes a one-line message to stderr.

## What Goes Where

- **Implementation Steps** (`[ ]` checkboxes): module restructure, CLI code, tests, native
  config, Dockerfile, docs.
- **Post-Completion** (no checkboxes): live scraping checks with real cookies, multi-arch/CI
  publishing if wanted later.

## Implementation Steps

### Task 1: Convert to a multi-module Maven build

**Files:**
- Modify: `pom.xml` (becomes parent `twscrape4j-parent`, packaging `pom`, modules `core`, `cli`)
- Create: `core/pom.xml` (artifactId `twscrape4j`, current dependencies + surefire config)
- Move: `src/` → `core/src/` (`git mv`)
- Create: `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`

- [x] commit the pending working-tree changes on `fix/graphql-404-transaction-id`, merge that
      branch into `main`, then create `feat/cli-native-docker` from `main` (see ⚠️ in Context;
      requested by the user during plan review)
      — done by orchestrator; spurious 644->755 file-mode flips were reverted, not committed
- [x] `git mv src core/src`. Create `core/pom.xml` holding the current dependencies, the Lombok
      annotation processor config and surefire `argLine`.
- [x] turn the root `pom.xml` into a parent: shared `properties` (keep the `<jackson.version>`
      comment), `dependencyManagement` for all versions (add `picocli.version`; manage both
      `slf4j-api` and `slf4j-simple` at `${slf4j.version}` = 2.0.17, because httpclient5 brings in
      slf4j-api 1.7.x), `pluginManagement` for compiler/surefire, and `<modules>core</modules>`
      (`cli` is added in Task 2)
- [x] parent `pluginManagement` holds the **full** surefire `<configuration>`, including the
      `argLine` with `-Dnet.bytebuddy.experimental=true` and the `--add-opens`, so that Mockito
      mocks of `final TwScrape` work on Java 25 in `cli` too. Alternatively, upgrade Mockito to a
      release that uses byte-buddy ≥ 1.17 and drop the experimental flag.
      — kept the experimental flag (Mockito not upgraded)
- [x] note on publishing: `core/pom.xml` now has a `<parent>`, so `twscrape4j-parent` must be
      deployed along with `twscrape4j` (or use `flatten-maven-plugin`). Library coordinates are
      unchanged.
      — documented as a comment at the top of the parent `pom.xml`
- [x] generate the Maven wrapper (`mvn wrapper:wrapper`) so Docker and contributors can build
      without a local Maven
- [x] verify no test changes are needed: `./mvnw -pl core test` must pass with the same test count
      as before the move
      — 127 run / 4 skipped both before and after the move
- [x] run tests - must pass before next task

### Task 2: CLI module skeleton with root command

**Files:**
- Modify: `pom.xml` (add `cli` module)
- Create: `cli/pom.xml`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/TwScrapeCli.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/VersionProvider.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/ScraperFactory.java` (one-method interface `TwScrape open()`)
- Create: `cli/src/main/resources/io/github/twscrape4j/cli/version.properties` (Maven-filtered `version=${project.version}`)
- Create: `cli/src/main/resources/simplelogger.properties`
- Create: `cli/src/test/java/io/github/twscrape4j/cli/TwScrapeCliTest.java`

- [x] `cli/pom.xml`: depend on `twscrape4j` excluding `sqlite-jdbc`, `jooq` and
      `conscrypt-openjdk-uber`; add `picocli` and `slf4j-simple`; add the `picocli-codegen`
      annotation processor with `-Aproject=io.github.twscrape4j/twscrape4j-cli`; enable filtering
      for `version.properties`. If `cli` uses Lombok, list both processors explicitly, because a
      child `annotationProcessorPaths` replaces the parent's instead of merging with it.
- [x] add `maven-shade-plugin`, producing a runnable `twscrape4j-cli-<version>-all.jar` for JVM
      use without GraalVM. It needs `ServicesResourceTransformer` (the slf4j 2 binding and Jackson
      services), `ManifestResourceTransformer` (`Main-Class`, `Multi-Release: true`), and filters
      excluding `META-INF/*.SF`, `*.DSA`, `*.RSA` and `module-info.class`.
- [x] `TwScrapeCli`: top-level `@Command(name = "twscrape", mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)` with no subcommands yet, and a `main` that
      applies `-v` (setting `org.slf4j.simpleLogger.log.io.github.twscrape4j=debug`) before
      picocli runs, then calls `System.exit(newCommandLine(...).execute(args))`. The `-v`
      pre-scan must handle `--verbose` and clustered short flags (`-vh`), and must stop at `--`,
      so that a query that is literally `-v` is not treated as the flag. Add a package-private
      `newCommandLine(ScraperFactory, PrintWriter out, PrintWriter err)` builder for tests.
- [x] `simplelogger.properties` (prefixed keys; unprefixed ones are silently ignored):
      `org.slf4j.simpleLogger.logFile=System.err`,
      `org.slf4j.simpleLogger.defaultLogLevel=warn`,
      `org.slf4j.simpleLogger.log.io.github.twscrape4j.http.HttpClientFactory=error`
- [x] write tests: `--help` exits 0 and prints usage, `--version` prints the project version, an
      unknown subcommand exits 2 with the message on stderr
- [x] write a test asserting `sqlite-jdbc`/`jooq`/`conscrypt` classes are not on the CLI test
      classpath (`Class.forName` throws), which guards the exclusions
- [x] write tests for the `-v` pre-scan: `-v`, `--verbose`, `-vh`, and `search -- -v` (not verbose)
- [x] write logging tests: the slf4j factory is not NOP; a normal run does not print the
      Conscrypt fallback WARN to stderr; with `-v`, `org.apache.hc` loggers are not DEBUG-enabled
- [x] run tests: `./mvnw test` - must pass before next task

### Task 3: Stateless account config from environment

**Files:**
- Create: `cli/src/main/java/io/github/twscrape4j/cli/EnvAccountConfig.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/CliChallengeHandler.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/DefaultScraperFactory.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/CliConfigException.java`
- Create: `cli/src/test/java/io/github/twscrape4j/cli/EnvAccountConfigTest.java`
- Create: `cli/src/test/java/io/github/twscrape4j/cli/CliChallengeHandlerTest.java`
- ➕ Create: `cli/src/test/java/io/github/twscrape4j/cli/DefaultScraperFactoryTest.java`
- ➕ Modify: `cli/src/main/java/io/github/twscrape4j/cli/TwScrapeCli.java` (`main` uses `DefaultScraperFactory`)

- [x] `EnvAccountConfig`: a sealed interface with `Cookies(username, authToken, ct0)` and
      `Login(username, password, email)` records, and `static EnvAccountConfig load(Map<String,String> env)`
      implementing the resolution order from Technical Details. Blank values count as unset.
      Throws `CliConfigException` listing the required variables.
- [x] `CliChallengeHandler(Map<String,String> env, Console console)`: returns
      `TWSCRAPE_CHALLENGE_CODE` if set; otherwise prompts via `console.readLine` if
      `console != null && console.isTerminal()` (JDK 22+ can return a non-terminal console);
      otherwise throws `CliConfigException("login requires a verification code; set TWSCRAPE_CHALLENGE_CODE")`
- [x] `DefaultScraperFactory implements ScraperFactory`: creates
      `TwScrape.create(challengeHandler)` and adds the account through `addAccountByCookies` or
      `addAccount`. Only auth or challenge rejections are wrapped in `CliConfigException` (exit 3);
      network/I/O errors propagate (exit 1).
      — rejection = login-flow `TwitterApiException` with 4xx (not 429), `IllegalStateException`
      (no session cookies after login) or `AccountSuspendedException`; a `CliConfigException` from the
      challenge handler is unwrapped from the login flow's `TwitterException` wrapper
- [x] write tests for `EnvAccountConfig`: cookie mode, login mode, cookie precedence when both
      are set, default username `cli`, blank handling, partial cookies → error, nothing set → error
      whose message names the variables
- [x] write tests for `CliChallengeHandler`: env code wins; mocked `Console` with
      `isTerminal()` true prompts; `isTerminal()` false or a null console with no env →
      `CliConfigException`
- [x] run tests - must pass before next task

### Task 4: JSON output layer

**Files:**
- Create: `cli/src/main/java/io/github/twscrape4j/cli/ModelJson.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/JsonOutput.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/OutputOptions.java` (picocli `@Mixin`: `--format`, `--limit`, `--raw`; `--limit` validation `-1` or ≥ 1)
- Create: `cli/src/test/java/io/github/twscrape4j/cli/ModelJsonTest.java`
- Create: `cli/src/test/java/io/github/twscrape4j/cli/JsonOutputTest.java`
- ➕ Modify: `cli/src/main/java/io/github/twscrape4j/cli/TwScrapeCli.java` (case-insensitive enum values, so `--format json` works)

- [x] `ModelJson`: `ObjectNode tweet(Tweet)`, `user(User)`, `trend(Trend)`, following the
      schema in Technical Details. IDs as strings, `Instant` as ISO-8601, nulls omitted, `url`
      derived from the username. Tree model only, with no reflection-based `valueToTree` and no
      annotations.
      — `conversationId` is omitted when 0 (the core mapper yields 0 when `conversation_id_str` is absent);
      tweet `url` is omitted when the author or its username is missing
- [x] `JsonOutput(PrintWriter out, OutputOptions opts)`: `writeStream(Stream<? extends JsonNode>)`
      applies the limit (`-1` means unlimited). JSONL writes one compact line per item and
      flushes it. JSON collects the items first, then writes a pretty array, so an exception
      mid-stream leaves stdout empty. Add `writeSingle(JsonNode)`. Use a single
      `JsonMapper.builder().build()` mapper.
- [x] write tests for `ModelJson`: full tweet with nested author/stats, nullable fields omitted,
      a large ID (> 2^53) round-trips exactly as a string, a user with no username has no `url`
- [x] write tests for `JsonOutput`: JSONL line count equals the limit, `-1` writes everything,
      an empty stream writes nothing in JSONL and `[]` in JSON, JSON output parses back as an
      array, and the stream is lazily cut (the supplier is not advanced past the limit)
- [x] write mid-stream error tests: in JSON mode, the exception propagates and nothing is written;
      in JSONL mode, the lines before the failure are present
- [x] run tests - must pass before next task

### Task 5: Base data command and exit-code mapping

**Files:**
- Create: `cli/src/main/java/io/github/twscrape4j/cli/commands/DataCommand.java` (abstract base)
- Create: `cli/src/main/java/io/github/twscrape4j/cli/ExitCodes.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/NotFoundException.java`
- Modify: `cli/src/main/java/io/github/twscrape4j/cli/TwScrapeCli.java` (register the exception handler)
- Create: `cli/src/test/java/io/github/twscrape4j/cli/ExitCodeMappingTest.java`

- [x] `DataCommand implements Callable<Integer>`: holds the `OutputOptions` mixin and the
      injected `ScraperFactory`. `call()` opens `TwScrape` in try-with-resources and delegates
      to `abstract void run(TwScrape, JsonOutput)`. It provides helpers
      `emitStream(Stream<T>, Function<T,JsonNode> mapper, Supplier<Stream<JsonNode>> raw)` and
      `emitOptional(...)`, the latter throwing `NotFoundException` on empty.
      — both helpers take `Supplier`s for the typed and raw calls, so only the chosen API method is invoked;
      the factory comes from the root `TwScrapeCli` via `spec.root().userObject()`
- [x] `IExecutionExceptionHandler` in `TwScrapeCli`: `CliConfigException` → 3,
      `NotFoundException` → 4, `TwitterException`/other → 1. It writes a one-line `error: <message>`
      to stderr and adds a stack trace only with `-v`.
      — `-v/--verbose` is now `scope = INHERIT`, so it is also accepted after a subcommand (`search -v ...`);
      ➕ `TwScrapeCli.configure(cmd, out, err)` applies writers/enum parsing/handler, and must be called after
      subcommands are registered (picocli only propagates these settings to existing subcommands)
- [x] write tests using a stub command with a mocked `TwScrape`: each exception type maps to its
      code, stderr gets the message, stdout stays empty on error
- [x] run tests - must pass before next task

### Task 6: Tweet, search and trends commands

**Files:**
- Create: `cli/src/main/java/io/github/twscrape4j/cli/commands/SearchCommand.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/commands/TrendsCommand.java`
- Create: `cli/src/main/java/io/github/twscrape4j/cli/commands/TweetCommands.java` (`tweet`, `replies`, `retweeters`)
- Modify: `cli/src/main/java/io/github/twscrape4j/cli/TwScrapeCli.java` (register subcommands)
- Create: `cli/src/test/java/io/github/twscrape4j/cli/commands/SearchCommandTest.java`
- Create: `cli/src/test/java/io/github/twscrape4j/cli/commands/TweetCommandsTest.java`
- Create: `cli/src/test/java/io/github/twscrape4j/cli/commands/TrendsCommandTest.java`
- ➕ Create: `cli/src/main/java/io/github/twscrape4j/cli/commands/PositiveLongConverter.java` (shared ID converter, reused by Task 7)
- ➕ Create: `cli/src/test/java/io/github/twscrape4j/cli/CliHarness.java` (runs the real command line with a mock, captures stdout/stderr)
- ➕ Create: `cli/src/test/java/io/github/twscrape4j/cli/commands/Fixtures.java` (model/raw test instances)

- [x] `search <query> --mode` (case-insensitive enum, default `latest`) → `search`/`searchRaw`
- [x] `trends --category` (default `trending`) → `trends`/`trendsRaw` (the list is emitted as a stream)
- [x] `tweet <id>` (single, exit 4 when empty), `replies <id>`, `retweeters <id>`. Validate that
      the ID is a positive long (picocli type conversion → exit 2).
      — subcommands are declared in the root `@Command(subcommands = ...)`, so they exist before `configure(...)` runs;
      the three tweet commands are nested classes `TweetCommands.TweetCommand/RepliesCommand/RetweetersCommand`
- [x] write tests with a mocked `TwScrape`: correct library method and arguments for each
      command, `--raw` uses the `*Raw` method, `--limit` and `--format json` are honored, the mode
      is parsed case-insensitively
- [x] write error tests: invalid mode/ID → 2, `tweet` returning empty → 4, `TwitterException` → 1
- [x] run tests - must pass before next task

### Task 7: User and list commands

**Files:**
- Create: `cli/src/main/java/io/github/twscrape4j/cli/commands/UserCommands.java` (`user`, `user-by-id`, `tweets`, `media`, `followers`, `following`)
- Create: `cli/src/main/java/io/github/twscrape4j/cli/commands/ListCommands.java` (`list-timeline`, `list-members`)
- Create: `cli/src/main/java/io/github/twscrape4j/cli/UserRef.java`
- Modify: `cli/src/main/java/io/github/twscrape4j/cli/TwScrapeCli.java` (register subcommands)
- Create: `cli/src/test/java/io/github/twscrape4j/cli/commands/UserCommandsTest.java`
- Create: `cli/src/test/java/io/github/twscrape4j/cli/commands/ListCommandsTest.java`
- Create: `cli/src/test/java/io/github/twscrape4j/cli/UserRefTest.java`

- [x] `UserRef`: a picocli type converter; a numeric value → `Id(long)`, `@login` → `Login(String)`,
      anything else → a conversion error. `long resolve(TwScrape)` looks up the login via
      `userByLogin` and throws `NotFoundException` when the user doesn't exist.
      — a sealed interface with records `Id`/`Login` and a nested `UserRef.Converter`; values over `Long.MAX_VALUE`,
      `0` and malformed logins are usage errors (exit 2); a bare name gets a "did you mean @name?" hint
- [x] user commands and list commands wired to the library methods per the command table
      — nested classes in `UserCommands`/`ListCommands`; `user <login>` also accepts a leading `@`;
      `user-by-id` and list IDs reuse `PositiveLongConverter`
- [x] write tests: each command calls the right method, `@login` resolution happens exactly once
      before the stream call, an unknown login → 4, `user` empty → 4, raw variants
- [x] write `UserRef` tests: numeric, `@name`, a bare name rejected with a helpful message, overflow
- [x] run tests - must pass before next task

### Task 8: Native image build profile

**Files:**
- Modify: `cli/pom.xml` (`native` profile with `native-maven-plugin`)
- Create: `cli/src/main/resources/META-INF/native-image/io.github.twscrape4j/twscrape4j-cli/native-image.properties`
- Create: `cli/src/main/resources/META-INF/native-image/io.github.twscrape4j/twscrape4j-cli/reachability-metadata.json` (GraalVM 25 unified format, resources section)
- Create: `cli/src/test/java/io/github/twscrape4j/cli/NativeConfigTest.java`

- [x] `native` profile: `org.graalvm.buildtools:native-maven-plugin`, goal `compile-no-fork` bound to `package`,
      `imageName=twscrape`, `mainClass=io.github.twscrape4j.cli.TwScrapeCli`, `skipNativeTests=true`
- [x] `native-image.properties`: `--no-fallback --static-nolibc -march=compatibility
      -H:+ReportExceptionStackTraces` (no `--link-at-build-time`/`--initialize-at-build-time`;
      see Key decision 4)
- [x] `reachability-metadata.json` resources (exact globs):
      `io/github/twscrape4j/cli/version.properties`, `simplelogger.properties`,
      `org/publicsuffix/list/effective_tld_names.dat` (httpclient5 public suffix list), and
      `org/apache/hc/client5/version.properties`. jsoup 1.23 ships no data resources, so nothing
      is needed for it.
- [x] write `NativeConfigTest` (JVM): `getResource(...)` is non-null for each of the four listed
      resources, and the picocli-codegen `reflect-config.json` exists under
      `META-INF/native-image/picocli-generated`
- [x] run tests - must pass before next task (the actual native compile is verified in Task 9)

### Task 9: Dockerfile with native build and smoke checks

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

- [x] builder stage (image is Oracle Linux 10.1 slim, not 9; curl present, unzip was missing) `ghcr.io/graalvm/native-image-community:25` (Oracle Linux 9 slim): run
      `microdnf install -y tar gzip unzip && microdnf clean all` so `mvnw` can bootstrap Maven
      (verify whether `curl` is present too). Copy the sources, then run
      `./mvnw -B -Pnative -pl cli -am package -DskipTests` with
      `--mount=type=cache,target=/root/.m2`. There is no `dependency:go-offline` step: it breaks
      on the reactor SNAPSHOT dependency, and the cache mount already covers it.
- [x] native smoke checks in the builder stage (a failure breaks the image build), using the
      absolute binary path `B=/src/cli/target/twscrape`:
      - `$B --help` exits 0
      - `$B --version` prints the version
      - `env -i $B search foo` exits 3 with the missing-variable message on stderr
      - `RUN --network=none env -i TWSCRAPE_AUTH_TOKEN=x TWSCRAPE_CT0=y $B user @jack` exits 1
        with a network error (not a crash, not 3). This reaches `HttpClientFactory`'s static
        init (the Conscrypt fallback) and the JDK TLS setup in native mode.
      - `ldd $B`: review that only glibc libs are linked (confirms `--static-nolibc`)
- [x] runtime stage `gcr.io/distroless/base-debian12:nonroot` (non-root via the tag): copy the
      binary to `/usr/local/bin/twscrape`, `ENTRYPOINT ["/usr/local/bin/twscrape"]`, OCI labels
      (source, version, licenses)
- [x] `.dockerignore` (also excludes `.claude`): `**/target`, `.git`, `.idea`, `*.iml`, `*.db`, `docs`
- [x] **hard gate** (verified with the equivalent `podman build`/`podman run`, linux/arm64, image 81.1 MB): build and run inside the runtime image. `docker build -t twscrape4j-cli .`,
      then `docker run --rm twscrape4j-cli --help` must exit 0, and
      `docker run --rm --network=none -e TWSCRAPE_AUTH_TOKEN=x -e TWSCRAPE_CT0=y twscrape4j-cli user @jack`
      must exit 1 with a network error. This catches missing shared libs in distroless.
- [x] (not needed - native build succeeded without metadata changes) ⚠️ if the native build fails on missing metadata, fix it in the Task 8 metadata files and add
      a matching `NativeConfigTest` assertion, then rerun Task 8 tests
- [x] run tests - must pass before next task

### Task 10: Verify acceptance criteria

- [x] verify all requirements from Overview are implemented (subcommands, JSON/JSONL,
      stateless env accounts, native binary, Docker image)
- [x] verify edge cases: empty results, `--limit -1`, huge IDs as strings, partial env, a
      non-interactive login without a challenge code, and nothing but data on stdout
- [x] run full test suite: `./mvnw verify` (286 tests, 0 failures, 4 skipped)
- [x] (verified with `podman build`, linux/arm64: 81.1 MB, uid 65532) run `docker build` end-to-end, check image size (target < 100 MB), and confirm the binary
      runs as non-root
- [x] (only slf4j-api, no picocli/slf4j-simple) confirm the core library jar still has no picocli/slf4j-simple dependency
      (`./mvnw -pl core dependency:tree`)
- [x] (offline run via macOS sandbox-exec, exit 1 network error) shaded jar runs: `java -jar cli/target/twscrape4j-cli-*-all.jar --version`, and a
      `--network`-less dummy-cookie run exits 1 (not a NoClassDefFoundError)

### Task 11: [Final] Update documentation

- [ ] README.md: new "Command-line tool" section covering the command table, env vars, output
      format/schema, exit codes, JVM jar usage, native build (`./mvnw -Pnative -pl cli -am package`
      needs GraalVM 25) and Docker usage (`docker run --rm -e TWSCRAPE_AUTH_TOKEN -e TWSCRAPE_CT0 twscrape4j-cli search "java" | jq`)
- [ ] README.md: update build instructions for the multi-module layout (`core/`, `cli/`, `./mvnw`)
- [ ] CLAUDE.md: document the module layout, the rule "CLI JSON goes through `ModelJson`
      (no reflection) so native builds need no model metadata", the stdout = data / stderr = logs
      rule, and where native-image config lives
- [ ] move this plan to `docs/plans/completed/`

## Post-Completion
*Items requiring manual intervention or external systems - no checkboxes, informational only*

**Manual verification**
- Live run with real cookies against X, in both JVM (`java -jar ...-all.jar`) and the Docker
  native image, for `search`, `user @login`, `tweets @login --limit 50` and `trends`. This checks
  that the JDK TLS path (no Conscrypt) is accepted by X and that no runtime reflection or
  resource errors appear in native mode.
- Login-mode run with `TWSCRAPE_CHALLENGE_CODE`, and an interactive run with `docker run -it`
  to check the TTY prompt.
- Rate-limit behavior with a single account: `AccountPool` sleeps until the lock expires
  (possibly about 15 minutes) with no output. Check that the wait is logged at WARN to stderr so
  it is visible without `-v`, and consider a `--timeout` option as a follow-up.

**Possible follow-ups (out of scope)**
- Multi-arch images (amd64 + arm64) via `docker buildx` and a GitHub Actions workflow publishing
  to GHCR and attaching native binaries to releases.
- Multiple accounts from env (e.g. a `TWSCRAPE_ACCOUNTS` JSON) and a proxy env var.
- Restoring Conscrypt in native mode, if X starts fingerprinting JDK TLS.
