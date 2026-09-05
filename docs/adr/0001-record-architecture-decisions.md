# ADR-0001: Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-09-05

## Context

This project pins an unusually large number of parameters — versions, timeouts, page sizes, the
search trigger, the module graph. Most of them look arbitrary from the outside, and several of them
were chosen against a plausible-looking alternative for a reason that is not visible in the code.

A reviewer reading `libs.versions.toml` cannot tell the difference between a version that is newest
stable and a version that is deliberately one behind. A future contributor cannot tell whether a
timeout is measured or guessed. Without a record, both end up re-litigated, and the cheapest way to
"fix" an undocumented constraint is to delete it.

The assignment also asks explicitly for anything that is not the newest stable version to be
justified, which means these decisions have to be written down somewhere durable regardless.

## Decision

Architecture decisions are recorded here as numbered Markdown files, in the format of
`docs/adr/template.md`, following Michael Nygard's ADR pattern.

An ADR is written when:

- a pinned version or parameter changes,
- a spike takes its documented fallback rather than its primary path,
- a layering or module-graph rule is introduced or relaxed,
- a dependency is added, with its licence and maintenance status.

ADRs are immutable once accepted. A decision that is reversed gets a new ADR that supersedes the old
one; the old file stays, marked superseded.

## Consequences

Decisions become reviewable in the same pull request as the code they justify, and the README can
link to them rather than restating them. The cost is a small amount of writing per decision, and the
discipline to write it at the moment of deciding rather than afterwards — an ADR reconstructed weeks
later records the rationalisation, not the reason.

## Alternatives considered

- **A single DECISIONS.md.** Rejected: one growing file produces merge conflicts on every parallel
  branch, and gives no stable anchor to link a specific decision from a PR or the README.
- **Commit messages alone.** Rejected: a commit message is discoverable only if you already know
  which commit to read, and squash-free history makes that search worse, not better.
- **No record.** Rejected: the assignment requires justifying non-newest versions, so at least part
  of this has to exist anyway.
