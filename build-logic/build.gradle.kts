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

    // build-logic decides what a build variant embeds, so it is the one place in this project where
    // a defect ships a secret rather than a bug. Its tests drive the real Gradle tasks.
    //
    // From the catalogue like every other version: `kotlin("test")` would resolve against Gradle's
    // embedded Kotlin instead, leaving the test classpath a version behind the stdlib that
    // `libs.plugin.kotlin.gradle` already puts on it. ProjectBuilder needs no declaration of its
    // own — `kotlin-dsl` contributes gradleApi(), and the test classpath extends that.
    testImplementation(libs.kotlin.test)
}

// JUnit Platform, which is also how kotlin-test resolves to its JUnit 5 variant — and that variant
// is what brings the engine and the launcher Gradle 9 no longer supplies from its own distribution.
// `@TempDir` comes from the same place.
tasks.test {
    useJUnitPlatform()
}
