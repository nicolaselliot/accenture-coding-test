package dev.nicolas.githubsearch.buildlogic

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The selection chain, as seen from a build script.
 *
 * The environment-variable leg is not covered: a test cannot set an environment variable for the
 * process it runs in, and faking `ProviderFactory` would assert the fake's precedence rather than
 * Gradle's. It is the one leg CI exercises on every run.
 *
 * `@TempDir` rather than a hand-rolled temporary directory: JUnit deletes it recursively after each
 * test, which `File.deleteOnExit()` cannot do — that only removes a directory if it is still empty,
 * and `ProjectBuilder` fills this one.
 */
class AppFlavorSelectionTest {
    @field:TempDir
    lateinit var projectDir: File

    @Test
    fun `selects dev when nothing asks for a flavor`() {
        assertEquals(AppFlavor.Dev, project().requestedAppFlavor())
    }

    @Test
    fun `takes the flavor a developer properties file names`() {
        File(projectDir, "local.properties").writeText("flavor=prod\n")

        assertEquals(AppFlavor.Prod, project().requestedAppFlavor())
    }

    @Test
    fun `takes the flavor a gradle property names, over the one in the properties file`() {
        // The per-invocation source wins, so `-Pgithubsearch.flavor=prod` selects prod without the
        // developer having to edit — and then remember to revert — a file.
        File(projectDir, "local.properties").writeText("flavor=dev\n")
        File(projectDir, "gradle.properties").writeText("githubsearch.flavor=prod\n")

        assertEquals(AppFlavor.Prod, project().requestedAppFlavor())
    }

    private fun project(): Project = ProjectBuilder.builder().withProjectDir(projectDir).build()
}
