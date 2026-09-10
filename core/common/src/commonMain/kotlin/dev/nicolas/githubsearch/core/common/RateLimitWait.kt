package dev.nicolas.githubsearch.core.common

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The longest wait worth repeating back to the user.
 *
 * GitHub's slowest budget is the hourly one on `GET /repos/{owner}/{repo}`, so an hour is the
 * largest legitimate answer. Anything beyond it means the header was wrong — a clock skewed against
 * GitHub's, or a value that is numeric but nonsense — and a screen promising a wait of days is worse
 * than one that says nothing specific at all.
 */
private val LONGEST_REAL_BUDGET = 60.minutes

private const val SECONDS_PER_MINUTE = 60

/**
 * How many whole minutes to tell the user to wait, or `null` if there is nothing sensible to say.
 *
 * Takes [now] rather than reading a clock, so the caller's injected [kotlin.time.Clock] stays the
 * only time source and the rounding rule below is testable without waiting for real time to pass.
 *
 * Declared on [AppError] rather than on [AppError.RateLimited] so the cast lives here: both screens
 * hand over whatever failed, and neither should have to know which variant carries an instant.
 *
 * Three rules, each of which exists because of a way this reads badly otherwise:
 * - **Rounded up.** Ninety seconds reported as one minute sends the user back half a minute early,
 *   and the retry spends a request that fails exactly the same way.
 * - **Never below one.** A reset that is seconds away, or already past, would otherwise render as
 *   "try again in about 0 min", which reads as a screen that has not finished thinking. The floor
 *   is also what keeps a stale header from producing a negative count.
 * - **Nothing above [LONGEST_REAL_BUDGET].** The result is `null`, and the caller falls back to the
 *   generic message. This is the same degradation a missing or non-numeric header already gets
 *   upstream, applied to the header that parsed but cannot be true.
 */
public fun AppError.rateLimitWaitMinutes(now: Instant): Int? {
    val remaining = (this as? AppError.RateLimited)?.let { it.resetAt - now }

    // The two cases with nothing to say share a branch: an error that carries no reset at all, and
    // one whose reset cannot be true. Compared as a Duration, before any conversion to a number —
    // Duration arithmetic saturates at infinity rather than overflowing, so an absurd instant fails
    // this check instead of arriving below as a wrapped-around negative.
    if (remaining == null || remaining > LONGEST_REAL_BUDGET) return null

    // Ceiling division. Kotlin truncates towards zero, so a negative remainder lands on 0 here and
    // the floor below lifts it to one — no separate branch for a reset that has already passed.
    val wholeMinutes = (remaining.inWholeSeconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE

    // Safe to narrow: the check above bounds this at sixty.
    return wholeMinutes.coerceAtLeast(1).toInt()
}
