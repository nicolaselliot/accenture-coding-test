# ADR-0007: Drop the Intel iOS simulator target (`iosX64`)

- **Status:** Accepted
- **Date:** 2026-09-06

## Context

`githubsearch.kmp.library` declared three iOS targets — `iosX64`, `iosArm64`,
`iosSimulatorArm64` — chosen before any module depended on Compose. `:core:designsystem` (PR7) is
the first module that does, and it does not resolve:

```
Couldn't resolve dependency 'org.jetbrains.compose.runtime:runtime:1.12.0' in 'commonMain'
for all target platforms.
The dependency should target platforms: [android, desktop, iosArm64, iosSimulatorArm64, iosX64]
Unresolved platforms: [iosX64]
```

Verified against Maven Central rather than inferred from the error:

| Artifact | HTTP |
|---|---|
| `org.jetbrains.compose.runtime:runtime-iosx64:1.12.0` | **404** |
| `org.jetbrains.compose.runtime:runtime-iosarm64:1.12.0` | 200 |
| `org.jetbrains.compose.runtime:runtime-iossimulatorarm64:1.12.0` | 200 |

`runtime-iosx64`'s `maven-metadata.xml` lists **`1.11.0-alpha01`** as its newest version of any
kind. Compose Multiplatform stopped publishing for the Intel iOS simulator after that release and
has not resumed.

The toolchain table already recorded that CMP 1.12.0 is "64-bit only", but that note was never
traced to the target list, and it reads as harmless because `iosX64` *is* 64-bit. The target it
actually removes is the **Intel-Mac iOS simulator**, which is a host-architecture concern, not a
word size.

This is the *unverified pairing* case in `CLAUDE.md` → *Guardrails*: the pinned version is stable
and correct, and what was unverified was CMP 1.12.0 × the declared target set. The spike took
minutes, well inside the two-hour timebox.

## Decision

Remove `iosX64()` from the `githubsearch.kmp.library` convention plugin. The project targets
`iosArm64` (device) and `iosSimulatorArm64` (Apple Silicon simulator).

The target is dropped project-wide rather than only in Compose-bearing modules. The reason is not
how many modules use Compose — only four of the nine on this convention plugin do — but that **no
application binary can link `iosX64` at all** once the UI cannot build for it. A target that no
application can consume produces artifacts with no consumer in *any* module, Compose-bearing or
not, so an `iosX64` klib from `:domain` or `:core:common` is dead weight rather than coverage.

`gradle/libs.versions.toml` is untouched. No pinned version changes — this is a target-set decision,
which is why it is recorded here rather than resolved by a version bump.

## Consequences

- **Cost:** the app cannot be run on an iOS simulator hosted on an Intel Mac. Apple stopped selling
  Intel Macs in 2023 and Xcode 26.6 (our pin) requires Apple Silicon to run the iOS 26 simulators
  at all, so the practical cost is zero for this project. The development machine is `arm64`.
- PR17/PR18 target `iosArm64` and `iosSimulatorArm64`. The PR18 UI-test task named in the plan,
  `:feature:*:iosSimulatorArm64Test`, is unaffected — it was already the Apple Silicon target.
- The 低「3つ以上のプラットフォーム」row is unaffected: iOS still ships, on device and simulator.
- One fewer target to compile, so the iOS leg of CI gets slightly faster.
- `config/detekt/detekt.yml` still lists `iosX64` under `MatchingDeclarationName.multiplatformTargets`.
  That is detekt's list of filename suffixes it tolerates, not a declaration of our targets, so it is
  left alone; trimming it would be unrelated churn in a build commit.
- `.github/workflows/ci.yml`'s comment on the `build` step said the macOS runner compiles "the iOS
  klibs for all three targets"; it is now two. Corrected in the same commit — a stale count in a
  comment is exactly the drift that produced this ADR.

## Alternatives considered

- **Downgrade Compose Multiplatform to a version that publishes `iosX64`.** The only such version is
  `1.11.0-alpha01`. `CLAUDE.md` forbids shipping an alpha to chase a coordinate, and this would trade
  a stable UI toolkit for a dead simulator architecture. Rejected.
- **Keep `iosX64` and exclude Compose from it.** The entire UI is Compose Multiplatform and is shared
  across all targets by design; a target that cannot compile the UI compiles nothing worth linking.
  Rejected.
- **Keep `iosX64` only on the non-Compose modules** (`:core:common`, `:domain`). Produces klibs no
  application can consume, and leaves the target set differing per module for no benefit. Rejected.
