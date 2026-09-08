# ADR-0010: Package Compose resources from the application module

- **Status:** Accepted
- **Date:** 2026-09-08

## Context

`main` at `7fc5cd8` crashed on launch on Android:

```
org.jetbrains.compose.resources.MissingResourceException: Missing resource with path:
composeResources/dev.nicolas.githubsearch.core.designsystem.generated.resources/values/strings.commonMain.cvr
```

Every gate was green. `ktlintCheck`, `detekt`, `lint`, every test suite, `assembleDebug` — all
passed, on a build whose APK could not draw its first screen. Desktop and iOS were unaffected, which
is why it survived PR12: nothing before that PR read a string on Android.

The APK contained **zero** `composeResources/` entries, and `:core:designsystem`'s AAR contained no
`assets/` at all.

Compose Resources are read from **assets** on Android — `DefaultAndroidResourceReader` goes to
`context.assets` and nowhere else — so the bundle has to be packaged as an asset or it does not
exist at runtime. The Compose plugin has a task for exactly that,
`copyAndroidMainComposeResourcesToAndroidAssets`, and it *is* registered in every Compose module.
Running it directly is what named the cause:

```
A problem was found with the configuration of task
':core:designsystem:copyAndroidMainComposeResourcesToAndroidAssets':
property 'outputDirectory' doesn't have a configured value
```

The plugin sets that output directory through AGP's variant API,
`variant.sources.assets.addGeneratedSourceDirectory(...)`. Probing the variant explains why it never
happens:

```
PROBE :core:designsystem androidMain assets=null
PROBE :feature:search    androidMain assets=null
PROBE :feature:detail    androidMain assets=null
PROBE :shared            androidMain assets=null
```

**AGP 9's `com.android.kotlin.multiplatform.library` plugin has no assets pipeline.** Its variants
report `sources.assets == null`, and its AAR carries no `assets/` entry. The Compose plugin's call
is a safe-call, so it is skipped in silence: the task stays registered with no output directory,
contributes nothing, and Gradle reports it as skipped rather than as broken.

That is the whole failure. Nothing is misconfigured in this repository; the CMP 1.12.0 × AGP 9.4.0
pairing simply has no route from a shared module's resources to an Android APK.

## Decision

**The application module packages the bundle, because it is the only module whose variants can.**

- `:core:designsystem` — which by `CLAUDE.md` owns every user-facing string, and is therefore the
  only module with resources — lays its prepared bundle out the way the runtime looks for it
  (`composeResources/<resource package>/…`) and publishes that directory on a plain named
  configuration, `androidComposeResources`.
- `:androidApp` consumes it by configuration name and copies it into a generated assets directory
  registered with `variant.sources.assets.addGeneratedSourceDirectory(...)`, which its
  `com.android.application` variants *do* expose.

The re-layout sits with the producer because the package segment comes from that module's
`packageOfResClass`, and the application has no business knowing it.

**A green build must never again mean a crashing app**, so `:androidApp:verifyDebugComposeResources`
asserts that the merged assets contain at least one `.cvr` bundle, and `check` depends on it. It was
written before the fix and observed to fail for the right reason.

## Consequences

- Android renders its strings. Verified on an emulator: the app launches, the search screen draws
  from the bundle, and the error state renders its localised message with a working retry.
- Both variants are covered — the release APK carries the bundle through R8 and `shrinkResources`.
- The wiring is explicit and greppable, and the guard fails loudly if it is ever severed.
- **The cost is a hand-maintained edge.** `:androidApp` now knows that `:core:designsystem`
  publishes assets. A second module that gained resources would have to be added, which the
  single-bundle rule in `CLAUDE.md` already forbids — but the coupling is real and is the reason the
  guard exists.
- Consuming by configuration name rather than by attribute means this artifact sits outside Android
  variant matching. That is deliberate: the module publishes several Kotlin and Android variants,
  and a plain directory has no business disambiguating between them.
- **This is a workaround for an upstream gap and should be deleted when the gap closes.** If AGP
  gives KMP library variants an assets pipeline, the Compose plugin's own wiring starts working and
  the bundle would be contributed twice. That fails loudly as a duplicate-asset merge error rather
  than silently, which is the right failure mode for a workaround left in too long.

## Alternatives considered

- **Wire the Compose plugin's own task from the convention plugin.** The first fix attempted, and it
  does not work twice over: `CopyResourcesToAndroidAssetsTask` is Kotlin-`internal`, so `build-logic`
  cannot name the type; and even with reflection the wiring target — `variant.sources.assets` — is
  the null that caused the bug. Recorded because it is the obvious fix and it is wrong.
- **Move the bundle to `:androidApp`.** Would fix Android by breaking Desktop and iOS, which read
  the same bundle from the shared module.
- **Switch shared modules back to `androidTarget()`.** The old plugin has the assets pipeline, but
  `CLAUDE.md` and AGP 9 both require `com.android.kotlin.multiplatform.library` for shared modules.
  Reverting the module system to work around one missing pipeline is far larger than the problem.
- **Publish the desktop target's processed resources.** `processedResources/desktop/main` already
  has the exact final layout and would have needed no re-layout task. Rejected: it makes the Android
  APK's contents depend on the desktop target, so removing or renaming that target would break
  Android packaging for no visible reason.
