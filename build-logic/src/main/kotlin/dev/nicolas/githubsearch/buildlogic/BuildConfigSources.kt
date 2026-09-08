package dev.nicolas.githubsearch.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.util.Properties

/**
 * The gitignored developer properties file, read lazily.
 *
 * Read through `providers.fileContents` rather than `File.readText`. A plain file read at
 * configuration time is invisible to the configuration cache, so editing the token would not
 * invalidate the cached configuration and the regenerated value would silently be the stale one.
 *
 * The agent is blocked from reading or writing this file by a `PreToolUse` hook; it is created by
 * hand.
 */
internal val Project.developerProperties: Provider<Properties>
    get() =
        providers
            .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
            .asText
            .map { text -> Properties().apply { text.reader().use { load(it) } } }

/**
 * A build-time configuration value: an environment variable if CI set one, else the developer
 * properties file, else the documented default.
 *
 * Deliberately **not** readable from a Gradle property, unlike the flavor selected by
 * [requestedAppFlavor]. Gradle properties are also read from the committed `gradle.properties`,
 * and a value that may be a secret must have no source inside version control at all. The flavor
 * is a build selection and can never be one.
 *
 * An empty value resolves to the default, which is what an exported-but-unset environment variable
 * should mean — the token's documented "absent" mode is exactly an empty string. Note that it does
 * **not** fall through to the next source: an environment variable exported as empty has a value as
 * far as Gradle is concerned, so it shadows the properties file and lands on the default. Safe in
 * every direction that matters (the fallback is always the quieter, tokenless value) but surprising,
 * and pre-existing behaviour from PR1 rather than something to change while adding flavors.
 */
public fun Project.buildConfigValue(
    environmentKey: String,
    propertyKey: String,
    default: String,
): Provider<String> =
    providers
        .environmentVariable(environmentKey)
        .orElse(developerProperties.map { it.getProperty(propertyKey).orEmpty() })
        .map { it.ifEmpty { default } }
        .orElse(default)
