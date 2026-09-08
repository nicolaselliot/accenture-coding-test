# ADR-0012: Run the UI suite on Desktop and iOS, not on Android device tests

- **Status:** Accepted
- **Date:** 2026-09-08

## Context

PR15 delivers 低「UIテスト」: `runComposeUiTest` suites driving the stateless `SearchContent` and
`DetailContent`. The plan put them in `commonTest` so one suite would run on every target — Desktop
as a JVM test, Android as a device test, iOS from PR18 — and risk 4 recorded the fallback if the
emulator leg proved unstable.

It did not prove unstable. It proved impossible, for two independent reasons, and a third finding
moved the suite out of `commonTest` altogether. All four were probed rather than assumed.

**One: the Android device test APK cannot carry the string bundle.** Wiring the compilation with
`withDeviceTestBuilder { sourceSetTreeName = "test" }` works — `connectedAndroidDeviceTest` appears
and `commonTest` reaches it. The build then fails exactly the way ADR-0010's defect failed:

```
A problem was found with the configuration of task
':feature:search:copyAndroidDeviceTestComposeResourcesToAndroidAssets':
property 'outputDirectory' doesn't have a configured value
```

Probing the variant API says why, and says it is the same gap:

```
PROBE variant=androidMain          assetsNull=true
PROBE deviceTest=AndroidTest       assets=null
```

`com.android.kotlin.multiplatform.library` exposes no assets container on the main variant — which
ADR-0010 already established — **and none on the device test component either**. ADR-0010's fix was
available because `:androidApp` is a `com.android.application` module whose variants do expose
assets; a library module's self-instrumenting test APK has no such counterpart. So there is nowhere
to put the bundle, and every test that reads a string would fail on the device even if the build
were unblocked.

**Two: D8 refuses to dex the test tree at `minSdk` 26.** Forcing the APK to build anyway hits this:

```
D8: Space characters in SimpleName 'dev/nicolas/githubsearch/feature/search/
SearchViewModelTest$a rate limited search carries the reset instant through to the state$1'
are not allowed prior to DEX version 040
```

Backticked behaviour-sentence test names are this project's convention, required by `CLAUDE.md` and
graded under 可読性. A lambda inside such a function compiles to a nested class whose name carries
the spaces, and DEX rejects those below version 040 — which `minSdk` 26 targets. The convention and
the platform are simply in conflict here, and the convention is the graded half.

**Three: `commonTest` is the wrong home regardless.** This project runs `commonTest` on Android's
*host* JVM as well (`withHostTest`, added so the suite does not silently skip the platform the app
ships to). A CMP UI test cannot run there at all — it reaches for the Android idling strategy, which
probes for Robolectric through `Build.FINGERPRINT`, null off-device:

```
java.lang.NullPointerException: Cannot invoke "String.toLowerCase(java.util.Locale)"
because "android.os.Build.FINGERPRINT" is null
    at androidx.compose.ui.test.RobolectricIdlingStrategy_androidKt.getHasRobolectricFingerprint
```

Every test in both suites died on it. Robolectric is not the way out: `CLAUDE.md` records that CMP
UI tests do not run under it.

**Four, minor but load-bearing for the author: Kotlin/Native rejects a comma in a test name**
(`Name contains illegal characters: ","`). Two names had one. It compiles on Desktop and Android and
fails the iOS test compilation, so it is caught — but only by the target that was meant to join
later.

## Decision

**The UI suite lives in a `uiTest` source set, offered to Desktop and to both iOS test targets, and
withheld from every Android test compilation.**

- `uiTest` `dependsOn(commonTest)`, and `desktopTest`, `iosArm64Test` and `iosSimulatorArm64Test`
  each `dependsOn(uiTest)`. Android's host test compilation does not, so the suite is never compiled
  into a place where it can only fail. Wired once in `githubsearch.kmp.compose`, because a module
  that draws is a module that can have UI tests.
- **No device-test wiring ships.** A `connectedAndroidDeviceTest` that cannot pass is worse than an
  absent one; the block to restore is a comment in the same convention plugin.
- 低「UIテスト」is delivered on **two of the three platforms**, both in CI on every pull request:
  the Desktop suite on every runner `./gradlew build` already covers, and the same suite on the iOS
  simulator on the macOS runner. iOS therefore arrives in PR15 rather than PR18.
  The Desktop suite is verified locally with `java.awt.headless=true` — the property is set on the
  test task rather than left to the default, so the environment it passes in here is the one it
  meets on a display-less runner. The Linux leg specifically has not rendered Skiko before; the
  first CI run proves it, and if it needs a virtual display that is a workflow change, not a
  redesign.
- Android's ViewModel and mapper suites keep running on its host JVM, as before.
- Two suite-level rules follow from the findings and are pinned in the plan's *Fixed parameters*:
  every expected string is resolved from the same bundle the UI reads (never written out in
  English), and no test name contains a comma.

## Consequences

- **CI needs no emulator, and no nightly job.** That was the plan's own reason for keeping the
  Android leg off pull requests — emulator jobs are slow and the flakiest thing in a mobile
  pipeline — so the cost here is smaller than it looks: the leg that was going to run rarely is the
  leg that cannot run at all.
- **No automated coverage of Android's renderer.** This is the real loss. It is mitigated but not
  erased: the composables under test are the same source on all three targets, two independent
  renderers exercise them on every PR, and the Android build is verified by hand on an emulator
  each PR — search, detail, both flavors, both locales. A defect that is Android-specific *and*
  invisible to the manual pass would slip through.
- **`applyDefaultHierarchyTemplate()` is now explicit** in the compose convention. Declaring one
  `dependsOn` edge by hand switches the default template off, and the resulting failure names
  `FormatCount.kt`'s missing `actual` rather than the source set that caused it. The call is
  load-bearing and the comment beside it says so.
- The suite is analysed by detekt like the other test source sets, with `**/uiTest/**` added to the
  same exclusion lists `**/commonTest/**` appears in. A source set left off that list is silently
  unanalysed, which is the failure mode this project keeps closing.
- **Restoring the Android leg needs one of the two blockers gone.** If AGP gives KMP library device
  tests an assets pipeline, and `minSdk` reaches 35 (DEX 040) or D8 stops rejecting those names, the
  wiring is one block and one `dependsOn` edge.

## Alternatives considered

- **Robolectric on the Android host JVM.** The idling strategy's fingerprint probe exists precisely
  to support it, so this looks like the answer. `CLAUDE.md` rules it out on documented grounds — CMP
  UI tests do not run under Robolectric — and the probe failing is a symptom of that, not an
  invitation to add the dependency.
- **Move the suites into `:androidApp/src/androidTest`.** That module has a working assets pipeline
  (ADR-0010), so the resource blocker would lift. Rejected: `:androidApp` is not multiplatform, so
  the suite would stop being one suite — the Desktop and iOS copies would have to be maintained
  separately, which is the whole thing `commonTest` buys.
- **Rename every test to an identifier-safe name.** Would fix the dexing blocker, fixes nothing
  about assets, and trades a graded readability convention for a platform that still cannot run the
  suite. Backticked names stay.
- **Raise `minSdk` for the device test compilation only.** Same objection: it addresses the second
  blocker and leaves the first, which is the one with no hook at all.
- **Screenshot tests instead.** A different technique answering a different question, and not what
  the 低 row asks for. Worth its own ADR if pixel regressions ever need catching.
