package dev.nicolas.githubsearch.data.github

import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.core.network.GithubClientConfig
import dev.nicolas.githubsearch.core.network.configureGithubClient
import dev.nicolas.githubsearch.core.testing.FakeClock
import dev.nicolas.githubsearch.core.testing.KOTLIN_SUMMARY
import dev.nicolas.githubsearch.core.testing.LINUX_COORDINATES
import dev.nicolas.githubsearch.core.testing.LINUX_DETAIL
import dev.nicolas.githubsearch.core.testing.TestDispatcherProvider
import dev.nicolas.githubsearch.domain.RepositoryCoordinates
import dev.nicolas.githubsearch.domain.SEARCH_PAGE_SIZE
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private const val BASE_URL = "https://api.github.com"

private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

/** A repository wired to [handler], through the real client configuration. */
private fun TestScope.repository(
    clock: FakeClock = FakeClock(NOW),
    handler: MockRequestHandler,
): GithubRepository =
    GithubRepository(
        client =
            HttpClient(MockEngine(handler)) {
                configureGithubClient(GithubClientConfig(BASE_URL, token = "", clock = clock))
            },
        clock = clock,
        // One dispatcher for all three roles, sharing this test's scheduler. githubCall hops with
        // withContext(dispatchers.io), so a provider on its own scheduler would put the request on
        // a second virtual clock that nothing advances — and every test here would hang.
        dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
    )

class GithubRepositoryTest {
    @Test
    fun `search asks GitHub for one page of the pinned size`() =
        runTest {
            var url: Url? = null
            val repository =
                repository { request ->
                    url = request.url
                    respond(searchBody(searchItem()), HttpStatusCode.OK, jsonHeaders)
                }

            repository.search(query = "kotlin", page = 2)

            assertEquals("/search/repositories", url?.encodedPath)
            assertEquals("kotlin", url?.parameters?.get("q"))
            assertEquals("2", url?.parameters?.get("page"))
            // The port's contract: the ceiling the domain derives is only correct if the request
            // actually pages at this size.
            assertEquals(SEARCH_PAGE_SIZE.toString(), url?.parameters?.get("per_page"))
        }

    @Test
    fun `a hostile query round-trips through the q parameter encoded`() =
        runTest {
            val hostile = "kotlin b/c?d&e=f#g"
            var url: Url? = null
            val repository =
                repository { request ->
                    url = request.url
                    respond(searchBody(searchItem()), HttpStatusCode.OK, jsonHeaders)
                }

            repository.search(query = hostile, page = 1)

            // Built through Ktor's parameter API, never by string concatenation. Round-tripping
            // proves the encoding held; the second assertion proves the `&` did not escape and
            // split the query into a parameter the caller never asked for.
            assertEquals(hostile, url?.parameters?.get("q"))
            assertNull(url?.parameters?.get("e"))
        }

    @Test
    fun `search maps the response into summaries`() =
        runTest {
            val repository = repository { respond(searchBody(searchItem()), HttpStatusCode.OK, jsonHeaders) }

            val outcome = repository.search(query = "kotlin", page = 1)

            // Whole object, so a field silently dropped by the mapper — the id, for instance —
            // fails here rather than passing a suite that only checks the fields it remembered.
            assertEquals(Outcome.Success(listOf(KOTLIN_SUMMARY)), outcome)
        }

    @Test
    fun `search reports a failure as an Outcome rather than throwing`() =
        runTest {
            val repository = repository { respondError(HttpStatusCode.InternalServerError) }

            val outcome = repository.search(query = "kotlin", page = 1)

            // The caller never sees an exception — that is what the module boundary is for.
            assertIs<Outcome.Failure>(outcome)
            assertIs<AppError.Unknown>(outcome.error)
        }

    @Test
    fun `detail asks for the repository record and maps every field the screen shows`() =
        runTest {
            var path: String? = null
            val repository =
                repository { request ->
                    path = request.url.encodedPath
                    respond(detailBody(), HttpStatusCode.OK, jsonHeaders)
                }

            val outcome = repository.detail(LINUX_COORDINATES)

            assertEquals("/repos/torvalds/linux", path)
            assertEquals(Outcome.Success(LINUX_DETAIL), outcome)
        }

    @Test
    fun `detail is served from the cache on a second request`() =
        runTest {
            var requests = 0
            val repository =
                repository {
                    requests++
                    respond(detailBody(), HttpStatusCode.OK, jsonHeaders)
                }

            repository.detail(LINUX_COORDINATES)
            repository.detail(LINUX_COORDINATES)

            // The wiring test for DetailCache, whose policy is covered on its own. Without this,
            // every cache test could pass while `detail` never consulted the cache at all.
            assertEquals(1, requests)
        }

    @Test
    fun `detail reports NotFound for a repository that does not exist`() =
        runTest {
            val repository = repository { respondError(HttpStatusCode.NotFound) }

            val outcome = repository.detail(RepositoryCoordinates("nobody", "nothing"))

            assertEquals(Outcome.Failure(AppError.NotFound), outcome)
        }

    @Test
    fun `a payload missing required fields is reported as Serialization`() =
        runTest {
            val repository =
                repository {
                    respond("""{"full_name": "torvalds/linux"}""", HttpStatusCode.OK, jsonHeaders)
                }

            val outcome = repository.detail(LINUX_COORDINATES)

            assertIs<Outcome.Failure>(outcome)
            assertIs<AppError.Serialization>(outcome.error)
        }

    @Test
    fun `a complete payload with an unsplittable full_name is reported as Serialization`() =
        runTest {
            // Distinct from the case above, and the reason this test exists: the payload decodes
            // cleanly, so kotlinx never raises anything and the failure comes from the mapper. A
            // plain `require` there throws IllegalArgumentException, which the network module's
            // translation reports as AppError.Unknown — SerializationException *extends*
            // IllegalArgumentException, not the reverse. Only a payload that reaches the mapper
            // can catch that, and the missing-fields case above cannot.
            val repository =
                repository {
                    respond(detailBody(fullName = "no-separator"), HttpStatusCode.OK, jsonHeaders)
                }

            val outcome = repository.detail(LINUX_COORDINATES)

            assertIs<Outcome.Failure>(outcome)
            assertIs<AppError.Serialization>(outcome.error)
        }

    @Test
    fun `a full_name whose halves are unusable is reported as Serialization`() =
        runTest {
            // The regression guard for the mapper's error channel. Each of these splits into
            // exactly two parts, so a segment-count check alone lets them through and the failure
            // comes from the coordinate rules instead — which is the path that used to surface as
            // AppError.Unknown because RepositoryCoordinates' `require` throws
            // IllegalArgumentException, and SerializationException *extends* that, not the reverse.
            listOf("torvalds/", "/linux", "./linux", "torvalds/..", "a%2Fb/linux").forEach { hostile ->
                val repository =
                    repository {
                        respond(detailBody(fullName = hostile), HttpStatusCode.OK, jsonHeaders)
                    }

                val outcome = repository.detail(LINUX_COORDINATES)

                assertIs<Outcome.Failure>(outcome, "full_name \"$hostile\" should fail the payload")
                assertIs<AppError.Serialization>(outcome.error, "full_name \"$hostile\" should be Serialization")
            }
        }

    @Test
    fun `the shipped client tolerates fields the DTOs do not declare`() =
        runTest {
            // Asserted through `repository { }` so it runs against the real configureGithubClient,
            // not a Json instance the test built itself. GitHub adds fields on their schedule; a
            // client that fails on an unrecognised key breaks in production with no change of ours.
            val withFutureFields =
                """
                {
                  "id": 2325298, "full_name": "torvalds/linux",
                  "owner": { "avatar_url": "https://avatars.githubusercontent.com/u/1024025",
                             "login": "torvalds", "type": "User" },
                  "language": "C",
                  "stargazers_count": 184000, "watchers_count": 184000, "subscribers_count": 8100,
                  "forks_count": 54000, "open_issues_count": 341,
                  "some_field_invented_next_year": { "nested": [1, 2, 3] }
                }
                """.trimIndent()
            val repository = repository { respond(withFutureFields, HttpStatusCode.OK, jsonHeaders) }

            assertEquals(Outcome.Success(LINUX_DETAIL), repository.detail(LINUX_COORDINATES))
        }

    @Test
    fun `detail takes its coordinates from the response and not from the request`() =
        runTest {
            // GitHub answers a renamed repository through a redirect, so the record returned can
            // legitimately name a different repository than the one asked for. The spec requires
            // the detail model to be built entirely from the detail response; carrying the
            // requested coordinates onto the screen instead would show the stale name beside
            // fresh numbers, and no other test here can tell the two apart because they all
            // request and answer the same full_name.
            val repository =
                repository { respond(detailBody(fullName = "torvalds/linux-kernel"), HttpStatusCode.OK, jsonHeaders) }

            val outcome = repository.detail(LINUX_COORDINATES)

            assertEquals("torvalds/linux-kernel", (outcome as Outcome.Success).value.coordinates.fullName)
        }
}
