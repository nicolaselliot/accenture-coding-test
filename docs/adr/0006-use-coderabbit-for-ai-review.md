# ADR-0006: Use CodeRabbit for AI review on pull requests

- **Status:** Accepted
- **Date:** 2026-09-05

## Context

中「AIレビュー」is one of the graded criteria, and the assignment explicitly permits AI use and
offers credit for showing how it was applied. Something has to review every pull request
automatically, and it should review *this* project's rules rather than generic Kotlin advice — the
defects that matter here are a layering violation, a swallowed `CancellationException`, and a detail
screen bound to `watchers_count` instead of `subscribers_count`.

Four options were considered against three constraints: it must be free or near-free, it must not
require managing a secret this project would rather not hold, and its configuration should be
*visible in the repository*, because the repository is the artifact being evaluated.

| Option | Cost | Configuration |
|---|---|---|
| Claude Code Action | Anthropic API tokens per PR, plus an `ANTHROPIC_API_KEY` secret | Committed workflow |
| GitHub Copilot code review | Needs Copilot Pro; the free tier reviews only a selection in the IDE, not pull requests | Repository ruleset — a settings toggle, invisible in the repository |
| Gemini Code Assist for GitHub | Free during preview, but requires an attached billing account | Committed style guide |
| **CodeRabbit** | **Free forever for public repositories**, full feature set | **Committed `.coderabbit.yaml`** |

Copilot was the initial preference on the assumption its free tier could review pull requests. It
cannot: Copilot Free provides selection review in the IDE only. Copilot code review additionally
began consuming Actions minutes on 2026-06-01, though public repositories are exempt from that
particular charge.

## Decision

**CodeRabbit**, configured by a committed `.coderabbit.yaml`.

The configuration is not left at defaults. `reviews.path_instructions` carries seven rule sets
mirroring `CLAUDE.md` — the `:domain` import restriction, the `subscribers_count` correction, the
`HttpRequestRetry`-before-`HttpTimeout` ordering, the Compose stability rules, the
fakes-over-mocks testing stance, and the version-catalogue rule — so the review is project-aware
rather than generic. `profile` is `assertive` because a missed defect costs more here than an extra
comment, and `request_changes_workflow` stays `false` because on a solo submission a bot that
requests changes blocks the only person who can merge.

The `ANTHROPIC_API_KEY` secret is consequently **not** required, and
`.github/workflows/ai-review.yml` is removed.

## Consequences

Review runs on every pull request at no cost and with no secret to hold, rotate, or leak — one
fewer credential in a project whose security posture is a graded criterion. The rules live in
version control next to the code they govern, so a reviewer can see what the bot was told, and the
rules can be corrected in a pull request like anything else.

The real cost is trust, and it is larger than a first reading suggests. CodeRabbit is a third-party
GitHub App, and its documented permissions are:

| Access | Scopes |
|---|---|
| Read-only | actions, checks, discussions, members, metadata |
| **Read and write** | **code, commit statuses, issues, pull requests** |

That includes **write access to code** — not merely to pull request comments. It is a materially
larger grant than any pinned action in ADR-0005, where an action's reach is bounded by the
`permissions` block of the job that runs it.

It is accepted for three specific reasons. The repository is public, so the source it reads is
already disclosed. The repository holds no secrets: `local.properties` and all signing material are
gitignored, and a `PreToolUse` hook blocks them being written at all. And what actually prevents an
unwanted change reaching `main` is **branch protection**, not the app's own restraint — `main`
requires a pull request, blocks force-pushes and deletions, and from PR2 onward requires the CI
status checks to pass. Write access to code means the app *could* push a branch; branch protection
is why that cannot become a merge without a human.

Stating this precisely matters more than making the decision look comfortable. An earlier draft of
this ADR claimed the app "cannot modify code or merge anything", which was wrong on the first half —
exactly the kind of reassuring inaccuracy a security rationale must not contain.

A second cost is vendor dependence: the free tier is a commercial decision that could change. If it
does, the fallback is Gemini Code Assist or the Claude Code Action, and the `path_instructions`
written here port to either with editing rather than rethinking, since they are prose rules.

Rate limits on the open-source plan are **1–10 pull request reviews per developer per hour**, as a
rolling allowance rather than a fixed quota, and **100–300 files per review** — a per-review ceiling,
not an hourly one. Both ranges vary with the repository's popularity, so they are not a fixed
guarantee. At this project's volume — nineteen planned pull requests, none of them large — neither
is a constraint.

## Alternatives considered

- **GitHub Copilot code review.** Rejected on both constraints that mattered: the free tier cannot
  review pull requests at all, and enabling automatic review is a repository ruleset — a setting
  with no trace in the repository, which is a poor fit when the repository is what gets evaluated.
  Its `.github/copilot-instructions.md` would have been committed, but the fact that reviews happen
  at all would not be.
- **Claude Code Action.** Written, verified, and then removed. It works and its prompt was already
  tuned to `CLAUDE.md`, but it needs a paid API key per review, and a secret that exists only to
  power a review bot is a credential the project would otherwise not have. Kept in mind as the
  fallback if CodeRabbit's free tier changes.
- **Gemini Code Assist for GitHub.** Free during preview, but requiring an attached billing account
  for a submission repository is friction with no upside over CodeRabbit.
- **No AI review; rely on the local `/code-review` skill.** Rejected: it satisfies nothing
  automatically, leaves no evidence in the pull request trail, and 中「AIレビュー」is graded on what
  the repository demonstrates rather than on what was run locally. The local skill stays in use
  before opening a pull request — the two are complementary, and the local one already caught a
  high-severity token-handling defect in PR1.
