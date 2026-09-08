package dev.nicolas.githubsearch.feature.search

import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.testing.KOTLIN_SUMMARY
import dev.nicolas.githubsearch.domain.RepositoryId
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * What counts as a change worth cross-fading.
 *
 * `AnimatedContent` compares the key, not the value, and the results phase is a new value on every
 * appended page. Keyed on the value it would fade the whole list out and back in each time another
 * thirty rows arrived — losing scroll position to a screen that flickers while the user reads it.
 *
 * The key has to cut the other way too. `AnimatedContent` reuses the composition group behind a
 * repeated key, `rememberLazyListState` included, and one key for all results would hand a fresh
 * search the previous one's scroll offset — visibly, whenever GitHub answers inside the 300ms fade
 * the outgoing results are still running.
 *
 * Both directions are pinned against the *request*. The separating test is written from the pair
 * that defeats any key derived from the rows — two searches leading with the same repository —
 * because a pair that merely differs would pass just as well against the wrong rule.
 */
class SearchPhaseTransitionTest {
    @Test
    fun `appending a page is not a change worth cross-fading`() {
        val request = SearchRequestId(1)
        val firstPage = SearchPhase.Content(persistentListOf(KOTLIN_SUMMARY), request)
        val appending = SearchPhase.Content(persistentListOf(KOTLIN_SUMMARY), request, isAppending = true)
        val appended = SearchPhase.Content(persistentListOf(KOTLIN_SUMMARY, OTHER_SUMMARY), request)

        assertEquals(firstPage.transitionKey, appending.transitionKey)
        assertEquals(firstPage.transitionKey, appended.transitionKey)
    }

    @Test
    fun `a different search is a different thing to cross-fade to even when led by the same row`() {
        val first = SearchPhase.Content(persistentListOf(KOTLIN_SUMMARY), SearchRequestId(1))
        val second = SearchPhase.Content(persistentListOf(KOTLIN_SUMMARY, OTHER_SUMMARY), SearchRequestId(2))

        assertNotEquals(first.transitionKey, second.transitionKey)
    }

    @Test
    fun `every phase cross-fades into every other`() {
        val phases =
            listOf(
                SearchPhase.Idle,
                SearchPhase.Loading,
                SearchPhase.Empty,
                SearchPhase.Failed(AppError.Network),
                SearchPhase.Content(persistentListOf(), SearchRequestId(1)),
            )

        val keys = phases.map { it.transitionKey }

        // A duplicate here is two states that swap in place with no transition at all — the
        // failure this catches is an empty result rendering as if nothing had happened.
        assertEquals(phases.size, keys.toSet().size, "phases share a transition key: $keys")
    }
}

// A different repository, not a renamed copy: rows are identified by id, so a copy sharing
// KOTLIN_SUMMARY's id would be the same row to everything that keys on one.
private val OTHER_SUMMARY =
    KOTLIN_SUMMARY.copy(
        id = RepositoryId(2),
        coordinates = KOTLIN_SUMMARY.coordinates.copy(name = "kotlinx"),
    )
