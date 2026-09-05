package dev.nicolas.githubsearch.core.common

import kotlin.time.Instant

/**
 * Every way this application can fail, as one closed set.
 *
 * Sealed so that `when` over an error is exhaustive without an `else`. That matters more than it
 * looks: an `else` branch silently absorbs the next variant added here, and the screen that should
 * have grown a new state keeps compiling while quietly showing the wrong one.
 *
 * `CancellationException` is deliberately **not** modelled. Cancellation is not a failure — it is
 * the caller withdrawing interest — and mapping it into this hierarchy would show an error message
 * for a screen the user has already left. It propagates instead.
 */
public sealed interface AppError {
    /** No usable connection: DNS failure, refused connection, or a timeout on any of the three. */
    public data object Network : AppError

    /**
     * The GitHub API rate limit was hit.
     *
     * [resetAt] is a real instant rather than a message so the UI can render a countdown, and it is
     * resolved from `x-ratelimit-reset` or from `retry-after` against an injected clock — which is
     * what makes the surrounding behaviour testable without waiting for real time to pass.
     */
    public data class RateLimited(
        val resetAt: Instant,
    ) : AppError

    /** The requested repository does not exist, or is private to this caller. */
    public data object NotFound : AppError

    /** The supplied token was rejected. Distinct from [NotFound], which GitHub also returns for it. */
    public data object Unauthorized : AppError

    /** The response did not match the expected shape. [cause] is a diagnostic, never shown raw. */
    public data class Serialization(
        val cause: String,
    ) : AppError

    /** Anything not otherwise classified. [cause] is a diagnostic, never shown raw. */
    public data class Unknown(
        val cause: String,
    ) : AppError
}
