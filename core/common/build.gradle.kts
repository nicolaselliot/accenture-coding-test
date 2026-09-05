plugins {
    id("githubsearch.kmp.library")
    // Generates AppConfig into commonMain. Lives here because it is the lowest module every other
    // one can already see, so no layer has to reach upward for build-time configuration.
    id("githubsearch.appconfig")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api, not implementation: DispatcherProvider names CoroutineDispatcher in its own
            // public signature, so every consumer needs the type on its compile classpath.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
