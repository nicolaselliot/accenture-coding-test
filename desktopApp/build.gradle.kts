import dev.nicolas.githubsearch.buildlogic.AppFlavor
import dev.nicolas.githubsearch.buildlogic.requestedAppFlavor
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("githubsearch.desktop.application")
}

// Desktop has no product flavors of its own — the JVM target is one variant — so the flavor
// arrives the same way it does everywhere else, as a Gradle property read by build-logic. It
// selects the generated AppConfig; here it decides the identity of what jpackage installs.
val selectedFlavor = requestedAppFlavor()

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            // Window and application come from here. currentOs resolves the Skiko native for the
            // machine doing the building, which is what makes `run` work locally; CI packages on
            // each runner in turn for the .dmg and .msi.
            implementation(compose.desktop.currentOs)
            // Without this, Dispatchers.Main throws IllegalStateException the first time it is
            // touched on Desktop — a first-run crash, not a subtle bug.
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.nicolas.githubsearch.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // jpackage uses packageName for install paths, so no spaces; and it requires a strict
            // MAJOR.MINOR.PATCH version with no suffix.
            //
            // Per flavor, for the reason the Android applicationId carries a `.dev` suffix: the two
            // installs must not overwrite each other, and the one in the Applications folder or the
            // Start menu has to say which it is. The suffix cannot go on packageVersion instead —
            // jpackage rejects anything but three numbers there.
            packageName =
                when (selectedFlavor) {
                    AppFlavor.Dev -> "GitHubSearchDev"
                    AppFlavor.Prod -> "GitHubSearch"
                }
            packageVersion = "1.0.0"
            vendor = "dev.nicolas"
        }
    }
}
