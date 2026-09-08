import java.io.File

plugins {
    id("githubsearch.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api, not implementation: this module's public declarations name types from each of
            // these, so a consumer cannot use them without the type on its compile classpath.
            //   material3  -> LightColorScheme/DarkColorScheme (ColorScheme), AppShapes (Shapes),
            //                 AppTypography (Typography)
            //   foundation -> AppShapes exposes CornerBasedShape from foundation.shape
            //   animation  -> AppMotion's easings are androidx.compose.animation.core.Easing
            //   runtime    -> AppTheme is @Composable
            //   ui         -> Spacing exposes Dp, and the schemes are built from Color
            // material3 happens to re-export foundation and animation-core transitively today,
            // but relying on that makes the guarantee accidental; declare what we expose.
            //
            // :core:common is deliberately absent: nothing here imports it. AppError is mapped to
            // a message in the layer that shows it, not in the theme, so this module needs the
            // string bundle it will own in PR12 but not the error type.
            api(compose.runtime)
            api(compose.material3)
            api(compose.foundation)
            api(compose.animation)
            api(compose.ui)

            // api: rememberWindowSizeClass returns a WindowSizeClass, so that type is this
            // module's ABI. window-core is the stable line — see docs/adr/0009 for why the
            // material3-adaptive route was not taken.
            api(libs.androidx.window.core)

            // The string bundle. api, not implementation: feature modules read Res off this
            // module, so the resource runtime has to be on their compile classpath too.
            api(compose.components.resources)
        }
    }
}

private val resourcePackage = "dev.nicolas.githubsearch.core.designsystem.generated.resources"

compose.resources {
    // The generated Res class is internal by default, which would make it unreachable from the
    // feature modules this bundle exists to serve. Naming the package as well keeps the import
    // stable if the module is ever renamed.
    publicResClass = true
    packageOfResClass = resourcePackage
}

// The bundle, laid out the way the Android runtime looks for it, published for :androidApp to
// package as assets.
//
// This module cannot deliver them itself. Compose Resources are read from `assets` on Android, and
// AGP 9's `com.android.kotlin.multiplatform.library` plugin has no assets pipeline: its variants
// report `sources.assets == null` and its AAR contains no `assets/` entry. The Compose plugin's own
// `copyAndroidMainComposeResourcesToAndroidAssets` task is registered anyway, but the null means it
// never gets an output directory, so it is silently skipped — a green build, a complete APK, and a
// MissingResourceException on the first screen. See docs/adr/0010.
//
// The re-layout belongs here rather than in the consumer: `preparedResources` has no package
// segment, and the package is this module's to know.
val androidComposeAssets =
    tasks.register<Sync>("androidComposeAssets") {
        group = "compose resources"
        description = "Lays this module's Compose resources out the way an Android APK carries them."

        // The published directory is the assets *root*, so the `composeResources/` prefix has to
        // be part of the layout rather than part of the path it is published from — the consumer
        // copies this directory's contents straight into `assets/`, and a prefix left implicit in
        // the publication path is simply lost there.
        from(tasks.named("prepareComposeResourcesTaskForCommonMain")) {
            into("composeResources/$resourcePackage")
        }
        into(layout.buildDirectory.dir("androidComposeAssets"))
    }

// A plain named configuration with no attributes, consumed by path rather than matched: this module
// publishes several Kotlin/Android variants and attribute matching between them is exactly the
// ambiguity a one-off artifact like this does not need to take part in.
val androidComposeResources: Configuration by
    configurations.creating {
        isCanBeConsumed = true
        isCanBeResolved = false
    }

artifacts.add(androidComposeResources.name, androidComposeAssets)

// The base bundle is the contract every locale has to meet. A key missing from a translation does
// not fail the build on its own — Compose Resources silently serves the English string, which is
// the worst outcome because it looks intentional. This turns that into a build failure, so the next
// PR that adds a string cannot forget the other half.
val verifyTranslations =
    tasks.register("verifyTranslations") {
        group = "verification"
        description = "Fails when a locale bundle is missing, or its keys and placeholders differ from the base."

        // Discovery alone cannot enforce that a translation exists. Delete `values-ja` and
        // `localeDirs` below is simply empty, so every check passes and the app falls back to
        // English on every string — the same silent failure this task exists to catch, one level
        // up. The assignment requires ja and en, and en is the base bundle.
        val requiredLocales = setOf("values-ja")

        // Resolved here, at configuration time, into a plain File. A script-level `val` referenced
        // from `doLast` captures the script object itself, which the configuration cache cannot
        // serialise; a local of this block is just a path.
        val composeResourcesDir = layout.projectDirectory.dir("src/commonMain/composeResources").asFile

        inputs.dir(composeResourcesDir)
        // A task with no declared output can never be up to date, and this runs on every `check`.
        val report = layout.buildDirectory.file("reports/translations/verified.txt")
        outputs.file(report)

        doLast {
            // Deliberately no local helper function. A named function declared inside `doLast`
            // compiles to a member of the script class, which the configuration cache refuses to
            // serialise — "cannot serialize Gradle script object references". So the one-line
            // extraction is written twice rather than shared.
            //
            // Every string in both bundles is a single-line `<string name="k">body</string>`;
            // DOT_MATCHES_ALL is there so a future multi-line string is still seen rather than
            // silently skipped by both sides at once, which would pass every check below.
            val stringPattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            val placeholderPattern = Regex("""%(?:\d+\$)?[a-zA-Z]""")

            // Key to the *set* of placeholders it uses. A set, not a list: Japanese reorders
            // arguments as a matter of grammar — `search_stars` is `%1$s stars` and `スター %1$s`
            // — so position is the translator's business. Dropping or renumbering one is not.
            val basePlaceholders =
                stringPattern
                    .findAll(File(composeResourcesDir, "values/strings.xml").readText())
                    .associate { match ->
                        match.groupValues[1] to
                            placeholderPattern.findAll(match.groupValues[2]).map { it.value }.toSet()
                    }
            val baseKeys = basePlaceholders.keys
            val problems = mutableListOf<String>()

            val localeDirs =
                composeResourcesDir
                    .listFiles()
                    .orEmpty()
                    .filter { it.isDirectory && it.name.startsWith("values-") }
                    .sortedBy { it.name }

            val absent = (requiredLocales - localeDirs.map { it.name }.toSet()).sorted()
            if (absent.isNotEmpty()) {
                problems += "required locale bundle absent: ${absent.joinToString()}"
            }

            localeDirs.forEach { dir ->
                val strings = File(dir, "strings.xml")
                if (!strings.exists()) {
                    problems += "${dir.name} has no strings.xml"
                    return@forEach
                }

                val localePlaceholders =
                    stringPattern
                        .findAll(strings.readText())
                        .associate { match ->
                            match.groupValues[1] to
                                placeholderPattern.findAll(match.groupValues[2]).map { it.value }.toSet()
                        }
                val localeKeys = localePlaceholders.keys
                val missing = (baseKeys - localeKeys).sorted()
                val extra = (localeKeys - baseKeys).sorted()
                val resigned =
                    (baseKeys intersect localeKeys)
                        .filter { localePlaceholders.getValue(it) != basePlaceholders.getValue(it) }
                        .sorted()

                if (missing.isNotEmpty()) {
                    problems += "${dir.name} is missing ${missing.size}: ${missing.joinToString()}"
                }
                if (extra.isNotEmpty()) {
                    problems += "${dir.name} declares keys absent from values/: ${extra.joinToString()}"
                }
                if (resigned.isNotEmpty()) {
                    // A translation that drops `%1$s` still renders — `stringResource` ignores the
                    // spare argument — so the count simply disappears from the screen with nothing
                    // logged. Renumbering it to `%2$s` is the same class of silent wrong.
                    problems += "${dir.name} changes the placeholders of: ${resigned.joinToString()}"
                }
            }

            report.get().asFile.apply { parentFile.mkdirs() }.writeText(
                "base keys: ${baseKeys.size}\nlocales: ${localeDirs.joinToString { it.name }}\n",
            )

            if (problems.isNotEmpty()) {
                error("Translation bundles do not match values/strings.xml:\n  " + problems.joinToString("\n  "))
            }
        }
    }

tasks.named("check") { dependsOn(verifyTranslations) }
