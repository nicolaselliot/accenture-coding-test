// Root build settings.
//
// Module graph follows JetBrains' recommended KMP structure: application entry points are separate
// modules and never mixed with shared code. Dependency direction is strictly one-way —
// entry points -> shared -> feature -> domain <- data -> core. See docs/adr/0001.

rootProject.name = "kmp-github-search"

pluginManagement {
    // Convention plugins live in an included build so they are compiled, testable Kotlin rather
    // than copy-pasted blocks in eleven build files.
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // A module that declares its own repositories is a supply-chain hole: it can pull an artifact
    // from somewhere the rest of the build does not trust. Fail the build instead.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    // Resolves a JDK 21 toolchain on machines and CI runners that do not already have one.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// --- Application entry points --------------------------------------------------------------------
include(":androidApp")
include(":desktopApp")
// iosApp is an Xcode project, not a Gradle module.

// --- Composition ----------------------------------------------------------------------------------
include(":shared")

// --- Core -----------------------------------------------------------------------------------------
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:testing")

// --- Domain and data --------------------------------------------------------------------------------
include(":domain")
include(":data:github")

// --- Features ---------------------------------------------------------------------------------------
include(":feature:search")
include(":feature:detail")
