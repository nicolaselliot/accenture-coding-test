# ADR-0005: Pin GitHub Actions by commit SHA

- **Status:** Accepted
- **Date:** 2026-09-05

## Context

CI introduces eight third-party GitHub Actions across seven repositories. Each one runs arbitrary
code inside a job that holds this repository's credentials, and one of them — the label sync —
additionally holds `issues: write`. That makes the actions a dependency in exactly the sense the
project's security rules already cover: *"Pin dependency versions. Review anything new for licence and
maintenance status before adding it."*

The usual way to reference an action is a major tag, `actions/checkout@v7`. A tag is a **mutable
pointer**. Whoever controls the repository can move `v7` to a different commit at any time, and
every workflow referencing it silently starts running the new code on the next run. That is the
same property this project already rejected for Gradle dependencies:

> **No dynamic versions** (`+`, `latest.release`) — they make builds non-reproducible and are a
> supply-chain risk.

A major tag is a dynamic version by that definition. Applying the rule to Gradle and not to CI would
leave the softer target unguarded — and CI is the softer target, because it holds credentials the
build does not.

Two are outside the `actions/` and `gradle/` namespaces and carry correspondingly less institutional
backing: `android-actions/setup-android` and `crazy-max/ghaction-github-labeler`.

## Decision

Every action is referenced by its **full 40-character commit SHA**, with the human-readable version
in a trailing comment:

```yaml
- uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
```

Licence and maintenance were reviewed before adding, as required:

| Action | Version | Licence | Last release activity |
|---|---|---|---|
| `actions/checkout` | v7.0.1 | MIT | actively maintained |
| `actions/setup-java` | v6.0.0 | MIT | actively maintained |
| `actions/upload-artifact` | v7.0.1 | MIT | actively maintained |
| `actions/download-artifact` | v8.0.1 | MIT | actively maintained |
| `gradle/actions` | v6.3.0 | Apache-2.0 | actively maintained |
| `android-actions/setup-android` | v4.0.1 | MIT | actively maintained |
| `crazy-max/ghaction-github-labeler` | v6.0.0 | MIT | actively maintained |

None is archived. All are permissively licensed and compatible with this project.

`.github/dependabot.yml` enables Dependabot for the `github-actions` ecosystem only, and explicitly
not for Gradle.

## Consequences

A hijacked or retagged action cannot reach this repository: the SHA either matches the reviewed
commit or the workflow fails. The cost is that a pinned action never picks up a security fix on its
own, which is precisely the failure mode SHA pinning is accused of — so Dependabot is enabled to
update the SHAs and rewrite the version comments weekly. Pinning without that is a trap rather than
a control.

Gradle is deliberately excluded from Dependabot. Every version in `gradle/libs.versions.toml` is a
recorded decision and the toolchain versions are a determinism contract; a bot opening a pull
request per bump would argue with that policy weekly and bury the signal that a version was chosen
deliberately. Dependency changes there stay deliberate, reviewed, and accompanied by an ADR — as
ADR-0002 already is.

The verbosity is real: a SHA tells a reader nothing on its own, which is why the version comment is
mandatory rather than decorative.

## Alternatives considered

- **Major tags (`@v7`).** Rejected. Mutable, and therefore the same class of risk the project
  already refuses for Gradle coordinates. The convenience is real but it is convenience bought with
  the one property that makes a pin worth having.
- **Exact release tags (`@v7.0.1`).** Rejected. Better than a major tag, but a release tag can also
  be force-moved; only a SHA is immutable.
- **Pin third-party actions by SHA and first-party ones by tag.** Tempting, and it would read more
  cleanly. Rejected because it encodes trust in a namespace rather than in a verified artifact, and
  because `actions/*` being compromised is not a hypothetical the industry has been spared.
- **No CI actions at all — hand-rolled shell.** Rejected as strictly worse: the same code would run
  with the same credentials, unreviewed, unversioned, and maintained by nobody.
