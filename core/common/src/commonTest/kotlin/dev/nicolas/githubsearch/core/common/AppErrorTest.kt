package dev.nicolas.githubsearch.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private val RESET_AT = Instant.fromEpochSeconds(1_788_000_000)

class AppErrorTest {
    @Test
    fun `rate limited carries the reset instant it was constructed with`() {
        val resetAt = RESET_AT

        val error = AppError.RateLimited(resetAt)

        assertEquals(resetAt, error.resetAt)
    }

    @Test
    fun `serialization carries the cause it was constructed with`() {
        val error = AppError.Serialization(cause = "Unexpected JSON token at offset 42")

        assertEquals("Unexpected JSON token at offset 42", error.cause)
    }

    @Test
    fun `unknown carries the cause it was constructed with`() {
        val error = AppError.Unknown(cause = "HTTP 500")

        assertEquals("HTTP 500", error.cause)
    }

    @Test
    fun `distinct kinds of error are never equal to one another`() {
        val distinct: List<AppError> =
            listOf(
                AppError.Network,
                AppError.NotFound,
                AppError.Unauthorized,
                AppError.RateLimited(RESET_AT),
                AppError.Serialization(cause = "boom"),
                AppError.Unknown(cause = "boom"),
            )

        // Same `cause` on two different kinds must not collapse them into one — a `when` that
        // treats Serialization as Unknown would silently mishandle a malformed payload.
        assertEquals(distinct.size, distinct.toSet().size)
    }
}
