plugins {
    id("githubsearch.kmp.library")
}

kotlin {
    sourceSets {
        // Deliberately commonMain, not commonTest: this module's *production* source is other
        // modules' test material. It stays a leaf because only *Test source sets may depend on it —
        // a rule the module graph documents and code review enforces.
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":domain"))
        }
    }
}
