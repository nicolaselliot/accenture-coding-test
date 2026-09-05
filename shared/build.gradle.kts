plugins {
    id("githubsearch.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:designsystem"))
            implementation(project(":core:network"))
            implementation(project(":domain"))
            // :shared is the only module that may see both a port and its implementation — that is
            // what makes it the composition root rather than just another module.
            implementation(project(":data:github"))
            implementation(project(":feature:search"))
            implementation(project(":feature:detail"))
        }
    }
}
