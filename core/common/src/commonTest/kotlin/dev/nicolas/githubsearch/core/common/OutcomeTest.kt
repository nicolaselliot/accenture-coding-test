package dev.nicolas.githubsearch.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class OutcomeTest {
    @Test
    fun `map transforms the value of a success`() {
        val outcome: Outcome<Int> = Outcome.Success(21)

        val mapped = outcome.map { it * 2 }

        assertEquals(Outcome.Success(42), mapped)
    }

    @Test
    fun `map returns the failure unchanged`() {
        val outcome: Outcome<Int> = Outcome.Failure(AppError.Network)

        val mapped = outcome.map { it * 2 }

        assertEquals(Outcome.Failure(AppError.Network), mapped)
    }

    @Test
    fun `map never invokes the transform on a failure`() {
        var invoked = false
        val outcome: Outcome<Int> = Outcome.Failure(AppError.NotFound)

        outcome.map {
            invoked = true
            it
        }

        // Not merely wasteful: the transform in a mapper can throw on absent data, so running it
        // over a failure turns a handled error into a crash.
        assertFalse(invoked)
    }

    @Test
    fun `failure carries the error it was constructed with`() {
        val error = AppError.RateLimited(Instant.fromEpochSeconds(1_788_000_000))

        val failure = Outcome.Failure(error)

        assertEquals(error, failure.error)
    }

    @Test
    fun `success carries the value it was constructed with`() {
        val success = Outcome.Success("kotlin")

        assertEquals("kotlin", success.value)
    }
}
