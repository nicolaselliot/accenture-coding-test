package dev.nicolas.githubsearch.buildlogic

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives the real task, not a copy of its logic.
 *
 * `ProjectBuilder` gives a task instance with working properties, and the assertion is on the file
 * that lands on disk — which is what the Kotlin compiler will read. A test against an extracted
 * `render` helper would pass while the wiring that reaches it was broken.
 *
 * `@TempDir` rather than a hand-rolled temporary directory: JUnit deletes it recursively after each
 * test, which `File.deleteOnExit()` cannot do — that only removes a directory if it is still empty,
 * and the generated file fills this one.
 */
class GenerateAppConfigTaskTest {
    @field:TempDir
    lateinit var root: File

    @Test
    fun `generates a config object carrying the values it was given`() {
        val generated =
            generate {
                baseUrl.set("https://api.github.example")
                logLevel.set("INFO")
                githubToken.set("a-token")
            }

        assertTrue(generated.contains("""public const val BASE_URL: String = "https://api.github.example""""))
        assertTrue(generated.contains("""public const val LOG_LEVEL: String = "INFO""""))
        assertTrue(generated.contains("""public const val GITHUB_TOKEN: String = "a-token""""))
    }

    @Test
    fun `writes the config into the directory its package names`() {
        generate { packageName.set("dev.nicolas.example.config") }

        assertTrue(outputDirectory.resolve("dev/nicolas/example/config/AppConfig.kt").isFile)
    }

    @Test
    fun `a token cannot break out of the literal it is generated into`() {
        // Every character that ends a Kotlin string literal, opens a template, or ends the line the
        // literal is on. A token is opaque text from a properties file: if one of these escaped the
        // literal the generated source would either not compile or compile as something else.
        val generated = generate { githubToken.set("a\"b\\c\$d\ne\tf") }

        assertTrue(
            generated.contains("""public const val GITHUB_TOKEN: String = "a\"b\\c\${'$'}d\ne\tf""""),
            generated,
        )
    }

    @Test
    fun `omits nothing when the token is absent`() {
        // Absent is a supported mode, not a degraded one: the reviewer runs this app without a PAT.
        // The constant still has to exist, because shared code reads it unconditionally.
        val generated = generate { githubToken.set("") }

        assertTrue(generated.contains("""public const val GITHUB_TOKEN: String = """""))
        assertFalse(generated.contains("hasToken: Boolean get() = GITHUB_TOKEN.isEmpty()"))
        assertTrue(generated.contains("hasToken: Boolean get() = GITHUB_TOKEN.isNotEmpty()"))
    }

    @Test
    fun `the prod flavor drops a configured token`() {
        // The reviewer runs the shipped build unauthenticated, so prod has nothing to gain from a
        // token and everything to lose: `strings` recovers a constant from a release binary. The
        // developer keeping one in local.properties must not have to remember to remove it before
        // a release build.
        val generated =
            generate {
                flavor.set(AppFlavor.Prod)
                githubToken.set("a-token")
            }

        assertTrue(generated.contains("""public const val GITHUB_TOKEN: String = """""), generated)
        assertFalse(generated.contains("a-token"), generated)
    }

    @Test
    fun `the prod flavor logs nothing, whatever was asked for`() {
        val generated =
            generate {
                flavor.set(AppFlavor.Prod)
                logLevel.set("ALL")
            }

        assertTrue(generated.contains("""public const val LOG_LEVEL: String = "NONE""""), generated)
    }

    @Test
    fun `the dev flavor keeps the token and the log level it was given`() {
        // The contrast matters as much as the rule: normalising both flavors would take away the
        // only reason the dev flavor exists.
        val generated =
            generate {
                flavor.set(AppFlavor.Dev)
                logLevel.set("HEADERS")
                githubToken.set("a-token")
            }

        assertTrue(generated.contains("""public const val LOG_LEVEL: String = "HEADERS""""), generated)
        assertTrue(generated.contains("""public const val GITHUB_TOKEN: String = "a-token""""), generated)
    }

    @Test
    fun `generates the package it was asked for`() {
        val generated = generate { packageName.set("dev.nicolas.example.config") }

        assertEquals("package dev.nicolas.example.config", generated.lineSequence().first { it.startsWith("package ") })
    }

    /** Where the task writes; a sibling of the project directory rather than inside it. */
    private val outputDirectory: File get() = root.resolve("generated")

    /** The defaults are the shipped ones; each test overrides only the input it is about. */
    private fun generate(configure: GenerateAppConfigTask.() -> Unit = {}): String {
        val project = ProjectBuilder.builder().withProjectDir(root.resolve("project")).build()
        val task = project.tasks.register("generateAppConfig", GenerateAppConfigTask::class.java).get()

        task.packageName.set("dev.nicolas.githubsearch.core.common.config")
        task.flavor.set(AppFlavor.Dev)
        task.baseUrl.set("https://api.github.com")
        task.logLevel.set("NONE")
        task.githubToken.set("")
        task.outputDirectory.set(outputDirectory)
        task.configure()

        task.generate()

        return outputDirectory
            .walkTopDown()
            .single { it.isFile && it.name == "AppConfig.kt" }
            .readText()
    }
}
