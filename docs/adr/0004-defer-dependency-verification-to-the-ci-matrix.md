# ADR-0004: Defer dependency verification metadata to the CI matrix

- **Status:** Accepted, and **not carried out**. See *Amendment* below.
- **Date:** 2026-09-05

## Context

Pinned version strings alone do not make a build reproducible — transitive dependencies still float,
and "newest stable" resolves differently in January than in September. The plan's chosen mechanism
was Gradle **dependency verification**: `gradle/verification-metadata.xml` with SHA-256 checksums,
generated once and updated in a reviewed PR whenever a dependency changes. It was chosen over
blanket Gradle dependency locking specifically because locking's lock state is host-dependent —
iOS configurations only resolve on macOS — which turns a reproducibility mechanism into a
merge-conflict generator. Dependency verification was expected to avoid that by pinning *artifacts*
rather than the resolution graph, and to defend the supply chain as a bonus.

Generating the file on this machine showed the premise does not hold for this project.

The metadata produced on macOS contains host-specific artifacts:

```
kotlin-native-prebuilt-2.4.10-macos-aarch64.tar.gz
skiko-awt-runtime-macos-arm64-0.150.1.jar
desktop-jvm-macos-arm64-1.12.0.pom
```

A Linux CI runner resolves `kotlin-native-prebuilt-…-linux-x86_64` and `skiko-awt-runtime-linux-x64`
instead. Those are absent from a macOS-generated file, so verification fails on the runner. Kotlin
Multiplatform pulls a native toolchain and Compose pulls a Skiko native binary, and both are chosen
by host — the same host-dependence that disqualified locking applies here, just one layer down.

Generation is also not complete in one pass. `--write-verification-metadata sha256 help` resolves
only what `help` touches; the subsequent real build failed on `aapt2-9.4.0`, which no configuration
had resolved at generation time. Covering the build means generating against the full task graph, on
every host that runs it.

Shipping the file in PR1 would therefore mean committing a supply-chain gate that is red on two of
the three CI runners before CI exists to reveal it.

## Decision

Dependency verification is **deferred to PR2**, where the CI matrix is introduced.

The file is generated there against the full task graph on each of `ubuntu`, `macos` and `windows`,
and the three results merged, so the committed metadata covers what CI actually resolves rather than
what one developer's laptop happened to resolve.

Until then, reproducibility rests on the mechanisms PR1 does ship, which are not affected by any of
this:

- every version declared as a literal in `gradle/libs.versions.toml`, with no dynamic versions;
- the Gradle wrapper committed and pinned by `distributionSha256Sum`, fixing the build tool itself;
- `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so no module can introduce an untrusted repository.

## Consequences

PR1 ships a build that is green on every host, and the supply-chain gate arrives one PR later
covering all three of them — which is the only state in which it is worth having. The cost is a
one-PR window in which artifact checksums are unverified; pinned versions and a pinned wrapper still
apply throughout, so the exposure is to a compromised artifact republished under an existing
version, not to version drift.

The maintenance cost is now visible rather than discovered later: any dependency change requires
regenerating the metadata on all three hosts. That is a real burden for a project this size, and
PR2 should weigh it against scoping verification to the JVM-only configurations, which are the
host-independent part of the graph.

## Amendment — 2026-09-10

**The deferral was never completed, and this record read as though it had been.** Stating that
plainly here matters more than the decision itself: a reader of the *Decision* section above would
conclude that artifact checksums have been verified since PR2, and they have not.

What PR2 actually shipped was the machinery, not the gate:

- `.github/workflows/verification-metadata.yml` generates the metadata on each of the three
  runners and merges the results through `scripts/merge_verification_metadata.py`.
- The workflow is `workflow_dispatch` only, **has never been run**, and there is no step that
  ingests its output.
- There is no `gradle/verification-metadata.xml` in the repository, and `ci.yml` has no
  verification step.

So the "one-PR window in which artifact checksums are unverified" described under *Consequences*
is in fact the whole project. The exposure is unchanged from what that paragraph names — a
compromised artifact republished under an existing version — and the three mechanisms PR1 shipped
still hold: literal versions in `gradle/libs.versions.toml` with no dynamic versions, a committed
wrapper pinned by `distributionSha256Sum` and validated in CI by
`gradle/actions/wrapper-validation`, and `RepositoriesMode.FAIL_ON_PROJECT_REPOS`.

Why it stays unshipped rather than being finished now: the maintenance cost this record already
predicted — regenerating on three hosts for every dependency change — is not worth paying on a
two-screen submission whose dependency set is frozen. The honest state is a documented gap, which
is what the README's *再現性* section says, and the two documents now agree. Finishing it means
either running the merge workflow and committing its output, or scoping verification to the
JVM-only configurations, which is the host-independent part of the graph.

## Alternatives considered

- **Commit the macOS-generated file anyway.** Rejected: it fails on the Linux and Windows runners,
  and a gate that is red for reasons unrelated to the change under review trains everyone to ignore
  it.
- **Add `<trusted-artifacts>` entries for the host-specific coordinates.** Rejected as the default:
  it carves the exemption precisely around the native toolchain and Skiko binaries — the artifacts
  with the largest blast radius — which leaves the mechanism's name intact and its value hollow. It
  remains available in PR2 as a fallback if merging three hosts proves impractical.
- **Blanket Gradle dependency locking.** Rejected for the reason already recorded in the plan: the
  lock state is host-dependent and the lockfiles differ by whoever regenerated them last.
- **Drop verification entirely.** Rejected: pinned versions defend against drift but not against an
  artifact being republished under an existing version, and the supply chain is explicitly in scope
  for the security review.
