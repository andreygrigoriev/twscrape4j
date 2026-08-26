# Jackson v3 Migration

## Overview
Migrate the library from Jackson v2.x (`com.fasterxml.jackson`) to Jackson v3.x (`tools.jackson`).
Jackson 3 changes the Maven group IDs, Java package prefixes, and folds Java Time support into
the core, eliminating the need for the separate `jackson-datatype-jsr310` module.

## Context (from discovery)
- Files/components involved: `pom.xml` + 21 main sources + 6 test sources (27 files total)
- Jackson APIs used: `JsonNode` (tree traversal), `ObjectMapper` (read/write), `SerializationFeature`, `JavaTimeModule`
- Primarily tree-model API. `TweetTest.java` also calls `readValue(json, Tweet.class)` to round-trip a Java Record — verify this test passes as part of Task 6's acceptance criteria (Jackson 3 supports records, but `JsonNode raw` field inside a record is worth confirming)
- Key files: `GraphQLClient.java`, `LoginClient.java`, `ModelMapper.java`, `TweetTest.java`

## Development Approach
- **Testing approach**: Regular (code first, then verify tests pass)
- Complete each task fully before moving to the next
- Make small, focused changes
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task
- **CRITICAL: all tests must pass before starting next task**

## Testing Strategy
- **Unit tests**: run `mvn test` after each task
- No e2e tests in this project

## Progress Tracking
- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix

## Solution Overview
Jackson 3 is a major version with three categories of breaking change relevant to this project:
1. **Maven coordinates**: group IDs change from `com.fasterxml.jackson.*` → `tools.jackson.*`
2. **Java package names**: all imports change from `com.fasterxml.jackson` → `tools.jackson`
3. **JavaTimeModule removed**: Java 8 time type support is built into Jackson 3 core; the
   `jackson-datatype-jsr310` module and its explicit `.registerModule(new JavaTimeModule())` calls
   are no longer needed. The `ObjectMapper` builder pattern (`JsonMapper.builder().build()`) is
   preferred for configuration.

## Technical Details
- Jackson 3 requires Java 11+; this project targets Java 25, so no JDK requirement conflict
- `JsonNode` tree-traversal API (`.path()`, `.asText()`, `.asLong()`, etc.) is unchanged in v3
- `ObjectMapper.readTree()`, `writeValueAsString()`, `createObjectNode()`, `createArrayNode()` all
  remain in v3 but configuration should use the builder: `JsonMapper.builder().<config>.build()`
- Verify the target Jackson 3.x version at https://github.com/FasterXML/jackson — use the latest
  stable or RC release (e.g. `3.0.0`)

## What Goes Where
- **Implementation Steps**: pom.xml edits, import swaps, API adaptation, test verification
- **Post-Completion**: confirm Jackson 3 GA availability before publishing to Maven Central

## Implementation Steps

### Task 1: Update Maven dependencies in pom.xml

**Files:**
- Modify: `pom.xml`

- [x] verify the latest available Jackson 3.x version on Maven Central (`mvn dependency:get -Dartifact=tools.jackson.core:jackson-databind:LATEST` or check https://central.sonatype.com/artifact/tools.jackson.core/jackson-databind) and substitute that version throughout pom.xml — do not proceed until this resolves
- [x] change `<jackson.version>` property from `2.19.0` to the confirmed Jackson 3.x version
- [x] change `jackson-databind` groupId from `com.fasterxml.jackson.core` → `tools.jackson.core`
- [x] remove the `jackson-datatype-jsr310` dependency block entirely (Java Time is built into Jackson 3 core)
- [x] verify `mvn dependency:resolve` succeeds and Jackson 3 artifacts are downloaded
- [x] run `mvn test-compile` — expect compilation failures due to old imports (confirms dep change took effect)

### Task 2: Update imports in src/main — models package

**Files:**
- Modify: `src/main/java/io/github/twscrape4j/models/Tweet.java`
- Modify: `src/main/java/io/github/twscrape4j/models/User.java`
- Modify: `src/main/java/io/github/twscrape4j/models/Trend.java`
- Modify: `src/main/java/io/github/twscrape4j/models/Community.java`

- [ ] replace `import com.fasterxml.jackson.databind.JsonNode` → `import tools.jackson.databind.JsonNode` in all 4 files
- [ ] run `mvn test-compile` — expect compilation failures in graphql, http, auth, and api packages (old imports not yet updated); this is expected and confirms the models package itself compiles under the new prefix

### Task 3: Update imports in src/main — graphql package

**Files:**
- Modify: `src/main/java/io/github/twscrape4j/graphql/ModelMapper.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/ListMembersOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/ListTimelineOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/SearchTimelineOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/TrendsOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/TweetDetailOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/TweetRepliesOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/TweetRetweetersOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/UserByIdOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/UserByScreenNameOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/UserFollowersOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/UserFollowingOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/UserMediaOperation.java`
- Modify: `src/main/java/io/github/twscrape4j/graphql/UserTweetsOperation.java`

- [ ] replace all `import com.fasterxml.jackson.databind.*` → `import tools.jackson.databind.*` across all 14 files
- [ ] run `mvn compile` — expect only http/auth packages to still fail

### Task 4: Update imports in src/main — http and auth packages

**Files:**
- Modify: `src/main/java/io/github/twscrape4j/http/GraphQLClient.java`
- Modify: `src/main/java/io/github/twscrape4j/auth/LoginClient.java`
- Modify: `src/main/java/io/github/twscrape4j/api/TwScrape.java`

- [ ] replace `import com.fasterxml.jackson.databind.JsonNode` → `import tools.jackson.databind.JsonNode` in all 3 files
- [ ] replace `import com.fasterxml.jackson.databind.ObjectMapper` → `import tools.jackson.databind.ObjectMapper` in `GraphQLClient.java` and `LoginClient.java`
- [ ] leave `new ObjectMapper()` construction unchanged in both files — these mappers use only `readTree`/`writeValueAsString`/`createObjectNode` with no time types or annotations, so no configuration changes are needed
- [ ] run `mvn compile` — must succeed with zero errors

### Task 5: Update test imports and remove JavaTimeModule

**Files:**
- Modify: `src/test/java/io/github/twscrape4j/models/TweetTest.java`
- Modify: `src/test/java/io/github/twscrape4j/graphql/ListOperationsTest.java`
- Modify: `src/test/java/io/github/twscrape4j/graphql/SearchTimelineOperationTest.java`
- Modify: `src/test/java/io/github/twscrape4j/graphql/TweetOperationsTest.java`
- Modify: `src/test/java/io/github/twscrape4j/graphql/UserOperationsTest.java`
- Modify: `src/test/java/io/github/twscrape4j/http/GraphQLClientTest.java`

- [ ] replace all `import com.fasterxml.jackson.*` → `import tools.jackson.*` in all 6 test files
- [ ] in `TweetTest.java`: remove `import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule`
- [ ] in `TweetTest.java`: remove `.registerModule(new JavaTimeModule())` from `MAPPER` construction
- [ ] in `TweetTest.java`: update `ObjectMapper` construction to Jackson 3 builder if needed:
  `JsonMapper.builder().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()`
- [ ] run `mvn test-compile` — must succeed with zero errors
- [ ] run `mvn test` — must pass before moving to Task 6

### Task 6: Verify acceptance criteria

- [ ] run full test suite: `mvn test`
- [ ] confirm all existing tests pass including `tweetSerializesAndDeserializesViaJackson` (Java Record + `JsonNode raw` round-trip)
- [ ] verify `mvn dependency:tree | grep jackson` shows only `tools.jackson` artifacts, no `com.fasterxml.jackson`
- [ ] verify no `com.fasterxml.jackson` imports remain: `grep -r "com.fasterxml.jackson" src/`
- [ ] move this plan to `docs/plans/completed/`

## Post-Completion

**Jackson 3 GA availability**:
- Verify Jackson 3 GA status before tagging a release of this library — as of early 2025, Jackson 3.x was
  still in RC. Pin the version in pom.xml to a stable release.

**API compatibility note**:
- `JsonNode` tree-model API is backward-compatible. If future Jackson 3 RC → GA introduces further
  breaking changes (e.g. `ObjectNode`/`ArrayNode` method signatures), review release notes before upgrading.
