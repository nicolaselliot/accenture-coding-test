package dev.nicolas.githubsearch.data.github

import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.core.network.githubCall
import dev.nicolas.githubsearch.domain.GithubRepositoryPort
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.RepositoryDetail
import dev.nicolas.githubsearch.domain.RepositorySummary
import dev.nicolas.githubsearch.domain.SEARCH_PAGE_SIZE
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlin.time.Clock

/**
 * The GitHub side of [GithubRepositoryPort].
 *
 * Everything that knows about HTTP, JSON or GitHub's field names stops here. Callers get domain
 * types and [Outcome]; no exception crosses this boundary, because [githubCall] retires every
 * failure into the app's single error vocabulary.
 *
 * **This must be bound as a singleton.** The detail cache is instance state, so a per-injection
 * binding gives every screen an empty cache and quietly returns the detail endpoint to one request
 * per tap — against a budget of 60 an hour unauthenticated. Nothing in the type system prevents
 * that, and every test here constructs one instance directly, so no test would catch it; the DI
 * graph check is where it has to be asserted.
 */
public class GithubRepository(
    private val client: HttpClient,
    clock: Clock,
) : GithubRepositoryPort {
    /**
     * Owned rather than injected: the cache is this implementation's private business, and nothing
     * outside the data layer should be able to reach in and read or clear it.
     */
    private val detailCache = DetailCache(clock)

    /**
     * One page of search results, straight from the API every time.
     *
     * Deliberately uncached, unlike [detail]. Search results are the volatile half of this API —
     * ranking shifts and star counts move — and a stale page is more misleading than a fresh
     * request is expensive. The rate limit points the same way: 10 requests a minute
     * unauthenticated is the reviewer's budget, but it is spent on *submits*, which the caller
     * already gates, rather than on repeat views of one result set.
     *
     * Every failure arrives as [Outcome.Failure]; no exception crosses this boundary.
     */
    override suspend fun search(
        query: String,
        page: Int,
    ): Outcome<List<RepositorySummary>> =
        githubCall {
            client
                .get("search/repositories") {
                    // Through Ktor's parameter API rather than string concatenation, so a query
                    // containing &, ?, / or a space is encoded rather than changing the request.
                    parameter("q", query)
                    // The page size the domain's ceiling is derived from. Sending anything else here
                    // would make page 33 request more than GitHub will serve, and the list would end
                    // in a 422 instead of stopping cleanly.
                    parameter("per_page", SEARCH_PAGE_SIZE)
                    parameter("page", page)
                }.body<SearchResponseDto>()
                .items
                .map { it.toDomain() }
        }

    /**
     * The authoritative record for one repository, served from a cache within the TTL.
     *
     * The caching is documented here rather than on `GithubRepositoryPort` because it is not part
     * of the promise: the port says only that this returns the authoritative record, and a caller
     * must not depend on a network hop happening. What it *can* depend on is that a repeat view
     * inside the window costs nothing, and that a failure is never cached — so the retry every
     * error state carries does real work.
     *
     * That matters because this endpoint allows only 60 requests an hour unauthenticated, which
     * one request per tap exhausts inside a minute of browsing. See [DetailCache].
     */
    override suspend fun detail(coordinates: RepositoryCoordinates): Outcome<RepositoryDetail> =
        detailCache.getOrFetch(coordinates) {
            githubCall {
                // Interpolated, but not unvalidated. RepositoryCoordinates rejects, at
                // construction, any half that is blank, contains a path separator or a percent,
                // question mark or hash, holds whitespace or a control character, or is a
                // traversal segment. Ktor treats this string as *already* percent-encoded and
                // stores it verbatim, so that guard — not an escaping step here — is what keeps
                // both halves inside the two path segments intended. If a future caller builds
                // coordinates from anything other than GitHub's own `full_name` (a deep-linked
                // navigation key, say), tighten that guard to GitHub's charset rather than
                // relying on this call site.
                client
                    .get("repos/${coordinates.owner}/${coordinates.name}")
                    .body<RepositoryDetailDto>()
                    .toDomain()
            }
        }
}
