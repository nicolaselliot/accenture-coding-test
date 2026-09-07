import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("githubsearch.desktop.application")
}

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
            packageName = "GitHubSearch"
            packageVersion = "1.0.0"
            vendor = "dev.nicolas"
        }
    }
}
