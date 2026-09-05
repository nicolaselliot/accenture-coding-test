package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.testing.FakeClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class GithubHttpClientTest {
    private fun client(
        token: String = "",
        handler: MockRequestHandler,
    ): HttpClient =
        HttpClient(MockEngine(handler)) {
            configureGithubClient(
                GithubClientConfig(baseUrl = BASE_URL, token = token, clock = FakeClock(NOW)),
            )
        }

    @Test
    fun `a server error is retried and then surfaces Unknown`() =
        runTest {
            var attempts = 0
            val client =
                client {
                    attempts++
                    respondError(HttpStatusCode.InternalServerError)
                }

            val thrown = assertFailsWith<GithubApiException> { client.get(BASE_URL) }

            assertIs<AppError.Unknown>(thrown.error)
            // One original attempt plus the three configured retries.
            assertEquals(4, attempts)
        }

    @Test
    fun `a client error is never retried`() =
        runTest {
            var attempts = 0
            val client =
                client {
                    attempts++
                    respondError(HttpStatusCode.NotFound)
                }

            val thrown = assertFailsWith<GithubApiException> { client.get(BASE_URL) }

            assertEquals(AppError.NotFound, thrown.error)
            // Retrying a 404 cannot succeed. It only spends requests against a rate limit that is
            // already the tightest constraint this app has.
            assertEquals(1, attempts)
        }

    @Test
    fun `a timed out request is attempted more than once`() =
        runTest {
            var attempts = 0
            val client =
                client {
                    attempts++
                    throw HttpRequestTimeoutException("$BASE_URL/", 15_000)
                }

            val thrown = assertFailsWith<GithubApiException> { client.get(BASE_URL) }

            assertEquals(AppError.Network, thrown.error)
            // This is what the plugin order buys. HttpRequestRetry is installed BEFORE
            // HttpTimeout; the other way round the timeout wraps the retry loop and a slow
            // response fails once, permanently.
            assertEquals(4, attempts)
        }

    @Test
    fun `a rate limited response surfaces RateLimited with the reset instant`() =
        runTest {
            val client =
                client {
                    respondError(
                        status = HttpStatusCode.Forbidden,
                        headers =
                            headersOf(
                                "x-ratelimit-remaining" to listOf("0"),
                                "x-ratelimit-reset" to listOf(RESET_AT_HEADER),
                            ),
                    )
                }

            val thrown = assertFailsWith<GithubApiException> { client.get(BASE_URL) }

            assertEquals(AppError.RateLimited(RESET_AT), thrown.error)
        }

    @Test
    fun `the authorization header is sent when a token is configured`() =
        runTest {
            var seen: String? = null
            val client =
                client(token = "ghp_example") { request ->
                    seen = request.headers[HttpHeaders.Authorization]
                    respond("{}", HttpStatusCode.OK)
                }

            client.get(BASE_URL)

            assertEquals("Bearer ghp_example", seen)
        }

    @Test
    fun `the authorization header is absent entirely when no token is configured`() =
        runTest {
            var present = true
            val client =
                client(token = "") { request ->
                    present = request.headers.contains(HttpHeaders.Authorization)
                    respond("{}", HttpStatusCode.OK)
                }

            client.get(BASE_URL)

            // Absent, not blank. `Authorization: Bearer ` is a malformed credential that GitHub
            // answers with 401, which would read as a bad token rather than as no token — and this
            // app must work unauthenticated, because the reviewer will run it that way.
            assertFalse(present)
        }

    @Test
    fun `cancellation propagates instead of becoming an AppError`() =
        runTest {
            var attempts = 0
            val client =
                client {
                    attempts++
                    throw CancellationException("the caller navigated away")
                }

            // Cancellation is not a failure — it is the caller withdrawing interest. Mapping it
            // into AppError would surface an error state on a screen the user has already left,
            // and would break structured concurrency by swallowing the signal.
            assertFailsWith<CancellationException> { client.get(BASE_URL) }

            // And it must not be retried: three more requests for a result nobody is waiting for
            // would spend a rate-limit budget the next real search needs.
            assertEquals(1, attempts)
        }

    @Test
    fun `a relative path resolves under the configured base`() =
        runTest {
            var seen: String? = null
            // No trailing slash, which is how anyone would write it in a developer properties file
            // or a CI variable. Ktor's defaultRequest merge replaces the last path segment of the
            // base, so without normalisation the "/github" prefix vanishes and every request
            // silently goes to the wrong path — with no error anywhere.
            val client =
                HttpClient(
                    MockEngine { request ->
                        seen = request.url.toString()
                        respond("{}", HttpStatusCode.OK)
                    },
                ) {
                    configureGithubClient(
                        GithubClientConfig(
                            baseUrl = "https://proxy.example.com/github",
                            token = "",
                            clock = FakeClock(NOW),
                        ),
                    )
                }

            client.get("search/repositories")

            assertEquals("https://proxy.example.com/github/search/repositories", seen)
        }
}
