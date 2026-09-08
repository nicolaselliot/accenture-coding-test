package dev.nicolas.githubsearch.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Ktor's name for "log nothing" — the level a prod build is **floored** to, whatever was asked for.
 *
 * Not the same thing as the default in `githubsearch.appconfig.gradle.kts`, which happens to be the
 * same string: that one is what a build gets when nobody configures a level, and someone may
 * legitimately change it. This one is the floor, and the two are deliberately *not* one symbol —
 * sharing it would mean an edit meant to make dev builds chattier silently lifted prod's floor.
 *
 * A literal rather than a reference to `LogLevel.NONE`, because build logic cannot see Ktor. Were
 * the two ever to drift, the failure mode is silence — `GithubClientConfig` maps an unrecognised
 * level to NONE — rather than an artifact that logs.
 */
private const val NO_LOGGING = "NONE"

/**
 * Generates `AppConfig` into a source directory wired onto `commonMain`.
 *
 * The generated object is the single place build-time configuration crosses into shared code. That
 * is what lets `:core:network` take the token as a constructor parameter rather than reading a
 * build property itself, keeping the network module free of any knowledge of Gradle.
 *
 * Caching is disabled deliberately: the output can embed an optional GitHub token, and a task whose
 * output contains a secret must never be pushed to a shared build cache.
 */
@DisableCachingByDefault(
    because = "Output can embed an optional token; it must never reach a shared build cache",
)
public abstract class GenerateAppConfigTask : DefaultTask() {
    @get:Input
    public abstract val packageName: Property<String>

    /**
     * Typed rather than a name, so an unrecognised flavor fails while the build is being
     * configured — before anything has been generated from it. [AppFlavor.of] is the only way in.
     */
    @get:Input
    public abstract val flavor: Property<AppFlavor>

    @get:Input
    public abstract val baseUrl: Property<String>

    /** Ktor logging level name. Anything shipped must be NONE — see the security gate in CLAUDE.md. */
    @get:Input
    public abstract val logLevel: Property<String>

    /** Optional. Empty means the app runs unauthenticated, which must always work. */
    @get:Input
    public abstract val githubToken: Property<String>

    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    @TaskAction
    public fun generate() {
        val pkg = packageName.get()
        val selectedFlavor = flavor.get()
        val configuredToken = githubToken.get()

        // The two values a flavor normalises, decided here rather than by whoever runs the build.
        // A release job inherits whatever environment it runs in, and a developer keeps a token in
        // their properties file for weeks — so "remember to unset it first" is not a control.
        // Dropping the token cannot be deferred to runtime either: it is a compile-time constant
        // in shared code, and `strings` recovers one of those from a release binary.
        val embeddedToken = if (selectedFlavor.embedsToken) configuredToken else ""
        val effectiveLogLevel = if (selectedFlavor.permitsLogging) logLevel.get() else NO_LOGGING

        val packageDir = outputDirectory.get().asFile.resolve(pkg.replace('.', '/'))
        packageDir.mkdirs()

        // What became of the token, never the token itself and never its length — either would put
        // a secret in CI output.
        val tokenState =
            when {
                embeddedToken.isNotEmpty() -> "present"
                configuredToken.isNotEmpty() -> "dropped, the ${selectedFlavor.id} flavor embeds none"
                else -> "absent"
            }

        // The flavor and the level are named because they decide what the artifact carries, and
        // this is the only line in a build that says which one it was configured for.
        logger.lifecycle(
            "AppConfig: flavor=${selectedFlavor.id}, logLevel=$effectiveLogLevel, token=$tokenState",
        )

        packageDir.resolve("AppConfig.kt").writeText(
            render(pkg, selectedFlavor, baseUrl.get(), effectiveLogLevel, embeddedToken),
        )
    }

    /**
     * Renders the file from values alone.
     *
     * Every input is a parameter, none is read from a task property here: two of them are
     * normalised by [generate], and a renderer that reached back for the raw property of one field
     * while being handed the normalised value of another is how a later normalisation gets
     * silently bypassed.
     */
    private fun render(
        pkg: String,
        flavor: AppFlavor,
        baseUrl: String,
        logLevel: String,
        token: String,
    ): String =
        buildString {
            appendLine("// Generated by GenerateAppConfigTask. Do not edit, do not commit.")
            appendLine("package $pkg")
            appendLine()
            appendLine("/** Build-time configuration: the only crossing point from Gradle into shared code. */")
            appendLine("public object AppConfig {")
            appendLine("    public const val FLAVOR: String = ${token(flavor.id)}")
            appendLine("    public const val BASE_URL: String = ${token(baseUrl)}")
            appendLine("    public const val LOG_LEVEL: String = ${token(logLevel)}")
            appendLine()
            appendLine("    /** Optional PAT. Empty means unauthenticated, which is a supported mode. */")
            appendLine("    public const val GITHUB_TOKEN: String = ${token(token)}")
            appendLine()
            appendLine("    public val hasToken: Boolean get() = GITHUB_TOKEN.isNotEmpty()")
            appendLine("}")
        }

    /**
     * Renders a Kotlin string literal. A token is opaque text, so a stray quote, backslash, newline, tab
     * or `$` must not be able to break out of the literal and change what compiles.
     *
     * Backslash is escaped first; doing it after the others would double-escape what they inserted.
     */
    private fun token(value: String): String {
        val escaped =
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        return "\"" + escaped + "\""
    }
}
