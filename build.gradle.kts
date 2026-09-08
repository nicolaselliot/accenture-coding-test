// Root build. Deliberately thin: per-module configuration lives in build-logic convention plugins,
// so a module's build file states what it *is*, not how it is wired.
//
// Every plugin below is declared with `apply false` except ktlint. That is not decoration: declaring
// them here puts them all in one shared classloader on the root buildscript classpath. Without it, a
// plugin applied from build-logic and a plugin applied from the main build land in sibling
// classloaders that cannot see each other, and ktlint fails at apply time with a
// NoClassDefFoundError on KotlinMultiplatformExtension.

plugins {
    // Gives the root the standard lifecycle tasks — `check`, `build`, `assemble`, `clean`. Applied
    // for `check`: it is what lets `./gradlew build`, the everyday gate, reach into the included
    // build below. Its `clean` also replaces the hand-written one this file used to declare.
    base
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.detekt) apply false
    // Applied, not merely declared, so the root's own .kts files are formatted too.
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint)
    ignoreFailures.set(false)
}

// Root tasks do not descend into an included build, so build-logic would otherwise sit outside the
// gate that .editorconfig claims is enforced. Wire it in explicitly.
tasks.named("ktlintCheck") {
    dependsOn(gradle.includedBuild("build-logic").task(":ktlintCheck"))
}
tasks.named("ktlintFormat") {
    dependsOn(gradle.includedBuild("build-logic").task(":ktlintFormat"))
}

// Its tests do not run from the main build for the same reason: `./gradlew build` matches the
// subprojects that have a `build` task, and an included build is not one of them. Named rather
// than folded into ktlintCheck, so what CI runs says what it verifies.
val checkBuildLogic =
    tasks.register("checkBuildLogic") {
        group = "verification"
        description = "Runs build-logic's own check task — its unit tests and formatting."
        dependsOn(gradle.includedBuild("build-logic").task(":check"))
    }

// On the root's `check`, so the everyday `./gradlew build` runs it. These are the tests that decide
// whether a prod artifact can carry a token, which makes "CI will catch it" the wrong place for
// them to live alone: a developer editing the generator would otherwise get a green local gate.
tasks.named("check") {
    dependsOn(checkBuildLogic)
}
