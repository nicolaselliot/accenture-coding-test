plugins {
    id("githubsearch.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The only dependency :domain may have. No Ktor, no Compose, no DTO, no androidx —
            // if one appears here the layering is broken and the fix is upstream, not a workaround.
            implementation(project(":core:common"))
        }
    }
}
