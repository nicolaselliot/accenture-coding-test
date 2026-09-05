// Root build. Deliberately thin: per-module configuration lives in build-logic convention plugins,
// so a module's build file states what it *is*, not how it is wired.
//
// Every plugin below is declared with `apply false` except ktlint. That is not decoration: declaring
// them here puts them all in one shared classloader on the root buildscript classpath. Without it, a
// plugin applied from build-logic and a plugin applied from the main build land in sibling
// classloaders that cannot see each other, and ktlint fails at apply time with a
// NoClassDefFoundError on KotlinMultiplatformExtension.

plugins {
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

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
