# CLAUDE.md — KMP GitHub Repository Search

**This file supersedes `~/.claude/CLAUDE.md` for this project. Do not apply the global guides here.**

The global guideline set assumes an Android-only stack — Hilt, Retrofit, MockK, Robolectric,
JVM-only `androidx` artifacts. None of it compiles in `commonMain`. The rules below replace it.

---

## Role and standard

You are acting as a **Kotlin Multiplatform tech lead**. Every change must read like it was written by
someone with years of production KMP experience. That means, on every single change, before you call
it done:

- **Security** — could this leak a secret, log sensitive data, trust unvalidated input, or ship a
  debug affordance to production?
- **Correctness** — what is the failure mode? What happens on null, empty, timeout, cancellation,
  rotation, process death, and a hostile API response?
- **Performance** — what does this cost per frame, per request, per recomposition? Is it on the main
  thread? Does it allocate in a hot loop?
- **Effectiveness** — is this the simplest thing that solves the actual problem, or am I building for
  a requirement nobody has?

If you cannot answer all four, the change is not finished. State the answers in the PR description,
not just in your head. Do not add abstraction for hypothetical futures — *"Duplication is a hint, not
a command."* (Kent Beck)

---

## Project

Cross-platform GitHub repository search app built for the Accenture coding test. The assignment
itself is ゆめみ's public KMP engineer code check —
<https://github.com/yumemi-inc/kmp-engineer-codecheck>, checked out locally at
`../kmp-engineer-codecheck/README.md`. **It is the contract. Never introduce a convention here
that violates it.**

Submitted against the **低 (tech lead)** tier. Per the assignment's own rule, that makes all
**13 高** and **11 中** items mandatory prerequisites and the **6 低** items the primary criteria.
All 30 rows must land.

`IMPLEMENTATION_PLAN.md`, referenced throughout this file, is a local working document and is
deliberately not committed. Where a rule below points at its *Fixed parameters* table, the
committed sources of truth are `gradle/libs.versions.toml` for every pinned version and
`docs/adr/` for every decision.

- **Platforms:** Android + iOS + Desktop
- **UI:** Compose Multiplatform, shared across all three
- **Priority:** Android and Desktop ship first; iOS follows and may take as long as it takes

Functional contract (from the spec, non-negotiable):
1. Enter a keyword.
2. Search GitHub repositories with it, via `search/repositories`.
3. Show results as a list of repository names.
4. Tap a result → detail showing **exactly these 7 fields**: repository name, owner avatar,
   language, stars, watchers, forks, open issues.

---

## Toolchain — pinned, verified 2026-09-04

| Component | Version |
|---|---|
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.12.0 |
| AGP | 9.4.0 |
| Gradle | 9.7.1 |
| compileSdk / targetSdk | 37 (Android 17) |
| Ktor | 3.5.2 |
| Koin | 4.2.2 |
| Navigation 3 (CMP) | 1.1.1 (`org.jetbrains.androidx.navigation3:navigation3-ui`) |
| AndroidX Lifecycle (CMP port) | 2.11.0 (`org.jetbrains.androidx.lifecycle:lifecycle-*`) |
| AndroidX SavedState (CMP port) | 1.4.0 |
| AndroidX Window (CMP port) | 1.5.1 (`window-core` — source of `WindowSizeClass`) |
| Xcode | 26.6 |
| JDK toolchain | 21 (LTS) |

Note that CMP 1.12.0 itself ships against nav3 `1.2.0-alpha02`, while the CMP navigation docs
document the stable `1.1.1`. We pin **1.1.1** — no alphas — which makes nav3 1.1.1 × CMP 1.12.0 a
pairing to verify in PR1, not an assumption. CMP 1.12.0 is also **64-bit only**.

Declare every version in `gradle/libs.versions.toml`. **No dynamic versions** (`+`, `latest.release`)
— they make builds non-reproducible and are a supply-chain risk. Every parameter an implementer might
otherwise guess is pinned in `IMPLEMENTATION_PLAN.md` → *Fixed parameters*. Treat that table as
binding: a change there is an ADR, not a preference.

Build hygiene, on from PR1:
- Gradle **configuration cache** and **build cache** enabled.
- **Explicit API mode `strict`** on every library module, so public surface is deliberate.
  - ktlint for formatting, gating CI. **detekt only if it resolves against Kotlin 2.4.10** — its 1.23.x
    compatibility table stops at Kotlin 2.0.21, and the only line built against 2.4.10 is `2.0.0-alpha`
    (which itself targets Gradle 9.6.1 / AGP 9.3.1, both behind our pins). Spike it in PR1. If it does
    not resolve cleanly, the lint gate is **ktlint + Android Lint**, and that is a documented outcome,
    not a failure — see `IMPLEMENTATION_PLAN.md` → *Known risks*, detekt row. Do not ship an alpha to
    chase a newer number, and do not block PR1 on it.
- Compose compiler metrics enabled on the dev variant, so stability regressions are visible rather
  than theoretical.

The spec requires justifying anything that is not the newest stable, so keep these documented in the
README: Koin 4.2.2 (built against Kotlin 2.3.20, binary-compatible), JDK 21 (not 25 — Android tooling
lag), Xcode 26.6 (27 is beta), and — if adaptive list-detail uses it —
`material3.adaptive:adaptive-navigation3` (beta; no stable line ships for CMP yet).

**README language: Japanese**, since the reviewers are Accenture's and read Japanese. Identifiers stay English (below).

---

## Test-Driven Development

**TDD is the working method for this project, not an afterthought.** Production code is written in
response to a failing test. No exceptions for "simple" code.

### Canon TDD — the loop

Follow Kent Beck's canonical formulation exactly:

1. **Write a list** of the test scenarios you want to cover.
2. **Turn exactly one item** on that list into a concrete, runnable, *failing* test.
3. **Change the code** to make that test — and all previous tests — pass. Add newly discovered
   scenarios to the list as you go.
4. **Optionally refactor** to improve the design.
5. **Repeat 2–4** until the list is empty.

The test list is the point. Without it you never know when you are done.

### TDD failure modes — do not do these

| Mistake | Why it's fatal |
|---|---|
| Turning the whole list into concrete tests up front | You lose the feedback that reshapes the design |
| Mixing refactoring into making the test pass | *Make it run, then make it right.* Two hats, never both at once |
| Deleting or weakening an assertion to get green | Make it pass **for real** |
| Pasting the computed value in as the expected value | The test now asserts the bug |
| Over-mocking | Beck: going deep with mocks "kills your ability to refactor" |
| Testing implementation instead of behaviour | Every refactor breaks the suite; the suite stops being an asset |
| Skipping the refactor step | Design debt compounds silently |
| Abstracting on the first duplication | Duplication is a hint, not a command |

### Test doubles

Meszaros's five kinds, as Fowler defines them: **dummy** (fills a parameter list, never used),
**fake** (working implementation, shortcut unsuitable for production), **stub** (canned answers),
**spy** (stub that records calls), **mock** (pre-programmed with expectations, verifies them).

**Default to Fakes.** In KMP this is not a style preference — hand-written fakes work on every target
with no code generation, debug cleanly, and do not couple tests to call sequences. Mocking frameworks
are unreliable on Native. Put shared fakes in `:core:testing`.

Reach for a mock only when the *interaction itself* is the specification (e.g. "the token is refreshed
exactly once on 401"). Assert on resulting state wherever state is observable.

### Test naming — English, behaviour-first

Names read as specification. **No Japanese identifiers anywhere in the codebase**, tests included.

```kotlin
@Test
fun `search returns repositories when the query matches`() { }

@Test
fun `search returns RateLimited with reset time when the API responds 403`() { }

@Test
fun `search returns an empty result when nothing matches`() { }

@Test
fun `detail falls back gracefully when language is null`() { }
```

Pattern: `subject + condition + expected outcome`. Cover the normal path, the failure path, and the
boundary (empty, null, zero, max, cancelled) for every unit.

### Structure

Given-When-Then inside every test body, separated by blank lines. One logical assertion per test —
multiple `assert` calls are fine when they verify one behaviour.

### Toolchain

`kotlin.test` + `kotlinx-coroutines-test` + Turbine + Ktor `MockEngine`, all in `commonTest`.

- **Only multiplatform libraries in `commonTest`.** A JVM-only test dependency breaks the iOS test
  compilation, usually loudly and at the worst time.
- **Always use Turbine for Flow.** Never hand-roll collection in a test — manual collection races and
  produces flaky suites. `flow.test { assertEquals(expected, awaitItem()) ; awaitComplete() }`
- `runTest` for anything suspending. Inject a `TestDispatcher`; never let a test touch a real one.
- **Inject `kotlin.time.Clock`; never call `Clock.System.now()` in production code.** Rate-limit reset
  times are time-dependent behaviour, and time-dependent behaviour that cannot be faked produces
  flaky tests. Use `kotlin.time.Instant` from the stdlib — not kotlinx-datetime.

### UI tests — two constraints that decide how they are written

`runComposeUiTest` in `commonTest` is the right API, but two documented limits shape every suite:

- **Koin cannot be initialised inside `runComposeUiTest`.** CMP UI tests do not use an Android test
  runner and never construct `Application`, so `KoinTest` and `KoinTestExtension` do not work there.
  **Therefore: UI tests target the stateless content, never the wired screen.** Drive
  `SearchContent` / `DetailContent` with a plain state object and lambda spies. The stateful
  `SearchScreen` — the part that touches Koin and `koin-compose-viewmodel` — is covered by the
  ViewModel unit tests plus `checkModules()`, not by a UI test. This is the primary path, not a
  fallback, and it is why the stateful/stateless split in *Compose performance* is mandatory.
- **On Android these run only as instrumented tests.** They cannot run as unit tests and cannot run
  under Robolectric. The device-test compilation must be created with the AGP 9 device-test
  **builder**, and CI needs an emulator. Desktop and iOS run the same source set with no emulator;
  see `IMPLEMENTATION_PLAN.md` for which CI job runs which target.

  Verified against AGP 9.4.0 in PR1 — the source set is **`androidDeviceTest`**, not
  `androidInstrumentedTest`, and `withDeviceTestBuilder` is what creates it. `withDeviceTest {}`
  only configures a compilation that already exists, so it is not a substitute:

  ```kotlin
  kotlin {
      android {
          // sourceSetTreeName = "test" is what makes commonTest feed androidDeviceTest.
          withDeviceTestBuilder { sourceSetTreeName = "test" }
              .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
      }
  }
  ```

---

## Architecture

Layering is strictly one-way. Never reverse it, never shortcut it.

```
UI (Composable) → ViewModel → UseCase → Port (interface) ← Repository → Ktor
```

### Module graph

Follows JetBrains' 2026 recommended structure: **application entry points are separate modules** and
never mixed with shared code.

```
androidApp/            Android application entry point
desktopApp/            Desktop (JVM) entry point
iosApp/                Xcode project, consumes the shared framework (not a Gradle module)
shared/                app composition: DI graph, nav host, root composable
core/common/           Outcome, AppError, dispatcher providers, expect/actual
core/designsystem/     theme, tokens, shared composables, string resources
core/network/          Ktor client, auth, rate-limit header parsing
core/testing/          fakes, fixtures, test dispatchers (test-only leaf)
domain/                entities, port interfaces, use cases
data/github/           DTOs, mappers, repository implementations
feature/search/        SearchScreen + SearchViewModel
feature/detail/        DetailScreen + DetailViewModel
build-logic/           convention plugins (included build, not a project module)
```

11 Gradle modules, plus `build-logic` as an included build and `iosApp` as an Xcode project.

### Rules

- **Entry points depend on shared modules, never the reverse.**
- `:domain` depends on `:core:common` and nothing else. It must never import Ktor, Compose, a DTO, or
  any `androidx` type. If it does, the layering is broken — fix it, don't work around it.
- Port interfaces live in `:domain`; implementations in `:data:github`; `:shared` binds them. `:shared`
  is the only module that knows both sides.
- DTOs never escape `:data:github`. Map to domain types at the boundary — this is an anticorruption
  layer, and it is where nullability and API quirks get normalised.
- **AGP 9 requirement:** shared modules use the `com.android.kotlin.multiplatform.library` plugin with
  an `android {}` block, *not* `androidTarget {}`. Only `androidApp` uses the application plugin.
  The block was named `androidLibrary {}` in earlier AGP 9 previews; as of **AGP 9.4.0 that name is
  deprecated** in favour of `android {}` — same plugin, renamed DSL. Verified against 9.4.0 in PR1.
- Keep every `expect`/`actual` pair in the common module's source sets so the visibility chain holds.
- `:core:testing` is a **test-only leaf**: only `*Test` source sets may depend on it, never a
  `*Main`. It depends on `:domain` and `:core:common` so it can hold fakes of the ports, and on
  nothing else. If production code needs something from it, that thing is in the wrong module.
- **All user-facing strings live in `:core:designsystem`**, so one module owns the resource bundle
  and `Res` is generated once. Feature modules read them via
  `core.designsystem.generated.resources.Res`. This trades a little layering purity for a single
  translation surface across two locales — a deliberate call for an app this size, not an oversight.
- Do not add modules beyond this graph. Over-modularizing a two-screen app is its own smell.

---

## Dependency injection — Koin

Hilt/Dagger is JVM+Android only and cannot inject into `commonMain` or iOS. Use Koin 4.2.2.

- One Koin module per Gradle module, aggregated in `:shared`.
- ViewModels via `koin-compose-viewmodel`.
- **Constructor injection only.** No service-locator lookups inside classes — they hide dependencies
  and make the class untestable without a running container.
- **Always inject dispatchers.** Never reference `Dispatchers.IO` directly in domain or data code —
  a hardcoded dispatcher cannot be swapped for a `TestDispatcher`, so the test is non-deterministic
  and `runTest` loses control of virtual time. Inject a `DispatcherProvider`.
  (`Dispatchers.IO` *does* exist on Native — it is JVM **and** Native. It does **not** exist on
  JS/Wasm, so if the Web target is ever added the injected provider is what keeps `commonMain`
  compiling. Testability is the reason that always holds.)
- Verify the graph in a test (`checkModules()`), so a missing binding fails in CI rather than at
  runtime on a reviewer's device.

---

## Concurrency

- `StateFlow` for state (always has a value, replays to new collectors). `SharedFlow` for one-shot
  events. Never model a navigation event as state — it will re-fire on rotation.
- **Never `GlobalScope`.** It leaks and escapes structured cancellation.
- ViewModel work goes in `viewModelScope`; it cancels automatically.
- Never collect a Flow in `init {}` without a scope.
- CPU-bound work: `withContext(dispatchers.default)`. I/O: `withContext(dispatchers.io)`. Emit back on
  main. Use `flowOn` to set upstream context, and keep the collector on main.
- **Desktop needs `kotlinx-coroutines-swing` on the JVM target** or `Dispatchers.Main` throws
  `IllegalStateException` at first use — a first-run crash, not a subtle bug. Add it to `desktopMain`.
- Suspend functions must be main-safe: a caller should never need to know which dispatcher to use.
- Cancellation is cooperative — do not swallow `CancellationException`. If you `catch (e: Exception)`,
  rethrow it explicitly.

---

## Networking — Ktor

- **One `HttpClient` for the app's lifetime.** Constructing per request throws away connection pooling
  and TLS session reuse. Provide it as a Koin singleton and close it on shutdown.
- Do **not** name an engine in `commonMain`. Let each platform resolve its own — OkHttp on Android,
  Darwin/`URLSession` on iOS, OkHttp or CIO on Desktop.
- **Install `HttpRequestRetry` before `HttpTimeout`**, or timeouts will not be retried. Use
  `retryOnServerErrors()` with `exponentialDelay()`.
- Keep retry and auth separate. Ktor's `Auth` plugin runs its own 401 cycle and does not pass through
  `HttpRequestRetry`; wiring token refresh into backoff produces infinite-loop bugs.
- Set all three timeouts explicitly: `requestTimeoutMillis`, `connectTimeoutMillis`,
  `socketTimeoutMillis`. A request with no timeout is a hang waiting to happen.
- Centralise failure translation in `HttpResponseValidator` — one place converts status codes and
  exceptions into `AppError`. No scattered `try/catch` in repositories.
- `ContentNegotiation` with `Json { ignoreUnknownKeys = true }`. GitHub adds fields; that must never
  crash the client.
- **Logging level `NONE` in release.** Never log headers, tokens, or full response bodies.

---

## GitHub API — three traps

**1. `watchers_count` is stars, not watchers.** It is a frozen alias kept from when starring was
called watching, and returns `stargazers_count` on **both** the search and repo endpoints. The spec
requires stars and watchers as *separate* fields, so binding both from one response shows identical
numbers on every repository. The real value is **`subscribers_count`**, available **only** from
`GET /repos/{owner}/{repo}`. Fetch it on tap. Verify against a repository where the two genuinely
differ.

**2. Rate limits are asymmetric and low.**

| API | Unauthenticated | With PAT |
|---|---|---|
| `search/repositories` | 10 / **minute** | 30 / minute |
| `GET /repos/{owner}/{repo}` | 60 / **hour** | 5,000 / hour |

**The reviewer will run this app without a PAT, so 10/minute is the real budget.** That decides how
search is triggered:

- **Search fires on explicit submit** — the IME search action or the search button. Not on every
  keystroke. The spec asks that a keyword「入力できる」and that it「検索できる」; it never asks for
  search-as-you-type, and burning 6 of 10 requests typing one word turns the rate-limit error state
  from an edge case into the default experience.
- If incremental search is kept as an affordance, it needs **all** of: a minimum query length, a
  debounce no shorter than the pinned value in *Fixed parameters*, and cancellation of the in-flight
  request on every change.

Pagination: search caps at **1,000 results** and GitHub rejects any request where
`page × per_page > 1000`. With the pinned `per_page` that ceiling is not reachable exactly — compute
the last valid page from `per_page` rather than hardcoding a number, and stop there cleanly instead
of letting the API return 422.

Rate-limit signals come in **two** shapes, and both must be handled:
- **Primary** limit — 403 or **429** with `x-ratelimit-remaining: 0`; the reset instant is in
  `x-ratelimit-reset` (unix seconds).
- **Secondary** limit — 403 or 429 with a **`retry-after`** header (delta seconds) and *no* useful
  `x-ratelimit-reset`. Convert it against the injected `Clock`.

A missing, empty, or non-numeric header must degrade to a generic error, never crash or produce an
`Instant` in the past.

Also: `open_issues_count` includes pull requests. Label it accurately or document it.

**3. The search index lags the repository record.** `search/repositories` serves a cached index, so
its `stargazers_count` / `forks_count` / `open_issues_count` can be behind
`GET /repos/{owner}/{repo}`. Build the detail model **entirely** from the detail response — never
carry search-time numbers onto the detail screen beside a freshly fetched `subscribers_count`, or the
screen shows two different ages of truth.

---

## Security

Treat every item as a review gate, not a wish list.

- **No secrets in version control, ever.** The optional GitHub PAT is read from `local.properties` or
  an environment variable, injected at build time, and gitignored. The app must work fully without it.
- Never log a token, an `Authorization` header, or a full response body. Release builds log nothing
  sensitive at any level.
- **HTTPS only.** `android:usesCleartextTraffic="false"`; no exceptions, no debug-only bypasses that
  can leak into a release variant.
- Validate and encode user input before it reaches the `q` parameter. Never build a URL by string
  concatenation — use Ktor's `parameters` so encoding is handled.
- Release builds: R8 enabled, `minifyEnabled` and `shrinkResources` on, debug symbols stripped.
- No debug affordances behind a release flag. If a feature is dev-only, it is excluded by variant, not
  by an `if`.
- If credentials ever need persisting, use platform secure storage (Android Keystore /
  iOS Keychain — via KVault or Kissme). This app persists none; keep it that way.
- Pin dependency versions. Review anything new for licence and maintenance status before adding it.
- Reference: OWASP MASVS / Mobile Top 10 for the threat checklist.

---

## Compose performance

- **Strong skipping** is on by default (Compose 1.7+). It only helps when parameters are stable —
  so keep them stable.
- `List`, `Set` and `Map` are **unstable** to the compiler. Use `kotlinx.collections.immutable`
  (`ImmutableList`, `persistentListOf`) for state that is passed into composables, or annotate with
  `@Immutable`.
- UI state is an immutable `data class` holding stable types. No `var`, no `MutableList` in state.
- `LazyColumn`: always supply a stable `key`, and `contentType` when rows differ. Without a key,
  insertions re-compose and lose animation and scroll position.
- `derivedStateOf` for values computed from other state, so downstream readers don't recompose on
  every upstream change.
- **Defer state reads to the narrowest scope.** Prefer lambda-taking modifiers (`Modifier.offset { }`,
  `graphicsLayer { }`) so a changing value causes layout/draw rather than recomposition.
- Hoist lambdas so they stay referentially stable across recomposition.
- **Stateful screen + stateless content:** `SearchScreen` collects state and passes it to a
  `SearchContent` that takes plain parameters and lambdas. The content is previewable and directly
  testable; the screen holds the wiring.
- Images through Coil 3 (KMP) with memory and disk caching, sized to the target — never load a
  full-resolution avatar into a 40dp circle.
- **Measure before optimising.** Compose compiler metrics and recomposition counts, not intuition.

---

## Kotlin style

- Value classes (`@JvmInline`) for identifiers and meaningful primitives. No bare `String` repo ids.
- Sealed interfaces for state and errors so `when` is exhaustive with no `else`. An `else` in a domain
  `when` silently swallows the next variant you add.
- **No `!!`. No unchecked casts. No empty `catch`.** Handle nullability at the mapper boundary so the
  UI layer never sees it.
- Immutable by default: `val`, read-only collection types, `data class` for state.
- `expect`/`actual` only where a platform genuinely differs. Prefer an interface with per-platform
  implementations injected via Koin — it is easier to fake in tests.
- Explicit API mode on library modules, so public surface is deliberate.
- Comments explain **why**, never what. KDoc on every public declaration.

---

## Error handling

One sealed hierarchy in `:core:common`, mapped to localised messages in the UI layer:

```kotlin
sealed interface AppError {
    data object Network : AppError
    data class RateLimited(val resetAt: Instant) : AppError
    data object NotFound : AppError
    data object Unauthorized : AppError
    data class Serialization(val cause: String) : AppError
    data class Unknown(val cause: String) : AppError
}
```

The result wrapper alongside it is **`Outcome<T>`**, never `Result`. A custom `Result` in
`commonMain` shadows `kotlin.Result` — the collision is silent at the import line and confusing at
every call site.

- Every screen models **loading / empty / error / content** as distinct states. No silent failure, no
  spinner that never resolves, no empty state that is indistinguishable from a failed one.
- `RateLimited` carries a real `Instant`, resolved from `x-ratelimit-reset` or from `retry-after`
  against the injected `Clock` — never a generic message.
- Every error state carries a working retry.
- `CancellationException` is not an `AppError`. It propagates; it is never mapped, logged, or shown.

---

## UI requirements

These map directly to graded items — treat them as acceptance criteria, not polish.

- **Theme:** Material 3, token-driven, defined once in `:core:designsystem`. Dynamic color on
  Android 12+. No hardcoded colors or dp values in feature code.
- **Dark mode:** follows the system, plus a manual override. Every screen verified in both.
- **Adaptive:** `WindowSizeClass` drives layout; list-detail on expanded widths. State survives
  rotation via `rememberSaveable` and the ViewModel.
- **Localisation:** ja and en, resolved from the **platform locale** — no in-app language picker.
  CMP resources resolve from the system locale on every target; a runtime override needs per-platform
  work on all three and buys no evaluation row. Every user-facing string comes from resources — a
  hardcoded string in a composable is a review blocker. Locale-aware number formatting has no stdlib
  API in KMP, so it is one small `expect`/`actual` (`NumberFormat` on JVM/Android,
  `NSNumberFormatter` on iOS) — pinned in *Fixed parameters*, not improvised per call site.
- **Motion:** shared-element transition list → detail, `animateItem()` on list changes,
  `AnimatedContent` between states, shimmer skeletons, haptics on Android/iOS. Motion must respect
  reduce-motion settings and never block input.
- **Accessibility:** content descriptions on every icon and image, 48dp minimum touch targets, and
  semantics that make the `runComposeUiTest` suite possible to write.

---

## Navigation

Navigation 3 for Compose Multiplatform (`org.jetbrains.androidx.navigation3:navigation3-ui:1.1.1`,
supported since CMP 1.10). `navigation3-common` arrives transitively — do not declare it.

- Routes are `@Serializable` and implement `NavKey` — type-safe, no string routes.
- **A route carries identifiers, never a domain object.** The detail key is
  `DetailKey(owner: String, name: String)` and the ViewModel fetches from those. Serialising a whole
  `RepositorySummary` into the back stack would persist stale search-index numbers across process
  death and couple navigation to the domain model.
- Back stack is a `SnapshotStateList` the UI observes; render it with `NavDisplay`.
- If Nav 3 fights a platform during integration, fall back to Navigation 2.9.x with type-safe
  `@Serializable` routes and record the reason in an ADR. Do not burn days on it.

---

## Git

### GitHub Flow — the branching model

**GitHub Flow, without exception.** `main` is always green and always deployable; every change
reaches it through a short-lived branch and a pull request. There is no direct commit to `main`, not
even for a one-line documentation fix, and not for the very first change in the repository.

The loop, every time:

1. **Branch off an up-to-date `main`.** `git switch main && git pull --ff-only` first — branching
   from a stale `main` is how you get a merge conflict you did not need.
2. **Commit on the branch**, following *How the work gets divided into commits* below.
3. **Push the branch** and open **one PR** for it, with a description covering the four checks in
   *Role and standard* and the TDD scenario list for the work.
4. **CI must be green** and the AI review must have run and been addressed.
5. **Merge with a merge commit or rebase — never squash**, then **delete the branch**.
6. `git switch main && git pull --ff-only` before starting the next unit.

Rules that make the flow hold:

- **One branch, one unit of work, one PR.** If a branch grows a second purpose, it becomes two
  branches. A PR that needs the reviewer to hold two unrelated ideas in mind is too big.
- **Branch names:** `<type>/<kebab-case-summary>`, where `<type>` is the Conventional Commit type
  the branch is mostly about — `feature/`, `fix/`, `refactor/`, `test/`, `docs/`, `ci/`, `build/`,
  `chore/`. Examples: `feature/search-viewmodel`, `ci/build-and-lint`, `docs/planning-baseline`.
- **Branches are short-lived.** A branch that outlives its PR review has drifted. Bring it up to
  date from `main` by rebase before it is pushed, or by merge after — never let it sit.
- **Never force-push `main`.** Force-pushing your own unreviewed feature branch is fine; once a PR
  has review comments on it, stop rewriting the branch.
- **The PR is a deliverable, not a formality.** 中「PR 機能などの利用」is graded on it, so it gets a
  real description, the right labels, and a link to the ADR or issue when one exists.

This applies from commit one. The planning documents go on a `docs/*` branch through a PR like
everything else — starting the history with direct pushes to `main` would contradict the model on
the very first line of `git log`, which a reviewer reads first.

### The agent never writes history

**Every commit, push, branch creation and PR is performed manually by the repository owner.** The
agent's job ends at a reviewed working tree and a proposed commit plan. This is not a preference to
be optimised away when a change feels small or obvious.

Concretely, the agent must **never** run: `git commit`, `git push`, `git merge`, `git rebase`,
`git tag`, `git branch`, `git checkout -b`, `git switch -c`, `git reset --hard`, `git stash`,
`gh pr create`, `gh pr merge`, or anything else that mutates history, refs, or the remote.
`git status`, `git diff`, `git log` and `git show` are fine and expected — reading is how the plan
gets built.

Instead, hand over a **branch-and-commit plan**: the branch to cut, the commits to make on it, and
the PR to open. In the order they should be done:

```
Branch: <type>/<kebab-case-summary>        off an up-to-date main

Commit N — <one-line purpose>
  Files:   exact paths, grouped, nothing implied by "and related files"
  Message: <Conventional Commit subject, ≤72 chars>

           <body: why, not what. Wrap at 72. Reference the ADR or issue if one exists.>
  Verify:  the exact command(s) that should be green before this is committed
  Notes:   anything the reviewer should look at closely, or any known follow-up

PR: <title>
  Body:    the four Role-and-standard checks, the TDD scenario list, and what a
           reviewer should look at first
  Labels:  <labels>
```

Then stop and wait. Do not proceed to the next unit of work on the assumption that the previous
commit was made, the branch was pushed, or the PR was merged.

### How the work gets divided into commits

The division is the deliverable, not an afterthought — 高「適切なコミット粒度」is graded on it, and a
reviewer's willingness to read a commit is inversely proportional to how many unrelated things it
touches. Rules, in priority order:

1. **One purpose per commit.** A commit answers exactly one "why". If the message needs an "and", or
   a bulleted list of unrelated changes, it is two commits.
2. **Every commit builds and its tests pass on its own.** The history stays bisectable. A commit
   that only compiles once the next one lands is a broken commit, however tidy it looks.
3. **The TDD cycle is the natural unit** — a failing test plus the production code that makes it
   pass plus the refactor, as one green commit. Do **not** split test and implementation into
   separate commits: a commit whose tests are red is exactly what rule 2 forbids.
4. **Mechanical changes never travel with behavioural ones.** A rename, a reformat, a version bump,
   or a file move goes in its own commit, so the diff a reviewer must actually think about is not
   buried in 400 lines of noise. This is the single highest-value split.
5. **Separate the scaffolding from the substance.** Adding a module, a dependency, or a CI job is
   its own commit before the code that uses it.
6. **Order commits so each one is readable in sequence** — types and errors before the code that
   returns them, the port before the implementation, the ViewModel before the screen.
7. **Keep them small enough to review in one sitting.** If a commit's diff cannot be held in your
   head, split it by rule 1 or 4 until it can.
8. **Never mix a fix with a feature**, and never smuggle an unrelated cleanup into either. If a
   cleanup is worth doing, it is worth its own commit.

Conventional Commits, and the type must be truthful: `feat` adds behaviour, `fix` corrects it,
`refactor` changes neither, `test`, `docs`, `chore`, `ci`, `build`, `style`. A `refactor` whose diff
changes behaviour is a mislabelled `feat` and defeats the purpose of the convention.

### Repository conventions

- **Conventional Commits**, atomic. One logical change per commit — the history is graded.
- With TDD the natural commit boundary is a green refactored cycle, not a half-finished feature.
- **Merge with `--no-ff` (a merge commit) or rebase-merge. Never squash.** 高「適切なコミット粒度」is
  graded on `main`'s history, and the evidence for it is one green TDD cycle per commit. Squashing 19
  PRs leaves 19 commits on `main` and buries the cadence inside closed PRs, where it is far less
  likely to be looked at. This is a repository setting, so set it in PR1 and never rely on
  remembering it at merge time.
- **Branch protection on `main`, configured as:** require the CI status checks to pass, require a PR
  (no direct pushes), block force-push and deletion, and require branches to be up to date.
  **Do not require approvals** — this is a solo submission and GitHub will not let you approve your
  own PR, so a required reviewer locks the repo. Do not enable auto-merge-by-squash.
- One PR per branch with a real description covering the four checks in *Role and standard*.
- Never commit: `local.properties`, secrets, `.kotlin/`, build outputs, `xcuserdata/`, `.DS_Store`.

---

## Guardrails — stop and ask

These are hard stops. Do not reason your way past one; surface it and wait.

| Situation | Action |
|---|---|
| A `CLAUDE.md` rule conflicts with the assignment spec | **The spec wins.** Follow it and flag the conflict |
| A spike passes its 2-hour timebox | Take the documented fallback, write the ADR, move on |
| A pinned version needs changing | Stop. It is an ADR and a deliberate PR, never an in-flight bump |
| You cannot write a test that fails first | Stop and say so — do not write the production code anyway |
| A secret would need to be written or committed | Stop. Values are injected at build time, never authored |
| Work would exceed the 30 evaluation rows | Stop and ask. Scope creep is not seniority |
| A test is red and the quickest fix is changing the test | Stop. Fix the code, unless the test itself encodes a wrong expectation — and say which |
| **A pinned version or coordinate does not resolve** | Stop. Do **not** silently substitute the nearest number — that is how a "verified" table rots. Report what failed, propose the change as an ADR |
| A pinned library is stable but its *pairing* is unverified (e.g. nav3 1.1.1 × CMP 1.12.0) | Spike it inside the 2-hour timebox, then take the fallback and ADR it |
| The work is finished and committing it is the obvious next step | **Stop.** Present the branch-and-commit plan and wait. Commits, pushes and PRs are the owner's, always — see *Git* |
| A change is small enough that a branch and PR feel like overhead | **Stop.** GitHub Flow has no exception for small. Propose the branch |
| A commit would need to mix two purposes to keep moving | Stop. Split it and say where the seam is. Do not commit the mixture and promise to tidy up later |

**Never, under any circumstance:**
- Run `git commit`, `git push`, `git merge`, `git rebase`, `git branch`, `gh pr create`, or anything
  else that mutates history, refs, or the remote. Propose; never execute.
- Force-push or rewrite history on `main`.
- Delete, skip, `@Ignore`, or weaken an assertion to reach green.
- Commit `local.properties`, keystores, provisioning profiles, or service-account JSON.
- Add a dependency without checking licence and maintenance status.
- Leave a `TODO` in merged code without a linked issue.

## Automation — skills and hooks

### Skills to invoke automatically

| Trigger | Skill | Why |
|---|---|---|
| Canon TDD **step 4 (refactor)** | `/simplify` | It is exactly the refactor pass: reuse, simplification, efficiency — quality only, no behaviour change. Run it inside the green window, never while a test is red |
| Before opening **any** PR | `/code-review` | Catches correctness bugs before the AI reviewer and a human see them |
| PRs touching network, auth, secrets, build variants or deploy | `/security-review` | PR4, 14, 16, 18 at minimum |
| Building or restyling Compose UI | `mobile-android-design` | Material 3 and Compose patterns, so the theme work is idiomatic rather than improvised |
| Before marking a UI PR done | `/run` | Verify in the real app, not only in tests — screenshots also feed the README |

### Hooks

`.claude/settings.json` is wired with a `PreToolUse` guard (`.claude/hooks/guard-secrets.sh`) on
**`Write|Edit|NotebookEdit` and `Bash`** that **blocks** any agent write to secret or signing
material: `local.properties`, `secrets.properties`, `signing.properties`, `*.jks`, `*.keystore`,
`*.p12`, `*.p8`, `*.pem`, `*.key`, `*.cer`, `*.mobileprovision`, `id_rsa*`, `*.xcconfig`, `.env*`,
`google-services.json`, `GoogleService-Info.plist`, and any `service-account*.json`.

Four properties that matter more than the pattern list:
- **It covers `Bash` too.** A guard that only matches `Write|Edit` is bypassed by
  `cat > local.properties <<EOF`, which is the *normal* way an agent edits files in some modes. A
  file-tool-only guard is theatre.
- **It matches bare relative paths**, not just absolute ones.
- **It blocks reads as well as writes.** `cat local.properties` creates no secret but copies one
  into the transcript, which is its own disclosure.
- **It fails closed.** Unparseable input, missing `python3`, missing script — all block. A security
  control whose error path is "allow" is worse than no control, because it is trusted.

Implementation is `guard_secrets.py` behind the `.sh` wrapper, with a self-test:

```bash
python3 .claude/hooks/guard_secrets_test.py     # 49 cases, both directions
```

The self-test exists because this runs on **every** Bash call: a false negative ships a secret, and
a false positive locks the agent out of its own repository. The allow-cases are load-bearing — PR1
has to write `local.properties` into `.gitignore`, and `grep local.properties .gitignore` searches
for the string rather than opening the file. Both stay allowed; `grep token local.properties` does
not. If you change the patterns, run the self-test and add a case in each direction.

Defence in depth: the gitignore stops these being committed, the hook stops them being written at
all. Neither is a substitute for the other. If the hook ever blocks something legitimate, create the
file by hand outside the agent — do not weaken the pattern list to get past it.

Add in PR1, once Gradle exists:
- `PostToolUse` on `*.kt` / `*.kts` → format the changed file with the **ktlint CLI**, not
  `./gradlew ktlintFormat`. A Gradle invocation per edit pays daemon and configuration cost on every
  keystroke-sized change; the CLI is sub-second and the Gradle task stays as the CI gate.
- A pre-commit guard running the lint gate on staged Kotlin files only.

---

## Definition of done

A change is done when **all** of these hold:

1. A failing test existed first, and it now passes.
2. Normal, failure, and boundary cases are covered.
3. The lint gate is clean — `./gradlew ktlintCheck lint`, plus `detekt` if the PR1 spike kept it.
4. Android and Desktop build and run; iOS builds (or its gap is recorded).
5. Dark mode and rotation verified on any touched screen.
6. No hardcoded user-facing strings.
7. No secret, token, or sensitive value is logged or committed.
8. The four checks in *Role and standard* are answered in the PR description.
9. `/simplify` has run on the refactor step and `/code-review` before the PR opens.
10. Every value it introduces is either in *Fixed parameters* or newly added there with a reason.
11. A **branch-and-commit plan** has been handed over — the branch name, one purpose per commit,
    each commit independently green, a Conventional Commit message and verification command for
    each, and the PR title and body. The agent has not run a single `git` command that writes.

---

## References

**TDD**
- [Canon TDD — Kent Beck](https://newsletter.kentbeck.com/p/canon-tdd)
- [Test Driven Development — Martin Fowler](https://martinfowler.com/bliki/TestDrivenDevelopment.html)
- [Test Double — Martin Fowler](https://martinfowler.com/bliki/TestDouble.html)
- [Mocks Aren't Stubs — Martin Fowler](https://martinfowler.com/articles/mocksArentStubs.html)

**Kotlin Multiplatform**
- [Recommended KMP project structure](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html)
- [A New Default Project Structure for KMP — JetBrains](https://blog.jetbrains.com/kotlin/2026/05/new-kmp-default-structure/)
- [Test your multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)
- [Testing Compose Multiplatform UI](https://kotlinlang.org/docs/multiplatform/compose-test.html) — the `runComposeUiTest` constraints above
- [Koin #2297 — Koin inside CMP common UI tests](https://github.com/InsertKoinIO/koin/issues/2297) — why UI tests target stateless content
- [Multiplatform ViewModel](https://kotlinlang.org/docs/multiplatform/compose-viewmodel.html)
- [Navigation 3 in Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html)
- [CMP 1.12.0 release notes](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.0) — source of the AndroidX port versions
- [Compose Multiplatform compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)

**Git**
- [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)

**Compose performance**
- [Stability in Compose](https://developer.android.com/develop/ui/compose/performance/stability)
- [Compose performance — curated internals](https://github.com/skydoves/compose-performance)

**Ktor**
- [Client engines](https://ktor.io/docs/client-engines.html)
- [Retrying failed requests](https://ktor.io/docs/client-request-retry.html)
- [Timeout](https://ktor.io/docs/client-timeout.html)

**Security & API**
- [OWASP MASVS](https://mas.owasp.org/MASVS/)
- [GitHub REST rate limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)
- [watchers vs subscribers](https://github.com/orgs/community/discussions/24795)
