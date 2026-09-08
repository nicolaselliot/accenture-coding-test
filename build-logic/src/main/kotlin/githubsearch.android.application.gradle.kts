import dev.nicolas.githubsearch.buildlogic.AppFlavor
import dev.nicolas.githubsearch.buildlogic.requestedAppFlavor
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

/** How AGP spells a flavor inside a variant task name: `prod` becomes `assembleProdRelease`. */
fun unselectedName(flavor: AppFlavor): String = flavor.id.replaceFirstChar(Char::titlecase)

// The one flavor this invocation is configured for. Read once: the product flavors below and the
// filter under `androidComponents` have to agree with the `AppConfig` generated in :core:common.
val selectedFlavor = requestedAppFlavor()

// Only one dimension, and there will not be a second: a dimension multiplies the variant count,
// and nothing here varies by anything but environment.
val environmentDimension = "environment"

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

    flavorDimensions += environmentDimension

    productFlavors {
        // Named from AppFlavor rather than as literals, so the Android flavor, the Gradle property
        // and the generated AppConfig cannot drift in how the name is spelled.
        create(AppFlavor.Dev.id) {
            dimension = environmentDimension
            // A distinct applicationId, so dev and prod install side by side on one device: a
            // reviewer comparing them does not have to uninstall one to see the other. The
            // launcher label differs too, through src/dev/res — by variant, not by a runtime `if`.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        create(AppFlavor.Prod.id) {
            dimension = environmentDimension
        }
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

// Only the selected flavor's variants are built, which is what keeps an artifact and the config
// compiled into it from disagreeing.
//
// `AppConfig` is generated into :core:common's `commonMain` and compiled once for the whole
// invocation, so a build cannot hold two configurations at the same time. Left unfiltered,
// `./gradlew build` would assemble a prod-flavored APK carrying whatever dev was configured with —
// a developer's personal access token included. Filtering makes that combination unbuildable
// rather than merely discouraged. See docs/adr/0011.
//
// The cost is that a flavor is selected per invocation: `./gradlew assembleProdRelease` needs
// `-Pgithubsearch.flavor=prod` alongside it, or the task does not exist. The rule below answers
// that case in words; `gradle.properties` documents the property; CI assembles both flavors.
androidComponents {
    beforeVariants { variant ->
        variant.enable = variant.flavorName == selectedFlavor.id
    }
}

// Asking for the flavor that is not selected gets an explanation rather than Gradle's bare "task
// 'assembleProdRelease' not found in project ':androidApp'", which says nothing about the property
// that would have created it. That error is the first thing a reviewer meets if they reach for the
// shipping variant by name, so it is worth answering properly.
//
// A rule rather than a set of placeholder tasks: it covers assemble, install, bundle, lint and
// anything else AGP names after a variant, without adding one task per name to `./gradlew tasks`.
// Matched on the capitalised id, which is how AGP spells a flavor inside a task name.
val unselectedFlavors = AppFlavor.entries - selectedFlavor

fun explainUnselectedFlavor(taskName: String) {
    val requested = unselectedFlavors.firstOrNull { taskName.contains(unselectedName(it)) } ?: return

    // Read into locals here, at configuration time. A `doFirst` that mentions a script-level `val`
    // captures the script object itself, which the configuration cache cannot serialise; a local is
    // just a string.
    val requestedFlavor = requested.id
    val configuredFlavor = selectedFlavor.id

    tasks.register(taskName) {
        group = "build"
        description = "Explains that the $requestedFlavor flavor is not selected in this invocation."

        doFirst {
            throw InvalidUserDataException(
                "Task '$taskName' names the $requestedFlavor flavor, but this invocation is " +
                    "configured for $configuredFlavor, so only $configuredFlavor's variants exist. " +
                    "Add -P${AppFlavor.GRADLE_PROPERTY}=$requestedFlavor to build it. One invocation " +
                    "configures one flavor — see docs/adr/0011.",
            )
        }
    }
}

tasks.addRule(
    // Listed by `./gradlew tasks` under "Rules", so the pattern doubles as documentation.
    "Pattern: <task>${unselectedFlavors.map(::unselectedName).joinToString("|")}<BuildType>: " +
        "says how to select that flavor",
    // The Action overload, spelled out: a bare lambda binds addRule's Closure overload instead. Its
    // parameter arrives as the receiver, which is why the task name is `this`.
    Action<String> { explainUnselectedFlavor(this) },
)

kotlin {
    jvmToolchain(21)
}
