package dev.nicolas.githubsearch.data.github

import dev.nicolas.githubsearch.core.testing.KOTLIN_SUMMARY
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun summaries(body: String) = json.decodeFromString<SearchResponseDto>(body).items.map { it.toDomain() }

class SearchResponseMappingTest {
    @Test
    fun `maps a complete search item to a summary`() {
        assertEquals(KOTLIN_SUMMARY, summaries(searchBody(searchItem())).single())
    }

    @Test
    fun `keeps language null when the API omits it`() {
        assertNull(summaries(searchBody(searchItem(language = null))).single().language)
    }

    @Test
    fun `an empty result set maps to an empty list rather than failing`() {
        // A search that matches nothing is a successful search. Treating it as a failure would put
        // a retry control on screen for a query that worked perfectly.
        assertTrue(summaries(searchBody()).isEmpty())
    }

    @Test
    fun `a full_name without a separator is rejected as a serialization failure`() {
        // SerializationException specifically, not IllegalArgumentException: only the former is
        // translated to AppError.Serialization by :core:network. See GithubMappers.
        assertFailsWith<SerializationException> {
            summaries(searchBody(searchItem(fullName = "kotlin")))
        }
    }

    @Test
    fun `a full_name with too many segments is rejected`() {
        assertFailsWith<SerializationException> {
            summaries(searchBody(searchItem(fullName = "a/b/c")))
        }
    }
}
