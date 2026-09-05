package dev.nicolas.githubsearch.core.network

import dev.nicolas.githubsearch.core.common.AppError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.time.Clock

/**
 * Translates an unsuccessful HTTP status into the one error vocabulary the rest of the app knows.
 *
 * Centralised here rather than at call sites so that every repository sees the same mapping — the
 * alternative is a `try`/`catch` per request, each subtly different, and a 429 that means one thing
 * on search and another on detail.
 */
internal fun statusToAppError(
    status: HttpStatusCode,
    headers: Headers,
    clock: Clock,
): AppError =
    when {
        status == HttpStatusCode.NotFound -> AppError.NotFound
        status == HttpStatusCode.Unauthorized -> AppError.Unauthorized
        status.isRateLimit(headers) -> rateLimited(headers, clock, status)
        else -> AppError.Unknown(cause = "HTTP ${status.value}")
    }

/**
 * GitHub signals the limit two ways, and only one of them is self-evident from the status.
 *
 * 429 always means rate limited. 403 is overloaded — it is also a genuine permission failure — so
 * it counts only when the response carries a rate-limit signal: either the primary budget is spent
 * (`x-ratelimit-remaining: 0`) or the secondary throttle has fired (`retry-after`).
 *
 * Both branches are needed. The secondary limit is a separate budget, so it answers 403 with a
 * `retry-after` while `x-ratelimit-remaining` is still non-zero; requiring the remaining header
 * alone would turn the most likely failure an unauthenticated user hits into a generic error with
 * no countdown.
 */
private fun HttpStatusCode.isRateLimit(headers: Headers): Boolean =
    this == HttpStatusCode.TooManyRequests ||
        (
            this == HttpStatusCode.Forbidden &&
                (
                    headers[HEADER_RATE_LIMIT_REMAINING]?.trim() == "0" ||
                        headers[HttpHeaders.RetryAfter] != null
                )
        )

/**
 * Degrades to [AppError.Unknown] when the response does not carry a usable reset time.
 *
 * A `RateLimited` with a fabricated instant is worse than a generic error: it drives a countdown to
 * a moment that means nothing and a retry control that claims to know when it will work.
 */
private fun rateLimited(
    headers: Headers,
    clock: Clock,
    status: HttpStatusCode,
): AppError =
    resolveRateLimitReset(headers, clock)
        ?.let(AppError::RateLimited)
        ?: AppError.Unknown(cause = "HTTP ${status.value} rate limited, no usable reset header")

/**
 * Translates a thrown failure into the same error vocabulary as [statusToAppError].
 *
 * `CancellationException` is deliberately not handled here. It is not a failure — it is the caller
 * withdrawing interest — and it is rethrown before reaching this function so that structured
 * concurrency keeps working.
 */
internal fun Throwable.toAppError(): AppError =
    when {
        isTransport() -> AppError.Network
        isSerialization() -> AppError.Serialization(cause = message ?: this::class.simpleName.orEmpty())
        else -> AppError.Unknown(cause = message ?: this::class.simpleName.orEmpty())
    }

/**
 * Whether retrying could plausibly succeed.
 *
 * Timeouts and connection failures are worth another attempt; a malformed payload is not, because
 * the same request will produce the same unparseable bytes.
 *
 * Socket timeouts are covered by the [IOException] branch — Ktor 3.5.2 declares no common
 * `SocketTimeoutException`, and every socket-level failure it raises in common code is an
 * `IOException`.
 */
internal fun Throwable.isTransport(): Boolean =
    this is IOException ||
        this is HttpRequestTimeoutException ||
        this is ConnectTimeoutException

private fun Throwable.isSerialization(): Boolean = this is SerializationException || this is JsonConvertException
