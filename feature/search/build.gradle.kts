plugins {
    id("githubsearch.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:designsystem"))
            implementation(project(":domain"))
        }
    }
}
