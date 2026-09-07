# No-Database Mode

## Overview

Add `TwScrape.create()` (no-arg) factory that works without any database setup. Users who want
a quick/ephemeral session — add accounts via cookies, scrape, done — should not need to configure
or depend on SQLite. Accounts added in a no-database session live only in memory for the duration
of the JVM process.

Key benefit: lowers the barrier for one-off scripts and testing; the SQLite path is unchanged.

## Context (from discovery)

- Files involved: `TwScrape.java`, `AccountRepository.java`, `AccountPool.java`, `TwScrapeTest.java`
- Current pattern: `AccountPool` uses `AccountRepository` for `findActive`, `findByUsername`,
  `updateState`, and `save` on every lifecycle event
- `AccountRepository` is already an interface with one impl (`SqliteAccountRepository`)
- Tests already mock `AccountRepository`, so an in-memory concrete impl fits naturally

## Development Approach

- **Testing approach**: Regular (code first, then tests)
- Complete each task fully before moving to the next
- Make small, focused changes
- **CRITICAL: every task MUST include new/updated tests**
- **CRITICAL: all tests must pass before starting next task**

## Testing Strategy

- Unit tests for `InMemoryAccountRepository` covering all four interface methods
- Unit tests for new `TwScrape.create()` and `TwScrape.create(ChallengeHandler)` no-arg overloads
- Verify `accounts()` round-trips correctly when using the in-memory repo via `TwScrape`

## Progress Tracking

- Mark completed items with `[x]` immediately when done
- Add newly discovered tasks with ➕ prefix
- Document issues/blockers with ⚠️ prefix

## Solution Overview

Add `InMemoryAccountRepository` in the `accounts` package — a `ConcurrentHashMap`-backed
implementation of `AccountRepository` with no persistence. Then add two new `TwScrape.create()`
overloads (no repo arg) that wire up this in-memory repo internally.

No existing method signatures change; the two new overloads are purely additive.

## Technical Details

- `InMemoryAccountRepository` stores accounts keyed by username in a `ConcurrentHashMap<String, Account>`
- `findActive()` returns accounts where `account.active() == true` — lock-expiry (`lockedUntil`)
  filtering is intentionally delegated to `AccountPool` (same as allowed by the `AccountRepository`
  Javadoc). This differs from `SqliteAccountRepository`, which also filters by `lockedUntil` in SQL;
  `AccountPool.acquire()` handles lock-expiry independently via `RateLimitTracker`, so no behavioral
  bug results. Note: `TwScrape.accounts()` callers will see rate-locked accounts in the list on an
  in-memory scraper, which they would not on the SQLite path.
- `save()` does an upsert (put by key)
- `findByUsername()` is a simple map lookup returning `Optional.ofNullable(...)`
- `updateState()` replaces the entry (same key, new value); if the updated account has `active=false`,
  subsequent `findActive()` calls will exclude it
- Thread-safety: all mutations go through the `ConcurrentHashMap`; `findActive()` returns a snapshot list

## What Goes Where

**Implementation Steps** (`[ ]` checkboxes): code changes and tests within this repo.

**Post-Completion** (no checkboxes): consuming projects or doc sites that reference the instantiation
pattern may want to show the no-arg form in their examples.

## Implementation Steps

### Task 1: Add InMemoryAccountRepository

**Files:**
- Create: `src/main/java/io/github/twscrape4j/accounts/InMemoryAccountRepository.java`
- Create: `src/test/java/io/github/twscrape4j/accounts/InMemoryAccountRepositoryTest.java`

- [ ] create `InMemoryAccountRepository` implementing `AccountRepository`
- [ ] back with `ConcurrentHashMap<String, Account>` (no constructor args needed)
- [ ] `save()` — `map.put(account.username(), account)`
- [ ] `findByUsername()` — `Optional.ofNullable(map.get(username))`
- [ ] `findActive()` — return snapshot list of entries where `account.active() == true`
- [ ] `updateState()` — `map.put(account.username(), account)` (same as save; interface contract differs conceptually)
- [ ] write tests: `save` then `findByUsername` round-trips correctly
- [ ] write tests: `findByUsername` returns `Optional.empty()` for unknown username
- [ ] write tests: `findActive` filters inactive accounts
- [ ] write tests: `updateState` with `active=false` then `findActive()` does not return the account
- [ ] write tests: `updateState` replaces entry without adding duplicates
- [ ] write tests: concurrent save + findActive does not throw
- [ ] run tests — must pass before task 2

### Task 2: Add no-arg TwScrape.create() factory overloads

**Files:**
- Modify: `src/main/java/io/github/twscrape4j/api/TwScrape.java`
- Modify: `src/test/java/io/github/twscrape4j/api/TwScrapeTest.java`

- [ ] add `TwScrape.create()` — delegates to `new TwScrape(new InMemoryAccountRepository(), ChallengeHandler.stdin())`
- [ ] add `TwScrape.create(ChallengeHandler)` — delegates to `new TwScrape(new InMemoryAccountRepository(), challengeHandler)`
- [ ] update class-level Javadoc to show both usage patterns (with and without repo)
- [ ] write test: `TwScrape.create()` no-arg creates non-null instance
- [ ] write test: `TwScrape.create(challengeHandler)` no-arg creates non-null instance
- [ ] write test: `addAccountByCookies` then `accounts()` returns the account when using no-arg create
- [ ] run tests — must pass before task 3

### Task 3: Verify acceptance criteria

- [ ] verify `var scraper = TwScrape.create()` compiles and all operations are accessible
- [ ] verify `addAccountByCookies` + `accounts()` works end-to-end with the in-memory repo
- [ ] verify existing `TwScrape.create(repo)` and `TwScrape.create(repo, handler)` signatures unchanged
- [ ] run full test suite: `mvn test`

### Task 4: Update README and move plan to completed

- [ ] update README.md no-arg usage example if a "quick start" section exists
- [ ] update CLAUDE.md if new patterns discovered
- [ ] move this plan to `docs/plans/completed/`

## Post-Completion

**Manual verification**: run a real one-off script using `TwScrape.create()` to confirm accounts
added via `addAccountByCookies` survive for the session length but are not persisted after JVM exit.
