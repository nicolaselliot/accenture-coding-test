import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("githubsearch.desktop.application")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared"))
            // Without this, Dispatchers.Main throws IllegalStateException the first time it is
            // touched on Desktop — a first-run crash, not a subtle bug.
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        // mainClass arrives with the entry point in PR10.
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // jpackage uses packageName for install paths, so no spaces; and it requires a strict
            // MAJOR.MINOR.PATCH version with no suffix.
            packageName = "GitHubSearch"
            packageVersion = "1.0.0"
            vendor = "dev.nicolas"
        }
    }
}
