import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

//
// Convention for every shared library module.
//
// AGP 9 requires shared modules to use `com.android.kotlin.multiplatform.library` rather than the
// old `androidTarget()`. Its DSL block is `android {}` — `androidLibrary {}` was renamed and is
// deprecated as of AGP 9.4.0. Only `:androidApp` uses the application plugin.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("githubsearch.lint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun sdk(name: String): Int =
    libs
        .findVersion(name)
        .get()
        .requiredVersion
        .toInt()

kotlin {
    jvmToolchain(21)

    // Strict explicit API: every public declaration needs an explicit visibility and return type,
    // so a module's public surface is deliberate rather than accidental.
    explicitApi()

    android {
        // Derived from the Gradle path (":core:common" -> "...core.common") so the namespace can
        // never drift from the module it names.
        namespace = "dev.nicolas.githubsearch" +
            project.path
                .removePrefix(":")
                .replace(':', '.')
                .replace("-", "")
                .let { ".$it" }
        compileSdk = sdk("compileSdk")
        minSdk = sdk("minSdk")
    }

    jvm("desktop")

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // Warnings are defects that have not failed yet. Kept as warnings locally; CI decides.
        allWarningsAsErrors.set(false)
    }

    targets.withType<org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
    }

    sourceSets {
        commonTest.dependencies {
            // Every module tests with kotlin.test, so it is a convention rather than eleven
            // identical declarations. It is multiplatform, so it does not break the iOS test
            // compilation the way a JVM-only assertion library would.
            implementation(kotlin("test"))
        }
    }
}
