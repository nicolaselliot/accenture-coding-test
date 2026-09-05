package dev.nicolas.githubsearch.core.testing

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A [Clock] that returns exactly what it is told to, and moves only when asked.
 *
 * Rate-limit handling is time-dependent behaviour: `x-ratelimit-reset` and `retry-after` are both
 * resolved against a clock, and the resulting [dev.nicolas.githubsearch.core.common.AppError] is
 * asserted on. Against the real clock those assertions compare instants that differ by however long
 * the test took to run, which is the definition of a flaky test.
 *
 * Deliberately mutable, and deliberately not thread-safe. A test drives it from one place; adding
 * synchronisation would imply a concurrency guarantee that no caller needs and no test exercises.
 */
public class FakeClock(
    private var current: Instant,
) : Clock {
    override fun now(): Instant = current

    /**
     * Moves the clock by [duration]. Negative durations move it back, which is how a test
     * expresses a rate-limit reset that has already passed — a case production code must
     * degrade gracefully for.
     */
    public fun advanceBy(duration: Duration) {
        current += duration
    }
}
