plugins {
    id("githubsearch.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":domain"))
        }
    }
}
