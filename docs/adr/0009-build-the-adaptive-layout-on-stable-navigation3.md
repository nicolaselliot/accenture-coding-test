# ADR-0009: Build the adaptive layout on stable Navigation 3, not material3-adaptive

- **Status:** Accepted
- **Date:** 2026-09-08

## Context

The adaptive list-detail layout — 中「画面回転・様々な画面サイズ対応」 — needs two things: a window
size class, and a way to show a list and a detail side by side.

`IMPLEMENTATION_PLAN.md` recorded this as risk 2, with two paths: `material3-adaptive`'s
`adaptive-navigation3`, or manual `WindowSizeClass` branching on `window-core`. It called the second
"arguably the better default".

Checked against Maven Central rather than assumed:

| Artifact | Newest published | Stable line |
|---|---|---|
| `org.jetbrains.compose.material3.adaptive:adaptive-navigation3` | `1.3.0-beta02` | **none** — the entire published history is `1.3.0-alpha04` … `beta02` |
| `org.jetbrains.androidx.window:window-core` | `1.5.1` | yes |
| `org.jetbrains.androidx.navigation3:navigation3-ui` | `1.1.1` (pinned) | yes |

So the first path does not mean "a beta until the stable arrives" — there has never been a stable
line, and the assignment requires justifying in the README anything that is not the newest stable.

The decisive discovery is separate: **`SceneStrategy` is part of stable Navigation 3.** The
`androidx.navigation3.scene` package in 1.1.1 ships `Scene`, `SceneStrategy`, `SceneStrategyScope`
and `SinglePaneSceneStrategy`. `adaptive-navigation3` does not provide the *capability* — it
provides a pre-built strategy on top of an extension point we already have.

## Decision

Use `WindowSizeClass` from `window-core` 1.5.1 for the breakpoint, and a project-owned
`TwoPaneSceneStrategy` on stable `navigation3-ui` 1.1.1 for the layout. No `material3-adaptive`
dependency, and therefore no beta in the graph and nothing to justify in the README.

Two details worth recording because they are not obvious:

- **The threshold is Material's expanded bound, 840dp, on width alone.** A test pins one dp either
  side. Orientation is deliberately not the signal: a small phone in landscape is around 640dp and
  stays single-pane, while a large phone in landscape reaches roughly 892dp and does get two panes.
  That surprised the first version of the test, which asserted the opposite and was wrong.
- **The two panes are a `Scene`, not a `Row` in place of `NavDisplay`.** This is the load-bearing
  part, and the reason is state, not tidiness — see *Alternatives*.

`NavEntry.key` is private, so a `SceneStrategy` can see the entries but not which route each came
from. The pane decision therefore lives with the holder of the back stack, where it is a pure
function over keys and unit-testable; the strategy maps the result onto positions, which is safe
because entries arrive in back-stack order and the list is always the start destination.

## Consequences

- No beta or alpha artifact in the dependency graph, and `adaptive-navigation3` can be dropped from
  the plan's version table rather than carried with a caveat.
- Roughly ninety lines of `Scene` and strategy that this project owns and must maintain, against a
  dependency that would have owned them. Accepted: it is a `Row`, a divider and a placeholder.
- The pane split is equal halves. That is a new value and belongs in *Fixed parameters*.
- Both entry decorators apply in both layouts, which is what makes crossing the breakpoint safe.
- Risk 2 is closed. Risk 1 — the nav3 1.1.1 × CMP 1.12.0 pairing — was closed in PR10.

## Alternatives considered

- **`adaptive-navigation3` at `1.3.0-beta02`.** Rejected. It buys a pre-built strategy for an
  extension point that is already stable, in exchange for a permanently pre-release artifact in a
  submission that has to justify every non-stable choice. `CLAUDE.md` already refuses an alpha to
  chase a coordinate; the same reasoning applies here.
- **A `Row` rendering `SearchScreen` and `DetailScreen` instead of `NavDisplay` when wide.**
  Rejected, and this is the alternative that looks simplest and is actually broken. Replacing the
  display removes both entry decorators, so a screen resolves its ViewModel from the navigation
  entry in one layout and from the host in the other. Crossing the breakpoint — which is exactly
  what rotating a tablet does — would then hand the screen a *different* ViewModel instance with a
  fresh `SavedStateHandle`, and the query the user had typed would disappear on the one gesture this
  row is graded on. Inside a `Scene` the entries and their decorators are the same objects in both
  layouts.
- **Gating on height as well as width**, so a large phone in landscape stays single-pane. Rejected:
  it invents a threshold the plan does not pin, and Material treats expanded width as the list-detail
  signal regardless of height.

## Note on numbering

This is 0009 because it is the next unwritten number, not because it was reserved. The plan's two
references to "ADR-0008" for the `SearchTimedOut` decision are stale — 0008 is PR7's colour-roles
record — and that ADR, still unwritten, becomes **0010** when someone writes it. Reserving a number
for a document that does not exist is what produced the original collision.
