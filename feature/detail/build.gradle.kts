plugins {
    id("githubsearch.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api: DetailUiState names AppError and RepositoryDetail in its own public shape, so
            // :shared cannot render this screen's state without those types on its classpath.
            api(project(":core:common"))
            api(project(":domain"))
            implementation(project(":core:designsystem"))

            // ViewModel is named in DetailViewModel's own declaration. No savedstate artifact
            // here: the coordinates arrive as a constructor argument from the navigation key, and
            // the back stack is what survives process death — see DetailViewModel.
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.koin.core)
            implementation(libs.koin.composeViewmodel)

            implementation(libs.kotlinx.coroutines.core)

            // The owner avatar in the header. The ImageLoader itself is the application's to
            // configure, and does not exist until the entry points land in PR10.
            implementation(libs.coil.compose)
        }

        commonTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
