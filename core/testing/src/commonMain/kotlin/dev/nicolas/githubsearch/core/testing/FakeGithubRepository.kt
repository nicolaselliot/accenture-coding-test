package dev.nicolas.githubsearch.core.testing

import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.domain.GithubRepositoryPort
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositoryDetail
import dev.nicolas.githubsearch.domain.RepositorySummary

/**
 * A hand-written [GithubRepositoryPort] that answers with whatever it was given.
 *
 * A fake rather than a mock: it works on every target with no code generation — mocking frameworks
 * are unreliable on Native — it debugs as ordinary code, and it does not couple a test to the order
 * its methods happen to be called in.
 *
 * It records every call, because a large share of the scenarios around it assert that the port was
 * **not** reached at all: a query below the minimum length and a page past the search ceiling both
 * have to be refused before a request is spent.
 *
 * Lives here rather than beside the tests that first needed it so that `:domain`, `:data:github`
 * and both feature modules share one fake instead of maintaining four that drift.
 */
public class FakeGithubRepository(
    private val searchResult: Outcome<List<RepositorySummary>> = Outcome.Success(listOf(KOTLIN_SUMMARY)),
    private val detailResult: Outcome<RepositoryDetail> = Outcome.Success(LINUX_DETAIL),
) : GithubRepositoryPort {
    /** Every `(query, page)` this fake was asked for, in order. */
    public val searches: MutableList<Pair<String, Int>> = mutableListOf()

    /** Every coordinate this fake was asked for, in order. */
    public val details: MutableList<RepositoryCoordinates> = mutableListOf()

    override suspend fun search(
        query: String,
        page: Int,
    ): Outcome<List<RepositorySummary>> {
        searches += query to page
        return searchResult
    }

    override suspend fun detail(coordinates: RepositoryCoordinates): Outcome<RepositoryDetail> {
        details += coordinates
        return detailResult
    }
}
