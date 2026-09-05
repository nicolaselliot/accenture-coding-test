package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.core.testing.FakeClock
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GithubCallTest {
    private fun client(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient =
        HttpClient(MockEngine(handler)) {
            configureGithubClient(
                GithubClientConfig(baseUrl = BASE_URL, token = "", clock = FakeClock(NOW)),
            )
        }

    @Test
    fun `returns Success carrying the value when the call succeeds`() =
        runTest {
            val client =
                client {
                    respond(
                        """{"name":"kotlin"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val outcome = githubCall { client.get(BASE_URL).body<JsonObject>() }

            // JsonObject rather than a @Serializable class: this module has no serialization
            // compiler plugin, because it owns no DTOs — those arrive with :data:github. The
            // decode is still real, which is what the test needs.
            assertIs<Outcome.Success<JsonObject>>(outcome)
            assertEquals("kotlin", outcome.value["name"]?.jsonPrimitive?.content)
        }

    @Test
    fun `returns Failure carrying the translated error`() =
        runTest {
            val client = client { respondError(HttpStatusCode.NotFound) }

            val outcome = githubCall { client.get(BASE_URL).body<JsonObject>() }

            assertEquals(Outcome.Failure(AppError.NotFound), outcome)
        }

    @Test
    fun `a malformed payload becomes Serialization end to end`() =
        runTest {
            val client =
                client {
                    respond(
                        """{"unexpected":[}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val outcome = githubCall { client.get(BASE_URL).body<JsonObject>() }

            // Asserted through the client rather than against the mapper in isolation: the
            // response is well-formed HTTP, so nothing fails until the body is decoded — which is
            // a path the status-based translation never sees.
            assertIs<Outcome.Failure>(outcome)
            assertIs<AppError.Serialization>(outcome.error)
        }

    @Test
    fun `cancellation propagates rather than becoming a Failure`() =
        runTest {
            // The one exception that must never be turned into a value. Swallowing it here would
            // break structured concurrency and leave an error state on an abandoned screen.
            assertFailsWith<CancellationException> {
                githubCall<Unit> { throw CancellationException("the caller navigated away") }
            }
        }

    @Test
    fun `an untranslated failure is still mapped rather than escaping`() =
        runTest {
            val outcome = githubCall<Unit> { error("something no plugin translated") }

            assertIs<Outcome.Failure>(outcome)
            assertIs<AppError.Unknown>(outcome.error)
        }
}
