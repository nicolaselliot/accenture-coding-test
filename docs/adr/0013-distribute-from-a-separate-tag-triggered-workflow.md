# ADR-0013: Distribute from a separate tag-triggered workflow

- **Status:** Accepted
- **Date:** 2026-09-08

## Context

PR16 delivers 低「仮のデプロイ環境」, part one: a signed Android APK to Firebase App Distribution, and
the Desktop `.dmg` and `.msi` published as release assets. TestFlight follows in PR18.

This is the first work in the repository that needs secrets. Everything until now has been buildable
by anyone who clones it — the optional PAT is a convenience, and ADR-0011 made sure a release build
refuses to embed one. Distribution cannot be that: it needs an upload key and a service account, and
a mistake with either is not a failed build but a published one.

Three constraints shape it.

**`ci.yml` must stay secret-free.** It runs on `pull_request`, so a fork's branch can run its code.
It holds no secrets today and must keep holding none.

**A green job must not mean a signed artifact.** The signing config added in this PR is
absent-safe on purpose: unconfigured means the release variant is simply unsigned, so
`./gradlew build` never depends on a secret. The cost of that choice is that a mistyped secret name
produces an unsigned APK and a passing job — Firebase would reject it, but only after the job went
green.

**Almost none of this can be verified from the development machine.** There is no keystore (the
agent is blocked from creating signing material, correctly), no Firebase project, and the Compose
plugin refuses to package a Desktop distribution with a Homebrew JDK. The first genuinely green run
happens in CI.

## Decision

**A separate `release.yml`, triggered by a `v*.*.*` tag or by hand, and never by a pull request.**

- **Triggers are `push: tags: ['v*.*.*']` and `workflow_dispatch`.** No `pull_request`, which is
  what keeps the secrets unreachable from a fork. A dispatch builds, signs, distributes to testers
  and keeps the Desktop packages as workflow artifacts; a tag does all of that and attaches every
  artifact to a GitHub Release.
- **The Firebase CLI, not the Firebase Gradle plugin.** `firebase-tools` is Google's own, pinned to
  an exact version like every other dependency here. Keeping it in the workflow means the deploy
  credential never joins the build classpath and `./gradlew build` is the same command it was
  yesterday — which matters more than usual, because the alternative would add a plugin to a build
  whose distribution path cannot be tested locally.
- **`gh release`, not a third-party release action.** ADR-0005 requires every action to be pinned by
  commit SHA, and the GitHub CLI is already on the runner: no new supply-chain edge for a two-line
  upload.
- **The upload key is read from the environment and nowhere else**, decoded from a secret into the
  runner's temporary directory — outside the checkout, where no `upload-artifact` glob can reach it
  and no later step walking the tree can read it.
- **Three gates, because the failure they catch is a published artifact.** A preflight step names
  every missing secret before anything is built, so a first run costs seconds rather than a full
  release build; `./gradlew check` runs beside the assemble, because a tag can sit on a commit that
  never reached `main` and a dispatch can run from any branch — without it the artifact handed to
  testers is the one thing in the project no suite has run against; and after the build the APK is
  rejected if its filename says `unsigned` or if `apksigner verify` fails. `apksigner` is located
  from whichever build-tools AGP installed rather than pinned to a version this repository cannot
  confirm.

  What that last gate proves is **validity, not identity**: an APK signed with the wrong keystore
  passes it. Closing that needs `apksigner verify --print-certs` compared against a known SHA-256
  fingerprint, which is worth adding the day this app targets Play — until then the store that
  would reject it is the only consumer.
- **The release is created as a draft and published last.** Creating it first is what stops three
  jobs racing to create the same release; publishing it first would mean a build that fails halfway
  leaves a notified release with missing downloads. Both artifact jobs must succeed before the
  draft is published, because a release advertising three downloads and carrying two is worse than
  one that arrives a day later.
- **The version comes from the tag.** `v1.2.0` becomes `1.2.0` for both the Android `versionName`
  and the Desktop `packageVersion`, overriding the pinned defaults in the build files. Without it
  every release ships artifacts claiming `1.0.0` — installers byte-indistinguishable from the last
  one, and neither Windows nor macOS treats them as an upgrade. `versionCode` stays the run number;
  jpackage takes three numbers and nothing else, so the leading `v` is stripped.
- **`versionCode` comes from `github.run_number`**, as the plan pins. Firebase and TestFlight both
  reject a non-monotonic build number, and a hand-maintained counter collides the first time two
  branches ship.
- **The Desktop packages are the unminified distribution** (`packageDistributionForCurrentOS`, not
  the release variant). Compose Desktop's release packaging runs ProGuard, which routinely needs
  keep rules for a Compose app — an unverifiable risk on a machine that cannot package at all, in
  exchange for a smaller download nobody asked for.
- **The `.dmg` is neither signed nor notarised.** macOS will warn on first open. Apple signing
  arrives with PR18's certificates; until then the release notes say how to open it.

## Consequences

- **Distribution is a deliberate act.** Nothing is published by merging; a tag or a button press is.
  For a provisional channel that is the right default — the alternative, distributing every push to
  `main`, spends tester notifications on work in progress.
- **The pipeline holds secrets in exactly one workflow**, which runs only from a tag or a dispatch on
  this repository. `contents: write` is scoped to the jobs that upload, and the workflow is
  unreachable from a pull request.
- **The first run will fail, and that is the design.** With no secrets configured the preflight step
  names all six missing values and stops. That is the intended first experience: a list of what to
  create, not a base64 error deep in a build log.
- **Two things stay unverified until CI runs them**, both called out in the PR: the Desktop package
  paths (this machine cannot package) and the WiX install on the Windows runner. The `find` guards
  turn either being wrong into a named failure rather than a release with a missing asset.
- **`build-tools` is not pinned for the verification step.** AGP installs what it needs and the
  newest is used. If that ever picks a build-tools whose `apksigner` behaves differently, the gate
  changes behaviour without a version bump — accepted, because pinning it here would be a second
  place for the SDK layout to drift and the alternative failure is worse.
- The upload key never touches the working tree, so `.gitignore`'s keystore patterns and the
  `PreToolUse` hook remain belt and braces rather than the only defence. It is written with
  `umask 077` — never readable even for the instant between creation and a `chmod` — and deleted as
  soon as the APK exists, which is before the Firebase CLI and its transitive tree run as the same
  user in the same job.
- **The signing passwords stay on the runner, and it rests on three external defaults.** AGP writes
  them in plaintext into `build/intermediates/signing_config_data/…`, and that output stays out of
  the shared build cache only because AGP annotates `SigningConfigWriterTask`
  `@DisableCachingByDefault`. The Gradle *configuration* cache holds them too, in the project's
  `.gradle/` directory, and `setup-gradle` saves configuration-cache data only when given a
  `cache-encryption-key` — which this workflow does not supply. `ci.yml`'s artifact globs
  (`**/build/reports/…`) reach neither. Written down because Actions caches saved from the default
  branch are readable by fork pull requests: if any one of those three defaults changed, a release
  run would start leaking the passwords, and nothing in this repository would report it.
- **`firebase-tools` is installed with `--ignore-scripts`, before any credential is written.** The
  pinned version covers one package; `npm install` resolves its transitive tree at install time and
  would otherwise run every dependency's lifecycle script in a step holding the upload key and the
  service account. Verified that the CLI still works with scripts ignored. A committed lockfile and
  `npm ci` would pin the tree itself, and is the next step if this channel outlives the assignment.
- **WiX is pinned and detected as WiX 3 specifically.** `candle`, not the `wix` dotnet tool: that
  one is WiX 4+, which only JDK 22's jpackage can drive, and this build runs on JDK 21. A separate
  step then confirms `candle` resolves, because `GITHUB_PATH` only affects later steps and a
  chocolatey package that installs elsewhere would otherwise fail as a jpackage error after a full
  build.

## Alternatives considered

- **The Firebase App Distribution Gradle plugin.** First-party and it would keep the whole flow in
  Gradle. Rejected for this project's shape: it adds a plugin and a service-account path to the
  build classpath, so a misconfiguration becomes a build failure for everyone rather than a
  workflow failure for the release job — and none of it can be exercised locally.
- **A third-party distribution action** (`wzieba/Firebase-Distribution-Github-Action` and
  similar). One less script, but it puts a community action in the credential path for the only
  secret in the repository, and ADR-0005's SHA pinning does not make an action's code trustworthy —
  only stable.
- **Distributing every push to `main`.** Genuinely "仮の", and rejected: testers would be notified of
  every merge, and a release channel that cries wolf is not one anybody installs from.
- **`packageReleaseDistributionForCurrentOS`.** Smaller artifacts, ProGuard rules to discover, on a
  path with no local reproduction. Revisit if the Desktop download size ever matters.
- **Signing the `.dmg` now.** Needs the Apple Developer ID certificate that PR18 brings, and
  bringing it forward would put a second set of secrets in this PR for a warning dialog.
