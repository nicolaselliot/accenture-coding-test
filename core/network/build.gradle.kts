plugins {
    id("githubsearch.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
        }
    }
}
