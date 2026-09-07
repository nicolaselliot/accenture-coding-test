plugins {
    id("githubsearch.android.application")
}

dependencies {
    implementation(project(":shared"))
    // setContent and enableEdgeToEdge. Android-only, so it lives here rather than in a shared
    // module — :androidApp is not multiplatform.
    implementation(libs.androidx.activity.compose)
}
