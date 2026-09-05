package dev.nicolas.githubsearch.core.network

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Absolute unix second at which the primary rate limit window resets. */
private const val HEADER_RATE_LIMIT_RESET = "x-ratelimit-reset"

/** Requests left in the primary window. `0` means that window is the binding constraint. */
internal const val HEADER_RATE_LIMIT_REMAINING = "x-ratelimit-remaining"

/**
 * Works out when the GitHub rate limit lifts, or `null` if the response does not say.
 *
 * GitHub runs two independent budgets and reports them differently:
 *
 *  - **Primary** — a request quota per window, reported by `x-ratelimit-remaining` and
 *    `x-ratelimit-reset` (an absolute unix second).
 *  - **Secondary** — an abuse-detection throttle, reported by `retry-after` (a delay in seconds).
 *
 * Which one is authoritative depends on `x-ratelimit-remaining`, and getting that backwards is not
 * cosmetic. GitHub sends the `x-ratelimit-*` headers on essentially every response, so a
 * secondary-limit reply carries both: when requests still remain, the primary reset is merely
 * informational, and reporting it would tell the user to wait the rest of the hour for a throttle
 * that lifts in a minute.
 *
 * Sources are tried in order of authority and the first that yields a *future* instant wins, so a
 * stale absolute reset — clock skew against GitHub, or a window that rolled over in flight — falls
 * through to `retry-after` rather than discarding it.
 *
 * Returning `null` rather than a fallback instant is deliberate: the caller degrades to a generic
 * error, which is honest, where a fabricated reset drives a countdown that is simply wrong.
 *
 * [clock] is injected because `retry-after` means nothing without one, and because a test asserting
 * on the result would otherwise be comparing against however long the test took to run.
 */
internal fun resolveRateLimitReset(
    headers: Headers,
    clock: Clock,
): Instant? {
    val now = clock.now()
    val primaryExhausted = headers[HEADER_RATE_LIMIT_REMAINING]?.trim() == "0"

    val byAuthority =
        if (primaryExhausted) {
            listOf(headers.absoluteReset(), headers.retryAfterDelay(clock))
        } else {
            listOf(headers.retryAfterDelay(clock), headers.absoluteReset())
        }

    // A reset in the past is unusable: it renders as a negative countdown and a retry control that
    // claims to be ready when it is not.
    return byAuthority.filterNotNull().firstOrNull { it > now }
}

private fun Headers.absoluteReset(): Instant? =
    this[HEADER_RATE_LIMIT_RESET]
        ?.trim()
        ?.toLongOrNull()
        ?.let(Instant::fromEpochSeconds)

/**
 * `retry-after` may legally be an HTTP date rather than a delay. GitHub sends seconds, and
 * [toLongOrNull] rejects the date form as unparseable, which degrades to a generic error rather
 * than to a wrong instant.
 */
private fun Headers.retryAfterDelay(clock: Clock): Instant? =
    this[HttpHeaders.RetryAfter]
        ?.trim()
        ?.toLongOrNull()
        ?.let { clock.now() + it.seconds }
