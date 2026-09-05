//
// Convention for shared modules that contain Compose UI. Layers on top of `githubsearch.kmp.library`
// so a module never has to restate the multiplatform setup to draw something.

plugins {
    id("githubsearch.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    // Stability regressions are visible rather than theoretical — but only when asked for, because
    // metrics slow every compilation. Enable with -PcomposeCompilerReports=true.
    if (providers.gradleProperty("composeCompilerReports").orNull == "true") {
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}
