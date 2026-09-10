package dev.nicolas.githubsearch.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.fromEpochSeconds(1_788_000_000)

class RateLimitWaitTest {
    @Test
    fun `a reset ninety seconds away reports two minutes`() {
        val error = AppError.RateLimited(resetAt = NOW + 90.seconds)

        val minutes = error.rateLimitWaitMinutes(NOW)

        // Rounded up, not to the nearest: a minute and a half reported as one sends the user back
        // thirty seconds early, and the retry spends a request that fails the same way.
        assertEquals(2, minutes)
    }

    @Test
    fun `a reset exactly one minute away reports one minute`() {
        val error = AppError.RateLimited(resetAt = NOW + 1.minutes)

        val minutes = error.rateLimitWaitMinutes(NOW)

        assertEquals(1, minutes)
    }

    @Test
    fun `a reset one second away reports one minute rather than zero`() {
        val error = AppError.RateLimited(resetAt = NOW + 1.seconds)

        val minutes = error.rateLimitWaitMinutes(NOW)

        // "Try again in about 0 min" reads as a screen that has not finished thinking.
        assertEquals(1, minutes)
    }

    @Test
    fun `a reset that has already passed reports one minute`() {
        val error = AppError.RateLimited(resetAt = NOW - 5.minutes)

        val minutes = error.rateLimitWaitMinutes(NOW)

        // The header can be stale by the time it is read. One minute is the safe floor: it never
        // produces a negative countdown, and the retry control is there for a user who waits less.
        assertEquals(1, minutes)
    }

    @Test
    fun `a reset at the longest real budget still reports a wait`() {
        val error = AppError.RateLimited(resetAt = NOW + 60.minutes)

        val minutes = error.rateLimitWaitMinutes(NOW)

        // Sixty minutes is legitimate: GET /repos/{owner}/{repo} is an hourly budget, so this is
        // the boundary rather than the absurd case below it.
        assertEquals(60, minutes)
    }

    @Test
    fun `a reset beyond the longest real budget reports no wait`() {
        val error = AppError.RateLimited(resetAt = NOW + 61.minutes)

        val minutes = error.rateLimitWaitMinutes(NOW)

        // No GitHub budget resets further out than an hour, so a header saying so is not to be
        // believed. Degrading to the generic message beats telling the user to wait a year.
        assertNull(minutes)
    }

    @Test
    fun `an absurd reset far in the future reports no wait rather than overflowing`() {
        val error = AppError.RateLimited(resetAt = Instant.DISTANT_FUTURE)

        val minutes = error.rateLimitWaitMinutes(NOW)

        // A non-numeric header already degrades to a generic error upstream; a numeric but absurd
        // one has to degrade here, and it must not arrive as a negative Int through an overflow.
        assertNull(minutes)
    }

    @Test
    fun `an error that is not a rate limit reports no wait`() {
        val minutes = AppError.Network.rateLimitWaitMinutes(NOW)

        // The caller passes whatever failed, so the cast lives here rather than at each screen.
        assertNull(minutes)
    }
}
