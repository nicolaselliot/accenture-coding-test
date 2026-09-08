import dev.nicolas.githubsearch.buildlogic.GenerateAppConfigTask
import dev.nicolas.githubsearch.buildlogic.buildConfigValue
import dev.nicolas.githubsearch.buildlogic.requestedAppFlavor

//
// Generates `AppConfig` into this module's `commonMain`, so build-time configuration crosses into
// shared code in exactly one place. That is what lets `:core:network` take the token as a
// constructor parameter instead of reading a build property itself.
//
// This is a precompiled script plugin rather than a binary `Plugin<Project>` on purpose: wiring a
// generated source directory needs the Kotlin Multiplatform extension type, and a binary plugin
// resolves that type from a different classloader than the one the project's Kotlin plugin was
// loaded in, which fails at apply time with a NoClassDefFoundError.
//
// Where the values come from is `BuildConfigSources.kt`: an environment variable for CI, the
// gitignored developer properties file for a local machine, then the documented default. The
// flavor additionally reads a Gradle property, because selecting one per invocation is the whole
// point of it; the values that may hold a secret deliberately do not.
//
// The token has no default. Absent means unauthenticated, and the app must work that way, because
// the reviewer will run it without a PAT. Only the dev flavor may embed one at all — the task
// drops it on prod, which is where a release build refuses to carry a secret.
//
// The token variable is deliberately **not** named `GITHUB_TOKEN`. That name is exported by the `gh`
// CLI and injected into every GitHub Actions job, so reading it would silently compile whatever PAT
// happens to be in the environment into a release binary, where `strings` can recover it. The
// project-scoped name has to be set on purpose.

plugins {
    // Declared so this script gets the typed `kotlin { }` accessor. Idempotent: the module also
    // applies githubsearch.kmp.library, which applies the same plugin.
    id("org.jetbrains.kotlin.multiplatform")
}

// Resolved here rather than inside the task configuration block: it is a property of the whole
// invocation, and `:androidApp` reads the same value to decide which variants it builds.
val selectedFlavor = requestedAppFlavor()

val generateAppConfig =
    tasks.register<GenerateAppConfigTask>("generateAppConfig") {
        packageName.set("dev.nicolas.githubsearch.core.common.config")
        flavor.set(selectedFlavor)
        baseUrl.set(buildConfigValue("GITHUBSEARCH_BASE_URL", "baseUrl", default = "https://api.github.com"))
        // NONE unless explicitly overridden: a default that logs is a default that leaks. This is
        // the dev-side default only — prod's floor is enforced inside the task, so raising this
        // cannot make a shipped build log.
        logLevel.set(buildConfigValue("GITHUBSEARCH_LOG_LEVEL", "logLevel", default = "NONE"))
        githubToken.set(buildConfigValue("GITHUBSEARCH_GITHUB_TOKEN", "github.token", default = ""))
        outputDirectory.set(layout.buildDirectory.dir("generated/appconfig/commonMain/kotlin"))
    }

kotlin {
    sourceSets.commonMain {
        // Wiring the task provider, not the directory, carries the task dependency with it.
        kotlin.srcDir(generateAppConfig)
    }
}
