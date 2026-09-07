plugins {
    id("githubsearch.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api: SearchUiState names AppError and RepositorySummary in its own public shape, so
            // :shared cannot render this screen's state without those types on its classpath.
            api(project(":core:common"))
            api(project(":domain"))
            implementation(project(":core:designsystem"))

            // ViewModel and SavedStateHandle are named in SearchViewModel's own declaration.
            // Both arrive through -compose transitively; declared because they are used directly.
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelSavedstate)
            // koinViewModel() and collectAsStateWithLifecycle(), used by the stateful screen only.
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.composeViewmodel)

            // List is unstable to the Compose compiler, so state that crosses into a composable
            // holds an ImmutableList instead. See the note on SearchPhase.Content.
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)

            // The owner avatar on a row. The ImageLoader itself is the application's to configure,
            // and does not exist until the entry points land in PR10.
            implementation(libs.coil.compose)
        }

        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
