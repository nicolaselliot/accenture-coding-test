package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.common.DispatcherProvider
import dev.nicolas.githubsearch.core.common.Outcome
import dev.nicolas.githubsearch.core.testing.FakeClock
import dev.nicolas.githubsearch.core.testing.TestDispatcherProvider
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

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

            val outcome = githubCall(dispatchers()) { client.get(BASE_URL).body<JsonObject>() }

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

            val outcome = githubCall(dispatchers()) { client.get(BASE_URL).body<JsonObject>() }

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

            val outcome = githubCall(dispatchers()) { client.get(BASE_URL).body<JsonObject>() }

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
                githubCall<Unit>(dispatchers()) { throw CancellationException("the caller navigated away") }
            }
        }

    @Test
    fun `an untranslated failure is still mapped rather than escaping`() =
        runTest {
            val outcome = githubCall<Unit>(dispatchers()) { error("something no plugin translated") }

            assertIs<Outcome.Failure>(outcome)
            assertIs<AppError.Unknown>(outcome.error)
        }

    @Test
    fun `the request leaves the caller's dispatcher for the injected io one`() =
        runTest {
            val ioDispatcher = StandardTestDispatcher(testScheduler)
            val mainDispatcher = StandardTestDispatcher(testScheduler)
            val dispatchers =
                object : DispatcherProvider {
                    override val main: CoroutineDispatcher = mainDispatcher
                    override val io: CoroutineDispatcher = ioDispatcher
                    override val default: CoroutineDispatcher = mainDispatcher
                }
            var observed: ContinuationInterceptor? = null

            githubCall(dispatchers) { observed = currentCoroutineContext()[ContinuationInterceptor] }

            // Not a restatement of `withContext`. Without the hop the block runs on whatever the
            // caller was on, which for a ViewModel is the main thread — and response decoding plus
            // the mapping that follows would happen there, per request, per page.
            assertSame(ioDispatcher, observed)
        }
}

/**
 * All three roles on one dispatcher that shares this test's scheduler.
 *
 * The scheduler has to be the enclosing `runTest`'s. `githubCall` now hops with
 * `withContext(dispatchers.io)`, so a provider built from a fresh `StandardTestDispatcher()` would
 * put the request on a second virtual clock that nothing ever advances — and every test here would
 * hang rather than fail informatively.
 */
private fun TestScope.dispatchers(): TestDispatcherProvider =
    TestDispatcherProvider(StandardTestDispatcher(testScheduler))
