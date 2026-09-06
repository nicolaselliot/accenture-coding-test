package dev.nicolas.githubsearch.domain

import dev.nicolas.githubsearch.core.common.Outcome

/**
 * Results per page.
 *
 * One screenful plus a buffer. Larger pages would reach the ceiling below in fewer requests but
 * make each payload heavier for a list that only renders names.
 */
public const val SEARCH_PAGE_SIZE: Int = 30

/**
 * The most results GitHub's search will serve for one query, across all pages.
 *
 * Not a page count — GitHub rejects any request where `page * per_page` exceeds this, with a 422.
 */
private const val SEARCH_RESULT_CAP: Int = 1_000

/**
 * The highest page that can be requested without a 422.
 *
 * Derived rather than written down, so changing [SEARCH_PAGE_SIZE] cannot leave a stale number
 * behind. Note the consequence: at 30 per page this is 33, so the reachable maximum is **990
 * items, not 1,000** — the cap is not divisible by the page size and the last page is not partial,
 * it is simply unreachable.
 */
public const val LAST_SEARCH_PAGE: Int = SEARCH_RESULT_CAP / SEARCH_PAGE_SIZE

/** GitHub's search pages are one-based; page 0 is a 422, not the first page. */
public const val FIRST_SEARCH_PAGE: Int = 1

/** The shortest query worth spending a request on. */
public const val MIN_QUERY_LENGTH: Int = 2

/**
 * Whether a query is worth spending a request on.
 *
 * Exported so the caller gating a submit control and the guard below apply the *same* rule. Exposing
 * only [MIN_QUERY_LENGTH] exports half of it: a caller checking `query.length` enables submit for
 * `" a "`, which trims to one character, and the user is told nothing matched a search that was
 * never sent.
 */
public fun isSearchable(query: String): Boolean = query.trim().length >= MIN_QUERY_LENGTH

/**
 * Searches GitHub repositories, refusing the requests that cannot succeed.
 *
 * Both refusals exist for the same reason: unauthenticated search allows ten requests a minute, and
 * that is the budget the reviewer will actually be running against. A one-character query and a
 * page past the ceiling are both certain to be wasted, so neither reaches the network.
 *
 * A refusal returns an empty success rather than a failure. Nothing went wrong — there is simply
 * nothing to show — and an error would put a retry control on screen for a query the user has to
 * edit first.
 *
 * Note what that costs: an empty result here is indistinguishable from GitHub matching nothing. The
 * minimum-length branch is therefore a **backstop protecting the request budget**, not an answer
 * the caller is meant to interpret. The ViewModel decides between "keep typing" and "no matches" —
 * it holds the query and has to gate the submit control on the same rule anyway.
 */
public class SearchRepositoriesUseCase(
    private val repository: GithubRepositoryPort,
) {
    public suspend operator fun invoke(
        query: String,
        page: Int,
    ): Outcome<List<RepositorySummary>> {
        val trimmed = query.trim()

        // Both refusals are the same decision — "this request cannot produce anything" — so they
        // read as branches of one `when` rather than as guard clauses scattered before the work.
        return when {
            !isSearchable(query) -> Outcome.Success(emptyList())

            // Both ends, not just the ceiling. GitHub answers page 0 and negative pages with the
            // same 422 it gives page 34, so each is a request certain to fail.
            page !in FIRST_SEARCH_PAGE..LAST_SEARCH_PAGE -> Outcome.Success(emptyList())

            else -> repository.search(trimmed, page)
        }
    }
}
