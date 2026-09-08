//
// Convention for shared modules that contain Compose UI. Layers on top of `githubsearch.kmp.library`
// so a module never has to restate the multiplatform setup to draw something.

plugins {
    id("githubsearch.kmp.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

composeCompiler {
    // Stability regressions are visible rather than theoretical — but only when asked for, because
    // metrics slow every compilation. Enable with -PcomposeCompilerReports=true.
    if (providers.gradleProperty("composeCompilerReports").orNull == "true") {
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}

// No `withDeviceTestBuilder` here, deliberately, and it is the first thing a reader will look for:
// PR15 was meant to run the UI suite on Android device tests too. Two independent blockers stop it,
// both probed rather than assumed — see docs/adr/0012. The wiring is one block to restore when
// either is fixed upstream.

kotlin {
    // Required because of the `dependsOn` edges below, and the failure without it is a long way
    // from its cause: declaring one edge by hand switches off the default hierarchy template, which
    // is what creates `iosMain` — so `:core:designsystem`'s expect declarations stop seeing their
    // iOS actuals and the error names `FormatCount.kt`, not this file. Asking for the template
    // explicitly keeps it, and the edges below are then additive.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // A source set for `runComposeUiTest` suites, sitting between commonTest and the targets
        // that can actually render.
        //
        // `commonTest` would be the obvious home and is the wrong one: this project runs commonTest
        // on Android's *host* JVM as well (see `withHostTest`), and a CMP UI test cannot run there
        // at all. It reaches for the Android idling strategy, which probes for Robolectric through
        // `Build.FINGERPRINT` — null off-device — and every test in the suite dies on the same
        // NullPointerException. Robolectric is not the answer either: CMP UI tests do not run under
        // it. So the suite is offered to Desktop and iOS, which render headlessly, and withheld
        // from a compilation where it can only fail.
        //
        // An Android device test compilation would belong here too, if ADR-0012's blockers lift.
        val uiTest =
            create("uiTest") {
                dependsOn(commonTest.get())

                dependencies {
                    // From the catalogue, not the plugin's `compose.uiTest` accessor: 1.12.0
                    // deprecates that accessor in favour of naming the coordinate, which is also
                    // what this project's own rule about versions asks for.
                    //
                    // The API is still @ExperimentalTestApi, which is why every suite opts in
                    // explicitly rather than the flag being set build-wide — the opt-in is where a
                    // reader will look when it changes under them.
                    implementation(libs.findLibrary("compose-ui-test").get())
                }
            }

        named("desktopTest") {
            dependsOn(uiTest)

            dependencies {
                // Skiko for the JVM test runtime. Without it the suite compiles and then fails at
                // run time with an UnsatisfiedLinkError, because nothing has brought the renderer
                // for this host. desktopMain gets it from :desktopApp in the app graph; a library
                // module's own tests do not.
                implementation(compose.desktop.currentOs)
            }
        }

        // Both Apple test source sets, so the suite compiles for the device target as well as
        // running on the simulator — a UI test that only compiles for the simulator would let an
        // iosArm64-only break through to PR17.
        iosArm64Test.get().dependsOn(uiTest)
        iosSimulatorArm64Test.get().dependsOn(uiTest)
    }
}

// The Desktop suite renders with no display, which is the only way it can run on a CI runner.
// Declared rather than left to the default, so the environment the tests pass in locally is the one
// they meet on Linux: a machine with a display would otherwise exercise a path CI never takes, and
// the first sign of it would be 33 red tests on `main` rather than one red test here.
tasks.named<Test>("desktopTest") {
    systemProperty("java.awt.headless", "true")
}
