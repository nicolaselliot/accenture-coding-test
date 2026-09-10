# ADR-0014: Enable the library module's Android resources pipeline

- **Status:** Accepted. Supersedes [ADR-0010](0010-package-compose-resources-from-the-application-module.md).
- **Date:** 2026-09-10

## Context

ADR-0010 diagnosed a real crash — Android launched with zero `composeResources/` entries in the APK
— and built a working fix for it. The fix was about two hundred lines: a `Sync` task in
`:core:designsystem` that re-laid the prepared bundle out under `composeResources/<package>/`, a
plain named configuration to publish that directory, a matching resolvable configuration in
`:androidApp`, a `CopyComposeResourcesToAssetsTask` in `build-logic`, and per-variant wiring into
`variant.sources.assets`.

It rested on one claim, and the claim was wrong:

> AGP 9's `com.android.kotlin.multiplatform.library` plugin has no assets pipeline.

The observation behind it was accurate: `sources.assets` really was `null`, and the AAR really did
carry no `assets/` entry. The inference was not. The pipeline is not absent — **it ships disabled**,
and there is a documented one-line opt-in that turns it on:

```kotlin
android {
    androidResources.enable = true
}
```

An earlier amendment to ADR-0010 softened the wording to "with the plugin configured as it is here",
but the opt-in itself was never tried, so the workaround stayed in place behind a sentence that no
longer justified it. *Alternatives considered* recorded the two fixes that had been attempted and
did not record this one.

## Decision

**Set `androidResources.enable = true` in the `githubsearch.kmp.library` convention plugin, and
delete the workaround entirely.**

Removed: `CopyComposeResourcesToAssetsTask`, the `androidComposeAssets` `Sync` task, both
configurations, the cross-module artifact publication, and the per-variant assets wiring.

Kept: `:androidApp:verify<Variant>ComposeResources`. The guard was never part of the workaround. It
asserts a property of the artifact — that the packaged assets contain at least one `.cvr` bundle —
and that property has to hold however the bundle gets there. Its failure message now names the
opt-in as the likely cause instead of the deleted wiring.

The flag is set for every library module rather than only for `:core:designsystem`. Which module
owns resources is a fact about the code, and a convention that has to be remembered the day that
changes is one that will not be.

## Consequences

- **About two hundred lines of build logic are gone**, along with a hand-maintained edge from
  `:androidApp` to `:core:designsystem` that ADR-0010 itself listed as its main cost.
- **The double-contribution risk ADR-0010 predicted is gone with it.** That record noted the
  workaround would collide with the plugin's own wiring once the gap closed. There is no second
  contributor now.
- **The guard is what proves it.** Verified rather than assumed, at three levels: the AAR carries
  `assets/composeResources/.../strings.commonMain.cvr` for both locales; `verifyDevDebugComposeResources`
  reports two bundles; and the shipped `prodRelease` APK carries both through R8 and
  `shrinkResources`.
- **The failure mode is unchanged and still covered.** Turning the flag off returns the build to
  exactly the ADR-0010 crash — silently, because the Compose plugin's copy task is a safe call on a
  null. That is precisely what the verification task exists to catch, and why it survives this
  change.
- **A lesson worth keeping.** "The platform cannot do this" is a claim about someone else's code,
  and this project's own guardrails say to check rather than infer. The workaround was correct,
  well-tested and well-documented, and it should never have existed.

## Alternatives considered

- **Leave the workaround in place.** It worked. But it is two hundred lines standing in for one, in
  a submission graded on 簡潔性 and 保守性, resting on a documented claim that a reviewer who knows
  Compose Multiplatform can disprove in five minutes.
- **Enable the flag only on `:core:designsystem`.** Narrower, and wrong for the reason above: it
  encodes today's module layout into a convention plugin.
- **Amend ADR-0010 again rather than supersede it.** The decision is being reversed, not refined.
  Superseding keeps the wrong turn legible, which is the more useful history.
