plugins {
    id("githubsearch.kmp.library")
}

kotlin {
    sourceSets {
        // Deliberately commonMain, not commonTest: this module's *production* source is other
        // modules' test material. It stays a leaf because only *Test source sets may depend on it —
        // a rule the module graph documents and code review enforces.
        commonMain.dependencies {
            api(project(":core:common"))
            implementation(project(":domain"))
            // api, not implementation: TestDispatcherProvider exposes TestDispatcher in its own
            // signature, so every consumer needs the type on its compile classpath.
            api(libs.kotlinx.coroutines.test)
        }
    }
}
