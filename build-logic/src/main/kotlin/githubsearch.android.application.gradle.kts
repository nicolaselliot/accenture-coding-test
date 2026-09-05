import org.gradle.api.artifacts.VersionCatalogsExtension

// Convention for the Android entry point. The only module that may use the application plugin. */

plugins {
    // AGP 9 compiles Kotlin itself; applying org.jetbrains.kotlin.android is an error since 9.0.
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("githubsearch.lint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun sdk(name: String): Int =
    libs
        .findVersion(name)
        .get()
        .requiredVersion
        .toInt()

android {
    namespace = "dev.nicolas.githubsearch"
    compileSdk = sdk("compileSdk")

    defaultConfig {
        applicationId = "dev.nicolas.githubsearch"
        minSdk = sdk("minSdk")
        targetSdk = sdk("targetSdk")
        // versionCode comes from the CI run number at release time; a hand-maintained counter
        // collides the first time two branches ship.
        versionCode = (providers.gradleProperty("versionCode").orNull ?: "1").toInt()
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}
