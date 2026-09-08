package dev.nicolas.githubsearch.buildlogic

import org.gradle.api.InvalidUserDataException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppFlavorTest {
    @Test
    fun `reads the two names the build and its properties files use`() {
        assertEquals(AppFlavor.Dev, AppFlavor.of("dev"))
        assertEquals(AppFlavor.Prod, AppFlavor.of("prod"))
    }

    @Test
    fun `reads a hand-typed name whatever its case and spacing`() {
        // Every source of this value is hand-edited: a properties file, a shell export, a -P flag.
        assertEquals(AppFlavor.Prod, AppFlavor.of("  PROD "))
    }

    @Test
    fun `rejects a name that is not a flavor, and says which ones are`() {
        val failure = assertFailsWith<InvalidUserDataException> { AppFlavor.of("staging") }

        assertTrue(failure.message!!.contains("staging"), failure.message)
        assertTrue(failure.message!!.contains("dev"), failure.message)
        assertTrue(failure.message!!.contains("prod"), failure.message)
    }

    @Test
    fun `rejects a blank name rather than reading it as the default`() {
        // `flavor=` left in a properties file must not resolve to dev by accident: dev is the
        // flavor that may embed a token, so a silent fallback is the wrong direction to fail in.
        assertFailsWith<InvalidUserDataException> { AppFlavor.of("   ") }
    }
}
