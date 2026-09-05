//
// Convention for the Desktop entry point.
//
// Exists so the module applies plugins without carrying versions: a `plugins { alias(...) }` with an
// explicit version in a subproject loads the Kotlin Gradle plugin a second time, which Gradle warns
// is unsupported and may break the build.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("githubsearch.lint")
}

kotlin {
    jvmToolchain(21)
    jvm()
}
