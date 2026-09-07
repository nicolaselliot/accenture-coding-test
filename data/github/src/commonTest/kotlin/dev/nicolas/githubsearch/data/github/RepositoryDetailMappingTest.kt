package dev.nicolas.githubsearch.data.github

import dev.nicolas.githubsearch.core.testing.LINUX_DETAIL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

private fun detail(body: String) = json.decodeFromString<RepositoryDetailDto>(body).toDomain()

class RepositoryDetailMappingTest {
    @Test
    fun `maps every field the detail screen shows`() {
        val mapped = detail(detailBody())

        // Whole object rather than field by field: the earlier per-field version asserted seven
        // of eight properties and never touched the id, so a mapper that dropped RepositoryId
        // passed. Equality against the shared fixture cannot have that hole.
        assertEquals(LINUX_DETAIL, mapped)
    }

    @Test
    fun `watchers comes from subscribers_count and never from watchers_count`() {
        // The fixture is the shape GitHub actually returns: watchers_count is a frozen alias for
        // stargazers_count, so the two are identical while subscribers_count carries the real
        // figure. A mapper reading the obvious-looking field produces stars == watchers on every
        // repository in existence, and the detail screen shows the same number twice.
        //
        // This is the regression guard for the whole design, and it has to live here, against a
        // payload, because a mapper is the only thing that can get it wrong.
        val mapped = detail(detailBody(stars = 184_000, subscribers = 8_100))

        assertEquals(8_100, mapped.watchers)
        assertNotEquals(mapped.stars, mapped.watchers)
    }

    @Test
    fun `keeps language null when the API omits it`() {
        // Null is legitimate. Substituting a placeholder here would push a presentation decision
        // into the anticorruption layer and hide the distinction from the UI.
        assertNull(detail(detailBody(language = null)).language)
    }

    @Test
    fun `ignores fields it does not know about`() {
        // GitHub adds fields on their release schedule, not ours. A client that fails on an
        // unrecognised key breaks in production without a line of our code changing. Written as a
        // literal because the point is the payload's shape, not its values.
        val mapped =
            detail(
                """
                {
                  "id": 1, "full_name": "a/b",
                  "owner": { "avatar_url": "a", "login": "a", "type": "User" },
                  "language": "Kotlin",
                  "stargazers_count": 1, "watchers_count": 1, "subscribers_count": 1,
                  "forks_count": 1, "open_issues_count": 1,
                  "some_field_invented_next_year": { "nested": [1, 2, 3] }
                }
                """.trimIndent(),
            )

        assertEquals("Kotlin", mapped.language)
    }
}
