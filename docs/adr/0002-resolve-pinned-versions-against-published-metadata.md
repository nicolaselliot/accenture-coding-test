# ADR-0002: Resolve two pinned versions against published metadata

- **Status:** Accepted
- **Date:** 2026-09-05

## Context

The implementation plan pins every dependency version, and treats changing one as a decision rather
than a preference. Two of those pins were provisional, and PR1 was the point at which they had to be
checked against what is actually published.

**`lifecycle-viewmodel-navigation3`** was pinned at **2.10.0** while the rest of AndroidX Lifecycle
was pinned at 2.11.0. The plan recorded this as a version skew to confirm — "confirm in PR1 that
2.10.0 and 2.11.0 coexist, and if not, drop to 2.10.0 across the board" — because the artifact was
believed to lag one minor behind the rest of the line.

**Coil** was pinned at **3.6.1**.

Checking the published Maven metadata for both:

- `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` publishes **2.11.0**. There is
  no skew: every Lifecycle artifact this project uses has a 2.11.0 release.
- `io.coil-kt.coil3:coil-compose` publishes **3.6.2**, one patch newer than the pin.

The assignment requires a README justification for any dependency that is not the newest stable
version, so leaving either pin as it stood would have added two entries to that list for no benefit.

## Decision

- Pin **all** AndroidX Lifecycle artifacts, `lifecycle-viewmodel-navigation3` included, at
  **2.11.0**.
- Pin **Coil** at **3.6.2**.

Both are recorded in `gradle/libs.versions.toml` as literal versions.

## Consequences

The skew the plan anticipated does not exist, so the risk it was tracking closes as resolved rather
than as a tolerated mismatch — one fewer thing to explain, and one fewer way for a later dependency
bump to go wrong. Neither library has any calling code yet, which makes this the cheapest possible
moment to take both changes: there is nothing to re-test.

Both are now newest stable, so neither appears in the README's list of justified deviations. That
list is now: Koin 4.2.2 (built against Kotlin 2.3.20, binary-compatible), JDK 21 (not 25, because
Android tooling lags), and Xcode 26.6 (27 is beta).

## Alternatives considered

- **Keep 2.10.0 and 3.6.1 as pinned.** Rejected: the plan's own rule is that a pinned version is
  binding *until* it is re-decided in the open, which is what this record does. Holding a version
  behind its newest stable release with no reason is exactly the thing the assignment asks to have
  justified, and there is no justification to offer here.
- **Take every newest version available.** Rejected: `navigation3` publishes 1.2.0-alpha02 and AGP
  publishes 9.5.0-alpha04. Chasing a newer number into an alpha is what the pinning discipline
  exists to prevent.
