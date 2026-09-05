package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.testing.FakeClock
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RateLimitTest {
    @Test
    fun `resolves the reset instant from x-ratelimit-reset`() {
        val clock = FakeClock(NOW)
        val headers = headersOf("x-ratelimit-reset", RESET_AT_HEADER)

        val resetAt = resolveRateLimitReset(headers, clock)

        // The primary limit reports an absolute unix second, so the clock is not consulted.
        assertEquals(RESET_AT, resetAt)
    }

    @Test
    fun `resolves the reset instant from retry-after as a delta on the clock`() {
        val clock = FakeClock(NOW)
        val headers = headersOf("retry-after", "60")

        val resetAt = resolveRateLimitReset(headers, clock)

        // The secondary limit reports a delta in seconds, which only means anything against a
        // clock — which is why the clock is injected rather than read from the system.
        assertEquals(NOW + 60.seconds, resetAt)
    }

    @Test
    fun `prefers the absolute reset when the primary budget is exhausted`() {
        val clock = FakeClock(NOW)
        val headers =
            headersOf(
                "x-ratelimit-remaining" to listOf("0"),
                "x-ratelimit-reset" to listOf(RESET_AT_HEADER),
                "retry-after" to listOf("5"),
            )

        val resetAt = resolveRateLimitReset(headers, clock)

        // remaining=0 means the primary window is the binding constraint, and its absolute instant
        // is better information than a delta measured against a clock that may be skewed.
        assertEquals(RESET_AT, resetAt)
    }

    @Test
    fun `prefers retry-after when the primary budget still has requests left`() {
        val clock = FakeClock(NOW)
        val headers =
            headersOf(
                // GitHub sends x-ratelimit-* on essentially every response, so a secondary-limit
                // reply carries both. A non-zero remaining means the primary window is NOT the
                // constraint, and its reset is merely informational — reporting it would tell the
                // user to wait ten minutes for a limit that lifts in one.
                "x-ratelimit-remaining" to listOf("47"),
                "x-ratelimit-reset" to listOf(RESET_AT_HEADER),
                "retry-after" to listOf("60"),
            )

        val resetAt = resolveRateLimitReset(headers, clock)

        assertEquals(NOW + 60.seconds, resetAt)
    }

    @Test
    fun `falls through to retry-after when the absolute reset has already passed`() {
        val clock = FakeClock(NOW)
        val headers =
            headersOf(
                // Stale because of clock skew against GitHub, or a window that rolled over while
                // the response was in flight.
                "x-ratelimit-remaining" to listOf("0"),
                "x-ratelimit-reset" to listOf("1700000000"),
                "retry-after" to listOf("60"),
            )

        val resetAt = resolveRateLimitReset(headers, clock)

        // Discarding a perfectly usable retry-after because the other header is stale degrades a
        // countdown the app could have shown into a generic error.
        assertEquals(NOW + 60.seconds, resetAt)
    }

    @Test
    fun `returns null when neither header is present`() {
        val resetAt = resolveRateLimitReset(headersOf(), FakeClock(NOW))

        assertNull(resetAt)
    }

    @Test
    fun `returns null when the header is empty or not a number`() {
        val clock = FakeClock(NOW)

        assertNull(resolveRateLimitReset(headersOf("x-ratelimit-reset", ""), clock))
        assertNull(resolveRateLimitReset(headersOf("x-ratelimit-reset", "soon"), clock))
        assertNull(resolveRateLimitReset(headersOf("retry-after", "Wed, 21 Oct 2026 07:28:00 GMT"), clock))
    }

    @Test
    fun `never resolves an instant in the past`() {
        val clock = FakeClock(NOW)
        // GitHub reports a reset that has already elapsed — clock skew, or a slow response.
        val headers = headersOf("x-ratelimit-reset", "1700000000")

        val resetAt = resolveRateLimitReset(headers, clock)

        // A countdown to a past instant renders as a negative timer and a retry button that lies.
        assertNull(resetAt)
    }
}
