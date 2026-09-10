# ADR-0008: Seed the Material 3 `*Fixed` colour roles

- **Status:** Accepted
- **Date:** 2026-09-07

## Context

`:core:designsystem` (PR7) builds `LightColorScheme` and `DarkColorScheme` by passing named roles to
Material's `lightColorScheme()` / `darkColorScheme()` factories. The factories accept 48 roles; the
first cut of the palette set 35 of them.

Two of the unset roles behave differently from each other, and the difference was not obvious:

- **`surfaceTint` is derived.** Its default is the `primary` *parameter*, so it picked up the seeded
  blue automatically. Verified: `LightColorScheme.surfaceTint` is `#0058C9`, equal to
  `LightColorScheme.primary`.
- **The twelve `*Fixed` roles are not derived.** `primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`,
  `onPrimaryFixedVariant` and the secondary/tertiary equivalents default to `ColorLightTokens.*Fixed`
  — Material's *baseline* palette, independent of the seed. Verified:
  `LightColorScheme.primaryFixed` was `#EADDFF`, byte-identical to `lightColorScheme().primaryFixed`,
  in **both** schemes.

So the palette shipped twelve baseline-lavender roles inside an otherwise blue app. Nothing rendered
them yet — no Compose Material 3 1.9.0 component reads a `*Fixed` role, and no feature code existed —
but `CLAUDE.md` forbids hardcoded colours in feature code, which pushes every feature to read roles
off `MaterialTheme.colorScheme`. The first screen to want a hero surface that keeps its colour
across light and dark would reach for `primaryFixed` and get lavender.

The existing test named *the schemes are seeded rather than left on the Material baseline* compared
only `primary`, so it asserted a property it did not actually cover.

## Decision

Set all twelve `*Fixed` roles explicitly, in both schemes.

**No new colour is introduced.** Material 3 assigns each `*Fixed` slot a tone of the accent palette
it belongs to, and every one of those tones is already present in the schemes:

| Role | Tone | Already in the palette as |
|---|---|---|
| `*Fixed` | T90 | the light `*Container` |
| `*FixedDim` | T80 | the dark accent (and light `inversePrimary`) |
| `on*Fixed` | T10 | the light `on*Container` |
| `on*FixedVariant` | T30 | the dark `*Container` |

The twelve values are therefore declared once as `private val primaryTone90` … `tertiaryTone10` and
shared by both schemes. Sharing is structural rather than asserted, which is the point: a `*Fixed`
role holds the same value in light and dark — that is what "fixed" means — and a single declaration
cannot drift the way two literals can. The existing roles that sit on the same tones now reference
the same vals, so the tone reuse is visible at every call site instead of being a coincidence
between twelve pairs of hex literals.

Because the tones already existed, this adds **no new colour parameter** — only a mapping from
roles that were previously unset onto tones `Color.kt` already declared. The seed, the chroma
values and the tone assignments are all unchanged, so this ADR records a *completeness* fix rather
than a new pinned value.

## Consequences

- `MaterialTheme.colorScheme.primaryFixed` is now `#DFE0FF`, a seed blue. Feature code may read any
  `*Fixed` role without drawing lavender.
- All twelve `*Fixed` pairs join the WCAG AA sweep in `ColorSchemeTest`: `on*Fixed` and
  `on*FixedVariant` against both `*Fixed` and `*FixedDim`. Worst pair is
  `onSecondaryFixedVariant/secondaryFixedDim` at **5.47:1**, clear of the 4.5:1 floor.
- Two guards were added, because neither property is enforced by the factories:
  - *the fixed roles are seeded rather than left on the Material baseline* — verified to fail:
    stripping all 24 assignments makes exactly this test red.
  - *the fixed roles hold the same value in both schemes* — catches an edit that reseeds one scheme
    and forgets the other.
- `Color.kt` moved from two per-declaration `@Suppress("MagicNumber")` annotations to one
  `@file:Suppress`. The shared tone vals are top-level properties and `detekt.yml` sets
  `ignorePropertyDeclaration: false`, so they would each have needed their own annotation. The
  existing justification — *"a colour ramp is named numbers; that is the whole file"* — was already
  a file-level argument.
- `surfaceTint` stays unset. It is derived correctly, and restating a correct derivation is noise.
  It is pinned by a test instead, so a Material release that stopped deriving it would be caught.

## Alternatives considered

- **Leave them at baseline and document them as off-limits.** This was the state before this ADR: a
  comment in `Color.kt` telling readers not to touch twelve of the scheme's roles. Rejected — it
  keeps a live landmine in a token set whose entire purpose is that feature code can read any role
  without thinking, and it relies on every future reader finding the comment.
- **Leave them unset and drop them from the scheme.** Not possible; the factories always populate
  them, so "unset" means "baseline", not "absent".
- **Derive them at runtime from the seed** with Material Color Utilities. Rejected for the same
  reason the rest of the palette is static: it adds a dependency and runtime tone maths to produce
  values that are fixed at build time anyway. See the palette rationale in `Color.kt`.
- **Invent fresh tones for the `*Fixed` slots.** Rejected — it would have meant pinning twelve new
  values (an ADR *and* a token-table change) to duplicate tones the seed had already produced.
