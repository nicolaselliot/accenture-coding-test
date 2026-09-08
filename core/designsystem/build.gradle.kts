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

compose.resources {
    // The generated Res class is internal by default, which would make it unreachable from the
    // feature modules this bundle exists to serve. Naming the package as well keeps the import
    // stable if the module is ever renamed.
    publicResClass = true
    packageOfResClass = "dev.nicolas.githubsearch.core.designsystem.generated.resources"
}

// The base bundle is the contract every locale has to meet. A key missing from a translation does
// not fail the build on its own — Compose Resources silently serves the English string, which is
// the worst outcome because it looks intentional. This turns that into a build failure, so the next
// PR that adds a string cannot forget the other half.
val verifyTranslations =
    tasks.register("verifyTranslations") {
        group = "verification"
        description = "Fails when a locale bundle does not declare exactly the base bundle's keys."

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
            val keyPattern = Regex("""<string name="([^"]+)"""")

            val baseKeys =
                keyPattern
                    .findAll(File(composeResourcesDir, "values/strings.xml").readText())
                    .map { it.groupValues[1] }
                    .toSet()
            val problems = mutableListOf<String>()

            val localeDirs =
                composeResourcesDir
                    .listFiles()
                    .orEmpty()
                    .filter { it.isDirectory && it.name.startsWith("values-") }
                    .sortedBy { it.name }

            localeDirs.forEach { dir ->
                val strings = File(dir, "strings.xml")
                if (!strings.exists()) {
                    problems += "${dir.name} has no strings.xml"
                    return@forEach
                }

                val localeKeys =
                    keyPattern.findAll(strings.readText()).map { it.groupValues[1] }.toSet()
                val missing = (baseKeys - localeKeys).sorted()
                val extra = (localeKeys - baseKeys).sorted()

                if (missing.isNotEmpty()) {
                    problems += "${dir.name} is missing ${missing.size}: ${missing.joinToString()}"
                }
                if (extra.isNotEmpty()) {
                    problems += "${dir.name} declares keys absent from values/: ${extra.joinToString()}"
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
