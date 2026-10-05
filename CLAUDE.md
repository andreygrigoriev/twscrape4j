# twscrape4j — Developer Notes

## Jackson 3 conventions

This project uses **Jackson 3.x** (`tools.jackson.*`). All Jackson imports must use the new package
prefix — the old `com.fasterxml.jackson.*` prefix will fail to compile.

| Jackson 2 (old)                                  | Jackson 3 (required)                        |
|--------------------------------------------------|---------------------------------------------|
| `com.fasterxml.jackson.databind.JsonNode`        | `tools.jackson.databind.JsonNode`           |
| `com.fasterxml.jackson.databind.ObjectMapper`    | `tools.jackson.databind.ObjectMapper`       |
| `com.fasterxml.jackson.databind.json.JsonMapper` | `tools.jackson.databind.json.JsonMapper`    |

### Mapper construction

Prefer `JsonMapper.builder().build()` over `new ObjectMapper()` when constructing mappers in tests
and production code. `JavaTimeModule` is no longer needed — Java 8 time types (`Instant`, etc.) are
supported natively in Jackson 3 core.

```java
// Preferred:
private static final ObjectMapper MAPPER = JsonMapper.builder().build();

// Avoid — still works but lacks explicit builder configuration:
private static final ObjectMapper MAPPER = new ObjectMapper();
```

### Maven dependency

```xml
<dependency>
    <groupId>tools.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>${jackson.version}</version>
</dependency>
```

Jackson 3 version is pinned in `pom.xml` via `<jackson.version>`. See the comment there for GA
status verification instructions.

## Module layout

Multi-module Maven build; always use the wrapper `./mvnw` from the repo root.

| Path    | Artifact                    | Notes                                                                |
|---------|-----------------------------|----------------------------------------------------------------------|
| `pom.xml` | `twscrape4j-parent`       | parent POM: versions, `dependencyManagement`, `pluginManagement`     |
| `core/` | `twscrape4j`                | the library; its coordinates and API must stay stable for consumers |
| `cli/`  | `twscrape4j-cli`            | `twscrape` CLI (picocli), shaded `-all.jar`, `native` profile        |

- `core` must never depend on picocli, slf4j-simple or native-image tooling.
- `cli` depends on `core` with `sqlite-jdbc`, `jooq` and `conscrypt-openjdk-uber` excluded; the CLI
  never loads `SqliteAccountRepository` and uses JDK TLS.
- Build/test: `./mvnw verify`. Native: `./mvnw -Pnative -pl cli -am package` (needs GraalVM 25), or
  `docker build .` / `podman build .` (multi-stage `Dockerfile`, no local GraalVM needed).

## CLI conventions

- **stdout = data, stderr = everything else.** Only `JsonOutput` (and picocli `--help`/`--version`)
  writes to stdout (the `FileDescriptor.out` writer built in `TwScrapeCli.main`). Logs go through slf4j-simple to stderr
  (`cli/src/main/resources/simplelogger.properties`, default `warn`), errors are printed as
  `error: <message>` by the root exception handler. Never print diagnostics to stdout. Prompts use
  `System.console()` (as `CliChallengeHandler` does), which writes to the terminal on fd 1 as well;
  that is safe only because the handler prompts solely when `Console.isTerminal()` is true (stdin and
  stdout both a TTY), so keep that guard on any new prompt.
- `-v/--verbose` raises only `io.github.twscrape4j` to debug. Never raise `org.apache.hc`: its DEBUG
  logs dump request headers including the `auth_token`/`ct0` cookies.
- `TwScrapeCli.main` pre-scans the args for `-v` and sets the slf4j-simple level system properties
  (`applyVerbosity`) before any logger exists, because slf4j-simple reads levels when a logger is
  created. Nothing may create a logger before that call: no `static Logger` field in `TwScrapeCli`
  and no static initializer reached from `main` ahead of it.
- **CLI JSON goes through `ModelJson`** (Jackson tree model, hand-written mapping, no reflection), so
  native builds need no reflection metadata for model records. Do not serialize model records with
  `ObjectMapper.writeValue*`. IDs are written as strings; absent fields (null, `""`, `Instant.EPOCH`) are
  omitted; `raw` is never
  included in typed output.
- Exit codes live in `ExitCodes`: throw `CliConfigException` (3) or `NotFoundException` (4); anything
  else maps to 1; picocli usage errors are 2.
- New data commands extend `commands/DataCommand` (shared `OutputOptions` mixin, scraper opened via
  the root `ScraperFactory`), use `emitStream`/`emitOptional`, and must be listed in the
  `subcommands` of `TwScrapeCli`'s `@Command` so `TwScrapeCli.configure(...)` reaches them.
- Commands are tested with a mocked `TwScrape` via `CliHarness` (see `cli/src/test/...`). Mocking the
  final `TwScrape` class relies on the surefire `argLine` in the parent POM's `pluginManagement`
  (`-Dnet.bytebuddy.experimental=true` plus `--add-opens`). Do not override `argLine` in a module POM
  without repeating those flags, or the cli tests fail to create mocks.

## Native image configuration

- Config lives in
  `cli/src/main/resources/META-INF/native-image/io.github.andreygrigoriev/twscrape4j-cli/`:
  `native-image.properties` (build flags) and `reachability-metadata.json` (resource globs).
  picocli reflection config is generated by `picocli-codegen` (annotation processor) at compile time.
- `--static-nolibc` is Linux-only, so it is not in `native-image.properties`: the OS-activated
  `native-linux` profile in `cli/pom.xml` adds it as a `buildArg` (the Docker build runs on Linux).
- New classpath resources read at run time must be added to `reachability-metadata.json`
  (`NativeConfigTest` checks the glob list).
- **Forbidden flags:** never add `--link-at-build-time` or `--initialize-at-build-time` for
  `io.github.twscrape4j`. The CLI relies on an incomplete classpath (excluded sqlite/jOOQ/Conscrypt)
  and run-time class initialization; `NativeConfigTest` fails if these flags appear.

## Releases

- Pushing a `v<version>` tag runs `.github/workflows/release.yml`: JVM tests, native binaries on four
  runners (macOS arm64/x86_64, Linux arm64/x86_64; native-image cannot cross-compile), a GitHub
  Release with `twscrape-<version>-<os>-<arch>.tar.gz` + `.sha256`, then the Homebrew formula.
- The tag must equal the POM version: the smoke test compares it with `twscrape --version`.
- Tags with a `-` (e.g. `v0.2.0-rc1`) become pre-releases and skip the Homebrew update.
- The formula is rendered from `packaging/homebrew/twscrape.rb.in` by `render-formula.sh` and pushed
  to `andreygrigoriev/homebrew-tap` with the `HOMEBREW_TAP_TOKEN` secret. Edit the template, never
  the tap's copy. Asset names in the workflow, template and script must stay in sync.
- Linux binaries are built on `ubuntu-22.04*` on purpose: they link glibc dynamically, so the
  oldest build glibc gives the widest compatibility.
