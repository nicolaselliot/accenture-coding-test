package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.testing.FakeClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private const val TOKEN = "ghp_sup3rs3cr3tvalue"

/**
 * Regression guards for redirect handling, which this project relies on but does not configure.
 *
 * Ktor's defaults are correct today — verified, not assumed. These tests exist because the
 * behaviour is a *security* boundary rather than a functional one: if a future Ktor followed a
 * cross-authority redirect while still sending `Authorization`, the PAT would be handed to whoever
 * controls the redirect target, and nothing else in this codebase would notice. Dependabot raises
 * dependency bumps on a schedule, so "a future Ktor" is a scheduled event, not a hypothetical.
 *
 * Written green rather than red-first, because they pin behaviour that is already correct. That is
 * the honest description of a regression guard.
 */
class RedirectSecurityTest {
    private fun client(
        redirectTo: String,
        record: (io.ktor.client.request.HttpRequestData) -> Unit,
    ): HttpClient {
        var hop = 0
        return HttpClient(
            MockEngine { request ->
                record(request)
                if (hop++ == 0) respondRedirect(redirectTo) else respond("{}", HttpStatusCode.OK)
            },
        ) {
            configureGithubClient(GithubClientConfig(BASE_URL, TOKEN, FakeClock(NOW)))
        }
    }

    @Test
    fun `a redirect that downgrades to cleartext is not followed`() =
        runTest {
            val visited = mutableListOf<String>()
            val client = client("http://evil.example.com/steal") { visited += it.url.toString() }

            assertFailsWith<GithubApiException> { client.get(BASE_URL) }

            // One hop only: the downgrade is refused rather than followed. Sending a bearer token
            // over cleartext would hand it to anyone on the path.
            assertEquals(1, visited.size)
        }

    @Test
    fun `the token is not forwarded when a redirect crosses to another host`() =
        runTest {
            var authOnSecondHop: String? = null
            var hop = 0
            val client =
                client("https://evil.example.com/steal") { request ->
                    if (hop++ == 1) authOnSecondHop = request.headers[HttpHeaders.Authorization]
                }

            client.get(BASE_URL)

            // Both halves, because assertNull alone passes vacuously: it is also satisfied if the
            // second request never happened. A future Ktor that simply stopped following
            // cross-authority redirects would leave this guard green while it verified nothing.
            assertEquals(2, hop, "the redirect must actually have been followed")
            // The credential is not carried across an authority boundary. This is the property
            // whose silent loss would hand the PAT to whoever controls the redirect target.
            assertNull(authOnSecondHop)
        }

    @Test
    fun `the token is still sent when a redirect stays on the same host`() =
        runTest {
            var authOnSecondHop: String? = null
            var hop = 0
            val client =
                client("$BASE_URL/other") { request ->
                    if (hop++ == 1) authOnSecondHop = request.headers[HttpHeaders.Authorization]
                }

            client.get(BASE_URL)

            assertEquals(2, hop, "the redirect must actually have been followed")
            // The complement of the test above: stripping unconditionally would silently break
            // authenticated requests against GitHub's own redirects, so this pins that the rule is
            // scoped to the authority change rather than to redirects in general.
            assertEquals("Bearer $TOKEN", authOnSecondHop)
        }
}
