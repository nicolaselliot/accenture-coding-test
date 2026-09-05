plugins {
    `kotlin-dsl`
    // build-logic holds the only real logic in the scaffolding, so it is linted like any other
    // module rather than exempted for being "just build code".
    alias(libs.plugins.ktlint)
}

group = "dev.nicolas.githubsearch.buildlogic"

kotlin {
    // Must match the toolchain the main build uses, or the precompiled script plugins are compiled
    // against a different JDK than the code that applies them.
    jvmToolchain(21)
}

ktlint {
    version.set(libs.versions.ktlint)
    ignoreFailures.set(false)
}

dependencies {
    // Convention plugins apply these by id, which requires them on build-logic's compile classpath.
    implementation(libs.plugin.kotlin.gradle)
    implementation(libs.plugin.kotlin.composeCompiler)
    implementation(libs.plugin.kotlin.serialization)
    implementation(libs.plugin.android.gradle)
    implementation(libs.plugin.compose.gradle)
    implementation(libs.plugin.ktlint.gradle)
    implementation(libs.plugin.detekt.gradle)
}
