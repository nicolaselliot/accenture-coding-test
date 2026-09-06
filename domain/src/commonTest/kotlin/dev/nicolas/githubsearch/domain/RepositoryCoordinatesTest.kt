package dev.nicolas.githubsearch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RepositoryCoordinatesTest {
    @Test
    fun `fullName composes the owner and the name`() {
        val coordinates = RepositoryCoordinates(owner = "JetBrains", name = "kotlin")

        assertEquals("JetBrains/kotlin", coordinates.fullName)
    }

    @Test
    fun `a blank owner is rejected`() {
        // Held as two fields rather than one string precisely so a half-formed coordinate cannot
        // exist. A blank owner would build the request path "//kotlin", which GitHub answers with
        // a 404 that reads as a missing repository rather than as our own bad input.
        assertFailsWith<IllegalArgumentException> {
            RepositoryCoordinates(owner = "  ", name = "kotlin")
        }
    }

    @Test
    fun `a blank name is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RepositoryCoordinates(owner = "JetBrains", name = "")
        }
    }

    @Test
    fun `coordinates differing only in case are not the same repository`() {
        // GitHub treats owner and name case-insensitively for lookup but preserves the case it was
        // given. Normalising here would make the detail screen show a name the user did not search
        // for, so the type stores what it was handed and equality follows suit.
        val asTyped = RepositoryCoordinates(owner = "JetBrains", name = "kotlin")
        val lowercased = RepositoryCoordinates(owner = "jetbrains", name = "kotlin")

        assertNotEquals(asTyped, lowercased)
    }

    @Test
    fun `a half containing a slash is rejected`() {
        // "not blank" is the symptom; "is one safe path segment" is the invariant. Without this,
        // RepositoryCoordinates("a/b", "c") builds the request path /repos/a/b/c — a different
        // repository than the caller asked for, with no error anywhere.
        assertFailsWith<IllegalArgumentException> {
            RepositoryCoordinates(owner = "a/b", name = "kotlin")
        }
        assertFailsWith<IllegalArgumentException> {
            RepositoryCoordinates(owner = "JetBrains", name = "kotlin/extra")
        }
    }

    @Test
    fun `a half that is a path traversal segment is rejected`() {
        // "." and ".." are non-blank and slash-free, so every earlier guard passes them — yet
        // /repos/../.. resolves to a different endpoint that answers 200 with JSON which is not a
        // repository. The user would see a serialization error rather than rejected input, and a
        // coordinate restored from a navigation key after process death has no other net.
        assertFailsWith<IllegalArgumentException> { RepositoryCoordinates(owner = "..", name = "..") }
        assertFailsWith<IllegalArgumentException> { RepositoryCoordinates(owner = ".", name = "kotlin") }
    }
}
