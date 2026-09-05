package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.common.AppError
import dev.nicolas.githubsearch.core.testing.FakeClock
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class FailureTranslationTest {
    private val clock = FakeClock(NOW)

    @Test
    fun `not found maps to NotFound`() {
        val error = statusToAppError(HttpStatusCode.NotFound, headersOf(), clock)

        assertEquals(AppError.NotFound, error)
    }

    @Test
    fun `unauthorized maps to Unauthorized`() {
        val error = statusToAppError(HttpStatusCode.Unauthorized, headersOf(), clock)

        assertEquals(AppError.Unauthorized, error)
    }

    @Test
    fun `forbidden with no remaining requests maps to RateLimited carrying the reset instant`() {
        val headers =
            headersOf(
                "x-ratelimit-remaining" to listOf("0"),
                "x-ratelimit-reset" to listOf(RESET_AT_HEADER),
            )

        val error = statusToAppError(HttpStatusCode.Forbidden, headers, clock)

        assertEquals(AppError.RateLimited(RESET_AT), error)
    }

    @Test
    fun `too many requests maps to RateLimited even with no remaining header`() {
        // 429 is unambiguous: it is the secondary limit, which carries retry-after and never
        // x-ratelimit-remaining. Treating it as Unknown would show a generic error for the single
        // most likely failure an unauthenticated reviewer will hit.
        val headers = headersOf("retry-after", "60")

        val error = statusToAppError(HttpStatusCode.TooManyRequests, headers, clock)

        assertEquals(AppError.RateLimited(NOW + 60.seconds), error)
    }

    @Test
    fun `forbidden with a retry-after is the secondary limit even when requests remain`() {
        val headers =
            headersOf(
                "x-ratelimit-remaining" to listOf("47"),
                "retry-after" to listOf("60"),
            )

        val error = statusToAppError(HttpStatusCode.Forbidden, headers, clock)

        // The secondary limit is a separate budget from the primary one: GitHub answers 403 with a
        // retry-after while x-ratelimit-remaining is still non-zero. Requiring remaining==0 makes
        // this a generic error with no countdown — the exact trap CLAUDE.md documents.
        assertEquals(AppError.RateLimited(NOW + 60.seconds), error)
    }

    @Test
    fun `forbidden that is not a rate limit maps to Unknown`() {
        // GitHub returns 403 for genuine permission failures too. Without the rate-limit signal
        // there is nothing to count down to.
        val error = statusToAppError(HttpStatusCode.Forbidden, headersOf(), clock)

        assertIs<AppError.Unknown>(error)
    }

    @Test
    fun `a rate limit with an unusable reset header degrades to Unknown`() {
        // The status says rate limited but the reset is unparseable. Reporting RateLimited with a
        // fabricated instant would drive a countdown to a time that means nothing.
        val headers =
            headersOf(
                "x-ratelimit-remaining" to listOf("0"),
                "x-ratelimit-reset" to listOf("not-a-number"),
            )

        val error = statusToAppError(HttpStatusCode.Forbidden, headers, clock)

        assertIs<AppError.Unknown>(error)
    }

    @Test
    fun `a server error maps to Unknown carrying the status for diagnosis`() {
        val error = statusToAppError(HttpStatusCode.InternalServerError, headersOf(), clock)

        assertIs<AppError.Unknown>(error)
        assertTrue(error.cause.contains("500"))
    }

    @Test
    fun `a timeout maps to Network`() {
        val error = HttpRequestTimeoutException(BASE_URL, 15_000).toAppError()

        // A timeout is indistinguishable from no connection as far as the user is concerned, and
        // both are answered by the same retry.
        assertEquals(AppError.Network, error)
    }

    @Test
    fun `a malformed payload maps to Serialization`() {
        val error = SerializationException("Unexpected JSON token at offset 42").toAppError()

        assertIs<AppError.Serialization>(error)
    }

    @Test
    fun `an unrecognised failure maps to Unknown rather than being swallowed`() {
        val error = IllegalStateException("something else entirely").toAppError()

        assertIs<AppError.Unknown>(error)
    }
}
