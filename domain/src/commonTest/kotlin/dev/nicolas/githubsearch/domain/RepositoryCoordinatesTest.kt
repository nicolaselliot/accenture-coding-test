package dev.nicolas.githubsearch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

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

    @Test
    fun `parse reads a well formed owner and name`() {
        val parsed = RepositoryCoordinates.parse("JetBrains/kotlin")

        assertEquals(RepositoryCoordinates(owner = "JetBrains", name = "kotlin"), parsed)
    }

    @Test
    fun `parse returns null when the separator is missing or repeated`() {
        // Total where the constructor throws, so a caller at a wire boundary can translate a bad
        // value into its own error vocabulary instead of guessing which exception init raises.
        assertNull(RepositoryCoordinates.parse("kotlin"))
        assertNull(RepositoryCoordinates.parse("a/b/c"))
        assertNull(RepositoryCoordinates.parse(""))
    }

    @Test
    fun `parse returns null when a half is blank`() {
        // These split into exactly two parts, so a segment count check alone lets them through.
        assertNull(RepositoryCoordinates.parse("a/"))
        assertNull(RepositoryCoordinates.parse("/b"))
        assertNull(RepositoryCoordinates.parse("/"))
    }

    @Test
    fun `parse returns null when a half is a traversal segment`() {
        assertNull(RepositoryCoordinates.parse("./kotlin"))
        assertNull(RepositoryCoordinates.parse("JetBrains/.."))
    }

    @Test
    fun `a half that could restructure the request path is rejected`() {
        // The literal '/' and ".." are not the only ways out. The path handed to Ktor is treated
        // as already percent-encoded and stored verbatim, so an encoded separator or traversal
        // ("%2F", "%2e%2e") would reach the wire as authored — and '?' or '#' would be read
        // structurally as the start of a query or fragment. None of these characters occurs in a
        // GitHub owner or repository name, so rejecting them cannot refuse valid data.
        listOf("%2e%2e", "%2F", "a?b", "a#b", "a b", "a\u0000b", "a\nb").forEach { hostile ->
            assertFailsWith<IllegalArgumentException>("owner \"$hostile\" should be rejected") {
                RepositoryCoordinates(owner = hostile, name = "kotlin")
            }
            assertFailsWith<IllegalArgumentException>("name \"$hostile\" should be rejected") {
                RepositoryCoordinates(owner = "JetBrains", name = hostile)
            }
            assertNull(RepositoryCoordinates.parse("$hostile/kotlin"))
        }
    }

    @Test
    fun `ordinary GitHub names are still accepted`() {
        // The guard above is a denylist of path-structuring characters, not a charset allowlist —
        // so dots, dashes and underscores, which GitHub does allow, must still pass.
        listOf("kotlinx.coroutines", "some-repo", "some_repo", "repo.js", "a1").forEach { valid ->
            assertEquals(valid, RepositoryCoordinates(owner = "JetBrains", name = valid).name)
        }
    }
}
