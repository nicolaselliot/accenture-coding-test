import org.gradle.api.artifacts.VersionCatalogsExtension

//
// Formatting and static analysis, applied to every module.
//
// ktlint is the formatter; detekt is the static-analysis gate. Both fail the build rather than
// warn — a lint gate that only warns is a lint gate nobody reads. See docs/adr/0003 for what
// detekt does and does not cover on a multiplatform project.

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

ktlint {
    // From the catalogue, not a literal: the root build drives the same engine, and two copies of
    // the version drift into a file that passes at the root and fails in a module.
    version.set(libs.findVersion("ktlint").get().requiredVersion)
    // CI must fail on a violation rather than quietly reformatting.
    ignoreFailures.set(false)
    filter {
        // Generated sources are not ours to format. invariantSeparatorsPath, because File.path uses
        // backslashes on Windows and this project builds an .msi there — a literal "/" comparison
        // would lint generated code on one OS only.
        exclude { it.file.invariantSeparatorsPath.contains("/build/generated/") }
    }
}

detekt {
    // detekt defaults to the JVM layout (src/main/kotlin) and finds nothing in a KMP module, so the
    // multiplatform source sets have to be named explicitly.
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/androidMain/kotlin",
        "src/desktopMain/kotlin",
        "src/desktopTest/kotlin",
        "src/iosMain/kotlin",
    )
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true

    // Report paths become relative to the repository root, which keeps CI output and any future
    // baseline file portable across machines. It does *not* affect how exclude globs are matched —
    // those are still absolute, which is why config/detekt/detekt.yml drops the default
    // '**/test/**' exclusion. See docs/adr/0003.
    basePath = rootProject.projectDir.absolutePath
}
