plugins {
    id("githubsearch.kmp.compose")
    // Navigation 3 routes are @Serializable NavKeys, and the back stack is persisted through
    // kotlinx-serialization. Version comes from the root declaration.
    id("org.jetbrains.kotlin.plugin.serialization")
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

            // Declared rather than inherited through :core:designsystem's api. GithubSearchApp
            // names @Composable, remember, Surface, Modifier and safeDrawingPadding directly, and
            // relying on another module's api to supply them makes this module stop compiling if
            // that module ever narrows one to implementation.
            implementation(compose.runtime)
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(compose.ui)

            // Navigation 3. navigation3-runtime arrives transitively from androidx.navigation3 —
            // org.jetbrains.androidx.navigation3 publishes only the -ui artifact, so declaring a
            // runtime coordinate under that group would not resolve.
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)
            implementation(libs.androidx.savedstate)
            implementation(libs.kotlinx.serialization.core)

            // The composition root. :shared is the only module that may see a port and its
            // implementation, which is what makes the whole graph its business — see AppModules.
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)

            // Images are loaded through their own client; see AppModules for why it must not be
            // the API client.
            implementation(libs.coil.compose)
            implementation(libs.coil.networkKtor)
        }

        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.koin.test)
        }
    }
}
