package dev.nicolas.githubsearch.domain

import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.core.testing.FakeGithubRepository
import dev.nicolas.githubsearch.core.testing.KOTLIN_SUMMARY
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchRepositoriesUseCaseTest {
    @Test
    fun `returns the summaries the port provides`() =
        runTest {
            val port = FakeGithubRepository()

            val outcome = SearchRepositoriesUseCase(port)(query = "  kotlin  ", page = 1)

            assertEquals(Outcome.Success(listOf(KOTLIN_SUMMARY)), outcome)
            assertEquals(listOf("kotlin" to 1), port.searches)
        }

    @Test
    fun `returns an empty result when nothing matches`() =
        runTest {
            val port = FakeGithubRepository(searchResult = Outcome.Success(emptyList()))

            val outcome = SearchRepositoriesUseCase(port)(query = "zzzznotathing", page = 1)

            // Empty is a legitimate answer, not a failure. Modelling it as an error would show a
            // retry control for a search that worked perfectly.
            assertEquals(Outcome.Success(emptyList()), outcome)
        }

    @Test
    fun `passes a failure through unchanged`() =
        runTest {
            val failure = Outcome.Failure(AppError.Network)
            val port = FakeGithubRepository(searchResult = failure)

            val outcome = SearchRepositoriesUseCase(port)(query = "kotlin", page = 1)

            // The use case adds no interpretation of its own; the error the network produced is
            // the error the screen renders.
            assertEquals(failure, outcome)
        }

    @Test
    fun `a blank query never reaches the port`() =
        runTest {
            val port = FakeGithubRepository()

            val outcome = SearchRepositoriesUseCase(port)(query = "   ", page = 1)

            assertEquals(Outcome.Success(emptyList()), outcome)
            assertTrue(port.searches.isEmpty(), "a blank query must not spend a request")
        }

    @Test
    fun `a single character query reaches the port`() =
        runTest {
            val port = FakeGithubRepository()

            SearchRepositoriesUseCase(port)(query = "k", page = 1)

            // 「何かしらのキーワードを入力できる」— `q=k` is a valid GitHub search that returns
            // real repositories, so refusing it made the app narrower than its own specification.
            // This is the assertion that fails if the floor is ever raised again without an ADR.
            assertEquals(listOf("k" to 1), port.searches)
        }

    @Test
    fun `the last reachable page is requested`() =
        runTest {
            val port = FakeGithubRepository()

            SearchRepositoriesUseCase(port)(query = "kotlin", page = LAST_SEARCH_PAGE)

            assertEquals(listOf("kotlin" to LAST_SEARCH_PAGE), port.searches)
        }

    @Test
    fun `a page past the ceiling never reaches the port`() =
        runTest {
            val port = FakeGithubRepository()

            val outcome = SearchRepositoriesUseCase(port)(query = "kotlin", page = LAST_SEARCH_PAGE + 1)

            // GitHub answers page * per_page > 1000 with a 422. Stopping here turns the end of the
            // list into a clean stop instead of an error at the bottom of an infinite scroll.
            assertEquals(Outcome.Success(emptyList()), outcome)
            assertTrue(port.searches.isEmpty())
        }

    @Test
    fun `the reachable result ceiling is 990 items rather than 1000`() {
        // A tripwire on a Fixed parameter rather than a behaviour test. GitHub rejects
        // page * per_page > 1000, and 1000 is not divisible by 30 — so the last page is not
        // partial, it is unreachable, and a test asserting 1000 would encode a ceiling no caller
        // can ever reach.
        assertEquals(990, LAST_SEARCH_PAGE * SEARCH_PAGE_SIZE)
    }

    @Test
    fun `a page below the first never reaches the port`() =
        runTest {
            val port = FakeGithubRepository()

            val outcome = SearchRepositoriesUseCase(port)(query = "kotlin", page = 0)

            // The ceiling had a guard and the floor did not. GitHub answers page=0 with the same
            // 422 it gives page=34, so both spend a request that cannot succeed — and the budget
            // is ten a minute.
            assertEquals(Outcome.Success(emptyList()), outcome)
            assertTrue(port.searches.isEmpty())
        }

    @Test
    fun `isSearchable applies the same trimming the use case does`() =
        runTest {
            // The trap this closes: a caller checking `query.length >= MIN_QUERY_LENGTH` enables
            // submit for "   " (length 3), the use case trims to "" and returns empty, and the
            // user is told nothing matched a search that was never sent. One predicate, both
            // sides.
            assertEquals(false, isSearchable("   "))
            // " a " trims to one character, which is searchable now — the padding is what the
            // trimming removes, not the reason to refuse. It was the false case here while the
            // floor was two, and is the clearest single assertion that the floor has moved.
            assertEquals(true, isSearchable(" a "))
            assertEquals(true, isSearchable(" kt "))

            val port = FakeGithubRepository()
            SearchRepositoriesUseCase(port)(query = "   ", page = 1)
            assertTrue(port.searches.isEmpty())
        }
}
