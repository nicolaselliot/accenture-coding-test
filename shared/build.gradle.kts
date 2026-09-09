plugins {
    id("githubsearch.kmp.compose")
    // Navigation 3 routes are @Serializable NavKeys, and the back stack is persisted through
    // kotlinx-serialization. Version comes from the root declaration.
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    // The framework Xcode links against, and the only artifact iOS sees of this whole graph.
    //
    // Declared here rather than in the `githubsearch.kmp.library` convention: every shared module
    // has iOS targets, but only :shared is an entry point, and a framework per module would be
    // eleven frameworks for Xcode to embed instead of one composition root.
    //
    // Configured through `withType` rather than by naming `iosArm64 { }` and `iosSimulatorArm64 { }`
    // in turn, so adding a target in the convention plugin does not silently produce a target with
    // no framework — the failure would be an Xcode link error naming a missing architecture.
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.framework {
            // Pinned in IMPLEMENTATION_PLAN.md → Fixed parameters. Xcode imports it under this
            // name, so changing it is an Xcode-project change as well as a Gradle one.
            baseName = "Shared"

            // Static, which is the Compose Multiplatform default and not merely a size choice:
            // a dynamic framework has to be embedded *and* signed as an app-bundle dependency, and
            // duplicate-symbol failures between it and the Compose runtime are the documented
            // reason the templates set this.
            isStatic = true
        }
    }

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

// The release frameworks are declared above but deliberately kept out of `assemble`, and therefore
// out of `build`.
//
// Nothing consumes one there. Only an Xcode archive does, and release.yml links it as an explicit
// step so that one task — and nothing else — gets the large heap its whole-program pass needs.
// Left attached, every `./gradlew build` on macOS paid for two of them: 18 minutes each on a CI
// runner, for an artifact the build then discarded. And they would now fail outright, because the
// heap that made them fit is no longer set globally — see gradle.properties.
//
// The link tasks still exist and can be asked for by name; they are simply no longer implied.
tasks.named("assemble") {
    setDependsOn(dependsOn.filterNot { it.toString().contains("linkReleaseFramework") })
}
