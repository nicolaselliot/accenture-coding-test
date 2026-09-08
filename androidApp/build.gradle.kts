import com.android.build.api.artifact.SingleArtifact
import dev.nicolas.githubsearch.buildlogic.CopyComposeResourcesToAssetsTask

plugins {
    id("githubsearch.android.application")
}

dependencies {
    implementation(project(":shared"))
    // setContent and enableEdgeToEdge. Android-only, so it lives here rather than in a shared
    // module — :androidApp is not multiplatform.
    implementation(libs.androidx.activity.compose)
}

// The Compose resource bundle, packaged as assets.
//
// :core:designsystem owns every user-facing string but cannot deliver them itself: AGP 9's
// `com.android.kotlin.multiplatform.library` plugin has no assets pipeline, so a shared module's
// variants expose no assets directory and its AAR carries none. The application module is the only
// one whose variants do, so it packages what that module publishes. See docs/adr/0010.
val composeResourceBundles: Configuration by
    configurations.creating {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    // By configuration name rather than by attribute: the published artifact is a plain directory
    // and has no business taking part in Android variant matching.
    composeResourceBundles(
        project(mapOf("path" to ":core:designsystem", "configuration" to "androidComposeResources")),
    )
}

// Compose Resources reach an Android APK as *assets*, and nothing else in the build notices when
// they do not. The Gradle task that copies them is registered whether or not it has anywhere to
// write, so a broken wiring produces a green build, a complete APK and a crash on the first
// `stringResource` — which is exactly what shipped before this check existed. Every other gate in
// the project passes in that state, so the assertion has to be on the assets themselves.
//
// Merged assets, specifically: the stage where every module's contribution has landed, and the last
// one before packaging. That is where the failure showed up — zero `composeResources/` entries — and
// it is reached by an asset merge alone, with no R8 and no APK. What it does not cover, then, is a
// packaging-level exclusion; this build declares none, and buying that coverage would mean
// `BuiltArtifactsLoader` and a minified APK on every `check`.
androidComponents {
    onVariants { variant ->
        val packageComposeResources =
            tasks.register<CopyComposeResourcesToAssetsTask>(
                "package${variant.name.replaceFirstChar(Char::titlecase)}ComposeResources",
            ) {
                bundles.from(composeResourceBundles)
                outputDirectory.set(layout.buildDirectory.dir("generated/composeResources/${variant.name}"))
            }

        // Registering the directory is what carries the task dependency too, so the assets are
        // built before they are merged rather than by luck of task ordering.
        variant.sources.assets?.addGeneratedSourceDirectory(
            packageComposeResources,
            CopyComposeResourcesToAssetsTask::outputDirectory,
        )

        // The merged assets of the whole app, every module's contribution included, taken through
        // the artifact API so the check is wired to the producing task rather than to a path.
        val assets = variant.artifacts.get(SingleArtifact.ASSETS)

        // Resolved here, at configuration time, into a plain String. A `doLast` that mentions
        // `variant` captures the whole ApplicationVariant — and through it the Kotlin/AGP
        // compilation graph — which the configuration cache cannot serialise.
        val variantName = variant.name
        val report = layout.buildDirectory.file("reports/composeResources/$variantName.txt")

        val verify =
            tasks.register("verify${variantName.replaceFirstChar(Char::titlecase)}ComposeResources") {
                group = "verification"
                description = "Fails when the Compose resource bundle is missing from the packaged assets."

                inputs.dir(assets)
                // A task with no declared output can never be up to date, and this runs on `check`.
                outputs.file(report)

                doLast {
                    // `.cvr` is the compiled value-resource bundle every `stringResource` reads
                    // from. Its absence is the failure; counting them also catches a bundle that
                    // is packaged but empty.
                    val bundles =
                        assets
                            .get()
                            .asFile
                            .resolve("composeResources")
                            .walkTopDown()
                            .filter { it.isFile && it.extension == "cvr" }
                            .toList()

                    report.get().asFile.apply { parentFile.mkdirs() }.writeText(
                        "compose resource bundles: ${bundles.size}\n" +
                            bundles.joinToString("\n") { it.name },
                    )

                    if (bundles.isEmpty()) {
                        error(
                            "No Compose resource bundle was packaged into the $variantName assets. " +
                                "Every user-facing string is served from one, so the app would crash on " +
                                "its first screen. Check that the Compose plugin's " +
                                "copyAndroidMainComposeResourcesToAndroidAssets task has an output " +
                                "directory wired into the variant's assets.",
                        )
                    }
                }
            }

        // Every variant, the one that ships included. `SingleArtifact.ASSETS` is produced by
        // `merge<Variant>Assets`, which sits upstream of R8, so gating release costs an asset merge
        // rather than the minified APK it looks like it would.
        tasks.named("check") { dependsOn(verify) }
    }
}
