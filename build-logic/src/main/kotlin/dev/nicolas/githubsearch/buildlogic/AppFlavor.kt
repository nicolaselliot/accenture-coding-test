package dev.nicolas.githubsearch.buildlogic

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project

/**
 * The environment a build is configured for: `dev` for a developer's own machine, `prod` for what
 * ships.
 *
 * One flavor is selected per Gradle invocation, not per Android variant. `AppConfig` is generated
 * into `:core:common`'s `commonMain`, which is compiled once for every target and every Android
 * variant at the same time, so a single build cannot hold two different configurations. That is why
 * `:androidApp` builds only the selected flavor's variants — see docs/adr/0011.
 *
 * The enum, rather than the string it is parsed from, is what the rest of the build passes around:
 * the Android product flavor, the Desktop distribution name and the generated `AppConfig` then
 * cannot disagree about how the flavor is spelled.
 */
public enum class AppFlavor(
    /** The name used by the Android product flavor, the Gradle property and the generated config. */
    public val id: String,
) {
    /**
     * A developer's own build. May embed a personal access token, which raises the search rate
     * limit from 10 requests a minute to 30 while working on the app.
     */
    Dev("dev"),

    /**
     * What ships. Never carries a token and never logs: see [GenerateAppConfigTask], which
     * normalises both rather than trusting whoever runs the release job.
     */
    Prod("prod"),
    ;

    /**
     * Whether an artifact of this flavor carries a configured personal access token.
     *
     * Only the dev flavor does. The token is a compile-time constant in shared code, so this is
     * decided when the code is generated and cannot be undone later by a runtime check.
     */
    public val embedsToken: Boolean
        get() = this == Dev

    /**
     * Whether an artifact of this flavor may log HTTP traffic at all.
     *
     * `CLAUDE.md` requires a shipped build to log nothing, and the flavor is the only axis that
     * decides it: a single Gradle invocation generates one `AppConfig` for every Android variant,
     * so the build type is not visible from here. Logging is off by default on both flavors
     * regardless, which is what keeps a `devRelease` artifact quiet too — this only decides
     * whether an explicit override is honoured.
     */
    public val permitsLogging: Boolean
        get() = this == Dev

    public companion object {
        /** `-Pgithubsearch.flavor=prod`, or a line in `gradle.properties`. */
        public const val GRADLE_PROPERTY: String = "githubsearch.flavor"

        /** How CI selects a flavor. Project-scoped, like every other variable this build reads. */
        public const val ENVIRONMENT_VARIABLE: String = "GITHUBSEARCH_FLAVOR"

        /** The key in the gitignored developer properties file. */
        public const val DEVELOPER_PROPERTY: String = "flavor"

        /** What a build with no opinion gets. A developer's machine is a developer's build. */
        public val Default: AppFlavor = Dev

        /**
         * Parses a flavor name, failing the build on anything else.
         *
         * Every source of this value is hand-edited, so the name is trimmed and matched without
         * regard to case. It is deliberately *not* defaulted when unrecognised or blank: `dev` is
         * the flavor that may embed a token, so a typo resolving to it silently is the wrong
         * direction to fail in. A release job whose flavor variable expanded to nothing must stop,
         * not ship a dev-configured binary.
         */
        public fun of(name: String): AppFlavor =
            entries.firstOrNull { it.id.equals(name.trim(), ignoreCase = true) }
                ?: throw InvalidUserDataException(
                    "'$name' is not an app flavor. Use one of: ${entries.joinToString { it.id }}. " +
                        "Select it with -P$GRADLE_PROPERTY=<flavor>, the $ENVIRONMENT_VARIABLE " +
                        "environment variable, or $DEVELOPER_PROPERTY=<flavor> in local.properties.",
                )
    }
}

/**
 * The flavor this invocation is configured for.
 *
 * Sources, first one present wins: the Gradle property, so a flavor can be selected per invocation;
 * then the environment variable, which is how CI selects one; then the gitignored developer
 * properties file, for a machine that always builds the same way.
 *
 * Resolved eagerly rather than as a `Provider`, because the Android variant filter needs the value
 * while the build is being configured. Each source is read through [org.gradle.api.provider.ProviderFactory],
 * so the configuration cache treats it as an input and a changed flavor invalidates the cached
 * configuration instead of being ignored.
 */
public fun Project.requestedAppFlavor(): AppFlavor {
    val requested =
        providers.gradleProperty(AppFlavor.GRADLE_PROPERTY).orNull
            ?: providers.environmentVariable(AppFlavor.ENVIRONMENT_VARIABLE).orNull
            ?: developerProperties.orNull?.getProperty(AppFlavor.DEVELOPER_PROPERTY)
            ?: return AppFlavor.Default

    return AppFlavor.of(requested)
}
