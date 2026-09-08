# ADR-0011: Select one build flavor per Gradle invocation

- **Status:** Accepted
- **Date:** 2026-09-08

## Context

The assignment grades 低「Build Variant」, and PR14 delivers it: `dev`/`prod` × `debug`/`release`,
feeding the `AppConfig` generator built in PR1. Two facts decide the shape of that, and they pull
against each other.

**One: shared code is compiled once per invocation.** `AppConfig` is generated into
`:core:common`'s `commonMain`, which is what lets `:core:network` take the token as a constructor
parameter instead of learning that Gradle exists. But `commonMain` is compiled once for every
target — and, on Android, once for every variant. A single Gradle invocation therefore cannot hold
two different configurations. `com.android.kotlin.multiplatform.library` has no build types or
product flavors of its own to vary them by, either.

**Two: only Android has variants at all.** Desktop is one JVM target; iOS gets its configurations
from Xcode, and `iosApp` does not exist until PR17. So the axis cannot be an Android concept if the
other two platforms are to have it.

Left alone, those two produce the defect this ADR exists to prevent. `./gradlew build` assembles
every variant, so a `prodRelease` APK would be compiled against whatever the *dev* flavor was
configured with — including a developer's personal access token, sitting in a release binary where
`strings` recovers it. The PR1 convention plugin already promised the opposite: *"PR14 introduces
the release variants, and that is where a release build must refuse to embed one."* A promise that
holds only when nobody runs `./gradlew build` is not a control.

## Decision

**The flavor is a property of the invocation, not of the variant, and the build refuses to produce
an artifact that disagrees with it.**

- One flavor is selected per invocation, first source present winning:
  `-Pgithubsearch.flavor=<flavor>` → `GITHUBSEARCH_FLAVOR` → `flavor=<flavor>` in the developer
  properties file → `dev`. An unrecognised or blank name fails the build and names the valid ones;
  it does **not** fall back to `dev`, because `dev` is the flavor that may embed a token.
- **`prod` normalises what it carries.** The generator drops a configured token and forces the log
  level to `NONE`, rather than trusting whoever runs the release job to have a clean environment.
  Both rules are unit-tested against the real task in `build-logic`.
- **`:androidApp` builds only the selected flavor's variants** (`beforeVariants { enable = … }`).
  The mismatched combination becomes unbuildable rather than merely discouraged.
- Android identity per flavor, by variant rather than by a runtime `if`: `dev` takes
  `applicationIdSuffix = ".dev"`, `versionNameSuffix = "-dev"` and its own launcher label from
  `src/dev/res`. The two install side by side and are distinguishable in the launcher.
- Desktop takes the same flavor from the same property and names its jpackage distribution after it
  (`GitHubSearchDev` / `GitHubSearch`), for the same reason the Android `applicationId` is
  suffixed: two installs must not overwrite each other.
- `release` keeps R8, `minifyEnabled` and `shrinkResources` from PR1. Signing arrives with PR16.
- **iOS is deferred to PR17**, where `iosApp` is created. The flavor maps onto an Xcode
  configuration with a `.dev` bundle-identifier suffix, and the agent cannot write `*.xcconfig` —
  the secrets hook blocks it — so it is the owner's step, taken with the project that needs it.

## Consequences

- **A prod artifact cannot carry a dev configuration.** Not "does not", cannot: the variant does
  not exist in an invocation configured for dev. That is the whole point of the filter.
- **A flavor must be named to build the other one.** `./gradlew assembleProdRelease` on its own
  cannot work; it needs `-Pgithubsearch.flavor=prod`. This is the real cost of the decision, so it
  is answered where it is met rather than only in documentation: a task rule in the Android
  convention plugin turns any task name carrying the unselected flavor into a failure that states
  the property to add. `gradle.properties` documents the property, an unrecognised flavor name
  names all three ways to select one, and CI builds both.
- **Android Studio's Build Variants selector shows only the selected flavor's variants**, because
  the others are not declared to AGP at all. Switching means changing the property and re-syncing —
  and, per the point below, recompiling. That is the same cost as the CLI, but it lands in the tool
  a reviewer opens first, so it is worth knowing before wondering where `prodRelease` went.
- **CI assembles the prod release variant in a second invocation**, on the ubuntu runner only. The
  flavor changes what is compiled into the APK, not how a host compiles it, so once is enough — but
  without it the only Android variants CI ever assembled would be the two nobody installs.
- **Switching flavors recompiles the Kotlin graph.** `AppConfig` is an input to `:core:common`, so
  everything downstream of it rebuilds. Inherent to build-time configuration in `commonMain`, not
  to this decision; it is why the CI step is separate rather than folded into the matrix.
- **The build type is invisible to shared code**, so the flavor — not the build type — is the axis
  that decides logging and the token. Logging is **off by default on both flavors**, and the flavor
  decides only whether an explicit override is honoured. So with nothing configured, all four
  variants are silent; with `logLevel` set, `devDebug` **and `devRelease`** honour it, and both dev
  variants embed a configured token. `CLAUDE.md`'s "logging level NONE in release" is therefore
  guaranteed for the two prod variants by construction and for the dev pair by default, not by
  construction — the price of one `AppConfig` per invocation, and the reason a dev artifact is
  `.dev`-suffixed and never distributed.
- **`AppConfig.FLAVOR` has no runtime consumer, deliberately.** Everything the flavor decides is
  decided at packaging time or at generation time. `CLAUDE.md` requires a dev-only affordance to be
  excluded by variant rather than gated by an `if`, and a build marker in the UI would be exactly
  that `if`. The constant stays as provenance, and the generator's log line reports it.
- Four variants are declared and three combinations are reachable per invocation pair; nothing
  about the matrix is hidden from a reviewer reading the convention plugin.

## Alternatives considered

- **Per-variant configuration through AGP's `BuildConfig`, with the values reaching shared code at
  runtime.** The architecturally correct answer, and the one to take if a flavor ever has to differ
  at runtime: AGP generates `BuildConfig` per variant, so all four variants could be built in one
  invocation with correct values, and no filter would be needed. Rejected because it buys nothing
  this app needs and costs a great deal: the token and log level are the only values that vary,
  neither is read after startup, and it would mean three separate configuration mechanisms — AGP
  `buildConfigField` for Android, a second generator for Desktop, an xcconfig for iOS — replacing
  the single crossing point PR1 built and the plan says to feed. Written down because the trade-off
  reverses the moment a flavor needs a different base URL at runtime.
- **Leave all four variants enabled and accept the mismatch**, on the grounds that CI holds no
  token so a distributed artifact could never carry one. Rejected: it makes a locally built
  `prodRelease` APK carry the developer's PAT, and "the artifact you can build by accident is
  wrong, but the one we distribute is fine" is not a security control.
- **Fail the build when a prod variant is assembled with a dev configuration.** Same guarantee,
  worse ergonomics: `./gradlew build` — the pre-commit gate — would fail on any machine that has a
  token configured, and the way out is to build with a flavor that drops the token from *both*
  flavors' artifacts. A gate that fires on the everyday command trains people to bypass it.
- **Derive the flavor from the requested task names**, so `assembleProdRelease` selects prod by
  itself. Tempting, and it would remove the ergonomic cost entirely. Rejected as too clever to
  trust: substring matching on task names is ambiguous for `build`, silently wrong for an
  invocation naming both flavors, and invisible when it guesses wrong.
- **Suffix the desktop `packageVersion` instead of the `packageName`.** jpackage requires a strict
  `MAJOR.MINOR.PATCH` and rejects a suffix outright.
