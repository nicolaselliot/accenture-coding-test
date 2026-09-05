package dev.nicolas.githubsearch.domain

import dev.nicolas.githubsearch.core.common.Outcome

/**
 * What this application needs from GitHub, expressed without saying how.
 *
 * Declared here and implemented in `:data:github` so the dependency points inward: the domain never
 * learns that Ktor, JSON or an HTTP status code exist. That is what lets these use cases be tested
 * against a hand-written fake with no network and no engine.
 */
public interface GithubRepositoryPort {
    /**
     * One page of search results, ordered as GitHub returns them.
     *
     * An empty list means no more results, which is how the caller learns it has reached the end
     * without needing a separate "has more" flag to keep in step.
     *
     * **The implementation must request exactly [SEARCH_PAGE_SIZE] per page.** `per_page` is not a
     * parameter here, but [LAST_SEARCH_PAGE] is derived from it: an implementation that pages at
     * 100 makes page 33 request 3,300 results, which GitHub rejects with a 422 at the bottom of
     * the list — and every test in this module would still be green, because none of them can see
     * the wire.
     */
    public suspend fun search(
        query: String,
        page: Int,
    ): Outcome<List<RepositorySummary>>

    /** The authoritative record for one repository, including the real watcher count. */
    public suspend fun detail(coordinates: RepositoryCoordinates): Outcome<RepositoryDetail>
}
