# ADR-0003: Lint gate is ktlint plus detekt, with detekt's multiplatform limits recorded

- **Status:** Accepted
- **Date:** 2026-09-05

## Context

The lint gate had one open question: whether detekt could be used at all. The concern was concrete —
detekt's stable line, 1.23.8, publishes a compatibility table that stops at Kotlin 2.0.21, and the
only detekt built against Kotlin 2.4.10 is `2.0.0-alpha`, which itself targets Gradle 9.6.1 and AGP
9.3.1, both behind this project's pins. The plan therefore expected to drop detekt and gate on
ktlint plus Android Lint, and treated that as a documented outcome rather than a failure.

The spike found something more useful than a yes or no.

**detekt 1.23.8 works on Kotlin 2.4.10 and Gradle 9.7.1.** It resolves, applies, and analyses. Its
syntactic rules run correctly on `commonMain`, and its type-resolution rules run correctly on
platform source sets: a bare `!!` in `src/desktopMain` is reported as `UnsafeCallOnNullableType` by
`detektDesktopMain`.

**But type resolution does not reach `commonMain`.** The Gradle task descriptions state it plainly:
`detektAndroidMain` and `detektDesktopMain` are described as running "with type resolution", while
every `detektMetadata*` task — the tasks that analyse `commonMain`, `iosMain` and the other shared
source sets — is not. This project keeps essentially all of its code in `commonMain` by design, so
the type-resolution rules are dead exactly where the code lives. The same `!!` that
`detektDesktopMain` catches in `desktopMain` is not reported when it sits in `commonMain`.

Two further findings came out of the spike, both of which would otherwise have produced a gate that
passes without checking anything:

1. **detekt does not discover KMP source sets.** Its default source layout is the JVM one
   (`src/main/kotlin`), so on a multiplatform module every detekt task reports `NO-SOURCE` and the
   build goes green having analysed nothing.
2. **The default config's `**/test/**` exclusion matches parent directories.** Fifteen default rules
   — `MagicNumber` and `UnsafeCallOnNullableType` among them — exclude `**/test/**`, and detekt
   matches that glob against the *absolute* path. This repository is checked out below a directory
   named `test`, so every one of those rules was silently disabled. Setting `basePath` does not fix
   it; `basePath` only affects how report paths are rendered.

The second finding is the dangerous one, because it is environment-dependent: it disables rules on
this machine and not on a CI runner, so local and CI results disagree with nothing to indicate why.

## Decision

The lint gate is **ktlint 1.8.0** (via ktlint-gradle 14.2.0) **and detekt 1.23.8**, both applied to
every module through the `githubsearch.lint` convention plugin, and both failing the build rather
than warning.

detekt is configured with three corrections that the spike showed are load-bearing:

- the KMP source sets are named explicitly via `source.setFrom(...)`, or detekt analyses nothing;
- `config/detekt/detekt.yml` is committed with two corrections to the exclusion lists of the fifteen
  rules that carry them. The bare `**/test/**` pattern is **removed**, because it is a JVM-layout
  leftover whose only effect here is to disable rules by accident. And `**/desktopTest/**` is
  **added**: the stock list names `**/jvmTest/**`, but this project names its JVM target `desktop`
  (`jvm("desktop")`, so that task names read `desktopMain` / `desktopTest`), which means the stock
  pattern matches nothing and every desktop test would otherwise be linted as production code —
  failing on the project's own backticked test-name convention, on `MagicNumber` in fixtures, and on
  `TooGenericExceptionCaught`;
- `basePath` is set so report paths and any future baseline stay portable across machines.

The `commonMain` type-resolution gap is accepted rather than worked around. No configuration closes
it, and the alternatives — moving code out of `commonMain`, or adding an alpha detekt — cost more
than the rules are worth.

## Consequences

`./gradlew ktlintCheck detekt` is the gate, and it is a real one: roughly two hundred syntactic
detekt rules run against `commonMain`, and full type-resolution analysis runs against
`androidMain` and `desktopMain`.

The cost is that the `!!` ban, `UnsafeCast`, and the other type-resolution rules in `potential-bugs`
are **not** enforced mechanically on shared code, which is most of the code. Those rules stay
enforced by code review and by the style rules in `CLAUDE.md`. This is a real gap and is written
down here rather than assumed away, because the failure mode of a partially-effective lint gate is
believing it is a complete one.

If detekt 2.x reaches stable against this Kotlin line and closes the metadata gap, revisiting it is
a version bump and a new ADR, not a redesign.

## Alternatives considered

- **Drop detekt; gate on ktlint plus Android Lint.** This was the plan's expected fallback, and it
  remains viable — 中「Linter導入」and 中「LintのCIチェック導入」are both satisfied by ktlint and Android
  Lint, and neither row ever depended on detekt. Rejected because detekt demonstrably works, and its
  syntactic rules on `commonMain` catch a class of defect ktlint does not look for: ktlint is a
  formatter, and `EmptyCatchBlock`, `SwallowedException` and `MagicNumber` are not formatting.
- **detekt `2.0.0-alpha`, which is built against Kotlin 2.4.10.** Rejected. It targets Gradle 9.6.1
  and AGP 9.3.1, both behind this project's pins, and shipping an alpha into the build to chase a
  newer number is precisely what the version-pinning discipline exists to prevent.
- **Set `basePath` and keep the stock config.** Rejected because it does not work. It was tested:
  `basePath` changes report rendering only, and `MagicNumber` stayed silent until the exclusion
  itself was removed.
- **Move shared code out of `commonMain` so type resolution reaches it.** Rejected as backwards —
  contorting the architecture to suit a linter's task graph trades a real design property for a
  partial rule set.
