import dev.nicolas.githubsearch.buildlogic.GenerateAppConfigTask
import java.util.Properties

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
// Values resolve in this order, first hit wins:
// 1. an environment variable — how CI supplies them;
// 2. the gitignored developer properties file — how a developer supplies them locally;
// 3. the documented default.
//
// The token has no default. Absent means unauthenticated, and the app must work that way, because
// the reviewer will run it without a PAT.
//
// The token variable is deliberately **not** named `GITHUB_TOKEN`. That name is exported by the `gh`
// CLI and injected into every GitHub Actions job, so reading it would silently compile whatever PAT
// happens to be in the environment into a release binary, where `strings` can recover it. The
// project-scoped name has to be set on purpose.
//
// Embedding a token is still only appropriate for a local dev build. PR14 introduces the release
// variants, and that is where a release build must refuse to embed one.

plugins {
    // Declared so this script gets the typed `kotlin { }` accessor. Idempotent: the module also
    // applies githubsearch.kmp.library, which applies the same plugin.
    id("org.jetbrains.kotlin.multiplatform")
}

/**
 * Gitignored, never committed; the agent is blocked from reading it by a PreToolUse hook.
 *
 * Read through `providers.fileContents` rather than `File.readText`. A plain file read at
 * configuration time is invisible to the configuration cache, so editing the token would not
 * invalidate the cached configuration and the regenerated value would silently be the stale one.
 */
val developerProperties: Provider<Properties> =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText
        .map { text -> Properties().apply { text.reader().use { load(it) } } }

fun configValue(
    environmentKey: String,
    propertyKey: String,
    default: String,
): Provider<String> =
    providers
        .environmentVariable(environmentKey)
        .orElse(developerProperties.map { it.getProperty(propertyKey).orEmpty() })
        .map { it.ifEmpty { default } }
        .orElse(default)

val generateAppConfig =
    tasks.register<GenerateAppConfigTask>("generateAppConfig") {
        packageName.set("dev.nicolas.githubsearch.core.common.config")
        flavor.set(configValue("GITHUBSEARCH_FLAVOR", "flavor", default = "dev"))
        baseUrl.set(configValue("GITHUBSEARCH_BASE_URL", "baseUrl", default = "https://api.github.com"))
        // NONE unless explicitly overridden: a default that logs is a default that leaks.
        logLevel.set(configValue("GITHUBSEARCH_LOG_LEVEL", "logLevel", default = "NONE"))
        githubToken.set(configValue("GITHUBSEARCH_GITHUB_TOKEN", "github.token", default = ""))
        outputDirectory.set(layout.buildDirectory.dir("generated/appconfig/commonMain/kotlin"))
    }

kotlin {
    sourceSets.commonMain {
        // Wiring the task provider, not the directory, carries the task dependency with it.
        kotlin.srcDir(generateAppConfig)
    }
}
