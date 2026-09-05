package dev.nicolas.githubsearch.core.testing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val START = Instant.fromEpochSeconds(1_788_000_000)

class FakeClockTest {
    @Test
    fun `now returns the instant the clock was constructed with`() {
        val start = START

        val clock = FakeClock(start)

        assertEquals(start, clock.now())
    }

    @Test
    fun `now returns the same instant however many times it is read`() {
        val clock = FakeClock(START)

        val first = clock.now()
        val second = clock.now()

        // The whole point of a fake clock: two reads inside one assertion must not straddle a
        // tick, or a test comparing them becomes flaky for reasons unrelated to the code.
        assertEquals(first, second)
    }

    @Test
    fun `advanceBy moves the clock forward by exactly that duration`() {
        val start = START
        val clock = FakeClock(start)

        clock.advanceBy(5.minutes)

        assertEquals(start + 5.minutes, clock.now())
    }
}
